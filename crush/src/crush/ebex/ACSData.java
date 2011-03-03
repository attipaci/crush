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
// Copyright (c) 2010 Attila Kovacs 

package crush.ebex;

import util.*;
import util.dirfile.*;

import java.io.IOException;

// ACS sampling rate is constant and continuous. So for ACS, it should suffice to get
// starting and ending index/timestamp values, and calculate indeces for timestamps 
// from these...

// Scans by index range or tmaj,tsss or date+ut range

public class ACSData extends DirFile {
	/**
	 * 
	 */
	private static final long serialVersionUID = 742804863836922299L;

	EBEXTimeStamp<Double> ebexTime;
	EBEXTimeStamp<Long> gpsTime;
	
	DataStore<?> AZ, EL, RA, DEC, LAT, LON, LST, roll;

	// SIP_ALT, SIP_LAT, SIP_LON
	// DGPS_AZ, ALT, DGPS_LAT, DGPS_AZ, DGPS_LON, DGPS_ALT, DGPS_AZ_RAW, DGPS_TRIM,
	// SS_AZ
	// DGPS_TIME, LST, ISC_RA, ISC_DEC, LAT, LON, RA, DEC, DGPS_PITCH, DGPS_ROLL
	// ENC_EL, PWM_EL
	// AZ, EL
	// ISC_FIELDROT,
	
	// Detector temperature
	// GRT 1 channel 1 - 250 GHz, 2 - 150GHz, 3 - 410 GHz (8,7 center)
	// dark bolometers: 
	// resistor, dark (taped), eccosorb (~10^3 attenuation).
	
	// Run 6
	// NAF-bolo-hardwaremap.xls
	
	
	// mag_az_new
	// radec_new
	
	long startTSIndex; // refers to starting index of EBEX timestamps...
	double startTS, samplingTSRate;
	
	public ACSData(String path) throws IOException {
		super(path);
		ebexTime = new EBEXTimeStamp<Double>((DataStore<Double>) get("ACS_TIME"));
		gpsTime = new EBEXTimeStamp<Long>((DataStore<Long>) get("dgps_time"));

		AZ = get("AZ");
		EL = get("EL");
		RA = get("RA");
		DEC = get("DEC");
		LAT = get("LAT");
		LON = get("LON");
		LST = get("LST");
		roll = get("DGPS_ROLL");
	}
	
	// using EBEX timestamps...
	public void setEBEXTimeRange(double fromEBEXTime, double toEBEXTime) throws IOException {
		startTSIndex = ebexTime.getLowerIndex(fromEBEXTime);
		startTS = ebexTime.get(startTSIndex);
		
		long endTSIndex = ebexTime.getLowerIndex(toEBEXTime) + 1L;
		double endTS = ebexTime.get(endTSIndex);
		
		samplingTSRate = (endTSIndex - startTSIndex) / (endTS - startTS);
		
		printSampling();
	}
	
	// Using dirfile indeces
	public void setIndexRange(long from, long to) throws IOException {
		// Convert indeces to timestamp indeces...
		from *= ebexTime.getSamples();
		to *= ebexTime.getSamples();
		
		startTSIndex = from;
		startTS = ebexTime.get(from);
		double endTS = ebexTime.get(to);
		
		samplingTSRate = (to - from) / (endTS - startTS);
		
		printSampling();
	}
	
	// Using UNIX timestamps
	public void setTimeRange(long from, long to) throws IOException {
		double relSampling = ebexTime.getSamples() / gpsTime.getSamples();
		
		startTSIndex = (long) Math.floor(relSampling * gpsTime.getLowerIndex(from));
		startTS = ebexTime.get(startTSIndex);
		
		long endTSIndex = (long) Math.floor(relSampling * gpsTime.getLowerIndex(to)) + 1L;
		double endTS = ebexTime.get(endTSIndex);
		
		samplingTSRate = (endTSIndex - startTSIndex) / (endTS - startTS);
		
		printSampling();
	}
	
	public double timeStampIndexOfEBEXTime(double ebexTimeStamp) {
		return startTSIndex + samplingTSRate * (ebexTimeStamp - startTS);
	}
	
	protected void printSampling() {
		System.err.println(" ACS Sampling Rate: " + Util.f2.format(samplingTSRate / ebexTime.getSamples()) + " Hz.");
	}
	
	public <Type extends Number> Type getNearest(DataStore<Type> data, double timeStampIndex) throws IOException {
		return data.get(Math.round(timeStampIndex / ebexTime.getSamples() * data.getSamples()));
	}
	
	public double getInterpolated(DataStore<?> data, double timeStampIndex) throws IOException {
		double row = timeStampIndex / ebexTime.getSamples() * data.getSamples();
		long n = (long) Math.floor(row);
		double f = row - n;
		
		if(f == 0.0) return data.get(n).doubleValue();
		
		return (1.0 - f) * data.get(n).doubleValue() + f * data.get(n+1).doubleValue();
	}
	
	public void getData(EBEXFrame frame) throws IOException {
		// AZ, EL, RA, DEC, LAT, LON, DGPS_TIME, LST
		// horizontal, horizontalOffset, sinA, cosA, sinPA, cosPA
		// sinA, cosA default for Cassegrain? (0.0,1.0) in validate()...
		// equatorial, MJD, LST
		// chopperPosition?

		double t = timeStampIndexOfEBEXTime(frame.ebexTime);
		
		frame.equatorial = new EquatorialCoordinates(getInterpolated(RA, t) * Unit.hourAngle, getInterpolated(DEC, t) * Unit.deg, CoordinateEpoch.J2000);
		frame.horizontal = new HorizontalCoordinates(getInterpolated(AZ, t) * Unit.deg, getInterpolated(EL, t) * Unit.deg);
		frame.location = new GeodeticCoordinates(getInterpolated(LON, t) * Unit.deg, getInterpolated(LAT, t) * Unit.deg);
		frame.LST = getInterpolated(LST, t) * Unit.hour;
		frame.MJD = AstroTime.getMJD(getNearest(gpsTime, t)); 

		// TODO how does roll angle go into position angle (sign)?
		double rollAngle = getInterpolated(roll, t) * Unit.deg;
		frame.sinA = Math.sin(rollAngle);
		frame.cosA = Math.cos(rollAngle);		

		frame.calcPA();	
	}
		

	
}
