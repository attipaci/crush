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
// Copyright (c) 2010  Attila Kovacs

package crush;

import util.Util;
import util.data.Statistics;
import util.data.WeightedPoint;

import java.io.*;

public class Signal implements Cloneable {
	private Mode mode;
	Integration<?, ?> integration;
	public float[] value, drifts;
	public float[] syncGains;
	int resolution;
	int driftN;
	boolean isFloating = false;
	
	Signal(Mode mode, Integration<?, ?> integration) {
		this.mode = mode;
		this.integration = integration;
		if(mode != null) {
			syncGains = new float[mode.channels.size()];
			integration.signals.put(mode, this);
		}
	}
	
	Signal(Mode mode, Integration<?, ?> integration, double[] values, boolean isFloating) {
		this(mode, integration);
		resolution = (int) Math.ceil((double) integration.size() / values.length);
		this.value = new float[values.length];
		for(int i=values.length; --i >= 0; ) this.value[i] = (float) values[i];
		driftN = values.length+1;
	}
	
	
	public Mode getMode() {
		return mode;
	}
	
	public int length() { return value.length; }
	
	public int getResolution() {
		return resolution;
	}
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public final float valueAt(final Frame frame) {
		return value[frame.index / resolution]; 
	}
		
	public synchronized void scale(double factor) {
		float fValue = (float) factor;
		for(int t=value.length; --t >= 0; ) value[t] *= fValue;
		if(drifts != null) for(int T=drifts.length; --T >= 0; ) drifts[T] *= fValue;
		for(int k=syncGains.length; --k >= 0; ) syncGains[k] /= fValue;
	}
	
	public synchronized void add(double x) {
		float fValue = (float) x;
		for(int t=value.length; --t >= 0; ) value[t] += fValue;
		if(drifts != null) for(int T=drifts.length; --T >= 0; ) drifts[T] += fValue;
	}
	
	public synchronized void subtract(double x) {
		float fValue = (float) x;
		for(int t=value.length; --t >= 0; ) value[t] -= fValue;
		if(drifts != null) for(int T=drifts.length; --T >= 0; ) drifts[T] -= fValue;
	}
	
	public synchronized void addDrifts() {
		if(drifts == null) return;
		
		for(int fromt=0, T=0; fromt<value.length; fromt+=driftN) {
			final int tot = Math.min(fromt + driftN, value.length);
			for(int t=tot; --t >= fromt; ) value[t] += drifts[T];
		}
		
		drifts = null;
	}
	
	public double getRMS() { return Math.sqrt(getVariance()); }
	
	public double getUnderlyingRMS() { return Math.sqrt(getUnderlyingVariance()); }
	
	public double getVariance() {
		double sum = 0.0;
		int n = 0;
		for(int t=value.length; --t >= 0; ) if(!Float.isNaN(value[t])) {
			sum += value[t] * value[t];
			n++;
		}	
		return sum / n;
	}

	public double getUnderlyingVariance() {
		double sum = 0.0;
		int n = 0;
		for(int t=value.length; --t >= 0; ) if(!Float.isNaN(value[t])) {
			sum += value[t] * value[t] - 1.0;
			n++;
		}	
		return sum / n;
	}
	
	
	public synchronized void removeDrifts() {
		int N = integration.framesFor(integration.filterTimeScale) / resolution;
		if(N == driftN) return;
		
		addDrifts();
		
		driftN = N;
		drifts = new float[(int) Math.ceil((double) value.length / driftN)];
		
		for(int fromt=0, T=0; fromt<value.length; fromt+=driftN) {
			double sum = 0.0;
			int n = 0;
			final int tot = Math.min(fromt + driftN, value.length);
			
			for(int t=tot; --t >= fromt; ) if(!Float.isNaN(value[t])) {
				sum += value[t];
				n++;
			}
			if(n > 0) {
				float fValue = (float) (sum / n);
				for(int t=tot; --t >= fromt; ) value[t] -= fValue;
				drifts[T++] = fValue;
			}
		}
	}
	
	
	public synchronized void level(int from, int to) {
		from = (int) Math.floor((double) from / resolution);
		to = (int) Math.ceil((double) from / resolution);
		
		double sum = 0.0;
		int n=0;
		
		for(int t=from; t<to; t++) {
			sum += value[t];
			n++;
		}
		if(n > 0) {
			double ave = sum / n;
			for(int t=from; t<to; t++) value[t] -= ave;
		}
	}
		
	public WeightedPoint getMedian() {
		final float[] temp = new float[value.length];
		for(int t=value.length; --t >= 0; ) if(!Float.isNaN(value[t])) temp[t] = value[t];
		return new WeightedPoint(Statistics.median(temp), Double.POSITIVE_INFINITY);
	}
	
	public WeightedPoint getMean() {
		double sum = 0.0;
		int n = 0;
		for(int t=value.length; --t >= 0; ) if(!Float.isNaN(value[t])) {
			sum += value[t];
			n++;
		}	
		return new WeightedPoint(sum / n, Double.POSITIVE_INFINITY);
	}
	
	// Use a quadratic fit...
	// 
	// f[n] = c
	// f[n-1] = a-b+c
	// f[n+1] = a+b+c
	//
	//  c=f[n]
	//  a=(f[n-1] + f[n+1])/2 - f[n]
	//  b=(f[n+1] - f[n-1])/2
	//
	//  f'=2ax+b --> f'[n]=b --> this is simply the chord!
	//
	// f[n+1] = f[n-1] + 2h f'[n]
	//
	// f[1] = f[0] + h f'[0]
	// f[n] = f[n-1] + h f'[n]
	public synchronized void differentiate() {
		final float dt = (float) (resolution * integration.instrument.samplingInterval);
		
		final int n = value.length;
		final int nm1 = n-1;
		
		// v[n] = f'[n+0.5]
		for(int t=0; t<nm1; t++) value[t] = (value[t+1] - value[t]) / dt;

		// the last value is based on the last difference...
		value[n-1] = value[n-2];
		// otherwise, it's:
		// v[n] = (f'[n+0.5] + f'[n-0.5]) = v[n] + v[n-1]
		for(int t=nm1; --t > 0; ) value[t] = 0.5F * (value[t] + value[t-1]);
		
		isFloating = false;
	}
	
	// Intergate using trapesiod rule...
	public synchronized void integrate() {
		double dt = (float) (resolution * integration.instrument.samplingInterval);		
		double I = 0.0;
		
		float halfLast = 0.0F;
		
		for(int t=0; t<value.length; t++) {
			// Calculate next half increment of h/2 * f[t]
			float halfNext = 0.5F * value[t];
			
			// Add half increments from below and above 
			I += halfLast;
			I += halfNext;
			value[t] = (float) (I * dt);
			
			halfLast = halfNext;
		}
		
		isFloating = true;
	}
	
	public Signal getDifferential() {
		Signal d = (Signal) clone();
		d.differentiate();
		return d;
	}
	
	public Signal getIntegral() {
		Signal d = (Signal) clone();
		d.integrate();
		return d;
	}
	
	public synchronized void level(boolean isRobust) {
		WeightedPoint center = isRobust ? getMedian() : getMean();
		float fValue = (float) center.value();
		for(int t=value.length; --t >= 0; ) value[t] -= fValue;
	}

	
	public void smooth(double FWHM) {
		// create a window with 2 FWHM width and odd number elements;
		int N = 2 * (int)Math.ceil(FWHM / resolution) + 1;
		double[] w = new double[N];
		int ic = N/2;
		double sigma = FWHM/resolution/Util.sigmasInFWHM;
		double A = -0.5 / (sigma * sigma);
		for(int i=N; --i >= 0; ) w[i] = Math.exp(A*(i-ic)*(i-ic));
		smooth(w);
	}
	
	// TODO Use this in ArrayUtil...
	public synchronized void smooth(double[] w) {
		int ic = w.length / 2;
		float[] smoothed = new float[value.length];
			
		for(int t=value.length; --t >= 0; ) {		
			int t1 = Math.max(0, t-ic); // the beginning index for the convolution
			final int tot = Math.min(value.length, t + w.length - ic);
			int i = ic + t - t1; // the beginning index for the weighting fn.
			double sum = 0.0, sumw = 0.0;
			
			for( ; t1<tot; t1++, i++) if(!Float.isNaN(value[t1])) {
				sum += w[i] * value[t1];
				sumw += Math.abs(w[i]);
			}
			if(sumw > 0.0) smoothed[t] = (float) (sum / sumw);
		}
		value = smoothed;
	}
	
	protected synchronized void setSyncGains(float[] G) {
		System.arraycopy(G, 0, syncGains, 0, G.length);
	}
	

	public void print(PrintStream out) {
		out.println("# " + (1.0 / (resolution * integration.instrument.integrationTime)));
		
		for(int t=0; t<value.length; t++) out.println(Util.e3.format(value[t]));
	}
	
	
	protected synchronized WeightedPoint[] getGainIncrement(boolean isRobust) {	
		if(integration.hasOption("signal-response")) 
			integration.comments += "{" + Util.f2.format(getCovariance()) + "}";

		// Precalculate the gain-weight products...
		prepareFrameTempFields();
				
		// Calculate gains here...
		return isRobust ? getRobustGainIncrement() : getMLGainIncrement();
	}
	
	
	protected final WeightedPoint[] getMLGainIncrement() {
		final int[] channelIndex = mode.getChannelIndex();
		
		final WeightedPoint[] dG = new WeightedPoint[mode.channels.size()];
		for(int k=dG.length; --k >= 0; ) dG[k] = new WeightedPoint();
		
		for(final Frame exposure : integration) if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {	
			for(int k=dG.length; --k >= 0; ) {
				final int c = channelIndex[k];
				if(exposure.sampleFlag[c] == 0) {
					final WeightedPoint increment = dG[k];
					increment.add(exposure.tempWC * exposure.data[c]);
					increment.addWeight(exposure.tempWC2);
				}
			}
		}
		for(int k=dG.length; --k >= 0; ) {
			final WeightedPoint increment = dG[k];
			if(increment.weight() > 0.0) increment.scaleValue(1.0 / increment.weight());
		}
		
		return dG;
	}
	
	protected final WeightedPoint[] getRobustGainIncrement() {
		final int[] channelIndex = mode.getChannelIndex();
		
		final WeightedPoint[] dG = new WeightedPoint[channelIndex.length];
		for(int k=dG.length; --k >= 0; ) dG[k] = new WeightedPoint();
		
		// Allocate storage for sorting if estimating robustly...
		final WeightedPoint[] gainData = new WeightedPoint[integration.size()];
		for(int t=integration.size(); --t >= 0; ) gainData[t] = new WeightedPoint();
	
		for(int k=dG.length; --k >= 0; ) {
			int n=0;
			final int c = channelIndex[k];
			final WeightedPoint increment = dG[k];
			for(final Frame exposure : integration) if(exposure != null) 
				if(exposure.tempWC2 > 0.0) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] == 0)  {
					final WeightedPoint point = gainData[n++];
					point.setValue(exposure.data[c] / exposure.tempC);
					point.setWeight(exposure.tempWC2);
					increment.addWeight(point.weight());
					
					assert !Double.isNaN(point.value());
					assert !Double.isInfinite(point.value());
				}
			Statistics.smartMedian(gainData, 0, n, 0.25, increment);
		}
		
		return dG;
	}

	

	protected synchronized void syncGains(float[] sumwC2, boolean isTempReady) throws IllegalAccessException {
		Mode mode = getMode();
		if(mode.fixedGains) throw new IllegalStateException("WARNING! Cannot change gains for fixed gain modes.");
		
		final ChannelGroup<?> channels = mode.channels;
		final int nc = channels.size();
		final Dependents parms = integration.getDependents("gains-" + mode.name);
		
		final float[] G = mode.getGains();
		final float[] dG = syncGains;
		final int[] channelIndex = mode.getChannelIndex();
		
		for(int k=nc; --k >=0; ) dG[k] = G[k] - dG[k];
		
		parms.clear(channels, 0, integration.size());

		// Precalculate the gain-weight products...
		if(!isTempReady) prepareFrameTempFields();

		// Sync to data and calculate dependeces...
		for(final Frame exposure : integration) if(exposure != null) {
			for(int k=nc; --k >=0; ) if(sumwC2[k] > 0.0) {
				exposure.data[channelIndex[k]] -= dG[k] * exposure.tempC;
				parms.add(exposure, exposure.tempWC2 / sumwC2[k]);
			}
		}

		// Account for the one gain parameter per channel...
		for(int k=nc; --k >= 0; ) if(sumwC2[k] > 0.0) parms.add(channels.get(k), 1.0);
		
		// Apply the mode dependeces...
		parms.apply(channels, 0, integration.size());
	
		// Register the gains as the ones used for the signal...
		setSyncGains(G);
		
		if(CRUSH.debug) integration.checkForNaNs(channels, 0, integration.size());
	}
	
	
	private void prepareFrameTempFields() {
		for(final Frame exposure : integration) if(exposure != null) {
			exposure.tempC = valueAt(exposure);
			if(Float.isNaN(exposure.tempC)) exposure.tempC = 0.0F;
			exposure.tempWC = exposure.isUnflagged(Frame.MODELING_FLAGS) ? exposure.relativeWeight * exposure.tempC : 0.0F;
			exposure.tempWC2 = exposure.tempWC * exposure.tempC;
		}
	}
	
	public double getCovariance() {
		ChannelGroup<?> channels = mode.channels.getChannels().discard(~0);
		int nc = integration.instrument.size();
		
		final double[] sumXS = new double[nc];
		final double[] sumX2 = new double[nc];
		final double[] sumS2 = new double[nc];
		
		for(final Frame exposure : integration) if(exposure != null) {
			for(final Channel channel : channels) if(exposure.sampleFlag[channel.index] == 0) {
				final int c = channel.index;
				final float x = exposure.data[c];
				final float S = valueAt(exposure);
				if(!Float.isNaN(S)) {
					sumXS[c] += channel.weight * x * S;
					sumX2[c] += channel.weight * x * x;
					sumS2[c] += channel.weight * S * S;
				}
			}	
		}
		
		double C2 = 0.0;
		for(Channel channel : channels) {
			final int c = channel.index;
			if(sumS2[c] > 0.0) C2 += sumXS[c] * sumXS[c] / (sumX2[c] * sumS2[c]);
		}
		
		return Math.sqrt(C2);
	}
	
	@Override
	public String toString() { return "Signal " + integration.getFullID("|") + "." + mode.getName(); }
	
}
