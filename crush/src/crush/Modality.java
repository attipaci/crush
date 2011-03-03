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

package crush;

import java.lang.reflect.*;
import java.util.*;


import util.Configurator;
import util.Range;
import util.Unit;
import util.data.WeightedPoint;

public class Modality<ModeType extends Mode> extends ArrayList<ModeType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2573110865041365370L;
	
	public String name;
	public String id;
	public String trigger;
	
	public boolean solveGains = true;
	public double resolution = Double.NaN;
	
	protected Modality(String name, String id) {
		this.name = name;
		this.id = id;
	}
	
	public Modality(String name, String id, ChannelDivision<?> division, Field gainField, Class<? extends ModeType> modeClass) { 
		this(name, id, division, modeClass);
		for(Mode mode : this) mode.setGainField(gainField);
	}
	
	public Modality(String name, String id, ChannelDivision<?> division, Class<? extends ModeType> modeClass) {
		this(name, id);
		for(ChannelGroup<?> group : division) {
			try {
				ModeType mode = modeClass.newInstance();
				mode.setChannels(group);
				add(mode);
			}
			catch(Exception e) { e.printStackTrace(); }
		}
		setDefaultNames();
	}
		
	public void setDefaultNames() {
		for(int i=0; i<size(); i++) get(i).name = name + ":" + get(i).channels.name;
	}
	
	public void setOptions(Configurator option) {	
		if(option.isConfigured("nogains")) solveGains = false;	
		
		resolution = option.isConfigured("resolution") ? option.get("resolution").getDouble() * Unit.s : 0.0;
		
		trigger = option.isConfigured("trigger") ? option.get("trigger").getValue() : null;
		
		boolean noGainField = option.isConfigured("nofield");
		setGainRange(option.isConfigured("gainrange") ? option.get("gainrange").getRange() : new Range());
		setGainDirection(option.isConfigured("signed") ? Instrument.GAINS_SIGNED : Instrument.GAINS_BIDIRECTIONAL);
		
		for(Mode mode : this) if(noGainField) mode.gainField = null;
	}

	public void setGainFlag(int pattern) {
		for(Mode mode : this) mode.gainFlag = pattern;
	}
	
	public void setGainRange(Range range) {
		for(Mode mode : this) mode.gainRange = range;
	}
	
	public void setGainDirection(int type) {
		for(Mode mode : this) mode.gainType = type;
	}
	
	
	public boolean updateGains(Integration<?, ?> integration, boolean isRobust) {
		if(!solveGains) return false;
		boolean isFlagging = false;
		
		for(Mode mode : this) if(!mode.fixedGains) {	
			try {
				WeightedPoint[] dG = mode.getGainIncrement(integration, isRobust);
				mode.applyGainIncrement(dG, integration, true);
				mode.incrementGains(dG);
				isFlagging = true;
			}
			catch(IllegalAccessException e) { e.printStackTrace(); }
		}
		
		return isFlagging;
	}

	@Override
	public String toString() {
		String description = getClass().getName() + " '" + id + "':\n";
		for(int i=0; i<size(); i++) 
			description += "  " + get(i).toString() + "\n";
		return description; 
	}
	
}
