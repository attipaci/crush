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
package crush;

import kovacs.math.Vector2D;

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
		case X: return v.getX();
		case Y: return v.getY();
		case X2 : return v.getX() * v.getX();
		case Y2 : return v.getY() * v.getY();
		case X_MAGNITUDE : return Math.abs(v.getX());
		case Y_MAGNITUDE : return Math.abs(v.getY());
		case MAGNITUDE : return v.length();
		case NORM : return v.norm(); 
		default : return Double.NaN;
		}
		
	}
	
	public final static int TELESCOPE = 1<<0;
	public final static int SCANNING = 1<<1;
	public final static int CHOPPER = 1<<2;
	public final static int PROJECT_GLS = 1<<3;
	
}
