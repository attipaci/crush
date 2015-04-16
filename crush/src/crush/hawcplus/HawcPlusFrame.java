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


import kovacs.astro.GeodeticCoordinates;
import crush.*;


public class HawcPlusFrame extends HorizontalFrame {
	GeodeticCoordinates site;
	long mceSerial;
	float VPA, PWV, HPWangle;
		
	public HawcPlusFrame(HawcPlusScan parent) {
		super(parent);
		setSize(HawcPlus.pixels);
	}
	
	public void parseData(int polarray, long[][] DAC) {
		parseData(DAC, polarray * HawcPlus.polArrayPixels);
	}
	
	public void parseData(int polarray, long[] DAC, int frameIndex) {
		parseData(DAC, frameIndex * HawcPlus.polArrayPixels, HawcPlus.polArrayPixels, polarray * HawcPlus.polArrayPixels);
	}
	
	private void parseData(long[][] DAC, int offset) {
		int bol = offset + DAC.length * DAC[0].length - 1;
		for(int i=DAC.length; --i >= 0; ) for(int j=DAC[0].length; --j >= 0; bol--)
		data[bol] = DAC[i][j] - ((HawcPlus) scan.instrument).get(bol).readoutOffset;		
	}
	
	private void parseData(long[] DAC, int from, int channels, int offset) {
		for(int i=channels; --i >= 0; )
			data[offset+i] = DAC[from+i] - ((HawcPlus) scan.instrument).get(offset+i).readoutOffset;
	}
	
}
