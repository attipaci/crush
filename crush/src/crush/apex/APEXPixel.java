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
// Copyright (c) 2010 Attila Kovacs 

package crush.apex;

import java.util.Collection;

import kovacs.data.DataPoint;
import kovacs.data.WeightedPoint;
import kovacs.math.Vector2D;

import crush.Channel;
import crush.Frame;
import crush.PhaseData;
import crush.PhaseSet;
import crush.PhaseWeighting;
import crush.array.SimplePixel;

public abstract class APEXPixel extends SimplePixel implements PhaseWeighting {
	public Vector2D fitsPosition;
	public double phaseWeight = 1.0;
	
	public APEXPixel(APEXArray<?> array, int backendIndex) {
		super(array, backendIndex);
	}
	
	@Override
	public Channel copy() {
		APEXPixel copy = (APEXPixel) super.copy();
		if(fitsPosition != null) copy.fitsPosition = (Vector2D) fitsPosition.clone();
		return copy;
	}
	

	public void derivePhaseWeights(PhaseSet phases) {	
		//double chi2 = Math.max(1.0, getLRChi2(phases, getLROffset(phases).value()));
		//double chi2 = getLRChi2(phases, getLROffset(phases).value());
		//phaseWeight /= chi2;
		//for(PhaseData offsets : phases) offsets.weight[this.index] /= chi2;
	}
	
	public double getPhaseWeight() { return phaseWeight; }
	
	
	public WeightedPoint getRelativeOffset(final PhaseSet phases, final int i) {
		int phase = phases.get(i).phase;
		
		final WeightedPoint signal = phases.get(i).getValue(this);		
			
		if(phases.get(i-1).phase != phase) signal.subtract(phases.get(i-1).getValue(this));
		else signal.setWeight(0.0);
		
		/*
		final WeightedPoint base = new WeightedPoint();
		if(phases.get(i-1).phase != phase) base.average(phases.get(i-1).getValue(this));
		if(phases.get(i+1).phase != phase) base.average(phases.get(i+1).getValue(this));
		signal.subtract(base);	
		*/
		
		if((phase & Frame.CHOP_LEFT) == 0) signal.scale(-1.0);
		return signal;
	}
	
	
	public WeightedPoint getCorrectedRelativeOffset(final PhaseSet phases, final int i, final Collection<APEXPixel> bgPixels, final double[] G) {
		final WeightedPoint bg = new WeightedPoint();
		
		for(final APEXPixel pixel : bgPixels) if(!pixel.isFlagged()) if(pixel != this) {
			final WeightedPoint lr = pixel.getRelativeOffset(phases, i);
			if(G[pixel.index] == 0.0) continue;
			lr.scale(1.0 / G[pixel.index]);
			bg.average(lr);
		}

		final WeightedPoint value = getRelativeOffset(phases, i);
		
		bg.scale(G[this.index]);	
		value.subtract(bg);
		
		return value;
	}

	public WeightedPoint getLROffset(final PhaseSet phases) {
		final WeightedPoint lr = new WeightedPoint();
		for(int i=phases.size()-1; i > 0; i-=2) lr.average(getRelativeOffset(phases, i));
		//lr.scaleWeight(0.5);
		return lr;
	}
	
	public WeightedPoint getCorrectedLROffset(final PhaseSet phases, final Collection<APEXPixel> bgPixels, final double[] sourceGain) {
		final WeightedPoint lr = new DataPoint();
		for(int i=phases.size()-1; i > 0; i-=2) lr.average(getCorrectedRelativeOffset(phases, i, bgPixels, sourceGain));
		//lr.scaleWeight(0.5);
		return lr;
	}

	
	public double getLRChi2(final PhaseSet phases, final double mean) {	
		double chi2 = 0.0;
		int n = 0;
		for(int i=phases.size()-1; i > 0; i-=2) {
			WeightedPoint LR = getRelativeOffset(phases, i);
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		
		double dof = n * (1.0 - (double) phases.driftParms / phases.size());
		dof = Math.min(dof, phases.size() - 1);
		
		return dof > 0.0 ? chi2/dof : Double.NaN;
	}

	public double getCorrectedLRChi2(final PhaseSet phases, final Collection<APEXPixel> bgPixels, final double mean, final double[] sourceGain) {	
		double chi2 = 0.0;
		int n = 0;
		for(int i=phases.size()-1; i > 0; i-=2) {
			WeightedPoint LR = getCorrectedRelativeOffset(phases, i, bgPixels, sourceGain);
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		double dof = n * (1.0 - (double) phases.driftParms / phases.size());
		dof = Math.min(dof, phases.size() - 1);
		
		return dof > 0.0 ? chi2/dof : Double.NaN;
	}

}
