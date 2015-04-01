/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.array;

import java.util.Collection;

import crush.Instrument;
import crush.InstrumentLayout;
import crush.Pixel;

public class SingleColorLayout<ChannelType extends SingleColorPixel> extends InstrumentLayout<ChannelType> {

	public SingleColorLayout(Instrument<? extends ChannelType> instrument) {
		super(instrument);
	}

	@Override
	public int getPixelCount() {
		return instrument.size();
	}

	@Override
	public Collection<? extends Pixel> getPixels() {
		return instrument.copyGroup();
	}

	@Override
	public Collection<? extends Pixel> getMappingPixels() {
		return instrument.getObservingChannels().copyGroup().discard(~0);	
	}

}
