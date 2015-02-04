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
package crush.iram;

import java.io.*;
import java.text.*;
import java.util.*;

import kovacs.astro.AstroTime;
import kovacs.data.DataPoint;
import kovacs.data.LocalAverage;
import kovacs.data.Locality;
import kovacs.data.LocalizedData;
import kovacs.util.*;

import crush.CRUSH;

public class IRAMTauTable extends LocalAverage<IRAMTauTable.Entry> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3850376076747456359L;

	public String fileName = "";

	public double timeWindow = 15.0 * Unit.min;
	
	private static Hashtable<String, IRAMTauTable> tables = new Hashtable<String, IRAMTauTable>();

	public static IRAMTauTable get(String fileName, String timeZone) throws IOException {
		IRAMTauTable table = tables.get(fileName);
		if(table == null) {
			table = new IRAMTauTable(fileName, timeZone);
			tables.put(fileName, table);
		}
		return table;
	}
	
		
	private IRAMTauTable(String fileName, String timeZone) throws IOException {
		read(fileName, timeZone);
	}
	
	private void read(String fileName, String timeZone) throws IOException {
		if(fileName.equals(this.fileName)) return;
			
		System.err.print(" [Loading skydip tau values.]");
		if(CRUSH.debug) System.err.print(" >> " + fileName + " >> ");		
		
		BufferedReader in = Util.getReader(fileName);
		String line = null;
		
		SimpleDateFormat df = new SimpleDateFormat("hh:mm:ss yyyy-MM-dd");
		df.setTimeZone(TimeZone.getTimeZone(timeZone));
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			
			if(tokens.countTokens() > 3) {
				Entry skydip = new Entry();
				String dateSpec = null;
				try { 
					dateSpec = tokens.nextToken() + " " + tokens.nextToken();
					Date date = df.parse(dateSpec);
					skydip.timeStamp = new TimeStamp(AstroTime.getMJD(date.getTime()));			
					
					try { 
						skydip.tau.setValue(Double.parseDouble(tokens.nextToken())); 	
						skydip.tau.setRMS(Double.parseDouble(tokens.nextToken()));
						add(skydip);
					}
					catch(NumberFormatException e) {}
					
				}
				catch(ParseException e) {
					System.err.println("WARNING! Cannot parse date " + dateSpec);
				}
			}
		}
		in.close();
		
		this.fileName = fileName;
		
		Collections.sort(this);
		
		System.err.println(" -- " + size() + " valid records found.");	
	}
	
	public double getTau(double MJD) {
		Entry mean = getCheckedLocalAverage(new TimeStamp(MJD));
		System.err.println(" Local average tau(225GHz) = " + mean.tau.toString(Util.f3) + " (from " + mean.measurements + " measurements)");
		return mean.tau.value();
	}
	
	
	class TimeStamp extends Locality {
		double MJD;
		
		public TimeStamp(double MJD) { this.MJD = MJD; }
		
		@Override
		public double distanceTo(Locality other) {
			return(Math.abs((((TimeStamp) other).MJD - MJD) * Unit.day / timeWindow));
		}

		@Override
		public int compareTo(Locality o) {
			return Double.compare(MJD, ((TimeStamp) o).MJD);
		}
		
		@Override
		public String toString() { return Double.toString(MJD); }

		@Override
		public double sortingDistanceTo(Locality other) {
			return distanceTo(other);
		}
	}

	class Entry extends LocalizedData {
		TimeStamp timeStamp;
		DataPoint tau = new DataPoint();
		
		@Override
		public Locality getLocality() {
			return timeStamp;
		}

		@Override
		public void setLocality(Locality loc) {
			timeStamp = (TimeStamp) loc;
		}

		@Override
		protected void averageWidth(LocalizedData other, Object env, double relativeWeight) {	
			Entry point = (Entry) other;	
			tau.average(point.tau.value(), relativeWeight * point.tau.weight());
		}

		@Override
		public boolean isConsistentWith(LocalizedData other) {
			Entry entry = (Entry) other;
			DataPoint difference = (DataPoint) entry.tau.copy();
			difference.subtract(tau);	
			return Math.abs(difference.significance()) < 5.0;
		}
		
		@Override
		public String toString() { 
			return timeStamp.toString() + ": " + tau.toString();
		}
		
	}

	@Override
	public Entry getLocalizedDataInstance() {
		return new Entry();
	}

	
}

