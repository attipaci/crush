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
package util;

import util.astro.EclipticCoordinates;
import util.astro.EquatorialCoordinates;
import util.astro.GalacticCoordinates;
import util.astro.HorizontalCoordinates;
import util.astro.SuperGalacticCoordinates;
import nom.tam.fits.*;

public class SphericalGrid implements Cloneable {
	public SphericalProjection projection;
	public Vector2D refIndex = new Vector2D();
	public Vector2D delta = new Vector2D();
	public String alt = ""; // The FITS alternative coordinate system specifier... 
	
	private double m11 = Double.NaN, m12 = Double.NaN, m21 = Double.NaN, m22 = Double.NaN;
	private double im11, im12, im21, im22;
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof SphericalGrid)) return false;
		SphericalGrid grid = (SphericalGrid) o;
		if(!grid.projection.getClass().equals(projection.getClass())) return false;
		if(grid.delta != delta) return false;
		
		// TODO compare the transformation matrices...	
		Vector2D a = new Vector2D();
		Vector2D b = new Vector2D();
		toOffset(a);
		toOffset(b);
		
		if(!a.equals(b)) return false;
		return true;
	}
	
	public boolean equals(Object o, double precision) {
		if(!(o instanceof SphericalGrid)) return false;
		SphericalGrid grid = (SphericalGrid) o;
		if(!grid.projection.getClass().equals(projection.getClass())) return false;
		if(Math.abs(grid.delta.x / delta.x - 1.0) > precision) return false;
		if(Math.abs(grid.delta.y / delta.y - 1.0) > precision) return false;
	
		// TODO Compare the transformation matrices too...
		Vector2D a = new Vector2D();
		Vector2D b = new Vector2D();
		toOffset(a);
		toOffset(b);
		a.subtract(b);
		a.x /= delta.x;
		a.y /= delta.y;
		
		if(Math.abs(a.x) > precision) return false;
		if(Math.abs(a.y) > precision) return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		return projection.hashCode() ^ (~refIndex.hashCode()) ^ HashCode.get(delta.x) ^ ~HashCode.get(delta.y);
	}
		
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public SphericalGrid copy() {
		SphericalGrid copy = (SphericalGrid) clone();
		copy.projection = projection.copy();
		copy.refIndex = (Vector2D) refIndex.clone();
		return copy;
	}
	
	public double getPixelArea() { return delta.x * delta.y; }
	
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
	
	public void setTransform(double a11, double a12, double a21, double a22) {
		m11 = a11;
		m12 = a12;
		m21 = a21;
		m22 = a22;
		calcInverseTransform();
	}
	
	public void calcInverseTransform() {
		double adet = Math.abs(m11 * m22 - m12 * m21);
		im11 = m11 / adet;
		im12 = m21 / adet;
		im21 = m12 / adet;
		im22 = m22 / adet;
	}
	
	public void setTransform(double[][] M) {
		setTransform(M[0][0], M[0][1], M[1][0], M[1][1]);		
	}
	
	public void normalizeTransform() {
		double adet = Math.abs(m11 * m22 - m12 * m21);
		m11 /= adet; m12 /= adet; m21 /= adet; m22 /= adet;
		im11 *= adet; im12 *= adet; im21 *= adet; im22 *= adet;
		delta.scale(adet);
	}
	
	// Generalize to non-square pixels...
	public void rotate(double angle) {
		if(angle == 0.0) return;
		
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		double a11 = Double.isNaN(m11) ? 1.0 : m11, a12 = Double.isNaN(m12) ? 0.0 : m12;
		double a21 = Double.isNaN(m21) ? 0.0 : m21, a22 = Double.isNaN(m22) ? 1.0 : m22;
		m11 = c * a11 - s * a21;
		m12 = c * a12 - s * a22;
		m21 = s * a11 + c * a21;
		m22 = s * a12 + c * a22;
		calcInverseTransform();
	}
	
	public void addCoordinateInfo(BasicHDU hdu) throws HeaderCardException {
		SphericalCoordinates reference = projection.getReference();
		
		nom.tam.util.Cursor cursor = hdu.getHeader().iterator();
		while(cursor.hasNext()) cursor.next();
		
		projection.edit(cursor, alt);
		reference.edit(cursor, alt);
		
		cursor.add(new HeaderCard("CRPIX1" + alt, refIndex.x + 1, "Reference grid position"));
		cursor.add(new HeaderCard("CDELT1" + alt, (reference.isReverseLongitude() ? -1 : 1) * delta.x/Unit.deg, "Grid spacing (deg)"));	
		//cursor.add(new HeaderCard("CROTA1" + alt, 0.0, "Axis rotation (deg)."));
		
		cursor.add(new HeaderCard("CRPIX2" + alt, refIndex.y + 1, "Reference grid position"));
		cursor.add(new HeaderCard("CDELT2" + alt, (reference.isReverseLatitude() ? -1 : 1) * delta.y/Unit.deg, "Grid spacing (deg)"));		
		//cursor.add(new HeaderCard("CROTA2" + alt, 0.0, "Axis rotation (deg)."));
		
		if(!Double.isNaN(m11)) cursor.add(new HeaderCard("PC1_1" + alt, m11, "Transformation matrix element"));
		if(!Double.isNaN(m12)) cursor.add(new HeaderCard("PC1_2" + alt, m12, "Transformation matrix element"));
		if(!Double.isNaN(m21)) cursor.add(new HeaderCard("PC2_1" + alt, m21, "Transformation matrix element"));
		if(!Double.isNaN(m21)) cursor.add(new HeaderCard("PC2_2" + alt, m22, "Transformation matrix element"));
		
	}
	
	public void getCoordinateInfo(Header header) throws HeaderCardException {
		String type = header.getStringValue("CTYPE1" + alt);
	
		try { projection = SphericalProjection.forName(type.substring(5, 8)); }
		catch(Exception e) { System.err.println("ERROR! Unknown projection " + type.substring(5, 8)); }
		
		SphericalCoordinates reference = null;
		
		String lonType = type.toUpperCase();
		
		if(lonType.startsWith("RA--")) reference = new EquatorialCoordinates();
		else if(lonType.substring(1).startsWith("LON")) {
			switch(lonType.charAt(0)) {
			case 'A' : reference = new HorizontalCoordinates(); break;
			case 'G' : reference = new GalacticCoordinates(); break;
			case 'E' : reference = new EclipticCoordinates(); break;
			case 'S' : reference = new SuperGalacticCoordinates(); break;
			default: System.err.println("ERROR! Unknown Coordinate Definition " + type);
			}
		}
		else System.err.println("ERROR! Unknown Coordinate Definition " + type);
		
		reference.parse(header, alt);
		projection.setReference(reference);
		
		refIndex.x = header.getDoubleValue("CRPIX1" + alt) - 1;
		refIndex.y = header.getDoubleValue("CRPIX2" + alt) - 1;
			
		// Internally keep the transformation matrix unitary 
		// And have delta carry the pixel sizes...
		
		if(header.containsKey("CD1_1" + alt) || header.containsKey("CD1_2" + alt) || header.containsKey("CD2_1" + alt) || header.containsKey("CD2_2" + alt)) {
			// When the CDi_j keys are used the scaling is incorporated into the CDi_j values.
			// Thus, the deltas are implicitly assumed to be 1...
			delta.x = delta.y = 1.0;
			setTransform(
					header.getDoubleValue("CD1_1" + alt, 1.0),
					header.getDoubleValue("CD1_2" + alt, 0.0),
					header.getDoubleValue("CD2_1" + alt, 0.0),
					header.getDoubleValue("CD2_2" + alt, 1.0)
					);
			normalizeTransform();
		}	
		else {
			// Otherwise, the scaling is set by CDELTi keys...
			delta.x = (reference.isReverseLongitude() ? -1.0 : 1.0) * header.getDoubleValue("CDELT1" + alt, 1.0) * Unit.deg;
			delta.y = (reference.isReverseLatitude() ? -1.0 : 1.0) * header.getDoubleValue("CDELT2" + alt, 1.0) * Unit.deg;
		
			// And the transformation is set by the PCi_j keys
			// Transform then scale...
			if(header.containsKey("PC1_1" + alt) || header.containsKey("PC1_2" + alt) || header.containsKey("PC2_1" + alt) || header.containsKey("PC2_2" + alt)) {				
				setTransform(
						header.getDoubleValue("PC1_1" + alt, 1.0),
						header.getDoubleValue("PC1_2" + alt, 0.0),
						header.getDoubleValue("PC2_1" + alt, 0.0),
						header.getDoubleValue("PC2_2" + alt, 1.0)
				);
				normalizeTransform();
			}
			// Or the rotation of the latitude axis is set via CROTAi...
			else if(header.containsKey("CROTA2" + alt)) {
				rotate(header.getDoubleValue("CROTA2" + alt) * Unit.deg);
			}		
		}	

	}
	
	@Override
	public String toString() {	
		SphericalCoordinates reference = projection.getReference();
		String projectionName = reference.getClass().getSimpleName();
		projectionName = projectionName.substring(0, projectionName.length() - "Coordinates".length());
		
		String info =
			"  " + projectionName + ": " + reference.toString() + "\n" +
			"  Projection: " + projection.getFullName() + " (" + projection.getFitsID() + ")\n" + 
			"  Grid Spacing: " + Util.f2.format(delta.x / Unit.arcsec) + " x " + Util.f2.format(delta.y / Unit.arcsec) + " arcsec.\n";
		
		return info;
	}
	
	public final void toIndex(Vector2D offset) {
		offset.x /= delta.x;
		offset.y /= delta.y;
		
		// transform here...
		if(!Double.isNaN(m11)) {
			double x = offset.x;
			offset.x = m11 * x + m12 * offset.y;
			offset.y = m21 * x + m22 * offset.y;
		}
		
		offset.x += refIndex.x;
		offset.y += refIndex.y;
	}
	
	public final void toOffset(Vector2D index) {
		index.x -= refIndex.x;
		index.y -= refIndex.y;

		// inverse transform
		if(!Double.isNaN(m11)) {
			double x = index.x;
			index.x = im11 * x + im12 * index.y;
			index.y = im21 * x + im22 * index.y;
		}
		
		index.x *= delta.x;
		index.y *= delta.y;
	}
	
    public final SphericalCoordinates getReference() { return projection.getReference(); }
    
    public final SphericalProjection getProjection() { return projection; }

    public void getIndex(SphericalCoordinates coords, Vector2D index) {
    	projection.project(coords, index);
    	toIndex(index);
    }
   
    public void getCoords(Vector2D index, SphericalCoordinates coords) {
    	double i = index.x;
    	double j = index.y; 
    	
    	toOffset(index);
    	projection.deproject(index, coords);
    	
    	index.x = i;
    	index.y = j;
    }
}
