/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

public class ProductStore extends DataStore<Double> {
	
	DataStore<?> a,b;
	double indexScale;
	
	public ProductStore(String name, DataStore<?> a, DataStore<?> b) {
		super(name);
		this.a = a;
		this.b = b;
		indexScale = b.getSamples() / a.getSamples();
	}
	
	@Override
	public Double get(long n) throws IOException {
		return a.get(n).doubleValue() * b.get(Math.round(indexScale * n)).doubleValue();
	}

	@Override
	public int getSamples() {
		return a.getSamples();
	}

	@Override
	public long length() throws IOException {
		return a.length();
	}

}
