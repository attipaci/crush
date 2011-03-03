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
// Copyright (c) 2007 Attila Kovacs 

package util.data;

import util.Unit;
import util.Util;

public class DataPoint extends WeightedPoint {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6790662017622838033L;

	public DataPoint() { super(); }

	public DataPoint(double value, double rms) {
		super(value, 1.0/(rms*rms));
	}
	
	public DataPoint(WeightedPoint template) {
		super(template);
	}
	
	public double rms() { return 1.0/Math.sqrt(weight); }

	public void setRMS(double value) { weight = 1.0 / (value * value); }
	
	public String toString(Unit unit) { return toString(this, unit); }
	
	public static String toString(WeightedPoint point, Unit unit) {
		return Util.getDecimalFormat(point.significance()).format(point.value / unit.value) 
			+ " +- " + Util.s2.format(1.0/(unit.value * Math.sqrt(point.weight))) + " " + unit.name;   
	}
	
	@Override
	public String toString() {
		return Util.getDecimalFormat(significance()).format(value) 
		+ " +- " + Util.s2.format(1.0/Math.sqrt(weight));  		
	}
}
