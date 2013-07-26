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
import crush.PhaseOffsets;
import crush.PhaseSet;
import crush.array.SimplePixel;

public abstract class APEXPixel extends SimplePixel {
	public Vector2D fitsPosition;
	//public WeightedPoint LROffset;
	
	public APEXPixel(APEXArray<?> array, int backendIndex) {
		super(array, backendIndex);
	}
	
	@Override
	public Channel copy() {
		APEXPixel copy = (APEXPixel) super.copy();
		if(fitsPosition != null) copy.fitsPosition = (Vector2D) fitsPosition.clone();
		return copy;
	}
	

	public WeightedPoint getRelativeOffset(PhaseSet phases, int i) {
		int phase = phases.get(i).phase;
		
		final WeightedPoint signal = phases.get(i).getValue(this);		
		final WeightedPoint base = new WeightedPoint();
		
		if(phases.get(i-1).phase != phase) base.average(phases.get(i-1).getValue(this));
		if(phases.get(i+1).phase != phase) base.average(phases.get(i+1).getValue(this));
		
		signal.subtract(base);		
		return signal;
	}
	
	public WeightedPoint getCorrectedRelativeOffset(final PhaseSet phases, final int i, final Collection<APEXPixel> neighbours, final double[] G) {
		final WeightedPoint base = new WeightedPoint();
		
		for(final APEXPixel pixel : neighbours) if(!pixel.isFlagged()) if(pixel != this) {
			final WeightedPoint lr = pixel.getRelativeOffset(phases, i);
			if(G[pixel.index] == 0.0) continue;
			lr.scale(1.0 / G[pixel.index]);
			base.average(lr);
		}

		final WeightedPoint value = getRelativeOffset(phases, i);
		
		base.scale(G[this.index]);	
		value.subtract(base);
		
		return value;
	}

	
	/*
	@Override
	public void update(PhaseSet phases) {
		if(LROffset == null) LROffset = new WeightedPoint();
		WeightedPoint increment = getLRIncrement(phases);
		if(increment.weight() > 0.0) {
			for(PhaseOffsets offsets : phases) if(offsets.phase == Frame.CHOP_LEFT) offsets.value[index] -= increment.value();
			LROffset.add(increment.value());
			LROffset.setWeight(increment.weight());
		}
		phases.level(this);
	}
	*/
	
	public WeightedPoint getLROffset(PhaseSet phases) {
		final WeightedPoint lr = new WeightedPoint();
		
		for(int i=phases.size()-1; --i > 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if((offsets.phase & Frame.CHOP_LEFT) != 0) lr.average(getRelativeOffset(phases, i));
		}
		
		return lr;
	}

	
	public WeightedPoint getCorrectedLROffset(PhaseSet phases, Collection<APEXPixel> neighbours, double[] sourceGain) {
		final WeightedPoint lr = new DataPoint();
		
		for(int i=phases.size()-1; --i > 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if((offsets.phase & Frame.CHOP_LEFT) != 0) lr.average(getCorrectedRelativeOffset(phases, i, neighbours, sourceGain));
		}
		
		return lr;
	}

	
	
	public double getLRChi2(PhaseSet phases, double mean) {	
		double chi2 = 0.0;
		int n = 0;
		for(int i=phases.size()-1; --i > 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if((offsets.phase & Frame.CHOP_LEFT) == 0) continue;

			WeightedPoint LR = getRelativeOffset(phases, i);
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		
		return n > 1 ? chi2/(n-1) : Double.NaN;
	}

	public double getCorrectedLRChi2(PhaseSet phases, Collection<APEXPixel> neighbours, double mean, double[] sourceGain) {	
		double chi2 = 0.0;
		int n = 0;
		for(int i=phases.size()-1; --i > 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if((offsets.phase & Frame.CHOP_LEFT) == 0) continue;

			WeightedPoint LR = getCorrectedRelativeOffset(phases, i, neighbours, sourceGain);
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		
		return n > 1 ? chi2/(n-1) : Double.NaN;
	}

}
