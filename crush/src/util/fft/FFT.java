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

import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import util.data.WindowFunction;

public abstract class FFT<Type> {
	private ThreadPoolExecutor pool;
	private int chunksPerThread = 2;
	private boolean autoThread = false;
	
	private int lastAddressBits;
	
	int getOptimalThreadBits() { return 15; }
	
	int getMaxThreads() { return Runtime.getRuntime().availableProcessors(); }
	
	public int getChunksPerThread() { return chunksPerThread; }
	
	public void setChunksPerThread(int n) { this.chunksPerThread = n; }
	
	
	// TODO clone pools properly...
	@Override
	public Object clone() {
		try { 
			@SuppressWarnings("unchecked")
			FFT<Type> clone = (FFT<Type>) super.clone(); 
			clone.pool = null;
			return clone;
		}
		catch(CloneNotSupportedException e) { return null; }
	}
	
	
	protected class Queue {
		private Vector<Task> tasks = new Vector<Task>();
		private int active = 0;
		
		protected void add(Task task) {
			task.setQueue(this);
			tasks.add(task); 
		}
		
		protected synchronized void process() {
			active += tasks.size();
			for(Task task : tasks) pool.execute(task);
			tasks.clear();
			while(active > 0) {
				try { wait(); }
				catch(InterruptedException e) {
					System.err.println("WARNING! Unexpected interrupt.");
					e.printStackTrace();
				}
			}
		}
		
		protected synchronized void checkout() {
			active--;
			notifyAll();
		}
	}
	
	
	abstract class Task implements Runnable {
		private Queue queue;
		private Type data;
		private int from, to;
		
		public Task(Type data, int from, int to) {
			this.data = data;
			this.from = from;
			this.to = to;
		}
		
		public final void run() {
			//Thread.yield();
			process(data, from, to);
			queue.checkout();
		}
		
		protected void setQueue(Queue queue) {
			this.queue = queue;
		}
		
		public abstract void process(Type data, int from, int to);
	}
	
	
	public abstract double getMaxErrorBitsFor(Type data);
	
	abstract int getFloatingPointBits();
	
	abstract void sequentialComplexTransform(Type data, boolean isForward);
	
	abstract void complexTransform(Type data, boolean isForward, int threads);
	
	abstract int sizeOf(Type data);
	
	abstract int addressSizeOf(Type data);
	
	public final void complexForward(Type data) throws InterruptedException { complexTransform(data, FORWARD); }
	
	public final void complexBack(Type data) throws InterruptedException { complexTransform(data, BACK); }
	
	
	public double getMinPrecisionFor(Type data) {
		return Math.pow(2.0, getMaxErrorBitsFor(data)) / Math.pow(2.0, getFloatingPointBits());
	}
	
	public double getMinSignificantBits(Type data) {
		return getFloatingPointBits() - getMaxErrorBitsFor(data); 
	}

	public double getDynamicRange(Type data) {
		return -20.0 * Math.log10(getMinPrecisionFor(data));
	}


	int getAddressBits(Type data) {
		int n = addressSizeOf(data);
		if(n == 1 << lastAddressBits) return lastAddressBits;
		
		int bits = 0;
		while((n >>= 1) != 0) bits++;
		return bits;
	}
		
	
	public abstract Type getPadded(Type data, int n);
	
	abstract double[] averagePower(Type data, double[] w);
	

	public void complexTransform(Type data, boolean isForward) {
		updateThreads(data);
		int chunks = getChunks();
		if(chunks == 1) sequentialComplexTransform(data, isForward);
		else complexTransform(data, isForward, chunks);
	}
	
	void updateThreads(Type data) {	
		if(!autoThread) return;
		setParallel(getOptimalThreads(getAddressBits(data)));
		autoThread = true;
	}
	

	public double[] averagePower(Type data, int windowSize) {
		if(sizeOf(data) < windowSize) return averagePower(getPadded(data, windowSize), windowSize);
		return averagePower(data, WindowFunction.getHamming(windowSize));			
	}
	
	public int getParallel() { 
		return pool == null ? 1 : pool.getCorePoolSize();
	}
	
	public int getChunks() { 
		return pool == null ? 1 : chunksPerThread * pool.getCorePoolSize();
	}
	
	public void setSequential() {
		pool = null;
	}
	
	public void setParallel(int threads) {
		autoThread = false;
		
		if(pool != null) {
			if(pool.getCorePoolSize() != threads) {
				pool.shutdown();
				pool = null;
			}
			else return;
		}
		
		if(threads == 1) pool = null;
		else pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads);
		//pool.prestartAllCoreThreads();
	}

	public void autoThread() { autoThread = true; }
	
	public boolean isAutoThreaded() { return autoThread; }
	
	private int getOptimalThreads(int addressBits) {
		int optimalThreadBits = getOptimalThreadBits();
		if(addressBits > optimalThreadBits) return Math.min(getMaxThreads(), 1<<(addressBits - optimalThreadBits));
		else return 1;
	}
	
	@Override
	protected void finalize() throws Throwable {
		pool.shutdown();
		super.finalize();
	}
	
	public void shutdown() { 
		if(pool == null) return;
		pool.shutdown(); 
		pool = null;
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
	public static final boolean BACK = false;
	
	
	final static double twoPi = 2.0 * Math.PI;



}
 