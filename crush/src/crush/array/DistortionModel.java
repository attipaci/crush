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
package crush.array;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;

import util.Configurator;
import util.HashCode;
import util.Unit;
import util.Vector2D;

public class DistortionModel extends Hashtable<DistortionModel.Term, Vector2D> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2378434929629074050L;
	private Unit unit = Unit.unity;
	
	public void setOptions(Configurator options) {
		clear();
		
		for(String key : options.branches.keySet()) {
			if(key.length() != 3) continue;
			int dir = key.charAt(0);
			int xExp = key.charAt(1) - '0';
			int yExp = key.charAt(2) - '0';
			if(xExp < 0 || yExp < 0) continue;
			double value = options.get(key).getDouble();
			if(dir == 'x') setX(xExp, yExp, value);
			else if(dir == 'y') setY(xExp, yExp, value);	
		}
		
		if(options.containsKey("unit")) {
			unit = Unit.get(options.get("unit").getValue());
		}
	}
	
	public Unit getUnit() { return unit; }
	
	public void setUnit(Unit u) { this.unit = u; }
	
	public void scale(double x) {
		for(Vector2D c : values()) c.scale(x);
	}
	
	public void set(int xExp, int yExp, double cx, double cy) {
		Term term = new Term(xExp, yExp);
		
		if(containsKey(term)) get(term).set(cx, cy);
		else put(term, new Vector2D(cx, cy));	
		
	}
	
	public void setX(int xExp, int yExp, double value) {
		Term term = new Term(xExp, yExp);
		
		if(containsKey(term)) get(term).setX(value);
		else put(term, new Vector2D(value, 0.0));		
	}
	
	public void setY(int xExp, int yExp, double value) {
		Term term = new Term(xExp, yExp);
		
		if(containsKey(term)) get(term).setY(value);
		else put(term, new Vector2D(0.0, value));		
	}
	
	public void distort(Vector2D v) { v.add(getValue(v)); }
	
	public Vector2D getValue(Vector2D v) {
		return getValue(v.getX(), v.getY());
	}
	
	public Vector2D getValue(double x, double y) {
		Vector2D sum = new Vector2D();
		for(Term term : keySet()) sum.addMultipleOf(get(term), term.getValue(x / unit.value(), y / unit.value()));	
		sum.scale(unit.value());
		return sum;	
	}

	@Override
	public String toString() {
		ArrayList<Term> sorted = new ArrayList<Term>(keySet());
		Collections.sort(sorted);
		
		StringBuffer buf = new StringBuffer();
		
		for(Term term : sorted) {
			buf.append("  " + term.toString() + ": ");
			buf.append(get(term).toString() + "\n");
		}
		
		return "Distortion:\n" + new String(buf);
	}
	
	public class Term implements Comparable<Term> {
		int xExp, yExp;
		
		public Term(int expX, int expY) { this.xExp = expX; this.yExp = expY; }

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof Term)) return false;
			Term term = (Term) o;
			if(term.xExp != xExp) return false;
			if(term.yExp != yExp) return false;
			return true;			
		}
		
		@Override
		public int hashCode() {
			return HashCode.get(xExp) ^ HashCode.get(yExp);
		}
		
		public double getValue(double x, double y) {
			return Math.pow(x, xExp) * Math.pow(y, yExp);			
		}

		public int compareTo(Term arg0) {
			if(equals(arg0)) return 0;
			if(xExp < arg0.xExp) return -1;
			if(xExp > arg0.xExp) return 1;
			if(yExp < arg0.yExp) return -1;
			return 1;
		}
		
		@Override
		public String toString() {
			return xExp + "," + yExp;
		}
	}
}
