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
package test;

import java.util.*;

import util.*;
import util.data.FFT;
import util.data.WindowFunction;



public class FFTTest {

	public static void main(String[] args) {
		
		double[] data = new double[16];
		
		data[0] = 1.0;
		Complex[] spectrum = FFT.forward(data);
		System.out.println("delta[0]:");
		print(spectrum);
		
		
		Arrays.fill(data, 1.0);
		spectrum = FFT.forward(data);
		System.out.println("cons(1.0):");
		print(spectrum);
		
		
		for(int i=0; i<data.length; i++) data[i] = Math.cos(2.0 * Math.PI * i / data.length);
		spectrum = FFT.forward(data);
		System.out.println("cos1:");
		print(spectrum);
		
		for(int i=0; i<data.length; i++) data[i] = Math.sin(4.0 * Math.PI * i / data.length);
		spectrum = FFT.forward(data);
		System.out.println("sin2:");
		print(spectrum);		
		
		Random random = new Random();
		data = new double[1024*1024];
		for(int i=0; i<data.length; i++) data[i] = random.nextGaussian();
		double[] power = FFT.averagePower(data, WindowFunction.getHamming(16));
		System.out.println("pow:");
		print(power);		
		
		/*
		int n = args.length > 0 ? Integer.parseInt(args[0]) : 16;
		int N = n * 1024*1024;
		int ops = N * (int) (Math.log(data.length) / Math.log(2.0));
		
		float[] fdata = new float[N];
		for(int i=0; i<fdata.length; i++) fdata[i] = (float) Math.random();
		long time = -System.currentTimeMillis();
		FFT.powerTransform(fdata, true);
		time += System.currentTimeMillis();
		System.err.println("float transform of " + n + "M points: " + time + "ms --> " + Util.e3.format(1e-3*ops/time) + " Mcycles/sec");
		
	
		data = new double[N];
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		FFT.powerTransform(data, true);
		time += System.currentTimeMillis();
		System.err.println("double transform of " + n + "M points: " + time + "ms --> " + Util.e3.format(1e-3*ops/time) + " Mcycles/sec");
		
		
		Complex[] cdata = new Complex[N/2];
		for(int i=0; i<cdata.length; i++) cdata[i] = new Complex(Math.random(), Math.random());
		time = -System.currentTimeMillis();
		FFT.powerTransform(cdata, true);
		time += System.currentTimeMillis();
		System.err.println("complex transform of " + n + "M points: " + time + "ms --> " + Util.e3.format(1e-3*ops/time) + " Mcycles/sec");
		*/

		System.out.println("Inplace real transform test:");
		
		float[] fdata = new float[16];
		for(int i=0; i<fdata.length; i+=4) fdata[i] = 1.0F;
		
		System.out.println("Original array");
		print(fdata);
		
		FFT.inplaceRealForward(fdata);
		
		System.out.println("Frequency space");
		print(fdata);
		
		System.out.println("Back transform...");
		FFT.inplaceRealBackward(fdata);
		
		print(fdata);
		
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
