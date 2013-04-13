/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.mako;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ToneList extends ArrayList<MakoPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7951574970001263947L;

	public ToneList(Mako mako) {
		addAll(mako);
		
		Collections.sort(this, new Comparator<MakoPixel>() {
			public int compare(MakoPixel arg0, MakoPixel arg1) {
				return Double.compare(arg0.toneFrequency, arg1.toneFrequency);
			}
		});
	}
	
	public int indexBefore(double f) throws ArrayIndexOutOfBoundsException {
		int i = 0;
		int step = size() >> 1;

		
		if(get(0).toneFrequency > f) 
			throw new ArrayIndexOutOfBoundsException("Specified point precedes lookup range.");
		
		if(get(size() - 1).toneFrequency < f) 
			throw new ArrayIndexOutOfBoundsException("Specified point is beyond lookup range.");
		
		
		while(step > 0) {
			if(get(i + step).toneFrequency < f) i += step;
			step >>= 1;
		}
		
		return i;
	}
	
	public int getNearestIndex(double f) {	
		try {
			int lower = indexBefore(f);
			int upper = lower+1;
			if(f - get(lower).toneFrequency < get(upper).toneFrequency - f) return lower;
			return upper;
		}
		catch(ArrayIndexOutOfBoundsException e) {
			if(f < get(0).toneFrequency) return 0;
			return size() - 1;
		}
	}
}
