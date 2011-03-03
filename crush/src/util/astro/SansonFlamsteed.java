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

package util.astro;

import util.CoordinatePair;
import util.SphericalProjection;

public class SansonFlamsteed extends SphericalProjection {

	public SansonFlamsteed() {}

	@Override
	public String getFitsID() { return fitsID; }

	@Override
	public String getFullName() { return fullName; }

	@Override
	public final void getOffsets(double theta, double phi, CoordinatePair toOffset) {
		toOffset.x = phi * Math.cos(theta);
		toOffset.y = theta;
	}

	@Override
	public final double phi(CoordinatePair offset) {
		return offset.x / Math.cos(offset.y);
	}

	@Override
	public final double theta(CoordinatePair offset) {
		return offset.y;
	}

	
	
	public final static String fitsID = "SFL";
	public final static String fullName = "Sanson-Flamsteed";
	
}
