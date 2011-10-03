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

import java.util.Arrays;

import util.Complex;
import util.Constant;

public class FFT {	

	// Rewritten to skip costly intermediate Complex storage...
	public static double[] convolve(double[] data, double[] beam) {
		return convolve(data, beam, false);		
	}

	public static double[] convolve(double[] A, double[] B, boolean inPlace) {
		final int n = inPlace ? A.length : getPaddedSize(A.length + B.length);
		
		final double[] a = inPlace ? A : new double[n];
		final double[] b = inPlace ? B : new double[n];

		if(!inPlace) {
			System.arraycopy(A, 0, a, 0, A.length);
			System.arraycopy(B, 0, b, 0, A.length);
		}
			
		powerRealTransform(a, FORWARD);
		powerRealTransform(b, FORWARD);
	
		a[0] *= b[0];
		a[1] *= b[1];
		
		for(int i=2; i<n; i+=2) {
			final double temp = a[i];
			final int j = i+1;
			a[i] = a[i] * a[i] - b[i] * b[i];
			a[j] = temp * b[j] + a[j] * b[i];
		}
		
		powerRealTransform(a, BACKWARD);
		
		return a;
	}

	public static double[] autoCorrelate(double[] data) { return convolve(data, data); }

	public static double[] averagePower(double[] data, int windowSize) {
		if(data.length < windowSize) return averagePower(getPadded(data, windowSize), windowSize);
		return averagePower(data, WindowFunction.getHamming(windowSize));
	}

	// Rewritten to skip costly intermediate Complex storage...
	public static double[] averagePower(double[] data, double[] w) {
		int windowSize = w.length;
		int stepSize = windowSize >> 1;
		final double[] block = new double[getPaddedSize(w.length)];
		final int nF = block.length >> 1;
		
		// Create the accumulated spectrum array
		double[] spectrum = null;
	
		int start = 0, N = 0;
		while(start + windowSize <= data.length) {
	
			for(int i=windowSize; --i >= 0; ) block[i] = w[i] * data[i+start];	
			Arrays.fill(block, windowSize, block.length, 0.0);
			powerRealTransform(block, FORWARD);
			
			if(spectrum == null) spectrum = new double[nF+1];
	
			spectrum[0] += block[0] * block[0];
			spectrum[nF] += block[1] * block[1];
			
			for(int i=nF, j=nF<<1; --i>=1; ) {
				spectrum[i] += block[--j] * block[j];
				spectrum[i] += block[--j] * block[j];
			}
	
			start += stepSize;
	
			N++;
		}
	
		// Should not use amplitude normalization here but power...
		// The spectal power per frequency component.
		double norm = (double) 1.0 / N;
	
		for(int i=spectrum.length; --i >= 0; ) spectrum[i] *= norm;
	
		return spectrum;	
	}

	// Rewritten to skip costly intermediate Complex storage...
	public static float[] averagePower(float[] data, double[] w) {
		int windowSize = w.length;
		int stepSize = windowSize >> 1;
		final float[] block = new float[getPaddedSize(w.length)];
		final int nF = block.length >> 1;
		
		// Create the accumulated spectrum array
		float[] spectrum = null;
	
		int start = 0, N = 0;
		while(start + windowSize <= data.length) {
	
			for(int i=windowSize; --i >= 0; ) block[i] = (float) w[i] * data[i+start];	
			Arrays.fill(block, windowSize, block.length, 0.0F);
			powerRealTransform(block, true);
			
			if(spectrum == null) spectrum = new float[nF + 1];
	
			spectrum[0] += block[0] * block[0];
			spectrum[nF] += block[1] * block[1];
			
			for(int i=nF, j=nF<<1; --i>=1; ) {
				spectrum[i] += block[--j] * block[j];
				spectrum[i] += block[--j] * block[j];
			}
	
			start += stepSize;
	
			N++;
		}
	
		// Should not use amplitude normalization here but power...
		// The spectal power per frequency component.
		double norm = (double) 1.0 / N;
	
		for(int i=spectrum.length; --i >= 0; ) spectrum[i] *= norm;
	
		return spectrum;	
	}

	public static Complex[] forward(double[] data) { return forward(data, getPaddedSize(data.length)); }

	public static double[][] backward(Complex[][] spectrum) {
		return null;
	}

	public static Complex[] forward(double[] data, int n) {
		double[] tdata = getPadded(data, n);
		int N = n/2;
	
		final Complex[] fdata = new Complex[N+1];
		for(int i=N+1; --i >= 0; ) fdata[i] = new Complex();
	
		uncheckedForward(tdata, fdata);
	
		return fdata;
	}

	// This performs the fft on tdata, and puts the
	// apmlitude normalized frequency data into the supplied Complex array.
	// but the tdata is also destroyed in the process...
	public static void uncheckedForward(final double[] data, final Complex[] fdata) {
		final int N = data.length>>1;
		final double norm = 1.0 / N;
	
		powerRealTransform(data, true);
	
		fdata[0].x = norm * data[0];
		fdata[N].x = norm * data[1];
	
		for(int i=N, ii=data.length; --i > 0; ) {
			fdata[i].x = norm * data[--ii];
			fdata[i].y = norm * data[--ii];
		}
	}

	// This performs the fft on tdata, and puts the
	// apmlitude normalized frequency data into the supplied Complex array.
	// but the tdata is also destroyed in the process...
	public static void uncheckedForward(final float[] data, final Complex[] fdata) {
		final int N = data.length>>1;
		final double norm = 1.0 / N;
	
		powerRealTransform(data, true);
	
		fdata[0].x = norm * data[0];
		fdata[N].x = norm * data[1];
	
		for(int i=N, ii=data.length; --i > 0; ) {
			fdata[i].x = norm * data[--ii];
			fdata[i].y = norm * data[--ii];
		}
	}

	// This performs the fft on tdata, and puts the
	// apmlitude normalized frequency data into the supplied Complex array.
	// but the tdata is also destroyed in the process...
	public static void forwardRealInplace(final double[] data) {
		powerRealTransform(data, true);
		
		final double norm = 2.0 / data.length;
		for(int i=data.length; --i >= 0; ) data[i] *= norm;
	}

	// This performs the fft on tdata, and puts the
	// apmlitude normalized frequency data into the supplied Complex array.
	// but the tdata is also destroyed in the process...
	public static void forwardRealInplace(final float[] data) {
		powerRealTransform(data, true);
		
		final double norm = 2.0 / data.length;
		for(int i=data.length; --i >= 0; ) data[i] *= norm;
	}

	public static void uncheckedBackward(final Complex[] fdata, final double[] data) {
		final int N = data.length>>1;
	
		data[0] = fdata[0].x;
		data[1] = fdata[N].x;
	
		for(int i=N, ii=data.length; --i > 0; ) {
			data[--ii] = fdata[i].x;
			data[--ii] = fdata[i].y;
		}
	
		powerRealTransform(data, false);
	}

	public static void uncheckedBackward(final Complex[] fdata, final float[] data) {
		final int N = data.length>>1;
	
		data[0] = (float) fdata[0].x;
		data[1] = (float) fdata[N].x;
	
		for(int i=N, ii=data.length; --i > 0; ) {
			data[--ii] = (float) fdata[i].x;
			data[--ii] = (float) fdata[i].y;
		}
	
		powerRealTransform(data, false);
	}

	public static void backRealInplace(double[] data) { powerRealTransform(data, false); }

	public static void backRealInplace(float[] data) { powerRealTransform(data, false); }

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
				value.x = datai[j];
				value.y = 0.0;
			}
		} 
	
		return cdata;
	}

	public static void unload(Complex[][] cdata, double[][] data) {
		int nx = data.length;
		int ny = data[0].length;
	
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) data[i][j] = cdata[i][j].x;
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

	// Loosely based on the Numberical Recipes routine realft.c
	public static void powerRealTransform(final double data[], final boolean forward) {
		if(forward) powerTransform(data, true);
		
		final int N = data.length>>1;
		
		final double uh = 0.5;
		final double sh = forward ? 0.5 : -0.5;
		final double theta = (forward ? 1 : -1) * Math.PI / N;
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);
		
		double wr = c;
		double wi = s;
	
		for(int r1=2, r2=data.length-2; r1<N; r1+=2, r2-=2) {
			final int i1 = r1 + 1;
			final int i2 = r2 + 1;
	
			double hr = sh * (data[i1] + data[i2]);
			double hi = sh * (data[r2] - data[r1]);
			final double r = wr * hr - wi * hi;
			final double i = wr * hi + wi * hr;
	
			hr = uh * (data[r1] + data[r2]);
			hi = uh * (data[i1] - data[i2]);
			
			data[r1] = hr + r;
			data[i1] = hi + i;
			data[r2] = hr - r;
			data[i2] = i - hi;
			
			final double temp = wr;
			wr = temp * c - wi * s;
			wi = wi * c + temp * s;
		}
		
		final double d0 = data[0];
	
		if(forward) {
			data[0] = d0 + data[1];
			data[1] = d0 - data[1];
		} 
		else {
			data[0] = uh * (d0 + data[1]);
			data[1] = uh * (d0 - data[1]);
			powerTransform(data, false);
		}
	}

	public static void bitReverse(final double[] data) {
		for(int i=0, j=0; i<data.length; i+=2) {
			if(j > i) {	
				double temp = data[i]; data[i] = data[j]; data[j] = temp;
				temp = data[i+1]; data[i+1] = data[j+1]; data[j+1] = temp;
			}
	
			int m = data.length >> 1;
			while(m >= 2 && j >= m) {
				j -= m;
				m >>= 1;
			}
			j += m;
		}
	}

	public static void bitReverse(final float[] data) {
		for(int i=0, j=0; i<data.length; i+=2) {
			if(j > i) {	
				float temp = data[i]; data[i] = data[j]; data[j] = temp;
				temp = data[i+1]; data[i+1] = data[j+1]; data[j+1] = temp;
			}
	
			int m = data.length >> 1;
			while(m >= 2 && j >= m) {
				j -= m;
				m >>= 1;
			}
			j += m;
		}
	}

	public static void bitReverse(final Complex[] data) {
		Complex temp = null;
		final int halfLength = data.length >> 1;
		
		for(int i=0, j=0; i<data.length; i++) {
			if (j > i) { temp = data[i]; data[i]=data[j]; data[j] = temp; }
			int k = halfLength;	
			while(k > 0 && j >= k) {
				j -= k;
				k >>= 1;
			}
			j += k;
		}	
		
	}

	// Loosely based on the Numerical Recipes routine four1.c
	public static void powerTransform(final double data[], final boolean isForward) {
		bitReverse(data);
		
		int blockSize = 2;
		final double sg2Pi = (isForward ? 1: -1) * Constant.twoPI;		
		final int N = data.length;
		
		while(blockSize < N) {
			final int step = (blockSize << 1) - 1;
			final double theta = sg2Pi / blockSize;
			final double c = Math.cos(theta);
			final double s = Math.sin(theta);
			
			double wr = 1.0;
			double wi = 0.0;
				
			for(int m=0; m<blockSize; m+=2) {
				for(int i1=m; i1<N; i1+=step) {
					final int i2 = i1 + blockSize;
					final int i2p = i2 + 1;
					
					final double d2r = data[i2];
					final double d2i = data[i2p];
					
					double x = wr * d2r - wi * d2i;
					data[i2] = data[i1] - x;
					data[i1] += x;
					
					x = wr * d2i + wi * d2r;
					data[i2p] = data[++i1] - x;
					data[i1] += x;
				}
				final double temp = wr;
				wr = temp * c - wi * s;
				wi = wi * c + temp * s;
			}
			blockSize <<= 1;
			Thread.yield();
		}
	}

	public static void powerTransform(final float[] data, final boolean isForward) {
		bitReverse(data);
		
		int blockSize = 2;
		final double sg2Pi = (isForward ? 1: -1) * Constant.twoPI;		
		final int N = data.length;
		
		while(blockSize < N) {
			final int step = (blockSize << 1) - 1;
			final double theta = sg2Pi / blockSize;
			final double c = Math.cos(theta);
			final double s = Math.sin(theta);
			
			double wr = 1.0;
			double wi = 0.0;
				
			for(int m=0; m<blockSize; m+=2) {
				final float fwr = (float) wr;
				final float fwi = (float) wi;
				
				for(int i1=m; i1<N; i1+=step) {
					final int i2 = i1 + blockSize;
					final int i2p = i2 + 1;
					
					final float d2r = data[i2];
					final float d2i = data[i2p];
					
					float x = fwr * d2r - fwi * d2i;
					data[i2] = data[i1] - x;
					data[i1] += x;
					
					x = fwr * d2i + fwi * d2r;
					data[i2p] = data[++i1] - x;
					data[i1] += x;
				}
				final double temp = wr;
				wr = temp * c - wi * s;
				wi = wi * c + temp * s;
			}
			blockSize <<= 1;
			Thread.yield();
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
					w.omegaFFT(data[i], data[i+blockSize]);
					
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

	// Loosely based on the Numberical Recipes routine realft.c
	public static void powerRealTransform(final float data[], final boolean forward) {
		if(forward) powerTransform(data, true);
		
		final int N = data.length>>1;
		
		final float uh = 0.5F;
		final float sh = forward ? 0.5F : -0.5F;
		final double theta = (forward ? 1 : -1) * Math.PI / N;
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);
		
		double wr = c;
		double wi = s;
	
		for(int r1=2, r2=data.length-2; r1<N; r1+=2, r2-=2) {
			final int i1 = r1 + 1;
			final int i2 = r2 + 1;
	
			float hr = sh * (data[i1] + data[i2]);
			float hi = sh * (data[r2] - data[r1]);
			final float r = (float) (wr * hr - wi * hi);
			final float i = (float) (wr * hi + wi * hr);
	
			hr = uh * (data[r1] + data[r2]);
			hi = uh * (data[i1] - data[i2]);
			
			data[r1] = hr + r;
			data[i1] = hi + i;
			data[r2] = hr - r;
			data[i2] = i - hi;
			
			final double temp = wr;
			wr = temp * c - wi * s;
			wi = wi * c + temp * s;
		}
		
		final float d0 = data[0];
	
		if(forward) {
			data[0] = d0 + data[1];
			data[1] = d0 - data[1];
		} 
		else {
			data[0] = uh * (d0 + data[1]);
			data[1] = uh * (d0 - data[1]);
			powerTransform(data, false);
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
	
	public static final boolean FORWARD = true;
	public static final boolean BACKWARD = true;
	
}
