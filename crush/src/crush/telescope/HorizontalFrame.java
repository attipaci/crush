/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.telescope;

import jnum.astro.AstroProjector;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.GeodeticCoordinates;
import jnum.astro.HorizontalCoordinates;
import jnum.math.Angle;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.projection.Projector2D;


public abstract class HorizontalFrame extends TelescopeFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4706123354315623700L;
	public HorizontalCoordinates horizontal; 	// includes chopping offsets
	public Vector2D horizontalOffset; 			// includes chopping offsets

	private Angle PA;                           // parallactic angle
	
	public double zenithTau = 0.0;
	
	public HorizontalFrame(GroundBasedScan<? extends TelescopeInstrument<?>, ? extends GroundBasedIntegration<?,?>> parent) {
		super(parent);
	}
	
	@Override
	public HorizontalFrame copy(boolean withContents) {
		HorizontalFrame copy = (HorizontalFrame) super.copy(withContents);
		
		if(horizontal != null) copy.horizontal = (HorizontalCoordinates) horizontal.copy();
		if(horizontalOffset != null) copy.horizontalOffset = horizontalOffset.copy();
		
		return copy;
	}
	
	@SuppressWarnings("unchecked")
    @Override
    public GroundBasedScan<? extends TelescopeInstrument<?>, ? extends GroundBasedIntegration<?,?>> getScan() { 
	    return (GroundBasedScan<? extends TelescopeInstrument<?>, ? extends GroundBasedIntegration<?,?>>) super.getScan();
	}

	
	@Override
	public boolean validate() {
	    if(hasTelescopeInfo) {
	        if(equatorial == null) calcEquatorial();
	        else if(horizontal == null) calcHorizontal();
	    }
		return super.validate();
	}
	
	@Override
	public void getEquatorial(final Vector2D position, final EquatorialCoordinates coords) {
		final double x = getNativeX(position);
		final double y = getNativeY(position);
        coords.setNativeLatitude(equatorial.y() + (PA.cos() * y + PA.sin() * x));
		coords.setNativeLongitude(equatorial.x() + (PA.cos() * x - PA.sin() * y) / coords.cosLat());
	}
	
	public void getHorizontal(final Vector2D position, final HorizontalCoordinates coords) {
        coords.setNativeLatitude(horizontal.y() + getNativeY(position));
	    coords.setNativeLongitude(horizontal.x() + getNativeX(position) / coords.cosLat());
	}
	
	public void getHorizontalOffset(final Vector2D position, final Vector2D offset) {
		offset.setX(horizontalOffset.x() + getNativeX(position));
		offset.setY(horizontalOffset.y() + getNativeY(position));
	}
	
	@Override
	public void getNativeOffset(final Vector2D position, final Vector2D offset) {
		getHorizontalOffset(position, offset);
	}
	
	
	@Override
	public final void getEquatorialNativeOffset(final Vector2D position, final Vector2D offset) {
		getHorizontalOffset(position, offset);
		horizontalToNativeEquatorial(offset);
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
    public void getNativeOffset(final Vector2D offset) {
        offset.copy(horizontalOffset);
    }
        
	@Override
	public void project(final Vector2D position, final Projector2D<?> projector) {
	    if(projector instanceof AstroProjector) project(position, (AstroProjector) projector);
	    else super.project(position, projector);
	}
	    

	public void project(final Vector2D position, final AstroProjector projector) {
		if(projector.isHorizontal()) {
			projector.setReferenceCoords();
			getHorizontalOffset(position, projector.offset);
			projector.getCoordinates().addNativeOffset(projector.offset);
			projector.project();
		} 
		// TODO handle native coordinates...
		else super.project(position, projector);		
	}
	
	
	// Calculates the parallactic angle from the site and the horizontal coordinates...
	public void calcParallacticAngle() {
		setParallacticAngle(horizontal.getParallacticAngle(getScan().site));		
	}
	
	// Calculates the parallactic angle from the site and the equatorial coordinates...
	public void calcParallacticAngle(double LST) {
		setParallacticAngle(equatorial.getParallacticAngle(getScan().site, LST));		
	}
	
	public void setParallacticAngle(double angle) {
	    PA = new Angle(angle);
	}
	
	public Angle getParallacticAngle() {
	    return PA;
	}
	
	public GeodeticCoordinates getSite() { return getScan().site; }
 	
	public void calcHorizontal() {
		EquatorialCoordinates apparent = new EquatorialCoordinates();
		getApparentEquatorial(apparent);
		horizontal = apparent.toHorizontal(getSite(), LST);
	}
	
	// TODO use the mapping projection, instead of hardcoded SFL
	public void calcEquatorial() {
		// This assumes that the object is tracked on sky...
		// Uses the scanning offsets, on top of the tracking coordinate of the scan...
		if(scan.isTracking) {
			if(equatorial == null) equatorial = (EquatorialCoordinates) getScan().equatorial.clone();
			equatorial.setNativeLongitude(getScan().equatorial.x() + (PA.cos() * horizontalOffset.x() - PA.sin() * horizontalOffset.y()) / getScan().equatorial.cosLat());
			equatorial.setNativeLatitude(getScan().equatorial.y() + (PA.cos() * horizontalOffset.y() + PA.sin() * horizontalOffset.x()));	
		}
		// Otherwise do the proper conversion....
		else {
			equatorial = horizontal.toEquatorial(getScan().site, LST);
			getScan().fromApparent.precess(equatorial);
		}
	}
	
	@Override
	public void pointingAt(Vector2D center) {
		super.pointingAt(center);
		calcEquatorial();
	}
	
	public void setZenithTau(double value) {
		zenithTau = value;		
		setTransmission(Math.exp(-zenithTau/horizontal.sinLat()));
	}
	
	// Rotate by PA
	public final void horizontalToNativeEquatorial(Vector2D offset) {
		final double x = offset.x();
		offset.setX(PA.cos() * x - PA.sin() * offset.y());
		offset.setY(PA.sin() * x + PA.cos() * offset.y());
	}
	
	public final void horizontalToEquatorial(Vector2D offset) {
		horizontalToNativeEquatorial(offset);
		offset.scaleX(-1.0);
	}
	
	// Rotate by -PA
	public final void equatorialNativeToHorizontal(Vector2D offset) {
		final double x = offset.x();
		offset.setX(PA.cos() * x + PA.sin() * offset.y());
		offset.setY(PA.cos() * offset.y() - PA.sin() * x);
	}
	
	public final void equatorialToHorizontal(Vector2D offset) {
		offset.scaleX(-1.0);
		equatorialNativeToHorizontal(offset);
	}
	
	@Override
	public final void nativeToNativeEquatorial(Vector2D offset) {
		horizontalToNativeEquatorial(offset);
	}
	
	@Override
	public final void nativeEquatorialToNative(Vector2D offset) {
		equatorialNativeToHorizontal(offset);
	}
	
	@Override
	public final void nativeToEquatorial(SphericalCoordinates coords, EquatorialCoordinates equatorial) {
		((HorizontalCoordinates) coords).toEquatorial(equatorial, getScan().site, LST);	
	}
	
	@Override
	public final void equatorialToNative(EquatorialCoordinates equatorial, SphericalCoordinates coords) {
		equatorial.toHorizontal((HorizontalCoordinates) coords, getScan().site, LST);
	}
	
}
