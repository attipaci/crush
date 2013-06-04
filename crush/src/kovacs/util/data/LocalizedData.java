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


import kovacs.util.Metric;

public abstract class LocalizedData implements Comparable<LocalizedData>, Metric<LocalizedData> {
	public int measurements = 1;
	
	public abstract Locality getLocality();

	public abstract void setLocality(Locality loc);
	
	public void average(LocalizedData other, Object env, double relativeWeight) {
		averageWidth(other, env, relativeWeight);
		measurements += other.measurements;
	}
	
	protected abstract void averageWidth(LocalizedData other, Object env, double relativeWeight);
	
	public int compareTo(LocalizedData other) { return getLocality().compareTo(other.getLocality()); }
	
	public int compareTo(Locality loc) { return getLocality().compareTo(loc); }
	
	public double distanceTo(Locality loc) {
		return getLocality().distanceTo(loc);
	}
	
	public double distanceTo(LocalizedData other) {
		return distanceTo(other.getLocality());
	}

	public double sortingDistanceTo(Locality loc) {
		return getLocality().sortingDistanceTo(loc);
	}
	
	public double sortingDistanceTo(LocalizedData other) {
		return sortingDistanceTo(other.getLocality());
	}

	
}

