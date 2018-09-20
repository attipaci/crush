/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope.sofia;

import java.text.NumberFormat;

import jnum.Copiable;
import jnum.Unit;

public class BracketedValues implements Cloneable, Copiable<BracketedValues> {
	public double start = Double.NaN, end = Double.NaN;

	public BracketedValues() {
	    this(Double.NaN, Double.NaN);
	}
	
	public BracketedValues(double start, double end) {
	    this.start = start;
	    this.end = end;
	}
	
	@Override
	public BracketedValues clone() {
		try { return (BracketedValues) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public BracketedValues copy() {
		return clone();
	}
	
	public void merge(BracketedValues bounds) {
		if(bounds.start < start) start = bounds.start;
		if(bounds.end > end) end = bounds.end;
	}

	public double midPoint() {
		if(Double.isNaN(end)) return start;
		else if(Double.isNaN(start)) return end;
		return 0.5 * (start + end);
	}
	
	@Override
    public String toString() {
	    return toString(null, null);
	}

	public String toString(NumberFormat nf, Unit u) {
	    double uValue = u == null ? 1.0 : u.value();
	    String uName = u == null ? "" : " " + u.name();
	    
	    if(nf != null) return nf.format(start / uValue) + uName + " --> " + nf.format(end / uValue) + uName;
	    return start + uName + " --> " + end + uName;
	}

	
}
