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
	
	public synchronized void normalizeGains(float[] G, double norm) {
		for(int i=G.length; --i >= 0; ) G[i] /= norm;
	}
	
	public synchronized void normalizeGains(WeightedPoint[] G, double aveG) {
		final double norm = 1.0 / aveG;
		for(WeightedPoint gain : G) gain.scale(norm);
	}
	
	public synchronized void scaleSignals(Integration<?,?> integration, double aveG) {
		if(fixedGains) throw new IllegalStateException("Correlate mode '" + name + "' has non-adjustable gains.");
		
		Signal signal = integration.signals.get(this);
		signal.scale(aveG);
		
		PhaseSet phases = integration.getPhases();
		if(phases != null) phases.signals.get(this).scale(aveG);
	}
	
	public void updateSignal(Integration<?, ?> integration, boolean isRobust) {
		if(fixedSignal) throw new IllegalStateException("WARNING! Cannot decorrelate fixed signal modes.");
		try { 
			CorrelatedSignal signal = (CorrelatedSignal) integration.signals.get(this);
			if(signal == null) signal = new CorrelatedSignal(this, integration);
			signal.update(isRobust); 	
		}
		catch(IllegalAccessException e) { e.printStackTrace(); }
	}	
	
	@Override
	protected void syncGains(Integration<?, ?> integration, float[] sumwC2, boolean isTempReady) throws IllegalAccessException {		
		// Now do the actual synching to the samples
		super.syncGains(integration, sumwC2, isTempReady);
		
			
		/*
		 * TODO
		 * Gain renormalization is dangerous when other modes depend on the same gains
		 * but are not renormalized... A better way to do this is to keep track of
		 * dependent modes, and renormalize them all... 
		 * 
		// Renormalized the gains to be unity on average...
		double aveG = getAverageGain(fG, gainFlag);
		normalizeGains(fG, aveG);
		normalizeGains(G, aveG);
		
		// Re-scale the relevant signals to keep gain/signal products intact...
		// This scales both the fast signals and the phase signals...
		scaleSignals(integration, aveG);	
		
		// Mark these as the gains used for the sync...
		// This has to happen after the sync!!!
		integration.signals.get(this).setUsedGains(fG);
		if(phases != null) phases.signals.get(this).setUsedGains(fG);		
		*/

	}
		

}
