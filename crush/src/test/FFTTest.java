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
import util.data.OldFFT;
import util.data.WindowFunction;



public class FFTTest {

	public static void main(String[] args) {
		
		double[] data = new double[16];
		
		data[0] = 1.0;
		Complex[] spectrum = OldFFT.forward(data);
		System.err.println("delta[0]:");
		print(spectrum);
		
		Arrays.fill(data, 1.0);
		spectrum = OldFFT.forward(data);
		System.err.println("const(1.0):");
		print(spectrum);
		
		
		for(int i=0; i<data.length; i++) data[i] = Math.cos(2.0 * Math.PI * i / data.length);
		spectrum = OldFFT.forward(data);
		System.err.println("cos1:");
		print(spectrum);
		
		for(int i=0; i<data.length; i++) data[i] = Math.sin(4.0 * Math.PI * i / data.length);
		spectrum = OldFFT.forward(data);
		System.err.println("sin2:");
		print(spectrum);		
		
		Random random = new Random();
		data = new double[1024*1024];
		for(int i=0; i<data.length; i++) data[i] = random.nextGaussian();
		double[] power = OldFFT.averagePower(data, WindowFunction.getHamming(16));
		System.err.println("pow:");
		print(power);		
		
		System.err.println("Inplace real transform test:");
		
		float[] fdata = new float[16];
		fdata[0] = 1.0F;
		//for(int i=0; i<fdata.length; i+=4) fdata[i] = 1.0F;
		
		System.err.println("Original array");
		print(fdata);
		
		OldFFT.forwardRealInplace(fdata);
		
		System.err.println("Frequency space");
		print(fdata);
		
		System.err.println("Back transform...");
		OldFFT.backRealInplace(fdata);
		
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
