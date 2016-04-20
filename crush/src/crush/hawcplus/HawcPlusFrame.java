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


import crush.Channel;
import crush.Instrument;
import crush.sofia.SofiaFrame;
import jnum.math.Vector2D;


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
        setSize(parent.instrument.size());
    }

    @Override
    public void setSize(int size) {
        super.setSize(size);
        jumpCounter = new byte[size];
    }

    public void parseData(int frameIndex, int[] DAC, short[] jump) {   
        parseData(DAC, jump, frameIndex * FITS_CHANNELS);
    }

    // Parses data for valid pixels only...
    private void parseData(int[] DAC, short[] jump, int from) {
        for(final HawcPlusPixel pixel : (HawcPlus) scan.instrument) {
            data[pixel.index] = DAC[from + pixel.fitsIndex];
            if(jump != null) jumpCounter[pixel.index] = (byte) jump[from + pixel.fitsIndex];
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

    @Override
    public void validate() {
        HawcPlus hawc = (HawcPlus) scan.instrument;

        if(hawc.darkSquidCorrection) darkCorrect();

        super.validate();
    }
    

    public void darkCorrect() {
        HawcPlus hawc = (HawcPlus) scan.instrument;

        for(HawcPlusPixel pixel : hawc) if(!pixel.isFlagged(Channel.FLAG_BLIND))
            data[pixel.index] -= data[hawc.darkSquidLookup[pixel.sub][pixel.col]];
    }
  
    public void instrumentToEquatorial(Vector2D offset) {
        offset.rotate(-instrumentVPA);
    }

    public void equatorialToInstrument(Vector2D offset) {
        offset.rotate(instrumentVPA);
    }
   
  
    

    public final static int FITS_ROWS = 41;
    public final static int FITS_COLS = 128;
    public final static int FITS_CHANNELS = FITS_ROWS * FITS_COLS;

    public static byte SAMPLE_PHI0_JUMP = sampleFlags.next('j', "phi0 jump").value();


}
