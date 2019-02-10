/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope.apex;

import crush.telescope.HorizontalFrame;


public class APEXFrame extends HorizontalFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4679861282148192736L;
	public int chopperPhase;
	public int nodFlag;
	
	protected APEXFrame(APEXSubscan<? extends APEXFrame> parent) {
		super(parent);
	}
	
	@SuppressWarnings("unchecked")
    @Override
    public APEXScan<? extends APEXSubscan<? extends APEXFrame>> getScan() {
	    return (APEXScan<? extends APEXSubscan<? extends APEXFrame>>) super.getScan();
	}

	void parse(float[][] fitsData) {
		data = new float[fitsData.length];
	
		for(int p=0, to=0; p<fitsData.length; p++) {
		    System.arraycopy(fitsData[p], 0, data, to, fitsData[p].length);
		    to += fitsData[p].length;
		}	
	}
	
	void parse(float[] flatData, int from, int channels) {
		data = new float[channels];
		System.arraycopy(flatData, from, data, 0, channels);
	}
	
	void parse(float[] flatData) {
		data = flatData;
	}
	
}
