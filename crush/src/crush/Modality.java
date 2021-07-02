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
// Copyright (c) 2010 Attila Kovacs

package crush;

import java.lang.reflect.*;
import java.util.*;

import crush.instrument.FieldGainProvider;
import crush.instrument.GainProvider;
import jnum.Configurator;
import jnum.Unit;
import jnum.data.WeightedPoint;
import jnum.math.Range;

/**
 * A class representing a collection of similar common {@link Mode}s, by some organizing principle. These modes typically
 * form a disjoint set, whose channels may partially or fully span an {@link Instrument}. 
 * 
 * 
 * A fundamental element of CRUSH is the removal of common modes from the timestream data, typically at the modality level.
 * I.e. CRUSH typically removes common modes for the entire set of similar modes in an instrument at the same time.
 * 
 * 
 * Modalities are normally created via {@link Instrument#createModalities()}.
 * 
 * @author Attila Kovacs
 *
 * @param <ModeType>    The generic type of the modes in this modality.
 */
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
	
	public Modality(String name, String id) {
		this.name = name;
		this.id = id;
	}
	
	public Modality(String name, String id, ChannelDivision<?> division, Field gainField, Class<? extends ModeType> modeClass) { 
		this(name, id, division, new FieldGainProvider(gainField), modeClass);
	}
	
	public Modality(String name, String id, ChannelDivision<?> division, GainProvider gainProvider, Class<? extends ModeType> modeClass) { 
		this(name, id, division, modeClass);
		for(Mode mode : this) mode.setGainProvider(gainProvider);
	}
	
	public Modality(String name, String id, ChannelDivision<?> division, Class<? extends ModeType> modeClass) {
		this(name, id);
		for(int i=0; i<division.size(); i++) {
		    ChannelGroup<?> group = division.get(i);
			try {
				ModeType mode = modeClass.getConstructor().newInstance();
				mode.setChannels(group);
				add(mode);
			}
			catch(Exception e) { CRUSH.error(this, e); }
		}
		setDefaultNames();
	}	
	
	public void setDefaultNames() {
		for(int i=size(); --i >= 0; ) get(i).name = name + ":" + get(i).getChannels().getName();
	}
	
	public void setOptions(Configurator option) {	
		resolution = option.hasOption("resolution") ? option.option("resolution").getDouble() * Unit.s : 0.0;
		trigger = option.hasOption("trigger") ? option.option("trigger").getValue() : null;
		
		solveGains = !option.hasOption("nogains");	
		phaseGains = option.hasOption("phasegains");

		boolean noGainField = option.hasOption("nofield");
		boolean uniformGains = option.hasOption("uniform");
		
		setGainRange(option.hasOption("gainrange") ? option.option("gainrange").getRange() : Range.getFullRange());
		setGainDirection(option.hasOption("signed") ? Instrument.GAINS_SIGNED : Instrument.GAINS_BIDIRECTIONAL);
		
		for(Mode mode : this) {
			if(noGainField) mode.setGainProvider(null);
			mode.phaseGains = phaseGains;
			if(uniformGains) if(!mode.fixedGains) { 
			    try { mode.uniformGains(); }
			    catch(Exception e) { CRUSH.warning(this, e); }
			}
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
	public void averageGains(WeightedPoint[] G, Integration<?> integration, boolean isRobust) throws Exception {
		for(Mode mode : this) if(!mode.fixedGains) {
			final ChannelGroup<?> channels = mode.getChannels();
			final WeightedPoint[] modeGain = mode.deriveGains(integration, isRobust);
			for(int k=modeGain.length; --k >= 0; ) G[channels.get(k).getFixedIndex()].average(modeGain[k]);
		}
	}
	
	// Gain arrays is according to dataIndex
	public boolean applyGains(WeightedPoint[] G, Integration<?> integration) throws Exception {
		boolean isFlagging = false;
		
		for(Mode mode : this) if(!mode.fixedGains) {
			final float[] fG = new float[mode.size()];
			final float[] sumwC2 = new float[mode.size()];
			
			for(int k=fG.length; --k >= 0; ) {
				final WeightedPoint channelGain = G[mode.getChannel(k).getFixedIndex()];
				fG[k] = (float) channelGain.value();
				sumwC2[k] = (float) channelGain.weight();
			}
			
			try { 
				isFlagging |= mode.setGains(fG);
				mode.syncAllGains(integration, sumwC2, true); 			
			}
			catch(IllegalAccessException e) { CRUSH.error(this, e); }
		}
		
		return isFlagging;
	}
	
	
	public boolean updateAllGains(Integration<?> integration, boolean isRobust) {
		if(!solveGains) return false;
		boolean isFlagging = false;
		
		for(Mode mode : this) if(!mode.fixedGains) {	
			try {
				WeightedPoint[] G = mode.deriveGains(integration, isRobust);
				isFlagging |= mode.setGains(WeightedPoint.floatValues(G));
				mode.syncAllGains(integration, WeightedPoint.floatWeights(G), true);
			}
			catch(Exception e) { CRUSH.error(this, e); }
		}
		
		return isFlagging;
	}

	@Override
	public String toString() {
		String description = getClass().getName() + " '" + id + "':\n";
		for(int i=0; i<size(); i++) 
			description += "  " + get(i) + "\n";
		return description; 
	}
	
	public class CoupledModality extends CorrelatedModality {

	    /**
	     * 
	     */
	    private static final long serialVersionUID = 866119477552433909L;


	    public CoupledModality(String name, String id, Field gainField) {
	        this(name, id, new FieldGainProvider(gainField));
	    }

	    public CoupledModality(String name, String id, GainProvider source) {
	        super(name, id);
	        for(Mode mode : Modality.this) CoupledModality.this.add(mode.new CoupledMode(source));
	    }
	}
	
	public class NonLinearity extends Modality<Mode.NonLinearResponse> {
	    /**
         * 
         */
        private static final long serialVersionUID = -7733111770338459357L;

        public NonLinearity(String name, String id, Field gainField) {
            this(name, id, new FieldGainProvider(gainField));
        }

        public NonLinearity(String name, String id, GainProvider source) {
            super(name, id);
            for(Mode mode : Modality.this) NonLinearity.this.add(mode.new NonLinearResponse(source));
        }
	}
		
}
