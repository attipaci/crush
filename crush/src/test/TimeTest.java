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
package test;


import java.text.*;
import java.util.*;

import kovacs.util.astro.AstroTime;
import kovacs.util.astro.LeapSeconds;

public class TimeTest {

	public static void main(String[] args) {
		String date = "2000-01-01T11:58:55.816";
		DateFormat fitsFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		fitsFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		long millis1900 = -2208988800000L; 
		System.err.println("last leap: " + fitsFormatter.format(3439756800000L + millis1900)); 
		
		try { 
			LeapSeconds.read("/home/pumukli/leap-seconds.list");
			System.err.println("leap 1900: " + LeapSeconds.get(millis1900));
			System.err.println("leap 2000: " + LeapSeconds.get(AstroTime.millisJ2000));
			System.err.println("leap now: " + LeapSeconds.get(System.currentTimeMillis()));
		}
		catch(Exception e) { e.printStackTrace(); }
		
		System.err.println(">>> " + AstroTime.millisJ2000);
		
		try {
			System.err.println("millis: " + fitsFormatter.parse(date).getTime());
		
			AstroTime time = AstroTime.forFitsTimeStamp(date);
			System.err.println("MJD: " + time.getMJD());
		}
		catch(Exception e) { e.printStackTrace(); }
		
		date = "1900-01-01T00:00:00.000";
		try { 
			System.err.println("millis1900: " + fitsFormatter.parse(date).getTime());
		}
		catch(Exception e) { e.printStackTrace(); }
		
		System.err.println("UNIX time ref: " + fitsFormatter.format(0L));
		
		
		
	}
	
}
