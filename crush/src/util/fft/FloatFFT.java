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

public class FloatFFT extends FFT<float[]> implements Cloneable, RealFFT<float[]> {
	private int bits = -1;

	
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}



	private void prepare(int n, boolean isForward) {	
		if(bits > 0) if(n == 1<<(bits+1)) return;
		
		bits = 0;
		while((n >>= 1) != 1) bits++;
	}


	private void bitReverse(final float[] data, final int from, final int to) {
		for(int i=from; i<to; i++) {
			int j = bitReverse(i >> 1, bits) << 1;
			if(j > i) {	
				float temp = data[i]; data[i++] = data[j]; data[j++] = temp;
				temp = data[i]; data[i] = data[j]; data[j] = temp;
			}
			else i++;
		}
	}
	

	// Sequential transform
	public void complexTransform(final float[] data, final boolean isForward) {
		prepare(data.length, isForward);

		bitReverse(data, 0, data.length);
		
		int N = data.length >> 1;

			int blkbit = 0;

			if((bits & 1) != 0) merge2(data, 0, N, isForward, blkbit++);

			while(blkbit < bits) {	
				merge4(data, 0, N, isForward, blkbit);
				blkbit += 2;
			}
	}

	// TODO measure performance of threads vs. size, to figure out what critical size to allocate extra threads for...
	public synchronized void complexTransform(final float[] data, final boolean isForward, int threads) throws InterruptedException {
		// Don't make more threads than there are processing blocks...
		threads = Math.min(threads, data.length >> 2);
		
		if(threads < 2) {
			complexTransform(data, isForward);
			return;			
		}

		prepare(data.length, isForward);
		setParallel(threads);

		Queue queue = new Queue();
		
		// Make from and to always multiples of 8 (for radix-4 merge)
		final double dn = (double) (data.length >> 3) / threads;
		final int[] from = new int[threads];
		final int[] to = new int[threads];

		for(int k=threads; --k >= 0; ) {
			from[k] = (int)Math.round(k * dn) << 2;
			to[k] = (int)Math.round((k+1) * dn) << 2;
		}
		
		class BitReverser extends Task {
			public BitReverser(float[] data, int from, int to) { super(data, from, to); }
			public void process(float[] data, int from, int to) { bitReverse(data, from, to); }
		}
		
		for(int i=0; i<threads; i++) queue.add(new BitReverser(data, from[i], to[i]));
		queue.process();

		int blkbit = 0;

		class Merger2 extends Task {
			private int blkbit;
			public Merger2(float[] data, int from, int to) { super(data, from, to); }
			public void setBlkBit(int blkbit) { this.blkbit = blkbit; }
			public void process(float[] data, int from, int to) { merge2(data, from, to, isForward, blkbit); }
		}

		
		if((bits & 1) != 0) {
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
			public Merger4(float[] data, int from, int to) { super(data, from, to); }
			public void setBlkBit(int blkbit) { this.blkbit = blkbit; }
			public void process(float[] data, int from, int to) { merge4(data, from, to, isForward, blkbit); }
		}

		if(blkbit < bits) {
			Merger4[] merger = new Merger4[threads];
			for(int i=0; i<threads; i++) merger[i] = new Merger4(data, from[i], to[i]);
		
			while(blkbit < bits) {
				for(int i=0; i<threads; i++) {
					merger[i].setBlkBit(blkbit);
					queue.add(merger[i]);
				}
				queue.process();
				blkbit += 2;
			}
		}

	}



	// Merge is called with abstract data indices.
	// Here from, to, are complex data indices. double array indices are 2x bigger...
	// Blockbit is the size of a merge block in bit shifts (e.g. size 2 is bit 1, size 4 is bit 2, etc.)
	// Two consecutive blocks are merged by the algorithm into one larger block...
	private void merge2(final float[] data, int from, int to, boolean isForward, int blkbit) {	
		// Change from abstract index to double[] storage index (x2)
		blkbit++; 

		// The double[] block size
		final int blk = 1 << blkbit;
		final int blkmask = blk - 1;

		// make from and to become double indeces for i1...
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


		for(int i1=from; i1<to; i1+=2) {
			// Skip over the odd blocks...
			// These are the i2 indices...
			if((i1 & blk) != 0) {
				i1 += blk;
				if(i1 >= to) break;
				r = 1.0;
				i = 0.0;
			}

			final float d1r = data[i1];
			final float d1i = data[i1+1];

			// --------------------------------
			// i2

			final int i2 = i1 + blk;
			final float d2r = data[i2];
			final float d2i = data[i2+1];

			final float wr = (float) r;
			final float wi = (float) i;
			
			final float xr = wr * d2r - wi * d2i;
			final float xi = wr * d2i + wi * d2r;

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




	
	
	// Merge is called with abstract data indices.
	// Here from, to, are complex data indices. double array indices are 2x bigger...
	// Blockbit is the size of a merge block in bit shifts (e.g. size 2 is bit 1, size 4 is bit 2, etc.)
	// Two consecutive blocks are merged by the algorithm into one larger block...
	private void merge4(final float[] data, int from, int to, boolean isForward, int blkbit) {
		// Change from abstract index to double[] storage index (x2)
		blkbit++; 

		// The double[] block size
		final int blk = 1 << blkbit;
		final int skip = 3 * blk;
		final int blkmask = blk - 1;

		// make from and to become double indices for i1...
		from >>= 1;
		to >>= 1;

		// make from and to become double indices for i1...
		from = ((from >> blkbit) << (blkbit + 1)) + (from & blkmask);
		to = ((to >> blkbit) << (blkbit + 1)) + (to & blkmask);
		

		// <------------------ Processing Block Starts Here ------------------------>
		// 
		// This one calculates the twiddle factors on the fly, using generators,
		// with precision readjustments as necessary.

		final double theta = (isForward ? 1.0 : -1.0) * twoPi / (blk << 1);
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);
		
		int m = (from & blkmask) >> 1;
		double wr1 = m == 0 ? 1.0 : Math.cos(m * theta);
		double wi1 = m == 0 ? 0.0 : Math.sin(m * theta);
	
		for(int i0=from; i0<to; i0 += 2) {
			// Skip over the 2nd, 3rd, and 4th blocks...
			if((i0 & skip) != 0) {
				i0 += skip;
				if(i0 >= to) break;
				wr1 = 1.0;
				wi1 = 0.0;
			}

			//->0:    f0 = F0

			final float f0r = data[i0];
			final float f0i = data[i0+1];
			
			// To keep the twiddle precision under control
			// recalculate every now and then...
			
			final float fwr1 = (float) wr1;
			final float fwi1 = (float) wi1;

			// Increment the twiddle factors...
			final double temp = wr1;
			wr1 = temp * c - wi1 * s;
			wi1 = wi1 * c + temp * s;
			
			final float fwr2 = fwr1 * fwr1 - fwi1 * fwi1;
			final float fwi2 = 2.0F * fwr1 * fwi1;

			final float fwr3 = fwr1 * fwr2 - fwi1 * fwi2;
			final float fwi3 = fwr1 * fwi2 + fwi1 * fwr2;


			//		1:      f1 = W1 * F1                    4[*], 2[+]
			//		2:      f2 = W2 * F2                    4[*], 2[+]
			//		3->:    f3 = W3 * F3                    4[*], 2[+]

			final int i1 = i0 + blk;
			final int i2 = i1 + blk;
			final int i3 = i2 + blk;
			
			float dr = data[i1];
			float di = data[i1+1];
			final float f2r = fwr2 * dr - fwi2 * di;
			final float f2i = fwr2 * di + fwi2 * dr;

			dr = data[i2];
			di = data[i2+1];
			final float f1r = fwr1 * dr - fwi1 * di;
			final float f1i = fwr1 * di + fwi1 * dr;
			
			dr = data[i3];
			di = data[i3+1];
			final float f3r = fwr3 * dr - fwi3 * di;
			final float f3i = fwr3 * di + fwi3 * dr;

			

			//		----------------------------
			//		-       y0 = d0 + d2
			//		-       y1 = d0 - d2
			//		-       y2 = d1 + d3
			//		-       y3 = d1 - d3                    8[+]
			//		----------------------------

			float y0r = f0r - f2r;
			float y0i = f0i - f2i;
			
			float y2r = f1r - f3r;
			float y2i = f1i - f3i;
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


	}
	
	// Called with complex indices...
	private void loadReal(final float[] data, int from, int to, boolean isForward) {
		
		from = Math.max(from, 2);
		to = Math.min(to, data.length);
		
		// Make from and to even...
		from &= ~1;
		to &= ~1;
		
		final double theta = (isForward ? 1.0 : -1.0) * twoPi / data.length;
		final double c = Math.cos(theta);
		final double s = Math.sin(theta);
		final float sh = isForward ? 0.5F : -0.5F;
			
		double a = (from>>1) * theta;
		double wr = from == 2 ? c : Math.cos(a);
		double wi = from == 2 ? s : Math.sin(a);

		for(int r1=from, r2=data.length-from; r1<to; r1+=2, r2-=2) {
			final int i1 = r1 + 1;
			final int i2 = r2 + 1;

			float hr = sh * (data[i1] + data[i2]);
			float hi = sh * (data[r2] - data[r1]);

			final float fwr = (float) wr;
			final float fwi = (float) wi;
			
			final float r = fwr * hr - fwi * hi;
			final float i = fwr * hi + fwi * hr;

			hr = 0.5F * (data[r1] + data[r2]);
			hi = 0.5F * (data[i1] - data[i2]);

			data[r1] = hr + r;
			data[i1] = hi + i;
			data[r2] = hr - r;
			data[i2] = i - hi;

			final double temp = wr;
			wr = temp * c - wi * s;
			wi = wi * c + temp * s;
			
		}				
	}

	public synchronized void realTransform(final float data[], final boolean isForward, int threads) throws InterruptedException {
		if(threads < 2) {
			realTransform(data, isForward);
			return;
		}
		
		prepare(data.length, isForward);
		setParallel(threads);
		
		if(isForward) complexTransform(data, true, threads);


		class RealLoader extends Task {
			public RealLoader(float[] data, int from, int to) { super(data, from, to); }
			public void process(float[] data, int from, int to) { loadReal(data, from, to, isForward); }
		}
		
		Queue queue = new Queue();
		
		double dn = (double) (data.length>>1) / threads;
		
		for(int k=threads; --k >= 0; ) {
			int from = (int)Math.round(k * dn);
			int to = (int)Math.round((k+1) * dn);
			queue.add(new RealLoader(data, from, to));
		}
		queue.process();
		
		final float d0 = data[0];

		if(isForward) {
			data[0] = d0 + data[1];
			data[1] = d0 - data[1];
		} 
		else {
			data[0] = 0.5F * (d0 + data[1]);
			data[1] = 0.5F * (d0 - data[1]);
			complexTransform(data, false, threads);
		}
		
	}

	public void realTransform(final float[] data, final boolean isForward) {
		prepare(data.length, isForward);
		
		if(isForward) complexTransform(data, true);

		loadReal(data, 0, data.length>>1, isForward);
		
		final float d0 = data[0];

		if(isForward) {
			data[0] = d0 + data[1];
			data[1] = d0 - data[1];
		} 
		else {
			data[0] = 0.5F * (d0 + data[1]);
			data[1] = 0.5F * (d0 - data[1]);
			complexTransform(data, false);
		}
	}
	
	
	private void scale(final float[] data, final float value, int threads) throws InterruptedException {
		if(threads < 2) for(int i=data.length; --i >= 0; ) data[i] *= value;
		else {				
			class Scaler extends Task {		
				public Scaler(float[] data, int from, int to) { super(data, from, to); }			
				public void process(float[] data, int from, int to) {
					for(int i=from; i<to; i++) data[i] *= value; 
				}
			}
			
			Queue queue = new Queue();
			
			double dn = (double) data.length / getParallel();
			for(int k=threads; --k >= 0; ) {
				int from = (int)Math.round(k * dn);
				int to = (int)Math.round((k+1) * dn);
				queue.add(new Scaler(data, from, to));
			}
			queue.process();
		}
	}
	
	
	public void real2Amplitude(final float[] data) {
		realTransform(data, true);
		final float scale = 2.0F / data.length;
		for(int i=data.length; --i >= 0; ) data[i] *= scale;
	}
	

	public void amplitude2Real(final float[] data) { 
		realTransform(data, false); 
	}

	public void real2Amplitude(final float[] data, int threads) throws InterruptedException {
		realTransform(data, true, threads);
		scale(data, 2.0F / data.length, threads);
	}
	
	
	public void amplitude2Real(final float[] data, int threads) throws InterruptedException { 
		realTransform(data, false, threads); 	
	}
	
	
	// Rewritten to skip costly intermediate Complex storage...
	public double[] averagePower(float[] data, double[] w, int threads) throws InterruptedException {
		int windowSize = w.length;
		int stepSize = windowSize >> 1;
		final float[] block = new float[Util.pow2ceil(w.length)];
		final int nF = block.length >> 1;
		
		// Create the accumulated spectrum array
		double[] spectrum = null;
	
		int start = 0, N = 0;
		while(start + windowSize <= data.length) {
	
			for(int i=windowSize; --i >= 0; ) block[i] = (float)w[i] * data[i+start];	
			Arrays.fill(block, windowSize, block.length, 0.0F);
			realTransform(block, FORWARD, threads);
			
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
		double norm = (double) 1.0 / N;
		for(int i=spectrum.length; --i >= 0; ) spectrum[i] *= norm;
	
		return spectrum;	
	}

	public final int sizeOf(float[] data) { return data.length; }
	
	public float[] getPadded(float[] data, int n) {
		if(data.length == n) return data;
		
		float[] padded = new float[n];
		int N = Math.min(data.length, n);
		System.arraycopy(data, 0, padded, 0, N);

		return padded;
	}
	
	private final static double twoPi = 2.0 * Math.PI;

}



