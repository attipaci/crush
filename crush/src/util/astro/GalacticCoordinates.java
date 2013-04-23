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
import util.Unit;
import util.text.AngleFormat;

public class GalacticCoordinates extends CelestialCoordinates {
	
	static CoordinateAxis longitudeAxis, latitudeAxis, longitudeOffsetAxis, latitudeOffsetAxis;
	static CoordinateSystem defaultCoordinateSystem, defaultLocalCoordinateSystem;
	
	static {
		defaultCoordinateSystem = new CoordinateSystem("Galactic Coordinates");
		defaultLocalCoordinateSystem = new CoordinateSystem("Galactic Offsets");
		
		longitudeAxis = new CoordinateAxis("Galactic Longitude", "GLON");
		longitudeAxis.setReverse(true);
		latitudeAxis = new CoordinateAxis("Galactic Latitude", "GLAT");
		longitudeOffsetAxis = new CoordinateAxis("dGLON", longitudeAxis.wcsName);
		longitudeOffsetAxis.setReverse(true);
		latitudeOffsetAxis = new CoordinateAxis("dGLAT", latitudeAxis.wcsName);
		
		defaultCoordinateSystem.add(longitudeAxis);
		defaultCoordinateSystem.add(latitudeAxis);
		defaultLocalCoordinateSystem.add(longitudeOffsetAxis);
		defaultLocalCoordinateSystem.add(latitudeOffsetAxis);	
		
			AngleFormat af = new AngleFormat(3);
		
		for(CoordinateAxis axis : defaultCoordinateSystem) axis.setFormat(af);
	}
	
    public GalacticCoordinates() {}

    public GalacticCoordinates(String text) { super(text); }

    public GalacticCoordinates(double lat, double lon) { super(lat, lon); }
    
    public GalacticCoordinates(CelestialCoordinates from) { super(from); }
    
    @Override
	public void setDefaultCoordinates() {
    	setCoordinateSystem(GalacticCoordinates.defaultCoordinateSystem);
    	setLocalCoordinateSystem(GalacticCoordinates.defaultLocalCoordinateSystem); 
    }
    
    public static final EquatorialCoordinates equatorialPole = new EquatorialCoordinates(12.0 * Unit.hourAngle + 49.0 * Unit.minuteAngle, 27.4 * Unit.deg, "B1950.0");
    public static double phi0 = 123.0 * Unit.deg;

    // Change the pole and phi0 to J2000, s.t. conversion to J2000 is faster...
    static { 
    	GalacticCoordinates zero = new GalacticCoordinates(phi0, 0.0);
    	phi0 = 0.0;
    	EquatorialCoordinates equatorialZero = zero.toEquatorial();
    	equatorialZero.precess(CoordinateEpoch.J2000);
    	zero.fromEquatorial(equatorialZero);
    	phi0 = -zero.getX();
    	equatorialPole.precess(CoordinateEpoch.J2000);
    }
  
    
    @Override
	public EquatorialCoordinates getEquatorialPole() {
    	return equatorialPole;
	}

	@Override
	public double getZeroLongitude() {
		return phi0;
	}
	
	@Override
	public boolean isGalactic() { return true; }
}
