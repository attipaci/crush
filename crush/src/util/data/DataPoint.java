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
// Copyright (c) 2007 Attila Kovacs 

package util.data;

import util.Unit;
import util.Util;

public class DataPoint extends WeightedPoint {
	public DataPoint() { super(); }

	public DataPoint(double value, double rms) {
		super(value, 1.0/(rms*rms));
	}
	
	public DataPoint(WeightedPoint template) {
		super(template);
	}
	
	public double rms() { return 1.0/Math.sqrt(weight()); }

	public void setRMS(double value) { setWeight(1.0 / (value * value)); }
	
	public String toString(Unit unit) { return toString(this, unit); }

	public final double significance() { return significanceOf(this); }
	
	public static double significanceOf(WeightedPoint point) {
		return Math.abs(point.value()) * Math.sqrt(point.weight());
	}
	
	public static String toString(DataPoint point, Unit unit) {
		return toString(point, unit, " +- ", " ");
	}
	
	public static String toString(DataPoint point, Unit unit, String before, String after) {
		double u = unit == null ? 1.0 : unit.value();
		double value = point.value() / u;
		double rms = point.rms() / u;
		double res = Math.pow(10.0, 2 - errorFigures + Math.floor(Math.log10(rms)));
		
		return Util.getDecimalFormat(Math.abs(value) / res, 6).format(point.value() / u) 
			+ before + Util.s[errorFigures].format(rms) + after + (unit == null ? "" : unit.name());   
	}
	
	@Override
	public String toString(String before, String after) {
		return toString(this, null, before, after);
	}
	
	public static int errorFigures = 2;
}
