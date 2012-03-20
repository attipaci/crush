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
	public boolean isNull() { return getX() == 0.0; }

	public final void scale(double value) { scaleX(value); }

	public final void rotate(double angle) { addY(angle); }

	public final double dot(PolarVector2D v) { return dot(this, v); }

	public static double dot(PolarVector2D v1, PolarVector2D v2) {
		if(v1.isNull() || v2.isNull()) return 0.0;
		return Math.sqrt(v1.getX()*v1.getX() + v2.getX()*v2.getX() - 2.0 * Math.abs(v1.getX() * v2.getX()) * Math.cos(v1.getY() - v2.getY())); 
	}

	public final double norm() { return getX() * getX(); }

	public final double length() { return Math.abs(getX()); }

	public final double angle() { return getY(); }

	public final void normalize() throws IllegalStateException {
		if(getX() == 0.0) throw new IllegalStateException("Null Vector");
		setX(1.0);
	}

	public static PolarVector2D normalized(PolarVector2D v) {
		PolarVector2D n = new PolarVector2D(v);
		n.normalize();
		return n;
	}

	public final void invert() { addY(Math.PI); }

	public static PolarVector2D project(PolarVector2D v1, PolarVector2D v2) {
		PolarVector2D v = new PolarVector2D(v1);
		v.setX(dot(v1,v2) / v2.getX());
		v.setY(v2.getY());
		return v;
	}

	public final void projectOn(PolarVector2D v) {
		setX(dot(v) / v.getX());
		setY(v.getY());
	}

	public static PolarVector2D reflect(PolarVector2D v1, PolarVector2D v2) {
		PolarVector2D v = new PolarVector2D(v1);
		v.setX(v1.getX());
		v.setY(2.0 * v2.getY() - v1.getY());
		return v;
	}

	public final void reflectOn(PolarVector2D v) {
		setY(2*v.getY() - getY());
	}


	public final Vector2D cartesian() { 
		Vector2D v = new Vector2D();
		v.setX(getX() * Math.cos(getY()));
		v.setY(getX() * Math.sin(getY()));
		return v;
	}

	public void math(char op, double b) throws IllegalArgumentException {
		switch(op) {
		case '*': scaleX(b); break;
		case '/': scaleX(1.0 / b); break;
		case '^': setX(Math.pow(getX(), b)); scaleY(b); break;
		default: throw new IllegalArgumentException("Illegal Operation: "+ op);
		}
	}

	public static PolarVector2D math(PolarVector2D a, char op, double b) throws IllegalArgumentException {
		PolarVector2D result = new PolarVector2D();

		switch(op) {
		case '*': result.setX(a.getX() * b); result.setY(a.getY()); break;
		case '/': result.setX(a.getX() / b); result.setY(a.getY()); break;
		default: throw new IllegalArgumentException("Illegal Operation: " + op);
		}

		return result;
	}

	public String toString(DecimalFormat df) { return "(" + df.format(getX()) + " cis " + df.format(getY()) + ")"; }

	@Override
	public String toString() { return "(" + getX() + " cis " + getY() + ")"; }

}
