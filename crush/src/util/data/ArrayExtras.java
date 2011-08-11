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

import java.util.Arrays;

public final class ArrayExtras {

	public static double[] stretch(double[] data, int n) {
		double[] stretched = new double[data.length * n];
		for(int i=data.length; --i >= 0; ) stretched[n*i] = data[i];
		return stretched;
	}

	public static double[] shrink(double[] data, int n) {
		double[] shrunk = new double[data.length/n];
		for(int i=shrunk.length; --i >= 0; ) shrunk[i] = data[n*i];
		return shrunk;
	}

	public static double[] boxcar(double[] data, int n) {
		double[] smoothed = new double[data.length];
		double sum = 0.0;
		int nans = 0;

		for(int i=n; --i >= 0; ) {
			if(Double.isNaN(data[i])) nans++;
			else sum += data[i];
		}

		for(int i=n,j=0; i<data.length; i++,j++) {	  
			smoothed[j] = nans > 0 ? Double.NaN : sum;

			if(Double.isNaN(data[i])) nans++;
			else sum += data[i];

			if(Double.isNaN(data[j])) nans--;
			else sum -= data[j];
		}
		smoothed[data.length-n] = sum;

		Arrays.fill(smoothed, data.length-n+1, data.length, Double.NaN);
	
		return smoothed;
	}

	public static double[] shift(double[] data, int n) {
		if(n>0) {
			for(int to=data.length-1; to >= n; to--) data[to] = data[to-n];
			for(int i=n; --i >= 0; ) data[i] = Double.NaN;
		}
		else {
			for(int from=n; from < data.length; from++) data[from-n] = data[from];
			Arrays.fill(data, data.length-1-n, data.length, Double.NaN);
		}

		return data;
	}


	public static double[] shift(double[] data, double d, double accuracy) {
		int n = (int) Math.round(1.0/accuracy);
		int dN = (int) Math.round(d/accuracy);
		return shrink(shift(boxcar(stretch(data, n), n), dN), n);
	}

}
