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

package crush.mustang2;

import kovacs.util.Copiable;

public class Mustang2Readout implements Cloneable, Copiable<Mustang2Readout> {
	private int index;
	double bias;
	double heater;
	
	public Mustang2Readout(int index) { 
		this.index = index;
	}	
	
	public int getIndex() { return index; }

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public Mustang2Readout copy() {
		return (Mustang2Readout) clone();
	}
	
	
}
