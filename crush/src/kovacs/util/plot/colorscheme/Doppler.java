/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package kovacs.util.plot.colorscheme;

import java.awt.Color;

import kovacs.util.plot.ColorScheme;



public class Doppler extends ColorScheme {

	@Override
	public int getRGB(double scaledI) {
		if(Double.isNaN(scaledI)) return Color.DARK_GRAY.getRGB();
		float I = (float) scaledI;
		if(I > 1.0F) I = 1.0F;
		if(I < 0.0F) I = 0.0F;
		
		float r, g, b;

		if(I <= 0.5) {
			b = 1.0F;
			r = g = 2.0F * I;
		}
		else {
			r = 1.0F;
			b = g = 2.0F - 2.0F * I;
		}

		return ColorScheme.getRGB(r, g, b);	
	}

	@Override
	public Color getHighlight() {
		return Color.BLACK;
	}
}
