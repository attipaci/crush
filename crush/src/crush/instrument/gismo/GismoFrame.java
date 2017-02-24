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

import java.util.Arrays;

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
	float[] SAE;
	
	public GismoFrame(GismoScan parent) {
		super(parent);
		create(((AbstractGismo) scan.instrument).pixels());
	}
	
	@Override
	public Frame copy(boolean withContents) {
		GismoFrame copy = (GismoFrame) super.copy(withContents);
		if(SAE != null) {
			copy.SAE = new float[SAE.length];
			if(withContents) System.arraycopy(SAE, 0, copy.SAE, 0, SAE.length);
		}	
		return copy;
	}
	
	@Override
    public void cloneReadout(Frame from) {
        super.cloneReadout(from);
        
        GismoFrame frame = (GismoFrame) from;
        
        samples = frame.samples;
        calFlag = frame.calFlag;
        digitalFlag = frame.digitalFlag;
        SAE = frame.SAE; 
	}
	
	@Override
	public void addDataFrom(Frame other, double scaling) {
		super.addDataFrom(other, scaling);
		if(scaling == 0.0) return;
		
		final GismoFrame gismoFrame = (GismoFrame) other;
		final float fScale = (float) scaling;
		if(SAE != null) for(int i=SAE.length; --i >= 0; ) SAE[i] += fScale * gismoFrame.SAE[i];
	}
	
	@Override
	public void scale(double factor) {
		super.scale(factor);
		
		if(SAE != null) {
			if(factor == 0.0) Arrays.fill(SAE, 0.0F);
			else {
				final float fScale = (float) factor;
				for(int i=SAE.length; --i >= 0; ) SAE[i] *= fScale;
			}
		}
	}
	
	@Override
	public void slimTo(Instrument<?> instrument) {
		super.slimTo(instrument);
		
		if(SAE == null) return;
		
		final float[] newSAE = new float[instrument.size()];
		for(int k=instrument.size(); --k >= 0; ) newSAE[k] = SAE[instrument.get(k).index];		
			
		SAE = newSAE;
	}
	
	public void parseData(float[][] DAC) {
		final int pixels = ((AbstractGismo) scan.instrument).pixels();
		for(int bol=0; bol<pixels; bol++) data[bol] = DAC[bol/8][bol%8];		
	}
	
	public void parseSAE(float[][] SAE) {
		final int pixels = ((AbstractGismo) scan.instrument).pixels();
		for(int bol=0; bol<pixels; bol++) this.SAE[bol] = SAE[bol/8][bol%8];	
	}
	
	public void parseData(float[] DAC, int from, int channels) {
		System.arraycopy(DAC, from, data, 0, channels);
	}
	
	public void parseSAE(float[] SAE, int from, int channels) {
		System.arraycopy(SAE, from, this.SAE, 0, channels);
	}
	
	public final static int CAL_NONE = 0;
	public final static int CAL_SHUTTER = 1;
	public final static int CAL_IV = 2;
	
	public final static int DIGITAL_IRIG = 1;
	
}
