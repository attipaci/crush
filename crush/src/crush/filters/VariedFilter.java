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

import crush.Channel;
import crush.Frame;
import crush.Integration;

public abstract class VariedFilter extends Filter {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5414130961462551874L;
	
	protected float dp;

	
	
	public VariedFilter(Integration<?> integration) {
		super(integration);
	}

	public VariedFilter(Integration<?> integration, float[] data) {
		super(integration, data);
	}
	
	
	//public float[] getSourceProfile() { return sourceProfile; 
	
	
	@Override
	protected void preFilter(Channel channel) {
		final double response = getPointResponse(channel);
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
		
		final double response = calcPointResponse(channel);
		
		if(Double.isNaN(response)) return;
		
		setPointResponse(channel, response);
		
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

	

}
