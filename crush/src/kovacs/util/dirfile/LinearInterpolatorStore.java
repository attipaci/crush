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
// Copyright (c) 2010 Attila Kovacs 

package kovacs.util.dirfile;


import java.io.*;

import kovacs.util.data.SimpleInterpolator;

public class LinearInterpolatorStore extends DataStore<Double> {
	protected DataStore<?> raw;
	protected String fileName;
	protected SimpleInterpolator table;
	
	public LinearInterpolatorStore(String name, DataStore<?> value, String fileName) {
		super(name);
		this.fileName = fileName;
		raw = value;
	}

	// Load interpolation table only upon request...
	@Override
	public Double get(long n) throws IOException {
		if(table == null) load();
		return table.getValue(raw.get(n).doubleValue());
	}
	
	public void load() throws IOException {
		table = new SimpleInterpolator(fileName);
	}
	
	@Override
	public int getSamples() {
		return raw.getSamples();
	}
	
	@Override
	public long length() throws IOException {
		return raw.length();
	}
 	
}
