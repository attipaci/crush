/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.instrument.scuba2;

import java.util.Arrays;

import crush.*;
import crush.telescope.HorizontalFrame;


class Scuba2Frame extends HorizontalFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7910019752807155234L;
	
	int frameNumber;
	float detectorT;
	int[][] darkSquid = new int[Scuba2.SUBARRAYS][];  // sub, col
	
	Scuba2Frame(Scuba2Subscan parent) {
		super(parent);
	}
	
	@Override
    public Scuba2Scan getScan() { return (Scuba2Scan) super.getScan(); }
	
	@Override
    public Scuba2Frame copy(boolean withContents) {
	    Scuba2Frame copy = (Scuba2Frame) super.copy(withContents);
	    
	    if(darkSquid != null) {
	        copy.darkSquid = new int[darkSquid.length][];
	        for(int i=darkSquid.length; --i >=0; ) if(darkSquid[i] != null) 
	            copy.darkSquid[i] = Arrays.copyOf(darkSquid[i], darkSquid[i].length);
	    }
	    
	    return copy;
	}
	
	void parseData(final int[][] DAC, final int channelOffset, final float scaling, int[] readoutLevel) {
		Scuba2Scan scuba2Scan = getScan();
		final int blankingValue = scuba2Scan.blankingValue;
		
		if(data == null) create(scuba2Scan.subarrays * Scuba2Subarray.PIXELS);
		
		for(int bol=Scuba2Subarray.PIXELS; --bol >= 0; ) {
			final int value = DAC[bol%Scuba2.SUBARRAY_COLS][bol/Scuba2.SUBARRAY_COLS];
			final int c = channelOffset + bol;
			if(value != blankingValue && readoutLevel[c] != blankingValue) data[c] = scaling * (value - readoutLevel[c]);
			else sampleFlag[c] |= Frame.SAMPLE_SKIP;
		}
	}
	
	void setDarkSquid(int subarray, int[] data) {
	    darkSquid[subarray] = data;
	}
	
}
