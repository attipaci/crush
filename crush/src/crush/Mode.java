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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.lang.reflect.*;
import java.util.Arrays;

import kovacs.data.Statistics;
import kovacs.data.WeightedPoint;
import kovacs.math.Range;
import kovacs.util.ExtraMath;


public class Mode {
	public String name;
	private ChannelGroup<?> channels;
	public GainProvider gainProvider;

	public boolean fixedGains = false;
	public boolean phaseGains = false;
	public double resolution;
	public Range gainRange = Range.getFullRange();
	public int gainFlag = 0;
	public int gainType = Instrument.GAINS_BIDIRECTIONAL;

	private static int counter = 0;
	private float[] gain;
		
	public Mode() {
		name = "mode-" + (++counter);
	}

	public Mode(ChannelGroup<?> group) {
		this();
		setChannels(group);
	}
	
	public Mode(ChannelGroup<?> group, GainProvider gainProvider) { 
		this(group);
		setGainProvider(gainProvider);
	}
	
	public Mode(ChannelGroup<?> group, Field gainField) { 
		this(group, new FieldGainProvider(gainField));
	}
	
	
	public void setGainProvider(GainProvider source) {
		this.gainProvider = source;
	}	
	
	@Override
	public boolean equals(Object o) {
		if(!super.equals(o)) return false;
		Mode m = (Mode) o;
		return name.equals(m.name);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public String getName() { return name; }	
	
	public ChannelGroup<?> getChannels() { return channels; }
	
	public int getChannelCount() { return channels.size(); }
	
	public void setChannels(ChannelGroup<?> group) {
		channels = group;
		name = group.getName();
	}
	
	public Channel getChannel(int k) { return channels.get(k); }
	
	public int size() { return channels.size(); }
	
	public int[] getChannelIndex() {
		final int[] index = new int[channels.size()];
		for(int c=channels.size(); --c >= 0; ) index[c] = channels.get(c).index;
		return index;
	}
	
	public float[] getGains() throws Exception {
		return getGains(true);
	}
		
	public synchronized float[] getGains(boolean validate) throws Exception {
		if(gainProvider == null) {
			if(gain == null) {
				gain = new float[channels.size()];
				Arrays.fill(gain, 1.0F);
			}
			return gain;
		}
		else {
			if(validate) gainProvider.validate(this);
			if(gain == null) gain = new float[channels.size()];
			if(gain.length != channels.size()) gain = new float[channels.size()];
			for(int c=channels.size(); --c >= 0; ) gain[c] = (float) gainProvider.getGain(channels.get(c));
			return gain;
		}
	}
	
	public synchronized boolean setGains(float[] gain) throws Exception {
		return setGains(gain, true);
	}
	
	// Return true if flagging...
	public synchronized boolean setGains(float[] gain, boolean flagNormalized) throws Exception {
		if(gainProvider == null) this.gain = gain;
		else for(int c=channels.size(); --c>=0; ) gainProvider.setGain(channels.get(c), gain[c]);
		return flagGains(flagNormalized);
	}
		
	public void uniformGains() throws Exception {
		float[] G = new float[channels.size()];
		Arrays.fill(G, 1.0F);
		setGains(G, false);
	}
	
	protected synchronized boolean flagGains(boolean normalize) throws Exception {
		if(gainFlag == 0) return false;
			
		final float[] gain = getGains();
		final float aveG = normalize ? (float) getAverageGain(~gainFlag) : 1.0F;
		
		for(int k=channels.size(); --k >= 0; ) {
			final Channel channel = channels.get(k);
			
			float G = Float.NaN;
			if(gainType == Instrument.GAINS_SIGNED) G = gain[k] / aveG;
			else if(gainType == Instrument.GAINS_BIDIRECTIONAL) G = Math.abs(gain[k] / aveG);

			if(!gainRange.contains(G)) channel.flag(gainFlag);
			else channel.unflag(gainFlag);
		}
		return true;
	}	
	
	public double getAverageGain(int excludeFlag) throws Exception {
		return getAverageGain(getGains(), excludeFlag);
		
	}
	
	public double getAverageGain(float[] G, int excludeFlag) {
		final double[] values = new double[channels.size()];
		int n = 0;
		for(int k=channels.size(); --k >= 0; ) if(channels.get(k).isUnflagged(excludeFlag))
			values[n++] = Math.log(1.0 + Math.abs(G[k]));
		
		// Use a robust mean (with 10% tails) to calculate the average gain...
		double aveG = Statistics.robustMean(values, 0, n, 0.1);
		if(Double.isNaN(aveG)) return 1.0;
 
		return Math.exp(aveG) - 1.0;	
	}
	
	public WeightedPoint[] deriveGains(Integration<?, ?> integration, boolean isRobust) throws Exception {
		if(fixedGains) throw new IllegalStateException("WARNING! Cannot solve gains for fixed gain modes.");
		
		float[] G0 = getGains();
		
		WeightedPoint[] G = phaseGains && integration.isPhaseModulated() ?  
				((PhaseModulated) integration).getPhases().getGainIncrement(this) : 
				integration.getSignal(this).getGainIncrement(isRobust);
				
		for(int i=G0.length; --i >= 0; ) {
			if(G[i] != null) G[i].add(G0[i]);
			else G[i] = new WeightedPoint(G0[i], 0.0);
		}
		
		return G;		
	}
	
	protected void syncAllGains(Integration<?,?> integration, float[] sumwC2, boolean isTempReady) throws Exception {			
		integration.getSignal(this).syncGains(sumwC2, isTempReady);
		
		// Solve for the correlated phases also, if required
		if(integration.isPhaseModulated()) if(integration.hasOption("phases"))
			((PhaseModulated) integration).getPhases().syncGains(this);
	}
	
	public int getFrameResolution(Integration<?, ?> integration) {
		return integration.power2FramesFor(resolution/Math.sqrt(2.0));
	}
	
	public int signalLength(Integration<?, ?> integration) {
		return ExtraMath.roundupRatio(integration.size(), getFrameResolution(integration));
	}
	
	@Override
	public String toString() {
		String description = name + ":";
		for(Channel channel : channels) description += " " + channel.getFixedIndex();
		return description;
	}
	
	
	
	protected static int nextMode = 0;
	public final static int TOTAL_POWER = nextMode++;
	public final static int CHOPPED = nextMode++;
	
}
