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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.lang.reflect.*;

import util.data.WeightedPoint;


// Gains can be linked to a channel's field, or can be internal...

public class CorrelatedMode extends Mode {
	
	public boolean fixedSignal = false;
	public boolean solvePhases = false;
	public int skipChannels = ~0;
	public float[] usedGains;
	
	public CorrelatedMode() {}
	
	public CorrelatedMode(ChannelGroup<?> group) {
		super(group);
	}
	
	public CorrelatedMode(ChannelGroup<?> group, Field gainField) { 
		super(group, gainField);
	}
	
	// TODO How to solve the indexing of valid channels...
	public ChannelGroup<?> getValidChannels() {
		return channels.getChannels().discard(skipChannels);		
	}	
	
	public synchronized void normalizeGains(float[] gain, Signal signal, int normFlags) throws IllegalStateException {
		if(fixedGains) throw new IllegalStateException("Correlate mode '" + name + "' has non-adjustable gains.");
		double aveG = getAverageGain(gain, normFlags);
		for(int k=0; k<channels.size(); k++) gain[k] /= aveG;
		signal.scale(aveG);
	}
	
	public void updateSignal(Integration<?, ?> integration, boolean isRobust) {
		if(fixedSignal) throw new IllegalStateException("WARNING! Cannot decorrelate fixed signal modes.");
		try { integration.updateCorrelated(this, isRobust); }
		catch(IllegalAccessException e) { e.printStackTrace(); }
	}
		
	@Override
	public void validateGainIncrement(WeightedPoint[] dG, Signal signal) throws IllegalAccessException {
		super.validateGainIncrement(dG, signal);
		if(fixedSignal) return;
		
		float[] G = getGains();
		float[] G1 = new float[G.length];
		for(int i=G.length; --i >= 0; ) G1[i] = G[i] + (float) dG[i].value;
		normalizeGains(G1, signal, gainFlag);
		
		for(int i=G.length; --i >= 0; ) dG[i].value = G1[i] - G[i];
	}
	
	public void setUsedGains(float[] G) {
		// The used gains is an independent copy of the currently set values...
		if(usedGains == null) usedGains = new float[G.length];
		else if(usedGains.length != G.length) usedGains = new float[G.length];
		System.arraycopy(G, 0, usedGains, 0, G.length);
	}
	
	@Override
	public void setGains(float[] G) throws IllegalAccessException {
		setUsedGains(G);
		super.setGains(G);
	}
	
}
