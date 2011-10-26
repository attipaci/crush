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


import java.util.*;

import util.astro.Gnomonic;
import util.astro.HammerAitoff;
import util.astro.Mercator;
import util.astro.PlateCarree;
import util.astro.RadioProjection;
import util.astro.SansonFlamsteed;
import util.astro.SlantOrthographic;
import util.astro.ZenithalEqualArea;

import nom.tam.fits.*;
import nom.tam.util.*;


// TODO Read fits Headers (for extra information on projection parameters)...
// TODO Implement a few more projections...

// Based on Calabretta & Greisen 2002
public abstract class SphericalProjection extends Projection2D<SphericalCoordinates> {
	// the reference in celestial (alpha0, delta0)
	protected SphericalCoordinates referenceNative; // the reference in native (phi0, theta0)
	
	protected SphericalCoordinates pole; // the pole in celestial (alphap, deltap)
	protected SphericalCoordinates poleNative; // the pole in native (phip, thetap)
	
	private boolean userPole = false; // True if not using the default pole.
	private boolean userReference = false; // True if not using the default native reference.
	
	public SphericalProjection() {
		referenceNative = new SphericalCoordinates(0.0, 0.0); // phi0, theta0;
		poleNative = new SphericalCoordinates(0.0, 0.0); // phip, thetap;
	} 
	
	@Override
	public boolean equals(Object o) {
		if(!o.getClass().equals(getClass())) return false;
		SphericalProjection projection = (SphericalProjection) o;
		
		if(projection.userPole != userPole) return false;
		if(!projection.referenceNative.equals(referenceNative)) return false;
		if(!projection.poleNative.equals(poleNative)) return false;
		return super.equals(o);		
	}
	
	@Override
	public int hashCode() {
		int hash = super.hashCode();
		if(reference != null) hash ^= reference.hashCode();
		if(referenceNative != null) hash ^= referenceNative.hashCode();
		if(!isRightAnglePole()) if(pole != null) hash ^= pole.hashCode();
		if(poleNative != null) hash ^= poleNative.hashCode();
		return hash;
	}
	
	@Override
	public Projection2D<SphericalCoordinates> copy() {
		SphericalProjection copy = (SphericalProjection) clone();
		if(pole != null) copy.pole = (SphericalCoordinates) pole.clone();
		if(referenceNative != null) copy.referenceNative = (SphericalCoordinates) referenceNative.clone();
		if(poleNative != null) copy.poleNative = (SphericalCoordinates) poleNative.clone();
		return copy;
	}
	
	public boolean isRightAnglePole() {
		return SphericalCoordinates.equalAngles(Math.abs(pole.y), rightAngle);
	}
	
	// Global projection
	@Override
	public void project(final SphericalCoordinates coords, final CoordinatePair toProjected) {		
		final double dLON = coords.x - pole.x;
		double phi = Double.NaN, theta = Double.NaN;
		
		if(isRightAnglePole()) {
			if(pole.y > 0.0) {
				phi = poleNative.x + dLON + Math.PI;
				theta = coords.y;
			}
			else {
				phi = poleNative.x - dLON;
				theta = -coords.y;
			}	
		}
		else {
			final double cosdLON = Math.cos(dLON);
			
			phi = poleNative.x + Math.atan2(
					-coords.cosLat * Math.sin(dLON),
					coords.sinLat * pole.cosLat - coords.cosLat * pole.sinLat * cosdLON);
			
			theta = asin(coords.sinLat * pole.sinLat + coords.cosLat * pole.cosLat * cosdLON);
		}
	
		phi = Math.IEEEremainder(phi, twoPI);
		
		//System.err.println(Util.f2.format(phi/Unit.deg) + ", " + Util.f2.format(theta/Unit.deg));
		
		getOffsets(theta, phi, toProjected);
	}
	
	// Global deprojection
	@Override
	public void deproject(final CoordinatePair projected, final SphericalCoordinates toCoords) {
		final double theta = theta(projected);
		final double phi = phi(projected);
		final double dPhi = phi - poleNative.x;
		
		if(isRightAnglePole()) {
			if(pole.y > 0.0) {
				toCoords.x = pole.x + dPhi - Math.PI;
				toCoords.y = theta;
			}
			else {
				toCoords.x = pole.x - dPhi;
				toCoords.y = -theta;
			}	
		}
		else {
			final double cosTheta = Math.cos(theta);
			final double sinTheta = Math.sin(theta);
			final double cosPhi = Math.cos(dPhi);
					
			toCoords.setNativeLongitude(pole.x + Math.atan2(
					-cosTheta * Math.sin(dPhi),
					sinTheta * pole.cosLat - cosTheta * pole.sinLat * cosPhi));	
			
			toCoords.setNativeLatitude(asin(sinTheta * pole.sinLat + cosTheta * pole.cosLat * cosPhi));
		}
		
		toCoords.standardize();
	}

	
	public abstract double phi(CoordinatePair offset);
	
	public abstract double theta(CoordinatePair offset);
	
	public abstract void getOffsets(double theta, double phi, CoordinatePair toOffset);
	
	@Override
	public void setReference(SphericalCoordinates coordinates) {
		setReference(coordinates, referenceNative);
	}
	
	public void setReference(SphericalCoordinates celestialCoords, SphericalCoordinates nativeCoords) throws IllegalArgumentException { 
		super.setReference(celestialCoords);
		referenceNative = nativeCoords; 
		
		if(!userPole) poleNative.setNativeLongitude(reference.y >= referenceNative.y ? 0 : Math.PI); 
			
		calcCelestialPole();	
	}
	
	public void calcCelestialPole() {
		pole = new SphericalCoordinates();	
		
		double sindPhi = Math.sin(poleNative.x - referenceNative.x);
		double cosdPhi = Math.cos(poleNative.x - referenceNative.x);
		
		double deltap = Math.atan2(referenceNative.sinLat, referenceNative.cosLat * cosdPhi);
		double cs = referenceNative.cosLat * sindPhi;
		
		double deltab = acos(reference.sinLat / Math.sqrt(1.0 - cs*cs));
		
		double delta1 = deltap + deltab;
		double delta2 = deltap - deltab;
		
		// make delta2 > delta1
		if(delta1 > delta2) {
			double temp = delta2;
			delta2 = delta1;
			delta1 = temp;
		}
		
		int solutions = 0;
		// Or, the pole nearest to the native latitude specification...
		// (northern by default).
		if(Math.abs(delta1) <= rightAngle) { pole.setNativeLatitude(delta1); solutions++; }
		if(Math.abs(delta2) <= rightAngle) {
			// If two solutions exists, chose the one closer to the native pole...
			if(solutions == 0 || Math.abs(delta2 - poleNative.y) < Math.abs(delta1 - poleNative.y)) pole.setNativeLatitude(delta2); 
			solutions++;
		}
		if(solutions == 0) throw new IllegalArgumentException("No solutions for celestial pole.");
		
		
		//System.err.println(solutions + " solution(s)");
		
		if(SphericalCoordinates.equalAngles(Math.abs(reference.y), rightAngle)) pole.x = reference.x;
		else if(SphericalCoordinates.equalAngles(Math.abs(pole.y), rightAngle)) {
			pole.setNativeLongitude(reference.x + (pole.y > 0 ? 
						poleNative.x - referenceNative.x - Math.PI :
						referenceNative.x - poleNative.x));
		}
		else {
			double sindLON = sindPhi * referenceNative.cosLat / reference.cosLat;
			double cosdLON = (referenceNative.sinLat - pole.sinLat * reference.sinLat) / (pole.cosLat * reference.cosLat);
			pole.setNativeLongitude(reference.x - Math.atan2(sindLON, cosdLON));
		}
		
		pole.standardize();
	}
	
	
	public void setNativePole(SphericalCoordinates nativeCoords) {
		userPole = true;
		poleNative = nativeCoords;
	}
	
	public SphericalCoordinates getNativePole() { return poleNative; }
	
	public SphericalCoordinates getCelestialPole() { return pole; }
	
	public void setDefaultPole() {
		userPole = false;
		poleNative.zero();
		setReference(getReference());
	}
	

	
	
	@Override
	public void edit(Cursor cursor, String alt) throws HeaderCardException {		
		
		for(int i=0; i<reference.coordinateSystem.size(); i++) {
			CoordinateAxis axis = reference.coordinateSystem.get(i);	
			cursor.add(new HeaderCard("CTYPE" + (i+1) + alt, axis.wcsName + "-" + getFitsID(), axis.label + " in " + getFullName() + " projection."));
		}
		
		if(userPole) {
			cursor.add(new HeaderCard("LONPOLE" + alt, poleNative.x / Unit.deg, "The longitude (deg) of the native pole."));
			cursor.add(new HeaderCard("LATPOLE" + alt, poleNative.y / Unit.deg, "The latitude (deg) of the native pole."));
		}
		if(userReference) {
			cursor.add(new HeaderCard("PV1_1" + alt, referenceNative.x / Unit.deg, "The longitude (deg) of the native reference."));
			cursor.add(new HeaderCard("PV1_2" + alt, referenceNative.y / Unit.deg, "The latitude (deg) of the native reference."));			
			// TODO should calculate and write PV0_j offsets
		}	
	}
	
	@Override
	public void parse(Header header, String alt) {
	
		if(header.containsKey("PV1_3" + alt)) {
			userPole = true;
			poleNative.setLongitude(header.getDoubleValue("PV1_3" + alt) * Unit.deg);
		}
		else if(header.containsKey("LONPOLE" + alt)) {
			userPole = true;
			poleNative.setLongitude(header.getDoubleValue("LONPOLE" + alt) * Unit.deg);
		}
		
		if(header.containsKey("PV1_4" + alt)) {
			userPole = true;
			poleNative.setLatitude(header.getDoubleValue("PV1_4" + alt) * Unit.deg);
		}
		else if(header.containsKey("LATPOLE" + alt)) {
			userPole = true;
			poleNative.setLatitude(header.getDoubleValue("LATPOLE" + alt) * Unit.deg);
		}
		
		if(header.containsKey("PV1_1" + alt)) {
			userReference = true;
			referenceNative.setLongitude(header.getDoubleValue("PV1_1" + alt) * Unit.deg);
		}
		if(header.containsKey("PV1_2" + alt)) {
			userReference = true;
			referenceNative.setLatitude(header.getDoubleValue("PV1_2" + alt) * Unit.deg);
		}
		// TODO reference offset PV0_j should be used also...		
	}
	
	@Override
	public SphericalCoordinates getCoordinateInstance() {
		return new SphericalCoordinates();
	}
	
	// Safe asin and acos for when rounding errors make values fall outside of -1:1 range.
	protected final static double asin(double value) {
		if(value < -1.0) value = -1.0;
		else if(value > 1.0) value = 1.0;
		return Math.asin(value);
	}
	
	protected final static double acos(double value) {
		if(value < -1.0) value = -1.0;
		else if(value > 1.0) value = 1.0;
		return Math.acos(value);
	}
	
	static Hashtable<String, SphericalProjection> registry;
	
	static {
		registry = new Hashtable<String, SphericalProjection>();
		register(new SlantOrthographic()); // SIN
		register(new Gnomonic()); // TAN
		register(new ZenithalEqualArea()); // ZEA
		register(new SansonFlamsteed()); // SFL
		register(new Mercator()); // MER
		register(new PlateCarree()); // CAR
		register(new HammerAitoff()); // AIT
		register(new RadioProjection()); // GLS
		//register(new AIPSLegacyProjection(new SansonFlamsteed(), "Radio", "GLS"));
	}
	
	public static void register(SphericalProjection projection) {
		registry.put(projection.getFitsID(), projection);
	}
	
	// Find projection by FITS name, full name, or class name...
	public static SphericalProjection forName(String name) throws InstantiationException, IllegalAccessException {
		SphericalProjection projection = registry.get(name);
		if(projection != null) return projection.getClass().newInstance();
		
		Collection<SphericalProjection> projections = registry.values();
		
		for(SphericalProjection p : projections) {
			if(p.getFitsID().equals("name")) return p.getClass().newInstance();
			else if(p.getFullName().equalsIgnoreCase("name")) return p.getClass().newInstance();
			else if(p.getClass().getSimpleName().equals("name")) return p.getClass().newInstance();
		}
		throw new InstantiationException("No projection " + name + " in registry.");
	}


	public final static double twoPI = 2.0 * Math.PI;
	public final static double rightAngle = 0.5 * Math.PI;

}
