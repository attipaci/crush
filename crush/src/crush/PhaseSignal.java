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

import util.data.WeightedPoint;

public class PhaseSignal {
	PhaseSet phases;
	CorrelatedMode mode;
	float[] value, weight;
	float[] syncGains;
	
	public PhaseSignal(PhaseSet phases, CorrelatedMode mode) {
		this.phases = phases;
		this.mode = mode;
		
		value = new float[phases.size()];
		weight = new float[phases.size()];
		
		syncGains = new float[mode.channels.size()];
		
		phases.signals.put(mode, this);
	}
	
	
	public double getValue(int i) { return value[i]; }
	
	public double getWeight(int i) { return weight[i]; }
	
	public void scale(double factor) {
		double f2 = factor * factor;
		for(int i=value.length; --i >= 0; ) {
			value[i] *= factor;
			weight[i] /= f2;
		}
		for(int k=syncGains.length; --k >= 0; ) syncGains[k] /= factor;
	}
	
	protected synchronized void setSyncGains(float[] G) {
		System.arraycopy(G, 0, syncGains, 0, G.length);
	}
	
	protected synchronized void update(final boolean isRobust) throws IllegalAccessException {
		final float[] G = mode.getGains();
		final float[] dG = syncGains;

		for(int k=G.length; --k >= 0; ) dG[k] = G[k] - dG[k];
			
		final ChannelGroup<?> channels = mode.channels;
		final WeightedPoint dC = new WeightedPoint(); 
		
		WeightedPoint[] temp = null;
		if(isRobust) {
			temp = new WeightedPoint[G.length];
			for(int i=temp.length; --i >= 0; ) temp[i] = new WeightedPoint();
		}
		
		for(int i=phases.size(); --i >= 0; ) {
			final PhaseOffsets offsets = phases.get(i);
				
			if(isRobust) offsets.getRobustCorrelated(mode, G, temp, dC);
			offsets.getMLCorrelated(mode, G, dC);
			
			for(int k=G.length; --k >= 0; ) 
				offsets.value[channels.get(k).index] -= dG[k] * value[i] + G[k] * dC.value;

			value[i] += dC.value;
			weight[i] = (float) dC.weight;
		}		
		
		setSyncGains(G);
	}
	
	public synchronized WeightedPoint[] getGainIncrement() {
		final ChannelGroup<?> channels = mode.channels;
		final WeightedPoint[] dG = new WeightedPoint[channels.size()];
		
		for(int k=channels.size(); --k >= 0; ) dG[k] = getGainIncrement(channels.get(k));
			
		return dG;
	}
	
	protected WeightedPoint getGainIncrement(Channel channel) {
		double sum = 0.0, sumw = 0.0;
		double sumC = 0.0, sumCw = 0.0;
		
		// Find the mean signal for the given phase...
		if(channel.sourcePhase != 0) for(int i=phases.size(); --i >= 0; ) {
			final PhaseOffsets offsets = phases.get(i);
	
			if(offsets.flag != 0) continue;
			if((offsets.phase & channel.sourcePhase) != 0) continue;
			
			sum += offsets.value[channel.index];
			sumw += offsets.weight[channel.index];		
			
			sumC += value[i];
			sumCw += weight[i];
		}
		final float ave = sumw > 0.0 ? (float) (sum / sumw) : 0.0F;
		final float aveC = sumCw > 0.0 ? (float) (sumC / sumCw) : 0.0F;
		
		
		// Get the gain for the given phase...
		sum = 0.0; sumw = 0.0;
		for(int i=phases.size(); --i >= 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if(offsets.flag != 0) continue;
			if((offsets.phase & channel.sourcePhase) != 0) continue;
		
			final float C = value[i] - aveC;
			final float wC = offsets.weight[channel.index] * C;
			sum += wC * (offsets.value[channel.index] - ave);
			sumw += wC * C;
		}
		return sumw > 0.0 ? new WeightedPoint(sum / sumw, sumw) : new WeightedPoint();
	}
	
	// TODO robust gains?...
	
	protected void syncGains() throws IllegalAccessException {
		final ChannelGroup<?> channels = mode.channels;
		final float[] G = mode.getGains();		
		final float[] dG = syncGains;
		
		for(int k=G.length; --k >= 0; ) dG[k] = G[k] - dG[k];
			
		for(int i=phases.size(); --i >= 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			for(int k=mode.channels.size(); --k >= 0; )
				offsets.value[channels.get(k).index] -= dG[k] * value[i];
		}
		
		setSyncGains(G);
	}
	
	
	
}
