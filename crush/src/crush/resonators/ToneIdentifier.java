/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.resonators;

import java.util.ArrayList;

import crush.CRUSH;
import jnum.Configurator;
import jnum.Util;
import jnum.data.fitting.ChiSquared;
import jnum.data.fitting.DownhillSimplex;
import jnum.data.fitting.Parameter;
import jnum.math.Range;

public abstract class ToneIdentifier<IDType extends FrequencyID> extends ArrayList<IDType> implements Cloneable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1708364254294475662L;
	
	public Range deltaRange;
	
	public int attempts = 100;
	public double rchi;
	public double maxDeviation = 3.0;
	public double power = 1.0;
	
	public ToneIdentifier() { 
		deltaRange = getDefaultShiftRange();		
	}
	
	public ToneIdentifier(Configurator options) {
		this();
		if(options.isConfigured("power")) power = options.get("power").getDouble();
		if(options.isConfigured("max")) maxDeviation = options.get("max").getDouble();
		if(options.isConfigured("attempts")) attempts = options.get("attempts").getInt();
		if(options.isConfigured("deltarange")) deltaRange = options.get("deltarange").getRange();
	}
		
	public Range getShiftRange() { return deltaRange; }
	
	public abstract Range getDefaultShiftRange();
		
	
	
	public final double match(final ResonatorList<?> resonators) {
		Range deltaRange = getShiftRange();
		return match(resonators, 0.01 * deltaRange.span());
	}
	
	public double match(ResonatorList<?> resonators, double initShift) {
		double shift = fit(resonators, initShift);
		assign(resonators, initShift);
		return shift;
	}
	
	protected double fit(final ResonatorList<?> resonators, double initShift) {
		resonators.sort();
			
		final Range deltaRange = getShiftRange();
		final double maxSearchDev = maxDeviation * 0.5 * deltaRange.span();
		
		final Parameter delta = new Parameter("delta", initShift, deltaRange);
		
		ChiSquared chi2 = new ChiSquared() {
            @Override
            public Double evaluate() {
                double chi2 = 0.0;
                int n = 0;
                
                for(FrequencyID id : ToneIdentifier.this) {
                    double fExp = id.getExpectedFrequencyFor(delta.value());
                    double dev = (resonators.getNearest(fExp).getFrequency() - fExp) / fExp;
                    if(Math.abs(dev) < maxSearchDev) {
                        chi2 += Math.pow(Math.abs(dev), power);
                        n++;
                    }
                }   
                return chi2 / n;
            }
		};    
		
		DownhillSimplex minimizer = new DownhillSimplex(chi2, new Parameter[] { delta });
		minimizer.minimize();
		
		rchi = Math.pow(minimizer.getMinimum(), 1.0 / power);
		CRUSH.info(this, "Tone assignment rms = " + Util.s3.format(1e6 * rchi) + " ppm.");
		
		return delta.value();
	}
	
	protected void assign(ResonatorList<?> resonators, double shift) {
		
		for(Resonator resonator : resonators) {
			resonator.setFrequencyID(null);
			resonator.flagID();
		}
		
		int n = assign(resonators, shift, 5);
		CRUSH.info(this, "Identified " + n + " resonances.");
	}
	
	private int assign(ResonatorList<?> resonators, double shift, int rounds) {
		if(rounds == 0) return 0;
		if(resonators.isEmpty()) return 0; 
	
		int ids = 0;
		
		final double maxShift = rchi * maxDeviation;
		
		for(FrequencyID id : this) {
			double fExp = id.getExpectedFrequencyFor(shift);
			Resonator resonator = resonators.getNearest(fExp);
			double adf = Math.abs(resonator.getFrequency() - fExp);
			
			// If the nearest tone is too far, then do not assign...
			if(adf > maxShift * fExp) continue;
			
			// If there is a better existing id, then leave it...
			if(resonator.getFrequencyID() == null) ids++;
			else if(Math.abs(resonator.getFrequency() - resonator.getFrequencyID().getExpectedFrequencyFor(shift)) < adf) continue;
			
			resonator.unflagID();
			resonator.setFrequencyID(id);
		}
		
		ResonatorList<Resonator> remaining = new ResonatorList<Resonator>(resonators.size());
		
		ToneIdentifier<?> extraIDs = (ToneIdentifier<?>) clone();
		
		for(int i=0; i<resonators.size(); i++) {
			Resonator resonator = resonators.get(i);
			if(resonator.getFrequencyID() == null) remaining.add(resonator);
			else extraIDs.remove(resonator.getFrequencyID());
		}
		
		
		//CRUSH.info(this, "     + " + ids + " resonances.");
		
		return ids + extraIDs.assign(remaining, shift, rounds-1);
	}
	
	
	public int indexBefore(double f) throws ArrayIndexOutOfBoundsException {
		int i = 0;
		int step = size() >> 1;

		
		if(get(0).freq > f) 
			throw new ArrayIndexOutOfBoundsException("Specified point precedes lookup range.");
		
		if(get(size() - 1).freq < f) 
			throw new ArrayIndexOutOfBoundsException("Specified point is beyond lookup range.");
		
		
		while(step > 0) {
			if(get(i + step).freq < f) i += step;
			step >>= 1;
		}
			
		return i;
	}
	
	
	public int getNearestIndex(double f) {	
		try {
			int lower = indexBefore(f);		
			int upper = lower+1;
		
			if(lower < 0) return upper;
			
			if(f - get(lower).freq < get(upper).freq - f) return lower;
			return upper;
		}
		catch(ArrayIndexOutOfBoundsException e) {
			if(f < get(0).freq) return 0;
			return size() - 1;
		}
	}
	
	
}
