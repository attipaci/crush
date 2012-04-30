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
import java.util.Collection;
import java.util.Vector;


// Gains can be linked to a channel's field, or can be internal...

public class CorrelatedMode extends Mode {
	
	public boolean fixedSignal = false;
	public boolean solvePhases = false;
	public int skipChannels = ~0;

	Vector<Spinoff> spinoffs;
	
	public CorrelatedMode() {}
	
	public CorrelatedMode(ChannelGroup<?> group) {
		super(group);
	}
	
	public CorrelatedMode(ChannelGroup<?> group, Field gainField) { 
		super(group, gainField);
	}
	
	private void addSpinoff(Spinoff m) {
		if(spinoffs == null) spinoffs = new Vector<Spinoff>();
		spinoffs.add(m);		
	}
	
	@Override
	public void setChannels(ChannelGroup<?> group) {
		super.setChannels(group);
		if(spinoffs != null) for(Spinoff mode : spinoffs) mode.setChannels(group);
	}
	
	// TODO How to solve the indexing of valid channels...
	public ChannelGroup<?> getValidChannels() {
		return getChannels().copyGroup().discard(skipChannels);		
	}	
		
	
	// TODO Gain normalization is not safe during reduction, it is meant solely
	// for use at the end of reduction, for creating a normalized gain set for writing.
	// To make it safe, it should properly rescale all dependent signals and gains...
	public synchronized void renormalizeGains(Collection<Integration<?,?>> integrations, int gainFlag) throws Exception {
		final float aveG = (float) getAverageGain(gainFlag);
		float[] G = getGains();
		
		for(int i=G.length; --i >= 0; ) G[i] /= aveG;
		
		for(Integration<?,?> integration : integrations) scaleSignals(integration, aveG);
	}

	
	public synchronized void scaleSignals(Integration<?,?> integration, double aveG) {
		if(fixedGains) throw new IllegalStateException("Correlate mode '" + name + "' has non-adjustable gains.");
		
		Signal signal = integration.signals.get(this);
		signal.scale(aveG);
		
		PhaseSet phases = integration.getPhases();
		if(phases != null) {
			PhaseSignal pSignal = phases.signals.get(this);
			if(pSignal != null) pSignal.scale(aveG);
		}
	}
	
	public synchronized void updateSignals(Integration<?, ?> integration, boolean isRobust) throws Exception {
		if(fixedSignal) throw new IllegalStateException("WARNING! Cannot decorrelate fixed signal modes.");

		CorrelatedSignal signal = (CorrelatedSignal) integration.signals.get(this);
		if(signal == null) signal = new CorrelatedSignal(this, integration);
		signal.update(isRobust); 	

		// Solve for the correlated phases also, if required
		if(integration.isPhaseModulated()) if(integration.hasOption("phases") || solvePhases) {
			PhaseSignal pSignal = integration.getPhases().signals.get(this);
			if(pSignal == null) pSignal = new PhaseSignal(integration.getPhases(), this);
			pSignal.update(isRobust);
		}
	}	
	
	@Override
	protected void syncAllGains(Integration<?,?> integration, float[] sumwC2, boolean isTempReady) throws Exception {			
		super.syncAllGains(integration, sumwC2, isTempReady);
		
		// Sync the gains to all the dependent modes too... 
		if(spinoffs != null) for(Spinoff mode : spinoffs) {
			Signal signal = integration.signals.get(mode);
			if(signal != null) signal.resyncGains();
		}
	}


	public class Spinoff extends CorrelatedMode {
		
		public Spinoff() {
			super(CorrelatedMode.this.getChannels());
			fixedGains = true;
			addSpinoff(this);
		}
		
		public Spinoff(float[] gains) throws Exception {
			this();
			super.setGains(gains);
		}
		
		public Spinoff(Field gainField) {
			this();
			setGainProvider(new FieldGainProvider(gainField));
		}
		
		public Spinoff(GainProvider gains) { 
			this();
			setGainProvider(gains);
		}
			
		@Override
		public synchronized float[] getGains() throws Exception {
			final float[] parentgains = CorrelatedMode.this.getGains();
			final float[] gain = super.getGains();
			for(int i=gain.length; --i>=0; ) gain[i] *= parentgains[i];
			return gain;
		}
		
		@Override
		public synchronized boolean setGains(float[] gain) throws IllegalAccessException {
			throw new UnsupportedOperationException("Cannot adjust gains in Spinoff Modes.");
		}
	}

	
}
