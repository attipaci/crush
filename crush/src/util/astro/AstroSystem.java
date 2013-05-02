/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package util.astro;

import util.SphericalCoordinates;

public class AstroSystem {
	private Class<? extends SphericalCoordinates> system;
	
	public AstroSystem(Class<? extends SphericalCoordinates> coordType) {
		this.system = coordType;
	}
	
	public boolean isHorizontal() { return HorizontalCoordinates.class.isAssignableFrom(system); }

	public boolean isEquatorial() { return EquatorialCoordinates.class.isAssignableFrom(system); }
	
	public boolean isEcliptic() { return EclipticCoordinates.class.isAssignableFrom(system); }

	public boolean isGalactic()  { return GalacticCoordinates.class.isAssignableFrom(system); }
	
	public boolean isSuperGalactic()  { return SuperGalacticCoordinates.class.isAssignableFrom(system); }
	
	public String getID() { return getID(system); }
	
	public SphericalCoordinates getCoordinateInstance() {
		if(system == null) return null;
		try { return system.newInstance(); } 
		catch(Exception e) { return null; }
	}
	
	public static String getID(Class<? extends SphericalCoordinates> coordType) {
		if(HorizontalCoordinates.class.isAssignableFrom(coordType)) return "HO";
		else if(EquatorialCoordinates.class.isAssignableFrom(coordType)) return "EQ";
		else if(EclipticCoordinates.class.isAssignableFrom(coordType)) return "EC";
		else if(GalacticCoordinates.class.isAssignableFrom(coordType)) return "GL";
		else if(SuperGalacticCoordinates.class.isAssignableFrom(coordType)) return "SG";
		else return "--";
	}
	
	public static String getID(AstroSystem coords) {
		if(coords.isHorizontal()) return "HO";
		if(coords.isEquatorial()) return "EQ";
		if(coords.isEcliptic()) return "EC";
		if(coords.isGalactic()) return "GL";
		if(coords.isSuperGalactic()) return "SG";
		return "--";
	}
	
	public static Class<? extends SphericalCoordinates> getCoordinateClass(String id) {
		id = id.toUpperCase();
		if(id.equals("ho")) return HorizontalCoordinates.class;
		if(id.equals("eq")) return EquatorialCoordinates.class;
		if(id.equals("ec")) return EclipticCoordinates.class;
		if(id.equals("gl")) return GalacticCoordinates.class;
		if(id.equals("sg")) return SuperGalacticCoordinates.class;
		return null;
	}
	
	public SphericalCoordinates getCoordinateInstance(String id) {
		Class<? extends SphericalCoordinates> coordType = getCoordinateClass(id);
		if(coordType == null) return null;
		try { return coordType.newInstance(); } 
		catch(Exception e) { return null; }
	}
	
	
}
