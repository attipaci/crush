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


public class SuperGalacticCoordinates extends CelestialCoordinates {
	
	static CoordinateAxis longitudeAxis, latitudeAxis, longitudeOffsetAxis, latitudeOffsetAxis;
	static CoordinateSystem defaultCoordinateSystem, defaultLocalCoordinateSystem;
	
	static {
		defaultCoordinateSystem = new CoordinateSystem("Super-Galactic Coordinates");
		defaultLocalCoordinateSystem = new CoordinateSystem("Super-Galactic Offsets");
		
		longitudeAxis = new CoordinateAxis("Super-Galactic Longitude", "SLON");
		longitudeAxis.setReverse(true);
		latitudeAxis = new CoordinateAxis("Super-Galactic Latitude", "SLAT");
		longitudeOffsetAxis = new CoordinateAxis("dSLON", longitudeAxis.wcsName);
		longitudeOffsetAxis.setReverse(true);
		latitudeOffsetAxis = new CoordinateAxis("dSLAT", latitudeAxis.wcsName);
		
		defaultCoordinateSystem.add(longitudeAxis);
		defaultCoordinateSystem.add(latitudeAxis);
		defaultLocalCoordinateSystem.add(longitudeOffsetAxis);
		defaultLocalCoordinateSystem.add(latitudeOffsetAxis);	
		
		AngleFormat af = new AngleFormat(3);
		
		for(CoordinateAxis axis : defaultCoordinateSystem) axis.setFormat(af);
			
	}
	
    public SuperGalacticCoordinates() {}

    public SuperGalacticCoordinates(String text) { super(text); }

    public SuperGalacticCoordinates(double lat, double lon) { super(lat, lon); }
    
    public SuperGalacticCoordinates(CelestialCoordinates from) { super(from); }
    
    @Override
	public void setDefaultCoordinates() {
    	setCoordinateSystem(SuperGalacticCoordinates.defaultCoordinateSystem);
    	setLocalCoordinateSystem(SuperGalacticCoordinates.defaultLocalCoordinateSystem);  
    }
    
    public final static GalacticCoordinates galacticPole = new GalacticCoordinates(47.37*Unit.deg, 6.32*Unit.deg);
    public final static GalacticCoordinates galacticZero = new GalacticCoordinates(137.37*Unit.deg, 0.0);
    public final static EquatorialCoordinates equatorialPole = galacticPole.toEquatorial(); 
    public static double phi0 = CelestialCoordinates.getZeroLongitude(galacticZero, new SuperGalacticCoordinates());
    
    @Override
	public EquatorialCoordinates getEquatorialPole() {
		return equatorialPole;
	}

	@Override
	public double getZeroLongitude() {
		return phi0;
	}

}
