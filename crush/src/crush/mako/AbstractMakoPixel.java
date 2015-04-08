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

import crush.array.SingleColorPixel;
import kovacs.util.Util;



public abstract class AbstractMakoPixel extends SingleColorPixel {
	public int array = AbstractMako.DEFAULT_ARRAY;
	public int row, col;
	
	public int toneIndex;
	public int toneBin;
	public int validCalPositions;
	public double toneFrequency;
	public double calError;
	
	public ResonanceID id;
	
	public AbstractMakoPixel(AbstractMako<?> array, int zeroIndex) {
		super(array, zeroIndex+1);
		toneIndex = zeroIndex;
		flag(FLAG_NOTONEID | FLAG_UNASSIGNED);
		row = -1;
		col = -1;
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
		return ((AbstractMako<?>) instrument).getAreaFactor();
	}
	
	@Override
	public String getRCPString() {
		return super.getRCPString() + "\t" + getID();
	}

	public abstract void setRowCol(int row, int col);
	
	
	

	public final static int FLAG_NOTONEID = 1 << nextSoftwareFlag++;
	public final static int FLAG_UNASSIGNED = 1 << nextSoftwareFlag++;
	
	
}
