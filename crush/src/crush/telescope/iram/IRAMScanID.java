/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.telescope.iram;


import java.util.StringTokenizer;

import jnum.Util;
import jnum.math.Range;


// 2012-04-12.138
// 01234567890123

public class IRAMScanID implements Comparable<IRAMScanID> {
	int year, month, day, scanNumber;
	
	public IRAMScanID() {}
	
	public IRAMScanID(String text) throws NumberFormatException { parse(text); }
	
	@Override
	public int hashCode() {
		return year ^ ~month ^ (day << 8) ^ (scanNumber << 12);  
	}
	
	@Override
	public boolean equals(Object o) {
		if(!super.equals(o)) return false;
		IRAMScanID other = (IRAMScanID) o;
		if(year != other.year) return false;
		if(month != other.month) return false;
		if(day != other.day) return false;
		if(scanNumber != other.scanNumber) return false;
		return true;		
	}
	
	public void parse(String id) throws NumberFormatException {
		if(!(id.charAt(4) == '-' && id.charAt(7) == '-' && id.charAt(10) == '.')) 
			throw new NumberFormatException(" Not a valid IRAM scan ID: '" + id + "'.");
		
		year = Integer.parseInt(id.substring(0, 4));
		month = Integer.parseInt(id.substring(5, 7));
		day = Integer.parseInt(id.substring(8, 10));
		scanNumber = Integer.parseInt(id.substring(11));
	}
	
	@Override
	public String toString() {
		return Util.d4.format(year) + "-" + Util.d2.format(month) + "-" + Util.d2.format(day) 
				+ "." + scanNumber;
	}
	
	// return a double that is equal to YYYYMMDD.nnnn
	public double asDouble() {
		return 1e4 * year + 100.0 * month + day + 1e-4 * scanNumber;
	}

	@Override
	public int compareTo(IRAMScanID other) {
		if(year != other.year) return year < other.year ? -1 : 1;
		if(month != other.month) return month < other.month ? -1 : 1;
		if(day != other.day) return day < other.day ? -1 : 1;
		if(scanNumber != other.scanNumber) return scanNumber < other.scanNumber ? -1 : 1;
		return 0;
	}
	
	public static Range rangeFor(String rangeSpec) {
		StringTokenizer tokens = new StringTokenizer(rangeSpec, ":");
		Range scanRange = new Range();

		String spec = tokens.nextToken();
		scanRange.setMin(spec.equals("*") ? Double.NEGATIVE_INFINITY : new IRAMScanID(spec).asDouble());
		scanRange.setMax(Double.POSITIVE_INFINITY);
		if(tokens.hasMoreTokens()) {
			spec = tokens.nextToken();
			scanRange.setMax(spec.equals("*") ? Double.POSITIVE_INFINITY : new IRAMScanID(spec).asDouble());
		}

		return scanRange;
	}
	
}
