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


import java.io.IOException;

import crush.CRUSH;
import crush.Channel;
import crush.telescope.sofia.SofiaChannel;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;

public class HirmesPixel extends SofiaChannel {
    /**
     * 
     */
    private static final long serialVersionUID = 293691569452930105L;
    
    public int detArray = -1, sub = -1, subrow = -1, subcol = -1, row = -1, col = -1, mux = -1, pin = -1, seriesArray = -1, biasLine = -1;
    public double absorberEfficiency = 1.0, subGain = 1.0, rowGain = 1.0, colGain = 1.0, muxGain = 1.0, pinGain = 1.0, seriesGain = 1.0, biasGain = 1.0;
    
    int fitsRow = -1, fitsCol = -1;
    
    boolean hasJumps = false;
    
    Vector2D focalPlanePosition;
    
    double restFrequency;
    double obsBandwidth;
    
    HirmesPixel(Hirmes hirmes, int zeroIndex) {
        super(hirmes, zeroIndex);
        
        
        fitsRow = zeroIndex / Hirmes.readoutCols;
        fitsCol = zeroIndex % Hirmes.readoutCols;
        int virtcol = (Hirmes.subCols-1) - fitsCol;
        
        sub = fitsRow / Hirmes.rows;
        
        detArray = zeroIndex < Hirmes.lowresPixels ? Hirmes.LORES_ARRAY : Hirmes.HIRES_ARRAY;
        
        if(virtcol < 0) {
            // Blind SQUIDs...
            flag(FLAG_BLIND);
            subcol = col = -(sub+1);
            subrow = mux = fitsRow;
            pin = -1;
        }
        else {
            // MUXes span 4 readout columns...
            mux = fitsCol / 4;

            // Blue subarray have MUX indices +8
            if(sub == Hirmes.LORES_BLUE_SUBARRAY) mux += 8;

            int virtrow = fitsRow % Hirmes.rows;
            
            // Upper rows have MUX indices +16
            if(virtrow >= 8) mux += 16;
            
            // MUX numbers are pairwise swapped...
            mux ^= 1;

            // Address line indices...
            pin = 8 * (fitsCol % 4);
            int subaddr = (fitsRow % 8);
                        
            if(sub == Hirmes.LORES_RED_SUBARRAY && virtrow < 8) pin += 7 - subaddr; 
            else if (sub == Hirmes.LORES_BLUE_SUBARRAY && virtrow >= 8) pin += 7 - subaddr;
            else if (sub == Hirmes.HIRES_ARRAY) pin += 7 - subaddr;
            else  pin += subaddr;
            
        
            if(detArray == Hirmes.LORES_ARRAY) {
                // LORES array...    
                row = fitsRow;
                subcol = virtcol;
            
                subrow = fitsRow % Hirmes.rows;  
                col = subcol + sub * Hirmes.subCols; 
            }
            else {
                // HIRES array...  
                subrow = virtcol % Hirmes.rows;
                subcol = (fitsCol == Hirmes.subCols) ? -1 : 2 * (fitsRow - sub * Hirmes.rows) + virtcol / Hirmes.rows;
           
                row = sub * Hirmes.rows + subrow;
                col = sub * Hirmes.subCols + subcol;
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
    public Hirmes getInstrument() { return (Hirmes) super.getInstrument(); }
    
    
    
    @Override
    public double overlap(final Channel channel, double pointSize) {
       if(!(channel instanceof HirmesPixel)) return 0.0;
       
       HirmesPixel pixel = (HirmesPixel) channel;       
       if(pixel.getFrequency() != getFrequency()) return 0.0; // TODO, do it better...
       
       return super.overlap(channel, pointSize);
    }
    
    
    @Override
    public double getFrequency() { return restFrequency; }

    @Override
    public double getResolution() { 
        Hirmes instrument = getInstrument();
        return instrument.getResolution() * instrument.getRestFrequency() / restFrequency;
    }

    void calcSIBSPosition3D() {
        Hirmes hirmes = getInstrument(); 
        HirmesLayout layout = hirmes.getLayout();
        
        if(isFlagged(FLAG_BLIND)) {
            getPixel().setPosition(focalPlanePosition = null);
            restFrequency = Double.NaN;
            obsBandwidth = 0.0;
        }
        else {
            focalPlanePosition = (sub == Hirmes.HIRES_SUBARRAY) ? 
                    layout.getHiresFocalPlanePosition(subcol, subrow) :
                    layout.getFocalPlanePosition(sub, subrow, subcol);
                    
            getPixel().setPosition(layout.getSIBSPosition(focalPlanePosition));
            restFrequency = hirmes.getRestFrequency(focalPlanePosition);
            
            Vector2D p = focalPlanePosition;
            Vector2D d = physicalSize.copy();
            d.scale(0.5);
            
            // TODO use absorber size, not spacing.
            double dfx = hirmes.getObservingFrequency(new Vector2D(p.x() + d.x(), p.y())) - hirmes.getRestFrequency(new Vector2D(p.x() - d.x(), p.y()));
            double dfy = hirmes.getObservingFrequency(new Vector2D(p.x(), p.y() + d.y())) - hirmes.getRestFrequency(new Vector2D(p.x(), p.y() - d.y()));
            
            double obsFrequency = hirmes.getObservingFrequency(focalPlanePosition);
            
            // Calculate the total sky coupling
            coupling = absorberEfficiency;
            
            obsBandwidth = (Math.abs(dfx) + Math.abs(dfy));
            
            // TODO is there a better place for this?
            // Atmopsheric transmission variation
            coupling *= hirmes.getRelativeTransmission(obsFrequency);
            
            // Detector bandwidth
            coupling *= obsBandwidth / HirmesPixel.gainNormBandwidth;
            
            try { coupling *= Math.pow(hirmes.getSlitEfficiency(obsFrequency), 2.0); }
            catch(IOException e) {
                CRUSH.warning(hirmes, "Could not apply slit efficiency correction.");
                CRUSH.trace(e);
            }
        }   
    }

    boolean isDarkSQUID() {
        return subcol < 0;
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
        if(isDarkSQUID()) return "dark" + mux;
        
        return sub == Hirmes.HIRES_SUBARRAY ?
                Hirmes.subID[sub] + subcol + "[" + subrow + "]" :
                Hirmes.subID[sub] + "[" + subrow + "," + subcol + "]";
    }
    
    
    @Override
    public String toString() {
        return super.toString() 
                + "\t" + Util.f3.format(coupling) 
                + "\t" + Util.f3.format(muxGain)
                + "\t" + Util.f3.format(pinGain) 
                + "\t" + getFixedIndex() 
                + "\t" + sub 
                + "\t" + mux 
                + "\t" + pin
                + "\t" + Util.e3.format(obsBandwidth);
    }
    
    @Override
    public void parseValues(SmartTokenizer tokens, int criticalFlags) { 
        super.parseValues(tokens, criticalFlags);
        
        if(tokens.hasMoreTokens()) absorberEfficiency = tokens.nextDouble();
        if(isFlagged(Channel.FLAG_DEAD)) absorberEfficiency = 0.0;
        
        if(tokens.hasMoreTokens()) muxGain = tokens.nextDouble();
        if(tokens.hasMoreTokens()) pinGain = tokens.nextDouble();      
    }
    
    public final static Vector2D physicalSize = new Vector2D(1.18 * Unit.mm, 1.01 * Unit.mm);
    public final static double gainNormBandwidth = Unit.GHz;
    
    
    public final static int FLAG_SUB = softwareFlags.next('@', "Bad subarray gain").value();
    public final static int FLAG_BIAS = softwareFlags.next('b', "Bad TES bias gain").value();
    public final static int FLAG_MUX = softwareFlags.next('m', "Bad MUX gain").value();
    public final static int FLAG_PIN = softwareFlags.next('p', "Bad MUX pin gain").value();
    public final static int FLAG_ROW = softwareFlags.next('R', "Bad detector row gain").value();
    public final static int FLAG_COL = softwareFlags.next('c', "Bad detector col gain").value();
    public final static int FLAG_SERIES_ARRAY = softwareFlags.next('M', "Bad series array gain").value();
    public final static int FLAG_FLICKER = softwareFlags.next('T', "Flicker noise").value();

}
