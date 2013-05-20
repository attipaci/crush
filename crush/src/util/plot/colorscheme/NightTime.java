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
package util.plot.colorscheme;

import java.awt.Color;

import util.plot.ColorScheme;


public class NightTime extends ColorScheme {
	@Override
	public int getRGB(double scaledI) {
		if(Double.isNaN(scaledI)) return Color.DARK_GRAY.getRGB();
		float I = (float) scaledI;
		if(I > 1.0F) I = 1.0F;
		if(I < 0.0F) I = 0.0F;
		
		float r, g, b;

		if(I < third) {
			b = I / third;
			g = r = 0.0F;
		}
		else if(I < twothirds) {
			b = 1.0F;
			g = (I - third) / third;
			r = 0.0F;			
		}
		else {
			b = g = 1.0F;
			r = (I - twothirds) / third;
		}
		
		return ColorScheme.getRGB(r, g, b);	
	}
	
	@Override
	public Color getHighlight() {
		return Color.ORANGE;
	}
	
	private static float third = 1.0F / 3.0F;
	private static float twothirds = 2.0F / 3.0F;
	
}
