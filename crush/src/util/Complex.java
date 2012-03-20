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

public class Complex extends Vector2D {

	public Complex() { super(); }

	public Complex(double re, double im) { super(re, im); }

	public Complex(Complex template) { super(template); }

	public Complex(double real) { super(real, 0.0); }

	public final double re() { return getX(); }

	public final double im() { return getY(); }

	public final double abs() { return length(); }

	public final double arg() { return angle(); }

	public final void conjugate() { scaleY(-1.0); }

	public static Complex conjugate(Complex arg) {
		Complex c = new Complex(arg);
		c.conjugate();
		return c;
	}

	public final void inverse() { 
		conjugate();
		scale(1.0 / norm());
	}

	public static Complex inverse(Complex arg) {
		Complex c = new Complex(arg);
		c.inverse();
		return c;
	}

	public final void multiplyBy(final Complex c) {
		final double x0 = getX();
		setX(getX() * c.getX() - getY() * c.getY());
		setY(x0 * c.getY() + getY() * c.getX());
	}
	
	// This is almost exactly the same speed as separating the operations
	// I.e. the overheads are in accessing the complex fields...
	public final void mergeFFT(final Complex d1, final Complex d2) {	
		final double mx = getX() * d2.getX() - getY() * d2.getY();
		final double my = getX() * d2.getY() + getY() * d2.getX();
		
		d2.setX(d1.getX() - mx);
		d2.setY(d1.getY() - my);
		
		d1.addX(mx);
		d1.addY(my);	
	}

	public final void pow(double b) {
		final double r = Math.pow(length(), b);
		final double phi = angle() * b;
		setX(r * Math.cos(phi));
		setY(r * Math.sin(phi));
	}

	public static Complex pow(Complex arg, double exp) {
		Complex c = new Complex(arg);
		c.pow(exp);
		return c;
	}


	public final void sqrt() { pow(0.5); }

	public static Complex sqrt(Complex arg) {
		Complex c = new Complex(arg);
		c.sqrt();
		return c;
	}	

	public final void exp() {
		double r = Math.exp(getX());
		setX(r * Math.cos(getY()));
		setY(r * Math.sin(getY()));
	}	

	public static Complex exp(Complex arg) {
		Complex c = new Complex(arg);
		c.exp();
		return c;
	}


	public final void log() {
		double r = length();
		setX(Math.log(r));
		setY(angle());
	}

	public static Complex log(Complex arg) {
		Complex c = new Complex(arg);
		c.log();
		return c;
	}


	public final void cos() {
		Complex e = math(i, '*', this); 
		e.exp();
		e.add(inverse(e));
		e.math('/', 2.0);
	}

	public static Complex cos(Complex arg) {
		Complex c = new Complex(arg);
		c.cos();
		return c;
	}

	public final void sin() {
		Complex e = math(i, '*', this);
		e.exp();
		e.subtract(inverse(e));
		e.math('/', new Complex(0.0, 2.0));
	}

	public static Complex sin(Complex arg) {
		Complex c = new Complex(arg);
		c.sin();
		return c;
	}

	public final void tan() {
		Complex c = cos(this);
		sin();
		math('/', c);
	}

	public static Complex tan(Complex arg) {
		Complex c = new Complex(arg);
		c.tan();
		return c;
	}


	public final void cosh() {
		Complex e = exp(this);
		e.add(inverse(e));
		e.math('/', 2.0);
	}

	public static Complex cosh(Complex arg) {
		Complex c = new Complex(arg);
		c.cos();
		return c;
	}

	public final void sinh() {
		Complex e = exp(this);
		e.subtract(inverse(e));
		e.math('/', new Complex(0.0, 2.0));
	}

	public static Complex sinh(Complex arg) {
		Complex c = new Complex(arg);
		c.sin();
		return c;
	}

	public final void tanh() {
		Complex c = cos(this);
		sin();
		math('/', c);
	}

	public static Complex tanh(Complex arg) {
		Complex c = new Complex(arg);
		c.tan();
		return c;
	}


	public final void math(char op, Complex b) throws IllegalArgumentException {
		double ax;

		switch(op) {
		case '*': 
			ax = getX();
			setX(ax * b.getX() - getY() * b.getY());
			setY(getY() * b.getX() + ax * b.getY());
			break;
		case '/': 
			ax = getX();
			setX(ax * b.getX() - getY() * b.getY());
			setY(getY() * b.getX() - ax * b.getY());
			scale(1.0 / b.norm());
			break;
		default: 
			super.math(op, b);
		}
	}


	public static Complex math(Complex a, char op, Complex b) throws IllegalArgumentException {
		Complex result = new Complex();

		switch(op) {
		case '*': 
			result.setX(a.getX() * b.getX() - a.getY() * b.getY()); 
			result.setY(a.getY() * b.getX() + a.getX() * b.getY()); 
			break;
		case '/': 
			result.setX(a.getX() * b.getX() - a.getY() * b.getY()); 
			result.setY(a.getY() * b.getX() - a.getX() * b.getY()); 
			result.scale(1.0 / b.norm());
			break;
		default: 
			Vector2D v = Vector2D.math(a, op, b);
			result.setX(v.getX()); result.setY(v.getY());
		}

		return result;
	}


	@Override
	public final void math(char op, double b) throws IllegalArgumentException {
		switch(op) {
		case '+': addX(b); break;
		case '-': subtractX(b); break;
		case '^': pow(b); break;	    
		default: super.math(op, b);
		}
	}

	public static Complex math(Complex a, char op, double b) throws IllegalArgumentException {
		Complex result = new Complex(a);

		switch(op) {
		case '+': result.setX(a.getX() + b); result.setY(a.getY()); break;
		case '-': result.setX(a.getX() - b); result.setY(a.getY()); break;
		case '^': result.pow(b); break;	    
		default: 
			Vector2D v = Vector2D.math(a, op, b);
			result.setX(v.getX()); result.setY(v.getY());	    
		}

		return result;
	}

	public static Complex math(double a, char op, Complex b) throws IllegalArgumentException {
		if(op == '*' || op == '+') return math(b, op, a);
		return math(new Complex(a), op, b);
	}

	public final String toString(DecimalFormat df) { return df.format(getX()) + (getY() < 0 ? "" : "+") + df.format(getY()) + "i"; }

	@Override
	public final String toString() { return getX() + (getY() < 0 ? "" : "+") + getY() + "i"; }

	final static Complex i = new Complex(0.0, 1.0);
}
