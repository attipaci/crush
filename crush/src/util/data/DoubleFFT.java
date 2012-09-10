/*******************************************************************************
 * Copyright (c) 2012 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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


import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import util.Constant;
import util.Parallel;

public class DoubleFFT {
	private double sg2Pi;
	private int bits = -1;
	private int threads = -1;
	private int errorBits = 3;
	private boolean lookup = false;
	private volatile int active;
	private ThreadPoolExecutor pool;

	private double[] w;	

	class BitReverser implements Runnable {
		private double[] data;
		private int from, to;

		public BitReverser(double[] data, int from, int to) {
			this.data = data;
			this.from = from;
			this.to = to;
			checkin();
		}

		public void run() {
			bitReverse(data, from, to);
			Thread.yield();
			checkout();
		}
	}

	class Merger implements Runnable {
		private double[] data;
		private int blkbit;
		private int from, to;

		public Merger(double[] data, int blkbit, int from, int to) {
			this.data = data;
			this.blkbit = blkbit;
			this.from = from;
			this.to = to;
			checkin();
		}

		public void run() {
			merge(data, blkbit, from, to);
			Thread.yield();
			checkout();
		}
	}
	
	class Merger4 implements Runnable {
		private double[] data;
		private int blkbit;
		private int from, to;

		public Merger4(double[] data, int blkbit, int from, int to) {
			this.data = data;
			this.blkbit = blkbit;
			this.from = from;
			this.to = to;
			checkin();
		}

		public void run() {
			merge4(data, blkbit, from, to);
			Thread.yield();
			checkout();
		}
	}
	
	
	public void setLookup(boolean value) {
		this.lookup = value;
	}

	public void setErrorBits(int value) {
		if(value < 0) value = -1; // At most precise, the error bit is the unrecorded bit, i.e. -1.
		if(this.errorBits > value) w = null;
		this.errorBits = value;
	}

	private synchronized void checkin() { 
		active++;
	}

	private synchronized void checkout() {
		active--;
		notifyAll();
	}

	private synchronized void waitComplete() throws Exception {
		while(active > 0) wait();
	}

	private void prepare(int n, boolean isForward) {
		sg2Pi = (isForward ? 1: -1) * Constant.twoPI;	

		if(lookup) createTwiddleLookup(n);
		else w = null;

		if(bits > 0) if(n == 1<<(bits+1)) return;

		//System.err.print("Preparing FFT... ");

		bits = 0;
		while((n >>= 1) != 1) bits++;	

		//System.err.println("complex address bits = " + bits + ", done.");	
	}

	private void createTwiddleLookup(int n) {
		final int N = n >> 1;

		if(w != null) if(w.length != N) w = null;
		if(w != null) return;

		//System.err.println("Preparing lookup...");

		w = new double[N];

		final double theta = sg2Pi / N;
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);

		double r = 1.0;
		double i = 0.0;

		final int clcmask = getErrorMask();

		for(int k=0; k<N; k+=2) {
			// To keep to the prescribed precision of the twiddle
			// factors recalculate the sin/cos regularly...
			if((k & clcmask) == 0) {
				final double a = (k >> 1) * theta;
				r = Math.cos(a);
				i = Math.sin(a);
			}

			w[k] = r;
			w[k+1] = i;

			// Otherwise, it just propagates incrementally...
			final double temp = r;
			r = temp * c - i * s;
			i = i * c + temp * s;
		}
	}

	public int getErrorMask() {
		// 1 bit from single cycle arithmetic
		// n/2 - 1 bits from cycles
		// -1 bit from k index incrementing by 2
		// --> n/2 - 1 bits total...
		return (1 << ((errorBits+1) << 1)) - 1;
	}

	private synchronized void setParallel(int n) {
		if(threads == n) return;

		if(pool != null) pool.shutdown();
		pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(n);

		threads = n;
	}

	public synchronized void wrapup() {
		if(pool != null) pool.shutdown();
	}

	private void bitReverse(final double[] data, final int from, final int to) {
		for(int i=from; i<to; i++) {
			int j = FFT.bitReverse(i >> 1, bits) << 1;
			if(j > i) {	
				double temp = data[i]; data[i++] = data[j]; data[j++] = temp;
				temp = data[i]; data[i] = data[j]; data[j] = temp;
			}
			else i++;
		}
	}


	// Loosely based on the Numerical Recipes routine four1.c
	// See also Chu, E: Computation Oriented Parallel FFT Algorithms (COPF)
	public void powerTransform1(final double[] data, final boolean isForward, final int threads) throws Exception {
		prepare(data.length, isForward);

		new Parallel<Void>() {
			@Override
			protected void processIndex(int k, int threadCount) throws Exception {
				final double dn = (double) data.length / threadCount;
				final int from = (int)Math.round(k * dn);
				final int to = (int)Math.round((k+1) * dn);

				bitReverse(data, from, to);				
			}
		}.process(threads);

		// Make from and to always even
		// It helps with calculating the correct i1 indeces for the double2 storage...
		final double dn = (double) (data.length >> 2) / threads;
		final int[] from = new int[threads];
		final int[] to = new int[threads];
		for(int k=threads; --k >= 0; ) {
			from[k] = (int)Math.round(k * dn) << 1;
			to[k] = (int)Math.round((k+1) * dn) << 1;
			//System.err.println("### " + k + " : " + Integer.toHexString(to[k]));
		}

		int blkbit = 0;
		while(blkbit < bits) {	
			final int bb = blkbit;

			new Parallel<Void>() {
				@Override
				protected void processIndex(int k, int threadCount) throws Exception {
					merge(data, bb, from[k], to[k]);
				}
			}.process(threads);

			blkbit++;
		}

	}

	// Sequential transform
	public void powerTransform(final double[] data, final boolean isForward) {
		prepare(data.length, isForward);

		bitReverse(data, 0, data.length);
		int N = data.length >> 1;

		int blkbit = 0;
		
		while(blkbit < bits) {	
			/*if(blkbit < bits - 1) {
				merge4(data, blkbit, 0, N);
				blkbit += 2;
			}
			else*/
			merge(data, blkbit++, 0, N);
		}
	}


	// Loosely based on the Numerical Recipes routine four1.c
	// See also Chu, E: Computation Oriented Parallel FFT Algorithms (COPF)
	public synchronized void powerTransform(final double[] data, final boolean isForward, final int threads) throws Exception {
		prepare(data.length, isForward);
		setParallel(threads);

		for(int k=0; k<threads; k++) {
			final double dn = (double) data.length / threads;
			final int from = (int)Math.round(k * dn);
			final int to = (int)Math.round((k+1) * dn);
			pool.execute(new BitReverser(data, from, to));			
		}
		waitComplete();

		// Make from and to always even
		// It helps with calculating the correct i1 indeces for the double2 storage...
		final double dn = (double) (data.length >> 2) / threads;
		final int[] from = new int[threads];
		final int[] to = new int[threads];
		for(int k=threads; --k >= 0; ) {
			from[k] = (int)Math.round(k * dn) << 1;
			to[k] = (int)Math.round((k+1) * dn) << 1;
			//System.err.println("### " + k + " : " + Integer.toHexString(to[k]));
		}

		
		int blkbit = 0;
		
			
		while(blkbit < bits) {
			/*
			if(blkbit < bits - 1) {
				for(int k=0; k<threads; k++) pool.execute(new Merger4(data, blkbit, from[k], to[k]));
				blkbit += 2;
			}
			else {
			*/
				for(int k=0; k<threads; k++) pool.execute(new Merger(data, blkbit, from[k], to[k]));
				blkbit++;
			//}
			waitComplete();
		}

	}


	private void merge(final double[] data, int blkbit, int from, int to) {	
		if(lookup) mergeLookup(data, blkbit, from, to);
		else mergeOnTheFly(data, blkbit, from, to);
	}
	
	private void merge4(final double[] data, int blkbit, int from, int to) {	
		if(lookup) merge4Lookup(data, blkbit, from, to);
		else throw new UnsupportedOperationException("Not yet implemented");
	}
	

	// Merge is called with abstract data indeces.
	// Here from, to, are complex data indeces. double array indeces are 2x bigger...
	// Blockbit is the size of a merge block in bit shifts (e.g. size 2 is bit 1, size 4 is bit 2, etc.)
	// Two consecutive blocks are merged by the algorithm into one larger block...
	private void mergeOnTheFly(final double[] data, int blkbit, int from, int to) {	
		// Change from abstract index to double[] storage index (x2)
		blkbit++; 

		// The double[] block size
		final int blk = 1 << blkbit;
		final int blkmask = blk - 1;

		//System.err.println("### blk = " + blk);

		// make from and to become double indeces for i1...
		from = ((from >> blkbit) << (blkbit + 1)) + (from & blkmask);
		to = ((to >> blkbit) << (blkbit + 1)) + (to & blkmask);
		//System.err.println("###   " + data.length + ": " + from + " -- " + to);


		// <------------------ Processing Block Starts Here ------------------------>
		// 
		// This one calculates the twiddle factors on the fly, using generators,
		// much like the Numerical Recipes routine. 

		final double theta = sg2Pi / blk;
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);

		int m = (from & blkmask) >> 1;
		double r = Math.cos(m * theta);
		double i = Math.sin(m * theta);

		int clcmask = getErrorMask();

		for(int i1=from; i1<to; i1+=2) {
			// Skip over the odd blocks...
			// These are the i2 indeces...
			if((i1 & blk) != 0) {
				i1 += blk;
				r = 1.0;
				i = 0.0;
				if(i1 >= to) break;
			}

			// To keep the twiddle precision under control
			// recalculate every now and then...
			if((i1 & clcmask) == 0) {
				final double a = (i1 >> 1) * theta;
				r = Math.cos(a);
				i = Math.sin(a);				
			}

			final double d1r = data[i1];
			final double d1i = data[i1+1];

			// --------------------------------
			// i2

			final int i2 = i1 + blk;
			final double d2r = data[i2];
			final double d2i = data[i2+1];

			final double xr = r * d2r - i * d2i;
			final double xi = r * d2i + i * d2r;

			data[i2] = d1r - xr;
			data[i2+1] = d1i - xi;

			// Increment the twiddle factors...
			final double temp = r;
			r = temp * c - i * s;
			i = i * c + temp * s;

			// --------------------------------
			// i1

			data[i1] = d1r + xr;
			data[i1+1] = d1i + xi;	
		}
		// <------------------- Processing Block Ends Here ------------------------->
	}




	// Merge is called with abstract data indeces.
	// Here from, to, are complex data indeces. double array indeces are 2x bigger...
	// Blockbit is the size of a merge block in bit shifts (e.g. size 2 is bit 1, size 4 is bit 2, etc.)
	// Two consecutive blocks are merged by the algorithm into one larger block...
	private void mergeLookup(final double[] data, int blkbit, int from, int to) {	
		// Change from abstract index to double[] storage index (x2)
		blkbit++; 

		// The double[] block size
		final int blk = 1 << blkbit;
		final int blkmask = blk - 1;

		// make from and to become double indeces for i1...
		from = ((from >> blkbit) << (blkbit + 1)) + (from & blkmask);
		to = ((to >> blkbit) << (blkbit + 1)) + (to & blkmask);
		//System.err.println("###   " + data.length + ": " + from + " -- " + to);


		// <------------------ Processing Block Starts Here ------------------------>
		// 
		// This one uses a lookup table for the twiddle factors...

		final int np = bits + 1 - blkbit;
		final int mstep = 1 << np;
		int m = ((from & blkmask) >> 1) << np;

		for(int i1=from; i1<to; i1+=2) {
			// Skip over the odd blocks...
			if(m >= w.length) {
				m = 0;
				i1 += blk;
				if(i1 >= to) break;
			}

			final double d1r = data[i1];
			final double d1i = data[i1+1];

			// --------------------------------
			// w

			final double r = w[m];
			final double i = w[m+1];
			
			m += mstep;

			// -------------------------------- 
			// i2

			final int i2 = i1 + blk;
			final double d2r = data[i2];
			final double d2i = data[i2+1];

			final double xr = r * d2r - i * d2i;
			final double xi = r * d2i + i * d2r;

			data[i2] = d1r - xr;
			data[i2+1] = d1i - xi;

			// --------------------------------
			// i1
			
			data[i1] = d1r + xr;
			data[i1+1] = d1i + xi;
		
		}
		// <------------------- Processing Block Ends Here ------------------------->
	}	 
	
	
	
	// Merge is called with abstract data indeces.
	// Here from, to, are complex data indeces. double array indeces are 2x bigger...
	// Blockbit is the size of a merge block in bit shifts (e.g. size 2 is bit 1, size 4 is bit 2, etc.)
	// Two consecutive blocks are merged by the algorithm into one larger block...
	private void merge4Lookup(final double[] data, int blkbit, int from, int to) {	
		// Change from complex index to double[] storage index (x2)
		blkbit++; 

		// The double[] block size
		final int blk = 1 << blkbit;
		final int skip = 3 * blk;
		final int blkmask = blk - 1;

			
		// make from and to become double indeces for i1...
		// TODO check to make sure from & to cover full range
		from >>= 1;
		to >>= 1;
	
		from = ((from >> blkbit) << (blkbit + 2)) + (from & blkmask);
		to = ((to >> blkbit) << (blkbit + 2)) + (to & blkmask);
		//System.err.println("###   " + data.length + ": " + from + " -- " + to);


		// <------------------ Processing Block Starts Here ------------------------>
		// 
		// This one uses a lookup table for the twiddle factors...

		// TODO check m and mstep...
		final int np = bits - blkbit;
		final int mstep = 1 << np;
		int m = ((from & blkmask) >> 1) << np;

		//System.err.println("blk: " + blk);
		
		for(int i0=from; i0<to; i0+=2) {
			// Skip over the 2nd, 3rd, and 4th blocks...
			if((i0 & skip) != 0) {
				m = 0;
				i0 += skip;
				if(i0 >= to) break;
			}

			//->0:    d0 = x0
			
			final double d0r = data[i0];
			final double d0i = data[i0+1];

			//	[W]     lookup W1
			//	        (lookup/calculate W2)
			
			//System.err.println("### " + i0 + " : " + m + ":" + w.length);
			
			final double wr1 = w[m];
			final double wi1 = w[m+1];
		
			final int m2 = m << 1;
			final double wr2 = w[m2];
			final double wi2 = w[m2+1];
		
			final int m3 = m2 + m;
			final double wr3 = w[m3];
			final double wi3 = w[m3+1];
			
			m += mstep;
			
			//		1:      d1 = W1 * x1                    4[*], 2[+]
			//		2:      d2 = W2 * x2                    4[*], 2[+]
			//		3->:    d3 = W3 * x3                    4[*], 2[+]

			final int i1 = i0 + blk;
			double dr = data[i1];
			double di = data[i1+1];
			final double d1r = wr1 * dr - wi1 * di;
			final double d1i = wr1 * di + wi1 * dr;
			
			final int i2 = i1 + blk;
			dr = data[i2];
			di = data[i2+1];
			final double d2r = wr2 * dr - wi2 * di;
			final double d2i = wr2 * di + wi2 * dr;
			
			final int i3 = i2 + blk;
			dr = data[i3];
			di = data[i3+1];
			final double d3r = wr3 * dr - wi3 * di;
			final double d3i = wr3 * di + wi3 * dr;
			
			//		----------------------------
			//		-       y0 = d0 + d2
			//		-       y1 = d0 - d2
			//		-       y2 = d1 + d3
			//		-       y3 = d1 - d3                    8[+]
			//		----------------------------

			final double y0r = d0r + d2r;
			final double y0i = d0i + d2i;
			
			final double y1r = d0r - d2r;
			final double y1i = d0i - d2i;
			
			final double y2r = d1r + d3r;
			final double y2i = d1i + d3i;
			
			final double y3r = d1r - d3r;
			final double y3i = d1i - d3i;
			
			//		->3:    Y3 = y1 + i * y3
			//		1:      Y1 = y1 - i * y3
			//		2:      Y2 = y0 - y2
			//		0->:    Y0 = y0 + y2                    8[+]

			data[i3] = y1r - y3i;
			data[i3+1] = y1i + y3r;
			
			data[i1] = y1r + y3i;
			data[i1+1] = y1i - y3r;
			
			data[i2] = y0r - y2r;
			data[i2+1] = y0i - y2i;
			
			data[i0] = y0r + y2r;
			data[i0+1] = y0i + y2i;
		
			
		}
		// <------------------- Processing Block Ends Here ------------------------->
	}	 


}



