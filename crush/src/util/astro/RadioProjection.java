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
import util.SphericalCoordinates;
import util.SphericalProjection;

public class RadioProjection extends SphericalProjection {	
	public RadioProjection() {}

	@Override
	public String getFitsID() { return fitsID; }

	@Override
	public String getFullName() { return fullName; }

	
	@Override
	public final void project(final SphericalCoordinates coords, final CoordinatePair toProjected) {
		toProjected.x = (coords.x - reference.x) * coords.cosLat;
		toProjected.y = coords.y - reference.y;
	}
	
	@Override
	public final void deproject(final CoordinatePair projected, final SphericalCoordinates toCoords) {
		toCoords.setNativeLatitude(reference.y + projected.y);
		toCoords.setNativeLongitude(reference.x + projected.x / toCoords.cosLat);
	}
	
	
	// These are not used thanks to the overriding of the projection equations...
	@Override
	public void getOffsets(double theta, double phi, CoordinatePair toOffset) {
		// TODO Auto-generated method stub
		return;
	}

	@Override
	public double phi(CoordinatePair offset) {
		// TODO Auto-generated method stub
		return Double.NaN;
	}

	@Override
	public double theta(CoordinatePair offset) {
		// TODO Auto-generated method stub
		return Double.NaN;
	}

	
	public final static String fitsID = "GLS";
	public final static String fullName = "Radio";

}
