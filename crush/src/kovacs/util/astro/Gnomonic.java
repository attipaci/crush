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

public class Gnomonic extends ZenithalProjection { 
	
	public Gnomonic() {}

	@Override
	public final double R(final double theta) {
		if(SphericalCoordinates.equalAngles(theta, rightAngle)) return 0.0;
		return 1.0 / Math.tan(theta);
	}

	@Override
	public final double thetaOfR(final double value) {
		return Math.atan2(1.0, value);
	}

	@Override
	public String getFitsID() { return fitsID; }

	@Override
	public String getFullName() { return fullName; }

	
	public final static String fitsID = "TAN";
	public final static String fullName = "Gnomonic";
	
	

}
