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
	
		fdata[0].setX(norm * data[0]);
		fdata[N].setX(norm * data[1]);
	
		for(int i=N, ii=data.length; --i > 0; ) {
			final Complex P = fdata[i];
			P.setY(norm * data[--ii]);
			P.setX(norm * data[--ii]);
		}
	}

	// This performs the fft on tdata, and puts the
	// apmlitude normalized frequency data into the supplied Complex array.
	// but the tdata is also destroyed in the process...
	public static void uncheckedForward(final float[] data, final Complex[] fdata) {
		final int N = data.length>>1;
		final double norm = 1.0 / N;
	
		powerRealTransform(data, true);
	
		fdata[0].setX(norm * data[0]);
		fdata[N].setX(norm * data[1]);
	
		for(int i=N, ii=data.length; --i > 0; ) {
			final Complex P = fdata[i];
			P.setY(norm * data[--ii]);
			P.setX(norm * data[--ii]);
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
	
		data[0] = fdata[0].getX();
		data[1] = fdata[N].getX();
	
		for(int i=N, ii=data.length; --i > 0; ) {
			final Complex P = fdata[i];
			data[--ii] = P.getY();
			data[--ii] = P.getX();
		}
	
		powerRealTransform(data, false);
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


	public static void bitReverse(final float[] data) {
		int bits = 0;
		int n=data.length;
		while((n >>= 1) != 1) bits++;
		
		for(int i=0; i<data.length; i++) {
			int j = bitReverse(i >> 1, bits) << 1;
				
			if(j > i) {	
				float temp = data[i]; data[i++] = data[j]; data[j++] = temp;
				temp = data[i]; data[i] = data[j]; data[j] = temp;
			}
			else i++;
	
		}
	}
	
	
	public static void bitReverse(final double[] data) {
		int bits = 0;
		int n=data.length;
		while((n >>= 1) != 1) bits++;
		
		for(int i=0; i<data.length; i++) {
			int j = bitReverse(i >> 1, bits) << 1;
			if(j > i) {	
				double temp = data[i]; data[i++] = data[j]; data[j++] = temp;
				temp = data[i]; data[i] = data[j]; data[j] = temp;
			}
			else i++;
	
		}
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
			int j = bitReverse(i, bits);
			if(j > i) {	temp.copy(data[i]); data[i].copy(data[j]); data[j].copy(data[i]); }
		}
	}
	
	
	// Loosely based on the Numerical Recipes routine four1.c
	// See also Chu, E: Computation Oriented Parallel FFT Algorithms (COPF)
	public static void powerTransform(final double data[], final boolean isForward) {
		bitReverse(data);
		
		int blockSize = 2;
		final double sg2Pi = (isForward ? 1: -1) * Constant.twoPI;		
		final int N = data.length;
		
		while(blockSize < N) {
			final int step = (blockSize << 1);
			final double theta = sg2Pi / blockSize;
			final double c = Math.cos(theta);
			final double s = Math.sin(theta);
			
			double wr = 1.0;
			double wi = 0.0;
			
			// Precalculating the sin/cos terms...
			//
			// wr = sin(2Pi (m') / blockSize)
			//    = sin(2Pi (m' * nBlocks) / N
			// --> wr[N] --> wr[m' * nBlocks]
			//
			// - 5 ops per middle loop... <-- small gain...
			// However lookup is slower than calculation... 
			
			for(int m=0; m<blockSize; m+=2) {
				for(int i1=m; i1<N; i1+=step) {
					int i2 = i1 + blockSize;
					
					final double d1r = data[i1];
					final double d1i = data[++i1];
					
					final double d2r = data[i2];
					final double d2i = data[++i2];
					
					final double xr = wr * d2r - wi * d2i;
					final double xi = wr * d2i + wi * d2r;
					
					data[i2] = d1i - xi;
					data[--i2] = d1r - xr;
					
					data[i1] += xi;
					data[--i1] += xr;
					/*
					final int i2 = i1 + blockSize;
					
					final double d2r = data[i2];
					final double d2i = data[i2+1];
					
					double x = wr * d2r - wi * d2i;
					data[i2] = data[i1] - x;
					data[i1] += x;
					
					x = wr * d2i + wi * d2r;
					data[i2+1] = data[++i1] - x;
					data[i1] += x;
					*/
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
			final int step = (blockSize << 1);
			final double theta = sg2Pi / blockSize;
			final double c = Math.cos(theta);
			final double s = Math.sin(theta);
			
			double wr = 1.0;
			double wi = 0.0;
				
			for(int m=0; m<blockSize; m+=2) {
				final float fwr = (float) wr;
				final float fwi = (float) wi;
				
				for(int i1=m; i1<N; i1+=step) {
					int i2 = i1 + blockSize;
					
					final float d1r = data[i1];
					final float d1i = data[++i1];
					
					final float d2r = data[i2];
					final float d2i = data[++i2];
					
					final float xr = fwr * d2r - fwi * d2i;
					final float xi = fwr * d2i + fwi * d2r;
					
					data[i2] = d1i - xi;
					data[--i2] = d1r - xr;
					
					data[i1] += xi;
					data[--i1] += xr;	
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
			
			final float fwr = (float) wr;
			final float fwi = (float) wi;
			
			float hr = sh * (data[i1] + data[i2]);
			float hi = sh * (data[r2] - data[r1]);
			final float r = fwr * hr - fwi * hi;
			final float i = fwr * hi + fwi * hr;
	
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
	
	public static final int bitReverse(final int i, final int bits) {
		if(bits <= 8) return br[i] >> (8 - bits);
		if(bits <= 16) return (br[(i >> 8)] | br[i & 0xFF] << 8) >> (16 - bits);
		if(bits <= 24) return (br[(i >> 16)] | br[(i >> 8) & 0xFF] << 8 | br[i & 0xFF] << 16) >> (24 - bits);
		return (br[(i >> 24)] | br[(i >> 16) & 0xFF] << 8 | br[(i >> 8) & 0xFF] << 16 | br[i & 0xFF] << 24) >> (32 - bits);
	}
	
	private static final int[] br = {
		  0x00, 0x80, 0x40, 0xC0, 0x20, 0xA0, 0x60, 0xE0, 0x10, 0x90, 0x50, 0xD0, 0x30, 0xB0, 0x70, 0xF0, 
		  0x08, 0x88, 0x48, 0xC8, 0x28, 0xA8, 0x68, 0xE8, 0x18, 0x98, 0x58, 0xD8, 0x38, 0xB8, 0x78, 0xF8, 
		  0x04, 0x84, 0x44, 0xC4, 0x24, 0xA4, 0x64, 0xE4, 0x14, 0x94, 0x54, 0xD4, 0x34, 0xB4, 0x74, 0xF4, 
		  0x0C, 0x8C, 0x4C, 0xCC, 0x2C, 0xAC, 0x6C, 0xEC, 0x1C, 0x9C, 0x5C, 0xDC, 0x3C, 0xBC, 0x7C, 0xFC, 
		  0x02, 0x82, 0x42, 0xC2, 0x22, 0xA2, 0x62, 0xE2, 0x12, 0x92, 0x52, 0xD2, 0x32, 0xB2, 0x72, 0xF2, 
		  0x0A, 0x8A, 0x4A, 0xCA, 0x2A, 0xAA, 0x6A, 0xEA, 0x1A, 0x9A, 0x5A, 0xDA, 0x3A, 0xBA, 0x7A, 0xFA,
		  0x06, 0x86, 0x46, 0xC6, 0x26, 0xA6, 0x66, 0xE6, 0x16, 0x96, 0x56, 0xD6, 0x36, 0xB6, 0x76, 0xF6, 
		  0x0E, 0x8E, 0x4E, 0xCE, 0x2E, 0xAE, 0x6E, 0xEE, 0x1E, 0x9E, 0x5E, 0xDE, 0x3E, 0xBE, 0x7E, 0xFE,
		  0x01, 0x81, 0x41, 0xC1, 0x21, 0xA1, 0x61, 0xE1, 0x11, 0x91, 0x51, 0xD1, 0x31, 0xB1, 0x71, 0xF1,
		  0x09, 0x89, 0x49, 0xC9, 0x29, 0xA9, 0x69, 0xE9, 0x19, 0x99, 0x59, 0xD9, 0x39, 0xB9, 0x79, 0xF9, 
		  0x05, 0x85, 0x45, 0xC5, 0x25, 0xA5, 0x65, 0xE5, 0x15, 0x95, 0x55, 0xD5, 0x35, 0xB5, 0x75, 0xF5,
		  0x0D, 0x8D, 0x4D, 0xCD, 0x2D, 0xAD, 0x6D, 0xED, 0x1D, 0x9D, 0x5D, 0xDD, 0x3D, 0xBD, 0x7D, 0xFD,
		  0x03, 0x83, 0x43, 0xC3, 0x23, 0xA3, 0x63, 0xE3, 0x13, 0x93, 0x53, 0xD3, 0x33, 0xB3, 0x73, 0xF3, 
		  0x0B, 0x8B, 0x4B, 0xCB, 0x2B, 0xAB, 0x6B, 0xEB, 0x1B, 0x9B, 0x5B, 0xDB, 0x3B, 0xBB, 0x7B, 0xFB,
		  0x07, 0x87, 0x47, 0xC7, 0x27, 0xA7, 0x67, 0xE7, 0x17, 0x97, 0x57, 0xD7, 0x37, 0xB7, 0x77, 0xF7, 
		  0x0F, 0x8F, 0x4F, 0xCF, 0x2F, 0xAF, 0x6F, 0xEF, 0x1F, 0x9F, 0x5F, 0xDF, 0x3F, 0xBF, 0x7F, 0xFF
		};
	
	public static final boolean FORWARD = true;
	public static final boolean BACKWARD = true;
	
	
	
	
}