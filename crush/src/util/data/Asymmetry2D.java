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

package util.data;

import util.Unit;


public class Asymmetry2D {
	DataPoint x, y;
	
	public DataPoint getX() { return x; }
	
	public DataPoint getY() { return y; }
	
	public void setX(DataPoint value) { this.x = value; }
	
	public void setY(DataPoint value) { this.y = value; }
	
	@Override
	public String toString() {
		if(x == null && y == null) return "Asymmetry: empty";
		
		return "  Asymmetry: " 
				+ (x == null ? "" : "x = " + x.toString(Unit.get("%")) + (y == null ? "" : ", ")) 
				+ (y == null ? "" : "y = " + y.toString(Unit.get("%")));	
	}
}
