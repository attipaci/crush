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
// Copyright (c) 2009 Attila Kovacs 

package util.astro;

import util.SphericalCoordinates;
import util.SphericalProjection;
import util.Vector2D;

public class CelestialProjector {
	public EquatorialCoordinates equatorial = new EquatorialCoordinates();
	public Vector2D offset = new Vector2D();
	
	private SphericalProjection projection;
	private CelestialCoordinates coords;
	
	public CelestialProjector(SphericalProjection projection) {
		this.projection = projection;
		SphericalCoordinates reference = projection.getReference();
		if(reference instanceof CelestialCoordinates) coords = (CelestialCoordinates) reference.clone();
	}
	
	public final boolean isHorizontal() {
		return projection.getReference() instanceof HorizontalCoordinates;
	}
	
	public final void project() {
		if(coords instanceof EquatorialCoordinates) projection.project(equatorial, offset);
		else {
			coords.fromEquatorial(equatorial);
			projection.project(coords, offset);
		}
	}
	
	public final void deproject() {
		if(coords instanceof EquatorialCoordinates) projection.deproject(offset, equatorial);
		else {
			projection.deproject(offset, coords);
			coords.toEquatorial(equatorial);
		}
	}
}
