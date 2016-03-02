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

package crush.sharc2;

import crush.array.SingleColorPixel;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;

import java.util.StringTokenizer;



public class Sharc2Pixel extends SingleColorPixel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1902854577318314033L;
	public int row, col, block = 0;
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
	}
	
	@Override
	public double getHardwareGain() {
		return ((Sharc2) instrument).rowGain[row];
	}
	
	public void calcPosition() {
		// ALt/Az maps show this to be correct...
		position = getPosition(((Sharc2) instrument).getPixelSize(), row, col);
	}
	
	public static Vector2D getPosition(Vector2D size, double row, double col) {
		return new Vector2D(-size.x() * col, size.y() * row);
	}
		
	@Override
	public void parseValues(StringTokenizer tokens, int criticalFlags) {
		super.parseValues(tokens, criticalFlags);
		coupling = Double.parseDouble(tokens.nextToken());
		rowGain = Double.parseDouble(tokens.nextToken());
	}
	
	@Override
	public String toString() {
		return super.toString() + "\t" + Util.f3.format(coupling) + "\t" + Util.f3.format(rowGain);
	}
	
	public double getAreaFactor() {
		Vector2D size = ((Sharc2) instrument).getPixelSize();
		return size.x() * size.y() / (defaultSize.x() * defaultSize.y());	
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		muxGain = 1.0;
		rowGain = 1.0;
	}
	
	
	public static Vector2D defaultSize = new Vector2D(4.89 * Unit.arcsec, 4.82 * Unit.arcsec);

	public final static int FLAG_13HZ = softwareFlags.next('^', "13Hz pickup").value();
	public final static int FLAG_ROW = softwareFlags.next('y', "Bad row gain").value();
	
}
