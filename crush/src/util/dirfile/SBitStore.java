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

public class SBitStore extends DataStore<Long> {
	DataStore<? extends Number> container;
	long mask = 0, cmask;
	int shift;
	
	public SBitStore(String name, DataStore<? extends Number> bits, int position) {
		super(name);
		this.container = bits;
		shift = position;
		cmask = 1; 
	}
	
	public SBitStore(String name, DataStore<? extends Number> bits, int from, int n) {
		super(name);
		this.container = bits;
		shift = from;
		for(int i=0; i<n-1; i++) mask |= 1 << i;
		cmask = 1 << (n-1);
	}
	
	@Override
	public Long get(long n) throws IOException {
		long value = container.get(n).longValue() >> shift;
		return (value & mask) - (value & cmask);
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
