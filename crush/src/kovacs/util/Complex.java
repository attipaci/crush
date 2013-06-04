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

package kovacs.util;

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

	public static Complex conjugate(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.conjugate();
		return c;
	}

	public final void inverse() { 
		conjugate();
		scale(1.0 / norm());
	}

	public static Complex inverse(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.inverse();
		return c;
	}

	public final void multiplyBy(final Complex c) {
		final double x0 = getX();
		setX(getX() * c.getX() - getY() * c.getY());
		setY(x0 * c.getY() + getY() * c.getX());
	}
	
	public final void divideBy(final Complex c) {
		final double ax = getX();
		setX(ax * c.getX() - getY() * c.getY());
		setY(getY() * c.getX() - ax * c.getY());
		scale(1.0 / c.norm());
	}
	
	public final void multiplyByI() {
		set(-getY(), getX());
	}
	
	public final void divideByI() {
		set(getY(), -getX());
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

	public final void pow(final double b) {
		final double r = Math.pow(length(), b);
		final double phi = angle() * b;
		
		setY(r * Math.sin(phi));
		setX(r * Math.cos(phi));
	}

	public static Complex pow(final Complex arg, final double exp) {
		final Complex c = (Complex) arg.clone();
		c.pow(exp);
		return c;
	}


	public final void sqrt() { pow(0.5); }

	public static Complex sqrt(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.sqrt();
		return c;
	}	

	public final void exp() {
		final double r = Math.exp(getX());
		setX(r * Math.cos(getY()));
		setY(r * Math.sin(getY()));
	}	

	public static Complex exp(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.exp();
		return c;
	}


	public final void log() {
		setX(Math.log(length()));
		setY(angle());
	}

	public static Complex log(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.log();
		return c;
	}


	public final void cos() {
		final Complex e = (Complex) clone();
		e.multiplyByI();
		e.exp();
		e.add(inverse(e));
		e.scale(0.5);
	}

	public static Complex cos(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.cos();
		return c;
	}

	public final void sin() {
		final Complex e = (Complex) clone();
		e.multiplyByI();
		e.exp();
		e.subtract(inverse(e));
		e.scale(0.5);
		e.divideByI();
	}

	public static Complex sin(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.sin();
		return c;
	}

	public final void tan() {
		final Complex c = cos(this);
		sin();
		divideBy(c);
	}

	public static Complex tan(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.tan();
		return c;
	}


	public final void cosh() {
		final Complex e = exp(this);
		e.add(inverse(e));
		e.scale(0.5);
	}

	public static Complex cosh(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.cos();
		return c;
	}

	public final void sinh() {
		final Complex e = exp(this);
		e.subtract(inverse(e));
		e.scale(0.5);
		e.divideByI();
	}

	public static Complex sinh(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.sin();
		return c;
	}

	public final void tanh() {
		final Complex c = cos(this);
		sin();
		divideBy(c);
	}

	public static Complex tanh(final Complex arg) {
		final Complex c = (Complex) arg.clone();
		c.tan();
		return c;
	}


	public final void math(final char op, final Complex c) throws IllegalArgumentException {
		switch(op) {
		case '*': 
			multiplyBy(c);
			break;
		case '/': 
			divideBy(c);
			break;
		default: 
			super.math(op, c);
		}
	}


	public static Complex math(Complex a, char op, Complex b) throws IllegalArgumentException {
		final Complex result = (Complex) a.clone();
		result.math(op, b);
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
		Complex result = (Complex) a.clone();
		result.math(op, b);
		return result;
	}


	public final String toString(DecimalFormat df) { return df.format(getX()) + (getY() < 0 ? "" : "+") + df.format(getY()) + "i"; }

	@Override
	public final String toString() { return getX() + (getY() < 0 ? "" : "+") + getY() + "i"; }

	final static Complex i = new Complex(0.0, 1.0);
}
