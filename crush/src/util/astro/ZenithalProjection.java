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


public abstract class ZenithalProjection extends SphericalProjection {

	public ZenithalProjection() { 
		referenceNative.setNative(0.0, rightAngle);
	}
	
	@Override 
	public void calcCelestialPole() {
		pole = reference;
	}
	
	@Override
	public final double phi(final CoordinatePair offset) {
		return Math.atan2(offset.getX(), -offset.getY());
	}
	
	@Override
	public final double theta(final CoordinatePair offset) {
		return thetaOfR(Math.sqrt(offset.getX()*offset.getX() + offset.getY()*offset.getY()));
	}
	
	@Override
	public final void getOffsets(final double theta, final double phi, final CoordinatePair toOffset) {
		double R = R(theta);
		// What is in Calabretta and Greisen 2002
		toOffset.setX(R * Math.sin(phi));
		toOffset.setY(-R * Math.cos(phi));
	}
	
	
	public abstract double R(double theta);
	
	public abstract double thetaOfR(double value);
	

}
