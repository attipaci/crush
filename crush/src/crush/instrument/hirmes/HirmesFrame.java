/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.hirmes;


import crush.Channel;
import crush.Frame;
import crush.Instrument;
import crush.telescope.sofia.SofiaFrame;
import jnum.astro.GeodeticCoordinates;
import jnum.math.Vector2D;


public class HirmesFrame extends SofiaFrame {
    /**
     * 
     */
    private static final long serialVersionUID = 2015348452508480568L;
    long mceSerial;
    byte[] jumpCounter;
    
    public float LOS, roll;
    
    int status;

    boolean isComplete = false;
    
    public HirmesFrame(HirmesScan scan) {
        super(scan);
        create(scan.instrument.size());
    }

    @Override
    public void create(int size) {
        super.create(size);
        jumpCounter = new byte[size];
    }

    public void parseData(int frameIndex, int[] DAC, short[] jump) {   
        parseData(DAC, jump, frameIndex * FITS_CHANNELS);
    }

    // Parses data for valid pixels only...   
    public void parseData(int[] DAC, short[] jump, int from) {  
        Hirmes hirmes = (Hirmes) scan.instrument;
        
        for(final HirmesPixel pixel : hirmes) {
            data[pixel.index] = DAC[from + pixel.getFixedIndex()];
            if(jump != null) jumpCounter[pixel.index] = (byte) jump[from + pixel.getFixedIndex()];
        }
    }
    
    public void parseData(int[][] DAC, short[][] jump) {  
        Hirmes hirmes = (Hirmes) scan.instrument;
        
        for(final HirmesPixel pixel : hirmes) {
            data[pixel.index] = DAC[pixel.mux][pixel.pin];
            if(jump != null) jumpCounter[pixel.index] = (byte) jump[pixel.mux][pixel.pin];
        }
    }


    @Override
    public void cloneReadout(Frame from) {
        super.cloneReadout(from);
        
        HirmesFrame frame = (HirmesFrame) from;
        jumpCounter = frame.jumpCounter;
        chopperPosition = frame.chopperPosition;
        MJD = frame.MJD;
        mceSerial = frame.mceSerial;
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
    public GeodeticCoordinates getSite() { return site; }
    
    @Override
    public boolean validate() {
        HirmesScan hirmesScan = (HirmesScan) scan;
        Hirmes hirmes = hirmesScan.instrument;
        
        if(!isComplete) return false;
         
        // Skip data that is not normal observing
        if(status != FITS_FLAG_NORMAL_OBSERVING || (hirmesScan.useBetweenScans && status == FITS_FLAG_BETWEEN_SCANS)) 
            return false;
        
        if(!Double.isNaN(hirmesScan.transitTolerance)) if(hirmesScan.chopper != null) {
            double dev = Math.abs(chopperPosition.length()) - hirmesScan.chopper.amplitude;
            if(Math.abs(dev) > hirmesScan.transitTolerance) return false;
        }
        
        
        if(hasTelescopeInfo) {
            if(equatorial == null) return false;
            if(equatorial.isNull()) return false;
            if(scan.isNonSidereal) if(objectEq.isNull()) return false;
            
            if(equatorial.isNaN()) return false;
            if(horizontal.isNaN()) return false;
         
            if(Double.isNaN(LST)) return false;
            if(Double.isNaN(site.longitude())) return false;
            if(Double.isNaN(site.latitude())) return false;
            if(Double.isNaN(telescopeVPA)) return false;
            if(Double.isNaN(instrumentVPA)) return false;
            
            if(chopperPosition != null) {
                if(Double.isNaN(chopVPA)) return false;
                
                horizontalOffset.add(chopperPosition);
                horizontal.addOffset(chopperPosition);

                Vector2D offset = chopperPosition.copy();
                
                horizontalToNativeEquatorial(offset);
                
                equatorial.addNativeOffset(offset);
            }

        }
           
        if(hirmes.darkSquidCorrection) darkCorrect();

        return super.validate();
       
    }
    

    public void darkCorrect() {
        Hirmes hirmes = (Hirmes) scan.instrument;

        for(HirmesPixel pixel : hirmes) if(!pixel.isFlagged(Channel.FLAG_BLIND))
            data[pixel.index] -= data[hirmes.darkSquidLookup[pixel.col]];
    }
  
    public void instrumentToEquatorial(Vector2D offset) {
        offset.rotate(-instrumentVPA);
    }

    public void equatorialToInstrument(Vector2D offset) {
        offset.rotate(instrumentVPA);
    }
    

    public final static int JUMP_RANGE = 1<<7;  // Jumps are 7-bit signed values...
    
    public final static int FITS_FLAG_NORMAL_OBSERVING = 0;
    public final static int FITS_FLAG_LOS_REWIND = 1;
    public final static int FITS_FLAG_IVCURVES = 2;
    public final static int FITS_FLAG_BETWEEN_SCANS = 3;

    public final static int FITS_CHANNELS = 1188;

    public static byte SAMPLE_PHI0_JUMP = sampleFlags.next('j', "phi0 jump").value();
    public static byte SAMPLE_TRANSIENT_NOISE = sampleFlags.next('T', "transient noise").value();
   
}
