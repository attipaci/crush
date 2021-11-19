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



public class APEXTauTable extends LocalAverage<ScalarLocality, Scalar> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7962217110619389946L;
	
	private static Hashtable<String, APEXTauTable> tables = new Hashtable<>();

	public String fileName;
	public double timeWindow = 1.5 * Unit.hour;
	
	
	private APEXTauTable(String fileName) throws IOException {
		read(fileName);
	}
	
	protected void read(String fileName) throws IOException {
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);

                tokens.skip(2);

                add(new LocalizedData<> (
                        new ScalarLocality(tokens.nextDouble()),
                        new Scalar(tokens.nextDouble()), 
                        0.005
                ));

                return true;
            }   
		}.read(fileName);
		
		this.fileName = fileName;
		
		CRUSH.info(this, "[Loading tau data] -- " + size() + " values parsed.");
	}	
	
	public double getTau(double MJD) {
		LocalizedData<ScalarLocality, Scalar> mean = getLocalAverage(new ScalarLocality(MJD), timeWindow / Unit.day);
		
		if(mean.weight() == 0.0) {
			CRUSH.info(this, "... No skydip data was found in specified time window.");
			
			if(timeWindow < 6.0 * Unit.hour) {
				CRUSH.info(this, "... expanding tau lookup window to 6 hours.");
				mean = getLocalAverage(new ScalarLocality(MJD), (6.0 * Unit.hour) / Unit.day);
			}
			
			if (mean.weight() == 0.0) {
				CRUSH.warning(this, "Local tau is unknown. Will use 0.0...");
				return 0.0;
			}
		}
		else if(!Double.isFinite(mean.getData().value())) {
			CRUSH.warning(this, "Non-finite local tau. Will use 0.0...");
			return 0.0;
		}
		
		CRUSH.values(this, "Local average tau = " + Util.f3.format(mean.getData().value()) + " (from " + mean.getCount() + " skydips)");
		return mean.getData().value();
	}


	public static APEXTauTable get(String fileName) throws IOException {
	    APEXTauTable table = tables.get(fileName);
	    if(table == null) {
	        table = new APEXTauTable(fileName);
	        tables.put(fileName, table);
	    }
	    return table;
	}

}

