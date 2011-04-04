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


import java.text.NumberFormat;

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
		if(end.index - start.index <= 1) return;
		
		final int nc = integration.instrument.size();
		if(value == null) {
			value = new float[nc];
			weight = new float[nc];
		}
		
		final int to = end.index + 1;
		
		parms.clear(channels, start.index, to);
	
		final double[] sum = new double[channels.size()];
		final double[] sumw = new double[channels.size()];
		
		for(int t=start.index; t<to; t++) {
			final Frame exposure = integration.get(t);
			if(exposure == null) continue;
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
			
			float fw = exposure.relativeWeight;

			for(int k=channels.size(); --k >= 0; ) {
				final Channel channel = channels.get(k);
				if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SPIKE_FLAGS) == 0) {
					sum[k] += fw * exposure.data[channel.index];
					sumw[k] += fw;
				}
			}
		}
		
		
		for(int k=channels.size(); --k >=0; ) {
			final Channel channel = channels.get(k);
			parms.add(channel, 1.0);
			if(sumw[channel.index] > 0.0) {
				sum[k] /= sumw[k];
				value[channel.index] += sum[k];
				weight[channel.index] = (float) sumw[k];
			}
		}
		
		// Remove the incremental phase offset from the integration...
		for(int t=start.index; t<to; t++) {
			final Frame exposure = integration.get(t);
			if(exposure == null) continue;
			
			final float fw = exposure.relativeWeight;
			
			for(int k=channels.size(); --k >=0; ) {
				final Channel channel = channels.get(k);
				exposure.data[channel.index] -= sum[k];
				if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SPIKE_FLAGS) == 0)					
					parms.add(exposure, fw / sumw[k]); 
			}
		}
	
		
		for(Channel channel : channels) weight[channel.index] *= channel.weight;
		
		parms.apply(channels, start.index, to);
	}
	
	public WeightedPoint getValue(Channel channel) {
		return new WeightedPoint(value[channel.index], weight[channel.index]);
	}
	
	protected void getMLCorrelated(final CorrelatedMode mode, final float[] G, final WeightedPoint increment) {	
		final int skipChannels = mode.skipChannels;

		double sum = 0.0, sumw = 0.0;
		
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.channels.get(k);

			if(channel.isFlagged(skipChannels)) continue;
			if(channel.sourcePhase != 0) continue;
	
			final float wG = weight[channel.index] * G[k];
			sum += (wG * value[channel.index]);
			sumw += (wG * G[k]);
		}
			
		increment.value = sum / sumw;
		increment.weight = sumw;
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
			final float wG2 = weight[channel.index] * Gk * Gk;
			if(wG2 == 0.0F) continue;
			
			final WeightedPoint point = temp[n++];
			point.value = (value[channel.index] / Gk);
			point.weight = wG2;
			
			increment.weight += wG2;
		}
		increment.value = n > 0 ? Statistics.smartMedian(temp, 0, n, 0.25) : Double.NaN;
	}
	
	public String toString(NumberFormat nf) {
		StringBuffer text = new StringBuffer();
		for(int c=0; c<value.length; c++) text.append((weight[c] > 0.0 ? nf.format(value[c]) : "NaN") + "\t");
		return new String(text);
	}

	public static final int SKIP_LEVEL = 1;
}
