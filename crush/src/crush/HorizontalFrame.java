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
package crush;

import kovacs.astro.CelestialProjector;
import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.HorizontalCoordinates;
import kovacs.math.SphericalCoordinates;
import kovacs.math.Vector2D;


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
		coords.setNativeLongitude(equatorial.getX() + (cosPA * x - sinPA * y) / scan.equatorial.cosLat());
		coords.setNativeLatitude(equatorial.getY() + (cosPA * y + sinPA * x));
	}
	
	public void getHorizontal(final Vector2D position, final HorizontalCoordinates coords) {
		// The proper GLS convention uses actual cos(DEC)
		// However, APECS uses cos(DEC0)
		coords.setNativeLongitude(horizontal.getX() + getX(position) / scan.horizontal.cosLat());
		coords.setNativeLatitude(horizontal.getY() + getY(position));
	}
	
	public void getHorizontalOffset(final Vector2D position, final Vector2D offset) {
		offset.setX(horizontalOffset.getX() + getX(position));
		offset.setY(horizontalOffset.getY() + getY(position));
	}
	
	@Override
	public void getNativeOffset(final Vector2D position, final Vector2D offset) {
		getHorizontalOffset(position, offset);
	}
	
	@Override
	public void getNativeOffset(final Vector2D offset) {
		offset.copy(horizontalOffset);
	}
		
	@Override
	public final void getEquatorialNativeOffset(final Vector2D position, final Vector2D offset) {
		getHorizontalOffset(position, offset);
		horizontalToEquatorialNative(offset);
	}
	
	@Override
	public HorizontalCoordinates getNativeCoords() {
		return horizontal;
	}

	@Override
	public Vector2D getNativeOffset() {
		return horizontalOffset;
	}	
	
	@Override
	public void project(final Vector2D position, final CelestialProjector projector) {
		if(projector.isHorizontal()) {
			getHorizontalOffset(position, projector.offset);
			projector.setReferenceCoords();
			projector.getCoordinates().addNativeOffset(projector.offset);
			projector.project();
		}
		else super.project(position, projector);		
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
		// Uses the scanning offsets, on top of the tracking coordinate of the scan...
		if(scan.isTracking) {
			if(equatorial == null) equatorial = (EquatorialCoordinates) scan.equatorial.clone();
			equatorial.setNativeLongitude(scan.equatorial.getX() + (cosPA * horizontalOffset.getX() - sinPA * horizontalOffset.getY()) / scan.equatorial.cosLat());
			equatorial.setNativeLatitude(scan.equatorial.getY() + (cosPA * horizontalOffset.getY() + sinPA * horizontalOffset.getX()));	
		}
		// Otherwise do the proper conversion....
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
		transmission = (float) Math.exp(-zenithTau/horizontal.sinLat());
	}
	
	// Rotate by PA
	public final void horizontalToEquatorialNative(Vector2D offset) {
		final double x = offset.getX();
		offset.setX(cosPA * x - sinPA * offset.getY());
		offset.setY(cosPA * offset.getY() + sinPA * x);
	}
	
	public final void horizontalToEquatorial(Vector2D offset) {
		horizontalToEquatorialNative(offset);
		offset.scaleX(-1.0);
	}
	
	// Rotate by -PA
	public final void quatorialNativeToHorizontal(Vector2D offset) {
		final double x = offset.getX();
		offset.setX(cosPA * x + sinPA * offset.getY());
		offset.setY(cosPA * offset.getY() - sinPA * x);
	}
	
	public final void equatorialToHorizontal(Vector2D offset) {
		offset.scaleX(-1.0);
		quatorialNativeToHorizontal(offset);
	}
	
	@Override
	public final void nativeToEquatorialNative(Vector2D offset) {
		horizontalToEquatorialNative(offset);
	}
	
	@Override
	public final void equatorialNativeToNative(Vector2D offset) {
		quatorialNativeToHorizontal(offset);
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
