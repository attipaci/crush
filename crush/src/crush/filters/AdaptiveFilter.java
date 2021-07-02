/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
package crush.filters;

import java.util.Arrays;
import java.util.stream.IntStream;

import crush.Channel;
import crush.Integration;
import jnum.data.Statistics;

public abstract class AdaptiveFilter extends VariedFilter {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2499384682820252338L;
	
	// TODO noiseFiltering to be replaced by dependents accounting...
	protected float[][] channelProfiles;
	protected double dF;	
	
	private float[] profile;
		
	public AdaptiveFilter(Integration<?> integration) {
		super(integration);
	}

	@Override
	public AdaptiveFilter clone() {
		AdaptiveFilter clone = (AdaptiveFilter) super.clone();
		if(profile != null) clone.profile = new float[profile.length];
		return clone;
	}
	
	public AdaptiveFilter(Integration<?> integration, float[] data) {
		super(integration, data);
	}
	
	public float[] getProfile() { return profile; }
	
	@Override
	protected void setIntegration(Integration<?> integration) {
		super.setIntegration(integration);
		channelProfiles = new float[getInstrument().size()][];		
	}

	protected void setSize(int nF) {
		if(profile == null) profile = new float[nF];
		else if(profile.length != nF) profile = new float[nF];
		
		dF = 0.5 / (nF * getInstrument().samplingInterval);	

		for(int i=channelProfiles.length; --i >= 0; ) {
			final float[] oldProfile = channelProfiles[i];
			if(oldProfile == null) continue;
			channelProfiles[i] = new float[nF];
			resample(oldProfile, channelProfiles[i]);
		}
	}
	
	protected void resample(final float[] from, final float[] to) {
		final double n = (double) from.length / to.length; 
		
		for(int i=to.length; --i >= 0; ) {
			final int fromj = (int) Math.round(i * n);
			final int toj = (int) Math.round((i+1) * n);
			if(toj == fromj) to[i] = from[fromj];
			else to[i] = Statistics.mean(from, fromj, toj);
		}	
	}
	
	@Override
	protected void preFilter(Channel channel) {
		super.preFilter(channel);
	}
	
	@Override
	protected void postFilter(Channel channel) {
		accumulateProfile(channel);
		super.postFilter(channel);
	}

	protected void accumulateProfile(Channel channel) {	
	    final int c = channel.getIndex();
	    
	    IntStream.range(0, profile.length).parallel().forEach(f -> {
	        channelProfiles[c][f] *= profile[f];
            profile[f] = channelProfiles[c][f];
	    });
	}
	
	@Override
	protected double responseAt(int fch) {
		if(profile == null) return 1.0;	
		return profile[(fch * profile.length) / (nf+1)];
	}
	
	public float[] getValidProfile(Channel channel) {
		float[] response = channelProfiles[channel.getIndex()];
		
		if(response == null) if(profile != null) {
			response = new float[profile.length];
			Arrays.fill(response, 1.0F);
		}
		
		return response;
	}

	@Override
	protected double countParms() {
		double hipassf = 0.5 / (integration.filterTimeScale);
		final int minF = (int) Math.ceil(hipassf / dF);
		
		if(profile == null) return 0.0;
		
		return IntStream.range(minF, profile.length).parallel().mapToDouble(F ->  1.0 - profile[F] * profile[F]).sum();
	}
	
}
