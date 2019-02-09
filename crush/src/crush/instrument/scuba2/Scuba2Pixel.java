/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/

package crush.instrument.scuba2;

import crush.Channel;
import jnum.Unit;
import jnum.math.Vector2D;

public class Scuba2Pixel extends Channel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -638264274147854699L;
	
	public int subarrayNo = -1;
	public int row = -1, col = -1, block=0;  // row = MUX, col = address line
	public double subarrayGain = 1.0, rowGain = 1.0, colGain = 1.0;
	public double he3Gain = 0.0;
	
	Scuba2Pixel(Scuba2 array, int zeroIndex) {
		super(array, zeroIndex);
		subarrayNo = zeroIndex / Scuba2Subarray.PIXELS;
		row = zeroIndex / Scuba2.SUBARRAY_COLS;
		col = Scuba2.SUBARRAY_COLS * subarrayNo + zeroIndex % Scuba2.SUBARRAY_COLS;
	}
	
	@Override
    public Scuba2 getInstrument() { return (Scuba2) super.getInstrument(); }
	
	@Override
	public int getCriticalFlags() {
		return FLAG_DEAD | FLAG_BLIND;
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		subarrayGain = 1.0;
		rowGain = 1.0;
		colGain = 1.0;
	}
	
	@Override
	public String getID() {
		return getInstrument().subarray[subarrayNo].id + ":" + row + "," + col;
	}
	
	public static Vector2D defaultSize = new Vector2D(5.7 * Unit.arcsec, 5.7 * Unit.arcsec);
	
	public final static int FLAG_ROW = softwareFlags.next('y', "Bad row gain").value();
	public final static int FLAG_COL = softwareFlags.next('c', "Bad col gain").value();
	public final static int FLAG_SUBARRAY = softwareFlags.next('a', "Bad subarray gain").value(); // new in 2.30
	
}
