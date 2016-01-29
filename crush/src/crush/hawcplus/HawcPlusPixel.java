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

package crush.hawcplus;

import java.util.StringTokenizer;

import crush.array.SingleColorPixel;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;

public class HawcPlusPixel extends SingleColorPixel {
	public int polarray, subarray, row, col, mux, pin;
	public double polarrayGain = 1.0, subarrayGain = 1.0, muxGain = 1.0, pinGain = 1.0, colGain = 1.0, rowGain = 1.0;
	
	// 16 x 8 (rows x cols)
	long readoutOffset = 0L;
	
	
	public HawcPlusPixel(HawcPlus array, int zeroIndex) {
		super(array, zeroIndex+1);
	
		row = zeroIndex / array.cols();
		col = zeroIndex % array.cols();
		
		polarray = row < array.rows ? 0 : 1;
		row %= array.rows;
		subarray = (polarray << 1) + (col < array.subarrayCols ? 0 : 1);
		
		mux = subarray * array.rows + row;
		pin = col % array.subarrayCols;
		
		// Flag the dark squids as such...
		if(col == array.cols()-1) flag(FLAG_BLIND);
		
		// TODO pin filled when reading 'wiring.dat'	
	}
	

	public void calcPosition() {
		final HawcPlus hawc = (HawcPlus) instrument;
		position = getPosition(hawc.pixelSize, hawc.subarrayOffset[polarray][subarray&1], row, col);
	}
	
	public static Vector2D getPosition(Vector2D size, Vector2D subarrayOffset, double row, double col) {
		return new Vector2D(subarrayOffset.x() - size.x() * row, subarrayOffset.y() + size.y() * col);
	}
	
	@Override
	public int getCriticalFlags() {
		return FLAG_DEAD;
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		muxGain = 1.0;
		pinGain = 1.0;
	}
	
	
	@Override
	public String toString() {
		return super.toString() + "\t" + Util.f3.format(muxGain);
	}
	
	@Override
	public void parseValues(StringTokenizer tokens) {	
		super.parseValues(tokens);
		if(tokens.hasMoreTokens()) muxGain = Double.parseDouble(tokens.nextToken());
	}
	
	
	
	public static Vector2D defaultSize = new Vector2D(5.0 * Unit.arcsec, 5.0 * Unit.arcsec);
	
	
	public final static int FLAG_POL = 1 << nextSoftwareFlag++;
	public final static int FLAG_SUB = 1 << nextSoftwareFlag++;
	public final static int FLAG_MUX = 1 << nextSoftwareFlag++;
	public final static int FLAG_PIN = 1 << nextSoftwareFlag++;
	public final static int FLAG_ROW = 1 << nextSoftwareFlag++;
	public final static int FLAG_COL = 1 << nextSoftwareFlag++;


	

	

	
}
