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
// Copyright (c) 2007,2008,2009,2010 Attila Kovacs

package crush;

import java.io.*;
import java.util.*;

import util.Unit;
import util.data.Interpolator;

public class ElevationCouplingCurve extends Interpolator {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ElevationCouplingCurve(String fileName) throws IOException {
		super(fileName);		
	}
	
	@Override
	public void readData(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			Interpolator.Data response = new Interpolator.Data();
			response.ordinate = Double.parseDouble(tokens.nextToken()) * Unit.deg;
			response.value = Double.parseDouble(tokens.nextToken());
			add(response);
		}
		in.close();
	}	
}
