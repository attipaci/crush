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

package util.fft;


import java.util.Arrays;
import util.Util;

/**
/* Split radix (2 & 4) FFT algorithms. For example, see Numerical recipes,
 * and Chu, E: Computation Oriented Parallel FFT Algorithms (COPF)
 * 
 * @author Attila Kovacs <attila@submm.caltech.edu>
 *
 */

public class DoubleFFT extends FFT<double[]> implements RealFFT<double[]> {
	private int errorBits = 3;
	
	@Override
	int getOptimalThreadBits() { return 14; }
	
	public void setErrorBits(int value) {
		if(value < 0) value = -1; // At most precise, the error bit is the unrecorded bit, i.e. -1.
		this.errorBits = value;
	}


	private int getErrorMask() {
		// 1 bit from single cycle arithmetic
		// n/2 - 1 bits from cycles
		// -1 bit from k index incrementing by 2
		// --> n/2 - 1 bits total...
		return (1 << ((errorBits+1) << 1)) - 1;
	}
	
	private void bitReverse(final double[] data, final int from, final int to) {
		final int addressBits = getAddressBits(data);
		for(int i=from; i<to; i++) {
			int j = bitReverse(i >> 1, addressBits) << 1;
			if(j > i) {	
				double temp = data[i]; data[i++] = data[j]; data[j++] = temp;
				temp = data[i]; data[i] = data[j]; data[j] = temp;
			}
			else i++;
		}
	}
	

	// Sequential transform
	@Override
	void sequentialComplexTransform(final double[] data, final boolean isForward) {	
		bitReverse(data, 0, data.length);
		
		final int addressBits = getAddressBits(data);
		int blkbit = 0;
		

		if((addressBits & 1) != 0) merge2(data, 0, data.length, isForward, blkbit++);

		while(blkbit < addressBits) {	
			merge4(data, 0, data.length, isForward, blkbit);
			blkbit += 2;
		}
	}

	
	@Override
	void complexTransform(final double[] data, final boolean isForward, int threads) throws InterruptedException {
		// Don't make more threads than there are processing blocks...
		threads = Math.min(threads, data.length >> 3);
		final int addressBits = getAddressBits(data);

		Queue queue = new Queue();
		
		// Make from and to always multiples of 8 (for radix-4 merge)
		final double dn = (double) (data.length >> 3) / threads;
		final int[] from = new int[threads];
		final int[] to = new int[threads];

		for(int k=threads; --k >= 0; ) {
			from[k] = (int)Math.round(k * dn) << 3;
			to[k] = (int)Math.round((k+1) * dn) << 3;
		}
		
		class BitReverser extends Task {
			public BitReverser(double[] data, int from, int to) { super(data, from, to); }
			@Override
			public void process(double[] data, int from, int to) { bitReverse(data, from, to); }
		}
		
		for(int i=0; i<threads; i++) queue.add(new BitReverser(data, from[i], to[i]));
		queue.process();

		int blkbit = 0;

		class Merger2 extends Task {
			private int blkbit;
			public Merger2(double[] data, int from, int to) { super(data, from, to); }
			public void setBlkBit(int blkbit) { this.blkbit = blkbit; }
			@Override
			public void process(double[] data, int from, int to) { merge2(data, from, to, isForward, blkbit); }
		}

		
		if((addressBits & 1) != 0) {
			for(int i=0; i<threads; i++) {
				Merger2 merger = new Merger2(data, from[i], to[i]);
				merger.setBlkBit(blkbit);
				queue.add(merger);
			}
			queue.process();
			blkbit++;		
		}

		
		class Merger4 extends Task {
			private int blkbit;
			public Merger4(double[] data, int from, int to) { super(data, from, to); }
			public void setBlkBit(int blkbit) { this.blkbit = blkbit; }
			@Override
			public void process(double[] data, int from, int to) { merge4(data, from, to, isForward, blkbit); }
		}

		if(blkbit < addressBits) {
			Merger4[] merger = new Merger4[threads];
			for(int i=0; i<threads; i++) merger[i] = new Merger4(data, from[i], to[i]);
		
			while(blkbit < addressBits) {
				for(int i=0; i<threads; i++) {
					merger[i].setBlkBit(blkbit);
					queue.add(merger[i]);
				}
				queue.process();
				blkbit += 2;
			}
		}

	}


	// Blockbit is the size of a merge block in bit shifts (e.g. size 2 is bit 1, size 4 is bit 2, etc.)
	// Two consecutive blocks are merged by the algorithm into one larger block...
	private void merge2(final double[] data, int from, int to, boolean isForward, int blkbit) {	
		// Change from abstract index to double[] storage index (x2)
		blkbit++; 
	
		// The double[] block size
		final int blk = 1 << blkbit;
		final int blkmask = blk - 1;
		
		// make from and to compactified indices for i1
		from >>= 1;
		to >>= 1;
		
		// convert to sprase indices for i1...
		from = ((from >> blkbit) << (blkbit + 1)) + (from & blkmask);
		to = ((to >> blkbit) << (blkbit + 1)) + (to & blkmask);
			
		
		// <------------------ Processing Block Starts Here ------------------------>
		// 
		// This one calculates the twiddle factors on the fly, using generators,
		// with precision readjustments as necessary.

		final double theta = (isForward ? 1.0 : -1.0) * twoPi / blk;
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);

		int m = (from & blkmask) >> 1;
		double r = m == 0 ? 1.0 : Math.cos(m * theta);
		double i = m == 0 ? 0.0 : Math.sin(m * theta);

		final int clcmask = getErrorMask();
	
		for(int i1=from; i1<to; i1+=2) {
			// Skip over the odd blocks...
			// These are the i2 indices...
			if((i1 & blk) != 0) {
				i1 += blk;
				if(i1 >= to) break;
				r = 1.0;
				i = 0.0;
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




	

	// Blockbit is the size of a merge block in bit shifts (e.g. size 2 is bit 1, size 4 is bit 2, etc.)
	// Two consecutive blocks are merged by the algorithm into one larger block...
	private void merge4(final double[] data, int from, int to, boolean isForward, int blkbit) {	
		// Change from abstract index to double[] storage index (x2)
		blkbit++; 

		// The double[] block size
		final int blk = 1 << blkbit;
		final int skip = 3 * blk;
		final int blkmask = blk - 1;

		// make from and to compactified indices for i1 (0...N/4)
		from >>= 2;
		to >>= 2;
		
		// convert to sprase indices for i1...
		from = ((from >> blkbit) << (blkbit + 2)) + (from & blkmask);
		to = ((to >> blkbit) << (blkbit + 2)) + (to & blkmask);
			
		
	
		// <------------------ Processing Block Starts Here ------------------------>
		// 
		// This one calculates the twiddle factors on the fly, using generators,
		// with precision readjustments as necessary.

		final double theta = (isForward ? 1.0 : -1.0) * twoPi / (blk << 1);
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);
		
		int m = (from & blkmask) >> 1;
		double wr1 = Math.cos(m * theta);
		double wi1 = Math.sin(m * theta);
		
		final int clcmask = getErrorMask();
			
		for(int i0=from; i0<to; i0 += 2) {
			// Skip over the 2nd, 3rd, and 4th blocks...
			if((i0 & skip) != 0) {
				i0 += skip;
				if(i0 >= to) break;
				wr1 = 1.0;
				wi1 = 0.0;
			}
			
			//->0:    f0 = F0

			final double f0r = data[i0];
			final double f0i = data[i0+1];
			
			// To keep the twiddle precision under control
			// recalculate every now and then...

			if((i0 & clcmask) == 0) {
				final double a = (i0 >> 1) * theta;
				wr1 = Math.cos(a);
				wi1 = Math.sin(a);				
			}
		
			final double wr2 = wr1 * wr1 - wi1 * wi1;
			final double wi2 = 2.0 * wr1 * wi1;

			final double wr3 = wr1 * wr2 - wi1 * wi2;
			final double wi3 = wr1 * wi2 + wi1 * wr2;


			//		1:      f1 = W1 * F1                    4[*], 2[+]
			//		2:      f2 = W2 * F2                    4[*], 2[+]
			//		3->:    f3 = W3 * F3                    4[*], 2[+]

			final int i1 = i0 + blk;
			final int i2 = i1 + blk;
			final int i3 = i2 + blk;
			
			double dr = data[i1];
			double di = data[i1+1];
			final double f2r = wr2 * dr - wi2 * di;
			final double f2i = wr2 * di + wi2 * dr;

			dr = data[i2];
			di = data[i2+1];
			final double f1r = wr1 * dr - wi1 * di;
			final double f1i = wr1 * di + wi1 * dr;
			
			dr = data[i3];
			di = data[i3+1];
			final double f3r = wr3 * dr - wi3 * di;
			final double f3i = wr3 * di + wi3 * dr;

			// Increment the twiddle factors...
			final double temp = wr1;
			wr1 = temp * c - wi1 * s;
			wi1 = wi1 * c + temp * s;

			//		----------------------------
			//		-       y0 = d0 + d2
			//		-       y1 = d0 - d2
			//		-       y2 = d1 + d3
			//		-       y3 = d1 - d3                    8[+]
			//		----------------------------

			double y0r = f0r - f2r;
			double y0i = f0i - f2i;
			
			double y2r = f1r - f3r;
			double y2i = f1i - f3i;
			//		->3:    Y3 = y1 -/+ i * y3
			//		1:      Y1 = y1 +/- i * y3
			//		2:      Y2 = y0 - y2
			//		0->:    Y0 = y0 + y2                    8[+]

		
			if(isForward) {
				data[i3] = y0r + y2i;
				data[i3+1] = y0i - y2r;

				data[i1] = y0r - y2i;
				data[i1+1] = y0i + y2r;
			}
			else {
				data[i3] = y0r - y2i;
				data[i3+1] = y0i + y2r;

				data[i1] = y0r + y2i;
				data[i1+1] = y0i - y2r;
			}
			
			y0r = f0r + f2r;
			y0i = f0i + f2i;

			y2r = f1r + f3r;
			y2i = f1i + f3i;
			
			data[i2] = y0r - y2r;
			data[i2+1] = y0i - y2i;

			data[i0] = y0r + y2r;
			data[i0+1] = y0i + y2i;
			
			
			
		}
		// <------------------- Processing Block Ends Here ------------------------->


		//System.err.println();
	}
	
	private void loadReal(final double[] data, int from, int to, boolean isForward) {
	
		to = Math.min(to, data.length);
		
		// Make from and to even indices 0...N/2
		from = Math.max(2, (from >> 2) << 1);
		to = (to >> 2) << 1;
		
		final double theta = (isForward ? 1.0 : -1.0) * twoPi / data.length;
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);
		final double sh = isForward ? 0.5 : -0.5;
		final int clcmask = getErrorMask();
			
		double a = (from>>1) * theta;
		double wr = from == 2 ? c : Math.cos(a);
		double wi = from == 2 ? s : Math.sin(a);

		for(int r1=from, r2=data.length-from; r1<to; r1+=2, r2-=2) {
			final int i1 = r1 + 1;
			final int i2 = r2 + 1;

			double hr = sh * (data[i1] + data[i2]);
			double hi = sh * (data[r2] - data[r1]);

			// Recalculate the twiddle factors as needed to keep the precision under control...
			if((r1 & clcmask) == 0) {
				a = (r1 >> 1) * theta;
				wr = Math.cos(a);
				wi = Math.sin(a);				
			}
	
			final double r = wr * hr - wi * hi;
			final double i = wr * hi + wi * hr;

			hr = 0.5 * (data[r1] + data[r2]);
			hi = 0.5 * (data[i1] - data[i2]);

			data[r1] = hr + r;
			data[i1] = hi + i;
			data[r2] = hr - r;
			data[i2] = i - hi;

			final double temp = wr;
			wr = temp * c - wi * s;
			wi = wi * c + temp * s;
			
		}				
	}


	public void realTransform(final double data[], final boolean isForward) throws InterruptedException {
		updateThreads(data);
		int chunks = getChunks();
		
		if(chunks == 1) {
			sequentialRealTransform(data, isForward);
			return;
		}
		setParallel(chunks);
		if(isForward) complexTransform(data, true, chunks);


		class RealLoader extends Task {
			public RealLoader(double[] data, int from, int to) { super(data, from, to); }
			@Override
			public void process(double[] data, int from, int to) { loadReal(data, from, to, isForward); }
		}
		
		Queue queue = new Queue();
		
		double dn = (double) data.length / chunks;
		
		for(int k=chunks; --k >= 0; ) {
			int from = (int)Math.round(k * dn);
			int to = (int)Math.round((k+1) * dn);
			queue.add(new RealLoader(data, from, to));
		}
		queue.process();
		
		final double d0 = data[0];

		if(isForward) {
			data[0] = d0 + data[1];
			data[1] = d0 - data[1];
		} 
		else {
			data[0] = 0.5 * (d0 + data[1]);
			data[1] = 0.5 * (d0 - data[1]);
			complexTransform(data, false, chunks);
		}
		
	}

	private void sequentialRealTransform(final double[] data, final boolean isForward) {
		if(isForward) {
			try { complexTransform(data, true); }
			catch(InterruptedException e) { e.printStackTrace(); } // This really should not happen...
		}

		loadReal(data, 0, data.length, isForward);
		
		final double d0 = data[0];

		if(isForward) {
			data[0] = d0 + data[1];
			data[1] = d0 - data[1];
		} 
		else {
			data[0] = 0.5 * (d0 + data[1]);
			data[1] = 0.5 * (d0 - data[1]);
			try { complexTransform(data, true); }
			catch(InterruptedException e) { e.printStackTrace(); } // This really should not happen...
		}
	}
	
	
	private void scale(final double[] data, final double value, int threads) throws InterruptedException {
		if(threads < 2) {
			for(int i=data.length; --i >= 0; ) data[i] *= value;
			return;
		}
		else {				
			class Scaler extends Task {		
				private double value;
				public Scaler(double[] data, int from, int to, double value) { 
					super(data, from, to); 
					this.value = value;
				}			
				@Override
				public void process(double[] data, int from, int to) {
					for(int i=from; i<to; i++) data[i] *= value; 
				}
			}
			
			Queue queue = new Queue();
			
			double dn = (double) data.length / threads;
			for(int k=threads; --k >= 0; ) {
				int from = (int)Math.round(k * dn);
				int to = (int)Math.round((k+1) * dn);
				queue.add(new Scaler(data, from, to, value));
			}
			queue.process();
		}
	}
	

	public void real2Amplitude(final double[] data) throws InterruptedException {
		realTransform(data, true);
		scale(data, 2.0 / data.length, getChunks());
	}
	
	
	public void amplitude2Real(final double[] data) throws InterruptedException { 
		realTransform(data, false); 
	}

	
	
	// Rewritten to skip costly intermediate Complex storage...
	@Override
	public double[] averagePower(double[] data, double[] w) throws InterruptedException {
		int windowSize = w.length;
		int stepSize = windowSize >> 1;
		final double[] block = new double[Util.pow2ceil(w.length)];
		final int nF = block.length >> 1;
		
		// Create the accumulated spectrum array
		double[] spectrum = null;
	
		int start = 0, N = 0;
		while(start + windowSize <= data.length) {
	
			for(int i=windowSize; --i >= 0; ) block[i] = w[i] * data[i+start];	
			Arrays.fill(block, windowSize, block.length, 0.0);
			realTransform(block, FORWARD);
			
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
		// The spectral power per frequency component.
		double norm = 1.0 / N;
	
		for(int i=spectrum.length; --i >= 0; ) spectrum[i] *= norm;
	
		return spectrum;	
	}

	@Override
	final int sizeOf(double[] data) { return data.length; }
	
	@Override
	final int addressSizeOf(double[] data) { return data.length>>1; }
	
	@Override
	public double[] getPadded(double[] data, int n) {
		if(data.length == n) return data;
		
		double[] padded = new double[n];
		int N = Math.min(data.length, n);
		System.arraycopy(data, 0, padded, 0, N);

		return padded;
	}
	

	
}



