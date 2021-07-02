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
package crush.telescope.iram;

import java.io.*;
import java.text.*;
import java.util.*;

import crush.CRUSH;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroTime;
import jnum.data.localized.LocalAverage;
import jnum.data.localized.LocalizedData;
import jnum.data.localized.ScalarLocality;
import jnum.io.LineParser;
import jnum.math.Scalar;
import jnum.text.SmartTokenizer;

public class IRAMTauTable extends LocalAverage<ScalarLocality, Scalar> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3850376076747456359L;

	public String fileName = "";

	public double timeWindow = 15.0 * Unit.min;
	
	private static Hashtable<String, IRAMTauTable> tables = new Hashtable<>();

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
           
                String dateSpec = tokens.nextToken() + " " + tokens.nextToken();
                Date date = df.parse(dateSpec);
                
                add(new LocalizedData<>(
                        new ScalarLocality(AstroTime.getMJD(date.getTime())),         
                        new Scalar(tokens.nextDouble()),
                        tokens.nextDouble()
                ));

                return true;
            }
        }.read(fileName);
			
        CRUSH.info(this, "[Loading skydip tau values.] -- " + size() + " valid records found.");
        if(CRUSH.debug) CRUSH.detail(this, " >> " + fileName + " >> ");
		
		this.fileName = fileName;
		
		Collections.sort(this);
		
	}
	
	  
	
	public double getTau(double MJD) {
		LocalizedData<ScalarLocality, Scalar> mean = getCheckedLocalAverage(new ScalarLocality(MJD), timeWindow / Unit.day);
		CRUSH.values(this, "Local average tau(225GHz) = " + mean.getData().toString(Util.f3) + " (from " + mean.getCount() + " measurements)");
		return mean.getData().value();
	}
	


	
}


