/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.telescope.cso;


import java.io.*;
import java.util.*;

import crush.CRUSH;
import jnum.Configurator;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.data.LocalAverage;
import jnum.data.Locality;
import jnum.data.LocalizedData;
import jnum.io.LineParser;
import jnum.text.SmartTokenizer;



public class CSOTauTable extends LocalAverage<CSOTauTable.Entry> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7962217110619389946L;
	
	private static Hashtable<String, CSOTauTable> tables = new Hashtable<>();

	public String fileName;
	public double timeWindow = 0.5 * Unit.hour;
	
	public static CSOTauTable get(int iMJD, String fileName) throws IOException {
		CSOTauTable table = tables.get(fileName);
		if(table == null) {
			table = new CSOTauTable(iMJD, fileName);
			tables.put(fileName, table);
		}
		return table;
	}
	
	private CSOTauTable(int iMJD, String fileName) throws IOException {
		read(iMJD, fileName);	
	}
	
	public void setOptions(Configurator options) {
		if(options.containsKey("window")) timeWindow = options.option("window").getDouble() * Unit.hour;
	}
	
	protected void read(final int iMJD, String fileName) throws IOException {
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                Entry entry = new Entry();
                entry.timeStamp = new TimeStamp(iMJD, tokens.nextToken());
                entry.tau.setValue(tokens.nextDouble());
                tokens.skip();
                entry.tau.setRMS(ExtraMath.hypot(0.005, tokens.nextDouble()));
                add(entry);
                return true;
            }
		}.read(fileName);
		
        this.fileName = fileName;
		
		CRUSH.info(this, "[Loading tau data] -- " + size() + " values parsed.");
	}	
	
	public double getTau(double MJD) {
			
		Entry mean = getLocalAverage(new TimeStamp(MJD));
		
		if(mean.tau.weight() == 0.0) {
			CRUSH.info(this, "... No skydip data was found in specified time window.");
			
			if(timeWindow < 6.0 * Unit.hour) {
				CRUSH.info(this, "... expanding tau lookup window to 6 hours.");
				timeWindow = 6.0 * Unit.hour;
				mean = getLocalAverage(new TimeStamp(MJD));
			}
			else {
				CRUSH.warning(this, "Tau is unknown.");
				return 0.0;
			}
		}
		
		CRUSH.values(this, "Local average tau = " + Util.f3.format(mean.tau.value()) + " (from " + mean.measurements + " measurements)");
		return mean.tau.value();
	}
	
	class TimeStamp extends Locality {
		double MJD;
		
		public TimeStamp(double MJD) { this.MJD = MJD; }
		
		public TimeStamp(int iMJD, String hhmm) { 
			this.MJD = iMJD + (Integer.parseInt(hhmm.substring(0, 2)) * Unit.hour + Integer.parseInt(hhmm.substring(2)) * Unit.min) / Unit.day; 
		}
		
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
		/**
		 * 
		 */
		private static final long serialVersionUID = 7189114815744822461L;
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

