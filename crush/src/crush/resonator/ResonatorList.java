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
package crush.resonator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;

import crush.Channel;

public class ResonatorList<ResonatorType extends Resonator> extends ArrayList<ResonatorType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7951574970001263947L;

	public ResonatorList(int size) {
		ensureCapacity(size);
	}

	public ResonatorList(Collection<ResonatorType> resonators) {
		addAll(resonators);
	}

	public void sort() {
		Collections.sort(this, new Comparator<Resonator>() {
			@Override
			public int compare(Resonator arg0, Resonator arg1) {
				return Double.compare(arg0.getFrequency(), arg1.getFrequency());
			}
		});	
	}
	
	public void assignTo(Collection<? extends Resonator> referenceList) {
		int assigned = 0;
		
		Hashtable<FrequencyID, Resonator> lookup = new Hashtable<FrequencyID, Resonator>(referenceList.size());
		for(Resonator reference : referenceList) if(reference.getFrequencyID() != null) 
			lookup.put(reference.getFrequencyID(), reference);
		
		for(Resonator reference : referenceList) if(reference.isAssigned()) {
			Resonator resonator = lookup.get(reference.getFrequencyID());
			if(resonator == null) continue;
			
			if(resonator.assignTo(reference)) assigned++;
		}	
		
		System.err.println(" Assigned " + assigned + " of " + size() + " resonators to references.");		
	}


	public int indexBefore(double f) throws ArrayIndexOutOfBoundsException {
		int i = 0;
		int step = size() >> 1;


		if(get(0).getFrequency() > f) 
			throw new ArrayIndexOutOfBoundsException("Specified point precedes lookup range.");

		if(get(size() - 1).getFrequency() < f) 
			throw new ArrayIndexOutOfBoundsException("Specified point is beyond lookup range.");


		while(step > 0) {
			if(get(i + step).getFrequency() < f) i += step;
			step >>= 1;
		}

		while(get(i).getChannel().isFlagged(Channel.FLAG_BLIND)) if(--i < 0) return i; 

		return i;
	}

	public ResonatorType getNearest(double f) {
		return get(getNearestIndex(f));
	}

	public int getNearestIndex(double f) {	
		try {
			int lower = indexBefore(f);		
			int upper = lower+1;

			while(get(upper).getChannel().isFlagged(Channel.FLAG_BLIND)) if(++upper == size()) return lower;	
			if(lower < 0) return upper;

			if(f - get(lower).getFrequency() < get(upper).getFrequency() - f) return lower;
			return upper;
		}
		catch(ArrayIndexOutOfBoundsException e) {
			if(f < get(0).getFrequency()) return 0;
			return size() - 1;
		}
	}
}

