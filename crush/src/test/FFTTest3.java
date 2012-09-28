/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package test;


import util.*;
import util.data.OldFFT;

public class FFTTest3 {

	public static void main(String[] args) {
		final int repeats = 32;
		final int n = args.length > 0 ? Integer.parseInt(args[0]) : 16;
		final int N = n * 1024;
	
		final float[] fdata = new float[N];
		for(int i=0; i<fdata.length; i++) fdata[i] = (float) Math.random();
		long time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) OldFFT.powerTransform(fdata, (k & 1) == 0);
		time += System.currentTimeMillis();
		double speed = repeats / (1e-3*time);
		System.err.println("float transform of " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		
		final double[] data = new double[N];
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) OldFFT.powerTransform(data, (k & 1) == 0);
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("double transform of " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
	
		final Complex[] cdata = new Complex[N>>1];
		for(int i=0; i<cdata.length; i++) cdata[i] = new Complex(Math.random(), Math.random());
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) OldFFT.powerTransform(cdata, (k & 1) == 0);
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("complex transform of " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
	
	}

	public static void print(Complex[] data) {
		for(int i=0; i<data.length; i++) 
			System.out.println("  " + i + ":\t" + data[i].toString(Util.f6));
		System.out.println();
	}

	public static void print(double[] data) {
		for(int i=0; i<data.length; i++) 
			System.out.println("  " + i + ":\t" + Util.e6.format(data[i]));
		System.out.println();
	}

	public static void print(float[] fdata) {
		for(int i=0; i<fdata.length; i++) 
			System.out.println("  " + i + ":\t" + Util.e6.format(fdata[i]));
		System.out.println();
	}

}

