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

//Add parsing

public class Vector2D extends CoordinatePair implements Metric<Vector2D> {

	public Vector2D() {}

	public Vector2D(String text) { parse(text); }

	public Vector2D(double X, double Y) { super(X, Y); }

	public Vector2D(Vector2D template) { super(template); }

	public Vector2D(Point2D point) { super(point); }
	
	public final void add(final Vector2D v) { addX(v.getX()); addY(v.getY()); }

	public static Vector2D sum(Vector2D a, Vector2D b) {
		return new Vector2D(a.getX() + b.getX(), a.getY() + b.getY());
	}
	
	public final void subtract(final Vector2D v) { subtractX(v.getX()); subtractY(v.getY()); }

	public final void isubtract(final Vector2D v) { setX(v.getX() - getX()); setY(v.getY() - getY()); }
	
	public final void addMultipleOf(final Vector2D vector, final double factor) {
		addX(factor * vector.getX());
		addY(factor * vector.getY());
	}
	
	public static Vector2D difference(final Vector2D a, final Vector2D b) {
		Vector2D value = new Vector2D();
		value.setX(a.getX() - b.getX());
		value.setY(a.getY() - b.getY());
		return value;
	}

	public final void scale(final double value) { scaleX(value); scaleY(value); }    

	public final void rotate(final double alpha) {
		final double cosA = Math.cos(alpha);
		final double sinA = Math.sin(alpha);

		final double x = getX();
		setX(x * cosA - getY() * sinA);
		setY(x * sinA + getY() * cosA);
	}

	public final double dot(Vector2D v) { return dot(this, v); }

	public static double dot(Vector2D v1, Vector2D v2) {
		return v1.getX() * v2.getX() + v1.getY() * v2.getY();
	}

	public final double norm() { return getX() * getX() + getY() * getY(); }

	public final double length() { return Math.hypot(getX(), getY()); }

	public final double angle() {
		if(isNull()) return Double.NaN;
		return Math.atan2(getY(), getX());
	}
	
	public final void normalize() throws IllegalStateException { 
		if(isNull()) throw new IllegalStateException("Null Vector");
		double inorm = 1.0 / norm(); 
		scaleX(inorm); scaleY(inorm); 
	}

	public final Vector2D normalized(Vector2D v) {
		Vector2D n = new Vector2D(v);
		n.normalize();
		return n;
	}

	public final void invert() { scaleX(-1.0); scaleY(-1.0); }	

	public static Vector2D inverseOf(Vector2D v) { return new Vector2D(-v.getX(), -v.getY()); }

	public static Vector2D project(Vector2D v1, Vector2D v2) {
		Vector2D v = new Vector2D(v1);
		double alpha = v2.angle();
		v.rotate(-alpha);
		v.setY(0.0);
		v.rotate(alpha);
		return v;
	}

	public final void projectOn(Vector2D v) {
		double alpha = v.angle();
		rotate(-alpha);
		setY(0.0);
		rotate(alpha);
	}

	public static Vector2D reflect(Vector2D v1, Vector2D v2) {
		Vector2D v = new Vector2D(v1);
		double alpha = v2.angle();
		v.rotate(-alpha);
		v.scaleY(-1.0);
		v.rotate(alpha);
		return v;
	}

	public final void reflectOn(Vector2D v) {
		double alpha = v.angle();
		rotate(-alpha);
		scaleY(-1.0);
		rotate(alpha);
	}


	public final PolarVector2D polar() {
		PolarVector2D p = new PolarVector2D();
		p.setX(length());
		p.setY(angle());	
		return p;
	}


	public void math(char op, Vector2D v) throws IllegalArgumentException {
		switch(op) {
		case '+': addX(v.getX()); addY(v.getY()); break;
		case '-': subtractX(v.getX()); subtractY(v.getY()); break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);
		}
	}


	public static Vector2D math(Vector2D a, char op, Vector2D b) throws IllegalArgumentException {
		Vector2D result = new Vector2D();

		switch(op) {
		case '+': result.setX(a.getX() + b.getX()); result.setY(a.getY() + b.getY()); break;
		case '-': result.setX(a.getX() - b.getX()); result.setY(a.getY() - b.getY()); break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);
		}

		return result;
	}

	public void math(char op, double b) throws IllegalArgumentException {
		switch(op) {
		case '*': scaleX(b); break;
		case '/': scaleX(1.0/b); break;
		case '^': setX(Math.pow(getX(), b)); setY(Math.pow(getY(), b)); break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);	    
		}
	}


	public static Vector2D math(Vector2D a, char op, double b) throws IllegalArgumentException {
		Vector2D result = new Vector2D();

		switch(op) {
		case '*': result.setX(a.getX() * b); result.setY(a.getY() * b); break;
		case '/': result.setX(a.getX() / b); result.setY(a.getY() / b); break;
		case '^': result.setX(Math.pow(a.getX(), b)); result.setY(Math.pow(a.getY(), b)); break;
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
		return Math.hypot(point.getX() - getX(), point.getY() - getY());
	}
	
	public static final int LENGTH = 2;
	public static final int NORM = 3;
	public static final int ANGLE = 4;
	
	public static final Vector2D NaN = new Vector2D(Double.NaN, Double.NaN);


	
}
