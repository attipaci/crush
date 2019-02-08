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

import java.lang.reflect.*;

import crush.instrument.FieldGainProvider;
import crush.instrument.GainProvider;
import jnum.Configurator;
import jnum.math.Range;

/**
 * A class representing a collection of similar {@link CorrelatedMode}s, by some organizing principle. These modes typically
 * form a disjoint set, whose channels may partially or fully span an {@link Instrument}. 
 * <p>
 * 
 * Besides the hard-coded modalities created explicitly by {@link Instrument#createModalities()}, additional correlated modalities 
 * are automatically created for {@link ChannelDivision}s specified at runtime via the <code>division</code> configuration option.
 * <p>
 * 
 * @author Attila Kovacs <attila@sigmyne.com>
 *
 * @param <ModeType>    The generic type of the correlated modes contained in this modality.
 */
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
		solveSignal = !option.hasOption("nosignals");
	}
	
	public void setSkipFlags(int pattern) {
		for(CorrelatedMode mode : this) mode.skipFlags = pattern;
	}
	
	public void updateSignals(Integration<?> integration, boolean isRobust) {	
		for(CorrelatedMode mode : this) {
			if(!Double.isNaN(resolution)) mode.resolution = resolution;
			try { mode.updateSignals(integration, isRobust); }
			catch(Exception e) { CRUSH.error(this, e); }
		}
	}

	

	
}
