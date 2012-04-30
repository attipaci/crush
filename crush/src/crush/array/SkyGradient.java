/*******************************************************************************
 * Copyright (c) 2012 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import crush.Channel;
import crush.GradientGains;

public class SkyGradient extends GradientGains {
	private double center = 0.0;
	public boolean horizontal = true;
	
	public SkyGradient(boolean isHorizontal) {
		this.horizontal = isHorizontal;
	}
	
	@Override
	public double getRawGain(Channel c) throws Exception {
		SimplePixel pixel = (SimplePixel) c;
		return (horizontal ? pixel.position.getX() : pixel.position.getY()) - center;
	}

	@Override
	public void setRawGain(Channel c, double value) throws Exception {
		throw new UnsupportedOperationException("Cannot change gradient gains.");
	}


}
