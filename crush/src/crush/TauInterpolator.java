/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs@post.harvard.edu>.
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
 *     Attila Kovacs <attila_kovacs@post.harvard.edu> - initial API and implementation
 ******************************************************************************/

// Copyright (c) 2007,2008,2009,2010 Attila Kovacs

package crush;

import java.io.*;
import java.util.*;

import util.data.Interpolator;


public class TauInterpolator extends Interpolator {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7962217110619389946L;
	
	private static Hashtable<String, TauInterpolator> tables = new Hashtable<String, TauInterpolator>();

	
	public static TauInterpolator get(String fileName) throws IOException {
		TauInterpolator table = tables.get(fileName);
		if(table == null) {
			table = new TauInterpolator(fileName);
			tables.put(fileName, table);
		}
		return table;
	}
	
	private TauInterpolator(String fileName) throws IOException {
		super(fileName);
	}
	
	@Override
	protected void readData(String fileName) throws IOException {
		System.err.print("   [Loading tau data] ");
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			Interpolator.Data skydip = new Interpolator.Data();
			tokens.nextToken();
			tokens.nextToken();
			skydip.ordinate = Double.parseDouble(tokens.nextToken());
			skydip.value = Double.parseDouble(tokens.nextToken());
			add(skydip);
		}
		in.close();
		
		this.fileName = fileName;
		
		System.err.println("-- " + size() + " values parsed.");
	}	
}

