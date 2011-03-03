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

//package crush.util;

import java.text.*;

//Add parsing

public class PolarVector2D extends CoordinatePair {

	public PolarVector2D() { super(); }

	public PolarVector2D(double r, double phi) { super(r, phi); }

	public PolarVector2D(PolarVector2D template) { super(template); }

	@Override
	public boolean isNull() { return x == 0.0; }

	public final void scale(double value) { x *= value; }

	public final void rotate(double angle) { y += angle; }

	public final double dot(PolarVector2D v) { return dot(this, v); }

	public static double dot(PolarVector2D v1, PolarVector2D v2) {
		if(v1.isNull() || v2.isNull()) return 0.0;
		return Math.sqrt(v1.x*v1.x + v2.x*v2.x - 2.0 * Math.abs(v1.x * v2.x) * Math.cos(v1.y - v2.y)); 
	}

	public final double norm() { return x * x; }

	public final double length() { return Math.abs(x); }

	public final double angle() { return y; }

	public final void normalize() throws IllegalStateException {
		if(x == 0.0) throw new IllegalStateException("Null Vector");
		x /= length();
	}

	public static PolarVector2D normalized(PolarVector2D v) {
		PolarVector2D n = new PolarVector2D(v);
		n.normalize();
		return n;
	}

	public final void invert() { y += Math.PI; }

	public static PolarVector2D project(PolarVector2D v1, PolarVector2D v2) {
		PolarVector2D v = new PolarVector2D(v1);
		v.x = dot(v1,v2) / v2.x;
		v.y = v2.y;
		return v;
	}

	public final void projectOn(PolarVector2D v) {
		x = dot(v) / v.x;
		y = v.y;
	}

	public static PolarVector2D reflect(PolarVector2D v1, PolarVector2D v2) {
		PolarVector2D v = new PolarVector2D(v1);
		v.x = v1.x;
		v.y = 2.0 * v2.y - v1.y;
		return v;
	}

	public final void reflectOn(PolarVector2D v) {
		y = 2*v.y - y;
	}


	public final Vector2D cartesian() { 
		Vector2D v = new Vector2D();
		v.x = x * Math.cos(y);
		v.y = x * Math.sin(y);
		return v;
	}

	public void math(char op, double b) throws IllegalArgumentException {
		switch(op) {
		case '*': x *= b; break;
		case '/': x /= b; break;
		case '^': x = Math.pow(x, b); break;
		default: throw new IllegalArgumentException("Illegal Operation: "+ op);
		}
	}

	public static PolarVector2D math(PolarVector2D a, char op, double b) throws IllegalArgumentException {
		PolarVector2D result = new PolarVector2D();

		switch(op) {
		case '*': result.x = a.x * b; result.y = a.y; break;
		case '/': result.x = a.x / b; result.y = a.y; break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);
		}

		return result;
	}

	public String toString(DecimalFormat df) { return "(" + df.format(x) + " cis " + df.format(y) + ")"; }

	@Override
	public String toString() { return "(" + x + " cis " + y + ")"; }

}
