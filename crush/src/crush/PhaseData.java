/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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


import java.io.Serializable;
import java.text.NumberFormat;

import jnum.Util;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;
import jnum.util.FlagSpace;
import jnum.util.FlagBlock;
import jnum.util.HashCode;


public class PhaseData implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1535628125231643099L;
	protected Integration<?,?> integration;
	public int index;
	
	public double[] value, weight;
	public int[] channelFlag;
	public Frame start, end;
	public int phase = 0;
	
	public double dependents = 0.0;
	
	public PhaseData(Integration<?,?> integration){
		this.integration = integration;
	}
	
	@Override
	public int hashCode() { 
		int hash = super.hashCode() ^ integration.getDisplayID().hashCode() ^ index ^ phase ^ HashCode.from(dependents);
		if(start != null) hash ^= start.index;
		if(end != null) hash ^= end.index;
		if(value != null) hash ^= value.length;
		return hash;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof PhaseData)) return false;
		if(!super.equals(o)) return false;
		
		PhaseData phases = (PhaseData) o;
		if(index != phases.index) return false;
		if(phase != phases.phase) return false;
		if(dependents != phases.dependents) return false;
		if(!Util.equals(start, phases.start)) return false;
		if(!Util.equals(end, phases.end)) return false;
		//if(!Arrays.equals(value, phases.value)) return false;
		//if(!Arrays.equals(weight, phases.weight)) return false;
		return true;
	}
	
	public boolean validate() {
		return end.index - start.index > 0;
	}
	
	public void update(final ChannelGroup<?> channels, final Dependents parms) {
		if(end.index - start.index < 1) return;
		
		final int nc = integration.instrument.size();
		if(value == null) {
			value = new double[nc];
			weight = new double[nc];
			channelFlag = new int[nc];
		}
		
		final int to = end.index + 1;
		
		parms.clear(channels, start.index, to);
			
		channels.new Fork<Void>() {
			@Override
			protected void process(Channel channel) {
				double sum = 0.0, sumw = 0.0;
				
				for(int t=start.index; t<to; t++) {
					final Frame exposure = integration.get(t);
					if(exposure == null) continue;
					if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
					
					if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SPIKE) == 0) {
						sum += exposure.relativeWeight * exposure.data[channel.index];
						sumw += exposure.relativeWeight;
					}
				}
				
				parms.addAsync(channel, 1.0);
				
				if(sumw > 0.0) {
					sum /= sumw;
					channel.temp = (float) sum;
					value[channel.index] += sum;
					weight[channel.index] = sumw;
				}
			}
		}.process();
		
		
		// Remove the incremental phase offset from the integration...
		new Fork<Void>() {
			@Override
			protected void processFrame(Frame exposure) {
				final boolean wasUsed = exposure.isUnflagged(Frame.MODELING_FLAGS);
				
				for(Channel channel : channels) {
					exposure.data[channel.index] -= channel.temp;
					if(wasUsed) if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SPIKE) == 0)					
						parms.addAsync(exposure, exposure.relativeWeight / weight[channel.index]); 
				}
			}
			
		}.process();
		
		
		
		for(final Channel channel : channels) {
			weight[channel.index] *= channel.weight;
			if(channel instanceof PhaseWeighting) weight[channel.index] *= ((PhaseWeighting) channel).getRelativePhaseWeight();
		}
		
		parms.apply(channels, start.index, to);
		
	}
	
	public final WeightedPoint getChannelValue(final Channel channel) {
		return new WeightedPoint(value[channel.index], weight[channel.index]);
	}
	
	protected void addChannelDependence(final PhaseDependents parms, final CorrelatedMode mode, final float[] G, final WeightedPoint increment) {
		final int skipChannels = mode.skipFlags;
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.getChannel(k);
			
			if(channel.isFlagged(skipChannels)) continue;
			if(channel.sourcePhase != 0) continue;
			if(channelFlag[channel.index] != 0) continue;
			
			parms.addAsync(channel, weight[channel.index] * G[k] * G[k] / increment.weight());
		}
	}
		
	
	
	protected void getMLCorrelated(final CorrelatedMode mode, final float[] G, final WeightedPoint correlated) {	
		final int skipChannels = mode.skipFlags;

		double sum = 0.0, sumw = 0.0;
		
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.getChannel(k);
			
			if(channel.isFlagged(skipChannels)) continue;
			if(channel.sourcePhase != 0) continue;
			if(channelFlag[channel.index] != 0) continue;
	
			final double wG = weight[channel.index] * G[k];
			sum += (wG * value[channel.index]);
			sumw += (wG * G[k]);
		}
			
		correlated.setValue(sum / sumw);
		correlated.setWeight(sumw);
	}
	
	protected void getRobustCorrelated(final CorrelatedMode mode, final float[] G, final WeightedPoint[] temp, final WeightedPoint correlated) {
		final int skipChannels = mode.skipFlags;
		
		int n=0;
		correlated.setWeight(0.0);
		
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.getChannel(k);
		
			if(channel.isFlagged(skipChannels)) continue;
			if(channel.sourcePhase != 0) continue;
			if(channelFlag[channel.index] != 0) continue;

			final float Gk = G[k];
			final double wG2 = weight[channel.index] * Gk * Gk;
			if(wG2 == 0.0) continue;
			
			final WeightedPoint point = temp[n++];
			point.setValue(value[channel.index] / Gk);
			point.setWeight(wG2);
			correlated.addWeight(point.weight());
			
			assert !Double.isNaN(point.value());
			assert !Double.isInfinite(point.value());
			
		}
		Statistics.smartMedian(temp, 0, n, 0.25, correlated);
	}
	
	public String toString(NumberFormat nf) {
		StringBuffer text = new StringBuffer();
		for(int c=0; c<value.length; c++) text.append((weight[c] > 0.0 ? nf.format(value[c]) : "NaN") + "\t");
		return new String(text);
	}

		
	public abstract class Fork<ReturnType> extends CRUSH.Fork<ReturnType> {

		public Fork() { super(end.index - start.index + 1, integration.getThreadCount()); }

		@Override
		protected void processIndex(int offset) {
			Frame frame = integration.get(start.index + offset);
			if(frame != null) processFrame(frame);
		}
		
		protected abstract void processFrame(final Frame exposure);
		
	}
	
	public static final FlagBlock<Integer> flags = new FlagSpace.Integer("phase-flags").getDefaultFlagBlock();
	public static final int FLAG_SPIKE = flags.next('s', "Spike").value();
	

}
 