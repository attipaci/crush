/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
package crush.telescope.cso;


import java.io.*;
import java.util.*;

import crush.CRUSH;
import jnum.Configurator;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.data.localized.LocalAverage;
import jnum.data.localized.LocalizedData;
import jnum.data.localized.ScalarLocality;
import jnum.io.LineParser;
import jnum.math.Scalar;
import jnum.text.SmartTokenizer;



public class CSOTauTable extends LocalAverage<CSOTauTable.TimeStamp, Scalar> {
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

                TimeStamp ts = new TimeStamp(iMJD, tokens.nextToken());
                double tau = tokens.nextDouble();
                tokens.skip();
       
                add(new LocalizedData<>(ts, new Scalar(tau), ExtraMath.hypot(0.005, tokens.nextDouble())));
                
                return true;
            }
		}.read(fileName);
		
        this.fileName = fileName;
		
		CRUSH.info(this, "[Loading tau data] -- " + size() + " values parsed.");
	}	
	
	public double getTau(double MJD) {
			
		LocalizedData<TimeStamp, Scalar> mean = getLocalAverage(new TimeStamp(MJD), timeWindow / Unit.day);
		
		if(mean.weight() == 0.0) {
			CRUSH.info(this, "... No tau data was found in specified time window.");
			
			if(timeWindow < 6.0 * Unit.hour) {
				CRUSH.info(this, "... expanding tau lookup window to 6 hours.");
				mean = getLocalAverage(new TimeStamp(MJD), (6.0 * Unit.hour) / Unit.day);
			}
			else {
				CRUSH.warning(this, "Tau is unknown.");
				return 0.0;
			}
		}
		
		CRUSH.values(this, "Local average tau = " + Util.f3.format(mean.getData().value()) + " (from " + mean.getCount() + " measurements)");
		return mean.getData().value();
	}
	
	class TimeStamp extends ScalarLocality {

		public TimeStamp(double MJD) { super(MJD); }
		 
		public TimeStamp(int iMJD, String hhmm) { 
		    this(iMJD + (Integer.parseInt(hhmm.substring(0, 2)) * Unit.hour + Integer.parseInt(hhmm.substring(2)) * Unit.min) / Unit.day);
		}
	}

	
}

