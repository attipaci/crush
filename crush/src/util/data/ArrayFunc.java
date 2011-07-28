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


public final class ArrayFunc {

	public static void print(double[] data) { print(data, System.out, new DecimalFormat("0.000000E0")); }

	public static void print(double[] data, DecimalFormat df) { print(data, System.out, df); }

	public static void print(double[] data, PrintStream out, DecimalFormat df) {
		for(int i=0; i<data.length; i++) out.println(df.format(data[i]));
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
