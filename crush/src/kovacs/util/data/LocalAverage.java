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
package kovacs.util.data;

import java.util.ArrayList;


public abstract class LocalAverage<DataType extends LocalizedData> extends ArrayList<DataType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 786022102371734003L;
	public double span = 3.0;	
		
	public int indexBefore(Locality loc) throws ArrayIndexOutOfBoundsException {
		int i = 0;
		int step = size() >> 1;

		
		if(get(0).compareTo(loc) > 0) 
			throw new ArrayIndexOutOfBoundsException("Specified point precedes lookup range.");
		
		if(get(size() - 1).compareTo(loc) < 0) 
			throw new ArrayIndexOutOfBoundsException("Specified point is beyond lookup range.");
		
		
		while(step > 0) {
			if(get(i + step).compareTo(loc) < 0) i += step;
			step >>= 1;
		}
		
		return i;
	}
	
	public double getRelativeWeight(double normalizedDistance) {
		return Math.exp(-0.5 * normalizedDistance * normalizedDistance);
	}
	
	public abstract DataType getLocalizedDataInstance();
	
	public DataType getLocalAverage(Locality loc) throws ArrayIndexOutOfBoundsException {
		return getLocalAverage(loc, null);
	}
	
	public DataType getLocalAverage(Locality loc, Object env) throws ArrayIndexOutOfBoundsException {
		int i0 = indexBefore(loc);
	
		DataType mean = getLocalizedDataInstance();
		mean.setLocality(loc);
		mean.measurements = 0;
		
		for(int i = i0; i >= 0; i--) {
			if(get(i).sortingDistanceTo(loc) > span) break;		
			DataType point = get(i);
			mean.average(point, env, getRelativeWeight(point.distanceTo(loc)));
		}
	
		for(int i = i0+1; i<size(); i++) {
			if(get(i).sortingDistanceTo(loc) > span) break;
			DataType point = get(i);
			mean.average(point, env, getRelativeWeight(point.distanceTo(loc)));
		}
			
		return mean;
		
	}
	
	
}
