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

import crush.Channel;
import crush.Frame;
import crush.Integration;

public abstract class AdaptiveFilter extends ProfiledFilter {
	
	// TODO noiseFiltering to be replaced by dependents accounting...
	float[] pointResponse;
	float[][] profiles;
	
	public AdaptiveFilter(Integration<?, ?> integration) {
		super(integration);
	}

	protected AdaptiveFilter(Integration<?,?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);
		
		int nc = integration.instrument.size();
		
		pointResponse = new float[nc];
		profiles = new float[nc][];

		Arrays.fill(pointResponse, 1.0F);		
	}
	
	@Override
	protected void apply(Channel channel) {
		channel.directFiltering /= getPointResponse(channel);
		parms.clear(channel);
		
		super.apply(channel);
		
		double rejected = countParms();
		parms.add(channel, rejected);
		
		final double dp = rejected / integration.getFrameCount(Frame.MODELING_FLAGS);
		for(Frame exposure : integration) if(exposure != null) parms.add(exposure, dp);
		
		pointResponse[channel.index] = (float) calcPointResponse();
		channel.directFiltering *= getPointResponse(channel);
	}
	
	@Override
	protected void dftFilter(Channel channel) {
		throw new UnsupportedOperationException("No DFT for adaptive filters.");
	}
	
	@Override
	protected void fftFilter(Channel channel) {
		updateProfile(channel);
			
		super.fftFilter(channel);
		
		double weighRescale = 1.0 - parms.get(channel) / integration.getFrameCount(Frame.MODELING_FLAGS, channel, ~0);
		channel.weight *= weighRescale;
		
		// Store the applied profile...
		for(int i=profile.length; --i >= 0; ) profiles[channel.index][i] *= profile[i];
	
		pointResponse[channel.index] = (float) calcPointResponse();
		channel.directFiltering *= pointResponse[channel.index];
	}
	
	protected abstract void updateProfile(Channel channel);

	@Override
	protected double responseAt(int fch) {
		if(profile == null) return 1.0;
		return profile[(int) Math.round((double) fch / (nf+1) * profile.length)];
	}
	
	public double getPointResponse(Channel channel) {
		return pointResponse[channel.index];
	}

	

}
