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
// Copyright (c) 2009 Attila Kovacs 

package crush.gismo;

import java.util.StringTokenizer;

import crush.array.SingleColorPixel;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;

public class GismoPixel extends SingleColorPixel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2948396506109435290L;
	public int row, col, mux, pin;
	public double muxGain = 1.0, pinGain = 1.0, colGain = 1.0, rowGain = 1.0, saeGain = 0.0;
	
	// 16 x 8 (rows x cols)
	
	public GismoPixel(AbstractGismo array, int zeroIndex) {
		super(array, zeroIndex+1);
		row = zeroIndex / 8;
		col = zeroIndex % 8;
		
		// mux & pin filled when reading 'wiring.dat'
		
		calcPosition();
	}
	


	public void calcPosition() {
		// ALt/Az maps show this to be correct...
		position = getPosition(((AbstractGismo) instrument).pixelSize, row, col);
	}
	
	public static Vector2D getPosition(Vector2D size, double row, double col) {
		return new Vector2D(size.x() * col, -size.y() * row);
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
	
	@Override
	public String toString() {
		return super.toString() + "\t" + Util.f3.format(muxGain);
	}
	
	@Override
	public void parseValues(StringTokenizer tokens) {	
		super.parseValues(tokens);
		if(tokens.hasMoreTokens()) muxGain = Double.parseDouble(tokens.nextToken());
	}
	
	public static Vector2D defaultSize = new Vector2D(13.88 * Unit.arcsec, 13.77 * Unit.arcsec);
	
	
	public final static int FLAG_MUX = 1 << nextSoftwareFlag++;
	public final static int FLAG_PIN = 1 << nextSoftwareFlag++;
	public final static int FLAG_ROW = 1 << nextSoftwareFlag++;
	public final static int FLAG_COL = 1 << nextSoftwareFlag++;
	public final static int FLAG_SAE = 1 << nextSoftwareFlag++;

	
}
