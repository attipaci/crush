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

package crush.sharc2;

import crush.Channel;
import crush.array.SimplePixel;

import java.util.StringTokenizer;

import util.Unit;
import util.Util;
import util.Vector2D;


public class Sharc2Pixel extends SimplePixel {
	public int row, col, block = 0;
	public Vector2D size;
	public double biasV;
	public short DAC;
	public double G0 = 0.0, V0 = Double.POSITIVE_INFINITY, T0 = Double.POSITIVE_INFINITY; // Gain non-linearity constants...
	
	public double rowGain = 1.0;
	public double muxGain;
	
	public Sharc2Pixel(Sharc2 array, int zeroIndex) {
		super(array, zeroIndex+1);
		row = zeroIndex / 32;
		col = zeroIndex % 32;
		muxGain = col < 16 ? 1.0 : -1.0;
		size = defaultSize;
	}
	
	@Override
	public Channel copy() {
		Sharc2Pixel copy = (Sharc2Pixel) super.copy();
		if(size != null) copy.size = (Vector2D) size.clone();
		return copy;		
	}
	
	@Override
	public double getHardwareGain() {
		return ((Sharc2) instrument).rowGain[row];
	}
	
	public void calcPosition() {
		// ALt/Az maps show this to be correct...
		position = getPosition(size, row, col);
	}
	
	public static Vector2D getPosition(Vector2D size, double row, double col) {
		return new Vector2D(-size.x * col, size.y * row);
	}
		
	@Override
	public void parseValues(StringTokenizer tokens) {
		super.parseValues(tokens);
		coupling = Double.parseDouble(tokens.nextToken());
		rowGain = Double.parseDouble(tokens.nextToken());
	}
	
	@Override
	public String toString() {
		return super.toString() + "\t" + Util.f3.format(coupling) + "\t" + Util.f3.format(rowGain);
	}
	
	public double getAreaFactor() {
		return size.x * size.y / (defaultSize.x * defaultSize.y);	
	}
	
	public static Vector2D defaultSize = new Vector2D(4.89 * Unit.arcsec, 4.82 * Unit.arcsec);

	public final static int FLAG_13HZ = 1 << nextHardwareFlag++;
	public final static int FLAG_ROW = 1 << nextSoftwareFlag++;
	
}
