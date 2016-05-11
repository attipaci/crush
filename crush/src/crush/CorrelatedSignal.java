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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.util.Arrays;
import java.util.Collection;

import jnum.ExtraMath;
import jnum.Parallel;
import jnum.data.DataPoint;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;



public class CorrelatedSignal extends Signal {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2003139600515268900L;
	Dependents dependents;
	float[] weight;
	float[] sourceFiltering; // per channel
	
	int generation = 0;
	
	int excludePixelFlags = Channel.FLAG_DEAD | Channel.FLAG_BLIND;
	int excludeFrameFlags = Frame.MODELING_FLAGS;
	
	public CorrelatedSignal(CorrelatedMode mode, Integration<?, ?> integration) {
		super(mode, integration);
		syncGains = new float[mode.size()];
		dependents = new Dependents(integration, mode.name);
		resolution = mode.getFrameResolution(integration);
		value = new float[mode.signalLength(integration)];
		weight = new float[value.length];
		driftN = value.length;
	}
	
	@Override
    public Signal copy() {
	    CorrelatedSignal copy = (CorrelatedSignal) super.copy();
	    if(weight != null) weight = Arrays.copyOf(weight, weight.length);
	    if(sourceFiltering != null) sourceFiltering = Arrays.copyOf(sourceFiltering, sourceFiltering.length);
	    return copy;
	}

	@Override
	public final float weightAt(Frame frame) {
		return weight[frame.index / resolution]; 
	}
	
	@Override
	public double getVariance() {
		double sum = 0.0, sumw = 0.0;
		for(int t=value.length; --t >= 0; ) if(weight[t] > 0.0){
			sum += weight[t] * value[t] * value[t];
			sumw += weight[t];
		}	
		return sum / sumw;
	}

	@Override
	public double getUnderlyingVariance() {
		double sum = 0.0, sumw = 0.0;
		for(int t=value.length; --t >= 0; ) {
			if(weight[t] <= 0.0) continue;
			final float xt = value[t];
			sum += weight[t] * xt * xt - 1.0;
			sumw += xt;
		}	
		return sum / sumw;
	}

	
	@Override
	public double level(int from, int to) {
		from = from / resolution;
		to = ExtraMath.roundupRatio(to, resolution);
		
		double sum = 0.0, sumw=0;
		for(int t=to; --t >= from; ) {
			sum += weight[t] * value[t];
			sumw += weight[t];
		}
		if(sumw == 0.0) return 0.0;
		
		final double ave = sum / sumw;
		for(int t=to; --t >= from; ) value[t] -= ave;
		return ave;
	}
	
	
	@Override
	public WeightedPoint getMedian() {
		WeightedPoint[] temp = new WeightedPoint[value.length];
		int n = 0;
		for(int t=value.length; --t >= 0; ) if(weight[t] > 0.0)
			temp[n++] = new WeightedPoint(value[t], weight[t]);
		return Statistics.median(temp, 0, n);
	}
	
	@Override
	public WeightedPoint getMean() {
		double sum = 0.0, sumw = 0.0;
		for(int t=value.length; --t >= 0; ) if(weight[t] > 0.0) {
			sum += weight[t] * value[t];
			sumw += weight[t];
		}	
		return new WeightedPoint(sum / sumw, sumw);
	}
	
	@Override
	public void differentiate() {
		final double idt = 1.0 / (resolution * integration.instrument.samplingInterval);
		final WeightedPoint p1 = new WeightedPoint();
		final WeightedPoint p2 = new WeightedPoint();
		
		final int n = value.length;
		final int nm1 = n-1;
		
		// v[n] = f'[n+0.5]
		for(int t=0; t<nm1; t++) {
			p1.setValue(value[t]);
			p1.setWeight(weight[t]);
			
			p2.setValue(value[t+1]);
			p2.setWeight(weight[t+1]);
		
			p2.subtract(p1);
			p2.scale(idt);
		
			value[t] = (float) p2.value();
			weight[t] = (float) p2.weight();
		}

		// the last value is based on the last difference...
		value[nm1] = value[n-2];
		weight[nm1] = weight[n-2];
		
		// otherwise, it's:
		// v[n] = (f'[n+0.5] + f'[n-0.5]) = v[n] + v[n-1]
		for(int t=nm1; --t > 0; ) {
			p1.setValue(value[t]);
			p1.setWeight(weight[t]);
			
			p2.setValue(value[t-1]);
			p2.setWeight(weight[t-1]);
			
			p2.average(p1);
			
			value[t] = (float) p2.value();
			weight[t] = (float) p2.weight();
		}
	}
	
	// Intergate using trapesiod rule...
	@Override
	public void integrate() {
		float dt = (float) (resolution * integration.instrument.samplingInterval);
		float dt2 = dt * dt;
		
		WeightedPoint halfLast = new WeightedPoint();
		WeightedPoint halfNext = new WeightedPoint();
		WeightedPoint I = new WeightedPoint();
		
		for(int t=0; t<value.length; t++) {
			// Calculate next half increment of h/2 * f[t]
			halfNext.setValue(value[t]);
			halfNext.setWeight(weight[t]);
			halfNext.scale(0.5 * dt);
			
			// Add half increments from below and above 
			I.add(halfLast);
			I.add(halfNext);
			value[t] = (float) I.value() / dt;
			weight[t] = (float) I.weight() * dt2;
			
			// Switch the last and next storages...
			WeightedPoint temp = halfLast;
			halfLast = halfNext;
			halfNext = temp;
		}
	}
	
	
	
	public void calcFiltering() {
		// Create the filtering srorage if necessary...
		if(sourceFiltering == null) {
			sourceFiltering = new float[getMode().size()];
			Arrays.fill(sourceFiltering, 1.0F);
		}
		
		// Calculate the source filtering for this mode...
		final double nP = getParms();
		
		final CorrelatedMode mode = (CorrelatedMode) getMode();
		final ChannelGroup<?> channels = mode.getChannels();
		final int skipFlags = mode.skipFlags;
		
		// The reduced filtering effect due to model time-resolution
		//final double T =  (resolution - 1) * integration.instrument.samplingInterval;
		//final double phit = 1.0 - T / (T + integration.getPointCrossingTime());
		
		final SourceModel model = integration.scan.sourceModel;
		final double pointSize = model == null ? integration.instrument.getPointSize() : model.getPointSize();
		integration.instrument.calcOverlap(pointSize);
		
		new CRUSH.Fork<Void>(channels.size(), integration.getThreadCount()) {
			@Override
			protected void processIndex(int k) {
				final Channel channel = channels.get(k);
				if(channel.isFlagged(skipFlags)) return;
				
				double phi = dependents.get(channel);
				final Collection<Overlap> overlaps = channel.getOverlaps();
				
				// Every pixel that sees the source contributes to the filtering...
				if(overlaps != null) for(Overlap overlap : overlaps) {
					final Channel other = (overlap.a == channel) ? overlap.b : overlap.a; 
					if(other.isFlagged(skipFlags)) continue;
					phi += overlap.value * dependents.get(other);
				}
					
				
				//phi *= phit;
				
				if(nP > 0) phi /= nP;
				if(phi > 1.0) phi = 1.0;
			
				// Undo the prior filtering correction
				if(sourceFiltering[k] > 0.0) channel.sourceFiltering /= sourceFiltering[k];
				if(Double.isNaN(channel.sourceFiltering)) channel.sourceFiltering = 1.0;
				
				// Calculate the new filtering gain correction...
				sourceFiltering[k] = (float) (1.0 - phi); 
				// And apply it...
				channel.sourceFiltering *= sourceFiltering[k];
			}
			
		}.process();
			
	}
	
	
	// TODO Use this in ArrayUtil...
	@Override
	public void smooth(double[] w) {
		int ic = w.length / 2;
		float[] smooth = new float[value.length];
		float[] smoothw = new float[value.length];
			
		for(int t=value.length; --t >= 0; ) {		
			int t1 = Math.max(0, t-ic); // the beginning index for the convolution
			int tot = Math.min(value.length, t + w.length - ic);
			int i = ic + t - t1; // the beginning index for the weighting fn.
			double sum = 0.0, sumw = 0.0;
			
			for( ; t1<tot; t1++, i++) if(weight[t1] > 0.0) {
				double wc = w[i] * weight[t1];
				sum += wc * value[t1];
				sumw += Math.abs(wc);
			}
			if(sumw > 0.0) {
				smooth[t] = (float) (sum / sumw);
				smoothw[t] = (float) sumw;
			}
		}
		value = smooth;
		weight = smoothw;
	}
	
	public double getParms() {
		int n = 0;
		for(int i=value.length; --i >= 0; ) if(weight[i] > 0.0) n++;	
		return n * (1.0 - 1.0 / driftN);
	}
	
	// Get correlated for all frames even those that are no good...
	// But use only channels that are valid, and skip over flagged samples...
	public void update(final boolean isRobust) throws Exception {
		// work on only a selected subset of not critically flagged channels only (for speed)
		final CorrelatedMode mode = (CorrelatedMode) getMode();
		final ChannelGroup<?> channels = mode.getChannels();
		final ChannelGroup<?> goodChannels = mode.getValidChannels();
		final int resolution = mode.getFrameResolution(integration);
		
		// Need at least 2 channels for decorrelaton...
		//if(goodChannels.size() < 2) return;
					
		final float[] G = mode.getGains();	
		final float[] dG = syncGains;
		
		boolean resyncGains = false;
		
		// Make syncGains carry the gain increment from last sync...
		// Precalculate the gain-weight products...
		for(int k=G.length; --k >= 0; ) { 
		    if(CRUSH.debug) if(Float.isNaN(G[k])) integration.comments += "!" + channels.get(k).getID();
		    
			dG[k] = G[k] - syncGains[k];
			if(dG[k] != 0.0) resyncGains = true;
			
			final Channel channel = channels.get(k);
			channel.temp = 0.0F;
			channel.tempG = G[k];
			channel.tempWG = (float) (channel.weight) * channel.tempG;
			channel.tempWG2 = channel.tempWG * channel.tempG;
		}
		
		
		// Remove channels with zero gain/weight from goodChannels
		// Precalculate the channel dependents...
		for(int k=goodChannels.size(); --k >= 0; ) {
			Channel channel = goodChannels.get(k);
			if(channel.tempWG2 == 0.0) goodChannels.remove(k);
			// Correct for lowered degrees of freedom due to prior filtering...
			else channel.temp = channel.tempWG2 * (float) channel.getFiltering(integration);
		}
		
		final boolean isGainResync = resyncGains;
			
		// Clear the dependents in all mode channels...
		dependents.clear(channels, 0, integration.size());
		
		integration.new BlockFork<float[]>(resolution) {
			private WeightedPoint increment;
			private DataPoint[] buffer;
			private float[] channelParms;
			private boolean isRecycling = true;
			
			@Override
			protected void init() {
				super.init();
				increment = new WeightedPoint();
				
				channelParms = integration.instrument.getFloats();
				Arrays.fill(channelParms, 0.0F);
				
				if(!isRobust) return;

				// Try to use a recycleable buffer if it is large enough...
				buffer = integration.instrument.getDataPoints();
				if(buffer.length < resolution * goodChannels.size()) {
				    // Otherwise, just create a larger ad-hoc buffer that will not be recycled...
				    Instrument.recycle(buffer);
				    buffer = DataPoint.createArray(resolution * integration.instrument.size());
				    isRecycling = false;
				}	
			}
			
			@Override
			protected void cleanup() {
				super.cleanup();
				if(buffer != null) if(isRecycling) Instrument.recycle(buffer);
			}
			
			@Override
			protected void process(int from, int to) {
				final int T = from / resolution;	
				
				// Resync gains, if necessary...
				final float C = value[T];
				
				if(isGainResync) for(int t=to; --t >= from; ) {
				    final Frame exposure = integration.get(t);
				    if(exposure != null) for(int k=G.length; --k >= 0; )					    
				        exposure.data[channels.get(k).index] -= dG[k] * C;
				}
			
				// Calculate the incremental correlated values...
				if(isRobust) getRobustCorrelated(goodChannels, from, to, increment, buffer);
				else getMLCorrelated(goodChannels, from, to, increment);
				
				// If there is no valid correlated signal, we are done...
				if(increment.weight() <= 0.0) return;
				
				// Cast the incremental value into float for speed...	
				final float dC = (float) increment.value();

				// sync to data and calculate dependences...float filterFactor = (float) (1.0 - integration.filterTimeScale / integration.getDuration());
				for(int t=to; --t >= from; ) {
					final Frame exposure = integration.get(t);
					if(exposure == null) continue;
					
					// Here usedGains carries the gain increment dG from the last correlated signal removal
					for(int k=dG.length; --k >= 0; ) {
					    final Channel channel = channels.get(k);
					    exposure.data[channel.index] -= G[k] * dC;
					}
												
					if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
					
					final double fpNorm = exposure.relativeWeight / increment.weight();
					
					for(final Channel channel : goodChannels) if(exposure.sampleFlag[channel.index] == 0) { 	
						final double dp = fpNorm * channel.temp;
						dependents.addAsync(exposure, dp);
						channelParms[channel.index] += dp;
					}
				}
					
				// Update the correlated signal model...	
				value[T] += dC;
				weight[T] = (float) increment.weight();
			}
		
			@Override
			public float[] getLocalResult() { return channelParms; }
			
			@Override
			protected void postProcess() {
				super.postProcess();
				
				for(Parallel<float[]> task : getWorkers()) {
					float[] localChannelParms = task.getLocalResult();
					dependents.addForChannels(localChannelParms);
					Instrument.recycle(localChannelParms);
				}
			}
			
		}.process();
		
		// Apply the mode dependencies only to the channels that have contributed...
		dependents.apply(goodChannels, 0, integration.size());	
			
		// Update the gain values used for signal extraction...
		setSyncGains(G);
				
		if(CRUSH.debug) integration.checkForNaNs(mode.getChannels(), 0, integration.size());
		
		generation++;
			
		// Calculate the point-source filtering by the decorrelation...
		calcFiltering();
	}
	
	
	private final void getMLCorrelated(final ChannelGroup<?> channels, final int from, int to, final WeightedPoint increment) {
		double sum = 0.0, sumw = 0.0;
		
		while(--to >= from) {
			final Frame exposure = integration.get(to);
			
			if(exposure == null) continue; 
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
			
			for(final Channel channel : channels) if(exposure.sampleFlag[channel.index] == 0) {
				sum += (exposure.relativeWeight * channel.tempWG * exposure.data[channel.index]);
				sumw += (exposure.relativeWeight * channel.tempWG2);
			}
		}
	
		increment.setValue(sum / sumw);
		increment.setWeight(sumw);
	}
		

	private final void getRobustCorrelated(final ChannelGroup<?> channels, final int from, int to, final WeightedPoint increment, WeightedPoint[] buffer) {
		increment.noData();
		int n = 0;
		
		while(--to >= from) {
			final Frame exposure = integration.get(to);
			
			if(exposure == null) continue; 
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
		
			for(final Channel channel : channels) if(exposure.sampleFlag[channel.index] == 0) {
				final WeightedPoint point = buffer[n++];
				point.setValue(exposure.data[channel.index] / channel.tempG);
				point.setWeight(exposure.relativeWeight * channel.tempWG2);
				increment.addWeight(point.weight());

				assert !Double.isNaN(point.value());
				assert !Double.isInfinite(point.value());
			}
		}

		Statistics.smartMedian(buffer, 0, n, 0.25, increment); 
	}
	
}
