/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush;


import java.io.Serializable;
import java.text.NumberFormat;

import jnum.data.Statistics;
import jnum.data.WeightedPoint;


public class PhaseData implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1535628125231643099L;
	protected Integration<?,?> integration;
	public int index;
	
	public float[] value, weight;
	public byte[] sampleFlag;
	public Frame start, end;
	public int phase = 0;
	
	public double dependents = 0.0;
	
	public PhaseData(Integration<?,?> integration){
		this.integration = integration;
	}
	
	public boolean validate() {
		return end.index - start.index > 0;
	}
	
	public void update(final ChannelGroup<?> channels, final Dependents parms) {
		if(end.index - start.index < 1) return;	
		
		final int nc = integration.instrument.size();
		if(value == null) {
			value = new float[nc];
			weight = new float[nc];
			sampleFlag = new byte[nc];
		}
		
		final int to = end.index + 1;
		final int skipSamples = Frame.SAMPLE_SPIKE | Frame.SAMPLE_SKIP;
		
		parms.clear(channels, start.index, to);
			
		channels.new Fork<Void>() {
			@Override
			protected void process(final Channel channel) {			    
				double sum = 0.0, sumw = 0.0;
				
				for(int t=start.index; t<to; t++) {
					final Frame exposure = integration.get(t);
					
					if(exposure == null) continue;
					if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
					if((exposure.sampleFlag[channel.index] & skipSamples) != 0) continue;
					
					sum += exposure.relativeWeight * exposure.data[channel.index];
					sumw += exposure.relativeWeight;
				}
				
				if(sumw > 0.0) {
				    parms.addAsync(channel, 1.0);
					float increment = (float) (sum / sumw);
					channel.temp = increment;    
					value[channel.index] += increment;
					weight[channel.index] = (float) sumw;
				}
			}
		}.process();
		
		
		// Remove the incremental phase offset from the integration...
		new Fork<Void>() {
			@Override
			protected void processFrame(Frame exposure) {
				final boolean frameUsed = exposure.isUnflagged(Frame.MODELING_FLAGS);
				
				for(Channel channel : channels) {
					exposure.data[channel.index] -= channel.temp;
					if(frameUsed) if((exposure.sampleFlag[channel.index] & skipSamples) == 0)					
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
	
	public final WeightedPoint getValue(final Channel channel) {
		return new WeightedPoint(value[channel.index], isFlagged(channel) ? 0.0 : weight[channel.index]);
	}
	
	protected boolean isContributing(final Channel channel, final CorrelatedMode mode) {
	    if(channel.isFlagged(mode.skipFlags | Channel.FLAG_PHASE_DOF)) return false;
	    if(channel.sourcePhase != 0) return false;
	    if(isFlagged(channel)) return false;
		return true;
	}
	
	private void addChannelDeps(final CorrelatedMode mode, final float[] G, double sumw, double[] channelParms) {
	    if(sumw <= 0.0) return;
	    // Add the channeldeps...
	    for(int k=G.length; --k >= 0; ) {
	        final Channel channel = mode.getChannel(k);
	        if(isContributing(channel, mode)) channelParms[k] += weight[channel.index] * G[k] * G[k] / sumw;
	    }
	}
	
	public boolean isFlagged(Channel channel) {
	    return sampleFlag[channel.index] != 0;
	}
	
	public boolean isUnflagged(Channel channel) {
        return sampleFlag[channel.index] == 0;
    }
	
	protected void getMLCorrelated(final CorrelatedMode mode, final float[] G, final WeightedPoint correlated, final double[] channelParms) {	
		double sum = 0.0, sumw = 0.0;
		
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.getChannel(k);
			if(!isContributing(channel, mode)) continue;
			
			final double wG = weight[channel.index] * G[k];
			sum += (wG * value[channel.index]);
			sumw += (wG * G[k]);
		}
		
		if(channelParms != null) addChannelDeps(mode, G, sumw, channelParms);
		
		correlated.setValue(sum / sumw);
		correlated.setWeight(sumw);
	}	
		
	
	
	protected void getRobustCorrelated(final CorrelatedMode mode, final float[] G, final WeightedPoint[] temp, final WeightedPoint correlated, final double[] channelParms) {
		int n=0;
		correlated.setWeight(0.0);
		
		for(int k=G.length; --k >= 0; ) {
			final Channel channel = mode.getChannel(k);
			if(!isContributing(channel, mode)) continue;
			
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
		Statistics.Inplace.smartMedian(temp, 0, n, 0.25, correlated);
		
		if(channelParms != null) addChannelDeps(mode, G, correlated.weight(), channelParms);
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
	
	// TODO if more than one flag type is in use, then create flag values through a managed flagSpace...
	public static final int FLAG_SPIKE = 1;
	
	

}
 