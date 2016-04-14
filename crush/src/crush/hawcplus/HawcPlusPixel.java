/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import jnum.Util;

public class HawcPlusPixel extends SingleColorPixel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5898856651596856837L;
	public int pol, sub, row, col, mux, pin;
	public int fitsIndex, fitsRow, fitsCol;
	
	public double polGain = 1.0, subGain = 1.0, muxGain = 1.0, pinGain = 1.0;
	
	int jumpCounter = 0;
	
	public HawcPlusPixel(HawcPlus array, int zeroIndex) {
		super(array, zeroIndex);
		
		sub = zeroIndex / HawcPlus.subarrayPixels;
		pol = (sub>>1);
		
		pin = zeroIndex / HawcPlus.subarrayCols;
		fitsRow = row = pin % HawcPlus.rows;
		
		col = zeroIndex % HawcPlus.subarrayCols;
		fitsCol = mux = sub * HawcPlus.subarrayCols + col;
		
		fitsIndex = fitsRow * HawcPlusFrame.FITS_COLS + fitsCol;
		
		// Flag the dark squids as such...
		if(row == HawcPlus.DARK_SQUID_ROW) flag(FLAG_BLIND);

	}
	

	public void calcPosition() {
		position = ((HawcPlus) instrument).getPosition(sub, row, col);
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
	public void parseValues(StringTokenizer tokens, int criticalFlags) {	
		super.parseValues(tokens, criticalFlags);
		if(tokens.hasMoreTokens()) muxGain = Double.parseDouble(tokens.nextToken());
	}
	
	@Override
    public String getID() {
	    return HawcPlus.polID[pol] + sub + "[" + row + "," + col + "]";
	}
	
	public final static int FLAG_POL = softwareFlags.next('p', "Bad polarray gain").value();
	public final static int FLAG_SUB = softwareFlags.next('@', "Bad subarray gain").value();
	public final static int FLAG_MUX = softwareFlags.next('m', "Bad MUX gain").value();
	public final static int FLAG_PIN = softwareFlags.next('#', "Bad MUX sample gain").value();

}
