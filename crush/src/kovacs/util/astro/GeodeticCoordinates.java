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

import kovacs.util.SphericalCoordinates;
import kovacs.util.Unit;

// TODO Needs updating
// ... rename to GeographicCoordinates
// ... toString() formatting, and parse with N,S,E,W

public class GeodeticCoordinates extends SphericalCoordinates {	
	public GeodeticCoordinates() {}
	
	public GeodeticCoordinates(String text) { super(text); }
	
	public GeodeticCoordinates(double lon, double lat) { super(lon, lat); }
	
	// Approximation for converting geocentric to geodesic coordinates.
	// Marik: Csillagaszat (1989)
	// based on Woolard & Clemence: Spherical Astronomy (1966)
	public GeodeticCoordinates(GeocentricCoordinates geocentric) {
		setNativeLongitude(geocentric.getX());
		setNativeLatitude(geocentric.getY() + X * Math.sin(2.0 * geocentric.getY()));
	}
	
	public final static double a = 6378137.0 * Unit.m; // Earth major axis
	public final static double b = 6356752.3 * Unit.m; // Earth minor axis
	
	public final static double f = 1.0 / 298257.0; // Flattening of Earth (Marik: Csillagaszat)
	private final static double X = 103132.4 * Unit.deg * (2.0 * f - f*f); // Approximation term for geodesic conversion (Marik: Csillagaszat)
	
	public final static int NORTH = 1;
	public final static int SOUTH = -1;
	public final static int EAST = 1;
	public final static int WEST = -1;
	
	// TODO verify units of X...

    // See Wikipedia Geodetic System...

    // e^2 = 2f-f^2 = 1- (b/a)^2
    // e'^2 = f(2-f)/(1-f)^2 = (a/b)^2 - 1


    // Australian Geodetic Datum (1966) and (1984)
    // AGD66 & GDA84
    // a = 6378160.0 m
    // f = 1/298.25

    // Geodetic Reference System 1980 (GRS80)
    // a = 6378137 m
    // f = 1/298.257222101

    // World Geodetic System 1984 (WGS84)
    // used by GPS navigation
    // a = 6378137.0 m
    // f = 1/298.257223563


    // Geodetic (phi, lambda, h) -> geocentric phi'
    // chi = sqrt(1-e^2 sin^2(phi))
    //
    // tan(phi') = [(a/chi)(1-f)^2 + h] / [(a/chi) + h] tan(phi)
    
      
}
