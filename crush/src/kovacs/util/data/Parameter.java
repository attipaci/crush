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
package kovacs.util.data;

import java.text.*;
import java.util.*;



public class Parameter extends DataPoint {
	String name;

	public Parameter(String name) { 
		this.name = name; 
		setValue(Double.NaN);
		exact();
	}
	
	public Parameter(String name, double value) { this(name); this.setValue(value); }

	@Override
	public String toString() {
		return name + " = " + (isExact() ? Double.toString(value()) : super.toString());		
	}
	
	@Override
	public String toString(DecimalFormat f) {
		return name + " = " + (isExact() ? f.format(value()) : super.toString(f));		
	}
	
	public void parse(String text) {
		StringTokenizer tokens = new StringTokenizer(text, " \t:");
		setValue(Double.parseDouble(tokens.nextToken()));
		if(tokens.hasMoreTokens()) {
			double rms = Double.parseDouble(tokens.nextToken());
			setWeight(1.0 / (rms * rms));
		}
		else exact();
	}
	
}
