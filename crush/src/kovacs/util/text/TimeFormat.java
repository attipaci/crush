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

package kovacs.util.text;

import kovacs.util.Unit;


public class TimeFormat extends AngleFormat {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6135295347868421521L;
	
	protected static final char[] colonMarker = { ':', ':', 0};
	protected static final char[] hmsMarker = { 'h', 'm', 's'};
	protected static final char[] symbolMarker = { 'h', '\'', '"'};
		
	public TimeFormat() { defaults(); }
	
	public TimeFormat(int decimals) { super(decimals); defaults(); }

	public void defaults() {
		unit = new double[] { Unit.hour, Unit.min, Unit.sec };	
		marker = hmsMarker;	
		wraparound = 24.0 * Unit.hour;
		oneSided = true;
	}
	
	@Override
	public void setSeparator(int type) {
		switch(type) {
		case COLONS: marker = colonMarker; break;
		case HMS: marker = hmsMarker; break;
		case SYMBOLS: marker = symbolMarker; break;
		default: marker = colonMarker;
		}	
	}
	
	@Override
	public int getSeparator() {
		if(marker == colonMarker) return COLONS;
		else if(marker == hmsMarker) return HMS;
		else if(marker == symbolMarker) return SYMBOLS;	
		else return -1;
	}
	
	@Override
	public void colons() { marker = colonMarker; }
	
	@Override
	public void letters() { marker = hmsMarker; }
	
	@Override
	public void symbols() { marker = symbolMarker; }
	
	
	@Override
	public void setTopLevel(int level) { 
		if(level < DEGREE || level > SECOND) throw new IllegalArgumentException("Undefined " + getClass().getSimpleName() + " level.");
		topLevel = level; 	
	}
	
	@Override
	public void setBottomLevel(int level) { 
		if(level < DEGREE || level > SECOND) throw new IllegalArgumentException("Undefined " + getClass().getSimpleName() + " level.");
		bottomLevel = level; 		
	}
	
	public static final int HOUR = AngleFormat.DEGREE;
	public static final int MINUTE = AngleFormat.MINUTE;
	public static final int SECOND = AngleFormat.SECOND;
	
	public static final int COLONS = 0;
	public static final int HMS = 1;
	public static final int SYMBOLS = 2;


}
