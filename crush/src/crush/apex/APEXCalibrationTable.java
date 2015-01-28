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
// Copyright (c) 2009,2010 Attila Kovacs

package crush.apex;

import java.io.*;
import java.util.*;

import kovacs.data.DataPoint;
import kovacs.data.LocalAverage;
import kovacs.data.Locality;
import kovacs.data.LocalizedData;
import kovacs.util.Unit;
import kovacs.util.Util;



public class APEXCalibrationTable extends LocalAverage<APEXCalibrationTable.Entry> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3843930469196283911L;
	
	private static Hashtable<String, APEXCalibrationTable> tables = new Hashtable<String, APEXCalibrationTable>();
	
	public String fileName;
	public double timeWindow = 30 * Unit.min;
	
	public static APEXCalibrationTable get(String fileName) throws IOException {
		APEXCalibrationTable table = tables.get(fileName);
		if(table == null) {
			table = new APEXCalibrationTable(fileName);
			tables.put(fileName, table);
		}
		return table;
	}
		
	private APEXCalibrationTable(String fileName) throws IOException {
		read(fileName);
	}
	
	protected void read(String datafile) throws IOException {	
		System.err.print("   [Loading calibration data] ");
		
		BufferedReader in = Util.getReader(datafile);

		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			Entry calibration = new Entry();
			
			tokens.nextToken();
			tokens.nextToken();
			calibration.timeStamp = new TimeStamp(Double.parseDouble(tokens.nextToken()));
			calibration.scaling.setValue(1.0 / Double.parseDouble(tokens.nextToken()));
			calibration.scaling.setWeight(1.0);
		
			add(calibration);
		}
		in.close();
		
		System.err.println("-- " + size() + " values parsed.");
	}
	
	public double getScaling(double MJD) {
		Entry mean = getLocalAverage(new TimeStamp(MJD));
		
		if(mean.scaling.weight() == 0.0) {
			System.err.println("   ... No calibration data was found in specified time window.");
			
			if(timeWindow < 6.0 * Unit.hour) {
				System.err.println("   ... expanding scaling lookup window to 6 hours.");
				timeWindow = 6.0 * Unit.hour;
				mean = getLocalAverage(new TimeStamp(MJD));
			}
			else {
				System.err.println("   WARNING! Local calibration scaling is unknown.");
				return 1.0;
			}
		}
		else if(Double.isInfinite(mean.scaling.value())) {
			System.err.println("   WARNING! Inifinite local calibration scaling.");
			return 1.0;
		}
		
		System.err.println("   Local average scaling = " + Util.f3.format(mean.scaling.value()) + " (from " + mean.measurements + " cal scans)");
		return mean.scaling.value();
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
		DataPoint scaling = new DataPoint();
		
		
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
			scaling.average(entry.scaling.value(), relativeWeight * entry.scaling.weight());
		}	
	}

	@Override
	public Entry getLocalizedDataInstance() {
		return new Entry();
	}
	
	
}
