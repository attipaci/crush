/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import util.*;
import util.data.AmoebaMinimizer;

import java.util.*;

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
	Parameter Kelvin = new Parameter("Kelvin");
	Parameter tau = new Parameter("tau", 1.0);

	double chi2Scale = 1.0;
	boolean fitOK = false;
	
	Vector<Parameter> parameters = new Vector<Parameter>();
	
	public void setOptions(Configurator options) {
		this.options = options;	
		
		parameters.clear();
		
		if(options.isConfigured("fit")) {
			List<String> names = options.get("fit").getList();
			for(String name : names) {
				name = name.toLowerCase();
				
				if(name.equals("tau")) parameters.add(tau);
				else if(name.equals("offset")) parameters.add(offset);
				else if(name.equals("data2k")) parameters.add(Kelvin);
				else if(name.equals("tsky")) parameters.add(Tsky);
			}
		}
		else {
			parameters.add(tau);
			parameters.add(offset);
			parameters.add(Kelvin);
		}
	}
	
	public double valueAt(double EL) {
		double eps = -Math.expm1(-tau.value / Math.sin(EL));
		double Tobs = eps * Tsky.value;
		return offset.value + Tobs * Kelvin.value;
	}
	
	protected void initialize(AmoebaMinimizer minimizer, SkyDip skydip) {
		double lowest = Double.NaN;
		for(int i=0; i<skydip.data.length; i++) if(skydip.data[i].weight > 0.0) {
			lowest = skydip.data[i].value;
			break;
		}
		double highest = Double.NaN;
		for(int i=skydip.data.length-1; i>=0; i--) if(skydip.data[i].weight > 0.0) {
			highest = skydip.data[i].value;
			break;
		}
		
		if(options.isConfigured("tsky")) Tsky.value = options.get("tsky").getDouble() * Unit.K;
		else if(skydip.Tsky.weight > 0.0) Tsky.value = skydip.Tsky.value;
		
		// Set some reasonable initial values for the offset and conversion...
		if(Double.isNaN(offset.value)) offset.value = highest;
		if(Double.isNaN(Kelvin.value)) Kelvin.value = (lowest - highest) / Tsky.value;
		
		minimizer.verbose = true;
		minimizer.precision = 1e-10;
	
		minimizer.init(getParms());
		minimizer.fitAll();
	}

	
	public double[] getParms() {
		double[] parm = new double[parameters.size()];
		for(int p=0; p<parameters.size(); p++) parm[p] = parameters.get(p).value;
		return parm;
	}
	
	public void setParms(double[] tryparm) {
		for(int p=0; p<parameters.size(); p++) parameters.get(p).value = tryparm[p];
	}
	
	public double getDeviationFrom(SkyDip skydip) {
		double sumdev = 0.0;
		for(int i=0; i<skydip.data.length; i++) if(skydip.data[i].weight > 0.0) {
			double EL = skydip.getEL(i);
			double w = skydip.data[i].weight;
			double dev = (valueAt(EL) - skydip.data[i].value);
			sumdev += w * Math.abs(dev * dev) * chi2Scale;
		}
		return sumdev;
	}

	
	public void fit(final SkyDip skydip) {
		AmoebaMinimizer minimizer = new AmoebaMinimizer() {
			@Override
			public double evaluate(double[] tryparm) {
				setParms(tryparm);
				return getDeviationFrom(skydip);
			}
		};
		
		initialize(minimizer, skydip);
		minimizer.verbose = false;
		minimizer.minimize();
		fitOK = minimizer.converged;
		
		
		int dof = 0;
		for(int i=0; i<skydip.data.length; i++) if(skydip.data[i].weight > 0.0) dof++;
		
		if(dof > parameters.size()) {
			chi2Scale = (dof - parameters.size()) / minimizer.getChi2();	
			minimizer.rescaleChi2(chi2Scale);
		
			for(int i=0; i<parameters.size(); i++) {
				Parameter p = parameters.get(i);
				double rms = minimizer.getTotalError(i, 0.01 * p.value);
				p.weight = 1.0 / (rms * rms);
			}
		}
		else for(Parameter p : parameters) p.weight = 0.0;
	}
	
	@Override
	public String toString() {
		String text = tau.toString(Util.f3) + "\n" +
			Tsky.toString(Util.f1) + " K" + "\n" +
			Kelvin.toString(Util.e3) + " [dataunit]";
		return text;
	}
}
