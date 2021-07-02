/* *****************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
package crush.motion;

import jnum.math.Vector2D;

public enum Motion {
	X("x"), Y("y"), Z("z"), 
	X2("x^2"), Y2("y^2"), Z2("z^2"), 
	X_MAGNITUDE("|x|"), Y_MAGNITUDE("|y|"), Z_MAGNITUDE("|z|"), 
	MAGNITUDE("mag"), 
	NORM("norm");
	
	public String id;
	
	private Motion(String id) {
		this.id = id;
	}
	
	public String getID() { return id; } 
	
	public static Motion forName(String name) {
		name = name.toLowerCase();
		for(Motion m : Motion.values()) if(m.id.equals(name)) return m;
		return null;
	}
	
	public double getValue(Vector2D v) {
		switch(this) {
		case X: return v.x();
		case Y: return v.y();
		case X2 : return v.x() * v.x();
		case Y2 : return v.y() * v.y();
		case X_MAGNITUDE : return Math.abs(v.x());
		case Y_MAGNITUDE : return Math.abs(v.y());
		case MAGNITUDE : return v.length();
		case NORM : return v.absSquared(); 
		default : return Double.NaN;
		}
		
	}
	
	public static final int TELESCOPE = 1<<0;
	public static final int SCANNING = 1<<1;
	public static final int CHOPPER = 1<<2;
	public static final int PROJECT_GLS = 1<<3;
	
}
