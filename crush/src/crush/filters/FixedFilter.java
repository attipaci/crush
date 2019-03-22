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


import crush.Channel;
import crush.Frame;
import crush.Integration;

public abstract class FixedFilter extends Filter {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8317718290223682350L;
	

	private double rejected = 0.0;
	
	
	public FixedFilter(Integration<?> integration) {
		super(integration);
	}

	public FixedFilter(Integration<?> integration, float[] data) {
		super(integration, data);
	}

	@Override 
	protected void preFilter() {
		super.preFilter();
		rejected = countParms();
	}
	
	@Override
	protected void postFilter() {
		super.postFilter();	
	}
	
	@Override
	protected void preFilter(Channel channel) {
		super.preFilter(channel);
		double response = getPointResponse(channel);
		channel.directFiltering /= response;
		channel.sourceFiltering /= response;
	}
	
	@Override
	protected void postFilter(Channel channel) {
		super.postFilter(channel);
		
		double response = calcPointResponse(channel);
		setPointResponse(channel, response);
		channel.directFiltering *= response;
		channel.sourceFiltering *= response;
		
		parms.addAsync(channel, rejected);		
		
		if(points > 0.0 && frameParms != null) addFrameParms(channel);
	}
	
	protected void addFrameParms(Channel channel) {
		final double dp = rejected / points;
		final int c = channel.getIndex();
		
		integration.validParallelStream(Frame.MODELING_FLAGS).filter(f -> f.sampleFlag[c] == 0)
		.forEach(f -> frameParms[f.index] += f.relativeWeight * dp);
	}
	

}
