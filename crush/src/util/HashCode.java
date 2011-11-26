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
package util;


public final class HashCode {

	public static int get(long value) {
		return (int)(value & 0xFFFF) ^ (int)((value >> 32) & 0xFFFF);
	}
	
	public static int get(float value) {
		return Float.floatToIntBits(value);
	}
	
	public static int get(double value) {
		return get(Double.doubleToLongBits(value));
	}
	
	public static int get(boolean[] values) { return get(values, 0, values.length); }
	
	public static int get(boolean[] values, int from, int to) {
		int shift = 0;
		int hash = 0;
		int current = 0;
		for(int i=from; i<to; i++, shift++) {
			if(shift == 32) {
				hash ^= current + i;
				shift = current = 0;
			}
			if(values[i]) current |= 1 << shift; 
		}
		return hash ^ current;
	}
	
	public static int get(byte[] values) { return get(values, 0, values.length); }
	
	public static int get(byte[] values, int from, int to) {
		int shift = 0;
		int hash = 0;
		int current = 0;
		for(int i=from; i<to; i++, shift+=8) {
			if(shift == 32) {
				hash ^= current + i;
				shift = current = 0;
			}
			current |= values[i] << shift; 
		}
		return hash ^ current;
		
	}
	
	public static int get(short[] values) { return get(values, 0, values.length); }
	
	public static int get(short[] values, int from, int to) {
		int shift = 0;
		int hash = 0;
		int current = 0;
		for(int i=from; i<to; i++, shift+=16) {
			if(shift == 32) {
				hash ^= current + i;
				shift = current = 0;
			}
			current |= values[i] << shift; 
		}
		return hash ^ current;
		
	}
	
	public static int get(int[] values) { return get(values, 0, values.length); }
	
	public static int get(int[] values, int from, int to) {
		int hash = 0;
		for(int i=from; i<to; i++) hash ^= values[i] + i; 
		return hash;
	}
	
	
	public static int get(long[] values) { return get(values, 0, values.length); }
	
	public static int get(long[] values, int from, int to) {
		int hash = 0;
		for(int i=from; i<to; i++) hash ^= get(values[i]) + i; 
		return hash;
	}
	
	
	public static int get(float[] values) { return get(values, 0, values.length); }
	
	public static int get(float[] values, int from, int to) {
		int hash = 0;
		for(int i=from; i<to; i++) hash ^= get(values[i]) + i; 
		return hash;		
	}
	
	public static int get(double[] values) { return get(values, 0, values.length); }
	
	public static int get(double[] values, int from, int to) {
		int hash = 0;
		for(int i=from; i<to; i++) hash ^= get(values[i]) + i; 
		return hash;		
	}
	
	public static int get(Object[] values) { return get(values, 0, values.length); }
	
	public static int get(Object[] values, int from, int to) {
		int hash = 0;
		for(int i=from; i<to; i++) hash ^= values[i].hashCode() + i; 
		return hash;		
	}
	
	// Get the first 32, the last 32, and 32 scattered
	public static int sampleFrom(boolean[] values) {
		if(values.length < 128) return get(values, 0, values.length);;
		int hash = get(values, 0, 32) ^ get(values, values.length-32, values.length);
		int step = (values.length - 64) >> 5;
		int samples = 0;
		for(int i=0, j=32; i<32; i++, j+=step) if(values[j]) samples |= 1 << i;
		return hash ^ samples;
	}
	
	// Get the first 16, last 16 and 16 scattered
	public static int sampleFrom(byte[] values) {
		if(values.length < 64) return get(values, 0, values.length);;
		int hash = get(values, 0, 16) ^ get(values, values.length-16, values.length);
		int step = (values.length - 32) >> 4;
		int samples = 0;
		for(int i=0, j=16, shift=0; i<16; i++, j+=step, shift+=8) {
			if(shift == 32) {
				hash ^= samples + (1<<i);
				shift = samples = 0;
			}
			samples |= values[j] << shift;
		}
		return hash ^ samples;
	}
	
	// Get the first 16, last 16 and 16 scattered
	public static int sampleFrom(short[] values) {
		if(values.length < 64) return get(values, 0, values.length);;
		int hash = get(values, 0, 16) ^ get(values, values.length-16, values.length);
		int step = (values.length - 32) >> 4;
		int samples = 0;
		for(int i=0, j=16, shift=0; i<16; i++, j+=step, shift+=16) {
			if(shift == 32) {
				hash ^= samples + (1<<i);
				shift = samples = 0;
			}
			samples |= values[j] << shift;
		}
		return hash ^ samples;
	}
	
	// Get the first 16, last 16 and 16 scattered
	public static int sampleFrom(int[] values) {
		if(values.length < 64) return get(values, 0, values.length);;
		int hash = get(values, 0, 16) ^ get(values, values.length-16, values.length);
		for(int i=0; i<16; i++) hash ^= values[i] + (1<<i);
		return hash;
	}
	
	// Get the first 16, last 16 and 16 scattered
	public static int sampleFrom(long[] values) {
		if(values.length < 64) return get(values, 0, values.length);;
		int hash = get(values, 0, 16) ^ get(values, values.length-16, values.length);
		for(int i=0; i<16; i++) hash ^= get(values[i]) + (1<<i);
		return hash;
	}
	
	// Get the first 16, last 16 and 16 scattered
	public static int sampleFrom(float[] values) {
		if(values.length < 64) return get(values, 0, values.length);;
		int hash = get(values, 0, 16) ^ get(values, values.length-16, values.length);
		for(int i=0; i<16; i++) hash ^= get(values[i]) + (1<<i);
		return hash;
	}
	
	// Get the first 16, last 16 and 16 scattered
	public static int sampleFrom(double[] values) {
		if(values.length < 64) return get(values, 0, values.length);;
		int hash = get(values, 0, 16) ^ get(values, values.length-16, values.length);
		for(int i=0; i<16; i++) hash ^= get(values[i]) + (1<<i);
		return hash;
	}
	
	// Get the first 16, last 16 and 16 scattered
	public static int sampleFrom(Object[] values) {
		if(values.length < 64) return get(values, 0, values.length);;
		int hash = get(values, 0, 16) ^ get(values, values.length-16, values.length);
		for(int i=0; i<16; i++) hash ^= values[i].hashCode() + (1<<i);
		return hash;
	}
	
}

