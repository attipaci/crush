/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sofia;

import kovacs.util.Unit;
import kovacs.util.Util;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public abstract class SofiaHeaderData implements Cloneable {

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public abstract void parseHeader(Header header);
	
	public abstract void editHeader(Cursor cursor) throws HeaderCardException;
	
	public float getHeaderFloat(double value) {
		if(Double.isNaN(value)) return UNKNOWN_FLOAT_VALUE;
		return (float) value;
	}
	
	public static String getStringValue(Header header, String key) {
		return header.containsKey(key) ? header.getStringValue(key) : null;
	}
	
	public static double getHMSTime(Header header, String key) {
		String value = header.getStringValue(key);
		if(value == null) return header.getDoubleValue(key, Double.NaN) * Unit.hour; 
		return Util.parseTime(value);
	}
	
	public static double getDMSAngle(Header header, String key) {
		String value = header.getStringValue(key);
		if(value == null) return header.getDoubleValue(key, Double.NaN) * Unit.deg; 
		return Util.parseAngle(value);
	}
	
	public final static int UNKNOWN_INT_VALUE = -9999;
	public final static float UNKNOWN_FLOAT_VALUE = -9999.0F;
	public final static String UNKNOWN_STRING_VALUE = "UNKNOWN";
	
}
