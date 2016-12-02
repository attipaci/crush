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

import java.text.NumberFormat;

import jnum.Copiable;
import jnum.Unit;

public class BracketedValues implements Cloneable, Copiable<BracketedValues> {
	public double start = Double.NaN, end = Double.NaN;

	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public BracketedValues copy() {
		return (BracketedValues) clone();
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
	    else return start + uName + " --> " + end + uName;
	}

	
}
