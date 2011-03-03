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

package util;

import java.util.*;

public class CoordinateSystem extends Vector<CoordinateAxis> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7965280172336615563L;
	public String name = "Default Coordinate System";

	public CoordinateSystem() {}
	
	public CoordinateSystem(String text) { name = text; }
	
	public CoordinateSystem(int dimension) {
		for(int i=0; i<dimension; i++)
			add(new CoordinateAxis(defaultLabel[i%defaultLabel.length] 
			        + (dimension > defaultLabel.length ? i/defaultLabel.length + "" : "")));
	}

	public CoordinateSystem(String text, int dimension) {
		this(dimension);
		name = text;
	}
	
	public CoordinateSystem(CoordinateSystem template) {
		copy(template);
	}
	
	public void copy(CoordinateSystem template) {
		name = template.name;
		for(CoordinateAxis axis : template) add((CoordinateAxis) axis.clone());
	}
	
	public void setName(String text) { name = text; }

	public String getName() { return name; }

	protected static String[] defaultLabel = { "x", "y", "z", "u", "v", "w" };


}

