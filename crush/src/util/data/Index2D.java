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

package util.data;

import util.*;

public class Index2D implements Cloneable {
	private int i,j;
	
	public Index2D() {}
	
	public Index2D(int i, int j) {
		set(i, j);
	}
	
	public Index2D(Vector2D index) {
		this((int)Math.round(index.getX()), (int)Math.round(index.getY()));
	}

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public int hashCode() {
		return i ^ ~j;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof Index2D)) return false;
		Index2D index = (Index2D) o;
		if(index.i != i) return false;
		if(index.j != j) return false;
		return true;		
	}
	
	public void set(int i, int j) { this.i = i; this.j = j; }
	
	public final int i() { return i; }
	
	public final int j() { return j; }
 	
	@Override
	public String toString() {
		return i + "," + j;
	}

}
