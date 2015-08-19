/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.scuba2;



import kovacs.math.Vector2D;
import kovacs.util.*;

public class Scuba2Subarray implements Cloneable {
	Scuba2 scuba2;
	String id;
	
	Vector2D focalPlanePixelOffset = new Vector2D();
	double orientation = 0.0;
	boolean isMirrored = false;
	public double scaling = 1.0;
	
	
	public Scuba2Subarray(Scuba2 parent, String id) {
		scuba2 = parent;
		this.id = id;
		setOptions();
	}
	
	@Override 
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public Scuba2Subarray copy() {
		Scuba2Subarray copy = (Scuba2Subarray) clone();
		if(focalPlanePixelOffset != null) copy.focalPlanePixelOffset = (Vector2D) focalPlanePixelOffset.clone();
		if(id != null) copy.id = new String(id);
		return copy;
	}

	public Vector2D getPhysicalPixelPosition(double row, double col) {
		Vector2D position = new Vector2D();
		getPhysicalPixelPosition(row, col, position);
		return position;
	}
	
	public void getPhysicalPixelPosition(double row, double col, Vector2D position) {
		position.set((isMirrored ? -1.0 : 1.0) * col, row);
		position.rotate(orientation);
		position.add(focalPlanePixelOffset);
		position.scale(scuba2.physicalPixelSize);
	}
	
	public void setOptions() {
		if(scuba2.hasOption(id + ".rotation")) orientation = scuba2.option(id + ".rotation").getDouble() * Unit.deg;
		if(scuba2.hasOption(id + ".position")) focalPlanePixelOffset = scuba2.option(id + ".position").getVector2D();
		isMirrored = scuba2.hasOption("mirror");
	}
	
	public static int PIXELS = Scuba2.ROWS * Scuba2.COLS;
}
