/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

import crush.*;
import crush.telescope.HorizontalFrame;


public class Scuba2Frame extends HorizontalFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7910019752807155234L;
	
	public int frameNumber;
	public float detectorT;
	public int[][] darkSquid = new int[Scuba2.SUBARRAYS][];  // sub, col
	
	public Scuba2Frame(Scuba2Scan parent) {
		super(parent);
	}
	
	public void parseData(final int[][] DAC, final int channelOffset, final float scaling, int[] readoutLevel) {
		Scuba2Scan scuba2Scan = (Scuba2Scan) scan;
		final int blankingValue = scuba2Scan.blankingValue;
		
		if(data == null) create(scuba2Scan.subarrays * Scuba2Subarray.PIXELS);
		
		for(int bol=Scuba2Subarray.PIXELS; --bol >= 0; ) {
			final int value = DAC[bol%Scuba2.SUBARRAY_COLS][bol/Scuba2.SUBARRAY_COLS];
			final int c = channelOffset + bol;
			if(value != blankingValue && readoutLevel[c] != blankingValue) data[c] = scaling * (value - readoutLevel[c]);
			else sampleFlag[c] |= Frame.SAMPLE_SKIP;
		}
	}
	
	public void setDarkSquid(int subarray, int[] data) {
	    darkSquid[subarray] = data;
	}
	
}
