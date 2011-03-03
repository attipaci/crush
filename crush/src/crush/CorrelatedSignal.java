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
import util.data.ArrayUtil;
import util.data.WeightedPoint;


public class CorrelatedSignal extends Signal {
	CorrelatedMode mode;
	Dependents dependents;
	float[] weight;
	float[] sourceFiltering; // per channel
	
	int generation = 0;
	
	int excludePixelFlags = Channel.FLAG_DEAD | Channel.FLAG_BLIND;
	int excludeFrameFlags = Frame.MODELING_FLAGS;
	
	private WeightedPoint[] temp;
	
	CorrelatedSignal(Integration<?, ?> integration, CorrelatedMode mode) {
		super(integration);
		this.mode = mode;
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
		if(temp[0] == null) for(int i=0; i<temp.length; i++) temp[i] = new WeightedPoint();	
		return temp;
	}

	public final float weightAt(Frame frame) {
		return weight[frame.index / resolution]; 
	}
	
	@Override
	public double getVariance() {
		double sum = 0.0, sumw = 0.0;
		for(int t=0; t<value.length; t++) if(weight[t] > 0.0){
			sum += weight[t] * value[t] * value[t];
			sumw += weight[t];
		}	
		return sum / sumw;
	}

	@Override
	public double getUnderlyingVariance() {
		double sum = 0.0, sumw = 0.0;
		for(int t=0; t<value.length; t++) if(weight[t] > 0.0) {
			sum += weight[t] * value[t] * value[t] - 1.0;
			sumw += value[t];
		}	
		return sum / sumw;
	}

	@Override
	public void removeDrifts() {
		int N = (int)Math.ceil((double) integration.framesFor(integration.filterTimeScale) / resolution);
		if(N == driftN) return;
		
		addDrifts();
		
		driftN = N;
		drifts = new float[(int) Math.ceil((double) value.length / driftN)];
		
		for(int fromt=0, T=0; fromt<value.length; fromt+=driftN) {
			double sum = 0.0, sumw = 0.0;
			final int tot = Math.min(fromt + driftN, value.length);
			
			for(int t=fromt; t<tot; t++) if(weight[t] > 0.0){
				sum += weight[t] * value[t];
				sumw += weight[t];
			}
			if(sumw > 0.0) {
				float fValue = (float) (sum /= sumw);
				for(int t=fromt; t<tot; t++) value[t] -= fValue;
				drifts[T++] = fValue;
			}
		}
	}
	
	
	@Override
	public WeightedPoint getMedian() {
		WeightedPoint[] temp = new WeightedPoint[value.length];
		double sumw = 0.0;
		for(int t=0; t<value.length; t++) if(weight[t] > 0.0) {
			temp[t] = new WeightedPoint(value[t], weight[t]);
			sumw += weight[t];
		}
		return new WeightedPoint(ArrayUtil.median(temp), sumw);
	}
	
	@Override
	public WeightedPoint getMean() {
		double sum = 0.0, sumw = 0.0;
		for(int t=0; t<value.length; t++) if(weight[t] > 0.0) {
			sum += weight[t] * value[t];
			sumw += weight[t];
		}	
		return new WeightedPoint(sum / sumw, sumw);
	}
	
	@Override
	public void differentiate() {
		double dt = resolution * integration.instrument.samplingInterval;
		WeightedPoint last = new WeightedPoint(value[0], weight[0]);
		WeightedPoint point = new WeightedPoint();
		
		for(int t=1; t<value.length; t++) {
			point.value = value[t];
			point.weight = weight[t];
			
			point.subtract(last);
			point.scale(1.0/dt);
			
			value[t-1] = (float) point.value;
			weight[t-1] = (float) point.weight;
			
			last = point;
		}
		
		float temp = value[value.length-1];
		value[value.length-1] = value[value.length-2];
		value[value.length-2] = temp;
		
		temp = weight[value.length-1];
		weight[value.length-1] = weight[value.length-2];
		weight[value.length-2] = temp;
		
		for(int t=value.length-2; t>0; t--) {
			value[t] = 0.5F * (value[t] + value[t-1]);
			weight[t] += weight[t-1];
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
			sourceFiltering = new float[mode.channels.size()];
			Arrays.fill(sourceFiltering, 1.0F);
		}
		
		// Calculate the source filtering for this mode...
		int nP = getParms();
		
		int skipFlags = mode.skipChannels;
		
		for(int k=0; k<mode.channels.size(); k++) {
			Channel channel = mode.channels.get(k);
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
	public void smooth(double[] w) {
		int ic = w.length / 2;
		WeightedPoint[] smoothed = new WeightedPoint[value.length];
	
		double norm = 0.0;
		for(int i=0; i<w.length; i++) norm += Math.abs(w[i]);
		
		for(int t=0; t<value.length; t++) {
			smoothed[t] = new WeightedPoint();
			
			int t1 = Math.max(0, t-ic); // the beginning index for the convolution
			int tot = Math.min(value.length, t + w.length - ic);
			int i = ic + t - t1; // the beginning index for the weighting fn.
			
			for( ; t1<tot; t1++, i++) if(weight[t1] > 0.0) {
				double wc = w[i] * weight[t1];
				smoothed[t].value += wc * value[t1];
				smoothed[t].weight += Math.abs(wc);
			}
			if(smoothed[t].weight != 0.0) {
				smoothed[t].value /= smoothed[t].weight;
				smoothed[t].weight /= norm;
			}
		}
		for(int t=0; t<value.length; t++) {
			value[t] = (float) smoothed[t].value;
			weight[t] = (float) smoothed[t].weight;
		}
	}
	
	
	public void noTempStorage() { temp = null; }
	
	public int getParms() {
		int n = 0;
		for(int i=0; i<value.length; i++) if(weight[i] > 0.0) n++;
		return n;
	}
	
	// TODO Use estimators...
	
	
}
