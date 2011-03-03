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

package util.dirfile;

import java.io.IOException;

public class InterpolatedStore {
	DataStore<?> values;
	
	public InterpolatedStore(DataStore<?> values) {
		this.values = values;
	}
	
	public Double get(double n) throws IOException {
		long k = (long) n;
		double f = n - k;
		
		if(f == 0.0) return values.get(k).doubleValue();
		
		return (1.0 - f) * values.get(k).doubleValue() + f * values.get(k+1).doubleValue();	
	}

	public long length() throws IOException {
		return values.length();
	}

}
