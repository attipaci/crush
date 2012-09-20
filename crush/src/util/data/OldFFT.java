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
package util.data;

import util.Complex;
import util.fft.DoubleFFT;
import util.fft.FFT;
import util.fft.FloatFFT;


public class OldFFT {	

	// Rewritten to skip costly intermediate Complex storage...
	public static double[] convolve(double[] data, double[] beam) {
		return convolve(data, beam, false);		
	}

	public static double[] convolve(double[] A, double[] B, boolean inPlace) {
		final int n = inPlace ? A.length : getPaddedSize(A.length + B.length);
		
		DoubleFFT fft = new DoubleFFT();
		
		final double[] a = inPlace ? A : new double[n];
		final double[] b = inPlace ? B : new double[n];

		if(!inPlace) {
			System.arraycopy(A, 0, a, 0, A.length);
			System.arraycopy(B, 0, b, 0, A.length);
		}
			
		fft.realTransform(a, FFT.FORWARD);
		fft.realTransform(b, FFT.FORWARD);
	
		a[0] *= b[0];
		a[1] *= b[1];
		
		for(int i=2; i<n; i+=2) {
			final double temp = a[i];
			final int j = i+1;
			a[i] = a[i] * a[i] - b[i] * b[i];
			a[j] = temp * b[j] + a[j] * b[i];
		}
		
		fft.realTransform(a, FFT.BACK);
		
		return a;
	}

	public static double[] autoCorrelate(double[] data) { return convolve(data, data); }

	public static double[][] backward(Complex[][] spectrum) {
		return null;
	}

	public static void uncheckedBackward(final Complex[] fdata, final double[] data) {
		final int N = data.length>>1;
	
		data[0] = fdata[0].getX();
		data[1] = fdata[N].getX();
	
		for(int i=N, ii=data.length; --i > 0; ) {
			final Complex P = fdata[i];
			data[--ii] = P.getY();
			data[--ii] = P.getX();
		}
	
		new DoubleFFT().realTransform(data, false);
	}

	public static void uncheckedBackward(final Complex[] fdata, final float[] data) {
		final int N = data.length>>1;
	
		data[0] = (float) fdata[0].getX();
		data[1] = (float) fdata[N].getX();
	
		for(int i=N, ii=data.length; --i > 0; ) {
			final Complex P = fdata[i];
			data[--ii] = (float) P.getY();
			data[--ii] = (float) P.getX();
		}
	
		new FloatFFT().realTransform(data, false);
	}

	public static double[] backward(Complex[] spectrum) {
		return backward(spectrum, getPaddedSize(spectrum.length-1));
	}

	public static double[] backward(Complex[] spectrum, int n) {
		double[] tdata = new double[2 * n];
		uncheckedBackward(spectrum, tdata);	
		return tdata;
	}

	public static void uncheckedTransform(final Complex[] data, final boolean isForward) {
		// FFT
		powerTransform(data, isForward);
	
		// Unload the normalized spectrum
		if(!isForward) return;
		
		double norm = 1.0 / data.length;
		
		// Renormalize forward transforms to amplitudes...
		for(Complex value : data) value.scale(norm);
		
	}
	
	public static void uncheckedForward(Complex[][] data, boolean isForward) {
		Complex[] ctemp = null;
		uncheckedForward(data, isForward, ctemp);
	}

	public static void uncheckedForward(Complex[][] data, boolean isForward, Complex[] temp) {
		int nx = data.length;
		int ny = data[0].length;
	
		if(temp == null) temp = new Complex[nx];
		else if(temp.length != nx) temp = new Complex[nx];
	
		for(int i=nx; --i >= 0; ) {
			if(temp[i] == null) temp[i] = new Complex();
			uncheckedTransform(data[i], isForward);
		}
	
		for(int j=ny; --j >= 0; ) {
			for(int i=nx; --i >= 0; ) temp[i] = data[i][j];
			uncheckedTransform(temp, isForward);
			for(int i=nx; --i >= 0; ) data[i][j] = temp[i];
		}
	}

	public static Complex[][] load(double[][] data) {
		Complex[][] cdata = null;
		return load(data, cdata);
	}

	public static Complex[][] load(double[][] data, Complex[][] cdata) {
		int nx = data.length;
		int ny = data[0].length;
	
		int nu = getPaddedSize(nx);
		int nv = getPaddedSize(ny);
	
		if(cdata.length != nu || cdata[0].length != nv) cdata = null;
	
		// If result array does not exists, then create it
		if(cdata == null) {
			cdata = new Complex[nu][nv];
			for(int i=nu; --i >= 0; ) {
				final Complex[] cdatai = cdata[i];
				for(int j=nv; --j >= 0; ) cdatai[j] = new Complex();
			}
		}
		// Otherwise clear the elements not set to data values...
		else for(int i=nx; i<nu; i++) {
			final Complex[] cdatai = cdata[i];
			for(int j=ny; j<nv; j++) cdatai[j].zero();
		}
		
		// Set the data elements...
		for(int i=Math.min(nu, nx); --i >= 0; ) {
			final Complex[] cdatai = cdata[i];
			final double[] datai = data[i];
			for(int j=Math.min(nv, ny); --j >= 0; ) {
				final Complex value = cdatai[j];
				value.setX(datai[j]);
				value.setY(0.0);
			}
		} 
	
		return cdata;
	}

	public static void unload(Complex[][] cdata, double[][] data) {
		int nx = data.length;
		int ny = data[0].length;
	
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) data[i][j] = cdata[i][j].getX();
	}

	public static double[][] amplitude(Complex[][] data) {
		int nx = data.length;
		int ny = data[0].length;
	
		double[][] a = new double[nx][ny];
	
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) a[i][j] = data[i][j].length();
	
		// Change from integral to amplitide normalization
		for(int i=nx; --i >= 0; ) {
			a[i][0] *= 0.5;
			a[i][ny-1] *= 0.5;
		}
	
		for(int j=ny; --j >= 0; ) {
			a[0][j] *= 0.5;
			a[nx-1][j] *= 0.5;
		}
	
		return a;
	}

	public static double[][] norm(Complex[][] data) {
		int nx = data.length;
		int ny = data[0].length;
	
		double[][] a = new double[nx][ny];
	
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) a[i][j] = data[i][j].norm();
	
		return a;
	}

	


	
	
	/*
	public static void bitReverse(final Complex[] data) {
		int bits = 1;
		int n=data.length;
		while((n >>= 1) != 1) bits++;
		
		for(int i=0; i<data.length; i++) {
			int j = bitReverse(i, bits);
			if(j > i) {	Complex temp = data[i]; data[i] = data[j]; data[j] = temp; }
		}
	}
	*/
	
	
	// Alternative bitreverse actually moves data around...
	public static void bitReverse(final Complex[] data) {
		Complex temp = new Complex();
		int bits = 1;
		int n=data.length;
		while((n >>= 1) != 1) bits++;
		
		for(int i=0; i<data.length; i++) {
			int j = FFT.bitReverse(i, bits);
			if(j > i) {	temp.copy(data[i]); data[i].copy(data[j]); data[j].copy(data[i]); }
		}
	}
	
	
	// First element is both the zero freq and the cutoff freq!
	public static void powerTransform(final Complex data[], boolean isForward) {
		bitReverse(data);
		
		final int N = data.length;
		final double sgPi = (isForward ? 1: -1) * Math.PI;
		int blockSize = 1;
		final Complex w = new Complex();
		final Complex dw = new Complex();
		//final Complex wXj = new Complex();
	
		// Now the FFT using the Cooley-Tukey algorithm...
		while(blockSize < N) {
			final int iStep = blockSize << 1;
			final double theta = sgPi / blockSize;
	
			dw.set(Math.cos(theta), Math.sin(theta));
			w.set(1.0, 0.0);
	
			for(int k=0; k<blockSize; k++) {
				for(int i=k; i<N; i+=iStep) {
					w.mergeFFT(data[i], data[i+blockSize]);
			
					/*
					final Complex di = data[i];
					final Complex dj = data[i+blockSize];
					
					// X_j = X_i - w X_j
					dj.multiplyBy(w);
					wXj.copy(dj);
					dj.isubtract(di);
					// X_i = X_i + w X_j
					di.add(wXj);
					*/
				}
	
				w.multiplyBy(dw);
			}
			blockSize <<= 1;
			Thread.yield();
		}
	}
	

	public static int getPaddedSize(int size) {
		int n=1;	
		while(n < size) n<<=1;
		return n;
	}

	public static int getTruncatedSize(int size) {
		int n=1;	
		while(n <= size) n<<=1;
		return n>>1;
	}

	public static double[] truncate(double[] data) {
		return getPadded(data, getTruncatedSize(data.length));
	}
	
	public static float[] truncate(float[] data) {
		return getPadded(data, getTruncatedSize(data.length));
	}

	public static Complex[] truncate(Complex[] data) {
		return getPadded(data, getTruncatedSize(data.length));
	}

	public static double[] pad(double[] data) {
		return getPadded(data, getPaddedSize(data.length));
	}
	
	public static float[] pad(float[] data) {
		return getPadded(data, getPaddedSize(data.length));
	}

	public static Complex[] pad(Complex[] data) {
		return getPadded(data, getPaddedSize(data.length));
	}
	
	private static double[] getPadded(double[] data, int n) {
		if(data.length == n) return data;
		
		double[] padded = new double[n];
		int N = Math.min(data.length, n);
		System.arraycopy(data, 0, padded, 0, N);

		return padded;
	}

	private static float[] getPadded(float[] data, int n) {
		if(data.length == n) return data;
		
		float[] padded = new float[n];
		int N = Math.min(data.length, n);
		System.arraycopy(data, 0, padded, 0, N);

		return padded;
	}

	
	private static Complex[] getPadded(Complex[] data, int n) {
		if(data.length == n) return data;
		
		Complex[] padded = new Complex[n];
		int N = Math.min(data.length, n);
		System.arraycopy(data, 0, padded, 0, N);
		//for(int i=0; i<N; i++) padded[i] = (Complex) data[i].clone();

		return padded;
	}
	
	
	
	
}