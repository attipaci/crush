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

public final class Statistics {

	public static double median(double[] data) { return median(data, 0, data.length); }

	public static double median(double[] data, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		int n = toIndex - fromIndex;
		return n % 2 == 0 ? 0.5 * (data[fromIndex + n/2-1] + data[fromIndex + n/2]) : data[fromIndex + (n-1)/2];
	}

	public static float median(float[] data) { return median(data, 0, data.length); }

	public static float median(float[] data, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		int n = toIndex - fromIndex;
		return n % 2 == 0 ? 0.5F * (data[fromIndex + n/2-1] + data[fromIndex + n/2]) : data[fromIndex + (n-1)/2];
	}

	public static WeightedPoint median(WeightedPoint[] data) { return median(data, 0, data.length); }
	
	public static void median(WeightedPoint[] data, WeightedPoint result) { median(data, 0, data.length, result); }

	public static WeightedPoint median(WeightedPoint[] data, int fromIndex, int toIndex) {
		return smartMedian(data, fromIndex, toIndex, 1.0);		
	}
	
	public static void median(WeightedPoint[] data, int fromIndex, int toIndex, WeightedPoint result) {
		smartMedian(data, fromIndex, toIndex, 1.0, result);		
	}
	
	public static WeightedPoint smartMedian(final WeightedPoint[] data, final int fromIndex, final int toIndex, final double maxDependence) {
		WeightedPoint result = new WeightedPoint();
		smartMedian(data, fromIndex, toIndex, maxDependence, result);
		return result;		
	}

	public static void smartMedian(final WeightedPoint[] data, final int from, final int to, final double maxDependence, WeightedPoint result) {
		// If no data, then 
		if(to == from) {
			result.noData();
			return;
		}
		
		if(to - from == 1) {
			result.copy(data[from]);
			return;
		}
	
		Arrays.sort(data, from, to);
	
		// wt is the sum of all weights
		// wi is the integral sum including the current point.
		double wt = 0.0, wmax = 0.0;
		
		for(int i=to; --i >= from; ) {
			final double w = data[i].weight;
			if(w > wmax) wmax = w;
			if(w > 0.0) wt += w;
		}
	
		// If a single datum dominates, then return the weighted mean...
		if(wmax >= maxDependence * wt) {
			double sum=0.0, sumw=0.0;
			for(int i = to; --i >= from; ) {
				final double w = data[i].weight;
				if(w > 0.0) {
					sum += w * data[i].value;
					sumw += w;
				}
			}
			result.value = sum/sumw;
			result.weight = sumw;
			return;
		}
		
		// If all weights are zero return the arithmetic median...
		// This should never happen, but just in case...
		if(wt == 0.0) {
			final int n = to - from;
			result.value = n % 2 == 0 ? 
					0.5F * (data[from + n/2-1].value + data[from + n/2].value) 
					: data[from + (n-1)/2].value;
			result.weight = 0.0;
			return;
		}
	
	
		final double midw = 0.5 * wt; 
		int ig = from; 
		
		WeightedPoint last = WeightedPoint.NaN;
		WeightedPoint point = data[from];
	
		double wi = point.weight;
		
		while(wi < midw) if(data[++ig].weight > 0.0) {
			last = point;
			point = data[ig];	    
			wi += 0.5 * (last.weight + point.weight);    
		}
		
		double wplus = wi;
		double wminus = wi - 0.5 * (last.weight + point.weight);
		
		double w1 = (wplus - midw) / (wplus + wminus);
		result.value = w1 * last.value + (1.0-w1) * point.value;
		result.weight = wt;
	}

	public static double select(double[] data, double fraction, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		return data[fromIndex + (int)Math.round(fraction * (toIndex - fromIndex - 1))];
	}

	public static float select(float[] data, double fraction, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		return data[fromIndex + (int)Math.floor(fraction * (toIndex - fromIndex - 1))];
	}
	
	public static float robustMean(float[] data, double tails) {
		return robustMean(data, 0, data.length, tails);
	}
	
	public static float robustMean(float[] data, int from, int to, double tails) {
		Arrays.sort(data, from, to);
		
		// Ignore the tails on both sides of the distribution...
		final int dn = (int) Math.round(tails * (to - from));
	
		to -= dn;
		from += dn;
		if(from >= to) return Float.NaN;

		// Average over the middle section of values...
		double sum = 0.0;
		for(int i = to; --i >= from; ) sum += data[i];
		return (float) (sum / (to - from));
	}
	
	public static double robustMean(double[] data, double tails) {
		return robustMean(data, 0, data.length, tails);
	}
	
	public static double robustMean(double[] data, int from, int to, double tails) {
		Arrays.sort(data, from, to);
		
		// Ignore the tails on both sides of the distribution...
		final int dn = (int) Math.round(tails * (to - from));
	
		to -= dn;
		from += dn;
		if(from >= to) return Double.NaN;

		// Average over the middle section of values...
		double sum = 0.0;
		for(int i = to; --i >= from; ) sum += data[i];
		return sum / (to - from);
	}
	
	public static WeightedPoint robustMean(WeightedPoint[] data, double tails) {
		return robustMean(data, 0, data.length, tails);
	}
	
	public static void robustMean(WeightedPoint[] data, double tails, WeightedPoint result) {
		robustMean(data, 0, data.length, tails, result);
	}
	
	public static WeightedPoint robustMean(WeightedPoint[] data, int from, int to, double tails) {
		WeightedPoint result = new WeightedPoint();
		robustMean(data, from, to, tails, result);
		return result;
	}
	
	public static void robustMean(WeightedPoint[] data, int from, int to, double tails, WeightedPoint result) {
		if(from >= to) {
			result.noData();
			return;
		}
		
		if(to-from == 1) {
			result.copy(data[from]);
			return;
		}
		
		Arrays.sort(data, from, to);
		
		// Ignore the tails on both sides of the distribution...
		final int dn = (int) Math.round(tails * (to - from));
	
		to -= dn;
		from += dn;
		if(from >= to) {
			result.noData();
			return;
		}

		// Average over the middle section of values...
		double sum = 0.0, sumw = 0.0;
		while(--to >= from) {
			final WeightedPoint point = data[to];
			sum += point.weight * point.value;
			sumw += point.weight;
		}
		result.value = sum / sumw;
		result.weight = sumw;
	}

}
