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

import util.Unit;

public class HourAngleFormat extends TimeFormat {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -6260375852141250856L;
	
	public HourAngleFormat() { super(); setOneSided(true); }
		
	public HourAngleFormat(int decimals) { super(decimals); setOneSided(true); }
	
	@Override
	public StringBuffer format(double angle, StringBuffer toAppendTo, FieldPosition pos) {
		return super.format(angle / Unit.timeAngle, toAppendTo, pos);
	}
	
	@Override
	public Number parse(String source, ParsePosition parsePosition) {
		return super.parse(source, parsePosition).doubleValue() * Unit.timeAngle;
	}	

}
