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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.util.*;

public class ChannelDivision<ChannelType extends Channel> extends Vector<ChannelGroup<ChannelType>> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2792676960859301146L;
	
	public String name;
	
	public ChannelDivision(String name) { this.name = name; }
	
	public ChannelDivision(ChannelGroup<ChannelType> group) { 
		this(group.getName());
		add(group);
		setDefaultNames();
	}
	
	public ChannelDivision(String name, ChannelGroup<ChannelType> group) { 
		this(name);
		add(group);
		setDefaultNames();
	}
	
	public ChannelDivision(String name, Vector<ChannelGroup<ChannelType>> groups) { 
		this(name);
		addAll(groups);
		setDefaultNames();
	}
	
	public void setDefaultNames() {
		for(int i=size(); --i >= 0; ) get(i).setName(name + "-" + (i+1));
	}
	
}
