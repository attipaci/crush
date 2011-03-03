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
// (C)2007 Attila Kovacs <attila@submm.caltech.edu>

package util.text;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;

import util.Util;


// Chooses the most compact format with the given number of significant figures
// Keeps at least the required number of significant figures.
// May keep more figures depending on the chosen format...

public class SignificantFigures extends DecimalFormat {
	int digits;
	/**
	 * 
	 */
	private static final long serialVersionUID = 5240055256401076047L;

	public SignificantFigures(int n) {
		digits = n;
	}
	
	@Override
	public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
		
		int order = (int)Math.floor(Math.log10(Math.abs(number)));
		
		if(order < 0) {
			//int elength = 4+digits; // #.###E-#
			//int flength = 1+digits-order; // 0.####
			if(order > -3) return Util.f[digits-order-1].format(number, toAppendTo, pos);
			else return Util.e[digits-1].format(number, toAppendTo, pos);
		}
		// ###.##
		// #.####E# or #.####E##
		else if(order > 9) {
			if(order > digits+3) return Util.e[digits-1].format(number, toAppendTo, pos);
			else return Util.f[digits-order-1].format(number, toAppendTo, pos);
		}
		else if(order > digits+2) return Util.e[digits-1].format(number, toAppendTo, pos);
		else if(order > digits-1) return Util.d[order+1].format(number, toAppendTo, pos);  
		else return Util.f[digits-order-1].format(number, toAppendTo, pos);
	}

	@Override
	public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
		int order = (int)Math.floor(Math.log10(number));
		
		if(order > 9) {
			if(order > digits+3) return Util.e[digits-1].format(number, toAppendTo, pos);
			else return Util.f[digits-order-1].format(number, toAppendTo, pos);
		}
		else if(order > digits+2) return Util.e[digits-1].format(number, toAppendTo, pos);
		else if(order > digits-1) return Util.d[order+1].format(number, toAppendTo, pos); 
		else return Util.f[digits-order-1].format(number, toAppendTo, pos);
	}

	@Override
	// TODO parse only so many significant figures?
	public Number parse(String source, ParsePosition parsePosition) {
		return Util.e9.parse(source, parsePosition);
	}
	
}
