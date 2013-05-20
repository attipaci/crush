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
package util.astro;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

// Last updated on 31 Oct 2012
//  -- Historical Leap seconds lookup added and fixed.

// NOTE: Under no circumstances should one query a NIST server more frequently than once every 4 seconds!!!


public final class LeapSeconds {
	private static ArrayList<LeapEntry> list;
	
	public final static long millis1900 = -2208988800000L; // "1900-01-01T00:00:00.000" UTC
	public static String dataFile = null;
	public static boolean verbose = false;
	
	private static int currentLeap = 35;
	
	private static long releaseEpoch = 3535228800L;			// seconds since 1900
	private static long expirationEpoch = 3581366400L;		// seconds since 1900
	private static long expirationMillis = millis1900 + 1000L * expirationEpoch;
	private static long currentSinceMillis = millis1900 + 1000L * 3550089600L; 
	private final static long firstLeapMillis = millis1900 + 1000L * 2272060800L;	// 1 January 1972
	
	public static int get(long timestamp) {
	
		if(timestamp >= currentSinceMillis) return currentLeap;
		if(timestamp < firstLeapMillis) return 0;
		
		if(list == null) {
			if(dataFile == null) {
				System.err.println("WARNING! No historical leap-seconds data. Will use: " + currentLeap + " s.");
				return currentLeap;
			}
			
			try { read(dataFile); }
			catch(IOException e) {
				System.err.println("WARNING! Could not real leap seconds data: " + dataFile);
				System.err.println("         Problem: " + e.getMessage());
				System.err.println("         Will use current default value: " + currentLeap + " s.");
				return currentLeap;
			}
			
			if(timestamp >= expirationMillis) {
				System.err.println("WARNING! Leap seconds data is no longer current.");
				System.err.println("         To fix it, update '" + dataFile + "'.");
			}

		}
		
		if(timestamp > expirationMillis) {
			System.err.println("WARNING! Leap data expired: " + dataFile);
			System.err.println("         Will use the current default value: " + currentLeap + " s");
			
		}
		
		int lower = 0, upper = list.size()-1;
		
		if(timestamp < list.get(lower).timestamp)
			return 0;
		
		if(timestamp > list.get(upper).timestamp) 
			return list.get(upper).leap;
		
		while(upper - lower > 1) {
			int i = (upper + lower) >> 1;
			long t = list.get(i).timestamp;
			if(timestamp >= t) lower = i;
			if(timestamp <= t) upper = i;
		}
		
		return list.get(lower).leap;
	}
	
	public static boolean isCurrent() {
		return System.currentTimeMillis() < expirationMillis;
	}
	
	public static void read(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		
		if(list == null) list = new ArrayList<LeapEntry>();
		else list.clear();
		
		if(verbose) System.err.println("Reading leap seconds table from " + fileName);
		
		while((line=in.readLine()) != null) if(line.length() > 2) {
			StringTokenizer tokens = new StringTokenizer(line);
			
			if(line.charAt(0) == '#') {
				tokens.nextToken();
				if(line.charAt(1) == '$') releaseEpoch = Long.parseLong(tokens.nextToken());
				else if(line.charAt(1) == '@') {
					expirationEpoch = Long.parseLong(tokens.nextToken());
					expirationMillis = 1000L * expirationEpoch + millis1900;
				}
			}
			else {
				LeapEntry entry = new LeapEntry();
				entry.timestamp = 1000L * Long.parseLong(tokens.nextToken()) + millis1900;
				entry.leap = Integer.parseInt(tokens.nextToken());
				list.add(entry);
			}
		}
		
		Collections.sort(list);
		
		LeapEntry current = list.get(list.size() - 1);
		currentLeap = current.leap;
		currentSinceMillis = current.timestamp;
		
		if(verbose) {
			System.err.println("--> Found " + list.size() + " leap-second entries.");
		
			DateFormat tf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
			tf.setTimeZone(TimeZone.getTimeZone("UTC"));
			System.err.println("--> Released: " + tf.format(1000L * releaseEpoch + millis1900));
			System.err.println("--> Expires: " + tf.format(expirationMillis));
		}
		
		in.close();
	}	
}

class LeapEntry implements Comparable<LeapEntry> {
	long timestamp;
	int leap;
	
	public int compareTo(LeapEntry other) {
		if(timestamp == other.timestamp) return 0;
		return timestamp < other.timestamp ? -1 : 1;
	}
}
