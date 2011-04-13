/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.gui.colorscheme;

import java.awt.*;

import crush.gui.ColorScheme;

public class Colorful extends ColorScheme {


	public Colorful() {
		schemename= "CameraLike";
	}

	@Override
	public int getRGB(double scaled) {
		if(Double.isNaN(scaled)) return noData;

		if(scaled < 0.0) scaled=0.0;
		else if(scaled > 1.0) scaled=1.0;
		
		if(scaled < 0.2) return Color.HSBtoRGB(0.8F, 1.0F, 5.0F * (float) scaled);
		else if(scaled >= 0.8) return Color.HSBtoRGB(0.0F, 5.0F - 5.0F * (float) scaled, 1.0F);
		else return Color.HSBtoRGB(4.0F/3.0F*(0.8F - (float)scaled), 1.0F, 1.0F);		
	}
}
