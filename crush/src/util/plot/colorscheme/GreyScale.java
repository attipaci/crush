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
// Copyright (c) 2007 Attila Kovacs 

package util.plot.colorscheme;

import java.awt.*;

import util.plot.ColorScheme;


public class GreyScale extends ColorScheme {

	@Override
	public int getRGB(double scaled) {
		if(Double.isNaN(scaled)) return noData;
		
		if(scaled < 0.0) scaled = 0.0;
		else if(scaled > 1.0) scaled = 1.0;

		final float value = (float) scaled;
		
		return Color.HSBtoRGB(0.0F, 0.0F, value);
	}

	@Override
	public Color getHighlight() {
		return Color.red;
	}

}
