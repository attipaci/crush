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

package util.text;


import java.text.*;

import util.Symbol;
import util.Unit;
import util.Util;

public class AngleFormat extends NumberFormat {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8006119682644201943L;
	private int decimals;
	private double precision;
	
	public int topLevel = DEGREE;
	public int bottomLevel = SECOND;
	
		
	protected static final char[] colonMarker = { ':', ':', 0};
	protected static final char[] dmsMarker = { 'd', 'm', 's'};
	protected static final char[] symbolMarker = { Symbol.degree, '\'', '"'};

	protected double[] unit = { Unit.deg, Unit.arcmin, Unit.arcsec };	
	protected char[] marker = dmsMarker;
	
	protected double wraparound = 2.0 * Math.PI;
	protected boolean wrap = true, oneSided = false;
	
	public AngleFormat() {
		setDecimals(0);
	}
	
	public AngleFormat(int decimals) {
		setDecimals(decimals);
	}
	
	public void setSeparator(int type) {
		switch(type) {
		case COLONS: marker = colonMarker; break;
		case DMS: marker = dmsMarker; break;
		case SYMBOLS: marker = symbolMarker; break;
		default: marker = colonMarker;
		}	
	}
	
	public int getSeparator() {
		if(marker == colonMarker) return COLONS;
		else if(marker == dmsMarker) return DMS;
		else if(marker == symbolMarker) return SYMBOLS;	
		else return -1;
	}
	
	public void colons() { marker = colonMarker; }
	
	public void letters() { marker = dmsMarker; }
	
	public void symbols() { marker = symbolMarker; }
	
	public void setTopLevel(int level) { 
		if(level < DEGREE || level > SECOND) throw new IllegalArgumentException("Undefined " + getClass().getSimpleName() + " level.");
		topLevel = level; 	
	}
	
	public void setBottomLevel(int level) { 
		if(level < DEGREE || level > SECOND) throw new IllegalArgumentException("Undefined " + getClass().getSimpleName() + " level.");
		bottomLevel = level; 		
	}
	
	public int getTopLevel() { return topLevel; }
	
	public int getBottomLevel() { return bottomLevel; }
	
	public void setDecimals(int decimals) { 
		this.decimals = decimals; 
		precision = decimals > 0 ? Math.pow(0.1, decimals) : 0.0;
	}
	
	public int getDecimals() { return decimals; }
	
	public void setOneSided(boolean value) { oneSided = value; }
	
	public boolean isOneSided() { return oneSided; }
	
	public void wrap(boolean value) { wrap = value; }
	
	public boolean isWrapping() { return wrap; }
	
	
	@Override
	public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {		
		if(wrap) {
			number = Math.IEEEremainder(number, wraparound);
			if(oneSided && number < 0) number += wraparound;
		}
		
		pos.setBeginIndex(toAppendTo.length());
		toAppendTo.append(toString(number));
		pos.setEndIndex(toAppendTo.length());
		return toAppendTo;
	}
	@Override
	public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
		// TODO Auto-generated method stub
		return null;
	}
	
	// Parse with any markers, and any level below the top level.
	// The top level is then readjusted to the 
	@Override
	public Number parse(String source, ParsePosition parsePosition) {
		return parse(source, parsePosition, DEGREE);
	}
	
	public Number parse(String source, ParsePosition parsePosition, int fromLevel) {
		int sign = 1;
		double angle = 0.0;
		
		if(marker[fromLevel] != 0) {
			int pos = parsePosition.getIndex();
			colons();
			if(source.indexOf(marker[fromLevel], pos) < 0) {
				letters();
				if(source.indexOf(marker[fromLevel], pos) < 0) {
					symbols();
					if(source.indexOf(marker[fromLevel], pos) < 0) return parse(source, parsePosition, fromLevel+1);
				}
			}
		}
		
		for(int level = fromLevel; level <= SECOND; level++) {
			if(marker[level] != 0) {
				int pos = parsePosition.getIndex();
				int to = source.indexOf(marker[level], pos);
				if(to < 0) {
					bottomLevel = level;
					break;
				}
				if(level == SECOND) angle += Double.parseDouble(source.substring(pos, to)) * unit[level];
				else angle += Integer.parseInt(source.substring(pos, to)) * unit[level];
				
				if(level == topLevel) if(angle < 0.0) {
					angle *= -1;
					sign = -1;
				}	
				
				parsePosition.setIndex(to+1);
			}
			else angle += Util.f[decimals].parse(source, parsePosition).doubleValue() * unit[SECOND];		
		} 
		
		return sign * angle;
	}
	
	public String toString(double angle) {
		StringBuilder text = new StringBuilder(13 + decimals); // 12 characters plus the decimals, plus 1 for good measure...
	
		if(angle < 0.0) {
			angle *= -1.0;
			text.append('-');	
		}
	
		// Round the angle to the formatting resolution (here, use the quick and dirty approach...)
		// This way the rounding is correctly propagated...
		// E.g. 1:20:59.9999 -> 1:21:00 instead of the wrong 1:20:60		
		angle += 0.5 * precision * unit[SECOND];
		
		for(int level = topLevel; level <= bottomLevel; level++) {
			if(level != SECOND) {
				int value = (int) (angle / unit[level]);
				angle -= value * unit[level];
				text.append(Util.d2.format(value));
				text.append(marker[level]);
			}
			else {
				if(angle < 10.0 * unit[SECOND]) text.append('0');
				text.append(Util.f[decimals].format(angle / unit[SECOND]));
				if(marker[SECOND] != 0.0) text.append(marker[SECOND]);
			}			
		}
		
		return new String(text);		
	}
	
	public static final int DEGREE = 0;
	public static final int MINUTE = 1;
	public static final int SECOND = 2;
	
	public static final int COLONS = 0;
	public static final int DMS = 1;
	public static final int SYMBOLS = 2;
}
