/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package util;

import java.text.NumberFormat;

public class Datum {
	public double value;
	public String name, unitName;
	public String comment;
	
	public Datum(String name, double value, String unitName) {
		this(name, value, unitName, "");
	}
	
	public Datum(String name, double value, String unitName, String comment) {
		this.name = name;
		this.value = value;
		this.unitName = unitName;	
		this.comment = comment;
	}

	@Override
	public String toString() {
		return name + " = " + value + " " + unitName + (comment.length() > 0 ? " (" + comment + ")" : "");
	}
	
	public String toString(NumberFormat nf) {
		return name + " = " + nf.format(value) + " " + unitName + (comment.length() > 0 ? " (" + comment + ")" : "");
	}
	
}

