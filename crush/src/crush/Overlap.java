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

package crush;

import java.io.Serializable;

import jnum.util.HashCode;

public class Overlap implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7567199967596593068L;
	Channel a, b;
	double value;
	
	public Overlap(Channel a, Channel b, double overlap) {
		this.a = a;
		this.b = b;
		this.value = overlap;
	}
	
	@Override
	public int hashCode() {
		int hash = super.hashCode() ^ HashCode.from(value);
		if(a != null) hash ^= a.hashCode();
		if(b != null) hash ^= b.hashCode();
		return hash;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof Overlap)) return false;
		if(!super.equals(o)) return false;
		Overlap other = (Overlap) o;
		
		if(value != other.value) return false;
		
		if(a == other.a && b == other.b) return true;
		if(a == other.b && b == other.a) return true;
		
		return false;
	}
}
