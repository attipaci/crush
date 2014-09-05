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

import kovacs.math.Vector2D;
import kovacs.util.Unit;
import kovacs.util.Util;



public abstract class MakoPixel extends SimplePixel {
	public int array = Mako.DEFAULT_ARRAY;
	public int row, col;
	public Vector2D size;
	
	public int toneIndex;
	public int toneBin;
	public int validCalPositions;
	public double toneFrequency;
	public double calError;
	
	public ResonanceID id;
	
	public MakoPixel(Mako<?> array, int zeroIndex) {
		super(array, zeroIndex+1);
		toneIndex = zeroIndex;
		flag(FLAG_NOTONEID | FLAG_UNASSIGNED);
		size = defaultSize;
		row = -1;
		col = -1;
	}
	
	@Override
	public Channel copy() {
		MakoPixel copy = (MakoPixel) super.copy();
		if(size != null) copy.size = (Vector2D) size.clone();
		return copy;		
	}
	
	@Override
	public String getID() {
		return id == null ? "---" : Util.f1.format(id.freq);
	}
	
	@Override
	public double getHardwareGain() {
		return 1.0;
	}
	
	public abstract void calcNominalPosition();
	
	
	public double getAreaFactor() {
		return size.x() * size.y() / (defaultSize.x() * defaultSize.y());	
	}
	
	@Override
	public String getRCPString() {
		return super.getRCPString() + "\t" + getID();
	}

	public abstract void setRowCol(int row, int col);
	
	
	public static Vector2D defaultSize = new Vector2D(3.86 * Unit.arcsec, 7.21 * Unit.arcsec);

	public final static int FLAG_NOTONEID = 1 << nextSoftwareFlag++;
	public final static int FLAG_UNASSIGNED = 1 << nextSoftwareFlag++;
	
	
}
