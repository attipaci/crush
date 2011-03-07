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

//Copyright (c) 2007 Attila Kovacs 

package util.data;

//package crush.util;

import java.io.*;
import java.text.*;
import java.util.*;

import util.Complex;


public final class ArrayUtil {

	public static void print(double[] data) { print(data, System.out, new DecimalFormat("0.000000E0")); }

	public static void print(double[] data, DecimalFormat df) { print(data, System.out, df); }

	public static void print(double[] data, PrintStream out, DecimalFormat df) {
		for(int i=0; i<data.length; i++) out.println(df.format(data[i]));
	}

	public static double median(double[] data) { return median(data, 0, data.length); }

	public static double median(double[] data, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		int n = toIndex - fromIndex;
		return n % 2 == 0 ? 0.5 * (data[fromIndex + n/2-1] + data[fromIndex + n/2]) : data[fromIndex + (n-1)/2];
	}
	
	public static double select(double[] data, double fraction, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		return data[fromIndex + (int)Math.round(fraction * (toIndex - fromIndex - 1))];
	}

	public static float median(float[] data) { return median(data, 0, data.length); }

	public static float median(float[] data, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		int n = toIndex - fromIndex;
		return n % 2 == 0 ? 0.5F * (data[fromIndex + n/2-1] + data[fromIndex + n/2]) : data[fromIndex + (n-1)/2];
	}

	public static float select(float[] data, double fraction, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		return data[fromIndex + (int)Math.floor(fraction * (toIndex - fromIndex - 1))];
	}
	
	public static double median(WeightedPoint[] data) { return median(data, 0, data.length); }
	
	public static double median(WeightedPoint[] data, int fromIndex, int toIndex) {
		return smartMedian(data, fromIndex, toIndex, 1.0);		
	}
	
	public static double smartMedian(final WeightedPoint[] data, final int fromIndex, final int toIndex, final double maxDependence) {
		if(toIndex - fromIndex == 1) return data[fromIndex].value;

		Arrays.sort(data, fromIndex, toIndex);

		// wt is the sum of all weights
		// wi is the integral sum including the current point.
		double wt = 0.0, wmax = 0.0;
		
		for(int i=fromIndex; i<toIndex; i++) {
			final double w = data[i].weight;
			if(w > wmax) wmax = w;
			if(w > 0.0) wt += w;
		}

		// If a single datum dominates, then return the weighted mean...
		if(wmax >= maxDependence * wt) {
			double sum=0.0, sumw=0.0;
			for(int i=fromIndex; i<toIndex; i++) {
				final double w = data[i].weight;
				if(w > 0.0) {
					sum += w * data[i].value;
					sumw += w;
				}
			}
			return sum/sumw;
		}
		
		// If all weights are zero return the arithmetic median...
		// This should never happen, but just in case...
		if(wt == 0.0) {
			final int n = toIndex - fromIndex;
			return n % 2 == 0 ? 0.5F * (data[fromIndex + n/2-1].value + data[fromIndex + n/2].value) : data[fromIndex + (n-1)/2].value;
		}


		final double midw = wt / 2.0; 
		int ig = fromIndex; 
		
		WeightedPoint last = WeightedPoint.NaN;
		WeightedPoint point = data[fromIndex];

		double wi = point.weight;
		
		
		while(wi < midw) if(data[++ig].weight > 0.0) {
			last = point;
			point = data[ig];	    
			wi += 0.5 * (last.weight + point.weight);    
		}
		
		double wplus = wi;
		double wminus = wi - 0.5 * (last.weight + point.weight);
		
		double w1 = (wplus - midw) / (wplus + wminus);
		return w1 * last.value + (1.0-w1) * point.value;
	}

	// Interpret integral normalized spectrum as signal amplitudes
	public static double[] amplitude(Complex[] data) {
		double[] amp = new double[data.length];
		for(int i=0; i<data.length; i++) amp[i] = data[i].length();
		amp[0] /= 2.0;
		amp[amp.length - 1] /= 2.0;
		return amp;
	}

	// Interpret integral normalized spectrum as noise amplitudes
	public static double[] noiseAmplitude(Complex[] data) {
		double[] amp = new double[data.length];
		for(int i=0; i<data.length; i++) amp[i] = data[i].length();
		return amp;
	}

	// Interpret integral normalized spectrum as signal power
	public static double[] norm(Complex[] data) {
		double[] norm = new double[data.length];
		for(int i=0; i<data.length; i++) norm[i] = data[i].norm();
		norm[0] /= 4.0;
		norm[norm.length - 1] /= 4.0;	
		return norm;
	}

	// Interpret integral normalized spectrum as noise power
	public static double[] noiseNorm(Complex[] data) {
		double[] norm = new double[data.length];
		for(int i=0; i<data.length; i++) norm[i] = data[i].norm();
		return norm;
	}

	public static double[] real(Complex[] data) {
		double[] real = new double[data.length];
		for(int i=0; i<data.length; i++) real[i] = data[i].x;
		return real;
	}

	public static double[] re(Complex[] data) { return real(data); }

	public static double[] imaginary(Complex[] data) {
		double[] imaginary = new double[data.length];
		for(int i=0; i<data.length; i++) imaginary[i] = data[i].y;
		return imaginary;
	}


	public static double[] im(Complex[] data) { return imaginary(data); }

	
	
	
	
}
