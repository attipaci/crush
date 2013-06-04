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
package kovacs.util;

import java.text.NumberFormat;

public class Datum {
	private double value;
	private String name, unitName;
	private String comment;
	
	public Datum(String name, double value, String unitName) {
		this(name, value, unitName, "");
	}
	
	public Datum(String name, double value, String unitName, String comment) {
		this.name = name;
		this.value = value;
		this.unitName = unitName;	
		this.comment = comment;
	}

	public double getValue() { return value; }
	
	public String getName() { return name; }
	
	public String getUnitName() { return unitName; }
	
	public String getComment() { return comment; }
	
	public void setValue(double x) { this.value = x; }
	
	public void setName(String value) { this.name = value; }
	
	public void setUnitName(String value) { this.unitName = value; }
	
	public void setComment(String value) { this.comment = value; }
	
	@Override
	public String toString() {
		return name + " = " + value + " " + unitName + (comment.length() > 0 ? " (" + comment + ")" : "");
	}
	
	public String toString(NumberFormat nf) {
		return name + " = " + nf.format(value) + " " + unitName + (comment.length() > 0 ? " (" + comment + ")" : "");
	}
	
}

