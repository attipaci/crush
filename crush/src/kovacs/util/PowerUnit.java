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
package kovacs.util;

import java.util.Hashtable;

public class PowerUnit extends Unit {
	private Unit base;
	private double exponent;
	private boolean bracketBase;
	
	public PowerUnit() { exponent = 1.0; }
	
	public PowerUnit(Unit base, double power) {
		set(base, power);
	}
	
	public Unit getBase() { return base; }
	
	public void set(Unit base, double exponent) {
		this.base = base;
		this.exponent = exponent;
		bracketBase = !base.getClass().equals(Unit.class);		
	}
	
	@Override
	public void setMultiplier(Multiplier m) { base.setMultiplier(m); }
	
	@Override
	public Multiplier getMultiplier() { return base.getMultiplier(); }
	
	@Override
	public String name() {		
		
		if(exponent == 1.0) return base.name();
		else if(exponent == 0.0) return "";
		
		int iExponent = (int)Math.round(exponent);
		if(exponent == iExponent) {
			return (bracketBase ? "(" + base.name() + ")" : base.name())
					+ "^" + (iExponent < 0 ? "{" + iExponent + "}" : iExponent);
		}
		
		return (bracketBase ? "(" + base.name() + ")" : base.name())
				+ "^" + (exponent < 0.0 ? "{" + exponent + "}" : exponent);
	}
	
	@Override
	public double value() {
		return Math.pow(base.value(), exponent);
	}
	
	public double getExponent() { return exponent; }
	
	public void setExponent(double value) { this.exponent = value; }
	
	public static PowerUnit get(String id) throws IllegalArgumentException {
		return get(id, standardUnits);
	}
	
	public static PowerUnit get(String id, Hashtable<String, Unit> baseUnits) throws IllegalArgumentException {
		if(id.contains("^")) return get(id, id.indexOf('^'), baseUnits);
		else if(id.contains("**")) return get(id, id.indexOf("**"), baseUnits);
		else return new PowerUnit(Unit.get(id, baseUnits), 1.0);
	}
	
	private static PowerUnit get(String value, int index, Hashtable<String, Unit> baseUnits) throws IllegalArgumentException {
		PowerUnit u = new PowerUnit(Unit.get(value.substring(0, index), baseUnits), 1.0);
		
		if(value.startsWith("{") || value.startsWith("(")) 
			u.exponent = Double.parseDouble(value.substring(index + 1, value.length()-1));
		else u.exponent = Double.parseDouble(value.substring(index+1));
		return u;
	}
	
	
}
