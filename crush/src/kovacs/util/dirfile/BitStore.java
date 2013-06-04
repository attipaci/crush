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

public class BitStore extends DataStore<Long> {
	DataStore<? extends Number> container;
	long mask = 0;
	int shift;
	
	public BitStore(String name, DataStore<? extends Number> bits, int position) {
		super(name);
		this.container = bits;
		mask = 1 << position; 
		shift = position;
	}
	
	public BitStore(String name, DataStore<? extends Number> bits, int from, int n) {
		super(name);
		this.container = bits;
		shift = from;
		for(int i=0; i<n; i++, from++) mask |= 1 << from;
	}
	
	@Override
	public Long get(long n) throws IOException {
		return (container.get(n).longValue() & mask) >> shift;
	}

	@Override
	public int getSamples() {
		return container.getSamples();
	}
	
	@Override
	public long length() throws IOException {
		return container.length();
	}


}
