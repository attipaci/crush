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

package util;

import java.text.*;
import java.util.*;

import util.text.AngleFormat;

import nom.tam.fits.*;
import nom.tam.util.*;

// TODO add BinaryTableIO interface (with projections...)

public class SphericalCoordinates extends CoordinatePair implements Metric<SphericalCoordinates> {
	private double cosLat, sinLat;
	
	private CoordinateSystem coordinateSystem, localCoordinateSystem;

	static CoordinateAxis longitudeAxis, latitudeAxis, longitudeOffsetAxis, latitudeOffsetAxis;
	static CoordinateSystem defaultCoordinateSystem, defaultLocalCoordinateSystem;
	
	static {
		
		defaultCoordinateSystem = new CoordinateSystem("Spherical Coordinates");
		defaultLocalCoordinateSystem = new CoordinateSystem("Spherical Offsets");
		
		longitudeAxis = new CoordinateAxis("Latitude", "LON-");
		longitudeAxis.setFormat(new AngleFormat(3));
	
		latitudeAxis = new CoordinateAxis("Longitude", "LAT-");
		latitudeAxis.setFormat(new AngleFormat(3));
	
		longitudeOffsetAxis = new CoordinateAxis("dLon", longitudeAxis.wcsName);
		latitudeOffsetAxis = new CoordinateAxis("dLat", latitudeAxis.wcsName);
		
		defaultCoordinateSystem.add(longitudeAxis);
		defaultCoordinateSystem.add(latitudeAxis);
		
		defaultLocalCoordinateSystem.add(longitudeOffsetAxis);
		defaultLocalCoordinateSystem.add(latitudeOffsetAxis);			
	}
	
	public SphericalCoordinates() {
		cosLat = 1.0;
		sinLat = 0.0;
		setDefaultCoordinates();		
	}

	public SphericalCoordinates(double X, double Y) { setDefaultCoordinates(); set(X,Y); }
	
	public SphericalCoordinates(String text) { setDefaultCoordinates(); parse(text); }
	
	public void setDefaultCoordinates() {
		coordinateSystem = SphericalCoordinates.defaultCoordinateSystem;
		localCoordinateSystem = SphericalCoordinates.defaultLocalCoordinateSystem;
	}
	
	public final double sinLat() { return sinLat; }
	
	public final double cosLat() { return cosLat; }
	
	public final CoordinateSystem getCoordinateSystem() { return coordinateSystem; }
	
	public final CoordinateSystem getLocalCoordinateSystem() { return localCoordinateSystem; }
	
	public void setCoordinateSystem(CoordinateSystem c) { this.coordinateSystem = c; }
	
	public void setLocalCoordinateSystem(CoordinateSystem c) { this.localCoordinateSystem = c; }
	
	@Override
	public boolean equals(Object o) {
		if(o.getClass().equals(getClass())) return false;
		SphericalCoordinates coords = (SphericalCoordinates) o;
		if(!equalAngles(coords.getX(), getX())) return false;
		if(!equalAngles(coords.getY(), getY())) return false;
		return true;		
	}
	
	@Override
	public void copy(CoordinatePair coords) {
		setNativeLongitude(coords.getX());
		setNativeLatitude(coords.getY());
	}
	
	
	@Override
	public final void setY(final double value) { 
		super.setY(Math.IEEEremainder(value, Math.PI));
		sinLat = Math.sin(value);
		cosLat = Math.cos(value);
	}
	
	@Override
	public final void addY(final double value) { 
		super.addY(Math.IEEEremainder(value, Math.PI));
		sinLat = Math.sin(getY());
		cosLat = Math.cos(getY());
	}
	
	@Override
	public void zero() { super.zero(); cosLat = 1.0; sinLat = 0.0; }

	@Override
	public void NaN() { super.NaN(); cosLat = Double.NaN; sinLat = Double.NaN; }

	@Override
	public void set(double lon, double lat) { setLongitude(lon); setLatitude(lat); }
		
	public void setNative(double x, double y) { super.set(x,  y); }
	
	public final double nativeLongitude() { return getX(); }
	
	public final double nativeLatitude() { return getY(); }
	
	public final boolean isReverseLongitude() { return coordinateSystem.get(0).isReverse(); }
	
	public final boolean isReverseLatitude() { return coordinateSystem.get(1).isReverse(); }
	
	// Like long on lat except returns the actual directly formattable
	// coordinates for this system...
	public final double longitude() { return isReverseLongitude() ? coordinateSystem.get(0).reverseFrom-nativeLongitude() : nativeLongitude(); }
	
	public final double latitude() { return isReverseLatitude() ? coordinateSystem.get(1).reverseFrom-nativeLatitude() : nativeLatitude(); }
	
	public final void setNativeLongitude(final double value) { setX(value); }
		
	public final void setNativeLatitude(final double value) { setY(value); }

	public final void setLongitude(final double value) {
		setNativeLongitude(isReverseLongitude() ? coordinateSystem.get(0).reverseFrom-value : value);
	}
	
	public final void setLatitude(final double value) {
		setNativeLatitude(isReverseLatitude() ? coordinateSystem.get(1).reverseFrom-value : value);
	}
	
	public void project(SphericalProjection projection, CoordinatePair toNativeOffset) {
		projection.project(this, toNativeOffset);
	}
	
	public void setProjected(SphericalProjection projection, CoordinatePair fromNativeOffset) {
		projection.deproject(fromNativeOffset, this);
	}
		
	public CoordinatePair getProjected(SphericalProjection projection) { return projection.getProjected(this); }
	
	
	public void addNativeOffset(final Vector2D offset) {
		addX(offset.getX() / cosLat);
		addY(offset.getY());
	}
	
	public void addOffset(final Vector2D offset) {
		if(isReverseLongitude()) subtractX(offset.getX() / cosLat);
		else addX(offset.getX() / cosLat);
		if(isReverseLatitude()) subtractY(offset.getY());
		else addY(offset.getY());
	}
	
	public void subtractNativeOffset(final Vector2D offset) {
		subtractX(offset.getX() / cosLat);
		subtractY(offset.getY());
	}
	
	public void subtractOffset(final Vector2D offset) {
		if(isReverseLongitude()) addX(offset.getX() / cosLat);
		else subtractX(offset.getX() / cosLat);
		if(isReverseLatitude()) addY(offset.getY());
		else subtractY(offset.getY());
	}
	
	
	public Vector2D getNativeOffsetFrom(SphericalCoordinates reference) {
		Vector2D offset = new Vector2D();
		getNativeOffsetFrom(reference, offset);
		return offset;
	}
	
	public Vector2D getOffsetFrom(SphericalCoordinates reference) {
		Vector2D offset = new Vector2D();
		getOffsetFrom(reference, offset);
		return offset;
	}
	
	
	public final void getNativeOffsetFrom(final SphericalCoordinates reference, final Vector2D toOffset) {
		toOffset.setX(Math.IEEEremainder(getX() - reference.getX(), Constant.twoPI) * reference.cosLat);
		toOffset.setY(getY() - reference.getY());
	}
	
	public void getOffsetFrom(final SphericalCoordinates reference, final Vector2D toOffset) {
		getNativeOffsetFrom(reference, toOffset);
		if(isReverseLongitude()) toOffset.scaleX(-1.0);
		if(isReverseLatitude()) toOffset.scaleY(-1.0);
	}
		
	public void standardize() {
		setX(Math.IEEEremainder(getX(), Constant.twoPI));
		setY(Math.IEEEremainder(getY(), Math.PI));
	}
	
	public String[] getFitsAxisNames(SphericalProjection projection) {
		String[] name = new String[2];
		name[0] = coordinateSystem.get(0).wcsName + projection.getFitsID();
		name[1] = coordinateSystem.get(1).wcsName + projection.getFitsID();	
		return name;
	}
	
	@Override
	public String toString() {
		return coordinateSystem.get(0).format(getX()) + " " + coordinateSystem.get(1).format(getY());
	}
	
	@Override
	public String toString(NumberFormat nf) {
		return nf.format(longitude()) + " " + nf.format(latitude());		
	}
	
	// Just use decimalformat. else one should call e.g. format(new HourAngleFormat(df))
	public String toString(DecimalFormat df) {		
		return df.format(longitude()) + " " + df.format(latitude());
	}

	@Override
	public void parse(String coords) throws NumberFormatException, IllegalArgumentException {
		StringTokenizer tokens = new StringTokenizer(coords, ", \t\n");
		
		try {
			setLongitude(coordinateSystem.get(0).format.parse(tokens.nextToken()).doubleValue());
			setLatitude(coordinateSystem.get(1).format.parse(tokens.nextToken()).doubleValue());
		} 
		catch(ParseException e) { throw new NumberFormatException(e.getMessage()); }
	}
	
	// Safe asin and acos for when rounding errors make values fall outside of -1:1 range.
	protected static double asin(double value) {
		if(value < -1.0) value = -1.0;
		else if(value > 1.0) value = 1.0;
		return Math.asin(value);
	}
	
	protected static double acos(double value) {
		if(value < -1.0) value = -1.0;
		else if(value > 1.0) value = 1.0;
		return Math.acos(value);
	}

	public double distanceTo(SphericalCoordinates point) {
		double sindTheta = Math.sin(point.getY() - getY());
		double sindPhi = Math.sin(point.getX() - getX());
		return 2.0 * asin(Math.sqrt(sindTheta * sindTheta + cosLat * point.cosLat * sindPhi * sindPhi));
	}

	@Override
	public void edit(Cursor cursor, String alt) throws HeaderCardException {	
		// Always write longitude in the 0:2Pi range.
		// Some FITS utilities may require it, even if it's not required by the FITS standard...
		double lon = Math.IEEEremainder(longitude(), Constant.twoPI);
		if(lon < 0.0) lon += Constant.twoPI;

		cursor.add(new HeaderCard("CRVAL1" + alt, lon / Unit.deg, "The reference longitude coordinate (deg)."));
		cursor.add(new HeaderCard("CRVAL2" + alt, latitude() / Unit.deg, "The reference latitude coordinate (deg)."));
	}
		
	@Override
	public void parse(Header header, String alt) {
		setLongitude(header.getDoubleValue("CRVAL1" + alt, 0.0) * Unit.deg);
		setLatitude(header.getDoubleValue("CRVAL2" + alt, 0.0) * Unit.deg);
	}
	
	
	public static boolean equalAngles(double a1, double a2) {
		return Math.abs(a1-a2) < angularAccuracy;
	}
	
	public final static double angularAccuracy = 1e-12;
	
	
	public static final void transform(final SphericalCoordinates from, final SphericalCoordinates newPole, final double phi0, final SphericalCoordinates to) {		
		final double dL = from.getX() - newPole.getX();
		final double cosdL = Math.cos(dL);	
		to.setNativeLatitude(asin(newPole.sinLat * from.sinLat + newPole.cosLat * from.cosLat * cosdL));
		to.setNativeLongitude(Constant.rightAngle - phi0 +
				Math.atan2(-from.sinLat * newPole.cosLat + from.cosLat * newPole.sinLat * cosdL, -from.cosLat * Math.sin(dL))
		);	
	}
	
	public static final void inverseTransform(final SphericalCoordinates from, final SphericalCoordinates pole, final double phi0, final SphericalCoordinates to) {		
		final double dL = from.getX() + phi0;
		final double cosdL = Math.cos(dL);
		
		to.setNativeLatitude(asin(pole.sinLat * from.sinLat + pole.cosLat * from.cosLat * cosdL));
		to.setNativeLongitude(pole.getX() + Constant.rightAngle + 
				Math.atan2(-from.sinLat * pole.cosLat + from.cosLat * pole.sinLat * cosdL, -from.cosLat * Math.sin(dL)));	
	}
	
	public static Class<? extends SphericalCoordinates> getFITSClass(String spec) {
		spec = spec.toUpperCase();
		
		if(spec.startsWith("RA-")) return util.astro.EquatorialCoordinates.class;
		else if(spec.startsWith("DEC-")) return util.astro.EquatorialCoordinates.class;
		else if(spec.substring(1).startsWith("LON")) {
			switch(spec.charAt(0)) {
			case 'A' : return util.astro.HorizontalCoordinates.class;
			case 'G' : return util.astro.GalacticCoordinates.class;
			case 'E' : return util.astro.EclipticCoordinates.class;
			case 'S' : return util.astro.SuperGalacticCoordinates.class;
			}
		}
		else if(spec.substring(1).startsWith("LAT")) {
			switch(spec.charAt(0)) {
			case 'A' : return util.astro.HorizontalCoordinates.class;
			case 'G' : return util.astro.GalacticCoordinates.class;
			case 'E' : return util.astro.EclipticCoordinates.class;
			case 'S' : return util.astro.SuperGalacticCoordinates.class;
			}
		}
		throw new IllegalArgumentException("Unknown Coordinate Definition " + spec);
	}
}
