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

import java.text.*;

import util.Util;

public class JulianEpoch extends CoordinateEpoch {

	public JulianEpoch() {}

	public JulianEpoch(double epoch) { super(epoch); }
	
	protected JulianEpoch(double epoch, boolean immutable) { super(epoch, immutable); }

	@Override
	public double getJulianYear() { return getYear(); }

	@Override
	public double getBesselianYear() { return BesselianEpoch.getYearForMJD(getMJD()); }

	public BesselianEpoch getBesselianEpoch() { 
		return new BesselianEpoch(getBesselianYear());
	}

	@Override
	public void setMJD(double MJD) {
		setYear(getYearForMJD(MJD));
	}

	@Override
	public double getMJD() {
		return getMJDForYear(getYear());
	}
	
	//  J = 2000.0 + (MJD - 51544) / 365.25
	public static double getYearForMJD(double MJD) {
		return 2000.0 + (MJD - mjdJ2000) / julianYear;
	}

	public static double getMJDForYear(double year) {
		return (year - 2000.0) * julianYear + mjdJ2000;
	}

	public static JulianEpoch forMJD(double MJD) {
		return new JulianEpoch(getYearForMJD(MJD));		
	}

	@Override
	public String toString() { return toString(Util.f1); }
	
	public String toString(NumberFormat nf) {
		return "J" + nf.format(getYear());
	}
	
	public void parse(String text) throws NumberFormatException, IllegalArgumentException {
		if(text.charAt(0) == 'J') setYear(Double.parseDouble(text.substring(1)));
		else if(text.charAt(0) == 'B') setYear(getYearForMJD(BesselianEpoch.getMJDForYear(Double.parseDouble(text.substring(1)))));
		else setYear(Double.parseDouble(text));
	}	
	

}
