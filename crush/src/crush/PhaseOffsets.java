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

import java.util.Arrays;

import util.data.Statistics;
import util.data.WeightedPoint;

public class PhaseOffsets {
	protected Integration<?,?> integration;
	
	public float[] value, weight;
	public Frame start, end;
	public int phase = 0;
	public int flag = 0;
	
	public PhaseOffsets(Integration<?,?> integration){
		this.integration = integration;
	}
	
	public void update(ChannelGroup<?> channels, Dependents parms) {
		if(end.index - start.index > 1);
		
		final Instrument<?> instrument = integration.instrument;
		final int nc = instrument.size();
		
		final float[] sum = new float[nc];
		
		if(value == null) {
			value = new float[nc];
			weight = new float[nc];
		}
		else Arrays.fill(weight, 0.0F);
		
		final int to = end.index;
		
		parms.clear(channels, start.index, to);
		
		for(int t=start.index; t<=to; t++) {
			final Frame exposure = integration.get(t);
			if(exposure == null) continue;
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
			
			float fw = exposure.relativeWeight;

			for(Channel channel : channels) if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SPIKE_FLAGS) == 0) {
				sum[channel.index] += fw * exposure.data[channel.index];
				weight[channel.index] += fw;
			}
		}
		
		
		for(Channel channel : instrument) {	
			parms.add(channel, 1.0);
			if(weight[channel.index] > 0.0) sum[channel.index] /= weight[channel.index];
			value[channel.index] += sum[channel.index];
		}
		
		// Remove the incremental phase offset from the integration...
		for(int t=start.index; t<=to; t++) {
			final Frame exposure = integration.get(t);
			if(exposure == null) continue;
			
			final float fw = exposure.relativeWeight;
			
			for(int c=nc; --c >=0; ) {
				exposure.data[c] -= sum[c];
				if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if((exposure.sampleFlag[c] & Frame.SAMPLE_SPIKE_FLAGS) == 0)					
					parms.add(exposure, fw / weight[c]); 
			}
		}
		
		for(Channel channel : instrument) weight[channel.index] *= channel.weight;
		
		parms.apply(channels, start.index, to);
	}
	
	public WeightedPoint getValue(Channel channel) {
		return new WeightedPoint(value[channel.index], weight[channel.index]);
	}
	
	protected void getMLCorrelated(CorrelatedMode mode, final float[] G, WeightedPoint increment) {
		increment.noData();
		
		final int skipChannels = mode.skipChannels;
	
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.channels.get(k);

			if(channel.isFlagged(skipChannels)) continue;
			if(channel.sourcePhase != 0) continue;
	
			final double wG = weight[channel.index] * G[k];
			increment.value += wG * value[channel.index];
			increment.weight += wG * G[k];
		}
		if(increment.weight > 0.0) increment.value /= increment.weight;
	}
	
	
	protected void getRobustCorrelated(CorrelatedMode mode, final float[] G, final WeightedPoint[] temp, final WeightedPoint increment) {
		final int skipChannels = mode.skipChannels;
		
		int n=0;
		increment.weight = 0.0;
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.channels.get(k);
			if(channel.isFlagged(skipChannels)) continue;
			if(channel.sourcePhase != 0) continue;

			final float Gk = G[k];
			final double wG2 = weight[channel.index] * Gk * Gk;
			if(wG2 == 0.0) continue;
			
			final WeightedPoint point = temp[n++];
			point.value = value[channel.index] / Gk;
			point.weight = wG2;
			increment.weight += wG2;
		}
		increment.value = n > 0 ? Statistics.smartMedian(temp, 0, n, 0.25) : 0.0;
	}
	
	
}
