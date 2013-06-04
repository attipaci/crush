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
// Copyright (c) 2007 Attila Kovacs 

package kovacs.util.astro;

import kovacs.util.CoordinatePair;
import kovacs.util.SphericalProjection;

public class Mercator extends SphericalProjection {

	public Mercator() {}

	@Override
	public String getFitsID() { return fitsID; }

	@Override
	public String getFullName() { return fullName; }

	@Override
	public final void getOffsets(double theta, double phi, CoordinatePair toOffset) {
		toOffset.setX(phi);
		toOffset.setY(Math.log(Math.tan(0.5*(rightAngle + theta))));
	}

	@Override
	public final double phi(CoordinatePair offset) { 
		return offset.getX();
	}

	@Override
	public final double theta(CoordinatePair offset) {
		return 2.0 * Math.atan(Math.exp(offset.getY())) - rightAngle;
	}

	
	
	public final static String fitsID = "MER";
	public final static String fullName = "Mercator";
}