/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.devel;

import java.io.IOException;

import jnum.Unit;
import jnum.Util;
import jnum.data.*;
import jnum.util.*;


public class SimpleDeboosting {
	static String fileName = "/home/pumukli/data/gismo/counts/dn_ds_beth.txt";
	//static String fileName = null;
	
	int order = 2;
	SimpleInterpolator table; 
	
	public static void main(String[] args) {

		SimpleDeboosting debooster = new SimpleDeboosting();		
		
		if(fileName != null) {
			try { 
				debooster.table = new SimpleInterpolator(fileName); 
				
				for(Interpolator.Data point : debooster.table) {
					point.ordinate /= 1e-3;
					point.value *= 1e-3 * Unit.sqdeg / Unit.rad2;
					point.value = Math.log(point.value);
					
					//System.err.println("> " + Util.f3.format(point.ordinate) + " : " + Util.f3.format(point.value));
				}
					
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
	

	public WeightedPoint deboost(double Sobs, double sigma, double beamFWHM) {		
		double suma = 0.0, suma2 = 0.0, sumb = 0.0;
		dS = 0.03 * sigma;
		double A = -0.5 / (sigma * sigma);
		double beamArea = 1.13 * beamFWHM * beamFWHM;	// beam-smoothed map
		double nBeams = Unit.deg2 / beamArea;
		double confusionLimit = nBeams;

		int toN = (int) Math.ceil((Sobs + 4.0 * sigma) / dS);
		
		System.err.println(" dS = " + Util.e2.format(dS) + " mJy");
		
		// Around the confusion limit, every beam pretty much has sources in it
		// So the sources make just a continuous bg, and add to the noise...
		// Around the confusion limit, every beam pretty much has sources in it
		// So the sources make just a continuous bg, and add to the noise...
		int fromN = 1;
		while(dN(fromN * dS) <= 0.0) fromN++;
		
		System.err.println(" no counts below " + Util.e2.format(fromN * dS) + " mJy");
			
		// First calculate probability that a beam
		// has an individual unconfused source in it at each level...
		double[] C = new double[toN + 1];
		int maxN = toN;
		while(dN(maxN * dS) * (maxN*dS) > 0.001 * confusionLimit) maxN *= 2;
			
		System.err.println("max: " + Util.e3.format(maxN * dS) + " (" + maxN + ")");
		
		boolean isConfused = false;
		double sumN = 0.0;
		for(int i=maxN; i >= fromN; i--) {
			sumN += dN(i * dS) * dS;
			if(!isConfused) if(sumN > confusionLimit) {
				System.err.println(" confused below " + Util.e2.format(i * dS) + " mJy");
				isConfused = true;
			}
			
			if(i <= toN) {
				C[i] = Math.exp(-sumN / confusionLimit);
				//System.err.println(Util.f5.format(i*dS) + ": " + Util.f5.format(C[i]));
			}
		}
		
		
		for( ; fromN < toN; fromN++) if(C[fromN] > 0.001) break;
		System.err.println(" completely confused below " + Util.e3.format(fromN * dS));
		
		
		if(C[toN] < 0.1) System.err.println("WARNING! Counts yield no unconfused sources for input flux.");	
		
		
		
		
		double[] P = new double[(order+1) * (toN+1)];
		
			
		
		for(int i=toN; i>=fromN; i--) {
			double Sa = i * dS;
			double dN0a = dN(Sa) * dS;
			
			// Near the confusion limit sources appear as fewer individual ones on top of a noisy background...
			double pa = C[i] * dN0a / nBeams; // probability of a beam with individual source.
					
			
			double D = Sa - Sobs;
			double ea = Math.exp(A*D*D);
			double Pa = ea * pa;
			suma += Pa * Sa; 
			suma2 += Pa * Sa*Sa;
			sumb += Pa;				// This is the probability that the brightest component is has brightness Sa

			P[i] += Pa;
			
			//System.out.println(Util.e3.format(Sa) + "\t" + Util.e3.format(Pa));
			
			// Add overlap fluxes...
			// Do not double count by counting just fainter
			
			
			if(order > 0) for(int j=i; j>=fromN; j--) {
				double Sb = j * dS;
				double dN0b = dN(Sb) * dS;
				double pb = C[j] * dN0b / nBeams; // probability of a beam with individual source.
				

				double S2 = Sa + Sb;
				D = S2 - Sobs;
				double eb = Math.exp(A*D*D);
				double Pb = (Pa * pb * eb); // Probability of flux arising from Sa+Sb and not just Sa
				
				// (Pa - Pb) * Sa + Pb (Sa + Sb) = [Pa Sa] + Pb Sb
				suma += Pb * Sb;
				
				// (Pa - Pb) * Sa^2 + Pb * (Sa+Sb)^2 = [Pa Sa^2] + 2 Pb Sa Sb + Pb Sb^2
				// = [Pa Sa^2] + Pb Sb (2 Sa + Sb) = [Pa Sa^2] + Pb Sb (Sa + S2)
				suma2 += Pb * Sb * (Sa + S2); 
				
				P[i] -= Pb;
				P[i+j] += Pb;
						
				
				// Add double overlap.
				// Do not double count by counting just fainter
				if(order > 1) for(int k=j; k>=fromN; k--) {
					double Sc = k*dS;
					double dN0c = dN(Sc) * dS;
					double pc = C[k] * dN0c / nBeams; // probability of a beam with individual source.

					double S3 = S2 + Sc;
					D = S3 - Sobs;
					double ec = Math.exp(A*D*D);
					double Pc = (Pb * pc * ec); // probability of flux arising from Sa+Sb+Sc and not just Sa+Sb
					
					suma += Pc * Sc;
					suma2 += Pc * Sc * (S2 + S3);
					
					P[i+j] -= Pc;
					P[i+j+k] += Pc;
				}
				
			}	
		}

		double mean = suma / sumb;
		double var = suma2 / sumb - mean*mean;	
			
		WeightedPoint result = new WeightedPoint(mean, 1.0/var);
			
		double sumP = 0.0;
		for(int i=1; i<P.length; i++) sumP += P[i];
		double norm = 1.0 / (sumP * dS);
		
		DataPoint S0 = new DataPoint(Sobs, sigma);
		
		System.out.println("# observed: " + S0.toString());
		System.out.println("# deboosted: " + result.toString(Util.getDecimalFormat(DataPoint.significanceOf(result))));
		System.out.println("# counts: " + fileName);
		System.out.println("# overlap order: " + order);
		System.out.println("#");
		System.out.println("# Si\tp");
		
		for(int i=1; i<P.length; i++) {
			System.out.println(Util.e3.format(i*dS) + "\t" + Util.e3.format(norm * P[i]));
		}
		
		
		return result;
	}
	
	double dS;

	
	// 2-mm to 850um ratio = 10.56
	// (assuming T/(1+z) = 10K, beta=1.5)
	//double fluxRatio = 1.0 / 10.56;

	// 1.2mm to 850um ratio = 1.75
	//double fluxRatio = 1.0 / 1.75;

	// in band...
	double fluxRatio = 1.0;


	public double dN(double S) {
		S /= fluxRatio;

		if(table != null) {
			
			// The table is per rad^2 per Jy vs. Jy fluxes.
			try { return Math.exp(table.getValue(S)); }
			catch(Exception e) { 
				System.err.print("!");
				
				// Lapi extension to below 100 uJy.
				//if(S < 0.1) return 3.16e6 * Math.pow(S, -2.262) * Unit.sqdeg / Unit.rad2;
				
				
				
				//System.err.println(Util.e3.format(S));
				//System.exit(1);
				
				return 0.0; 
			}
		}
		
		// LESS:
		//return S < 0.5 ? dN(0.5*fluxRatio) : 150.0 * Math.pow(S/5.0, -3.2);

		// SHADES single:
		//else return S < 0.5 ? dN(0.5*fluxRatio) : 0.5*(189.0+136.0) * Math.pow(S/5.0, -2.95);

		// SHADES broken:
		else return S < 0.5 ? dN(0.5*fluxRatio) : (735.0/9.0) / (Math.pow(S/9.0, 2.0) + Math.pow(S/9.0, 5.8));
		
		//return 1600*Math.pow(S/3.3, -1.0) * Math.exp(-S/3.3);		
		//return 382.7 / (0.005 + Math.pow(S, 4.30));	
		//return 2800 * Math.pow(S, -2.39) * Math.exp(-0.62 * S);

		// 2-mm counts from Eli, (straightedned by eye)
		//return S < 0.01 ? 0.0 : 16.0 * Math.pow(S, -2.8);
	}

}
