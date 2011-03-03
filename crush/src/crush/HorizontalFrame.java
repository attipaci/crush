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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import util.*;
import util.astro.EquatorialCoordinates;
import util.astro.HorizontalCoordinates;


public abstract class HorizontalFrame extends Frame implements GroundBased {
	public HorizontalCoordinates horizontal; // includes chopping offsets
	public Vector2D horizontalOffset; // includes chopping offsets
	public double cosPA, sinPA;
		
	public HorizontalFrame(Scan<?, ?> parent) {
		super(parent);
	}
	
	@Override
	public void validate() {
		if(equatorial == null) calcEquatorial();
		else if(horizontal == null) calcHorizontal();
		super.validate();
	}
	
	
	@Override
	public void getEquatorial(final Vector2D position, final EquatorialCoordinates coords) {
		// The proper GLS convention uses actual cos(DEC)
		// However, APECS uses cos(DEC0)
		final double x = getX(position);
		final double y = getY(position);
		coords.setNativeLongitude(equatorial.x + (cosPA * x - sinPA * y) / scan.equatorial.cosLat);
		coords.setNativeLatitude(equatorial.y + (cosPA * y + sinPA * x));
	}
	
	@Override
	public void getHorizontal(final Vector2D position, final HorizontalCoordinates coords) {
		// The proper GLS convention uses actual cos(DEC)
		// However, APECS uses cos(DEC0)
		coords.setNativeLongitude(horizontal.x + getX(position) / scan.horizontal.cosLat);
		coords.setNativeLatitude(horizontal.y + getY(position));
	}
	
	@Override
	public void getHorizontalOffset(final Vector2D position, final Vector2D offset) {
		offset.x = horizontalOffset.x + getX(position);
		offset.y = horizontalOffset.y + getY(position);
	}
	
	
	@Override
	public final void getEquatorialOffset(final Vector2D position, final Vector2D offset) {
		getHorizontalOffset(position, offset);
		toEquatorial(offset);
	}
	
	@Override
	public HorizontalCoordinates getNativeCoords() {
		return horizontal;
	}

	@Override
	public Vector2D getNativeOffset() {
		return horizontalOffset;
	}	
	
	// Calculates the parallactic angle from the site and the horizontal coordinates...
	public void calcParallacticAngle() {
		setParallacticAngle(horizontal.getParallacticAngle(scan.site));		
	}
	
	// Calculates the parallactic angle from the site and the equatorial coordinates...
	public void calcParallacticAngle(double LST) {
		setParallacticAngle(equatorial.getParallacticAngle(scan.site, LST));		
	}
	
	public void setParallacticAngle(double angle) {
		sinPA = Math.sin(angle);
		cosPA = Math.cos(angle);
	}
	
	public double getParallacticAngle() {
		return Math.atan2(sinPA, cosPA);
	}
 	
	public void calcHorizontal() {
		EquatorialCoordinates apparent = new EquatorialCoordinates();
		getApparentEquatorial(apparent);
		horizontal = apparent.toHorizontal(scan.site, LST);
	}
	
	public void calcEquatorial() {
		// This assumes that the object is tracked on sky...
		if(scan.isTracking) {
			if(equatorial == null) equatorial = (EquatorialCoordinates) scan.equatorial.clone();
			equatorial.setNativeLongitude(scan.equatorial.x + (cosPA * horizontalOffset.x - sinPA * horizontalOffset.y) / scan.equatorial.cosLat);
			equatorial.setNativeLatitude(scan.equatorial.y + (cosPA * horizontalOffset.y + sinPA * horizontalOffset.x));	
		}
		else {
			equatorial = horizontal.toEquatorial(scan.site, LST);
			scan.fromApparent.precess(equatorial);
		}
	}
	
	@Override
	public void pointingAt(Vector2D center) {
		super.pointingAt(center);
		calcEquatorial();
	}
	
	public void setZenithTau(double value) {
		zenithTau = value;
		transmission = (float) Math.exp(-zenithTau/horizontal.sinLat);
	}
	
	// Rotate by PA
	public final void toEquatorial(Vector2D offset) {
		final double x = offset.x;
		offset.x = cosPA * x - sinPA * offset.y;
		offset.y = cosPA * offset.y + sinPA * x;
		offset.x *= -1.0;
	}
	
	// Rotate by -PA
	public final void toHorizontal(Vector2D offset) {
		final double x = -offset.x;
		offset.x = cosPA * x + sinPA * offset.y;
		offset.y = cosPA * offset.y - sinPA * x;
	}
	
	@Override
	public final void nativeToEquatorial(Vector2D offset) {
		toEquatorial(offset);
	}
	
	@Override
	public final void equatorialToNative(Vector2D offset) {
		toHorizontal(offset);
	}
	
	@Override
	public final void nativeToEquatorial(SphericalCoordinates coords, EquatorialCoordinates equatorial) {
		((HorizontalCoordinates) coords).toEquatorial(equatorial, scan.site, LST);	
	}
	
	@Override
	public final void equatorialToNative(EquatorialCoordinates equatorial, SphericalCoordinates coords) {
		equatorial.toHorizontal((HorizontalCoordinates) coords, scan.site, LST);
	}
	
}
