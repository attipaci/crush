/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package kovacs.util.data;

import java.io.*;
import java.text.ParseException;
import java.util.StringTokenizer;
import java.util.Vector;

import kovacs.util.CoordinatePair;




public class Mask<CoordinateType extends CoordinatePair> extends Vector<Region<CoordinateType>> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2991565823082882993L;
	
	public void read(String fileName, int format, GridImage<CoordinateType> image) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		int startSize = size();
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			String first = tokens.nextToken();
			Region<CoordinateType> r;
			String spec = null;
			
			if(first.equals("begin")) {
				r = regionFor(tokens.nextToken());
				StringBuffer buf = new StringBuffer();
				boolean isComplete = false;
				
				while(!isComplete && (line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
					if(line.startsWith("end")) isComplete = true;
					else buf.append(line + "\n");
				}
				
				spec = new String(line);
			}
			else { 
				r = new CircularRegion<CoordinateType>();
				spec = line;
			}
				
			try {
				r.parse(spec, format, image);
				add(r);
			}
			catch(ParseException e) {
				System.err.println(" WARNING! parse error for:\n" + spec);
			}
			
		}
		
		in.close();
		
		System.err.println(" Parsed " + (size() - startSize) + " regions.");
	}
	
	public Region<CoordinateType> regionFor(String id) {
		id = id.toLowerCase();
		if(id.equals("circle")) return new CircularRegion<CoordinateType>();
		else return null;
	}
	
}
