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
package crush.resonators;

import jnum.util.HashCode;

public class FrequencyID implements Comparable<FrequencyID> {
	public int index;
	public double freq;
	
	public FrequencyID(int index) {
		this.index = index;
	}
	
	@Override
	public int compareTo(FrequencyID other) {
		return Double.compare(freq, other.freq);
	}
	
	@Override
	public int hashCode() {
		return HashCode.get(freq);
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof FrequencyID)) return false;
		FrequencyID id = (FrequencyID) o;
		return freq == id.freq;
	}
	
	public double getExpectedFrequencyFor(double shift) {
		return freq * (1.0 + shift);
	}
	
}
