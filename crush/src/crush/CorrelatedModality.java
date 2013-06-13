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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.lang.reflect.*;

import kovacs.math.Range;
import kovacs.util.Configurator;


public class CorrelatedModality extends Modality<CorrelatedMode> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1124638494612727550L;
	
	public boolean solveSignal = true;
	public Range gainRange = new Range();
	
	public CorrelatedModality(String name, String id) {
		super(name, id);
	}
	
	public CorrelatedModality(String name, String id, ChannelDivision<?> division) {
		super(name, id, division, CorrelatedMode.class);
	}
	
	public CorrelatedModality(String name, String id, ChannelDivision<?> division, Field gainField) { 
		this(name, id, division, new FieldGainProvider(gainField));
	}
	
	public CorrelatedModality(String name, String id, ChannelDivision<?> division, GainProvider gainSource) { 
		super(name, id, division, gainSource, CorrelatedMode.class);
	}
	
	public CorrelatedModality(String name, String id, ChannelDivision<?> division, Class<? extends CorrelatedMode> modeClass) {
		super(name, id, division, modeClass);		
	}
	
	public CorrelatedModality(String name, String id, ChannelDivision<?> division, Field gainField, Class<? extends CorrelatedMode> modeClass) {
		this(name, id, division, new FieldGainProvider(gainField), modeClass);
	}
	
	public CorrelatedModality(String name, String id, ChannelDivision<?> division, GainProvider gainSource, Class<? extends CorrelatedMode> modeClass) {
		super(name, id, division, gainSource, modeClass);
	}
	
	@Override
	public void setOptions(Configurator option) {
		super.setOptions(option);
		solveSignal = !option.isConfigured("nosignals");
		
		boolean solvePhases = option.isConfigured("phases");
		for(CorrelatedMode mode : this) mode.solvePhases = solvePhases;
	}
	
	public void setSkipFlags(int pattern) {
		for(CorrelatedMode mode : this) mode.skipChannels = pattern;
	}
	
	/*
	public void scaleSignals(Integration<?,?> integration, double aveG) {
		for(CorrelatedMode mode : this) if(!mode.fixedSignal) mode.scaleSignals(integration, aveG);
	}
	*/

	public void updateSignals(Integration<?, ?> integration, boolean isRobust) {	
		for(CorrelatedMode mode : this) if(!mode.fixedSignal) {
			if(!Double.isNaN(resolution)) mode.resolution = resolution;
			try { mode.updateSignals(integration, isRobust); }
			catch(Exception e) { e.printStackTrace(); }
		}
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
			for(CorrelatedMode mode : CorrelatedModality.this) CoupledModality.this.add(mode.new CoupledMode(source));
			CoupledModality.this.setDefaultNames();
		}
		
	}
	
}
