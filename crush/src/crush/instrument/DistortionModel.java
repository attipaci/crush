/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.instrument;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;

import jnum.Configurator;
import jnum.Unit;
import jnum.data.fitting.Parameter;
import jnum.math.Vector2D;



public class DistortionModel extends Hashtable<DistortionModel.Term, Parameter> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2378434929629074050L;
	private String id = "[n/a]";
	private Unit unit = Unit.unity;
	
	public DistortionModel() {}
	
	public DistortionModel(Configurator options) {
		this();
		setOptions(options);
	}
	
	public void setOptions(Configurator options) {
		clear();
		
		for(String key : options.branches.keySet()) {
			if(key.length() != 3) continue;
			int dir = key.charAt(0);
			int xExp = key.charAt(1) - '0';
			int yExp = key.charAt(2) - '0';
			if(xExp < 0 || yExp < 0) continue;
			double value = options.option(key).getDouble();
			if(dir == 'x') setX(xExp, yExp, value);
			else if(dir == 'y') setY(xExp, yExp, value);	
		}
		
		if(options.containsKey("unit")) unit = Unit.get(options.option("unit").getValue());
		if(options.containsKey("name")) id = options.option("name").getValue();
	}
	
	public String getName() { return id; }
	
	public Unit getUnit() { return unit; }
	
	public void setUnit(Unit u) { this.unit = u; }
	
	public void scale(double x) {
		for(Term term : keySet()) get(term).scale(x);
	}
	
	public void set(int xExp, int yExp, double cx, double cy) {
		setX(xExp, yExp, cx);
		setY(xExp, yExp, cy);
	}
	
	public Term getTerm(String dir, int xExp, int yExp) {
	    Term match = new Term(dir, xExp, yExp);
        for(Term term : keySet()) if(term.equals(match)) return term;
        Parameter p = match.createParameter(0.0);
        put(match, p);
        return match;
	}
	
	public Parameter getParameter(String dir, int xExp, int yExp) {
	    return get(getTerm(dir, xExp, yExp));
	}
	
	public void setX(int xExp, int yExp, double value) {
		Term term = new Term("x", xExp, yExp);
		if(containsKey(term)) get(term).setValue(value);
		else put(term, term.createParameter(value));		
	}
	
	public void setY(int xExp, int yExp, double value) {
		Term term = new Term("y", xExp, yExp);
		if(containsKey(term)) get(term).setValue(value);
		else put(term, term.createParameter(value));		
	}
	
	public void distort(Vector2D v) { v.add(getValue(v)); }
	
	public Vector2D getValue(Vector2D v) {
		return getValue(v.x(), v.y());
	}
	
	public Vector2D getValue(double x, double y) {
		Vector2D sum = new Vector2D();
		for(Term term : keySet()) {
		    double termValue = get(term).value() * term.getValue(x / unit.value(), y / unit.value());
		    
		    if(term.dir == "x") sum.addX(termValue);
		    else if(term.dir == "y") sum.addY(termValue);
		}
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
	
	public class Term implements Comparable<Term>, Serializable {
		/**
         * 
         */
        private static final long serialVersionUID = 8495738757424333815L;
        String dir;
        int xExp, yExp;
		
		public Term(String dir, int expX, int expY) {
		    this.dir = dir; this.xExp = expX; this.yExp = expY; 
		}
		
		public double getValue(double x, double y) {
			return Math.pow(x, xExp) * Math.pow(y, yExp);			
		}

		@Override
		public int compareTo(Term arg0) {
			if(equals(arg0)) return 0;
			if(xExp < arg0.xExp) return -1;
			if(xExp > arg0.xExp) return 1;
			if(yExp < arg0.yExp) return -1;
			return dir.compareTo(arg0.dir);
		}
		
		@Override
		public String toString() {
			return dir + "[" + xExp + "," + yExp + "]";
		}
		
		public double getStepSize() {
		    return Math.pow(0.01, 0.5*(xExp+yExp+1));
		}
		
		public Parameter createParameter(double value) {
		    return new Parameter(toString(), value, getStepSize());
		}
	}
	
}
