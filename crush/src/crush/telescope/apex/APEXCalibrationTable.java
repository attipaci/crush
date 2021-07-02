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

package crush.telescope.apex;

import java.io.*;
import java.util.*;

import crush.CRUSH;
import jnum.Unit;
import jnum.Util;
import jnum.data.localized.LocalAverage;
import jnum.data.localized.LocalizedData;
import jnum.data.localized.ScalarLocality;
import jnum.io.LineParser;
import jnum.math.Scalar;
import jnum.text.SmartTokenizer;



public class APEXCalibrationTable extends LocalAverage<ScalarLocality, Scalar> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3843930469196283911L;
	
	private static Hashtable<String, APEXCalibrationTable> tables = new Hashtable<>();
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
               
                tokens.skip(2);
                
                add(new LocalizedData<>(
                        new ScalarLocality(tokens.nextDouble()),
                        new Scalar(1.0 / tokens.nextDouble()),
                        0.005
                ));
  
                return true;
            }   
		}.read(datafile);
		
		this.fileName = datafile;
		
		CRUSH.info(this, "[Loading calibration data] -- " + size() + " values parsed.");
	}
	
	public double getScaling(double MJD) {
		LocalizedData<ScalarLocality, Scalar> mean = getLocalAverage(new ScalarLocality(MJD), timeWindow / Unit.day);
		
		if(mean.weight() == 0.0) {
			CRUSH.info(this, "... No calibration data was found in specified time window.");
			
			if(timeWindow < 6.0 * Unit.hour) {
				CRUSH.info(this, "... expanding scaling lookup window to 6 hours.");
				mean = getLocalAverage(new ScalarLocality(MJD), timeWindow / Unit.day);
			}
			else {
				CRUSH.warning(this, "Local calibration scaling is unknown.");
				return 1.0;
			}
		}
		else if(Double.isInfinite(mean.getData().value())) {
			CRUSH.warning(this, "Inifinite local calibration scaling.");
			return 1.0;
		}
		
		CRUSH.values(this, "Local average scaling = " + Util.f3.format(mean.getData().value()) + " (from " + mean.getCount() + " cal scans)");
		return mean.getData().value();
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
