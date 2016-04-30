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

import java.util.List;

import crush.ColorArrangement;
import crush.Pixel;

public class SingleColorArrangement<ChannelType extends SingleColorPixel> extends ColorArrangement<ChannelType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3711770466718770949L;

	@Override
	public int getPixelCount() {
		return instrument.size();
	}

	@Override
	public List<? extends Pixel> getPixels() {
		return instrument.copyGroup();
	}

	@Override
	public List<? extends Pixel> getMappingPixels(int keepFlags) {
		return instrument.getObservingChannels().copyGroup().discard(~keepFlags);	
	}

}
