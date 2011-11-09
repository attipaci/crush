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

package util.data;

import java.awt.geom.AffineTransform;

import util.CoordinatePair;
import util.HashCode;
import util.Projection2D;
import util.Unit;
import util.Util;
import util.Vector2D;
import util.astro.EclipticCoordinates;
import util.astro.EquatorialCoordinates;
import util.astro.GalacticCoordinates;
import util.astro.HorizontalCoordinates;
import util.astro.SuperGalacticCoordinates;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

public abstract class Grid2D<CoordinateType extends CoordinatePair> implements Cloneable {
	public Projection2D<CoordinateType> projection;
	public String alt = ""; // The FITS alternative coordinate system specifier... 
	
	public Vector2D refIndex = new Vector2D();
	
	// These are transformation matrix elements to native offsets
	private double m11, m12, m21, m22, i11, i12, i21, i22;
	
	@Override
	public boolean equals(Object o) {
		return equals(o, 1e-8);
	}
	
	public boolean equals(Object o, double precision) {
		if(!(o instanceof Grid2D)) return false;
		Grid2D<?> grid = (Grid2D<?>) o;
		if(!grid.projection.equals(projection)) return false;
		if(Math.abs(grid.m11 / m11 - 1.0) > precision) return false;
		if(Math.abs(grid.m12 / m12 - 1.0) > precision) return false;
		if(Math.abs(grid.m21 / m21 - 1.0) > precision) return false;
		if(Math.abs(grid.m22 / m22 - 1.0) > precision) return false;
		if(Math.abs(grid.refIndex.x / refIndex.x - 1.0) > precision) return false;
		if(Math.abs(grid.refIndex.y / refIndex.y - 1.0) > precision) return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		return projection.hashCode() ^ 
			~HashCode.get(m11) ^ HashCode.get(m22) ^ HashCode.get(m12) ^ ~HashCode.get(m21) ^
			refIndex.hashCode();
	}
		
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@SuppressWarnings("unchecked")
	public Grid2D<CoordinateType> copy() {
		Grid2D<CoordinateType> copy = (Grid2D<CoordinateType>) clone();
		copy.projection = projection.copy();
		copy.refIndex = (Vector2D) refIndex.clone();
		return copy;
	}
	
	public double getPixelArea() { return Math.abs(m11 * m22 - m12 * m21); }
	
	public void setResolution(double delta) {
		setResolution(delta, delta);
	}
	
	public double[][] getTransform() {
		return new double[][] {{ m11, m12 }, { m21, m22 }};
	}
	
	public void setTransform(double[][] M) {
		if(M.length != 2) throw new IllegalArgumentException("Coordinate transform should a 2x2 matrix.");
		if(M[0].length != 2) throw new IllegalArgumentException("Coordinate transform should a 2x2 matrix.");
		
		m11 = M[0][0];
		m12 = M[0][1];
		m21 = M[1][0];
		m22 = M[1][1];
		calcInverseTransform();
	}
	
	public void setResolution(double dx, double dy) {
		m11 = dx;
		m22 = dy;
		m21 = m12 = 0.0;
		calcInverseTransform();
	}
	
	public boolean isHorizontal() {
		return getReference() instanceof HorizontalCoordinates;
	}
	
	public boolean isEquatorial() {
		return getReference() instanceof EquatorialCoordinates;
	}
	
	public boolean isEcliptic() {
		return getReference() instanceof EclipticCoordinates;
	}
	
	public boolean isGalactic() {
		return getReference() instanceof GalacticCoordinates;
	}
	
	public boolean isSuperGalactic() {
		return getReference() instanceof SuperGalacticCoordinates;
	}
	
	
	public AffineTransform getLocalAffineTransform() {
		return new AffineTransform(m11, m12, m21, m22, 0.0, 0.0);
	}
	
	public Vector2D getResolution() {
		return new Vector2D(m11, m22);
	}
	
	public double pixelSizeX() { return Math.abs(m11); }
	
	public double pixelSizeY() { return Math.abs(m22); }
	
	public void calcInverseTransform() {
		double adet = getPixelArea();
		i11 = m11 / adet;
		i12 = m21 / adet;
		i21 = m12 / adet;
		i22 = m22 / adet;
	}
	
	// Generalize to non-square pixels...
	public void rotate(double angle) {
		if(angle == 0.0) return;
		
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		double a11 = m11, a12 = m12;
		double a21 = m21, a22 = m22;
		m11 = c * a11 - s * a21;
		m12 = c * a12 - s * a22;
		m21 = s * a11 + c * a21;
		m22 = s * a12 + c * a22;
		
		calcInverseTransform();

	}
	
	public boolean isReverseX() { return false; }
	
	public boolean isReverseY() { return false; }
	
	
	public void editHeader(Cursor cursor) throws HeaderCardException {
		CoordinateType reference = projection.getReference();
			
		// TODO 
		projection.edit(cursor, alt);
		reference.edit(cursor, alt);
		
		double a11 = m11, a12 = m12, a21 = m21, a22 = m22;
		if(isReverseX()) { a11 *= -1.0; a21 *= -1.0; }
		if(isReverseY()) { a22 *= -1.0; a12 *= -1.0; }
		
		cursor.add(new HeaderCard("CRPIX1" + alt, refIndex.x + 1, "Reference grid position"));
		cursor.add(new HeaderCard("CRPIX2" + alt, refIndex.y + 1, "Reference grid position"));
		
		if(m12 == 0.0 && m21 == 0.0) {
			cursor.add(new HeaderCard("CDELT1" + alt, a11/Unit.deg, "Grid spacing (deg)"));	
			cursor.add(new HeaderCard("CDELT2" + alt, a22/Unit.deg, "Grid spacing (deg)"));		
		}
		else {		
			cursor.add(new HeaderCard("CD1_1" + alt, a11, "Transformation matrix element"));
			cursor.add(new HeaderCard("CD1_2" + alt, a12, "Transformation matrix element"));
			cursor.add(new HeaderCard("CD2_1" + alt, a21, "Transformation matrix element"));
			cursor.add(new HeaderCard("CD2_2" + alt, a22, "Transformation matrix element"));
		}
	}
	
	public abstract void parseProjection(Header header) throws HeaderCardException;
		
	public abstract CoordinateType getCoordinateInstanceFor(String type) throws InstantiationException, IllegalAccessException;
	
	public void parseHeader(Header header) throws HeaderCardException, InstantiationException, IllegalAccessException {
		String type = header.getStringValue("CTYPE1" + alt);
	
		try { parseProjection(header); }
		catch(Exception e) { System.err.println("ERROR! Unknown projection " + type.substring(5, 8)); }
		
		CoordinateType reference = null;
		
		reference = getCoordinateInstanceFor(type);
		
		// TODO
		reference.parse(header, alt);
		projection.setReference(reference);
		
		// Internally keep the transformation matrix unitary 
		// And have delta carry the pixel sizes...
		
		if(header.containsKey("CD1_1" + alt) || header.containsKey("CD1_2" + alt) || header.containsKey("CD2_1" + alt) || header.containsKey("CD2_2" + alt)) {
			// When the CDi_j keys are used the scaling is incorporated into the CDi_j values.
			// Thus, the deltas are implicitly assumed to be 1...
			m11 = header.getDoubleValue("CD1_1" + alt, 1.0);
			m12 = header.getDoubleValue("CD1_2" + alt, 0.0);
			m21 = header.getDoubleValue("CD2_1" + alt, 0.0);
			m22 = header.getDoubleValue("CD2_2" + alt, 1.0);	
		}	
		else {
			// Otherwise, the scaling is set by CDELTi keys...
			double dx = header.getDoubleValue("CDELT1" + alt, 1.0) * Unit.deg;
			double dy = header.getDoubleValue("CDELT2" + alt, 1.0) * Unit.deg;
			
			// And the transformation is set by the PCi_j keys
			// Transform then scale...
			m11 = dx * header.getDoubleValue("PC1_1" + alt, 1.0);
			m12 = dx * header.getDoubleValue("PC1_2" + alt, 0.0);
			m21 = dy * header.getDoubleValue("PC2_1" + alt, 0.0);
			m22 = dy * header.getDoubleValue("PC2_2" + alt, 1.0);
			
			// Or the rotation of the latitude axis is set via CROTAi...
			if(header.containsKey("CROTA2" + alt)) {
				rotate(header.getDoubleValue("CROTA2" + alt) * Unit.deg);
			}		
		}	
		
		if(isReverseX()) { m11 *= -1.0; m21 *= -1.0; }
		if(isReverseY()) { m22 *= -1.0; m12 *= -1.0; }
		
		refIndex.x = header.getDoubleValue("CRPIX1" + alt) - 1;
		refIndex.y = header.getDoubleValue("CRPIX2" + alt) - 1;
		
		calcInverseTransform();
	}
	
	@Override
	public String toString() {	
		CoordinateType reference = projection.getReference();
		String projectionName = reference.getClass().getSimpleName();
		projectionName = projectionName.substring(0, projectionName.length() - "Coordinates".length());
		
		String info =
			"  " + projectionName + ": " + reference.toString() + "\n" +
			"  Projection: " + projection.getFullName() + " (" + projection.getFitsID() + ")\n" + 
			"  Grid Spacing: " + Util.f2.format(m11 / Unit.arcsec) + " x " + Util.f2.format(m22 / Unit.arcsec) + " arcsec.\n";
		
		return info;
	}

	
	public final void toIndex(final Vector2D offset) {
		// transform here...
		final double x = offset.x;
		offset.x = i11 * x + i12 * offset.y + refIndex.x;
		offset.y = i21 * x + i22 * offset.y + refIndex.y;
	}
	
	public final void toOffset(final Vector2D index) {
		index.x -= refIndex.x;
		index.y -= refIndex.y;

		final double x = index.x;
		index.x = m11 * x + m12 * index.y;
		index.y = m21 * x + m22 * index.y;
	}
	
    public final CoordinateType getReference() { return projection.getReference(); }
    
    public void setReference(CoordinateType reference) { projection.setReference(reference); }
    
    public Vector2D getReferenceIndex() { return refIndex; }
    
    public void setReferenceIndex(Vector2D v) { refIndex = v; }
    
    public final Projection2D<CoordinateType> getProjection() { return projection; }

    public void getIndex(CoordinateType coords, Vector2D index) {
    	projection.project(coords, index);
    	toIndex(index);
    }
   
    public void getCoords(Vector2D index, CoordinateType coords) {
    	double i = index.x;
    	double j = index.y; 
    	
    	toOffset(index);
    	projection.deproject(index, coords);
    	
    	index.x = i;
    	index.y = j;
    }
    
    public void toggleNative(Vector2D offset) {
    	if(isReverseX()) offset.x *= -1.0;
    	if(isReverseY()) offset.y *= -1.0;
    }
    
    
    public void shift(Vector2D offset) {
    	toIndex(offset);
    	refIndex.x += offset.x;
    	refIndex.y += offset.y;
    }
    
    
    
}
