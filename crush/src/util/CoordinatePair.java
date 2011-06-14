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
// Copyright (c) 2007 Attila Kovacs 

package util;

import java.awt.geom.Point2D;
import java.text.*;

//Add parsing

public class CoordinatePair implements Cloneable {
	public double x, y;

	public CoordinatePair() { defaults(); }

	public void defaults() {
		x = Double.NaN;
		y = Double.NaN;
	}
	
	public CoordinatePair(Point2D point) { x = point.getX(); y = point.getY(); }
	
	public CoordinatePair(String text) { parse(text); }

	public CoordinatePair(double X, double Y) { set(X,Y); }

	public CoordinatePair(CoordinatePair template) { x = template.x; y = template.y; }

	public void copy(final CoordinatePair template) { x = template.x; y = template.y; }

	@Override
	public boolean equals(Object o) {
		if(o instanceof CoordinatePair) return equals((CoordinatePair) o);
		else return false;
	}
	
	@Override
	public int hashCode() {
		return HashCode.get(x) ^ ~HashCode.get(y);
	}

	public boolean equals(CoordinatePair c) {
		if(c.x == x && c.y == y) return true;
		else return c.isNaN() && isNaN();
	}

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public Point2D getPoint2D() {
		return new Point2D.Double(x, y);
	}
	
	public void toPoint2D(Point2D point) {
		point.setLocation(x, y);
	}
	
	public void fromPoint2D(Point2D point) {
		x = point.getX(); y = point.getY();
	}
	
	public void set(final double X, final double Y) { x = X; y = Y; }

	public void zero() { x = 0.0; y = 0.0; }

	public boolean isNull() { return x == 0.0 && y == 0.0; }	

	public void NaN() { x = Double.NaN; y = Double.NaN; }

	public final boolean isNaN() { return Double.isNaN(x) || Double.isNaN(y); }

	public final void weightedAverageWith(double w1, CoordinatePair coord, double w2) {
		double isumw = 1.0 / (w1 + w2);
		w1 *= isumw; w2 *= isumw;
		x = w1 * x + w2 * coord.x;
		y = w1 * y + w2 * coord.y;
	}

	public void parse(String text) throws NumberFormatException, IllegalArgumentException {
		int i=0, to = text.length();
		
		if(text.contains("(")) {
			i = text.indexOf('(') + 1;
			to = text.lastIndexOf(')');
			if(to < i) throw new IllegalStateException("Unmatched brackets.");
		}
		
		int open = 0;
		char c;
		while(((c = text.charAt(i)) != ',' || open > 0) && i < to) {
			if(c == '(') open++;
			else if(c == ')') open--;
			i++;
		}
		if(i>0 && i<text.length()) {
			x = Double.parseDouble(text.substring(0, i));
			y = Double.parseDouble(text.substring(i+1, to));
		}
	}

	public String toString(NumberFormat nf) {
		return "(" + nf.format(x) + "," + nf.format(y) + ")";
	}

	@Override
	public String toString() { 
		return "(" + x + "," + y + ")";
	}
	
	public final void createFromDoubles(Object array) throws IllegalArgumentException {
		if(!(array instanceof double[])) throw new IllegalArgumentException("argument is not a double[].");
		double[] components = (double[]) array;
		if(components.length != 2) throw new IllegalArgumentException("argument double[] array is to small.");
		x = components[0];
		y = components[1];
	}

	public final void viewAsDoubles(Object view) throws IllegalArgumentException {
		if(!(view instanceof double[])) throw new IllegalArgumentException("argument is not a double[].");
		double[] components = (double[]) view;
		if(components.length != 2) throw new IllegalArgumentException("argument double[] array is to small.");
		components[0] = x;
		components[1] = y;
	}
	
	public final Object viewAsDoubles() {
		return new double[] { x, y };		
	}
	
	
	public double getValue(int field) throws NoSuchFieldException {
		switch(field) {
		case X: return x;
		case Y: return y;
		default: throw new NoSuchFieldException(getClass().getSimpleName() + " has no field for " + field);
		}
	}
	
	public void setValue(int field, double value) throws NoSuchFieldException {
		switch(field) {
		case X: x = value; break;
		case Y: y = value; break; 
		default: throw new NoSuchFieldException(getClass().getSimpleName() + " has no field for " + field);
		}
	}
	
	public static String toString(CoordinatePair coords, Unit unit) {
		return toString(coords, unit, 3);
	}
	
	public static String toString(CoordinatePair coords, Unit unit, int decimals) {
		return Util.f[decimals].format(coords.x / unit.value) + " " + unit.name + 
		", " + Util.f[decimals].format(coords.y / unit.value) + " " + unit.name;
	}
	
	
	public static final int X = 0;
	public static final int Y = 1;
}
