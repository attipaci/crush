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

package crush.apex;

import java.io.*;
import java.util.*;

import crush.CRUSH;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.data.ScalarLocality;
import jnum.io.LineParser;
import jnum.text.SmartTokenizer;
import jnum.data.LocalAverage;
import jnum.data.Locality;
import jnum.data.LocalizedData;



public class APEXCalibrationTable extends LocalAverage<APEXCalibrationTable.Entry> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3843930469196283911L;
	
	private static Hashtable<String, APEXCalibrationTable> tables = new Hashtable<String, APEXCalibrationTable>();
		public String fileName;
	public double timeWindow = 30 * Unit.min;
	

		
	private APEXCalibrationTable(String fileName) throws IOException {
		read(fileName);
	}
	
	protected void read(String datafile) throws IOException {	
	    new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                Entry calibration = new Entry();
                
                tokens.skip(2);
                calibration.timeStamp = new TimeStamp(tokens.nextDouble());
                calibration.scaling.setValue(1.0 / tokens.nextDouble());
                calibration.scaling.setWeight(1.0);
            
                add(calibration);
                return true;
            }   
		}.read(datafile);
		
		this.fileName = datafile;
		
		CRUSH.info(this, "[Loading calibration data] -- " + size() + " values parsed.");
	}
	
	public double getScaling(double MJD) {
		Entry mean = getLocalAverage(new TimeStamp(MJD));
		
		if(mean.scaling.weight() == 0.0) {
			CRUSH.info(this, "... No calibration data was found in specified time window.");
			
			if(timeWindow < 6.0 * Unit.hour) {
				CRUSH.info(this, "... expanding scaling lookup window to 6 hours.");
				timeWindow = 6.0 * Unit.hour;
				mean = getLocalAverage(new TimeStamp(MJD));
			}
			else {
				CRUSH.warning(this, "Local calibration scaling is unknown.");
				return 1.0;
			}
		}
		else if(Double.isInfinite(mean.scaling.value())) {
			CRUSH.warning(this, "Inifinite local calibration scaling.");
			return 1.0;
		}
		
		CRUSH.values(this, "Local average scaling = " + Util.f3.format(mean.scaling.value()) + " (from " + mean.measurements + " cal scans)");
		return mean.scaling.value();
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
		private static final long serialVersionUID = 4103895830752978780L;
		
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
	
	public static APEXCalibrationTable get(String fileName) throws IOException {
	    APEXCalibrationTable table = tables.get(fileName);
	    if(table == null) {
	        table = new APEXCalibrationTable(fileName);
	        tables.put(fileName, table);
	    }
	    return table;
	}

}
