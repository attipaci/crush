/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
// Copyright (c) 2007 Attila Kovacs 

package kovacs.util.astro;

import nom.tam.fits.*;
import nom.tam.util.*;

public abstract class CoordinateEpoch implements Cloneable, Comparable<CoordinateEpoch> {
	private double year;
	private boolean immutable = false;

	public CoordinateEpoch() {}

	public CoordinateEpoch(double epoch) { year = epoch; }
	
	protected CoordinateEpoch(double epoch, boolean immutable) { this(epoch); this.immutable = immutable; }

	// The clone is always mutable...
	@Override
	public Object clone() {
		try { 
			CoordinateEpoch clone = (CoordinateEpoch) super.clone(); 
			clone.immutable = false;
			return clone;
		}
		catch(CloneNotSupportedException e) { return null; }		
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof CoordinateEpoch)) return false;
		return compareTo((CoordinateEpoch) o) == 0;
	}
	
	@Override
	public int hashCode() {
		return (int) (year/precision);
	}
	
	public int compareTo(CoordinateEpoch epoch) {
		double y1 = getJulianYear();
		double y2 = epoch.getJulianYear();
		if(Math.abs(y1 - y2) < precision) return 0;
		else return y1 < y2 ? -1 : 1;
	}
	
	public void setImmutable(boolean value) {
		immutable = value;
	}
	
	public boolean isImmutable() { return immutable; }
	
	public double getYear() { return year; }
	
	protected void setYear(double year) {
		if(immutable) throw new UnsupportedOperationException("Cannot alter immutable coordinate epoch.");
		this.year = year;
	}
	
	public abstract double getJulianYear();

	public abstract double getBesselianYear();
	
	public abstract void setMJD(double MJD);
	
	public abstract double getMJD();
	
	public void forJulianDate(double JD) { setMJD(JD - 2400000.5); }
	
	public double getJulianDate() { return getMJD() + 2400000.5; }
	
	
	public void edit(Cursor cursor) throws HeaderCardException { edit(cursor, ""); }
	
	public void edit(Cursor cursor, String alt) throws HeaderCardException {
		cursor.add(new HeaderCard("EQUINOX" + alt, year, "The epoch of the quoted coordinates"));
	}
	
	public void parse(Header header) { parse(header, ""); }

	public void parse(Header header, String alt) {
		year = header.getDoubleValue("EQUINOX" + alt, this instanceof BesselianEpoch ? 1950.0 : 2000.0);
	}
	

	public static CoordinateEpoch forString(String text) throws NumberFormatException {
		if(text.charAt(0) == 'B') return new BesselianEpoch(Double.parseDouble(text.substring(1)));
		else if(text.charAt(0) == 'J') return new JulianEpoch(Double.parseDouble(text.substring(1)));
		else {
			double year = Double.parseDouble(text);
			if(year < 1984.0) return new BesselianEpoch(year);
			else return new JulianEpoch(year);
		}
	}
	
	protected final static double besselianYear = 365.242198781;
	protected final static double julianYear = 365.25;
	protected final static double mjdB1900 = 15019.81352; // JD 2415020.31352
	protected final static double mjdB1950 = 33281.92345905; // JD 2433282.42345905
	protected final static double mjdJ1900 = 15020.5; // JD 2415021.0 
	protected final static double mjdJ2000 = 51544.5; // JD 2551545.0
	//  2451545.0 JD = 1 January 2000, 11:58:55.816 UT, or 11:59:27.816 TAI
	
	public final static BesselianEpoch B1900 = new BesselianEpoch(1900.0, true);
	public final static BesselianEpoch B1950 = new BesselianEpoch(1950.0, true);
	public final static JulianEpoch J2000 = new JulianEpoch(2000.0, true);
	
	// The precision to which to epochs must match to be considered equal...
	public final static double precision = 1e-3; // in years...
}
