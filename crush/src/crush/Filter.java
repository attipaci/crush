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

import util.Complex;
import util.Util;
import util.data.Data;
import util.data.FFT;

public abstract class Filter {
	Integration<?,?> integration;
	
	private float[] data;
	float[] phi;
	double[] sourceProfile; 
	Complex[] sourceSpectrum, filteredSpectrum;
	double sumpwG = 0.0, aveSourceWhitening = 0.0;
		
	public Filter() {}
	
	public Filter(Integration<?,?> integration) {
		this();
		setIntegration(integration);
	}
	
	public abstract float[] getFilterProfile();
	
	public void setIntegration(Integration<?,?> integration) {
		this.integration = integration;
		
		int nF = integration.framesFor(integration.filterTimeScale) >> 1;
		double sigma = integration.getPointCrossingTime() / Util.sigmasInFWHM / integration.instrument.samplingInterval;
		
		sourceSpectrum = new Complex[nF+1];
		for(int F=sourceSpectrum.length; --F >= 0; ) sourceSpectrum[F] = new Complex(); 
		
		filteredSpectrum = new Complex[nF+1];
		for(int F=filteredSpectrum.length; --F >= 0; ) filteredSpectrum[F] = new Complex(); 
		
		sourceProfile = new double[2*nF];
		sourceProfile[0] = 1.0;
		for(int t=nF; --t > 0; ) {
			double dev = t / sigma;
			double a = Math.exp(-0.5*dev*dev);
			sourceProfile[t] = sourceProfile[sourceProfile.length-t] = a;
		}
		
		FFT.uncheckedForward(sourceProfile, sourceSpectrum);
	}



	
	protected void getSpectrum(Channel channel) {
		int n = FFT.getPaddedSize(integration.size());
		if(data == null) data = new float[n];
		else if(data.length != n) data = new float[n];
		
		// If the filterResponse array does not exist, create it...
		if(channel.filterResponse == null) {
			channel.filterResponse = new float[filteredSpectrum.length - 1];
			Arrays.fill(channel.filterResponse, 1.0F);
		}
		
		// Put the time-stream data into an array, and FFT it...
		integration.getWeightedTimeStream(channel, data);		
		
		// Average power in windows, then convert to rms amplitude
		FFT.forwardRealInplace(data);
	}
	
	protected void filter(Channel channel) {	
		toRejectedSignal();
		level(channel);
		
		final int c = channel.index;
		for(Frame exposure : integration) if(exposure != null) exposure.data[c] -= data[exposure.index];
		
		
		// Now, do some accounting...	
		
		// Get a standard length representation of the filter response
		// to record...
		float[] filterResponse = new float[channel.filterResponse.length];
		Data.resample(phi, filterResponse);
		
		double sumPreserved = 0.0;	
		for(int F=filterResponse.length; --F >= 0; ) {
			channel.filterResponse[F] *= filterResponse[F];				
			final double phi2 = channel.filterResponse[F] * channel.filterResponse[F];
			sumPreserved += phi2;
		}

		
		// Noisewhitening measures the relative noise amplitude after filtering
		double noisePowerWhitening = sumPreserved / filterResponse.length;
		double noiseAmpWhitening = Math.sqrt(noisePowerWhitening);
		// Adjust the weights given that the measured noise amplitude is reduced
		// by the filter from its underlying value...
		channel.weight *= noiseAmpWhitening / channel.noiseWhitening;
		channel.noiseWhitening = noiseAmpWhitening;

		// Figure out how much filtering effect there is on the point source peaks...
		for(int F=filterResponse.length; --F >= 0; ) {
			filteredSpectrum[F].copy(sourceSpectrum[F]);
			filteredSpectrum[F].scale(channel.filterResponse[F]);
		}
		
		// Calculate the filtered source profile...
		FFT.uncheckedBackward(filteredSpectrum, sourceProfile);	
		// Discount the effect of prior whitening...
		if(channel.directFiltering > 0.0) channel.sourceFiltering /= channel.directFiltering;
		// And apply the new values...
		channel.directFiltering = sourceProfile[0];		
		channel.sourceFiltering *= channel.directFiltering;
		
		// To calculate <G> = sum w G^2 / sum w G
		if(channel.isUnflagged()) {
			aveSourceWhitening += channel.directFiltering * channel.directFiltering / channel.variance;
			sumpwG += Math.abs(channel.directFiltering) / channel.variance;
		}
		
	
		
	}
	
	protected void level(Channel channel) {
		final int c = channel.index;
		double sum = 0.0, sumw = 0.0;
		for(int i=integration.size(); --i >= 0; ) {
			final Frame exposure = integration.get(i);
			if(exposure == null) continue;
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
			if(exposure.sampleFlag[c] != 0) continue;
			sum += exposure.relativeWeight * data[i];
			sumw += exposure.relativeWeight;
		}
		if(sumw <= 0.0) Arrays.fill(data, 0, integration.size(), 0.0F);
		else {
			float ave = (float) (sum / sumw);
			for(int i=integration.size(); --i >= 0; ) data[i] -= ave;			
		}
	}
	
	protected void toRejectedSignal() {
		// Calculate the idealized filter (no flags, no padding).
		data[0] *= 1.0 - phi[0];
		data[1] *= 1.0 - phi[phi.length-1];
		
		for(int i=data.length; --i >= 2; ) {
			final int F = (int) ((long)i * phi.length / data.length);
			final float rejection = (float) (1.0 - phi[F]);
			data[i] *= rejection;
			data[--i] *= rejection;
		}
		
		FFT.backRealInplace(data);
	}
	
}
