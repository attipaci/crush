/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2010 Attila Kovacs 

package util.dirfile;

import java.util.*;
import java.io.*;
import util.*;

public class LinearCombinationStore extends DataStore<Double> {
	private ArrayList<DataStore<?>> terms = new ArrayList<DataStore<?>>();
	private ArrayList<Vector2D> coeffs = new ArrayList<Vector2D>();
	private ArrayList<Double> indexScale = new ArrayList<Double>();

	public LinearCombinationStore(String name) {
		super(name);
	}
	
	public synchronized void addTerm(DataStore<?> store, double a, double b) {
		terms.add(store);
		coeffs.add(new Vector2D(a, b));
		if(indexScale.isEmpty()) indexScale.add(1.0);
		else indexScale.add((double) store.getSamples() / getSamples());
	}

	@Override
	public Double get(long n) throws IOException {
		double value = 0.0;
		
		for(int i=0; i<terms.size(); i++) {
			Vector2D coeff = coeffs.get(i);
			value += terms.get(i).get(Math.round(n * indexScale.get(i))).doubleValue() * coeff.x + coeff.y;
		}
		
		return value;
	}
	
	@Override
	public int getSamples() {
		return terms.get(0).getSamples();
	}
	
	@Override
	public long length() throws IOException {
		return terms.get(0).length();
	}

	
}
