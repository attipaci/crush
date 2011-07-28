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

package util.data;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import util.*;

public class SimpleGrid2D implements Grid2D<Vector2D> {
	public Vector2D reference = new Vector2D(), refIndex = new Vector2D();
	public Vector2D delta = new Vector2D(1.0, 1.0);

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}

	public boolean equals(Object o, double precision) {
		if(!(o instanceof SimpleGrid2D)) return false;
		SimpleGrid2D grid = (SimpleGrid2D) o;
		if(Math.abs(grid.delta.x / delta.x - 1.0) > precision) return false;
		if(Math.abs(grid.delta.y / delta.y - 1.0) > precision) return false;
		if(Math.abs(grid.reference.x / reference.x - 1.0) > precision) return false;
		if(Math.abs(grid.reference.y / reference.y - 1.0) > precision) return false;
		if(Math.abs(grid.refIndex.x / refIndex.x - 1.0) > precision) return false;
		if(Math.abs(grid.refIndex.y / refIndex.y - 1.0) > precision) return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		return delta.hashCode() ^ refIndex.hashCode() ^ reference.hashCode();
	}
	
	public Grid2D<Vector2D> copy() {
		SimpleGrid2D copy = (SimpleGrid2D) clone();
		if(delta != null) copy.delta = (Vector2D) delta.clone();
		if(reference != null) copy.reference = (Vector2D) reference.clone();
		if(refIndex != null) copy.refIndex = (Vector2D) refIndex.clone();
		return copy;
	}
	
	public Vector2D getReference() {
		return reference;
	}

	public void setReference(Vector2D c) {
		reference = c;
	}

	public Vector2D getReferenceIndex() { return refIndex; }
	    
	public void setReferenceIndex(Vector2D v) { refIndex = v; }
	
	public double getPixelArea() {
		return delta.x * delta.y;
	}

	public Vector2D getResolution() {
		return delta;
	}

	public double pixelSizeX() {
		return delta.x;
	}

	public double pixelSizeY() {
		return delta.y;
	}

	public void setResolution(double value) {
		setResolution(value, value);
	}

	public void setResolution(double x, double y) {
		delta = new Vector2D(x, y);
	}

	public void toIndex(Vector2D v) {
		v.subtract(reference);
		v.x /= delta.x;
		v.y /= delta.y;
		v.add(refIndex);
	}

	public void toOffset(Vector2D v) {
		v.subtract(refIndex);
		v.x *= delta.x;
		v.y *= delta.y;
		v.add(reference);
	}

	public double[][] getTransform() {
		return new double[][] { { delta.x, 0.0 }, {0.0, delta.y }};
	}

	public void setTransform(double[][] M) {
		if(M[0][1] != 0.0 || M[1][0] != 0.0) 
			throw new IllegalArgumentException(getClass().getSimpleName() + " does not support off-diagonal transforms.");
		delta.x = M[0][0];
		delta.y = M[1][1];
	}

	public void addCoordinateInfo(BasicHDU hdu) throws HeaderCardException {	
		nom.tam.util.Cursor cursor = hdu.getHeader().iterator();
		while(cursor.hasNext()) cursor.next();
		
		cursor.add(new HeaderCard("CTYPE1", "x", "Axis label"));
		cursor.add(new HeaderCard("CRVAL1", reference.x, "Reference value"));
		cursor.add(new HeaderCard("CRPIX1", refIndex.x + 1, "Reference grid position"));
		cursor.add(new HeaderCard("CDELT1", delta.x, "Pixel size."));
		
		cursor.add(new HeaderCard("CTYPE2", "y", "Axis label"));
		cursor.add(new HeaderCard("CRVAL2", reference.y, "Reference value"));
		cursor.add(new HeaderCard("CRPIX2", refIndex.y + 1, "Reference grid position"));
		cursor.add(new HeaderCard("CDELT2", delta.y, "Pixel size."));
	
	}
	
	public void parseCoordinateInfo(Header header) throws HeaderCardException, InstantiationException, IllegalAccessException {
		reference.x = header.getDoubleValue("CRVAL1", 0.0);
		reference.y = header.getDoubleValue("CRVAL2", 0.0);
		
		refIndex.x = header.getDoubleValue("CRPIX1", 1.0) - 1.0;
		refIndex.y = header.getDoubleValue("CRPIX2", 1.0) - 1.0;
		
		delta.x = header.getDoubleValue("CDELT1", 1.0);
		delta.y = header.getDoubleValue("CDELT2", 1.0);
	}
	
}
