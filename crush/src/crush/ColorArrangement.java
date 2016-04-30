/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import java.io.Serializable;
import java.util.List;

import jnum.Configurator;
import jnum.Copiable;

public abstract class ColorArrangement<ChannelType extends Channel> implements Serializable, Cloneable, Copiable<ColorArrangement<ChannelType>> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6144882903894123342L;
	
	public Instrument<? extends ChannelType> instrument;
		
	public void setInstrument(Instrument<? extends ChannelType> instrument) {
		this.instrument = instrument;
	}
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public ColorArrangement<ChannelType> copy()  {
		return (ColorArrangement<ChannelType>) clone();
	}
	
	public boolean hasOption(String key) {
		return instrument.hasOption(key);
	}
	
	public Configurator option(String key) {
		return instrument.option(key);
	}
	
	public void validate(Configurator options) {
		if(hasOption("beam")) instrument.setResolution(option("beam").getDouble() * instrument.getSizeUnitValue());
	}
	
	public abstract int getPixelCount();
	
	public abstract List<? extends Pixel> getPixels();
	
	public abstract List<? extends Pixel> getMappingPixels(int keepFlags);
		
}
