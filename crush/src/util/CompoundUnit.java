/*******************************************************************************
 * Copyright (c) 2012 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

public class CompoundUnit extends Unit {
	public ArrayList<Unit> factors = new ArrayList<Unit>();
	public ArrayList<Unit> divisors = new ArrayList<Unit>();
	
	@Override
	public String name() {
		StringBuffer name = new StringBuffer();
		
		if(factors.isEmpty()) name.append("1.0");
		else for(int i=0; i<factors.size(); i++) {
			if(i != 0) name.append(" ");
			name.append(factors.get(i).name());
		}
		
		if(!divisors.isEmpty()) {
			name.append("/");
			for(int i=0; i<divisors.size(); i++) {
				if(i != 0) name.append(" ");
				name.append(divisors.get(i).name());
			}
		}
		
		return new String(name);
	}
	
	@Override
	public double value() {
		double num = 1.0, denom = 1.0;
		for(Unit u : factors) num *= u.value();
		for(Unit u : divisors) denom *= u.value();
		return num / denom;
	}
	
}
