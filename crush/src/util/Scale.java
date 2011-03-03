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

package util;
// Copyright (c) 2007 Attila Kovacs 

import java.util.*;

public class Scale implements Cloneable {
	public double min, max, range, bigdiv;

	public Vector<Double> bigDivisions = new Vector<Double>();
	public Vector<Double> smallDivisions = new Vector<Double>();
	
	public boolean logarithmic = false;
	public boolean sqrt = false;
	
	public Scale () {}

	public Scale(double setmin, double setmax) {
		setScale(setmin, setmax);
	}

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public void linear() { logarithmic = false; sqrt = false; setScale(min, max); }
	
	public void log() { logarithmic = true; sqrt = false; setScale(min, max); }
	
	public void sqrt() { logarithmic = false; sqrt = true; setScale(min, max); }
	
	public double getScaled(double level) {
		if(logarithmic) return (Math.log10(level)-Math.log10(Math.abs(min))) / range;
		else if(sqrt) {
			int sign = level >= 0.0 ? 1 : -1;
			int minsign = min >= 0.0 ? 1 : -1;
			return (sign*Math.sqrt(Math.abs(level)) - minsign*Math.sqrt(Math.abs(min))) / range;
		}
		else return (level-min)/range;		
	}
	
	public double getLevel(double scaled) {
		if(logarithmic) return Math.pow(10.0, range * scaled + Math.log10(Math.abs(min)));
		else if(sqrt) {
			int sign = scaled >= 0.0 ? 1 : -1;
			int minsign = min >= 0.0 ? 1 : -1;
			double tmp = range * scaled + minsign * Math.sqrt(Math.abs(min));
			return sign * tmp * tmp;
		}
		else return range*scaled+min;
	}
	
	
	public void setScale(double setmin, double setmax) {
		min=setmin;
		max=setmax;
		
		bigDivisions.clear();
		smallDivisions.clear();
			
		if(logarithmic) {
			int loOrder = (int) Math.floor(Math.log10(Math.abs(min)));
			int hiOrder = (int) Math.ceil(Math.log10(Math.abs(max)));
			range = Math.log10(Math.abs(max)) - Math.log10(Math.abs(min));
			bigdiv = 1.0;
			double level = Math.pow(10.0, loOrder);
			for(int order = loOrder; order <= hiOrder; order++) {
				bigDivisions.add(level);
				for(int multiple = 0; multiple <= 9; multiple++) smallDivisions.add(multiple * level);
				level *= 10.0;
			}
	
		}
		else {
			range = max-min;
			int order = (int)Math.floor(Math.log10(0.5*range));
			bigdiv = Math.pow(10.0, order);
			double smalldiv = bigdiv / 10.0;
			int fromi = (int)Math.floor(min/bigdiv);
			int toi = (int)Math.ceil(max/bigdiv);
			
			for(int i=fromi; i<=toi; i++) {
				double level = i*bigdiv;
				bigDivisions.add(level);
				smallDivisions.add(level);
				for(int step=1; step<10; step++) smallDivisions.add(level + step*smalldiv);
			}
			
			if(sqrt) {
				int maxSign = max < 0.0 ? -1 : 1;
				int minSign = min < 0.0 ? -1 : 1;
				range = maxSign*Math.sqrt(Math.abs(max)) - minSign*Math.sqrt(Math.abs(min));
			}
		}
	}

}
