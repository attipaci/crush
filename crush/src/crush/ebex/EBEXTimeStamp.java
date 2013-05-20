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

package crush.ebex;

import util.dirfile.*;
import java.io.*;

public class EBEXTimeStamp<Type extends Number> extends DataStore<Type> {
	DataStore<Type> values;
	
	public EBEXTimeStamp(DataStore<Type> values) {
		super(values.getName());
		this.values = values;
	}

	public double getDoubleValue(long n) throws IOException {
		return values.get(n).doubleValue();		
	}
	
	@Override
	public int getSamples() {
		return values.getSamples();
	}
	
	public long getLowerIndex(double timeStamp) throws IOException {
		return getLowerIndex(timeStamp, 0, values.length());
	}
	
	// return the fractional index of the first datum after the timestamp
	public long getLowerIndex(double timeStamp, long from, long to) throws IOException {
		// Do a log2(N) search for the first data point after the timestamp...
		// Do this directly on the data file without reading the whole thing...
		
		long size = to - from;
		long step = size >> 1;
		
		long index = from;
			
		while(step > 1) {
			if(index + step < size) {
				double tryValue = getDoubleValue(index + step);
				if(tryValue > timeStamp) step >>= 1;
				else index += step;
			}
			else step >>= 1;
		}
		
		return index;
	}	
	
	public double getFractionalIndex(double timeStamp) throws IOException {
		return getFractionalIndex(timeStamp, 0, values.length());
	}
		
	public double getFractionalIndex(double timeStamp, long from, long to) throws IOException {
		long below = getLowerIndex(timeStamp);
		double f = timeStamp - below;
		
		return (1.0 - f) * below + f * (below + 1);
	}
	
	public long getNearestIndex(double timeStamp) throws IOException {
		return getNearestIndex(timeStamp, 0, values.length());
	}
	
	public long getNearestIndex(double timeStamp, long from, long to) throws IOException {
		long i = getLowerIndex(timeStamp);
		double below = getDoubleValue(i);
		double above = getDoubleValue(i+1);
		
		if(timeStamp - below < above - timeStamp) return i;
		return i+1;	
	}

	@Override
	public Type get(long n) throws IOException {
		return values.get(n);
	}

	@Override
	public long length() throws IOException {
		return values.length();
	}
	
}
