/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package util;

import java.util.*;

// TODO Better handling of custom divisions (all vs within range)

public class ScaleDivisions implements Cloneable {
	protected Range range;
	protected int type = LINEAR;
	
	protected ArrayList<Double> divisions = new ArrayList<Double>();
	protected ArrayList<Double> subDivisions = new ArrayList<Double>();
	
	public ScaleDivisions () {}

	public ScaleDivisions(double setmin, double setmax) {
		updateDivs(setmin, setmax);
	}

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	
	public void linear() { type = LINEAR; updateDivs(); }
	
	public void log() { type = LOGARITHMIC; updateDivs(); }
	
	public void sqrt() { type = SQRT; updateDivs(); }
	
	public void power() { type = POWER; updateDivs(); }
	
	
	public boolean isCustom() { return type == CUSTOM; }
	
	public boolean isLinear() { return type == LINEAR; }
	
	public boolean isLogarithmic() { return type == LOGARITHMIC; }
	
	public boolean isSqrt() { return type == SQRT; }

	public boolean isPower() { return type == POWER; }
	
	
	public ArrayList<Double> getDivisions() { return divisions; }
	
	public ArrayList<Double> getSubDivisions() { return subDivisions; }
	
	
	public void clear() { 
		type = CUSTOM;
		divisions.clear();
		subDivisions.clear();
	}
	
	public void addDivisions(Collection<Double> divs) {
		type = CUSTOM;
		for(double level : divs) divisions.add(level);
	}

	public void addSubDivisions(Collection<Double> divs) {
		type = CUSTOM;
		for(double level : divs) subDivisions.add(level);
	}

	public void updateDivs() {
		updateDivs(range);
	}
	
	public void updateDivs(Range range) {
		updateDivs(range.min, range.max);
	}
	
	
	public void updateDivs(double setmin, double setmax) {
		range.setRange(setmin, setmax);
		
		if(isCustom()) return;
		
		divisions.clear();
		subDivisions.clear();		
		
		if(isLogarithmic()) {
			final int loOrder = (int) Math.floor(Math.log10(Math.abs(range.min)));
			final int hiOrder = (int) Math.ceil(Math.log10(Math.abs(range.max)));

			double level = Math.pow(10.0, loOrder);
			for(int order = loOrder; order <= hiOrder; order++) {
				divisions.add(level);
				subDivisions.add(level);
				for(int multiple = 2; multiple < 10; multiple++) subDivisions.add(multiple * level);
				level *= 10.0;
			}
	
		}
		else {
			final int order = (int)Math.floor(Math.log10(0.5*range.span()));
			
			final double bigdiv = Math.pow(10.0, order);
			final double smalldiv = bigdiv / 10.0;
			final int fromi = (int)Math.floor(range.min/bigdiv);
			final int toi = (int)Math.ceil(range.max/bigdiv);
			
			double level = fromi * bigdiv;
			
			for(int i=fromi; i<=toi; i++) {
				divisions.add(level);
				subDivisions.add(level);
				for(int step=10; --step > 0; ) {
					level += smalldiv;
					subDivisions.add(level);
				}
			}
		}
	}
	
	private final static int CUSTOM = -1;
	private final static int LINEAR = 0;
	private final static int LOGARITHMIC = 1;
	private final static int SQRT = 2;
	private final static int POWER = 3;
}
