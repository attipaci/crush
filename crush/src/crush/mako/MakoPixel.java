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

package crush.mako;

import crush.Channel;
import crush.array.SimplePixel;

import java.util.StringTokenizer;

import util.Unit;
import util.Util;
import util.Vector2D;


public class MakoPixel extends SimplePixel {
	public int row, col;
	public Vector2D size;
	
	public int toneBin;
	public int validCalPositions;
	public double toneFrequency;
	public double calError;
	
	ResonanceID association;
	
	public MakoPixel(Mako array, int zeroIndex) {
		super(array, zeroIndex+1);
		flag(FLAG_UNASSIGNED);
		size = defaultSize;
	}
	
	@Override
	public Channel copy() {
		MakoPixel copy = (MakoPixel) super.copy();
		if(size != null) copy.size = (Vector2D) size.clone();
		return copy;		
	}
	
	@Override
	public double getHardwareGain() {
		return 1.0;
	}
	
	public void calcPosition() {
		// ALt/Az maps show this to be correct...
		position = getPosition(size, row, col);
	}
	
	public static Vector2D getPosition(Vector2D size, double row, double col) {
		return new Vector2D(-size.getX() * col, size.getY() * row);
	}
		
	@Override
	public void parseValues(StringTokenizer tokens) {
		super.parseValues(tokens);
		coupling = Double.parseDouble(tokens.nextToken());
	}
	
	@Override
	public String toString() {
		return super.toString() + "\t" + Util.f3.format(coupling) + Util.f1.format(toneFrequency);
	}
	
	public double getAreaFactor() {
		return size.getX() * size.getY() / (defaultSize.getX() * defaultSize.getY());	
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
	}
	
	public static Vector2D defaultSize = new Vector2D(3.88 * Unit.arcsec, 6.89 * Unit.arcsec);
	
	public final static int FLAG_UNASSIGNED = 1 << nextSoftwareFlag++;
	
}
