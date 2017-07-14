/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.filters;

import java.io.Serializable;
import java.util.Arrays;

import crush.Channel;
import crush.ChannelGroup;
import crush.Dependents;
import crush.Frame;
import crush.Integration;
import jnum.Configurator;
import jnum.Constant;
import jnum.ExtraMath;
import jnum.Util;
import jnum.parallel.ParallelTask;


public abstract class Filter implements Serializable, Cloneable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 9018904303446615792L;
	
	protected Integration<?,?> integration;
	protected Dependents parms;
	protected float[] frameParms;
	private ChannelGroup<?> channels;
	
	protected int nt, nf;
	protected double df, points;
	
	boolean dft = false;
	boolean isEnabled = false;
	
	boolean isPedantic = false;
	
	private float[] data;
	
	public Filter(Integration<?,?> integration) {
		setIntegration(integration);
	}
	
	public Filter(Integration<?,?> integration, float[] data) {
		this.data = data;
		setIntegration(integration);
	}
	
	@Override
	public Object clone() {
		try {
			Filter clone = (Filter) super.clone();
			clone.data = null;
			clone.frameParms = null;
			return clone;
		} catch(CloneNotSupportedException e) { return null; }
	}
	
	protected void makeTempData() {
		if(data != null) discardTempData();
		data = integration.getFloats();
	}
	
	protected void discardTempData() {
		Integration.recycle(data);
		data = null;
	}
	
	public boolean isEnabled() {
		return isEnabled;
	}
	
	public float[] getTempData() { return data; }
	
	public void setTempData(float[] data) { this.data = data; }
	
	public abstract String getID();
	
	public abstract String getConfigName();
	
	protected abstract double responseAt(int fch);
	
	protected double countParms() {
		final int minf = getHipassIndex();
		double parms = 0.0;
		for(int f = nf; --f >= minf; ) parms += rejectionAt(f);
		return parms;
	}
	
	protected abstract double getMeanPointResponse();
	
	public double rejectionAt(int fch) {
		return 1.0 - responseAt(fch);
	}
	
	protected void setIntegration(Integration<?,?> integration) {
		this.integration = integration;
		
		nt = ExtraMath.pow2ceil(integration.size());	
		nf = nt >>> 1;
		df = 1.0 / (integration.instrument.samplingInterval * nt);
		
		if(getChannels() == null) setChannels(integration.instrument);
	}
	
	public ChannelGroup<?> getChannels() {
		return channels;
	}
	
	public void setChannels(ChannelGroup<?> channels) {
		this.channels = channels;
	}
	
	public boolean hasOption(String key) { 
		return integration.hasOption(getConfigName() + "." + key);
	}
	
	public Configurator option(String key) {
		return integration.option(getConfigName() + "." + key);
	}
	
	public void updateConfig() {
		isEnabled = integration.hasOption(getConfigName());	
		isPedantic = integration.hasOption("filter.mrproper");
	}
	
	// Allows to adjust the FFT filter after the channel spectrum has been loaded
	protected void updateProfile(Channel channel) {}
	
	public final boolean apply() { return apply(true); }
		
	public final boolean apply(boolean report) {
		
		updateConfig();
		
		if(!isEnabled()) return false;
		
		integration.comments += getID();
		
		preFilter();
		
		getChannels().new Fork<float[]>() {
			private Filter worker;

			@Override
			protected void init() {
				super.init();
				worker = (Filter) Filter.this.clone();
				worker.makeTempData();
				
				worker.frameParms = integration.getFloats();
				Arrays.fill(worker.frameParms, 0, integration.size(), 0.0F);
			}
			
			@Override
			protected void cleanup() {
				super.cleanup();
				worker.discardTempData();
			}
			
			@Override
			public float[] getLocalResult() { return worker.frameParms; }
			
			@Override
			protected void postProcess() {
				super.postProcess();
				
				for(ParallelTask<float[]> task : getWorkers()) {
					float[] localFrameParms = task.getLocalResult();
					parms.addForFrames(localFrameParms);
					Integration.recycle(localFrameParms);
				}
			}
			
			@Override
			protected void process(Channel channel) {
				worker.applyTo(channel);
			}
			
		}.process();
		
			
		postFilter();
		
		if(report) report();
		
		return true;
	}
	
	private void applyTo(Channel channel) {
		boolean localTemp = (data == null);
		if(localTemp) makeTempData();
		
		loadTimeStream(channel);
		
		preFilter(channel);
		
		// Apply the filter, with the rejected signal written to the local data array
		if(dft) dftFilter(channel);
		else fftFilter(channel);
		
		if(isPedantic) levelDataForChannel(channel);
		
		postFilter(channel);
		
		remove(channel);
		
		if(localTemp) discardTempData();
	}
	
	protected void preFilter() {
		if(parms == null) parms = integration.getDependents(getConfigName());
		parms.clear(getChannels(), 0, integration.size());
	}
	
	protected void postFilter() {
		parms.apply(getChannels(), 0, integration.size());	
	}
	
	protected void preFilter(Channel channel) {}
	
	protected void postFilter(Channel channel) {
		// Remove the DC component...
		//levelDataFor(channel);
	}
	
	protected void remove(Channel channel) {
		// Subtract the rejected signal...
		final int c = channel.index;
		for(int t = integration.size(); --t >= 0; ) remove(data[t], integration.get(t), c);
	}
	
	protected void remove(final float value, final Frame exposure, final int channel) {
		if(exposure == null) return;
		exposure.data[channel] -= value;
	}
	
	public void report() {
		integration.comments += integration.instrument.mappingChannels > 0 ? 
				"(" + Util.f2.format(getMeanPointResponse()) + ")" :
				"(---)";
	}
	
	// TODO smart timestream access...
	protected void loadTimeStream(Channel channel) {
		final int c = channel.index;
		
		points = 0.0;
		
		double sum = 0.0;
		int n=0;
		
		// Load the channel data into the data array
		for(int t = integration.size(); --t >= 0; ) {
			final Frame exposure = integration.get(t);

			if(exposure == null) data[t] = Float.NaN;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = Float.NaN;
			else if(exposure.sampleFlag[c] != 0) data[t] = Float.NaN;
			else {
				sum += (data[t] = exposure.relativeWeight * exposure.data[c]);
				points += exposure.relativeWeight;
				n++;
			}
		}
			
		// Remove the DC offset...
		if(n > 0) {
			final float ave = (float) (sum / n);
			for(int t = integration.size(); --t >= 0; ) {
				if(Float.isNaN(data[t])) data[t] = 0.0F;
				else data[t] -= ave;			
			}
		}
		else Arrays.fill(data, 0, integration.size(), 0.0F);
	}
	
	
	// Convert data into a rejected signal (unlevelled)
	protected void fftFilter(Channel channel) {
		// Pad with zeroes as necessary...
		Arrays.fill(data, integration.size(), data.length, 0.0F);
		
		integration.getFFT().real2Amplitude(data);
		
		updateProfile(channel);
		
		data[0] = 0.0F;
		data[1] *= rejectionAt(nf);
		
		for(int i=2; i<data.length; ) {
			final double rejection = rejectionAt(i >>> 1);
			data[i++] *= rejection;
			data[i++] *= rejection; 	
		}
		
		integration.getFFT().amplitude2Real(data);
	}
	
	// Convert data into a rejected signal (unlevelled)
	protected void dftFilter(Channel channel) {
		// TODO make rejected a private field, initialize or throw away as needed (setDFT())
		float[] rejected = new float[integration.size()];
		for(int f=nf+1; --f >= 0; ) {
			final double rejection = rejectionAt(f);
			if(rejection > 0.0) dftFilter(channel, f, rejection, rejected);
		}
		System.arraycopy(rejected, 0, data, 0, rejected.length);
	}
	
	// What about non-total power modulations...
	protected double calcPointResponse() {
		// Assume Gaussian source profile under crossing time
		// sigmaT sigmaw = 1
		// T/2.35 * 2pi * sigmaf = 1
		// sigmaf = 2.35/2pi * 1/T;
		// df = 1 / (n dt)
		// sigmaF = sigmaf / df = 2.35/2Pi * n dt / T; 
		
		final double T = integration.getPointCrossingTime();
		final double sigma = Constant.sigmasInFWHM / (Constant.twoPi * T * df);
		final double a = -0.5 / (sigma * sigma);
		final double f0 = integration.getModulationFrequency(Frame.TOTAL_POWER) / df;
		
		
		// Start from the 1/f filter cutoff
		int minf = getHipassIndex();
		
		double sum = 0.0;
		
		// just calculate x=0 component -- O(N)
		// Below the hipass time-scale, the filter has no effect, so count it as such...
		for(int f=minf; --f >= 0; ) {
		    sum += Math.exp(a*(f-f0)*(f-f0)) + Math.exp(a*(f+f0)*(f+f0));
		}
		double norm = sum;
		
		// Calculate the true source filtering above the hipass timescale...
		// Consider a symmetric source profile, peaked at the origin --
		// Its peak is simply the sum of the cosine terms, which are the real part of the amplitudes.
		// So, the filter correction is simply the ratio of the sum of the filtered real amplitudes
		// relative to the sum of the original real amplitudes.
		
		for(int f=nf; --f >= minf; ) {
			double sourceResponse = Math.exp(a*(f-f0)*(f-f0)) + Math.exp(a*(f+f0)*(f+f0));
			sum += sourceResponse * responseAt(f);
			norm += sourceResponse;
		}
		
		return sum / norm;
	}

	
	protected int getHipassIndex() {
		double hipassf = 0.5 / integration.filterTimeScale;
		
		if(Double.isNaN(hipassf)) return 1;
		if(hipassf < 0.0) return 1;
		
		return (int) Math.ceil(hipassf / df);
	}
	
	
	protected void levelDataForChannel(Channel channel) {
		levelForChannel(channel, data);
	}
		
	protected void levelForChannel(Channel channel, float[] signal) {	
		final int c = channel.index;
		double sum = 0.0;
		int n = 0;
		
		for(int t=integration.size(); --t >= 0; ) {
			final Frame exposure = integration.get(t);
			if(exposure == null) continue;
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
			if(exposure.sampleFlag[c] != 0) continue;
			
			sum += signal[t];
			n++;
		} 
		if(n > 0) {
			final float ave = (float) (sum / n);
			for(int t=integration.size(); --t >= 0; ) signal[t] -= ave;			
		}
		else Arrays.fill(signal, 0, integration.size(), 0.0F);
	}
	
	protected void levelData() { level(data); }
	
	protected void level(float[] signal) {
		double sum = 0.0;
		int n = 0;

		for(int i=integration.size(); --i >= 0; ) if(!Float.isNaN(signal[i])) {
			sum += signal[i];
			n++;
		}

		final float level = (float) (sum / n);

		if(n > 0) for(int i=integration.size(); --i >= 0; ) {
			if(Float.isNaN(signal[i])) signal[i] = 0.0F;
			else signal[i] -= level;
		}
	}
	
	public void setDFT(boolean value) { dft = value; }
	
	public boolean isDFT() { return dft; }
	
	protected void dftFilter(Channel channel, int F, double rejection, float[] rejected) {		
		double sumc = 0.0, sums = 0.0;
		
		if(F == 0) F = data.length >> 1;
		
		final double theta = F * Constant.twoPi / data.length;
		final double s0 = Math.sin(theta);
		final double c0 = Math.cos(theta);
		
		double c = 1.0;
		double s = 0.0;
		
		// 27 real ops per frequency...
		for(int t = integration.size(); --t >= 0; ) {
			final double x = data[t];
			
			sumc += c * x;
			sums += s * x;
			
			final double temp = c;
			c = temp * c0 - s * s0;
			s = temp * s0 + s * c0;
		}
		
		final double norm = 2.0 / data.length * rejection;
		sumc *= norm;
		sums *= norm;
		
		c = 1.0;
		s = 0.0;
		
		// 25 real ops per frequency
		for(int t = integration.size(); --t >= 0; ) {
			rejected[t] += c * sumc + s * sums;
			
			final double temp = c;
			c = temp * c0 - s * s0;
			s = temp * s0 + s * c0;
		}	
	}

	// Get a fixed-length representation of the filter response.
	protected void getFilterResponse(float[] response) {
		final double n = (double) (nf+1) / response.length;
		
		for(int i=response.length; --i >= 0; ) {
			final int fromf = (int) Math.round(i * n); 
			final int tof = (int) Math.round((i+1) * n);
			
			if(tof == fromf) response[i] = (float) responseAt(fromf);
			else {
				double sum = 0.0;
				for(int f=tof; --f >= fromf; ) sum += responseAt(f);
				response[i] = (float) (sum / (tof - fromf));
			}
		}
		
	}
	
}
