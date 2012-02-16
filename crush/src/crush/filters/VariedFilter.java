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

package crush.filters;

import java.util.Arrays;

import util.Constant;
import util.Util;

import crush.Channel;
import crush.Frame;
import crush.Integration;

public abstract class VariedFilter extends Filter {
	protected float[] pointResponse;

	protected float[] sourceProfile;
	protected double sourceNorm;
	
	
	public VariedFilter(Integration<?, ?> integration) {
		super(integration);
	}

	public VariedFilter(Integration<?,?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);
		
		pointResponse = new float[integration.instrument.size()];
		Arrays.fill(pointResponse, 1.0F);
		
		updateSourceProfile();
	}
	
	
	@Override
	protected void preFilter(Channel channel) {
		final double response = pointResponse[channel.index];
		if(response > 0.0) {
			channel.directFiltering /= response;
			channel.sourceFiltering /= response;
		}
		super.preFilter(channel);
	}
		
	
	@Override
	protected void postFilter(Channel channel) {	
		super.postFilter(channel);
		
		final double rejected = countParms();
		parms.add(channel, rejected);
		
		final double dp = rejected / points;
		final int c = channel.index;
		for(Frame exposure : integration) if(exposure != null) 
			if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] == 0)
				parms.add(exposure, dp);
		
		final double response = calcPointResponse();
		pointResponse[channel.index] = (float) response;
		
		channel.directFiltering *= response;
		channel.sourceFiltering *= response;
		
		/*
		double weighRescale = 1.0 - rejected / points;
		channel.weight *= weighRescale;
		*/
	}
	
	@Override
	protected void dftFilter(Channel channel) {
		throw new UnsupportedOperationException("No DFT for adaptive filters.");
	}
	
	@Override
	protected void fftFilter(Channel channel) {				
		super.fftFilter(channel);	
	}
	
	protected double getPointResponse(Channel channel) {
		return pointResponse[channel.index];
	}

	@Override
	protected double getMeanPointResponse() {
		double sumwG2 = 0.0, sumwG = 0.0;
		
		for(Channel channel : getChannels()) if(channel.isUnflagged()) {
			final double G = getPointResponse(channel);
			sumwG2 += channel.weight * G * G;
			sumwG += channel.weight * G;
	
		}
		
		return sumwG2 / sumwG;
	}
	
	protected void updateSourceProfile() {
		sourceProfile = new float[nf+1];
		
		// Assume Gaussian source profile under crossing time
		// sigmaT sigmaw = 1
		// T/2.35 * 2pi * sigmaf = 1
		// sigmaf = 2.35/2pi * 1/T;
		// df = 1 / (n dt)
		// sigmaF = sigmaf / df = 2.35/2Pi * n dt / T; 
		
		final double T = integration.getPointCrossingTime();
		final double sigma = Util.sigmasInFWHM / (Constant.twoPI * T * df);
		final double a = -0.5 / (sigma * sigma);
		
		sourceNorm = 0.0;
		
		// just calculate x=0 component -- O(N)
		for(int f=sourceProfile.length; --f >= 0; ) {
			sourceProfile[f] = (float) Math.exp(a*f*f);
			sourceNorm += sourceProfile[f];
		}
	}
	
	@Override
	protected double calcPointResponse() {
		// Start from the 1/f filter cutoff
		final int minf = getHipassIndex();
		
		double sum = 0.0;
		
		// Below the hipass time-scale, the filter has no effect, so count it as such...
		for(int f=minf; --f >= 0; ) sum += sourceProfile[f];
			
		// Calculate the true source filtering above the hipass timescale...
		for(int f=nf+1; --f >= minf; ) sum += sourceProfile[f] * responseAt(f);	
					
		return sum / sourceNorm;
	}
}
