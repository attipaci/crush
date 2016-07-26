/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.jcmt;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.StringTokenizer;

import crush.CRUSH;
import jnum.Configurator;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.data.LocalAverage;
import jnum.data.Locality;
import jnum.data.LocalizedData;


public class JCMTTauTable  extends LocalAverage<JCMTTauTable.Entry> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2346424548165913964L;
	private static Hashtable<String, JCMTTauTable> tables = new Hashtable<String, JCMTTauTable>();
	public String fileName;
	public double timeWindow = 0.5 * Unit.hour;
	
	public static JCMTTauTable get(int iMJD, String fileName) throws IOException {
		JCMTTauTable table = tables.get(fileName);
		if(table == null) {
			table = new JCMTTauTable(iMJD, fileName);
			tables.put(fileName, table);
		}
		return table;
	}
	
	private JCMTTauTable(int iMJD, String fileName) throws IOException {
		read(iMJD, fileName);	
	}
	
	public void setOptions(Configurator options) {
		if(options.containsKey("window")) timeWindow = options.get("window").getDouble() * Unit.hour;
	}
	
	protected void read(int iMJD, String fileName) throws IOException {
		
			
		BufferedReader in = Util.getReader(fileName);
		
		// Skip the header line...
		String line = in.readLine();
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') if(line.charAt(0) != ' ') {
			StringTokenizer tokens = new StringTokenizer(line);
			Entry skydip = new Entry();
			skydip.timeStamp = new TimeStamp(iMJD, tokens.nextToken());
			skydip.tau.setValue(Double.parseDouble(tokens.nextToken()));
			//tokens.nextToken();
			skydip.tau.setRMS(0.001);
			add(skydip);
		}
		in.close();
		
		CRUSH.info(this, "[Loading tau data] " + size() + " values parsed.");
		
		Collections.sort(this);
		
		this.fileName = fileName;
		
		
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
		
		public TimeStamp(int iMJD, String hhmmsss) { 
			this.MJD = iMJD + (
						Integer.parseInt(hhmmsss.substring(0, 2)) * Unit.hour +
						Integer.parseInt(hhmmsss.substring(3, 5)) * Unit.min + 
						Double.parseDouble(hhmmsss.substring(6)) * Unit.s
					) / Unit.day; 
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
		private static final long serialVersionUID = -3204359502356393498L;
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


