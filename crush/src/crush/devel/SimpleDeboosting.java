/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of crush.
 * 
 *     crush is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     crush is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with crush.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package crush.devel;

import java.io.IOException;

import kovacs.data.*;
import kovacs.util.*;


public class SimpleDeboosting {
	//static String fileName = "/home/pumukli/data/gismo/counts/dn_ds_lapi.txt";
	static String fileName = null;
	
	int order = 2;
	SimpleInterpolator table; 
	
	public static void main(String[] args) {

		SimpleDeboosting debooster = new SimpleDeboosting();		
		
		if(fileName != null) {
			try { 
				debooster.table = new SimpleInterpolator(fileName); 
				System.err.println("### Got " + debooster.table.size() + " entries.");
			}
			catch(IOException e) { e.printStackTrace(); }
		}
		
		double S1 = Double.parseDouble(args[0]);
		double sigma = Double.parseDouble(args[1]);			
		double beamFWHM = 17.5 * Unit.arcsec;
		//double beamFWHM = 2.0 * Unit.arcsec;
		
		System.err.println(" Deboosted: " + debooster.deboost(S1, sigma, beamFWHM).toString(Util.s3));
	}
	

	public WeightedPoint deboost(double S1, double sigma, double beamFWHM) {		
		double suma = 0.0, suma2 = 0.0, sumb = 0.0;
		double dS = 0.01 * sigma;
		double A = -0.5 / (sigma * sigma);
		double beamArea = 1.13 * beamFWHM * beamFWHM;	// beam-smoothed map
		double confusionLimit = Unit.deg2 / beamArea;

		int toN = (int) Math.ceil((S1 + 4.0*sigma) / dS);
		
		System.err.println(" dS = " + Util.e2.format(dS) + " mJy");
		
		// Around the confusion limit, every beam pretty much has sources in it
		// So the sources make just a continuous bg, and add to the noise...
		// Around the confusion limit, every beam pretty much has sources in it
		// So the sources make just a continuous bg, and add to the noise...
		int fromN = 1;
		while(dN(fromN * dS) == 0.0) fromN++;
		
		System.err.println(" no counts below " + Util.e2.format(fromN * dS) + " mJy");
			
		double nc = fromN;
		while(dN(nc * dS) > confusionLimit) nc++;
		System.err.println(" confused below " + Util.e2.format(nc * dS) + " mJy");
		
		if(nc >= toN) {
			System.err.println("WARNING! Counts yield no unconfused sources for input flux.");
			//System.exit(1);			
		}
		
		
		for(int i=fromN; i<=toN; i++) {
			double Sa = i * dS;
			double dN0a = dN(Sa) * dS;
				
			// Near the confusion limit sources appear as fewer individual ones on top of a noisy background...
			double pa = -Math.expm1(-dN0a * fromN / confusionLimit); // number of beams with source above background.
					
			double D = Sa - S1;
			double ea = Math.exp(A*D*D);
			double Pa = ea * pa;
			suma += Pa * Sa; 
			suma2 += Pa * Sa*Sa;
			sumb += Pa;				// This is the probability that the brightest component is has brightness Sa

			//System.out.println(Util.e3.format(Sa) + "\t" + Util.e3.format(Pa));
			
			// Add overlap fluxes...
			// Do not double count by counting just fainter
			
			if(order > 0) for(int j=fromN; j<=i; j++) {
				double Sb = j * dS;
				double dN0b = dN(Sb) * dS;
				double pb = -Math.expm1(-dN0b * fromN / confusionLimit); // number of beams with source above background.

				if(pb * Sb < 0.001*dS) break;

				double S2 = Sa + Sb;
				D = S2 - S1;
				double eb = Math.exp(A*D*D);
				double Pb = pb * Pa * (eb - ea); // Probability of flux arising from Sa+Sb and just Sa
				
				suma += Pb * Sb;
				// (P - Pb) * Sa^2 + Pb (Sa + Sb)^2 = PSa^2 + 2 Pb Sa Sb + Pb Sb^2 
				suma2 += Pb * Sb * (Sa + S2); 
				
				// Add double overlap.
				// Do not double count by counting just fainter
				if(order > 1) for(int k=fromN; k<=j; k++) {
					double Sc = k*dS;
					double dN0c = dN(Sc) * dS;
					double pc = Math.expm1(-dN0c * fromN / confusionLimit); // number of beams with source above background.

					if(pb * pc * Sc < 0.001*dS) break;

					double S3 = S2 + Sc;
					D = S3 - S1;
					double ec = Math.exp(A*D*D);
					double Pc = pb * Pb * (ec - eb); // probability of flux arising from Sa+Sb+Sc and not just Sa+Sb
					
					suma += Pc * Sc;
					suma2 += Pc * Sc * (S2 + S3);
				}
			}
		}

		double mean = suma / sumb;
		double var = suma2 / sumb - mean*mean;	
		
		WeightedPoint result = new WeightedPoint(mean, 1.0/var);
			
		return result;
	}
	
	
	// 2-mm to 850um ratio = 10.56
	// (assuming T/(1+z) = 10K, beta=1.5)
	double fluxRatio = 1.0 / 10.56;

	// 1.2mm to 850um ratio = 1.75
	//double fluxRatio = 1.0 / 1.75;

	// 850um
	//double fluxRatio = 1.0;


	public double dN(double S) {
		S /= fluxRatio;

		if(table != null) {
			// Lapi extension to below 100 uJy.
			if(S < 0.1) return 3.16e6 * Math.pow(S, -2.262) * Unit.sqdeg / Unit.rad2;
			
			// The table is per rad^2 per Jy vs. Jy fluxes.
			try { return 1e-3 * table.getValue(1e-3 * S) * Unit.sqdeg / Unit.rad2; }
			catch(Exception e) { 
				System.err.print("!");
				//System.err.println(Util.e3.format(S));
				return 0.0; 
			}
		}
		
		// LESS:
		//return S < 0.5 ? 0.0 : 150.0 * Math.pow(S/5.0, -3.2);

		// SHADES:
		return S < 0.5 ? 0.0 : 0.5*(189.0+136.0) * Math.pow(S/5.0, -2.95);

		//return 1600*Math.pow(S/3.3, -1.0) * Math.exp(-S/3.3);		
		//return 382.7 / (0.005 + Math.pow(S, 4.30));	
		//return 2800 * Math.pow(S, -2.39) * Math.exp(-0.62 * S);

		// 2-mm counts from Eli, (straightedned by eye)
		//return S < 0.01 ? 0.0 : 16.0 * Math.pow(S, -2.8);
	}

}
