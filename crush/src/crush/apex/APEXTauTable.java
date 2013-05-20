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
package crush.apex;

import java.io.*;
import java.util.*;

import util.Unit;
import util.Util;
import util.data.DataPoint;
import util.data.LocalAverage;
import util.data.Locality;
import util.data.LocalizedData;


public class APEXTauTable extends LocalAverage<APEXTauTable.Entry> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7962217110619389946L;
	
	private static Hashtable<String, APEXTauTable> tables = new Hashtable<String, APEXTauTable>();

	public String fileName;
	public double timeWindow = 1.5 * Unit.hour;
	
	public static APEXTauTable get(String fileName) throws IOException {
		APEXTauTable table = tables.get(fileName);
		if(table == null) {
			table = new APEXTauTable(fileName);
			tables.put(fileName, table);
		}
		return table;
	}
	
	private APEXTauTable(String fileName) throws IOException {
		read(fileName);
	}
	
	protected void read(String fileName) throws IOException {
		System.err.print("   [Loading tau data] ");
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			Entry skydip = new Entry();
			tokens.nextToken();
			tokens.nextToken();
			skydip.timeStamp = new TimeStamp(Double.parseDouble(tokens.nextToken()));
			skydip.tau.setValue(Double.parseDouble(tokens.nextToken()));
			skydip.tau.setRMS(1.0);
			add(skydip);
		}
		in.close();
		
		this.fileName = fileName;
		
		System.err.println("-- " + size() + " values parsed.");
	}	
	
	public double getTau(double MJD) {
		Entry mean = getLocalAverage(new TimeStamp(MJD));
		
		if(mean.tau.weight() == 0.0) {
			System.err.println("   ... No skydip data was found in specified time window.");
			
			if(timeWindow < 6.0 * Unit.hour) {
				System.err.println("   ... expanding tau lookup window to 6 hours.");
				timeWindow = 6.0 * Unit.hour;
				mean = getLocalAverage(new TimeStamp(MJD));
			}
			else {
				System.err.println("   WARNING! Tau is unknown.");
				return 0.0;
			}
		}
		
		System.err.println("   Local average tau = " + Util.f3.format(mean.tau.value()) + " (from " + mean.measurements + " skydips)");
		return mean.tau.value();
	}
	
	class TimeStamp extends Locality {
		double MJD;
		
		public TimeStamp(double MJD) { this.MJD = MJD; }
		
		public double distanceTo(Locality other) {
			return(Math.abs((((TimeStamp) other).MJD - MJD) * Unit.day / timeWindow));
		}

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
			Entry entry = (Entry) other;
			tau.average(entry.tau.value(), relativeWeight * entry.tau.weight());
		}	
	}

	@Override
	public Entry getLocalizedDataInstance() {
		return new Entry();
	}
	
}

