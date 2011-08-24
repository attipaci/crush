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

import java.util.Arrays;

import util.data.Statistics;
import util.data.WeightedPoint;


public class CorrelatedSignal extends Signal {
	Dependents dependents;
	float[] weight;
	float[] sourceFiltering; // per channel
	
	int generation = 0;
	
	int excludePixelFlags = Channel.FLAG_DEAD | Channel.FLAG_BLIND;
	int excludeFrameFlags = Frame.MODELING_FLAGS;
	
	private WeightedPoint[] temp;
	
	public CorrelatedSignal(CorrelatedMode mode, Integration<?, ?> integration) {
		super(mode, integration);
		syncGains = new float[mode.channels.size()];
		dependents = new Dependents(integration, mode.name);
		resolution = mode.getFrameResolution(integration);
		value = new float[mode.getSize(integration)];
		weight = new float[value.length];
	}
	
	public WeightedPoint[] getTempStorage(ChannelGroup<?> channels) {
		final int n = channels.size() * resolution;
		if(n == 0) return new WeightedPoint[0];
		if(temp == null) temp = new WeightedPoint[n];
		if(temp.length < n) temp = new WeightedPoint[n];
		if(temp[0] == null) for(int i=temp.length; --i >= 0; ) temp[i] = new WeightedPoint();	
		return temp;
	}

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
		for(int t=value.length; --t >= 0; ) if(weight[t] > 0.0) {
			sum += weight[t] * value[t] * value[t] - 1.0;
			sumw += value[t];
		}	
		return sum / sumw;
	}

	@Override
	public synchronized void removeDrifts() {
		int N = (int)Math.ceil((double) integration.framesFor(integration.filterTimeScale) / resolution);
		if(N == driftN) return;
		
		addDrifts();
		
		driftN = N;
		drifts = new float[(int) Math.ceil((double) value.length / driftN)];
		
		for(int fromt=0, T=0; fromt < value.length; fromt += driftN) {
			double sum = 0.0, sumw = 0.0;
			final int tot = Math.min(fromt + driftN, value.length);
			
			for(int t=tot; --t >= fromt; ) if(weight[t] > 0.0){
				sum += weight[t] * value[t];
				sumw += weight[t];
			}
			if(sumw > 0.0) {
				float fValue = (float) (sum /= sumw);
				for(int t=tot; --t >= fromt; ) value[t] -= fValue;
				drifts[T++] = fValue;
			}
		}
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
	public synchronized void differentiate() {
		final double idt = 1.0 / (resolution * integration.instrument.samplingInterval);
		final WeightedPoint p1 = new WeightedPoint();
		final WeightedPoint p2 = new WeightedPoint();
		
		final int n = value.length;
		final int nm1 = n-1;
		
		// v[n] = f'[n+0.5]
		for(int t=0; t<nm1; t++) {
			p1.value = value[t];
			p1.weight = weight[t];
			
			p2.value = value[t+1];
			p2.weight = weight[t+1];
		
			p2.subtract(p1);
			p2.scale(idt);
		
			value[t] = (float) p2.value;
			weight[t] = (float) p2.weight;
		}

		// the last value is based on the last difference...
		value[nm1] = value[n-2];
		weight[nm1] = weight[n-2];
		
		// otherwise, it's:
		// v[n] = (f'[n+0.5] + f'[n-0.5]) = v[n] + v[n-1]
		for(int t=nm1; --t > 0; ) {
			p1.value = value[t];
			p1.weight = weight[t];
			
			p2.value = value[t-1];
			p2.weight = weight[t-1];
			
			p2.average(p1);
			
			value[t] = (float) p2.value;
			weight[t] = (float) p2.weight;
		}
	}
	
	// Intergate using trapesiod rule...
	@Override
	public synchronized void integrate() {
		float dt = (float) (resolution * integration.instrument.samplingInterval);
		float dt2 = dt * dt;
		
		WeightedPoint halfLast = new WeightedPoint();
		WeightedPoint halfNext = new WeightedPoint();
		WeightedPoint I = new WeightedPoint();
		
		for(int t=0; t<value.length; t++) {
			// Calculate next half increment of h/2 * f[t]
			halfNext.value = value[t];
			halfNext.weight = weight[t];
			halfNext.scale(0.5 * dt);
			
			// Add half increments from below and above 
			I.add(halfLast);
			I.add(halfNext);
			value[t] = (float) I.value / dt;
			weight[t] = (float) I.weight * dt2;
			
			// Switch the last and next storages...
			WeightedPoint temp = halfLast;
			halfLast = halfNext;
			halfNext = temp;
		}
	}
	
	
	public void calcFiltering() {
		// Create the filtering srorage if necessary...
		if(sourceFiltering == null) {
			sourceFiltering = new float[getMode().channels.size()];
			Arrays.fill(sourceFiltering, 1.0F);
		}
		
		// Calculate the source filtering for this mode...
		int nP = getParms();
		
		final CorrelatedMode mode = (CorrelatedMode) getMode();
		final ChannelGroup<?> channels = mode.channels;
		int skipFlags = mode.skipChannels;
		
		for(int k=channels.size(); --k >= 0; ) {
			Channel channel = channels.get(k);
			double phi = 0.0;
			// Every pixel that sees the source contributes to the filtering...
			if(channel.isUnflagged(skipFlags)) for(Channel other : mode.channels) if(other.isUnflagged(skipFlags))
				phi += channel.overlap(other, integration.scan.sourceModel) * dependents.get(other);

			if(nP > 0) phi /= nP;
			if(phi > 1.0) phi = 1.0;
		
			// Undo the prior filtering correction
			if(sourceFiltering[k] > 0.0) channel.sourceFiltering /= sourceFiltering[k];
			// Calculate the new filtering gain correction...
			sourceFiltering[k] = (float) (1.0 - phi); 
			// And apply it...
			channel.sourceFiltering *= sourceFiltering[k];
		}		
	}
	
	// TODO Use this in ArrayUtil...
	@Override
	public synchronized void smooth(double[] w) {
		int ic = w.length / 2;
		float[] smooth = new float[value.length];
		float[] smoothw = new float[value.length];
		
		double norm = 0.0;
		for(int i=w.length; --i >= 0; ) norm += Math.abs(w[i]);
		
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
	
	
	public void noTempStorage() { temp = null; }
	
	public int getParms() {
		int n = 0;
		for(int i=value.length; --i >= 0; ) if(weight[i] > 0.0) n++;
		return n;
	}

	
	// Get correlated for all frames even those that are no good...
	// But use only channels that are valid, and skip over flagged samples...
	public synchronized void update(boolean isRobust) throws IllegalAccessException {
		// work on only a selected subset of not critically flagged channels only (for speed)
		final CorrelatedMode mode = (CorrelatedMode) getMode();
		final ChannelGroup<?> channels = mode.channels;
		final ChannelGroup<?> goodChannels = mode.getValidChannels();
		final int nc = channels.size();
		
		// Need at least 2 channels for decorrelaton...
		//if(goodChannels.size() < 2) return;
		
		final int resolution = mode.getFrameResolution(integration);
		final int nt = integration.size();
		final int nT = mode.getSize(integration);
		
		// Clear the dependents in all mode channels...
		dependents.clear(channels, 0, integration.size());
		
		final float[] G = mode.getGains();
		final float[] dG = syncGains;
		
		// Make syncGains carry the gain increment from last sync...
		for(int k=nc; --k >= 0; ) dG[k] = G[k] - dG[k];
		
		// Precalculate the gain-weight products...
		for(int k=nc; --k >= 0; ) {
			final Channel channel = channels.get(k);
			channel.temp = 0.0F;
			channel.tempG = G[k];
			channel.tempWG = (float) (channel.weight) * channel.tempG;
			channel.tempWG2 = channel.tempWG * channel.tempG;
		}
		
		// Remove channels with zero gain/weight from goodChannels
		for(int k=goodChannels.size(); --k >= 0; ) {
			Channel channel = goodChannels.get(k);
			if(channel.tempWG2 == 0.0) goodChannels.remove(k);
		}
		
		
		final WeightedPoint increment = new WeightedPoint();
		
		WeightedPoint[] buffer = null;
		if(isRobust) buffer = getTempStorage(goodChannels);
		else noTempStorage();
		
		for(int T=0, from=0; T < nT; T++, from += resolution) {
			final int to = Math.min(from + resolution, nt);
			
			if(isRobust) getRobustCorrelated(goodChannels, from, to, increment, buffer);
			else getMLCorrelated(goodChannels, from, to, increment);
			
			if(increment.weight <= 0.0) continue;
			
			// Cast the incremental value into float for speed...
			final float C = value[T];
			final float dC = (float) increment.value;

			// precalculate the channel dependences...
			for(final Channel pixel : goodChannels) pixel.temp = pixel.tempWG2 / (float) increment.weight;

			// sync to data and calculate dependeces...
			for(int t=to; --t >= from; ) {
				final Frame exposure = integration.get(t);
				if(exposure == null) continue;

				final float[] data = exposure.data;
				final float fw = exposure.relativeWeight;
				
				for(int k=nc; --k >= 0; ) {
					final Channel channel = channels.get(k);
					// Here usedGains carries the gain increment dG from the last correlated signal removal
					data[channel.index] -= dG[k] * C + channel.tempG * dC;
					if(channel.temp > 0.0F) dependents.add(exposure, channel, fw * channel.temp);
				}
			}
				
			// Update the correlated signal model...	
			value[T] += dC;
			weight[T] = (float) increment.weight;
		}
		
		// Update the gain values used for signal extraction...
		setSyncGains(G);
		
		// Free up the temporary storage, which is used for calculating medians
		noTempStorage();
		
		if(CRUSH.debug) integration.checkForNaNs(mode.channels, 0, integration.size());
		
		generation++;
		
		// Apply the mode dependices only to the channels that have contributed...
		dependents.apply(goodChannels, 0, integration.size());	
		
		// Calculate the point-source filtering by the decorrelation...
		calcFiltering();
	}
	
	
	private final void getMLCorrelated(final ChannelGroup<?> channels, final int from, int to, final WeightedPoint increment) {
		double sum = 0.0, sumw = 0.0;
		
		while(--to >= from) {
			final Frame exposure = integration.get(to);
						
			if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {
				for(final Channel channel : channels) if(exposure.sampleFlag[channel.index] == 0) {
					sum += (exposure.relativeWeight * channel.tempWG * exposure.data[channel.index]);
					sumw += (exposure.relativeWeight * channel.tempWG2);
				}
			}
		}
	
		increment.value = sum / sumw;
		increment.weight = sumw;
	}
		

	private final void getRobustCorrelated(final ChannelGroup<?> channels, final int from, int to, final WeightedPoint increment, WeightedPoint[] buffer) {
		increment.noData();
		int n = 0;
		
		while(--to >= from) {
			final Frame exposure = integration.get(to);
			if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {
				for(final Channel channel : channels) if(exposure.sampleFlag[channel.index] == 0) {
					final WeightedPoint point = buffer[n++];
					point.value = (exposure.data[channel.index] / channel.tempG);
					increment.weight += (point.weight = (exposure.relativeWeight * channel.tempWG2));
					
					assert !Double.isNaN(point.value);
					assert !Double.isInfinite(point.value);
				}
			}
		}
		Statistics.smartMedian(buffer, 0, n, 0.25, increment); 
	}
	
	
	
	// TODO Use estimators...
	
	
	
	
}
