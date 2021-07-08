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

package crush;

import java.util.*;


/**
 * A class that represent a collection of similar {@link ChannelGroup}s, which are typically grouped by the
 * same organizing principle. These groups are typically a disjoint (non overlapping) set, which may partially
 * or fully span the channels of an {@link Instrument}. For example, each readout MUX may have a corresponding 
 * {@link ChannelGroup}, which together constitude a division based on MUX association.
 * 
 * 
 * In other words, you may have a <code>ChannelDivision</code> named "muxes", which consists of a the
 * channel groups "mux-1", "mux-2", "mux-3" ...
 * 
 * 
 * Similarly, the correlated {@link Mode}s associated to the channel groups in the division, can be similarly
 * grouped into a {@link Modality}, and can be decorrelated together during reduction 
 * (see <code>correlated.&lt?&gt</code> option and sub-options in the GLOSSARY. 
 * 
 * 
 * Channel divisions are typically created via {@link Instrument#createDivisions()}. Most divisions are
 * hard-coded, and often pattern based using {@link Instrument#getDivision(String, java.lang.reflect.Field, int)}.
 * But, divisions can also be defined in the runtime configuration via the <code>division</code> option.
 * 
 * 
 * @see Modality 
 * 
 * @author Attila Kovacs
 *
 * @param <ChannelType>     The generic type of the channels contained within this division.
 */
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
