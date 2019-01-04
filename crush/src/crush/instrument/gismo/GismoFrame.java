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
// Copyright (c) 2009 Attila Kovacs 

package crush.instrument.gismo;

import crush.*;
import crush.telescope.HorizontalFrame;


public class GismoFrame extends HorizontalFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = -322299070222166192L;
	int samples = 1;
	int frameNumber;
	int calFlag = 0;
	int digitalFlag = 0;
	//double labviewTime;
	//float[] diodeT, resistorT, diodeV;
	
	public GismoFrame(GismoScan parent) {
		super(parent);
		create(getScan().getInstrument().pixels());
	}
	
	@Override
    public GismoScan getScan() { return (GismoScan) super.getScan(); }
	
	@Override
    public void cloneReadout(Frame from) {
        super.cloneReadout(from);
        
        GismoFrame frame = (GismoFrame) from;
        
        samples = frame.samples;
        calFlag = frame.calFlag;
        digitalFlag = frame.digitalFlag;
	}
	
	
	public void parseData(float[][] DAC) {
		final int pixels = getScan().getInstrument().pixels();
		for(int bol=0; bol<pixels; bol++) data[bol] = DAC[bol/8][bol%8];		
	}
	
	
	public void parseData(float[] DAC, int from, int channels) {
		System.arraycopy(DAC, from, data, 0, channels);
	}

	
	public final static int CAL_NONE = 0;
	public final static int CAL_SHUTTER = 1;
	public final static int CAL_IV = 2;
	
	public final static int DIGITAL_IRIG = 1;
	
}
