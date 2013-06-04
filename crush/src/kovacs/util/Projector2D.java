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
package kovacs.util;

public class Projector2D<CoordinateType extends CoordinatePair> {
	
	public Vector2D offset = new Vector2D();
	
	private Projection2D<CoordinateType> projection;
	private CoordinateType coords;
	
	
	@SuppressWarnings("unchecked")
	public Projector2D(Projection2D<CoordinateType> projection) {
		this.projection = projection;
		coords = (CoordinateType) projection.getReference().clone();
	}

	public CoordinateType getCoordinates() { return coords; }
	
	public void setReferenceCoords() {
		coords.copy(getProjection().getReference());
	}
	
	public void project() {
		projection.project(coords, offset);
	}
	
	public void deproject() {
		projection.deproject(offset, coords);
	}
	
	public Projection2D<CoordinateType> getProjection() { return projection; }
}
