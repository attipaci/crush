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

import java.text.*;

import util.Util;

public class BesselianEpoch extends CoordinateEpoch {	
	
	public BesselianEpoch() { }

	public BesselianEpoch(double epoch) { super(epoch); }
	
	protected BesselianEpoch(double epoch, boolean immutable) { super(epoch, immutable); }

	@Override
	public double getBesselianYear() { return getYear(); }

	@Override
	public double getJulianYear() { return JulianEpoch.getYearForMJD(getMJD()); }

	public JulianEpoch getJulianEpoch() { 
		return new JulianEpoch(getBesselianYear());
	}
	
	//  B = 1900.0 + (JD - 2415020.31352) / 365.242198781
	@Override
	public void setMJD(double MJD) {
		setYear(getYearForMJD(MJD));
	}

	@Override
	public double getMJD() {
		return getMJDForYear(getYear());
	}
	
	public static double getYearForMJD(double MJD) {
		return 1900.0 + (MJD - mjdB1900) / julianYear;
	}
	
	public static double getMJDForYear(double year) {
		return (year - 1900.0) * julianYear + mjdB1900;
	}

	public static BesselianEpoch forMJD(double MJD) {
		return new BesselianEpoch(getYearForMJD(MJD));		
	}

	@Override
	public String toString() { return toString(Util.f1); }
	
	public String toString(NumberFormat nf) {
		return "B" + nf.format(getYear());
	}

	public void parse(String text) throws NumberFormatException, IllegalArgumentException {
		if(text.charAt(0) == 'B') setYear(Double.parseDouble(text.substring(1)));
		else if(text.charAt(0) == 'J') setYear(getYearForMJD(JulianEpoch.getMJDForYear(Double.parseDouble(text.substring(1)))));
		else setYear(Double.parseDouble(text));
	}	
	
}
