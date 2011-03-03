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
// Copyright (c) 2010 Attila Kovacs 

package crush.polarization;

import java.lang.reflect.Field;

import crush.ChannelGroup;
import crush.Mode;

public class PolarizationMode extends Mode {

	public PolarizationMode() {
		// TODO Auto-generated constructor stub
	}

	public PolarizationMode(ChannelGroup<?> group) {
		super(group);
		// TODO Auto-generated constructor stub
	}

	public PolarizationMode(ChannelGroup<?> group, Field gainField) {
		super(group, gainField);
		// TODO Auto-generated constructor stub
	}

	
	public static final int I = nextMode++;
	public static final int Q = nextMode++;
	public static final int U = nextMode++;
	public static final int V = nextMode++;
}
