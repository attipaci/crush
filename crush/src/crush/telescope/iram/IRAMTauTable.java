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
package crush.telescope.iram;

import java.io.*;
import java.text.*;
import java.util.*;

import crush.CRUSH;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroTime;
import jnum.data.DataPoint;
import jnum.data.ScalarLocality;
import jnum.io.LineParser;
import jnum.text.SmartTokenizer;
import jnum.data.LocalAverage;
import jnum.data.Locality;
import jnum.data.LocalizedData;

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
			

		final SimpleDateFormat df = new SimpleDateFormat("hh:mm:ss yyyy-MM-dd");
        df.setTimeZone(TimeZone.getTimeZone(timeZone));	
		
        new LineParser() {

            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);     
                if(tokens.countTokens() < 4) return false;
                
                Entry skydip = new Entry();
                String dateSpec = tokens.nextToken() + " " + tokens.nextToken();
                Date date = df.parse(dateSpec);
                skydip.timeStamp = new TimeStamp(AstroTime.getMJD(date.getTime()));         
                
                skydip.tau.setValue(tokens.nextDouble());    
                skydip.tau.setRMS(tokens.nextDouble());
                add(skydip);
                return true;
            }
        }.read(fileName);
			
        CRUSH.info(this, "[Loading skydip tau values.] -- " + size() + " valid records found.");
        if(CRUSH.debug) CRUSH.detail(this, " >> " + fileName + " >> ");
		
		this.fileName = fileName;
		
		Collections.sort(this);
		
	}
	
	public double getTau(double MJD) {
		Entry mean = getCheckedLocalAverage(new TimeStamp(MJD));
		CRUSH.values(this, "Local average tau(225GHz) = " + mean.tau.toString(Util.f3) + " (from " + mean.measurements + " measurements)");
		return mean.tau.value();
	}
	
	
	class TimeStamp extends ScalarLocality {
		
		public TimeStamp(double MJD) { super(MJD); }
		
		@Override
		public double distanceTo(Locality other) {
			return super.distanceTo(other) * Unit.day / Math.abs(timeWindow);
		}
	}

	class Entry extends LocalizedData {
		/**
		 * 
		 */
		private static final long serialVersionUID = -7635791450826001376L;
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
			DataPoint difference = entry.tau.copy();
			difference.subtract(tau);	
			return Math.abs(difference.significance()) < 5.0;
		}
		
		@Override
		public String toString() { 
			return timeStamp.toString() + ": " + tau;
		}
		
	}

	@Override
	public Entry getLocalizedDataInstance() {
		return new Entry();
	}

	
}


