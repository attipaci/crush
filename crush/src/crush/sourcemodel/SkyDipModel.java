/* *****************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
package crush.sourcemodel;


import java.util.*;

import crush.CRUSH;
import jnum.Configurator;
import jnum.Unit;
import jnum.Util;
import jnum.data.fitting.ChiSquared;
import jnum.data.fitting.DownhillSimplex;
import jnum.data.fitting.Minimizer;
import jnum.data.fitting.Parameter;
import jnum.math.ConvergenceException;
import jnum.math.Range;

// Simple sky dip:
//
// Tsky     sky temperature
// conv     temperature conversion
// C0       signal offset
// 
// Tobs = Tsky * (1-exp(-tau/sin(EL)))
// 
// C = C0 + conv * Tobs

public class SkyDipModel {
	Configurator options;

	Parameter Tsky = new Parameter("Tsky", 273.0 * Unit.K, 1.0 * Unit.K); // 0 C
	Parameter offset = new Parameter("offset");
	Parameter kelvin = new Parameter("kelvin");
	Parameter tau = new Parameter("tau", 1.0, new Range(0.0, 10.0));
	
	double frequency;

	boolean hasConverged = false;
	
	String dataUnit = "[dataunit]";

	int usePoints = 0;
	boolean uniformWeights = false;
	
	Range elRange;
	
	int attempts = 3;
	
	Minimizer minimizer;
	Vector<Parameter> parameters = new Vector<>();
	
	
	public void setOptions(Configurator options) {
		this.options = options;	
		
		if(options.hasOption("elrange")) {
			elRange = options.option("elrange").getRange(true);
			elRange.scale(Unit.deg);
		}
		
		if(options.hasOption("attempts")) {
		    attempts = options.option("attempts").getInt();
		}
		
		uniformWeights = options.hasOption("uniform");
		
		parameters.clear();
		
		if(options.hasOption("fit")) {
			List<String> names = options.option("fit").getList();
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
	
	protected void initParms(SkyDip skydip) {   
		if(options.hasOption("tsky")) Tsky.setValue(options.option("tsky").getDouble() * Unit.K);
		else if(skydip.Tamb.weight() > 0.0) Tsky.setValue(skydip.Tamb.value());
		
		Range signalRange = skydip.getSignalRange();
		
		// Set some reasonable initial values for the offset and conversion...
		if(Double.isNaN(offset.value())) offset.setValue(signalRange.midPoint());
		if(Double.isNaN(kelvin.value())) kelvin.setValue(signalRange.span() / Tsky.value());
		else {
		    Range amRange = skydip.getAirmassRange();
		    double x = signalRange.span() / (amRange.span() * Tsky.value() * kelvin.value());
		    if(x < 0.0) tau.setValue(0.1);
		    else if(x > 1.0) tau.setValue(1.0);
		    else {
		        tau.setValue(-Math.log1p(-x));
		        tau.setStepSize(0.03 * tau.value());
		    }
		}
		
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
		double sumdev = 0.0;
		for(int i=from; i<to; i++) if(skydip.data[i].weight() > 0.0) {
			final double dev = (valueAt(skydip.getEL(i)) - skydip.data[i].value());
			final double w = uniformWeights ? 1.0 : skydip.data[i].weight();
			sumdev += w * dev * dev;
		}
		return sumdev;
	}
	
	public void fit(final SkyDip skydip) { 
	    
	    if(CRUSH.debug) {
	        CRUSH.debug(this, "Initial skydip values: " + Tsky.value());
            CRUSH.debug(this, "    Tsky = " + Tsky.value());
            CRUSH.debug(this, "    offset = " + offset.value());
            CRUSH.debug(this, "    kelvin = " + kelvin.value());
            CRUSH.debug(this, "    tau = " + tau.value());
        }
	    
	    
	    int fromBin, toBin;
	    if(elRange != null) {
            fromBin = Math.max(0,  skydip.getBin(elRange.min()));
            toBin = Math.min(skydip.data.length, skydip.getBin(elRange.max()));
        }
        else { fromBin = 0; toBin = skydip.data.length; }
            
        usePoints = 0;
        for(int i=fromBin; i<toBin; i++) if(skydip.data[i].weight() > 0.0) usePoints++;
	   
        final int from = fromBin;
        final int to = toBin;
        
        ChiSquared chi2 = new ChiSquared() {
            @Override
            public Double evaluate() { return getDeviationFrom(skydip, from, to); } 
        };
        
        initParms(skydip);
        minimizer = new DownhillSimplex(chi2, parameters);
        hasConverged = false;
        
        try {
            minimizer.minimize(attempts); 
            hasConverged = true;
        }
        catch(ConvergenceException e) { skydip.warning("Skydip fit did not converge!"); }
        
		final int dof = usePoints - parameters.size();
		
		// Renormalize to chi2 = 1;
		if(dof > 0.0) {
			double rChi2 = minimizer.getMinimum() / dof;	
			
			for(int i=0; i<parameters.size(); i++) {
				final Parameter p = parameters.get(i);
				p.scaleWeight(1.0 / rChi2);
			}		
		}
		else for(Parameter p : parameters) p.setWeight(0.0);
		
	}
	
	@Override
	public String toString() {	
		if(!hasConverged) CRUSH.warning(this, "The fit has not converged. Try again!");
	
		StringBuffer text = new StringBuffer();
		
		if(parameters.contains(tau)) text.append("  " + tau.toString(Util.f3) + "\n");
		if(parameters.contains(Tsky)) text.append("  " + Tsky.toString(Util.f1) + " K" + "\n");
		if(parameters.contains(kelvin)) text.append("  " + kelvin.toString(Util.s3) + " " + dataUnit + "\n");

		text.append("\t\t\t\t[" + Util.s3.format(Math.sqrt(minimizer.getMinimum()) / kelvin.value()) + " K rms]\n");
		
		return new String(text);
	}

}
