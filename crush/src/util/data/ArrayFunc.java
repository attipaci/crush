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

import util.Complex;
import util.Util;


public final class ArrayFunc {

	public static void print(double[] data) { print(data, System.out, new DecimalFormat("0.000000E0")); }

	public static void print(double[] data, DecimalFormat df) { print(data, System.out, df); }

	public static void print(double[] data, PrintStream out, DecimalFormat df) {
		for(int i=0; i<data.length; i++) out.println(df.format(data[i]));
	}

	// Interpret integral normalized spectrum as signal amplitudes
	public static double[] amplitude(Complex[] data) {
		double[] amp = new double[data.length];
		for(int i=data.length; --i >= 0; ) amp[i] = data[i].length();
		amp[0] /= 2.0;
		amp[amp.length - 1] /= 2.0;
		return amp;
	}

	// Interpret integral normalized spectrum as noise amplitudes
	public static double[] noiseAmplitude(Complex[] data) {
		double[] amp = new double[data.length];
		for(int i=data.length; --i >= 0; ) amp[i] = data[i].length();
		return amp;
	}

	// Interpret integral normalized spectrum as signal power
	public static double[] norm(Complex[] data) {
		double[] norm = new double[data.length];
		for(int i=data.length; --i >= 0; ) norm[i] = data[i].norm();
		norm[0] /= 4.0;
		norm[norm.length - 1] /= 4.0;	
		return norm;
	}

	// Interpret integral normalized spectrum as noise power
	public static double[] noiseNorm(Complex[] data) {
		double[] norm = new double[data.length];
		for(int i=data.length; --i >= 0; ) norm[i] = data[i].norm();
		return norm;
	}

	public static double[] real(Complex[] data) {
		double[] real = new double[data.length];
		for(int i=data.length; --i >= 0; ) real[i] = data[i].x;
		return real;
	}

	public static double[] re(Complex[] data) { return real(data); }

	public static double[] imaginary(Complex[] data) {
		double[] imaginary = new double[data.length];
		for(int i=data.length; --i >= 0; ) imaginary[i] = data[i].y;
		return imaginary;
	}


	public static double[] im(Complex[] data) { return imaginary(data); }

	
	public static void resample(float[] from, float[] to) {
		if(to.length == from.length) System.arraycopy(from, 0, to, 0, from.length);
		
		// Anti-alias filter as necessary...
		final float delta = (float) from.length / to.length;
		if(delta > 1.0F) from = getSmoothed(from, delta);
	
		for(int i=to.length; --i >= 0; ) to[i] = ArrayFunc.valueAt(from, i * delta);
	}
	
	
	public static synchronized float valueAt(float[] data, float index) {
		final int i0 = (int)Math.floor(index-1.0);

		final int fromi = Math.max(0, i0);
		final int toi = Math.min(data.length, i0+4);
				
		// Calculate the spline coefficients...
		float sum = 0.0F, sumw = 0.0F;
		
		for(int i=fromi; i<toi; i++) if(!Float.isNaN(data[i])) {
			final float dx = (float) Math.abs(i - index);
			final float spline = dx > 1.0F ? 
					((-0.5F * dx + 2.5F) * dx - 4.0F) * dx + 2.0F : (1.5F * dx - 2.5F) * dx * dx + 1.0F;
					
			sum += spline * data[i];
			sumw += data[i];
		}
		
		return sum / sumw;
	}
	
	public static float[] getSmoothed(float[] data, float FWHM) {
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
	
	
	public static void resample(double[] from, double[] to) {
		if(to.length == from.length) System.arraycopy(from, 0, to, 0, from.length);
		
		// Anti-alias filter as necessary...
		final double delta = (double) from.length / to.length;
		if(delta > 1.0) from = getSmoothed(from, delta);
	
		for(int i=to.length; --i >= 0; ) to[i] = ArrayFunc.valueAt(from, i * delta);
	}
	
	
	public static synchronized double valueAt(double[] data, double index) {
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
	
	public static double[] getSmoothed(double[] data, double FWHM) {
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
	
	
	
}
