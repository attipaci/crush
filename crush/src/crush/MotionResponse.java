/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import java.util.StringTokenizer;


public abstract class MotionResponse extends Response {

	public MotionResponse() {
		super();
	}

	public MotionResponse(ChannelGroup<?> group, Field gainField) {
		super(group, gainField);
	}

	public MotionResponse(ChannelGroup<?> group) {
		super(group);
	}
	
	public abstract Signal getSignal(Integration<?, ?> integration, Motion direction);

	@Override
	public Signal getSignal(Integration<?, ?> integration) {
		StringTokenizer tokens = new StringTokenizer(name, "-:");
		tokens.nextToken();
		String type = tokens.nextToken();
		
		Motion direction = Motion.forName(type);
		return getSignal(integration, direction);
	}

}
