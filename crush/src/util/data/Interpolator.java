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

import java.io.*;
import java.util.*;

import util.Util;

public abstract class Interpolator extends ArrayList<Interpolator.Data> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7962217110619389946L;
	public boolean verbose = false;
	
	public String fileName = "";
	
	public Interpolator(String fileName) throws IOException {
		read(fileName);
		if(verbose) System.err.println(getClass().getSimpleName() + "> " + size() + " records parsed.");	
		Collections.sort(this);
	}
	
	public void read(String fileName) throws IOException {
		if(fileName.equals(this.fileName)) return;
		readData(fileName);
		this.fileName = fileName;
	}
	
	protected abstract void readData(String fileName) throws IOException; 
	
	// Linear interpolation.
	// Throws Exception if MJD is outside of the interpolator range.
	public double getValue(double ordinate) throws ArrayIndexOutOfBoundsException {
		int upper = getIndexAbove(ordinate);
		
		double dt1 = ordinate - get(upper-1).ordinate;
		double dt2 = get(upper).ordinate - ordinate;
		
		return (dt2 * get(upper-1).value + dt1 * get(upper).value) / (dt1 + dt2);	
	}	
	
	public int getIndexAbove(double ordinate) throws ArrayIndexOutOfBoundsException {
		int lower = 0, upper = size()-1;
		
		if(ordinate < get(lower).ordinate || ordinate > get(upper).ordinate) 
			throw new ArrayIndexOutOfBoundsException(getClass().getSimpleName() + "> outside of interpolator range.");
		
		while(upper - lower > 1) {
			int i = (upper + lower) >> 1;
			double x = get(i).ordinate;
			if(ordinate >= x) lower = i;
			if(ordinate <= x) upper = i;
		}
		
		return upper;
	}
	

	public double getSmoothValue(double ordinate, double fwhm) throws ArrayIndexOutOfBoundsException {
		int i0 = getIndexAbove(ordinate); 
		
		double sum = 0.0, sumw = 0.0;
		Data last = get(i0);
		
		double sigma = fwhm / Util.sigmasInFWHM;
		double A = -0.5 / (sigma * sigma);
		double dt;
		
		int i = i0;
		while(i < size() && (dt = last.ordinate - ordinate) < 2 * fwhm) {
			double w = Math.exp(-A*dt*dt);
			sum += w * last.value;
			sumw += w;
			last = get(++i);
		}
		
		i = i0-1;
		last = get(i0);
		while(i >= 0 && (dt = ordinate - last.ordinate) < 2 * fwhm) {
			double w = Math.exp(A*dt*dt);
			sum += w * last.value;
			sumw += w;
			last = get(--i);
		}
		
		return sum / sumw;
	}

	public class Data implements Comparable<Interpolator.Data> {
		public double ordinate, value;
		
		public Data() {}
		
		public int compareTo(Data other) {
			return Double.compare(ordinate, other.ordinate);
		}
		
	}

	
}


