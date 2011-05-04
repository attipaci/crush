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


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;

import util.Unit;
import util.text.TimeFormat;

//  BUG Fixes 4 Nov 2009

//	2451545.0 JD = 1 January 2000, 11:58:55.816 UT, or 11:59:27.816 TAI
//  UTC routines are approximate but consistent (btw. getUTC() and setUTC(), and currentTime())
//  only UTC <===> (MJD, TT) conversion is approximate...
//  Use (quadratic) fit to leap? This should give some accuracy for UTC...
public class AstroTime {
	double MJD = Double.NaN; // Assuming that MJD goes with TT
	
	public AstroTime() {}
	
	public AstroTime(long millis) { setMillis(millis); }
	
	public AstroTime now() {
		setMillis(System.currentTimeMillis()); 
		return this;
	}
	
	public void setMJD(double date) { MJD = date; }
	
	public void setJD(double JD) { setMJD(JD - 2400000.5); }
	
	// Assuming UNIX clock measures TT...
	// TODO With UTC it needs adjustment for LeapSeconds between 2000 and the time...
	// If implemented take leap correction from currentTime...
	public void setMillis(long millis) {
		MJD = getMJD(millis);
	}
	
	public static double getMJD(long millis) {
		return mjdJ2000 + (double)(millis - millisJ2000) / dayMillis;
	}
	
	public void setLeap(double dt) { leap = dt; }
	
	public void setTime(Date date) { setMillis(date.getTime()); }
	
	// Terrestrial Time (based on Atomic Time TAI)
	public void setTT(double TT) { MJD = Math.floor(MJD) + TT / Unit.day; }
	
	public void setUTC(double UTC) { setTAI(UTC + leap); }

	public void setTAI(double TAI) { setTT(TAI + TAI2TT); }
	
	public void setGPSTime(double GPST) { setTAI(GPST + GPS2TAI); }
	
	public void setTCG(double TCG) {
		setTT(TCG - 6.969290134e-10 * (MJD - 43144.5003725) * Unit.day);
	}
	
	
	public void setTimeFromJ2000(double time) { MJD = mjdJ2000 + time/Unit.day; }

	
	
	public double getMJD() { return MJD; }
	
	public double getJD() { return 2400000.5 + MJD; }
	
	public long getMillis() { return millisJ2000 + (long)((MJD - mjdJ2000) * dayMillis); }

	public Date getDate() { return new Date(getMillis()); }
	
	// Terrestrial Time (based on Atomic Time TAI)
	public double getTT() {
		return (MJD - (int)Math.floor(MJD)) * Unit.day;
	}
	
	// TODO use DUT1?
	public double getUTC() {
		return getTAI() - leap;
		// TODO correct by actual leaps...
	}
	
	public double getTAI() { return getTT() - TAI2TT; }
	
	public double getGPSTime() { return getTAI() - GPS2TAI; }

	
	// TCG is based on the Atomic Time but corrects for the gravitational dilation on Earth
	// Thus it is a good measure of time in space.
	// TT = TCG − LG × (JDTCG − 2443144.5003725) × 86400
	// LG = 6.969290134e-10
	public double getTCG() {
		return getTT() + 6.969290134e-10 * (MJD - 43144.5003725) * Unit.day;
	}
	
	public double getTimeFromJ2000() {
		return (MJD - mjdJ2000) * Unit.day;
	}
	
	// Mean Fictive Equatorial Sun's RA in time units (use for calculationg LST)
    public double getMeanFictiveEquatorialSunTime() {
    	double Tu = (MJD - mjdJ2000) / julianCenturyDays;
	
    	double alphaU = 24110.54841 + 8640184.812866 * Tu + 0.093104 * Tu * Tu;
    	alphaU *= Unit.sec ;
    	return alphaU;
    }
	
	// Ratio of mLST to UT1 = 0.997269566329084 − 5.8684×10−11T + 5.9×10−15T², 
	// where T is the number of Julian centuries of 36525 days each that have elapsed since JD 2451545.0 (J2000).[1]
    // Greenwich Sidereal Time
	public double getGST() {
		return getUTC() + getMeanFictiveEquatorialSunTime();
	}
	
	public double getLST(double longitude) {
		return getGST() + longitude / Unit.timeAngle;
	}
	
	public BesselianEpoch getBesselianEpoch() { 
		BesselianEpoch epoch = new BesselianEpoch();
		epoch.setMJD(MJD);
		return epoch;
	}

	public JulianEpoch getJulianEpoch() { 
		JulianEpoch epoch = new JulianEpoch();
		epoch.setMJD(MJD);
		return epoch;
	}
	
	
	public void parseISOTimeStamp(String text) throws ParseException {
		setMillis(isoFormatter.parse(text).getTime());
	}
	
	public String getISOTimeStamp() {
		return isoFormatter.format(getDate());
	}
	
	// Proper parsing of UT (rather than UTC)...
	public void parseFitsTimeStamp(String text) throws ParseException {
		// Set the nearest MJD day to the given UTC day...
		setMillis(fitsDateFormatter.parse(text.substring(0,10)).getTime()); 
		MJD = Math.round(MJD);
		
		// Add in the UT time component...
		if(text.length() > 11) {
			double UT = 0.0;
			StringTokenizer tokens = new StringTokenizer(text.substring(11), ":");
			if(tokens.hasMoreTokens()) UT += Integer.parseInt(tokens.nextToken()) * Unit.hour;
			if(tokens.hasMoreTokens()) UT += Integer.parseInt(tokens.nextToken()) * Unit.min;
			if(tokens.hasMoreTokens()) UT += Double.parseDouble(tokens.nextToken()) * Unit.s;
			MJD += UT / Unit.day;			
		}
	
	}
	
	public String getFitsTimeStamp() {
		long millis = getMillis();
		long remainder = (millis - millisJ2000) % dayMillis;
		
		// The UT day around noon (to very safely avoid rounding errors
		// plus the UT time from the fractional part...
		return fitsDateFormatter.format(millis - remainder + dayMillis/2) + 
			'T' + fitsTimeFormat.format((MJD - Math.floor(MJD)) * Unit.day);
	}
	
	public void parseSimpleDate(String text) throws ParseException {
		setMillis(defaultFormatter.parse(text).getTime());
	}
	
	public String getSimpleDate() {
		return defaultFormatter.format(getDate());
	}
	
	public static AstroTime forISOTimeStamp(String text) throws ParseException {
		AstroTime time = new AstroTime();
		time.parseISOTimeStamp(text);
		return time;
	}
	
	public static AstroTime forFitsTimeStamp(String text) throws ParseException {
		AstroTime time = new AstroTime();
		
		time.parseFitsTimeStamp(text.substring(0, 10));
		time.MJD = Math.round(time.MJD);
		if(text.length() > 11) {
			double UT = 0.0;
			StringTokenizer tokens = new StringTokenizer(text.substring(11), ":");
			if(tokens.hasMoreTokens()) UT += Integer.parseInt(tokens.nextToken()) * Unit.hour;
			if(tokens.hasMoreTokens()) UT += Integer.parseInt(tokens.nextToken()) * Unit.min;
			if(tokens.hasMoreTokens()) UT += Double.parseDouble(tokens.nextToken()) * Unit.s;
			time.MJD += UT / Unit.day;			
		}
			
		return time;
	}
	
	public static AstroTime forSimpleDate(String text) throws ParseException {
		AstroTime time = new AstroTime();
		time.parseSimpleDate(text);
		return time;
	}
	
	public static double timeOfDay(double time) {
		return time - Unit.day * Math.floor(time / Unit.day);
	}
	
	// millis of 2000 UT - leap2000 - tai2tt -> 2000 TT
	public final static long millisJ2000 = 946684800000L - 32000L - 32184L; // millis at TT 2000
	protected final static double mjdJ2000 = 51544.0;
	protected final static long dayMillis = 86400L * 1000L;
	protected final static double julianCenturyMillis = Unit.julianCentury / Unit.s * 1000.0;
	protected final static double julianCenturyDays = 36525.0;
	protected final static double leap2000 = 32.0 * Unit.sec;
	protected static double leap = 34.0 * Unit.sec;
	
	protected static double TAI2TT = 32.184 * Unit.s;
	protected static double GPS2TAI = -19.0 * Unit.s;
	
	public static TimeZone UTC = TimeZone.getTimeZone("UTC");
	
	private static DateFormat isoFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	private static DateFormat fitsDateFormatter = new SimpleDateFormat("yyyy-MM-dd");
	private static DateFormat defaultFormatter = new SimpleDateFormat("yyyy.MM.dd");
	private static TimeFormat fitsTimeFormat = new TimeFormat(3); 
	
	static {
		fitsDateFormatter.setTimeZone(UTC);
		defaultFormatter.setTimeZone(UTC);
		fitsTimeFormat.colons();
	}
	
}
