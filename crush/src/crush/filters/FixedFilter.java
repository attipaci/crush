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

import crush.Channel;
import crush.Frame;
import crush.Integration;

public abstract class FixedFilter extends Filter {

	private double pointResponse = 1.0;
	private double rejected = 0.0;
	
	public FixedFilter(Integration<?, ?> integration) {
		super(integration);
	}

	protected FixedFilter(Integration<?, ?> integration, float[] data) {
		super(integration, data);
	}

	public double getPointResponse() {
		return pointResponse;
	}
	
	@Override
	public void apply() {
		for(Channel channel : channels) channel.directFiltering /= pointResponse;
		
		rejected = countParms();	
		super.apply();
		
		pointResponse = calcPointResponse();
		
		for(Channel channel : channels) channel.directFiltering *= pointResponse;
	}
	
	@Override
	protected void apply(Channel channel) {
		parms.clear(channel);
		
		super.apply(channel);
		
		parms.add(channel, rejected);
		
		final double dp = rejected / integration.getFrameCount(Frame.MODELING_FLAGS);
		for(Frame exposure : integration) if(exposure != null) parms.add(exposure, dp);
	}

}
