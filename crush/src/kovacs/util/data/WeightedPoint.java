/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
// Copyright (c) 2007 Attila Kovacs 

package kovacs.util.data;

import java.text.*;

public class WeightedPoint implements Comparable<WeightedPoint>, Cloneable {
	private double value, weight;

	public WeightedPoint() {}

	public WeightedPoint(WeightedPoint template) {
		copy(template);
	}

	public WeightedPoint(final double x, final double w) { 
		value = x;
		weight = w;
	}
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public int compareTo(WeightedPoint point) throws ClassCastException {
		return Double.compare(value, point.value);
	}
	
	public final double value() { return value; }
	
	public final double weight() { return weight; }
	
	public void setValue(double x) { this.value = x; }
	
	public void setWeight(double w) { this.weight = w; }
	
	public void add(double dx) { value += dx; }
	
	public void subtract(double dx) { value -= dx; }
	
	public void addWeight(double dw) { weight += dw; }
	
	public void scaleValue(double factor) { value *= factor; }
	
	public void scaleWeight(double factor) { weight *= factor; }

	public void noData() { 
		value = weight = 0.0;
	}

	public boolean isNaN() { return isNaN(this); }

	public static boolean isNaN(WeightedPoint point) { 
		return Double.isNaN(point.value) || point.weight == 0.0;
	}

	public final void exact() { weight = Double.POSITIVE_INFINITY; }

	public final boolean isExact() { return Double.isInfinite(weight); }

	public void copy(final WeightedPoint x) {
		value = x.value;
		weight = x.weight;
	}
	
	public void add(final WeightedPoint x) {
		value += x.value;
		if(weight == 0.0) return;
		if(x.weight == 0.0) weight = 0.0;
		else weight = 1.0 / (1.0/weight + 1.0/x.weight);
	}
	
	public void subtract(final WeightedPoint x) {
		value -= x.value;
		if(weight == 0.0) return;
		if(x.weight == 0.0) weight = 0.0;
		else weight = 1.0 / (1.0/weight + 1.0/x.weight);
	}
	
	public void average(final WeightedPoint x) {
		average(x.value, x.weight);
	}
	
	public void average(final double v, final double w) {
		value = weight * value + w * v;
		weight += w;
		if(weight > 0.0) value /= weight;		
	}

	public final void scale(final double x) {
		value *= x;
		weight /= x*x;
	}

	public void math(final char op, final WeightedPoint x) throws IllegalArgumentException {
		switch(op) {
		case '+' : add(x); break;
		case '-' : subtract(x); break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);
		}
	}

	public static WeightedPoint math(final WeightedPoint a, final char op, final WeightedPoint b) {
		WeightedPoint result = new WeightedPoint(a);
		result.math(op, b);
		return result;
	}

	public void math(final char op, final double x) throws IllegalArgumentException {
		switch(op) {
		case '+' : add(x); break;
		case '-' : subtract(x); break;
		case '*' : scale(x); break;
		case '/' : scale(1.0/x); break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);
		}
	}

	public static WeightedPoint math(final WeightedPoint a, final char op, final double b) {
		WeightedPoint result = new WeightedPoint(a);
		result.math(op, b);
		return result;
	}

	@Override
	public String toString() {
		return toString(" +- ", ""); 
	}
	
	public String toString(String before, String after) {
		return value + before + Math.sqrt(1.0 / weight) + after; 
	}

	public String toString(final DecimalFormat df) {
		return toString(df, " +- ", "");
	}
	
	public String toString(final DecimalFormat df, String before, String after) {
		return df.format(value) + before + df.format(Math.sqrt(1.0 / weight)) + after; 
	}

	public static float[] floatValues(WeightedPoint[] data) {
		float[] fdata = new float[data.length];
		for(int i=data.length; --i >= 0; ) fdata[i] = (float) data[i].value;
		return fdata;
	}
	
	public static double[] values(WeightedPoint[] data) {
		double[] ddata = new double[data.length];
		for(int i=data.length; --i >= 0; ) ddata[i] = data[i].value;
		return ddata;
	}
	
	public static float[] floatWeights(WeightedPoint[] data) {
		float[] fdata = new float[data.length];
		for(int i=data.length; --i >= 0; ) fdata[i] = (float) data[i].weight;
		return fdata;
	}
	
	public static double[] weights(WeightedPoint[] data) {
		double[] ddata = new double[data.length];
		for(int i=data.length; --i >= 0; ) ddata[i] = data[i].weight;
		return ddata;
	}
	
	public final static WeightedPoint NaN = new WeightedPoint(0.0, 0.0);

	
}