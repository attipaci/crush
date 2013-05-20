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

import crush.Channel;
import crush.Frame;
import crush.PhaseOffsets;
import crush.PhaseSet;
import crush.array.SimplePixel;
import util.*;
import util.data.DataPoint;
import util.data.WeightedPoint;

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
	
	public WeightedPoint getCorrectedLROffset(PhaseSet phases, int i, Collection<APEXPixel> neighbours) {
		WeightedPoint base = new WeightedPoint();
		for(APEXPixel pixel : neighbours) {
			WeightedPoint bias = pixel.getRelativeOffset(phases, i);
			bias.scale(1.0 / pixel.gain);
			base.average(bias);
		}

		WeightedPoint value = getRelativeOffset(phases, i);
		value.scale(1.0 / gain);
		
		value.subtract(base);
		value.scale(gain);
		
		return value;
	}

	
	
	public WeightedPoint getRelativeOffset(PhaseSet phases, int i) {
		final WeightedPoint signal = phases.get(i).getValue(this);	
		final WeightedPoint base = phases.get(i-1).getValue(this);
		base.average(phases.get(i+1).getValue(this));
		signal.subtract(base);		
		return signal;
	}
	
	/*
	public void updateLROffset(PhaseSet phases) {
		if(LROffset == null) LROffset = new WeightedPoint();
		WeightedPoint increment = getLROffset(phases);
		if(increment.weight <= 0.0) return;
		
		for(PhaseOffsets offsets : phases) offsets.value[index] -= increment.value;
		
		LROffset.value += increment.value;
		LROffset.weight = increment.weight;
	}
	*/
	
	
	public WeightedPoint getLROffset(PhaseSet phases) {
		final WeightedPoint bias = new WeightedPoint();
		
		for(int i=phases.size()-1; --i > 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if((offsets.phase & Frame.CHOP_LEFT) != 0) bias.average(getRelativeOffset(phases, i));
		}
		
		return bias;
	}

	public WeightedPoint getCorrectedLROffset(PhaseSet phases, Collection<APEXPixel> neighbours) {
		final WeightedPoint bias = new DataPoint();
		
		for(int i=phases.size()-1; --i > 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if((offsets.phase & Frame.CHOP_LEFT) != 0) bias.average(getCorrectedLROffset(phases, i, neighbours));
		}
		
		return bias;
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
	
	public double getCorrectedLRChi2(PhaseSet phases, Collection<APEXPixel> neighbours, double mean) {	
		double chi2 = 0.0;
		int n = 0;
		for(int i=phases.size()-1; --i > 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if((offsets.phase & Frame.CHOP_LEFT) == 0) continue;

			WeightedPoint LR = getCorrectedLROffset(phases, i, neighbours);
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		
		return n > 1 ? chi2/(n-1) : Double.NaN;
	}
	
}
