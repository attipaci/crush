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
import crush.array.SingleColorPixel;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;

public class HirmesPixel extends SingleColorPixel {
    /**
     * 
     */
    private static final long serialVersionUID = 293691569452930105L;
    
    public int detArray, sub, readrow, readcol, subrow, subcol, row, col, mux, pin, seriesArray, biasLine;
    public float jump = 0.0F;
    public boolean hasJumps = false;
    
    public double subGain = 1.0, rowGain = 1.0, colGain = 1.0, muxGain = 1.0, pinGain = 1.0, seriesGain = 1.0, biasGain = 1.0;
    
    int jumpCounter = 0;
    
    Vector2D focalPlanePosition;
    
    double restFrequency;
    
    public HirmesPixel(Hirmes hirmes, int zeroIndex) {
        super(hirmes, zeroIndex);
        
        readrow = zeroIndex / Hirmes.readoutCols;
        readcol = zeroIndex % Hirmes.readoutCols;
        int virtcol = (Hirmes.subCols-1) - readcol;
        
        sub = readrow / Hirmes.rows;
        
        detArray = zeroIndex < Hirmes.lowresPixels ? Hirmes.LORES_ARRAY : Hirmes.HIRES_ARRAY;
        
        if(virtcol < 0) {
            // Blind SQUIDs...
            flag(FLAG_BLIND);
            detArray = 
            subcol = -1;
            col = -sub;
            mux = readrow;
            pin = -1;
        }
        
        else if(detArray == Hirmes.LORES_ARRAY) {
            // LORES array...
            
            row = readrow;
            subcol = virtcol;
            
            subrow = row % Hirmes.rows;  
            col = subcol + sub * Hirmes.subCols; 
            
            // It's easiest to index MUXes from reverse indexed physical columns...
            final int mcol = (Hirmes.lowresCols-1) - col; 
            mux = mcol / 4;
            
            // MUX numbers are pairwise swapped...
            mux ^= 1;
            
            // Address lines increase by 8 along the reversed columns
            pin = 8 * (mcol % 4);
            
            // Address lines are increasing away from center row.
            if(row > 7) {
                mux += 16;
                pin += (row % 8); 
            }
            else {
                pin += ((7-row) % 8);
            }   
           
        }
        
        else {
            // HIRES array...
            
            subrow = virtcol % Hirmes.rows;
            subcol = (readcol == Hirmes.subCols) ? -1 : 2 * (readrow - sub * Hirmes.rows) + virtcol / Hirmes.rows;
           
            row = sub * Hirmes.rows + subrow;
            col = sub * Hirmes.subCols + subcol;
            
            // MUX numbers in quartiles.
            mux = 32;
            if(col > 3) mux++;      // If we are on the right side, the MUX is 1 higher
            if(row > 7) mux += 2;   // If we are on top half, then MUX is higher by 2...
            
            // Address lines increase by 8 with columns...
            pin = 8 * (subcol % 4);
            
            // Address lines are increasing away from center row
            if(row > 7) {
                mux += 16;
                pin += (row % 8);
            }
            else {
                pin += ((7-row) % 8);
            }
                
        }
          
        seriesArray = mux >>> 1;   // series array on pair-wise MUX, TODO ckeck!
        
        // TODO biasLine
    }
    
    
    @Override
    public HirmesPixel copy() {
        HirmesPixel copy = (HirmesPixel) super.copy();
        if(focalPlanePosition != null) copy.focalPlanePosition = focalPlanePosition.copy();
        return copy;
    }
    
    
    @Override
    public double overlap(final Channel channel, double pointSize) {
       if(!(channel instanceof HirmesPixel)) return 0.0;
       
       HirmesPixel pixel = (HirmesPixel) channel;       
       if(pixel.getFrequency() != getFrequency()) return 0.0; // TODO, do it better...
       
       return super.overlap(channel, pointSize);
    }
    
    
    @Override
    public double getFrequency() { return restFrequency; }


    public void calcSIBSPosition3D() {
        Hirmes hirmes = (Hirmes) instrument; 
        HirmesLayout layout = hirmes.getLayout();
        
        if(isFlagged(FLAG_BLIND)) {
            focalPlanePosition = position = null;
            restFrequency = Double.NaN;
        }
        else {
            focalPlanePosition = (sub == Hirmes.HIRES_SUBARRAY) ? 
                    layout.getHiresFocalPlanePosition(subcol, subrow) :
                    layout.getFocalPlanePosition(sub, subrow, subcol);
                    
            position = layout.getSIBSPosition(focalPlanePosition);
            restFrequency = hirmes.getRestFrequency(focalPlanePosition);
        }
    }

    public boolean isDark() {
        return subcol < 0 || position == null;
    }
        
    @Override
    public int getCriticalFlags() {
        return FLAG_DEAD;
    }
    
    @Override
    public void uniformGains() {
        super.uniformGains();
        muxGain = 1.0;
    }
    
    @Override
    public String getID() {
        return sub == Hirmes.HIRES_SUBARRAY ?
                Hirmes.subID[sub] + subcol + "[" + subrow + "]" :
                Hirmes.subID[sub] + "[" + subrow + "," + subcol + "]";
    }
    
    
    @Override
    public String toString() {
        return super.toString() 
                + "\t" + Util.f3.format(muxGain) 
                + "\t" + getFixedIndex() 
                + "\t" + sub 
                + "\t" + subrow 
                + "\t" + subcol;
    }
    
    @Override
    public void parseValues(SmartTokenizer tokens, int criticalFlags) { 
        super.parseValues(tokens, criticalFlags);
        if(tokens.hasMoreTokens()) muxGain = tokens.nextDouble();            
        if(isFlagged(Channel.FLAG_DEAD)) coupling = 0.0;
    }
    
    public final static Vector2D physicalSize = new Vector2D(1.18 * Unit.mm, 1.01 * Unit.mm);
    
    public final static int FLAG_SUB = softwareFlags.next('@', "Bad subarray gain").value();
    public final static int FLAG_BIAS = softwareFlags.next('b', "Bad TES bias gain").value();
    public final static int FLAG_MUX = softwareFlags.next('m', "Bad MUX gain").value();
    public final static int FLAG_PIN = softwareFlags.next('p', "Bad MUX pin gain").value();
    public final static int FLAG_ROW = softwareFlags.next('R', "Bad detector row gain").value();
    public final static int FLAG_COL = softwareFlags.next('c', "Bad detector col gain").value();
    public final static int FLAG_SERIES_ARRAY = softwareFlags.next('M', "Bad series array gain").value();
    public final static int FLAG_FLICKER = softwareFlags.next('T', "Flicker noise").value();
    public final static int FLAG_LOS_RESPONSE = softwareFlags.next('L', "LOS response").value();
    public final static int FLAG_ROLL_RESPONSE = softwareFlags.next('\\', "Roll response").value();

}
