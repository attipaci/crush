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

import util.Constant;
import util.SphericalCoordinates;

// This is an abstract class for coordinate systems that are fixed (except perhaps a precession)
// w.r.t the distant stars (quasars)...
public abstract class CelestialCoordinates extends SphericalCoordinates {

	public CelestialCoordinates() { super(); }
	
	public CelestialCoordinates(String text) { super(text); }
	
	public CelestialCoordinates(double lon, double lat) { super(lon, lat); }
	
	public CelestialCoordinates(CelestialCoordinates from) {
		convert(from, this);
	}
	
	public abstract EquatorialCoordinates getEquatorialPole();
	
	public abstract double getZeroLongitude();
	
	public EquatorialCoordinates toEquatorial() {
		EquatorialCoordinates equatorial = new EquatorialCoordinates();
		toEquatorial(equatorial);
		return equatorial;
	}
	
	public void toEquatorial(EquatorialCoordinates equatorial) {
		final EquatorialCoordinates pole = getEquatorialPole();
		
		SphericalCoordinates.inverseTransform(this, pole, getZeroLongitude(), equatorial);
		
		if(!equatorial.epoch.equals(pole.epoch)) {
			final CoordinateEpoch epoch = equatorial.epoch;
			equatorial.epoch = pole.epoch;
			equatorial.precess(epoch);			
		}
		
	}
	
	public synchronized void fromEquatorial(EquatorialCoordinates equatorial) {
		final EquatorialCoordinates pole = getEquatorialPole();
		
		if(!equatorial.epoch.equals(pole.epoch)) {
			equatorial = (EquatorialCoordinates) equatorial.clone();
			equatorial.precess(pole.epoch);			
		}
		
		SphericalCoordinates.transform(equatorial, pole, getZeroLongitude(), this);
	}
	
	public void convertFrom(CelestialCoordinates other) {
		convert(other, this);
	}
	
	public void convertTo(CelestialCoordinates other) {
		convert(this, other);
	}
	
	public void toEcliptic(EclipticCoordinates ecliptic) { convertTo(ecliptic); }
	
	public void toGalactic(GalacticCoordinates galactic) { convertTo(galactic); }
	
	public void toSuperGalactic(SuperGalacticCoordinates supergal) { convertTo(supergal); }
	
	public EclipticCoordinates toEcliptic() {
		EclipticCoordinates ecliptic = new EclipticCoordinates();
		convertTo(ecliptic);
		return ecliptic;
	}
	
	public GalacticCoordinates toGalactic() {
		GalacticCoordinates galactic = new GalacticCoordinates();
		convertTo(galactic);
		return galactic;
	}
		
	public SuperGalacticCoordinates toSuperGalactic() {
		SuperGalacticCoordinates supergal = new SuperGalacticCoordinates();
		convertTo(supergal);
		return supergal;
	}
	
	private static EquatorialCoordinates reuseEquatorial = new EquatorialCoordinates();
	public static synchronized void convert(CelestialCoordinates from, CelestialCoordinates to) {
		
		if(from.getClass().equals(to.getClass())) {
			if(from instanceof Precessing) {
				CoordinateEpoch toEpoch = ((Precessing) to).getEpoch();
				to.copy(from);
				((Precessing) to).precess(toEpoch);
			}
			else to.copy(from);
		}
		
		if(from instanceof EquatorialCoordinates) reuseEquatorial = (EquatorialCoordinates) from;
		else from.toEquatorial(reuseEquatorial);
		
		if(to instanceof EquatorialCoordinates) {
			if(!reuseEquatorial.epoch.equals(((EquatorialCoordinates) to).epoch)) reuseEquatorial.precess(((EquatorialCoordinates) to).epoch);
			to.copy(reuseEquatorial);
		}
		else to.fromEquatorial(reuseEquatorial);
	}
	
	
	public static EquatorialCoordinates getPole(double inclination, double risingRA) {
		return new EquatorialCoordinates(risingRA - Constant.rightAngle, Constant.rightAngle - inclination);
	}

	public static EquatorialCoordinates getPole(CelestialCoordinates referenceSystem, double inclination, double risingLON) {
		referenceSystem.set(risingLON - Constant.rightAngle, Constant.rightAngle - inclination);
		return referenceSystem.toEquatorial();
	}
	
	public static double getZeroLongitude(CelestialCoordinates from, CelestialCoordinates to) {
		EquatorialCoordinates equatorialZero = from.toEquatorial();
    	to.fromEquatorial(equatorialZero);
    	return to.nativeLongitude();		
	}

	
	
}
