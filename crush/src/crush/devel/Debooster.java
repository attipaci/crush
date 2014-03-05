package crush.devel;

import kovacs.data.*;

public class Debooster {
	double dS;

	// TODO single argument or table of fluxes...
	
	
	// TODO -> Configurator
	// 	
	//  order					- overlap order				- 
	//  beam					- beam FWHM in arcsec
	//  table
	//		table.fluxUnit	
	//		table.areaUnit		- e.g. deg^2 or sr
	//		table.intergral		- if integral counts
	//		table.below			- model name to to use below table
	//		table.above			- model name to to use above table
	//  fluxUnit
	//	col.flux				- The flux column or 'name' if deboosting a table
	//  col.dflux				- uncertainty column or 'name' if deboosting a table
	//  col.id					- id column or 'name'
	//  scaleflux				- scale input fluxes by this to use with counts
	//  scalecounts				-
	//  wavelength				- scale some known counts to this wavelengths (assume Td=35K)
	//  wavelength.model		- the model wavelenths.
	//  load					- e.g. SHADES1, SHADES2, LESS...
	//							  survey models to be stored in <survey.dat>, e.g. 'SHADES1.dat'
	//							  with entries like 'fluxUnit', 'areaUnit', 'wavelength.model', 'powerlaw.N0 = X.XX' 
	//  model					- e.g. powerlaw, broken, schechter...
	//  	powerlaw.N0
	//      powerlaw.S0			- model-dependent parms
	//  cutoff					- a low-flux cutoff value
	//  cutoff.type 			- 'flux' or 'average'
	//	cutoff.shape			- cliff, flat, taper, or flattenning 
	//  grid					- the numerical grid size to use
	
	// TODO use tables with log counts
	
	// TODO Precalculate resolved likelihood. (If index > storage, assume resolved likelihood is 1...
	
	
	// TODO Use proper units (Jy, mJy, deg^2, sr...)
	public double getdNdS(double S);
	

	
	// TODO deboost as a function of source flux/uncertainty and beam size...
	
	
	void addOverlap(double Sobs, int i0, double p0, WeightedPoint sum, double[] P) {
		double S0 = i0 * dS;
		
		for(int k=i; k>=fromN; k--) {
			double S1 = k*dS;
			double dN1 = dN(S1) * dS;
			double P1 = C[k] * dN1 / nBeams; // probability of a beam with individual source.

			double S = S0 + S1;
			double D = S - Sobs;
			double e = Math.exp(A*D*D);
			double p1 = e * P1 * p0; // probability of flux arising from Sa+Sb+Sc and not just Sa+Sb
		
			sum.add(p1 * S1);
			sum.addWeight(p1 * S1 * (S0 + S));
		
		if(i0 > 0) P[i] -= p1;
		P[i+k] += p1;
	
	}
	
}
