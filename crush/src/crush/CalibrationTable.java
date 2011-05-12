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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.io.*;
import java.util.*;

import util.data.Interpolator;


public class CalibrationTable extends Interpolator {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3843930469196283911L;
	
	public CalibrationTable(String fileName) throws IOException {
		super(fileName);
	}
	
	@Override
	public void readData(String datafile) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(datafile)));

		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			Interpolator.Data calibration = new Interpolator.Data();
			
			tokens.nextToken();
			tokens.nextToken();
			calibration.ordinate = Double.parseDouble(tokens.nextToken());
			calibration.value = Double.parseDouble(tokens.nextToken());
		
			add(calibration);
		}
		in.close();
	}
	
	
}
