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

import java.text.*;
import java.util.*;

import util.CoordinateAxis;
import util.CoordinateSystem;
import util.SphericalCoordinates;
import util.Unit;
import util.Vector2D;
import util.text.AngleFormat;
import util.text.HourAngleFormat;

import nom.tam.fits.*;
import nom.tam.util.*;


// x, y kept in longitude,latitude form
// use RA(), DEC(), setRA() and setDEC(functions) to for RA, DEC coordinates...

public class EquatorialCoordinates extends CelestialCoordinates implements Precessing {
	
	public CoordinateEpoch epoch;
	
	static CoordinateAxis rightAscentionAxis, declinationAxis, rightAscentionOffsetAxis, declinationOffsetAxis;
	static CoordinateSystem defaultCoordinateSystem, defaultLocalCoordinateSystem;
	
	static {
		defaultCoordinateSystem = new CoordinateSystem("Equatorial Coordinates");
		defaultLocalCoordinateSystem = new CoordinateSystem("Equatorial Offsets");
		
		rightAscentionAxis = new CoordinateAxis("Right Ascention", "RA--");
		rightAscentionAxis.setReverse(true);
		rightAscentionAxis.setFormat(new HourAngleFormat(2));

		declinationAxis = new CoordinateAxis("Declination", "DEC-");
		declinationAxis.setFormat(new AngleFormat(1));

		rightAscentionOffsetAxis = new CoordinateAxis("dRA", rightAscentionAxis.wcsName);
		rightAscentionOffsetAxis.setReverse(true);
	
		declinationOffsetAxis = new CoordinateAxis("dDEC", declinationAxis.wcsName);
		
		defaultCoordinateSystem.add(rightAscentionAxis);
		defaultCoordinateSystem.add(declinationAxis);
		
		defaultLocalCoordinateSystem.add(rightAscentionOffsetAxis);
		defaultLocalCoordinateSystem.add(declinationOffsetAxis);		
	}
	
    public EquatorialCoordinates() { epoch = CoordinateEpoch.J2000; }

	public EquatorialCoordinates(String text) { super(text); }

	public EquatorialCoordinates(double ra, double dec) { super(ra, dec); epoch = CoordinateEpoch.J2000; }

	public EquatorialCoordinates(double ra, double dec, double aEpoch) { super(ra, dec); epoch = aEpoch < 1984.0 ? new BesselianEpoch(aEpoch) : new JulianEpoch(aEpoch); }

	public EquatorialCoordinates(double ra, double dec, String epochSpec) { super(ra, dec); epoch = CoordinateEpoch.forString(epochSpec); }
	
	public EquatorialCoordinates(double ra, double dec, CoordinateEpoch epoch) { super(ra, dec); this.epoch = epoch; }
		
	
	public EquatorialCoordinates(CelestialCoordinates from) { super(from); }
	
	public EquatorialCoordinates copy() {
		EquatorialCoordinates copy = (EquatorialCoordinates) clone();
		if(epoch != null) copy.epoch = (CoordinateEpoch) epoch.clone();
		return copy;
	}
	
	@Override
	public void setDefaultCoordinates() {
		coordinateSystem = EquatorialCoordinates.defaultCoordinateSystem;
    	localCoordinateSystem = EquatorialCoordinates.defaultLocalCoordinateSystem;		
	}

	@Override
	public boolean equals(Object o) {
		if(!super.equals(o)) return false;
		EquatorialCoordinates coords = (EquatorialCoordinates) o;
		if(!coords.epoch.equals(epoch)) return false;
		return true;		
	}
	
	public void copy(SphericalCoordinates coords) {
		super.copy(coords);
		if(!(coords instanceof EquatorialCoordinates)) return;
		EquatorialCoordinates equatorial = (EquatorialCoordinates) coords;
		epoch = (CoordinateEpoch) equatorial.epoch.clone();
	}
	
	public double RA() { return longitude(); }

	public double rightAscension() { return RA(); }

	public double DEC() { return y; }

	public double declination() { return y; }

	public void setRA(double RA) { setLongitude(RA); }

	public void setDEC(double DEC) { setLatitude(DEC); }

	public double getParallacticAngle(GeodeticCoordinates site, double LST) {
		double H = LST * Unit.timeAngle - RA();
		double cosasinq = site.cosLat * Math.sin(H);
		double cosacosq = site.sinLat * cosLat - site.cosLat * sinLat * Math.cos(H);
		return Math.atan2(cosasinq, cosacosq);
	}
	
	public HorizontalCoordinates toHorizontal(GeodeticCoordinates site, double LST) {
		HorizontalCoordinates horizontal = new HorizontalCoordinates();
		toHorizontal(this, horizontal, site, LST);
		return horizontal;
	}
	
	public void toHorizontal(HorizontalCoordinates toCoords, GeodeticCoordinates site, double LST) { toHorizontal(this, toCoords, site, LST); }
	
	
	public void toHorizontalOffset(Vector2D offset, GeodeticCoordinates site, double LST) {
		toHorizontalOffset(offset, getParallacticAngle(site, LST));
	}

	public static void toHorizontalOffset(Vector2D offset, double PA) {
		offset.x *= -1.0;
		offset.rotate(-PA);
	}
	
	public static void toHorizontal(EquatorialCoordinates equatorial, HorizontalCoordinates horizontal, GeodeticCoordinates site, double LST) {
		double H = LST * Unit.timeAngle - equatorial.RA();
		double cosH = Math.cos(H);
		horizontal.setNativeLatitude(asin(equatorial.sinLat * site.sinLat + equatorial.cosLat * site.cosLat * cosH));
		double asinA = -Math.sin(H) * equatorial.cosLat;
		double acosA = site.cosLat * equatorial.sinLat - site.sinLat * equatorial.cosLat * cosH;
		horizontal.x = Math.atan2(asinA, acosA);
	}

	public void precess(CoordinateEpoch newEpoch) {
		if(epoch.equals(newEpoch)) return;
		Precession precession = new Precession(epoch, newEpoch);
		precession.precess(this);
	}
	
	@Override
	public String toString() {
		return super.toString() + " (" + (epoch == null ? "unknown" : epoch.toString()) + ")";	
	}
	
	@Override
	public String toString(NumberFormat nf) {
		return super.toString(nf) + " " + "(" + (epoch == null ? "unknown" : epoch.toString()) + ")";	
	}
	
	@Override
	public String toString(DecimalFormat df) {
		return super.toString(df) + " " + "(" + (epoch == null ? "unknown" : epoch.toString()) + ")";	
	}
	

	@Override
	public void parse(String coords) throws NumberFormatException, IllegalArgumentException {
		StringTokenizer tokens = new StringTokenizer(coords, ",() \t\r\n");
		super.parse(tokens.nextToken() + " " + tokens.nextToken());
		
		try { epoch = tokens.hasMoreTokens() ? CoordinateEpoch.forString(tokens.nextToken()) : null; }
		catch(NumberFormatException e) { epoch = null; }
	}

	
	@Override
	public void edit(Cursor cursor, String alt) throws HeaderCardException {
		super.edit(cursor, alt);
		cursor.add(new HeaderCard("RADESYS" + alt, epoch instanceof BesselianEpoch ? "FK4" : "FK5", "The coordinate system used."));
		epoch.edit(cursor, alt);
	}
	
	@Override
	public void parse(Header header, String alt) {
		super.parse(header, alt);
		
		String system = header.getStringValue("RADESYS");
		if(system == null) system = header.getDoubleValue("EQUINOX" + alt) < 1984.0 ? "FK4" : "FK5";
		
		if(system.equalsIgnoreCase("FK4")) epoch = new BesselianEpoch();
		else if(system.equalsIgnoreCase("FK4-NO-E")) epoch = new BesselianEpoch();
		else epoch = new JulianEpoch();
		
		epoch.parse(header, alt);
	}

	@Override
	public EquatorialCoordinates getEquatorialPole() { return equatorialPole; }

	@Override
	public double getZeroLongitude() { return 0.0; }
	
	private static EquatorialCoordinates equatorialPole = new EquatorialCoordinates(0.0, rightAngle);

	public CoordinateEpoch getEpoch() { return epoch; }
}
