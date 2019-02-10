/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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

import java.util.Arrays;
import java.util.stream.IntStream;

import crush.Channel;
import crush.Frame;
import crush.Integration;
import jnum.Constant;

public abstract class VariedFilter extends Filter {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5414130961462551874L;
	
	protected float[] sourceProfile;
	protected float[] pointResponse;
	
	protected float dp;
	protected double sourceNorm;
	
	
	public VariedFilter(Integration<?> integration) {
		super(integration);
	}

	public VariedFilter(Integration<?> integration, float[] data) {
		super(integration, data);
	}
	
	
	public float[] getSourceProfile() { return sourceProfile; }
	
	@Override
	protected void setIntegration(Integration<?> integration) {
		super.setIntegration(integration);
		
		pointResponse = new float[getInstrument().size()];
		Arrays.fill(pointResponse, 1.0F);
		
		updateSourceProfile();
	}
	
	
	@Override
	protected void preFilter(Channel channel) {
		final double response = pointResponse[channel.getIndex()];
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
		parms.addAsync(channel, rejected);
		
		dp = points >= 0.0 ? (float) (rejected / points) : 0.0F;
		
		final double response = calcPointResponse();
		
		if(Double.isNaN(response)) return;
		
		pointResponse[channel.getIndex()] = (float) response;
		
		channel.directFiltering *= response;
		channel.sourceFiltering *= response;
		
		/*
		double weighRescale = 1.0 - rejected / points;
		channel.weight *= weighRescale;
		*/
	}
	
	
	@Override
	protected void remove(final float value, final Frame exposure, final int channel) {
		if(exposure == null) return;
		exposure.data[channel] -= value;
		if(exposure.sampleFlag[channel] == 0) frameParms[exposure.index] += exposure.relativeWeight * dp;		
	}
	
	@Override
	protected void dftFilter(Channel channel) {
		throw new UnsupportedOperationException("No DFT for adaptive filters.");
	}
	
	
	protected double getPointResponse(Channel channel) {
		return pointResponse[channel.getIndex()];
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
		final double f0 = integration.getModulationFrequency(Frame.TOTAL_POWER) / df;
		final double sigma = Constant.sigmasInFWHM / (Constant.twoPi * T * df);
		final double a = -0.5 / (sigma * sigma);
		

		// just calculate x=0 component -- O(N)
		sourceNorm = IntStream.range(0, sourceProfile.length).parallel()
		        .mapToDouble(f -> sourceProfile[f] = 0.5F * (float) (Math.exp(a*(f-f0)*(f-f0)) + Math.exp(a*(f+f0)*(f+f0))))
		        .sum();
	}
	
	@Override
	protected double calcPointResponse() {
		// Start from the 1/f filter cutoff
		final int minf = getHipassIndex();

		// Below the hipass time-scale, the filter has no effect, so count it as such...
		double sum = IntStream.range(0, minf).parallel().mapToDouble(f -> sourceProfile[f]).sum();
	
		// Calculate the true source filtering above the hipass timescale...
		sum += IntStream.rangeClosed(minf, nf).parallel().mapToDouble(f -> sourceProfile[f] * responseAt(f)).sum();

		return sum / sourceNorm;
	}
}
