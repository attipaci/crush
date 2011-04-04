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

package util;

import java.text.*;
import java.util.*;

import util.text.AngleFormat;

import nom.tam.fits.*;
import nom.tam.util.*;

// TODO add BinaryTableIO interface (with projections...)

public class SphericalCoordinates extends CoordinatePair {
	public double cosLat, sinLat;
	
	public CoordinateSystem coordinateSystem, localCoordinateSystem;

	static CoordinateAxis longitudeAxis, latitudeAxis, longitudeOffsetAxis, latitudeOffsetAxis;
	static CoordinateSystem defaultCoordinateSystem, defaultLocalCoordinateSystem;
	
	static {
		defaultCoordinateSystem = new CoordinateSystem("Spherical Coordinates");
		defaultLocalCoordinateSystem = new CoordinateSystem("Spherical Offsets");
		
		longitudeAxis = new CoordinateAxis("Latitude", "LON-");
		longitudeAxis.setFormat(new AngleFormat(3));
		longitudeAxis.setTickUnits(new double[] { Unit.deg, Unit.arcmin, Unit.arcsec, Unit.mas});

		latitudeAxis = new CoordinateAxis("Longitude", "LAT-");
		latitudeAxis.setFormat(new AngleFormat(3));
		latitudeAxis.setTickUnits(new double[] { Unit.deg, Unit.arcmin, Unit.arcsec, Unit.mas });

		longitudeOffsetAxis = new CoordinateAxis("d phi", longitudeAxis.wcsName);
		latitudeOffsetAxis = new CoordinateAxis("d theta", latitudeAxis.wcsName);
		
		defaultCoordinateSystem.add(longitudeAxis);
		defaultCoordinateSystem.add(latitudeAxis);
		
		defaultLocalCoordinateSystem.add(longitudeOffsetAxis);
		defaultLocalCoordinateSystem.add(latitudeOffsetAxis);			
	}
	
	public SphericalCoordinates() {}

	public SphericalCoordinates(double X, double Y) { setDefaultCoordinates(); set(X,Y); }
	
	public SphericalCoordinates(String text) { setDefaultCoordinates(); parse(text); }
	
	@Override
	public final void defaults() {
		super.defaults();
		cosLat = 1.0;
		sinLat = 0.0;
		setDefaultCoordinates();
	}
	
	public void setDefaultCoordinates() {
		coordinateSystem = SphericalCoordinates.defaultCoordinateSystem;
		localCoordinateSystem = SphericalCoordinates.defaultLocalCoordinateSystem;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o.getClass().equals(getClass())) return false;
		SphericalCoordinates coords = (SphericalCoordinates) o;
		if(!equalAngles(coords.x, x)) return false;
		if(!equalAngles(coords.y, y)) return false;
		return true;		
	}
	
	@Override
	public void copy(CoordinatePair coords) {
		setNativeLongitude(coords.x);
		setNativeLatitude(coords.y);
	}

	@Override
	public void zero() { super.zero(); cosLat = 1.0; sinLat = 0.0; }

	@Override
	public void NaN() { super.NaN(); cosLat = Double.NaN; sinLat = Double.NaN; }

	@Override
	public void setNative(double lon, double lat) { setNativeLongitude(lon); setNativeLatitude(lat); }
	
	@Override
	public void set(double lon, double lat) { setLongitude(lon); setLatitude(lat); }
	
	public final double nativeLongitude() { return x; }
	
	public final double nativeLatitude() { return y; }
	
	public final boolean isReverseLongitude() { return coordinateSystem.get(0).isReverse(); }
	
	public final boolean isReverseLatitude() { return coordinateSystem.get(1).isReverse(); }
	
	// Like long on lat except returns the actual directly formattable
	// coordinates for this system...
	public final double longitude() { return isReverseLongitude() ? coordinateSystem.get(0).reverseFrom-nativeLongitude() : nativeLongitude(); }
	
	public final double latitude() { return isReverseLatitude() ? coordinateSystem.get(1).reverseFrom-nativeLatitude() : nativeLatitude(); }
	
	public final void setNativeLongitude(final double value) { x = Math.IEEEremainder(value, 2.0*Math.PI); }
	
	public final void setNativeLatitude(final double value) { 
		y = Math.IEEEremainder(value, Math.PI);
		cosLat = Math.cos(y);
		sinLat = Math.sin(y);
	}

	public final void setLongitude(final double value) {
		setNativeLongitude(isReverseLongitude() ? coordinateSystem.get(0).reverseFrom-value : value);
	}
	
	public final void setLatitude(final double value) {
		setNativeLatitude(isReverseLatitude() ? coordinateSystem.get(1).reverseFrom-value : value);
	}
	
	public double getCosLat() { return cosLat; }
	
	public double getSinLat() { return sinLat; }

	
	public void project(SphericalProjection projection, CoordinatePair toNativeOffset) {
		projection.project(this, toNativeOffset);
	}
	
	public void setProjected(SphericalProjection projection, CoordinatePair fromNativeOffset) {
		projection.deproject(fromNativeOffset, this);
	}
		
	public CoordinatePair getProjected(SphericalProjection projection) { return projection.getProjected(this); }
	
	
	public void addNativeOffset(Vector2D offset) {
		x += offset.x / cosLat;
		setNativeLatitude(y + offset.y);
	}
	
	public void addOffset(Vector2D offset) {
		if(isReverseLongitude()) x -= offset.x / cosLat;
		else x += offset.x / cosLat;
		if(isReverseLatitude()) setNativeLatitude(y - offset.y);
		else setNativeLatitude(y + offset.y);
	}
	
	public void subtractNativeOffset(Vector2D offset) {
		x -= offset.x / cosLat;
		setNativeLatitude(y - offset.y);
	}
	
	public void subtractOffset(Vector2D offset) {
		if(isReverseLongitude()) x += offset.x / cosLat;
		else x -= offset.x / cosLat;
		if(isReverseLatitude()) setNativeLatitude(y + offset.y);
		else setNativeLatitude(y - offset.y);
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
		toOffset.x = Math.IEEEremainder(x - reference.x, fullTurn) * reference.cosLat;
		toOffset.y = y - reference.y;
	}
	
	public void getOffsetFrom(final SphericalCoordinates reference, final Vector2D toOffset) {
		getNativeOffsetFrom(reference, toOffset);
		if(isReverseLongitude()) toOffset.x *= -1.0;
		if(isReverseLatitude()) toOffset.y *= -1.0;
	}
		
	public void standardize() {
		x = Math.IEEEremainder(x, fullTurn);
		y = Math.IEEEremainder(y, Math.PI);
	}
	
	public String[] getFitsAxisNames(SphericalProjection projection) {
		String[] name = new String[2];
		name[0] = coordinateSystem.get(0).wcsName + projection.getFitsID();
		name[1] = coordinateSystem.get(1).wcsName + projection.getFitsID();	
		return name;
	}
	
	@Override
	public String toString() {
		return coordinateSystem.get(0).format(x) + " " + coordinateSystem.get(1).format(y);
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
		double sindTheta = Math.sin(point.y - y);
		double sindPhi = Math.sin(point.x - x);
		return 2.0 * asin(Math.sqrt(sindTheta * sindTheta + cosLat * point.cosLat * sindPhi * sindPhi));
	}

	public void edit(Cursor cursor) throws HeaderCardException { edit(cursor, ""); }
	
	public void edit(Cursor cursor, String alt) throws HeaderCardException {
		cursor.add(new HeaderCard("CRVAL1" + alt, longitude() / Unit.deg, "The reference longitude coordinate (deg)."));
		cursor.add(new HeaderCard("CRVAL2" + alt, latitude() / Unit.deg, "The reference latitude coordinate (deg)."));
	}

	
	public void parse(Header header) { parse(header, ""); }
		
	public void parse(Header header, String alt) {
		setLongitude(header.getDoubleValue("CRVAL1" + alt, 0.0) * Unit.deg);
		setLatitude(header.getDoubleValue("CRVAL2" + alt, 0.0) * Unit.deg);
	}
	
	
	public static boolean equalAngles(double a1, double a2) {
		return Math.abs(a1-a2) < angularAccuracy;
	}
	
	public final static double angularAccuracy = 1e-12;
	public final static double fullTurn = 2.0 * Math.PI;
	public final static double halfTurn = Math.PI;
	public final static double quarterTurn = 0.5 * Math.PI;
	
	public static final void transform(final SphericalCoordinates from, final SphericalCoordinates newPole, final double phi0, final SphericalCoordinates to) {		
		final double dL = from.x - newPole.x;
		final double cosdL = Math.cos(dL);	
		to.setNativeLatitude(asin(newPole.sinLat * from.sinLat + newPole.cosLat * from.cosLat * cosdL));
		to.setNativeLongitude(quarterTurn - phi0 +
				Math.atan2(-from.sinLat * newPole.cosLat + from.cosLat * newPole.sinLat * cosdL, -from.cosLat * Math.sin(dL))
		);	
	}
	
	public static final void inverseTransform(final SphericalCoordinates from, final SphericalCoordinates pole, final double phi0, final SphericalCoordinates to) {		
		final double dL = from.x + phi0;
		final double cosdL = Math.cos(dL);
		
		to.setNativeLatitude(asin(pole.sinLat * from.sinLat + pole.cosLat * from.cosLat * cosdL));
		to.setNativeLongitude(pole.x + quarterTurn + 
				Math.atan2(-from.sinLat * pole.cosLat + from.cosLat * pole.sinLat * cosdL, -from.cosLat * Math.sin(dL)));	
	}
}
