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

//Add parsing

public class Vector2D extends CoordinatePair implements Metric<Vector2D> {

	public Vector2D() {}

	public Vector2D(String text) { parse(text); }

	public Vector2D(double X, double Y) { super(X, Y); }

	public Vector2D(Vector2D template) { super(template); }

	public final void add(final Vector2D v) { x += v.x; y += v.y; }

	public static Vector2D sum(Vector2D a, Vector2D b) {
		return new Vector2D(a.x + b.x, a.y + b.y);
	}

	public final void subtract(final Vector2D v) { x -= v.x; y -= v.y; }

	public final void isubtract(final Vector2D v) { x = v.x - x; y = v.y - y; }
	
	public final void addMultipleOf(final Vector2D vector, final double factor) {
		x += factor * vector.x;
		y += factor * vector.y;
	}
	
	public static Vector2D difference(Vector2D a, Vector2D b) {
		Vector2D value = new Vector2D();
		value.x = a.x - b.x;
		value.y = a.y - b.y;
		return value;
	}

	public final void scale(final double value) { x *= value; y *= value; }    

	public final void rotate(double alpha) {
		double cosA = Math.cos(alpha);
		double sinA = Math.sin(alpha);

		double newx = x * cosA - y * sinA;
		y           = x * sinA + y * cosA;
		x = newx;
	}

	public final double dot(Vector2D v) { return dot(this, v); }

	public static double dot(Vector2D v1, Vector2D v2) {
		return v1.x*v2.x + v1.y*v2.y;
	}

	public final double norm() { return x*x + y*y; }

	public final double length() { return Math.hypot(x, y); }

	public final double angle() {
		if(isNull()) return Double.NaN;
		return Math.atan2(y,x);
	}
	
	public final void normalize() throws IllegalStateException { 
		if(isNull()) throw new IllegalStateException("Null Vector");
		double il = 1.0 / norm(); 
		x *= il; y *= il; 
	}

	public final Vector2D normalized(Vector2D v) {
		Vector2D n = new Vector2D(v);
		n.normalize();
		return n;
	}

	public final void invert() { x *= -1; y *= -1; }	

	public static Vector2D inverseOf(Vector2D v) { return new Vector2D(-v.x, -v.y); }

	public static Vector2D project(Vector2D v1, Vector2D v2) {
		Vector2D v = new Vector2D(v1);
		double alpha = v2.angle();
		v.rotate(-alpha);
		v.y = 0.0;
		v.rotate(alpha);
		return v;
	}

	public final void projectOn(Vector2D v) {
		double alpha = v.angle();
		rotate(-alpha);
		y = 0.0;
		rotate(alpha);
	}

	public static Vector2D reflect(Vector2D v1, Vector2D v2) {
		Vector2D v = new Vector2D(v1);
		double alpha = v2.angle();
		v.rotate(-alpha);
		v.y *= -1.0;
		v.rotate(alpha);
		return v;
	}

	public final void reflectOn(Vector2D v) {
		double alpha = v.angle();
		rotate(-alpha);
		y *= -1.0;
		rotate(alpha);
	}


	public final PolarVector2D polar() {
		PolarVector2D p = new PolarVector2D();
		p.x = length();
		p.y = angle();	
		return p;
	}


	public void math(char op, Vector2D v) throws IllegalArgumentException {
		switch(op) {
		case '+': x += v.x; y += v.y; break;
		case '-': x -= v.x; y -= v.y; break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);
		}
	}


	public static Vector2D math(Vector2D a, char op, Vector2D b) throws IllegalArgumentException {
		Vector2D result = new Vector2D();

		switch(op) {
		case '+': result.x = a.x + b.x; result.y = a.y + b.y; break;
		case '-': result.x = a.x - b.x; result.y = a.y - b.y; break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);
		}

		return result;
	}

	public void math(char op, double b) throws IllegalArgumentException {
		switch(op) {
		case '*': x *= b; y *= b; break;
		case '/': x /= b; y /= b; break;
		case '^': x = Math.pow(x, b); y = Math.pow(y, b); break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);	    
		}
	}


	public static Vector2D math(Vector2D a, char op, double b) throws IllegalArgumentException {
		Vector2D result = new Vector2D();

		switch(op) {
		case '*': result.x = a.x * b; result.y = a.y * b; break;
		case '/': result.x = a.x / b; result.y = a.y / b; break;
		case '^': result.x = Math.pow(a.x, b); result.y = Math.pow(a.y, b); break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);	    
		}

		return result;
	}
	

	@Override
	public double getValue(int field) throws NoSuchFieldException {
		switch(field) {
		case LENGTH: return length();
		case NORM: return norm();
		case ANGLE: return angle();
		default: return super.getValue(field);
		}
	}
	
	@Override
	public void setValue(int field, double value) throws NoSuchFieldException {
		switch(field) {
		case LENGTH: scale(value/length()); break;
		case NORM: scale(Math.sqrt(value/norm())); break;
		case ANGLE: rotate(value - angle()); break;
		default: super.setValue(field, value);
		}
	}

	public final double distanceTo(Vector2D point) {
		return Math.hypot(point.x - x, point.y - y);
	}
	
	public static final int LENGTH = 2;
	public static final int NORM = 3;
	public static final int ANGLE = 4;
	
	public static final Vector2D NaN = new Vector2D(Double.NaN, Double.NaN);


	
}
