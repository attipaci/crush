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
// Copyright (c) 2010 Attila Kovacs 

package util;
import java.util.*;

public class SimpleDate implements Comparable<SimpleDate> {

	byte year, month, day;
	
	public SimpleDate() {}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof SimpleDate)) return false; 
		return compareTo((SimpleDate) o) == 0;
	}
	
	@Override
	public int hashCode() {
		return (year << 16) | (month << 8) | day;
	}
	
	public void parse(String date) {
		StringTokenizer tokens = new StringTokenizer(date, ".-");
		year = Byte.parseByte(tokens.nextToken());
		month = Byte.parseByte(tokens.nextToken());
		day = Byte.parseByte(tokens.nextToken());
	}

	@Override
	public String toString() {
		return year + "-" + month + "-" + day;
	}
	
	public int compareTo(SimpleDate other) {
		if(year < other.year) return -1;
		else if(year > other.year) return 1;
		else if(month < other.month) return -1;
		else if(month > other.month) return 1;
		else if(day < other.day) return -1;
		else if(day > other.day) return 1;
		else return 0;
	}
	
}
