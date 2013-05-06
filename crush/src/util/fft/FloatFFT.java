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

public class FloatFFT extends FFT<float[]> implements RealFFT<float[]> {
	
	@Override
	int getOptimalThreadBits() { return 14; }
	
	
	private void bitReverse(final float[] data, final int from, final int to) {
		final int addressBits = getAddressBits(data);
		for(int i=from; i<to; i++) {
			int j = bitReverse(i >> 1, addressBits) << 1;
			if(j > i) {	
				float temp = data[i]; data[i++] = data[j]; data[j++] = temp;
				temp = data[i]; data[i] = data[j]; data[j] = temp;
			}
			else i++;
		}
	}
	

	// Sequential transform
	@Override
	void sequentialComplexTransform(final float[] data, final boolean isForward) {	
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
	void complexTransform(final float[] data, final boolean isForward, int threads) {
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
			public BitReverser(float[] data, int from, int to) { super(data, from, to); }
			@Override
			public void process(float[] data, int from, int to) { bitReverse(data, from, to); }
		}
		
		for(int i=0; i<threads; i++) queue.add(new BitReverser(data, from[i], to[i]));
		queue.process();

		int blkbit = 0;

		class Merger2 extends Task {
			private int blkbit;
			public Merger2(float[] data, int from, int to) { super(data, from, to); }
			public void setBlkBit(int blkbit) { this.blkbit = blkbit; }
			@Override
			public void process(float[] data, int from, int to) { merge2(data, from, to, isForward, blkbit); }
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
			public Merger4(float[] data, int from, int to) { super(data, from, to); }
			public void setBlkBit(int blkbit) { this.blkbit = blkbit; }
			@Override
			public void process(float[] data, int from, int to) { merge4(data, from, to, isForward, blkbit); }
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
	void merge2(final float[] data, int from, int to, final boolean isForward, int blkbit) {	
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
	
		final double s = Math.sin(theta);
		final double c = Math.cos(theta);
		
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

			
			final float fr = (float) r;
			final float fi = (float) i;
			
			final float d1r = data[i1];
			final float d1i = data[i1+1];

			// --------------------------------
			// i2

			final int i2 = i1 + blk;
			final float d2r = data[i2];
			final float d2i = data[i2+1];

			final float xr = fr * d2r - fi * d2i;
			final float xi = fr * d2i + fi * d2r;

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
	void merge4(final float[] data, int from, int to, final boolean isForward, int blkbit) {	
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
		
		final double s = Math.sin(theta);
		final double c = Math.cos(theta);
		
		int m = (from & blkmask) >> 1;
		double wr1 = Math.cos(m * theta);
		double wi1 = Math.sin(m * theta);
		
			
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
		
			final float fwr2 = fwr1 * fwr1 - fwi1 * fwi1;
			final float fwi2 = 2.0F * fwr1 * fwi1;

			final float fwr3 = fwr1 * fwr2 - fwi1 * fwi2;
			final float fwi3 = fwr1 * fwi2 + fwi1 * fwr2;

			// Increment the twiddle factors...
			final double temp = wr1;
			wr1 = temp * c - wi1 * s;
			wi1 = wi1 * c + temp * s;


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


		//System.err.println();
	}
	
	void loadReal(final float[] data, int from, int to, final boolean isForward) {
	
		to = Math.min(to, data.length);
		
		// Make from and to even indices 0...N/2
		from = Math.max(2, (from >> 2) << 1);
		to = (to >> 2) << 1;
		
		final double theta = (isForward ? 1.0 : -1.0) * twoPi / data.length;
		final double s = Math.sin(theta);
		final double c = Math.cos(theta);
		
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


	public void realTransform(final float data[], final boolean isForward) {
		updateThreads(data);
		
		// Don't make more chunks than there are processing blocks...
		int chunks = Math.min(getChunks(), data.length >> 2);
		
		if(chunks == 1) {
			sequentialRealTransform(data, isForward);
			return;
		}
		setThreads(chunks);
		if(isForward) complexTransform(data, true, chunks);


		class RealLoader extends Task {
			public RealLoader(float[] data, int from, int to) { super(data, from, to); }
			@Override
			public void process(float[] data, int from, int to) { loadReal(data, from, to, isForward); }
		}
		
		Queue queue = new Queue();
		
		double dn = (double) data.length / chunks;
		
		for(int k=chunks; --k >= 0; ) {
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
			complexTransform(data, false, chunks);
		}
		
	}

	private void sequentialRealTransform(final float[] data, final boolean isForward) {
		if(isForward) complexTransform(data, true);

		loadReal(data, 0, data.length, isForward);
		
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
	
	
	private void scale(final float[] data, final float value, int threads) {
		if(threads < 2) {
			for(int i=data.length; --i >= 0; ) data[i] *= value;
			return;
		}
		else {				
			class Scaler extends Task {		
				private float value;
				public Scaler(float[] data, int from, int to, float value) { 
					super(data, from, to); 
					this.value = value;
				}			
				@Override
				public void process(float[] data, int from, int to) {
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
	

	public void real2Amplitude(final float[] data) {
		realTransform(data, true);
		scale(data, 2.0F / data.length, getChunks());
	}
	
	
	public void amplitude2Real(final float[] data) { 
		realTransform(data, false); 
	}

	
	
	// Rewritten to skip costly intermediate Complex storage...
	@Override
	public double[] averagePower(float[] data, final double[] w) {
		int windowSize = w.length;
		int stepSize = windowSize >> 1;
		final float[] block = new float[Util.pow2ceil(w.length)];
		final int nF = block.length >> 1;
		
		// Create the accumulated spectrum array
		double[] spectrum = null;
	
		int start = 0, N = 0;
		while(start + windowSize <= data.length) {
	
			for(int i=windowSize; --i >= 0; ) block[i] = (float) w[i] * data[i+start];	
			Arrays.fill(block, windowSize, block.length, 0.0F);
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
	final int sizeOf(float[] data) { return data.length; }
	
	@Override
	final int addressSizeOf(float[] data) { return data.length>>1; }
	
	@Override
	public float[] getPadded(float[] data, int n) {
		if(data.length == n) return data;
		
		float[] padded = new float[n];
		int N = Math.min(data.length, n);
		System.arraycopy(data, 0, padded, 0, N);

		return padded;
	}
	
	@Override
	public double getMaxErrorBitsFor(float[] data) {
		// radix-4: 6 ops per 4 cycle
		// radix-2: 4 ops per 2 cycle
		
		int ops = 0;
	
		int bits = getAddressBits(data);
		if((bits & 1) != 0) {
			ops += 4;
			bits--;
		}
		while(bits > 0) {
			ops += 6;
			bits -= 2;
		}
		
		return 0.5 * Util.log2(1+ops);
		
	}

	@Override
	final int getMaxSignificantBits() {
		return 24;	
	}
	

	
}



