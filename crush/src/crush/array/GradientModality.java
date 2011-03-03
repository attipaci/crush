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
// Copyright (c) 2009 Attila Kovacs 

package crush.array;

import crush.ChannelDivision;
import crush.ChannelGroup;
import crush.CorrelatedModality;

public class GradientModality extends CorrelatedModality {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5173363949944068920L;
	
	public GradientModality(String name, String id, ChannelDivision<?> division) {
		super(name, id);
		
		for(ChannelGroup<?> group : division) {
			add(new GradientMode(group, true)); // The horizontal gradient
			add(new GradientMode(group, false)); // The vertical gradient
		}
		setDefaultNames();
	}
}
