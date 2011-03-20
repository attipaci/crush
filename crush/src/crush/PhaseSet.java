/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush;

import java.util.*;

import util.data.Statistics;
import util.data.WeightedPoint;

public class PhaseSet extends ArrayList<PhaseOffsets> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3448515171055358173L;
	
	protected Integration<?,?> integration;
	protected Hashtable<Mode, float[]> signals = new Hashtable<Mode, float[]>();
	protected Hashtable<Mode, float[]> gains = new Hashtable<Mode, float[]>();
	protected Hashtable<Mode, float[]> usedGains = new Hashtable<Mode, float[]>();
	
	public PhaseSet(Integration<?,?> integration) {
		this.integration = integration;		
	}
	
	public void update(ChannelGroup<?> channels) {
		String name = "phases";
		Dependents parms = integration.dependents.containsKey(name) ? integration.dependents.get(name) : new Dependents(integration, name);
		
		for(PhaseOffsets offsets : this) offsets.update(channels, parms);	
		
		// Discard the DC component of the phases...
		for(Channel channel : channels) level(channel); 
	}
	
	protected float[] getGains(Mode mode) throws IllegalAccessException {
		float[] G = null;
		if(gains.contains(mode)) G = gains.get(mode); 
		else {
			float[] G0 = mode.getGains();
			G = new float[G0.length];
			System.arraycopy(G0, 0, G, 0, G0.length);
		}
		return G;
	}
	
	public float[] updateGains(Mode mode, int normFlags) throws IllegalAccessException { 
		//System.err.println("### G " + mode.name);
		
		final float[] signal = signals.get(mode);
		final float[] G = getGains(mode);
		
		final ChannelGroup<?> channels = mode.channels;
		final int nc = channels.size();
		
		for(int k=nc; --k>=0; ) {
			final Channel channel = channels.get(k);
			try {
				float dG = getGainIncrement(signal, channel); 		
				for(int i=signal.length; --i >= 0; ) get(i).value[channel.index] -= dG * signal[i];
				G[k] += dG;
			}
			catch(IllegalStateException e) {}
		}
		
		// Renormalized the gains
		float aveG = (float) mode.getAverageGain(G, normFlags);
		for(int i=G.length; --i>=0; ) G[i] /= aveG;
		for(int i=signal.length; --i>=0; ) signal[i] *= aveG;
		
		/*
		System.err.println("### " + mode.name + ": " + aveG);
		
		for(int i=0; i<G.length; i++) {
			final Channel channel = channels.get(i);
			System.err.print(util.Util.f3.format(G[i] / channel.gain) + " ");
		}
		System.err.println();
		*/
		
		gains.put(mode, G);
		usedGains.put(mode, G);
		
		return G;
	}
	
	protected float getGainIncrement(float[] signal, Channel channel) throws IllegalStateException {
		double sum = 0.0, sumw = 0.0;
		for(int i=size(); --i >= 0; ) {
			final PhaseOffsets offsets = get(i);
			if(offsets.flag != 0) continue;
			
			final float C = signal[i];
			final float wC = offsets.weight[i] * C;
			sum += wC * offsets.value[i];
			sumw += wC * C;
		}
		if(sumw == 0.0) throw new IllegalStateException("No data to determine channel gains from phases.");
		return (float) (sum / sumw);
	}
	
	protected void updateSignal(CorrelatedMode mode, boolean isRobust) throws IllegalAccessException {
		//System.err.println("### S " + mode.name);		
		
		final float[] G = getGains(mode);
		final float[] signal = signals.contains(mode) ? signals.get(mode) : new float[size()];
		final float[] dG = usedGains.contains(mode) ? usedGains.get(mode) : new float[G.length];
		
		for(int k=G.length; --k >= 0; ) dG[k] = G[k] - dG[k];
		
		final ChannelGroup<?> channels = mode.channels;
		WeightedPoint[] temp = null;
		if(isRobust) {
			temp = new WeightedPoint[G.length];
			for(int i=temp.length; --i >= 0; ) temp[i] = new WeightedPoint();
		}
		WeightedPoint dC = new WeightedPoint(); 
		
		for(int i=size(); --i >= 0; ) {
			final PhaseOffsets offsets = get(i);
			if(isRobust) getRobustCorrelated(offsets, mode, G, temp, dC);
			getMLCorrelated(offsets, mode, G, dC);
			
			for(int k=G.length; --k >= 0; ) {
				final Channel channel = channels.get(k);
				offsets.value[channel.index] -= dG[k] * signal[i] + G[k] * dC.value;
			}			
			signal[i] += dC.value;
		}		
		
		signals.put(mode, signal);
		usedGains.put(mode, G);
	}
	
	public void getMLCorrelated(PhaseOffsets offsets, CorrelatedMode mode, float[] G, WeightedPoint increment) {
		increment.noData();
		
		final int skipChannels = mode.skipChannels;
		
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.channels.get(k);
			if(channel.isFlagged(skipChannels)) continue;
			if((channel.label & Channel.LABEL_ONSOURCE) != 0) continue;
			final double wG = offsets.weight[channel.index] * G[k];
			increment.value += wG * offsets.value[channel.index];
			increment.weight += wG * G[k];
		}
		if(increment.weight > 0.0) increment.value /= increment.weight;
	}
	
	public void getRobustCorrelated(PhaseOffsets offsets, CorrelatedMode mode, float[] G, WeightedPoint[] temp, WeightedPoint increment) {
		final int skipChannels = mode.skipChannels;
		
		int n=0;
		increment.weight = 0.0;
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.channels.get(k);
			if(channel.isFlagged(skipChannels)) continue;
			if((channel.label & Channel.LABEL_ONSOURCE) != 0) continue;
			final float Gk = G[k];
			final double wG2 = offsets.weight[channel.index] * Gk * Gk;
			if(wG2 == 0.0) continue;
			WeightedPoint point = temp[n++];
			point.value = offsets.value[channel.index] / Gk;
			point.weight = wG2;
			increment.weight += wG2;
		}
		increment.value = n > 0 ? Statistics.smartMedian(temp, 0, n, 0.25) : 0.0;
	}
	
	protected void level(Channel channel) {
		final int c = channel.index;
		double sum = 0.0, sumw = 0.0;
		for(PhaseOffsets offsets : this) if(offsets.flag == 0) {			
			sum += offsets.weight[c] * offsets.value[c];
			sumw += offsets.weight[c];
		}
		if(sumw == 0.0) return;
		final float d = (float) (sum / sumw);
		
		for(PhaseOffsets offsets : this) offsets.value[c] -= d;
	}
	
}
