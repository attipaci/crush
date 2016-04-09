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


import crush.Instrument;
import crush.sofia.SofiaFrame;


public class HawcPlusFrame extends SofiaFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6511202510198331668L;
	
	long mceSerial;
	float hwpAngle;
	
	byte[] jumpCounter;
		
	public HawcPlusFrame(HawcPlusScan parent) {
		super(parent);
		setSize(parent.instrument.pixels());
	}
	
	@Override
    public void setSize(int size) {
	    super.setSize(size);
	    jumpCounter = new byte[size];
	}
	
	public void parseData(int frameIndex, long[] DAC, short[] jump) {   
		parseData(DAC, jump, frameIndex * FITS_CHANNELS, FITS_CHANNELS);
	}
	
	private void parseData(long[] DAC, short[] jump, int from, int channels) {
	    final int polOffset = HawcPlus.polArrayPixels;
	    
	    // Unpack the FITS array into the internal array (with overlapping polarizations separated out)
		for(int i=channels; --i >= 0; ) {
		    final int row = i >> 7;
		    final int col = i & 63;
		    final int offset = (i & 64) == 0 ? 0 : polOffset; 
		    final int gridIndex = offset + (row<<6) + col;
		    
		    data[gridIndex] = (int) DAC[from+i];
		    if(jump != null) jumpCounter[gridIndex] = (byte) jump[from+i];
		}
	}
	

	@Override
    public void slimTo(Instrument<?> instrument) {
        super.slimTo(instrument);
        
        if(jumpCounter == null) return;
        
        final byte[] newJumpCounter = new byte[instrument.size()];
        for(int k=instrument.size(); --k >= 0; ) newJumpCounter[k] = jumpCounter[instrument.get(k).index];      
            
        jumpCounter = newJumpCounter;
    }
   
	
	public final static int FITS_ROWS = 41;
	public final static int FITS_COLS = 128;
	public final static int FITS_CHANNELS = FITS_ROWS * FITS_COLS;
	
	public static byte SAMPLE_PHI0_JUMP = sampleFlags.next('j', "phi0 jump").value();

   
}
