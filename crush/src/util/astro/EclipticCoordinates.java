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

package util.astro;


import java.text.DecimalFormat;
import java.text.NumberFormat;

import util.Constant;
import util.CoordinateAxis;
import util.CoordinatePair;
import util.CoordinateSystem;
import util.Unit;
import util.text.AngleFormat;
import nom.tam.fits.*;
import nom.tam.util.*;

public class EclipticCoordinates extends CelestialCoordinates implements Precessing {
	public CoordinateEpoch epoch;
	
	static CoordinateAxis longitudeAxis, latitudeAxis, longitudeOffsetAxis, latitudeOffsetAxis;
	static CoordinateSystem defaultCoordinateSystem, defaultLocalCoordinateSystem;
	
	static {
		defaultCoordinateSystem = new CoordinateSystem("Ecliptic Coordinates");
		defaultLocalCoordinateSystem = new CoordinateSystem("Ecliptic Offsets");
		
		longitudeAxis = new CoordinateAxis("Ecliptic Longitude", "ELON");
		longitudeAxis.setReverse(true);
		latitudeAxis = new CoordinateAxis("Ecliptic Latitude", "ELAT");
		longitudeOffsetAxis = new CoordinateAxis("dLON", longitudeAxis.wcsName);
		longitudeOffsetAxis.setReverse(true);
		latitudeOffsetAxis = new CoordinateAxis("dLAT", latitudeAxis.wcsName);
		
		defaultCoordinateSystem.add(longitudeAxis);
		defaultCoordinateSystem.add(latitudeAxis);
		defaultLocalCoordinateSystem.add(longitudeOffsetAxis);
		defaultLocalCoordinateSystem.add(latitudeOffsetAxis);
					
		AngleFormat af = new AngleFormat(3);
		
		for(CoordinateAxis axis : defaultCoordinateSystem) axis.setFormat(af);
	}

	
    public EclipticCoordinates() { epoch = CoordinateEpoch.J2000; }

    public EclipticCoordinates(String text) { super(text); }
    
	public EclipticCoordinates(double lon, double lat) { super(lon, lat); epoch = CoordinateEpoch.J2000; }

	public EclipticCoordinates(double lon, double lat, double aEpoch) { super(lon, lat); epoch = aEpoch < 1984.0 ? new BesselianEpoch(aEpoch) : new JulianEpoch(aEpoch); }

	public EclipticCoordinates(double lon, double lat, String epochSpec) { super(lon, lat); epoch = CoordinateEpoch.forString(epochSpec); }
     
	public EclipticCoordinates(CelestialCoordinates from) { super(from); }
	
	public CoordinatePair copy() {
		EclipticCoordinates copy = (EclipticCoordinates) clone();
		copy.epoch = (CoordinateEpoch) epoch.clone();
		return copy;
	}
	
    @Override
	public void setDefaultCoordinates() {
    	setCoordinateSystem(EclipticCoordinates.defaultCoordinateSystem);
    	setLocalCoordinateSystem(EclipticCoordinates.defaultLocalCoordinateSystem);
    }
    
    @Override
	public void copy(CoordinatePair coords) {
		super.copy(coords);
		if(coords instanceof EclipticCoordinates) {
			EclipticCoordinates equatorial = (EclipticCoordinates) coords;
			epoch = (CoordinateEpoch) equatorial.epoch.clone();	
		}
		else epoch = null;
	}
    
    @Override
    public void toEquatorial(EquatorialCoordinates equatorial) {
    	super.toEquatorial(equatorial);
    }
    
    @Override
    public void fromEquatorial(EquatorialCoordinates equatorial) {
    	super.fromEquatorial(equatorial);
    	epoch = equatorial.epoch;
    }
    
    
    @Override
	public String toString() {
		return super.toString() + " (" + (epoch == null ? "unknown" : epoch.toString()) + ")";	
	}
	
	@Override
	public String toString(NumberFormat nf) {
		return super.toString(nf) + " (" + (epoch == null ? "unknown" : epoch.toString()) + ")";	
	}
	
	@Override
	public String toString(DecimalFormat df) {
		return super.toString(df) + " (" + (epoch == null ? "unknown" : epoch.toString()) + ")";	
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
	
    
    public final static double inclination = 23.0 * Unit.deg + 26.0 * Unit.arcmin + 30.0 * Unit.arcsec; // to equatorial    
    public final static EquatorialCoordinates equatorialPole = CelestialCoordinates.getPole(inclination, 0.0);
    
	@Override
	public EquatorialCoordinates getEquatorialPole() { return equatorialPole; }

	@Override
	public double getZeroLongitude() { return Constant.rightAngle; }

	public CoordinateEpoch getEpoch() { return epoch; }

	public void setEpoch(CoordinateEpoch epoch) { this.epoch = epoch; }
	
	public void precess(CoordinateEpoch toEpoch) {
		if(epoch.equals(toEpoch)) return;
		
		EquatorialCoordinates equatorial = toEquatorial();
		equatorial.precess(toEpoch);
		fromEquatorial(equatorial);
	}
	
    
}
