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
import java.util.Arrays;

import util.Range;
import util.Util;
import util.data.Statistics;
import util.data.WeightedPoint;

public class Mode {
	public String name;
	public ChannelGroup<?> channels;
	public Field gainField;

	public boolean fixedGains = false;
	public double resolution;
	public Range gainRange = new Range();
	public int gainFlag = 0;
	public int gainType = Instrument.GAINS_BIDIRECTIONAL;

	private static int counter = 0;
	private float[] gain;
	
	// Compressing the dynamical range of gains helps arrive at the better 'mean' value
	// when the gains are scattered...
	public double dynamicalCompression = 1.0; 
	
	// Calculating an offset average (under compression) can mitigate the
	// biasing effect of many nearly blind detectors... 
	public double gainAveragingOffset = 0.0;
	
		
	public Mode() {
		name = "mode-" + (++counter);
		gainRange.fullRange();
	}

	public Mode(ChannelGroup<?> group) {
		this();
		setChannels(group);
	}
	
	public Mode(ChannelGroup<?> group, Field gainField) { 
		this();
		setGainField(gainField);
		setChannels(group);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public void setGainField(Field f) {
		gainField = f;
	}	
	
	public void setChannels(ChannelGroup<?> group) {
		channels = group;
		name = group.name;
	}
	
	public synchronized float[] getGains() throws IllegalAccessException {
		if(gainField == null) {
			if(gain == null) {
				gain = new float[channels.size()];
				Arrays.fill(gain, 1.0F);
			}
			return gain;
		}
		else {
			if(gain == null) gain = new float[channels.size()];
			if(gain.length != channels.size()) gain = new float[channels.size()];
			for(int c=channels.size(); --c >= 0; ) gain[c] = (float) gainField.getDouble(channels.get(c));
			return gain;
		}
	}
	
	public synchronized void setGains(float[] gain) throws IllegalAccessException { 
		if(gainField == null) this.gain = gain;
		else updateFields(gain);
		flagGains();
	}
	
	public void incrementGains(WeightedPoint[] dG) throws IllegalAccessException { 
		float[] g = getGains();
		for(int i=dG.length; --i >= 0; ) g[i] += dG[i].value;
		setGains(g);
	}
	
	public synchronized void updateFields(float[] gain) throws IllegalAccessException { 
		if(gainField == null) return;
		Class<?> fieldClass = gainField.getClass();
		if(fieldClass.equals(float.class)) for(int c=channels.size(); --c >=0; ) gainField.setFloat(channels.get(c), gain[c]);
		else for(int c=channels.size(); --c>=0; ) gainField.setDouble(channels.get(c), gain[c]);
	}		
	
	protected synchronized void flagGains() throws IllegalAccessException {
		if(gainFlag == 0) return;
		
		float[] gain = getGains();
		
		for(int k=channels.size(); --k >= 0; ) {
			Channel pixel = channels.get(k);
			
			float G = Float.NaN;
			if(gainType == Instrument.GAINS_SIGNED) G = gain[k];
			else if(gainType == Instrument.GAINS_BIDIRECTIONAL) G = Math.abs(gain[k]);

			if(!gainRange.contains(G)) pixel.flag(gainFlag);
			else pixel.unflag(gainFlag);
		}
	}
	
	public double getAverageGain(float[] gain, int gainFlag) {
		double[] value = new double[channels.size()];
		int n = 0;
		for(int k=channels.size(); --k >= 0; ) if(channels.get(k).isUnflagged(gainFlag)) 
			value[n++] = Math.pow(gainAveragingOffset + Math.abs(gain[k]), dynamicalCompression);
		
		// Use a robust mean (with 10% tails) to calculate the average gain...
		double aveG = Statistics.robustMean(value, 0, n, 0.1);
		if(Double.isNaN(aveG)) return 1.0;
 
		return Math.pow(aveG, 1.0/dynamicalCompression) - gainAveragingOffset;	
	}
	
	public WeightedPoint[] getGains(Integration<?, ?> integration, boolean isRobust) throws IllegalAccessException {
		WeightedPoint[] G = getGainIncrement(integration, isRobust);
		float[] G0 = getGains();
		for(int i=G0.length; --i >= 0; ) if(G[i] != null) G[i].value += G0[i];
		return G;
	}
	
	
	public WeightedPoint[] getGainIncrement(Integration<?, ?> integration, boolean isRobust) throws IllegalAccessException {
		if(fixedGains) throw new IllegalStateException("WARNING! Cannot solve gains for fixed gain modes.");
		
		final int nc = channels.size();					
		final Signal signal = integration.signals.get(this);
			
		if(signal == null) return null;
		//throw new IllegalStateException("No such correlated mode.");

		if(integration.hasOption("signal-response")) 
			integration.comments += "{" + Util.f2.format(integration.getCovariance(this, signal)) + "}";

		// Precalculate the gain-weight products...
		for(final Frame exposure : integration) if(exposure != null) {
			exposure.tempC = signal.valueAt(exposure);
			if(Float.isNaN(exposure.tempC)) exposure.tempC = 0.0F;
			exposure.tempWC = exposure.isUnflagged(Frame.MODELING_FLAGS) ? exposure.relativeWeight * exposure.tempC : 0.0F;
			exposure.tempWC2 = exposure.tempWC * exposure.tempC;
		}

		// Load data into static arrays used for fast access internally...
		final WeightedPoint[] dG = new WeightedPoint[nc];
		final int[] index = new int[nc];
		for(int k=0; k<nc; k++) {
			dG[k] = new WeightedPoint();
			index[k] = channels.get(k).index;
		}

		// Calculate gains here...
		if(isRobust) integration.getRobustGainIncrement(index, dG);
		else integration.getMLGainIncrement(index, dG);

		validateGainIncrement(dG, signal);
		
		return dG;
	}
	
	public void validateGainIncrement(WeightedPoint[] dG, Signal signal) throws IllegalAccessException {
		
	}
	
	public void applyGains(WeightedPoint[] G, Integration<?, ?> integration, boolean isTempReady) throws IllegalAccessException {
		float[] G0 = getGains();
		WeightedPoint[] dG = new WeightedPoint[G.length];
		for(int i=G.length; --i >=0; ) if(G[i] != null) {
			dG[i] = (WeightedPoint) G[i].clone();
			dG[i].value -= G0[i];
		}
		applyGainIncrement(dG, integration, isTempReady);
	}
	
	public void applyGainIncrement(WeightedPoint[] dG, Integration<?, ?> integration, boolean isTempReady) throws IllegalAccessException {
		if(fixedGains) throw new IllegalStateException("WARNING! Cannot solve gains for fixed gain modes.");

		final int nc = channels.size();
		final Dependents parms = integration.getDependents("gains-" + name);
		
		parms.clear(channels, 0, integration.size());

		// Precalculate the gain-weight products...
		if(!isTempReady) {
			final Signal signal = integration.signals.get(this);
			if(signal == null) return;
			for(final Frame exposure : integration) if(exposure != null) {
				exposure.tempC = signal.valueAt(exposure);
				if(Float.isNaN(exposure.tempC)) exposure.tempC = 0.0F;
				exposure.tempWC = exposure.isUnflagged(Frame.MODELING_FLAGS) ? exposure.relativeWeight * exposure.tempC : 0.0F;
				exposure.tempWC2 = exposure.tempWC * exposure.tempC;
			}
		}

		// Sync to data and calculate dependeces...
		for(final Frame exposure : integration) if(exposure != null) {
			final float[] data = exposure.data;
			final float C = exposure.tempC;
			final float wC2 = exposure.tempWC2;

			for(int k=nc; --k >=0; ) {
				final WeightedPoint increment = dG[k];
				if(increment == null) continue;
				data[channels.get(k).index] -= (float) increment.value * C;
				parms.add(exposure, wC2 / increment.weight);
			}
		}

		// Account for the one gain parameter per channel...
		for(int k=0; k<nc; k++) if(dG[k].weight > 0.0) {
			parms.add(channels.get(k), 1.0);
		}
		
		// Apply the mode dependeces...
		parms.apply(channels, 0, integration.size());

		if(CRUSH.debug) integration.checkForNaNs(channels, 0, integration.size());
	}
	
	public int getFrameResolution(Integration<?, ?> integration) {
		return integration.power2FramesFor(resolution/Math.sqrt(2.0));
	}
	
	public int getSize(Integration<?, ?> integration) {
		return (int)Math.ceil((double) integration.size() / getFrameResolution(integration));
	}
	
	@Override
	public String toString() {
		String description = name + ":";
		for(Channel channel : channels) description += " " + channel.dataIndex;
		return description;
	}
	
	protected static int nextMode = 0;
	public final static int TOTAL_POWER = nextMode++;
	public final static int CHOPPED = nextMode++;
	
}
