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
import util.data.Statistics;
import util.data.WeightedPoint;

public class Mode {
	public String name;
	public ChannelGroup<?> channels;
	public Field gainField;

	public boolean fixedGains = false;
	public boolean phaseGains = false;
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
			Channel channel = channels.get(k);
			
			float G = Float.NaN;
			if(gainType == Instrument.GAINS_SIGNED) G = gain[k];
			else if(gainType == Instrument.GAINS_BIDIRECTIONAL) G = Math.abs(gain[k]);

			if(!gainRange.contains(G)) channel.flag(gainFlag);
			else channel.unflag(gainFlag);
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
		if(fixedGains) throw new IllegalStateException("WARNING! Cannot solve gains for fixed gain modes.");
		float[] G0 = getGains();
		WeightedPoint[] G = phaseGains ? 
				integration.getPhases().getGainIncrement(this) : 
				integration.signals.get(this).getGainIncrement(isRobust);
				
		for(int i=G0.length; --i >= 0; ) {
			if(G[i] != null) G[i].value += G0[i];
			else G[i] = new WeightedPoint(G0[i], 0.0);
		}
		return G;		
	}
	
	protected void syncGains(Integration<?,?> integration, float[] sumwC2, boolean isTempReady) throws IllegalAccessException {
		integration.signals.get(this).syncGains(sumwC2, isTempReady);		
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
