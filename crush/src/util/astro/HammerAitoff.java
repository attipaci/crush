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
// Copyright (c) 2007 Attila Kovacs 

package util.astro;

import util.CoordinatePair;
import util.SphericalProjection;


public class HammerAitoff extends SphericalProjection {
	
	public HammerAitoff() {}

	@Override
	public String getFitsID() { return fitsID; }

	@Override
	public String getFullName() { return fullName; }

	@Override
	public final void getOffsets(double theta, double phi, CoordinatePair toOffset) {
		double gamma = gamma(theta, phi);
		toOffset.setX(2.0 * gamma * Math.cos(theta) * Math.sin(0.5*phi));
		toOffset.setY(gamma * Math.sin(theta));
	}

	@Override
	public final double phi(CoordinatePair offset) {
		double Z2 = Z2(offset);
		double Z = Math.sqrt(Z2);
		return 2.0 * Math.atan2(0.5*Z*offset.getX(), 2.0*Z2 - 1.0);
	}

	@Override
	public final double theta(CoordinatePair offset) {
		return asin(offset.getY() * Math.sqrt(Z2(offset)));
	}
	
	public final double Z2(CoordinatePair offset) {
		return 1.0 - (offset.getX() * offset.getX())/16.0 - (offset.getY()*offset.getY())/4.0;
	}
	
	public final double gamma(double theta, double phi) {
		return Math.sqrt(2.0 / (1.0+Math.cos(theta)*Math.cos(0.5*phi)));
	}
	
	public final static String fitsID = "AIT";
	public final static String fullName = "Hammer-Aitoff";
}
