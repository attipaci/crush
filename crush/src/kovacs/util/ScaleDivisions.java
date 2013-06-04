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
// Copyright (c) 2010 Attila Kovacs 

package kovacs.util;

import java.util.*;

// TODO Better handling of custom divisions (all vs within range)

public class ScaleDivisions implements Cloneable {
	private Range range = new Range();
	private int type = LINEAR;
	private int overSampling = 1;
	
	private ArrayList<Double> divisions = new ArrayList<Double>();
	
	public ScaleDivisions () {}
	
	public ScaleDivisions (int oversampling) { this.overSampling = oversampling; }
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
		
	public void linear() { type = LINEAR; update(); }
	
	public void log() { type = LOGARITHMIC; update(); }
	
	public void sqrt() { type = SQRT; update(); }
	
	public void power() { type = POWER; update(); }
	
	
	public boolean isCustom() { return type == CUSTOM; }
	
	public boolean isLinear() { return type == LINEAR; }
	
	public boolean isLogarithmic() { return type == LOGARITHMIC; }
	
	public boolean isSqrt() { return type == SQRT; }

	public boolean isPower() { return type == POWER; }
	
	public int overSample(int n) { 
		if(overSampling != n) {
			overSampling = Math.min(1, n);
			update();
		}
		return overSampling;
	}
	
	public int getOverSampling() { return overSampling; }
	
	public ArrayList<Double> getDivisions() { return divisions; }
	
	public int size() { return divisions.size(); }
	
	public void clear() { 
		type = CUSTOM;
		divisions.clear();
	}
	
	public void addDivisions(Collection<Double> divs) {
		type = CUSTOM;
		for(double level : divs) divisions.add(level);
	}

	public void addSubDivisions(Collection<Double> divs) {
		type = CUSTOM;
	}

	public void update() {
		if(range == null) return;
		update(range);
	}
	
	public void update(Range range) {
		update(range.min(), range.max());
	}
	
	
	public void update(double setmin, double setmax) {
		range.setRange(setmin, setmax);
		
		//System.err.println("### ScaleDivisions: setting range " + range);
		
		if(isCustom()) return;
		
		divisions.clear();
		
		if(isLogarithmic()) {
			final int from = (int) Math.floor(Math.log10(Math.abs(range.min())));
			final int to = (int) Math.ceil(Math.log10(Math.abs(range.max())));

			final double increment = Math.pow(10.0, 1.0 / overSampling);
			double level = Math.pow(10.0, from);
			
			for(int order = from; order <= to; order++) {
				divisions.add(level);
				level *= increment;
			}	
		}
		else {
			final int order = (int)Math.floor(Math.log10(0.5*range.span()));	
			
			// Snap to the nearest big division (no oversampling)
			double div = Math.pow(10.0, order);
			int fromi = (int) Math.floor(range.min() / div);
			int toi = (int) Math.ceil(range.max() / div);
			double level = fromi * div;
			
			// Then oversample...
			fromi *= overSampling;
			toi *= overSampling;
			div /= overSampling;
			
			for(int i=fromi; i<=toi; i++) {
				divisions.add(level);
				level += div;
			}
		}
	}
	
	private final static int CUSTOM = -1;
	private final static int LINEAR = 0;
	private final static int LOGARITHMIC = 1;
	private final static int SQRT = 2;
	private final static int POWER = 3;
}
