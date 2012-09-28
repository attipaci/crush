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
import java.util.Arrays;

import util.Complex;
import util.Util;


public final class Data {

	public static void print(double[] data) { print(data, System.out, new DecimalFormat("0.000000E0")); }

	public static void print(double[] data, DecimalFormat df) { print(data, System.out, df); }

	public static void print(final double[] data, final PrintStream out, final NumberFormat nf) {
		for(int i=0; i<data.length; i++) out.println(nf.format(data[i]));
	}

	// Interpret integral normalized spectrum as signal amplitudes
	public static double[] amplitude(final Complex[] data) {
		final double[] amp = new double[data.length];
		for(int i=data.length; --i >= 0; ) amp[i] = data[i].length();
		amp[0] /= 2.0;
		amp[amp.length - 1] /= 2.0;
		return amp;
	}

	// Interpret integral normalized spectrum as noise amplitudes
	public static double[] noiseAmplitude(final Complex[] data) {
		final double[] amp = new double[data.length];
		for(int i=data.length; --i >= 0; ) amp[i] = data[i].length();
		return amp;
	}

	// Interpret integral normalized spectrum as signal power
	public static double[] norm(final Complex[] data) {
		final double[] norm = new double[data.length];
		for(int i=data.length; --i >= 0; ) norm[i] = data[i].norm();
		norm[0] /= 4.0;
		norm[norm.length - 1] /= 4.0;	
		return norm;
	}

	// Interpret integral normalized spectrum as noise power
	public static double[] noiseNorm(final Complex[] data) {
		final double[] norm = new double[data.length];
		for(int i=data.length; --i >= 0; ) norm[i] = data[i].norm();
		return norm;
	}

	public static double[] real(final Complex[] data) {
		final double[] real = new double[data.length];
		for(int i=data.length; --i >= 0; ) real[i] = data[i].getX();
		return real;
	}

	public static double[] re(Complex[] data) { return real(data); }

	public static double[] imaginary(final Complex[] data) {
		final double[] imaginary = new double[data.length];
		for(int i=data.length; --i >= 0; ) imaginary[i] = data[i].getY();
		return imaginary;
	}


	public static double[] im(Complex[] data) { return imaginary(data); }

	
	public static void resample(float[] from, final float[] to) {
		if(to.length == from.length) System.arraycopy(from, 0, to, 0, from.length);
		
		// Anti-alias filter as necessary...
		final float delta = (float) from.length / to.length;
		if(delta > 1.0F) from = getSmoothed(from, delta);
	
		for(int i=to.length; --i >= 0; ) to[i] = Data.valueAt(from, i * delta);
	}
	
	
	public static synchronized float valueAt(final float[] data, final float index) {
		final int i0 = (int)Math.floor(index-1.0);

		final int fromi = Math.max(0, i0);
		final int toi = Math.min(data.length, i0+4);
				
		// Calculate the spline coefficients...
		float sum = 0.0F, sumw = 0.0F;
		
		for(int i=fromi; i<toi; i++) if(!Float.isNaN(data[i])) {
			final float dx = Math.abs(i - index);
			final float spline = dx > 1.0F ? 
					((-0.5F * dx + 2.5F) * dx - 4.0F) * dx + 2.0F : (1.5F * dx - 2.5F) * dx * dx + 1.0F;
					
			sum += spline * data[i];
			sumw += data[i];
		}
		
		return sum / sumw;
	}
	
	public static float[] getSmoothed(final float[] data, final float FWHM) {
		final double sigma = FWHM / Util.sigmasInFWHM;
		final int nbeam = 1 + 2 * (int) Math.ceil(3.0 * sigma);
		final int cbeam = nbeam / 2;
		final float[] beam = new float[nbeam];
		
		final double A = -0.5 / (sigma*sigma);
	
		for(int d=cbeam-1; --d >=0; ) beam[cbeam - d] = beam[cbeam + d] = (float) Math.exp(A * d * d);
		beam[cbeam] = 1.0F;
		
		float[] smoothed = new float[data.length];
		
		for(int i=smoothed.length; --i >= 0; ) {
			final int i0 = i - cbeam;
			final int fromi = Math.max(0, i0);
			final int toi = Math.min(data.length, i0 + nbeam);
		
			double sum = 0.0, sumw = 0.0;
			for(int i1=fromi; i1 < toi; i1++) if(!Float.isNaN(data[i1])) {
				sum += beam[i1 - i0] * data[i1];
				sumw += beam[i1 - i0];
			}
			smoothed[i] = (float) (sum / sumw);
		}
		
		return smoothed;
	}
	
	
	public static void resample(double[] from, final double[] to) {
		if(to.length == from.length) System.arraycopy(from, 0, to, 0, from.length);
		
		// Anti-alias filter as necessary...
		final double delta = (double) from.length / to.length;
		if(delta > 1.0) from = getSmoothed(from, delta);
	
		for(int i=to.length; --i >= 0; ) to[i] = Data.valueAt(from, i * delta);
	}
	
	
	public static synchronized double valueAt(final double[] data, final double index) {
		final int i0 = (int)Math.floor(index-1.0);

		final int fromi = Math.max(0, i0);
		final int toi = Math.min(data.length, i0+4);
				
		// Calculate the spline coefficients...
		double sum = 0.0, sumw = 0.0;
		
		for(int i=fromi; i<toi; i++) if(!Double.isNaN(data[i])) {
			final double dx = Math.abs(i - index);
			final double spline = dx > 1 ? 
					((-0.5 * dx + 2.5) * dx - 4.0) * dx + 2.0 : (1.5 * dx - 2.5) * dx * dx + 1.0;
					
			sum += spline * data[i];
			sumw += data[i];
		}
		
		return sum / sumw;
	}
	
	public static double[] getSmoothed(final double[] data, final double FWHM) {
		final double sigma = FWHM / Util.sigmasInFWHM;
		final int nbeam = 1 + 2 * (int) Math.ceil(3.0 * sigma);
		final int cbeam = nbeam / 2;
		final double[] beam = new double[nbeam];
		
		final double A = -0.5 / (sigma*sigma);
	
		for(int d=cbeam-1; --d >=0; ) beam[cbeam - d] = beam[cbeam + d] = Math.exp(A * d * d);
		beam[cbeam] = 1.0;
		
		final double[] smoothed = new double[data.length];
		
		for(int i=smoothed.length; --i >= 0; ) {
			final int i0 = i - cbeam;
			final int fromi = Math.max(0, i0);
			final int toi = Math.min(data.length, i0 + nbeam);
		
			double sum = 0.0, sumw = 0.0;
			for(int i1=toi, ib=toi-i0-1; --i1 >= fromi; ib--) if(!Double.isNaN(data[i1])) {
				sum += beam[ib] * data[i1];
				sumw += beam[ib];
			}
			smoothed[i] = sum / sumw;
		}
		
		return smoothed;
	}
	
	
	public static int indexOfMax(final float[] data) {
		int index=0;
		float max = 0.0F;
		for(int i=data.length; --i > 0; ) if(data[i] > max) {
			max = data[i];
			index = i;
		}	
		return index;
	}
	
	public static int indexOfMax(final double[] data) {
		int index=0;
		double max = 0.0F;
		for(int i=data.length; --i > 0; ) if(data[i] > max) {
			max = data[i];
			index = i;
		}	
		return index;
	}
	

	public static double[] boxcar(final double[] data, final int n) {
		final double[] smoothed = new double[data.length];
		double sum = 0.0;
		int pts = 0;

		for(int i=n-1; --i >= 0; ) if(!Double.isNaN(data[i])) { sum += data[i]; pts++; }

		for(int i=n,j=0; i<data.length; i++,j++) {	  
			if(!Double.isNaN(data[i])) { sum += data[i]; pts++; }
			smoothed[j] = pts > 0 ? sum / pts : Double.NaN;
			if(!Double.isNaN(data[j])) { sum -= data[j]; pts--; }
		}
		
		Arrays.fill(smoothed, data.length-n+1, data.length, Double.NaN);
	
		return smoothed;
	}

	

	public static float[] boxcar(final float[] data, final int n) {
		final float[] smoothed = new float[data.length];
		double sum = 0.0;
		int pts = 0;

		for(int i=n-1; --i >= 0; ) if(!Float.isNaN(data[i])) { sum += data[i]; pts++; }

		for(int i=n,j=0; i<data.length; i++,j++) {	  
			if(!Float.isNaN(data[i])) { sum += data[i]; pts++; }
			smoothed[j] = pts > 0 ? (float) (sum / pts) : Float.NaN;
			if(!Float.isNaN(data[j])) { sum -= data[j]; pts--; }
		}
		
		Arrays.fill(smoothed, data.length-n+1, data.length, Float.NaN);
	
		return smoothed;
	}

	
	
	public static double[] shift(final double[] data, final int n) {
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


	public static float[] shift(final float[] data, final int n) {
		if(n>0) {
			for(int to=data.length-1; to >= n; to--) data[to] = data[to-n];
			for(int i=n; --i >= 0; ) data[i] = Float.NaN;
		}
		else {
			for(int from=n; from < data.length; from++) data[from-n] = data[from];
			Arrays.fill(data, data.length-1-n, data.length, Float.NaN);
		}

		return data;
	}
	

	public static void shift(final double[] data, final double delta, final double precision) {
		int n = (int) Math.round(1.0/precision);
		int dN = (int) Math.round(delta/precision);
		
		double[] stretched = new double[n];
		resample(data, stretched);
		shift(stretched, dN);
		resample(stretched, data);
	}

	public static void shift(final float[] data, final double delta, final double precision) {
		int n = (int) Math.round(1.0/precision);
		int dN = (int) Math.round(delta/precision);
		
		float[] stretched = new float[n];
		resample(data, stretched);
		shift(stretched, dN);
		resample(stretched, data);
	}
}
