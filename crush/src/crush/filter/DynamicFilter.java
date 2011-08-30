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

import crush.Channel;
import crush.Integration;

public abstract class DynamicFilter extends ProfiledFilter {
	
	// TODO noiseFiltering to be replaced by dependents accounting...
	float[] noiseFiltering, pointSourceThroughput;
	float[][] profiles;
	
	Channel currentChannel;
	
	public DynamicFilter(Integration<?, ?> integration) {
		super(integration);
	}

	protected DynamicFilter(Integration<?,?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	public void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);
		
		int nc = integration.instrument.size();
		
		noiseFiltering = new float[nc];
		pointSourceThroughput = new float[nc];
		profiles = new float[nc][];
		
		Arrays.fill(noiseFiltering, 1.0F);
		Arrays.fill(pointSourceThroughput, 1.0F);		
	}
	
	@Override
	public void filter(Channel channel) {
		currentChannel = channel;
		profile = profiles[channel.index];
		
		super.filter(channel);
		// TODO add updateProfile() in filtering sequence...
	}
	
	public abstract float[] getIncrementalProfile();

	@Override
	public double throughputAt(int fch) {
		if(profile == null) return 1.0;
		return profile[(int) Math.round((double) fch / (nf+1) * profile.length)];
	}

}
