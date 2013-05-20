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
import util.SphericalCoordinates;
import util.SphericalProjection;
import util.Vector2D;


public class AIPSLegacyProjection extends SphericalProjection {
	SphericalProjection baseProjection;
	String name;
	String fitsID;
	Vector2D referenceOffsets;
	
	
	public AIPSLegacyProjection(SphericalProjection baseProjection, String name, String fitsID) {
		this.baseProjection = baseProjection;
		this.name = name;
		this.fitsID = fitsID;
	}
	
	@Override
	public String getFitsID() {
		return fitsID;
	}

	@Override
	public String getFullName() {
		return name;
	}
	
	@Override
	public void setReference(SphericalCoordinates celestialCoords, SphericalCoordinates nativeCoords) throws IllegalArgumentException {
		baseProjection.project(celestialCoords, referenceOffsets);
		super.setReference(celestialCoords, nativeCoords);
	}
		
	@Override
	public final void project(SphericalCoordinates coords, CoordinatePair toProjected) {
		baseProjection.project(coords, toProjected);
		toProjected.subtractX(referenceOffsets.getX());
		toProjected.subtractY(referenceOffsets.getY());
	}
	
	@Override
	public final void deproject(CoordinatePair projected, SphericalCoordinates toCoords) {
		projected.addX(referenceOffsets.getX());
		projected.addY(referenceOffsets.getY());
		baseProjection.deproject(projected, toCoords);		
	}
	
	
	@Override
	public final void getOffsets(double theta, double phi, CoordinatePair toOffset) {
		baseProjection.getOffsets(theta, phi, toOffset);
	}

	@Override
	public final double phi(CoordinatePair offset) {
		return baseProjection.phi(offset);
	}

	@Override
	public double theta(CoordinatePair offset) {
		return baseProjection.theta(offset);
	}

}
