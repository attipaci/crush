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

package crush.filter;

import java.util.Arrays;
import java.util.Hashtable;

import crush.Channel;
import crush.ChannelGroup;
import crush.Dependents;
import crush.Frame;
import crush.Integration;

import util.SphericalCoordinates;
import util.Util;
import util.data.FFT;

public abstract class Filter {
	protected Integration<?,?> integration;
	protected ChannelGroup<?> channels;
	protected float[] data;
	protected int nf;
	protected double df;	
	
	boolean isGlobal = true;
	boolean dft = false;
	
	public Filter(Integration<?,?> integration) {
		setIntegration(integration);
	}
	
	protected Filter(Integration<?,?> integration, float[] data) {
		this.data = data;
		setIntegration(integration);
	}
	
	public abstract String getID();
	
	public abstract double throughputAt(int fch);
	
	public abstract double countParms();
	
	public double rejectionAt(int fch) {
		return 1.0 - throughputAt(fch);
	}
	
	protected void setIntegration(Integration<?,?> integration) {
		this.integration = integration;
		
		int nt = FFT.getPaddedSize(integration.size());
		
		if(data == null) data = new float[nt];
		else if(data.length != nt) data = new float[nt];
		
		nf = nt >> 1;
		df = 1.0 / (integration.instrument.samplingInterval * nt);
		
		if(channels == null) channels = integration.instrument;
	}
	
	public void setChannels(ChannelGroup<?> channels) {
		this.channels = channels;
	}
	
	// Allows to adjust the FFT filter after the channel spectrum has been loaded
	public void update() {}
	
	// TODO point source filtering...
	public void filter() {
		integration.comments += getID();
		Dependents parms = integration.getDependents(getClass().getSimpleName());
			
		double rejected = countParms();	
		
		for(Channel channel : channels) {
			parms.clear(channel);
			filter(channel);
			parms.add(channel, rejected);
		}
		
		final double dp = rejected / integration.getFrameCount(Frame.MODELING_FLAGS);
		for(Frame exposure : integration) if(exposure != null) parms.add(exposure, dp);
		
		parms.apply(channels, 0, integration.size());
	}
	
	protected void loadTimeStream(Channel channel) {
		final int c = channel.index;
		
		// Load the channel data into the data array
		for(int t = integration.size(); --t >= 0; ) {
			final Frame exposure = integration.get(t);

			if(exposure == null) data[t] = Float.NaN;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = Float.NaN;
			else if(exposure.sampleFlag[c] != 0) data[t] = Float.NaN;
			else data[t] = exposure.relativeWeight * exposure.data[c];
		}
		
		// Remove DC component
		levelData();
	}
	
	public void filter(Channel channel) {
		loadTimeStream(channel);
		
		// Apply the filter, with the rejected signal written to the local data array
		if(dft) dftFilter(channel);
		else fftFilter(channel);
		
		// Remove the DC component...
		levelDataFor(channel);
		
		// Subtract the rejected signal...
		final int c = channel.index;
		for(int t = integration.size(); --t >= 0; ) {
			final Frame exposure = integration.get(t);
			if(exposure != null) exposure.data[c] -= data[t];	
		}
	}
	
	// Convert data into a rejected signal (unlevelled)
	public synchronized void fftFilter(Channel channel) {	
		Arrays.fill(data, integration.size(), data.length, 0.0F);
		
		FFT.forwardRealInplace(data);
		
		update();
		
		data[0] = 0.0F;
		data[1] *= rejectionAt(nf);
		
		for(int i=2; i<data.length; i+=2) {
			final double rejection = rejectionAt(i >> 1);
			data[i] *= rejection;
			data[i+1] *= rejection; 	
		}
		
		FFT.backRealInplace(data);
	}
	
	// Convert data into a rejected signal (unlevelled)
	public synchronized void dftFilter(Channel channel) {
		float[] rejected = new float[integration.size()];
		for(int f=nf+1; --f >= 0; ) {
			final double rejection = rejectionAt(f);
			if(rejection > 0.0) dftFilter(channel, f, rejection, rejected);
		}
		System.arraycopy(rejected, 0, data, 0, rejected.length);
	}
	
	public double getPointSourceThroughput() {
		// Assume Gaussian source profile under crossing time
		// sigmaT sigmaw = 1
		// T/2.35 * 2pi * sigmaf = 1
		// sigmaf = 2.35/2pi * 1/T;
		// df = 1 / (n dt)
		// sigmaF = sigmaf / df = 2.35/2Pi * n dt / T; 
		
		final double T = integration.getPointCrossingTime();
		final double sigma = Util.sigmasInFWHM / (SphericalCoordinates.twoPI * T * df);
		final double a = -0.5 / (sigma * sigma);
		
		// Start from the 1/f filter cutoff
		double hipassf = 0.5 / integration.filterTimeScale;
		int minf = (int) Math.ceil(hipassf / df);
		
		double sum = 0.0, norm = 0.0;;
		
		// just calculate x=0 component -- O(N)
		// Below the hipass time-scale, the filter has no effect, so count it as such...
		for(int f=minf; --f >= 0; ) sum += Math.exp(a*f*f);
		norm = sum;
		
		// Calculate the true source filtering above the hipass timescale...
		for(int f=minf; f <= nf; f++) {
			double A = Math.exp(a*f*f);
			sum += A * throughputAt(f);
			norm += A;
		}
		
		// TODO check normalization...
		return sum / norm;
	}
	
	
	public double getNoiseWhitening() {		
		double sumPreserved = 0.0;
		int minf = getMinIndex();
		
		// just calculate x=0 component O(N)
		for(int f=minf; f <= nf; f++) {
			double phi = throughputAt(f);
			sumPreserved += phi * phi;
		}
		
		return (nf > minf) ? sumPreserved /= nf - minf : 1.0;
	}
	
	public int getMinIndex() {
		double hipassf = 0.5 / integration.filterTimeScale;
		
		if(Double.isNaN(hipassf)) return 1;
		if(hipassf < 0.0) return 1;
		
		return (int) Math.ceil(hipassf / df);
	}
	
	
	protected void levelDataFor(Channel channel) {
		levelFor(channel, data);
	}
		
	protected void levelFor(Channel channel, float[] signal) {
		final int c = channel.index;
		double sum = 0.0, sumw = 0.0;
		
		for(int t=integration.size(); --t >= 0; ) {
			final Frame exposure = integration.get(t);
			if(exposure == null) continue;
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
			if(exposure.sampleFlag[c] != 0) continue;
			
			sum += exposure.relativeWeight * signal[t];
			sumw += exposure.relativeWeight;
		}
		if(sumw <= 0.0) Arrays.fill(signal, 0, integration.size(), 0.0F);
		else {
			float ave = (float) (sum / sumw);
			for(int t=integration.size(); --t >= 0; ) signal[t] -= ave;			
		}
	}
	
	protected void levelData() { level(data); }
	
	protected void level(float[] signal) {
		double sum = 0.0, sumw = 0.0;

		for(int i=integration.size(); --i >= 0; ) if(!Float.isNaN(signal[i])) {
			Frame exposure = integration.get(i);
			if(exposure == null) continue;

			sum += signal[i];
			sumw += integration.get(i).relativeWeight;
		}

		final float level = (float) (sum / sumw);

		for(int i=integration.size(); --i >= 0; ) {
			if(Float.isNaN(signal[i])) signal[i] = 0.0F;
			else signal[i] -= level;
		}
	}
	
	public void setDFT(boolean value) { dft = value; }
	
	public boolean isDFT() { return dft; }
	
	protected void dftFilter(Channel channel, int F, double rejection, float[] rejected) {		
		double sumc = 0.0, sums = 0.0;
		
		if(F == 0) F = data.length >> 1;
		
		final double dw = 2.0 * Math.PI / data.length;
		final double c0 = Math.cos(F * dw);
		final double s0 = Math.sin(F * dw);
		
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

	public static void register(Class<Filter> filterClass, String name) {
		registry.put(name, filterClass);
	}
	
	public static Class<Filter> forName(String name) {
		return registry.get(name);
	}
	
	static Hashtable<String, Class<Filter>> registry = new Hashtable<String, Class<Filter>>();
	
}
