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

package crush.partemis;

import util.*;
import crush.apex.APEXPixel;

public class PArtemisPixel extends APEXPixel {
	public int row, col;
	public double rowGain = 1.0, colGain = 1.0;
	public Vector2D size;
	
	public PArtemisPixel(PArtemis array, int backendIndex) {
		super(array, backendIndex);
		int i = backendIndex - 1;
		row = i/16;
		col = i%16;
		size = defaultSize;
	}
	
	public void calcPosition() {
		position = getPosition(size, row, col);
	}
	
	public static Vector2D getPosition(Vector2D size, double row, double col) {
		return new Vector2D(size.x * col, size.y * row);
	}
	
	public final static int FLAG_ROW = 1 << nextSoftwareFlag++;
	public final static int FLAG_COL = 1 << nextSoftwareFlag++;
	
	public static Vector2D defaultSize = new Vector2D(4.72 * Unit.arcsec, 4.72 * Unit.arcsec);

}
