/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package util;

import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public abstract class Projection2D<CoordinateType extends CoordinatePair> implements Cloneable  {

	private CoordinateType reference;
	
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public boolean equals(Object o) {
		if(!o.getClass().equals(getClass())) return false;
		Projection2D<?> projection = (Projection2D<?>) o;
		if(!projection.getClass().equals(getClass())) return false;
		if(!projection.reference.equals(reference)) return false;
		return true;		
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		if(reference != null) hash ^= reference.hashCode();
		return hash;
	}
	

	@SuppressWarnings("unchecked")
	public Projection2D<CoordinateType> copy() {
		Projection2D<CoordinateType> copy = (Projection2D<CoordinateType>) clone();
		if(reference != null) copy.reference = (CoordinateType) reference.clone();
		return copy;
	}

	public abstract CoordinateType getCoordinateInstance();
	
	public abstract void project(CoordinateType coords, CoordinatePair toProjected);

	public abstract void deproject(CoordinatePair projected, CoordinateType toCoords);
	
	public abstract String getFitsID();
	
	public abstract String getFullName();
	
	public CoordinateType getReference() { return reference; }
	
	public void setReference(CoordinateType coordinates) {
		reference = coordinates;
	}
	
	public CoordinatePair getProjected(CoordinateType coords) {
		CoordinatePair offset = new CoordinatePair();
		project(coords, offset);
		return offset;		
	}
	
	public CoordinateType getDeprojected(Vector2D projected) {
		CoordinateType coords = getCoordinateInstance();
		deproject(projected, coords);
		return coords;		
	}
	
	public void parse(Header header) { parse(header, ""); }
	
	public abstract void parse(Header header, String alt);
	
	public void edit(Cursor cursor) throws HeaderCardException { edit(cursor, ""); }
	
	public abstract void edit(Cursor cursor, String alt) throws HeaderCardException;
	
	
}
