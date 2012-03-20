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

import util.CoordinateAxis;
import util.CoordinateSystem;
import util.SphericalCoordinates;
import util.Unit;
import util.Vector2D;
import util.text.AngleFormat;

public class HorizontalCoordinates extends SphericalCoordinates {
	static CoordinateAxis azimuthAxis, elevationAxis, azimuthOffsetAxis, elevationOffsetAxis;
	static CoordinateSystem defaultCoordinateSystem, defaultLocalCoordinateSystem;

	static {
		defaultCoordinateSystem = new CoordinateSystem("Horizontal Coordinates");
		defaultLocalCoordinateSystem = new CoordinateSystem("Horizontal Offsets");

		azimuthAxis = new CoordinateAxis("Azimuth", "ALON");
		elevationAxis = new CoordinateAxis("Elevation", "ALAT");
		azimuthOffsetAxis = new CoordinateAxis("dAZ", azimuthAxis.wcsName);
		elevationOffsetAxis = new CoordinateAxis("dEL", elevationAxis.wcsName);
		
		defaultCoordinateSystem.add(azimuthAxis);
		defaultCoordinateSystem.add(elevationAxis);
		defaultLocalCoordinateSystem.add(azimuthOffsetAxis);
		defaultLocalCoordinateSystem.add(elevationOffsetAxis);
		
		AngleFormat af = new AngleFormat(3);
		
		for(CoordinateAxis axis : defaultCoordinateSystem) axis.setFormat(af);
	}

	public HorizontalCoordinates() {}

	public HorizontalCoordinates(String text) { super(text); } 

	public HorizontalCoordinates(double az, double el) { super(az, el); }

	@Override
	public void setDefaultCoordinates() {
		coordinateSystem = defaultCoordinateSystem;
		localCoordinateSystem = defaultLocalCoordinateSystem;		
	}

	public double AZ() { return nativeLongitude(); }

	public double azimuth() { return nativeLongitude(); }

	public double EL() { return nativeLatitude(); }

	public double elevation() { return nativeLatitude(); }

	public double ZA() { return 90.0 * Unit.deg - nativeLatitude(); }

	public double zenithAngle() { return ZA(); }

	public double cosEL() { return cosLat(); }

	public void setAZ(double AZ) { setNativeLongitude(AZ); }

	public void setEL(double EL) { setNativeLatitude(EL); }

	public void setZA(double ZA) { setNativeLatitude(90.0 * Unit.deg - ZA); }

	public EquatorialCoordinates toEquatorial(GeodeticCoordinates site, double LST) {
		EquatorialCoordinates equatorial = new EquatorialCoordinates();
		toEquatorial(this, equatorial, site, LST);
		return equatorial;
	}
	
	public void toEquatorial(EquatorialCoordinates toCoords, GeodeticCoordinates site, double LST) { toEquatorial(this, toCoords, site, LST); }
	
	public double getParallacticAngle(GeodeticCoordinates site) {
		double cosdsinq = -site.cosLat() * Math.sin(getX());
		double cosdcosq = site.sinLat() * cosLat() - site.cosLat() * sinLat() * Math.cos(getX());
		return Math.atan2(cosdsinq, cosdcosq);
	}
	
	public static void toEquatorial(HorizontalCoordinates horizontal, EquatorialCoordinates equatorial, GeodeticCoordinates site, double LST) {
		double cosAZ = Math.cos(horizontal.getX());
		equatorial.setNativeLatitude(asin(horizontal.sinLat() * site.sinLat() + horizontal.cosLat() * site.cosLat() * cosAZ));
		final double asinH = -Math.sin(horizontal.getX()) * horizontal.cosLat();
		final double acosH = site.cosLat() * horizontal.sinLat() - site.sinLat() * horizontal.cosLat() * cosAZ;
		equatorial.setRA(LST * Unit.timeAngle - Math.atan2(asinH, acosH));
	}

	
	public void toEquatorial(Vector2D offset, GeodeticCoordinates site) {
		toEquatorialOffset(offset, getParallacticAngle(site));
	}

	public static void toEquatorialOffset(Vector2D offset, double PA) {
		offset.rotate(PA);
		offset.scaleX(-1.0);
	}    

}
