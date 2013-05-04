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
import java.util.Hashtable;

import crush.Channel;

public class ResonanceList extends ArrayList<MakoPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7951574970001263947L;

	public ResonanceList(int size) {
		ensureCapacity(size);
	}
	
	public ResonanceList(Mako mako) {
		addAll(mako);
		
	}
	
	public void sort() {
		Collections.sort(this, new Comparator<MakoPixel>() {
			public int compare(MakoPixel arg0, MakoPixel arg1) {
				return Double.compare(arg0.toneFrequency, arg1.toneFrequency);
			}
		});	
	}
	
	public void assign(Mako mako) {
		int assigned = 0;
		
		Hashtable<ResonanceID, MakoPixel> lookup = new Hashtable<ResonanceID, MakoPixel>(mako.size());
		for(MakoPixel channel : mako) if(channel.id != null) lookup.put(channel.id, channel);
		
		for(MakoPixel pixel : this) {
			if(pixel.id == null) continue;
			if(pixel.row < 0) continue;
			if(pixel.col < 0) continue;
			
			MakoPixel channel = lookup.get(pixel.id);
			if(channel == null) continue;
			
			channel.row = pixel.row;
			channel.col = pixel.col;
			channel.setFixedIndex(pixel.row * Mako.cols + pixel.col);
			channel.unflag(MakoPixel.FLAG_UNASSIGNED);
			
			assigned++;
		}	
		
		System.err.println(" Assigned " + assigned + " of " + size() + " resonances to pixels.");		
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
		
		while(get(i).isFlagged(Channel.FLAG_BLIND)) if(--i < 0) return i; 
		
		return i;
	}
	
	public MakoPixel getNearest(double f) {
		return get(getNearestIndex(f));
	}
	
	public int getNearestIndex(double f) {	
		try {
			int lower = indexBefore(f);		
			int upper = lower+1;
		
			while(get(upper).isFlagged(Channel.FLAG_BLIND)) if(++upper == size()) return lower;	
			if(lower < 0) return upper;
			
			if(f - get(lower).toneFrequency < get(upper).toneFrequency - f) return lower;
			return upper;
		}
		catch(ArrayIndexOutOfBoundsException e) {
			if(f < get(0).toneFrequency) return 0;
			return size() - 1;
		}
	}
}
