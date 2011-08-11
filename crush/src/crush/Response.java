/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import java.lang.reflect.Field;

import util.data.WeightedPoint;

public abstract class Response extends Mode {

	public Response() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Response(ChannelGroup<?> group, Field gainField) {
		super(group, gainField);
		// TODO Auto-generated constructor stub
	}

	public Response(ChannelGroup<?> group) {
		super(group);
		// TODO Auto-generated constructor stub
	}

	public abstract Signal getSignal(Integration<?, ?> integration);
	
	@Override
	public WeightedPoint[] getGains(Integration<?, ?> integration, boolean isRobust) throws IllegalAccessException {
		Signal signal = integration.signals.get(this);
		
		if(signal == null) {
			signal = getSignal(integration);
			if(signal.isFloating) signal.level(isRobust);
			integration.signals.put(this, signal);	
		}
		
		return super.getGains(integration, isRobust);
	}	
	
}
