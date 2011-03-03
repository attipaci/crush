/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2010 Attila Kovacs 

package crush.polka;

import java.io.*;
import java.util.*;

public class PolKaTimeStamps extends Vector<Double> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3924428949508560042L;
	
	public int lowerIndex(double value) {
		int step = size()-1;
		
		if(value < get(0)) return -1;
		
		int i=0;
		while(step > 1) {
			if(value > get(i+step)) i += step;
			step >>= 1; 
		}
		
		return i;
	}
	
	public int higherIndex(double value) {
		return lowerIndex(value) + 1;
	}
	

	public void read(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
		
		int MJD0 = 0;
		
		while((line = in.readLine()) != null) if(line.length() > 0) {
			if(line.charAt(0) == '#') {
				if(line.startsWith("# MJD zero")) MJD0 = Integer.parseInt(line.substring(11));
			}
			else add(MJD0 + Double.parseDouble(line));
		}
		
		in.close();
		Collections.sort(this);
	}

}
