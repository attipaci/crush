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

package util.plot;

import java.awt.*;
import java.util.Hashtable;

import util.plot.colorscheme.*;


public abstract class ColorScheme {
	public String schemename;
	public int highlight = Color.WHITE.getRGB();
	public int noData = Color.TRANSLUCENT;
	
	public ColorScheme() {}

	public abstract int getRGB(double scaledintensity);
	
	public int getHighlight() { return highlight; }

	public String getSchemeName() {
		return schemename;
	}
	
	public static ColorScheme getInstanceFor(String name) throws IllegalAccessException, InstantiationException {
		name = name.toLowerCase();
		return schemes.containsKey(name) ? schemes.get(name).newInstance() : null;
	}
	
	public static Hashtable<String, Class<? extends ColorScheme>> schemes = new Hashtable<String, Class<? extends ColorScheme>>();
	
	static {
		schemes.put("grayscale", GreyScale.class);
		schemes.put("colorful", Colorful.class);
		schemes.put("blue", CoolBlue.class);
		schemes.put("orange", Orangy.class);
		schemes.put("greyscale", GreyScale.class);
		schemes.put("gray", GreyScale.class);
		schemes.put("grey", GreyScale.class);
	}
}
