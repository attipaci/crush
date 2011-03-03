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
package crush.gui;

import util.*;

import java.awt.*;

public abstract class ArrayImager extends ScaleImager {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7939307537857595792L;
	int i0 = 0, j0 = 0;	// The subarray offset to image
	
	public void setSubarray(int fromi, int fromj, int toi, int toj) {
		i0 = fromi;
		j0 = fromj;
		createBuffer(toi - fromi, toj - fromj);
		System.err.println("Selecting " + fromi + "," + fromj + " -- " + toi + "," + toj);	
	}
	
	@Override
	public void defaults() {
		super.defaults();
		setFullArray();
		autoscale();
	}
	
	public abstract Dimension getArraySize();
	
	public void setFullArray() {
		Dimension size = getArraySize();
		setSubarray(0, 0, size.width, size.height);
	}
	
	@Override
	public int getRGB(int i, int j) {
		return getRGB(getValue(i+i0, j+j0));		
	}
	
	public abstract double getValue(int i, int j);
	
	public Range getDataRange() {
		Range range = new Range();
		for(int i=buffer.getWidth(); --i >=0; ) for(int j=buffer.getHeight(); --j >=0; ) {
			final double value = getValue(i, j);
			if(!Double.isNaN(value)) range.include(value);
		}
		return range;	
	}
	
	public void autoscale() {
		Range range = getDataRange();
		scale = new Scale(range.min, range.max);
		System.err.println("Setting scale to " + range);
	}

	public static final int ZOOM_FIXED = 0;
	public static final int ZOOM_FIT = 1;
	public static final int ZOOM_FILL = 2;
}

