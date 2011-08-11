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
// Copyright (c) 2009 Attila Kovacs 

package crush.gismo;

import crush.Channel;
import crush.array.SimplePixel;
import util.*;

public class GismoPixel extends SimplePixel {
	public int row, col, mux, pin;
	public Vector2D size = defaultSize;
	public double muxGain = 1.0, pinGain = 1.0;
	
	// 16 x 8 (rows x cols)
	
	public GismoPixel(Gismo array, int zeroIndex) {
		super(array, zeroIndex+1);
		row = zeroIndex / 8;
		col = zeroIndex % 8;
		calcPosition();
		// TODO This is just a workaround...
		variance = 1.0;
	}
	
	@Override
	public Channel copy() {
		GismoPixel copy = (GismoPixel) super.copy();
		if(size != null) copy.size = (Vector2D) size.clone();
		return copy;		
	}

	public void calcPosition() {
		// ALt/Az maps show this to be correct...
		position = getPosition(size, row, col);
	}
	
	public static Vector2D getPosition(Vector2D size, double row, double col) {
		return new Vector2D(size.x * col, -size.y * row);
	}
	
	@Override
	public int getCriticalFlags() {
		return FLAG_DEAD | FLAG_BLIND;
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		muxGain = 1.0;
		pinGain = 1.0;
	}
	
	public static Vector2D defaultSize = new Vector2D(13.9 * Unit.arcsec, 10.56 * Unit.arcsec);
	
	public final static int FLAG_MUX = 1 << nextSoftwareFlag++;
	public final static int FLAG_PIN = 1 << nextSoftwareFlag++;
	public final static int FLAG_ROW = 1 << nextSoftwareFlag++;
	public final static int FLAG_COL = 1 << nextSoftwareFlag++;
	
	
}
