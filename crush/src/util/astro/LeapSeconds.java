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

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public final class LeapSeconds {
	private static ArrayList<LeapEntry> list;
	
	public final static long millis1900 = -2208988800000L; // "1900-01-01T00:00:00.000" UTC
	
	private static long releaseEpoch = 3427142400L;
	private static long expirationEpoch = 3534019200L;
	private static long expirationMillis = expirationEpoch + millis1900;
	
	private static int currentLeap = 34;
	private static long currentSinceMillis = 3439756800000L + millis1900; 

	public static int get(long timestamp) {
		if(timestamp >= currentSinceMillis) return currentLeap;
		
		//if(list == null) return currentLeap;
		
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
		
		System.err.println("Reading leap seconds table from " + fileName);
		
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
		
		System.err.println("--> Found " + list.size() + " leap-second entries.");
		
		DateFormat tf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		tf.setTimeZone(TimeZone.getTimeZone("UTC"));
		System.err.println("--> Released: " + tf.format(1000L * releaseEpoch + millis1900));
		System.err.println("--> Expires: " + tf.format(expirationMillis));
		
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