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
package util;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;

public class CompoundUnit extends Unit {
	public ArrayList<PowerUnit> factors = new ArrayList<PowerUnit>();
	public ArrayList<PowerUnit> divisors = new ArrayList<PowerUnit>();
	
	public CompoundUnit() {}
	
	public CompoundUnit(String spec, PowerUnit template) { super(spec); }
	
	@Override
	public String name() {
		StringBuffer name = new StringBuffer();
		
		if(factors.isEmpty()) name.append("1");
		else for(int i=0; i<factors.size(); i++) {
			if(i != 0) name.append(" ");
			String uName = factors.get(i).name();
			if(uName.length() > 0) name.append(uName);
		}
		
		if(!divisors.isEmpty()) {
			name.append("/");
			for(int i=0; i<divisors.size(); i++) {
				if(i != 0) name.append(" ");
				String uName = divisors.get(i).name();
				if(uName.length() > 0) name.append(uName);
			}
		}
		
		return new String(name);
	}
	
	public void multiplyBy(Unit u) {
		if(divisors.contains(u)) divisors.remove(u);
		
		for(PowerUnit pu : divisors) if(pu.getBase().equals(u)) {
			pu.setExponent(pu.getExponent() - 1.0);
			return;
		}
		
		for(PowerUnit pu : factors) if(pu.getBase().equals(u)) {
			pu.setExponent(pu.getExponent() + 1.0);
			return;
		}
		
	}
	
	
	@Override
	public double value() {
		double num = 1.0, denom = 1.0;
		for(Unit u : factors) num *= u.value();
		for(Unit u : divisors) denom *= u.value();
		return num / denom;
	}
	
	@Override
	public void setMultiplier(Multiplier m) { 
		if(!factors.isEmpty()) factors.get(0).setMultiplier(m);	
	}
	
	public void parse(String spec, Hashtable<String, Unit> baseUnits) {	
		if(factors == null) factors = new ArrayList<PowerUnit>();
		else factors.clear();
		
		if(divisors == null) divisors = new ArrayList<PowerUnit>();
		else divisors.clear();
		
		int index = spec.contains("/") ? spec.lastIndexOf('/') : spec.length();
		StringTokenizer numerator = new StringTokenizer(spec.substring(0, index));
		StringTokenizer denominator = new StringTokenizer(spec.substring(index+1, spec.length()));
		
		while(numerator.hasMoreTokens()) factors.add(PowerUnit.get(numerator.nextToken(), baseUnits));
		while(denominator.hasMoreTokens()) divisors.add(PowerUnit.get(denominator.nextToken(), baseUnits));
	}
	
	
}
