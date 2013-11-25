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
package crush.sourcemodel;


import java.util.*;

import kovacs.data.fitting.AmoebaMinimizer;
import kovacs.data.fitting.Parameter;
import kovacs.math.Range;
import kovacs.util.*;

// Simple sky dip:
//
// hot spillover, hot temperature
// sky temperature
// temperature conversion
// offset
// 
// Tobs = eta_h * Thot + (1-eta_h) * Tsky * (1-exp(-tau/sin(EL)))
// 
// C = C0 + conv * Tobs

public class SkyDipModel {
	Configurator options;

	Parameter Tsky = new Parameter("Tsky", 273.0 * Unit.K); // 0 C
	Parameter offset = new Parameter("offset");
	Parameter kelvin = new Parameter("kelvin");
	Parameter tau = new Parameter("tau", 0.3);

	String dataUnit = "[dataunit]";

	int usePoints = 0;
	double rmsDev = 1.0;
	boolean uniformWeights = false;
	boolean fitOK = false;
	
	Range elRange;
	
	Vector<Parameter> parameters = new Vector<Parameter>();
	
	public void setOptions(Configurator options) {
		this.options = options;	
		
		if(options.isConfigured("elrange")) {
			elRange = options.get("elrange").getRange(true);
			elRange.scale(Unit.deg);
		}
		
		uniformWeights = options.isConfigured("uniform");
		
		parameters.clear();
		
		if(options.isConfigured("fit")) {
			List<String> names = options.get("fit").getList();
			for(String name : names) {
				name = name.toLowerCase();
				
				if(name.equals("tau")) parameters.add(tau);
				else if(name.equals("offset")) parameters.add(offset);
				else if(name.equals("data2k")) parameters.add(kelvin);
				else if(name.equals("kelvin")) parameters.add(kelvin);
				else if(name.equals("tsky")) parameters.add(Tsky);
			}
		}
		else {
			parameters.add(tau);
			parameters.add(offset);
			parameters.add(kelvin);
		}
	}
	
	public double valueAt(double EL) {
		double eps = -Math.expm1(-tau.value() / Math.sin(EL));
		double Tobs = eps * Tsky.value();
		return offset.value() + Tobs * kelvin.value();
	}
	
	protected void initialize(AmoebaMinimizer minimizer, SkyDip skydip) {
		double lowest = Double.NaN;
		for(int i=0; i<skydip.data.length; i++) if(skydip.data[i].weight() > 0.0) {
			lowest = skydip.data[i].value();
			break;
		}
		double highest = Double.NaN;
		for(int i=skydip.data.length-1; i>=0; i--) if(skydip.data[i].weight() > 0.0) {
			highest = skydip.data[i].value();
			break;
		}
		
		if(options.isConfigured("tsky")) Tsky.setValue(options.get("tsky").getDouble() * Unit.K);
		else if(skydip.Tamb.weight() > 0.0) Tsky.setValue(skydip.Tamb.value());
			
		// Set some reasonable initial values for the offset and conversion...
		if(Double.isNaN(offset.value())) offset.setValue(highest);
		if(Double.isNaN(kelvin.value())) kelvin.setValue((lowest - highest) / Tsky.value());
		
		minimizer.verbose = true;
		minimizer.precision = 1e-10;
	
		minimizer.init(getParms());
		minimizer.fitAll();
	}

	
	public double[] getParms() {
		double[] parm = new double[parameters.size()];
		for(int p=0; p<parameters.size(); p++) parm[p] = parameters.get(p).value();
		return parm;
	}
	
	public void setParms(double[] tryparm) {
		for(int p=0; p<parameters.size(); p++) parameters.get(p).setValue(tryparm[p]);
	}
	
	public double getDeviationFrom(SkyDip skydip, int from, int to) {
		double sumdev = 0.0, sumw = 0.0;
		for(int i=from; i<to; i++) if(skydip.data[i].weight() > 0.0) {
			final double dev = (valueAt(skydip.getEL(i)) - skydip.data[i].value()) / rmsDev;
			final double w = uniformWeights ? 1.0 : skydip.data[i].weight();
			sumdev += w * dev * dev;
			sumw += w;
		}
		return sumdev / sumw;
	}

	
	public void fit(final SkyDip skydip) {
		
		AmoebaMinimizer minimizer = new AmoebaMinimizer() {
			int fromBin = 0;
			int toBin = skydip.data.length;
			
			@Override
			public void init(double[] p) {
				super.init(p);
				if(elRange != null) {
					fromBin = Math.max(0,  skydip.getBin(elRange.min()));
					toBin = Math.min(skydip.data.length, skydip.getBin(elRange.max()));
					usePoints = 0;
					for(int i=fromBin; i<toBin; i++) if(skydip.data[i].weight() > 0.0) usePoints++;
				}
			}
			
			@Override
			public double evaluate(double[] tryparm) {
				setParms(tryparm);
				return getDeviationFrom(skydip, fromBin, toBin);
			}
		};
		
		initialize(minimizer, skydip);
		minimizer.verbose = false;
		minimizer.retry = false;
		minimizer.maxSteps = 1000;
		minimizer.minimize(3);
		fitOK = minimizer.converged;

		final int dof = usePoints - parameters.size();
		
		if(dof > 0.0) {
			rmsDev = Math.sqrt(minimizer.getChi2() / dof);	
			minimizer.rescaleChi2(1.0 / (rmsDev * rmsDev));
			
			for(int i=0; i<parameters.size(); i++) {
				final Parameter p = parameters.get(i);
				final double rms = minimizer.getTotalError(i, 0.01 * p.value());
				p.setWeight(1.0 / (rms * rms));
			}		
		}
		else for(Parameter p : parameters) p.setWeight(0.0);
		
		double[] fitted = minimizer.getFitParameters();
		for(int i=0; i<fitted.length; i++) parameters.get(i).setValue(fitted[i]);
		
	}
	
	@Override
	public String toString() {	
		if(!fitOK) return "WARNING! The fit has not converged. Try again!";
	
		StringBuffer text = new StringBuffer();
		
		
		if(parameters.contains(tau)) text.append("  " + tau.toString(Util.f3) + "\n");
		if(parameters.contains(Tsky)) text.append("  " + Tsky.toString(Util.f1) + " K" + "\n");
		if(parameters.contains(kelvin)) text.append("  " + kelvin.toString(Util.s3) + " " + dataUnit + "\n");

		text.append("\t\t\t\t[" + Util.s3.format(rmsDev / kelvin.value()) + " K rms]\n");
		
		return new String(text);
	}

}
