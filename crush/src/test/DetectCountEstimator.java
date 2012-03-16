package test;

import util.ConfidenceCalculator;
import util.Unit;

public class DetectCountEstimator {

	double fluxRatio = 1.0;
	
	public static void main(String[] args) {
		double area = 20 * Unit.arcmin * 20 * Unit.arcmin;
		double rms = 0.35; // mJy
		double from = 1.4; // mJy
		double to = 2.0; // mJy
		
		DetectCountEstimator estimator = new DetectCountEstimator();
		System.err.println(estimator.getCount(area, rms, from, to) / Unit.deg2);
		
	}
	
	// Flux limit in mJy, area in normal units.
	public double getCount(double area, double rms, double fluxlimit, double upperlim) {
		double dF = 0.1 * rms;
		
		double sum = 0.0;
		
		for(double F=0.0; F<upperlim; F += dF) {
			double dev = (fluxlimit - F) / rms;
			double completeness = ConfidenceCalculator.getProbabilityOutsideOf(dev);
			if(dev < 0.0) completeness = 1.0 - completeness;
			//System.err.println(("c(" + Util.f3.format(F / rms)) + ") = " + Util.f3.format(completeness));
			
			sum += completeness * dN(F);
		}
		
		return area * dF * sum;
	}
	
	public double dN(double S) {
		S /= fluxRatio;
		
		// LESS:
		//return S < 0.5 ? 0.0 : 150.0 * Math.pow(S/5.0, -3.2);
		
		// SHADES:
		//return S < 0.5 ? 0.0 : 0.5*(189.0+136.0)*Math.pow(S/5.0, -2.95);
		
		//return 1600*Math.pow(S/3.3, -1.0) * Math.exp(-S/3.3);		
		//return 382.7 / (0.005 + Math.pow(S, 4.30));	
		//return 2800 * Math.pow(S, -2.39) * Math.exp(-0.62 * S);
		
		
		// 2-mm counts from Eli, (straightedned by eye)
		return S < 0.01 ? 0.0 : 5 * 16.0 * Math.pow(S, -2.8);
	}

}
