package crush.tools;

import java.io.IOException;

import kovacs.util.*;
import kovacs.util.data.*;


public class SimpleDeboosting {
	//static String fileName = "dn_ds_lapi.txt";
	static String fileName = null;
	
	int order = 1;
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
		double beamFWHM = Math.sqrt(2.0) * 17.2 * Unit.arcsec; // beam-smoothed...
		
		System.err.println(" Deboosted: " + debooster.deboost(S1, sigma, beamFWHM).toString(Util.s3));
	}
	

	public WeightedPoint deboost(double S1, double sigma, double beamFWHM) {		
		double suma = 0.0, suma2 = 0.0, sumb = 0.0;
		double dS = 0.01 * sigma;
		double A = -0.5 / (sigma * sigma);
		double beamArea = 1.13 * beamFWHM * beamFWHM;	// beam-smoothed map
		double confusionLimit = Unit.deg2 / beamArea;
		
		int toN = (int) Math.ceil((S1 + 4.0*sigma) / dS);

		// Around the confusion limit, every beam pretty much has sources in it
		// So the sources make just a continuous bg, and add to the noise...
		int fromN = (int) Math.ceil(3.0 * S1 / dS);
		double N = 0.0;
		while(N < confusionLimit && fromN > 0) N += dN((fromN--) * dS) * dS;
		
		System.err.println("# from = " + Util.s3.format(fromN * dS));
		System.err.println("# to = " + Util.s3.format(toN * dS));
		
		if(fromN >= toN) {
			System.err.println("ERROR! Counts yield no unconfused sources for input flux.");
			System.exit(1);			
		}
		
		for(int i=fromN; i<=toN; i++) {
			double S = i * dS;
			double dN0 = dN(S) * dS;

			// Near the confusion limit sources appear as fewer individual ones on top of a noisy background...
			double dN1 = -confusionLimit * Math.expm1(-dN0 / confusionLimit);
			
			double D = S - S1;
			double e = Math.exp(A*D*D);
			double P = e * dN1;
			suma += P * S; 
			suma2 += P * S*S;
			sumb += P;

			// Add overlap fluxes...
			if(order > 0) for(int j=fromN; j<=i; j++) {
				double Sb = j * dS;
				double dN0b = dN(Sb) * dS;
				double pb = -Math.expm1(-dN0b / confusionLimit);

				if(pb * Sb < 0.001*dS) break;

				double S2 = S + Sb;
				D = S2 - S1;
				double eb = Math.exp(A*D*D);
				double Pb = pb * P * (eb - e);
				suma += Pb * Sb;
				suma2 += Pb * Sb * Sb;
				sumb += Pb;
				
				// Add double overlap.
				if(order > 1) for(int k=fromN; k<=j; k++) {
					double Sc = k*dS;
					double dN0c = dN(Sc) * dS;
					double pc = -Math.expm1(-dN0c / confusionLimit);

					if(pb * pc * Sc < 0.001*dS) break;

					double S3 = S2 + Sc;
					D = S3 - S1;
					double ec = Math.exp(A*D*D);
					double Pc = pb * pc * P * (ec - eb);
					suma += Pc * Sc;
					suma2 += Pc * Sc * Sc;
					sumb += Pc;
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
	
	
	public double dN(double S) {
		if(table != null) {
			try { return table.getValue(S) * Unit.deg2 / Unit.sr; }
			catch(ArrayIndexOutOfBoundsException e) {
				return 0.0; 
				//return Math.pow(10.0, -2.25 * Math.log10(S) + 2.78);
			}
		}
		
		S /= fluxRatio;
		
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
