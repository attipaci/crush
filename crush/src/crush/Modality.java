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
	public boolean phaseGains = false;
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
		for(int i=size(); --i >= 0; ) get(i).name = name + ":" + get(i).channels.name;
	}
	
	public void setOptions(Configurator option) {	
		resolution = option.isConfigured("resolution") ? option.get("resolution").getDouble() * Unit.s : 0.0;
		trigger = option.isConfigured("trigger") ? option.get("trigger").getValue() : null;
		
		if(option.isConfigured("nogains")) solveGains = false;	
		if(option.isConfigured("phasegains")) phaseGains = true;
		
		boolean noGainField = option.isConfigured("nofield");
		
		setGainRange(option.isConfigured("gainrange") ? option.get("gainrange").getRange() : new Range());
		setGainDirection(option.isConfigured("signed") ? Instrument.GAINS_SIGNED : Instrument.GAINS_BIDIRECTIONAL);
		
		for(Mode mode : this) {
			if(noGainField) mode.gainField = null;
			mode.phaseGains = phaseGains;
		}
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
	
	// Gains are stored according to dataIndex
	public void averageGains(WeightedPoint[] G, Integration<?,?> integration, boolean isRobust) throws IllegalAccessException {
		for(Mode mode : this) if(!mode.fixedGains) {
			final ChannelGroup<?> channels = mode.channels;
			final WeightedPoint[] modeGain = mode.getGains(integration, isRobust);
			for(int k=modeGain.length; --k >= 0; ) G[channels.get(k).storeIndex].average(modeGain[k]);
		}
	}
	
	// Gain arrays is according to dataIndex
	public boolean applyGains(WeightedPoint[] G, Integration<?,?> integration) throws IllegalAccessException {
		boolean isFlagging = false;
		
		for(Mode mode : this) if(!mode.fixedGains) {
			final float[] fG = new float[mode.channels.size()];
			final float[] sumwC2 = new float[mode.channels.size()];
			
			for(int k=fG.length; --k >= 0; ) {
				final WeightedPoint channelGain = G[mode.channels.get(k).storeIndex];
				fG[k] = (float) channelGain.value;
				sumwC2[k] = (float) channelGain.weight;
			}
			
			try { 
				isFlagging |= mode.setGains(fG);
				mode.syncAllGains(integration, sumwC2, true); 			
			}
			catch(IllegalAccessException e) { e.printStackTrace(); }
		}
		
		return isFlagging;
	}
	
	public boolean updateAllGains(Integration<?, ?> integration, boolean isRobust) {
		if(!solveGains) return false;
		boolean isFlagging = false;
		
		for(Mode mode : this) if(!mode.fixedGains) {	
			try {
				WeightedPoint[] G = mode.getGains(integration, isRobust);
				isFlagging |= mode.setGains(WeightedPoint.floatValues(G));
				mode.syncAllGains(integration, WeightedPoint.floatWeights(G), true);
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
