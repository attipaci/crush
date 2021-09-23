/* *****************************************************************************
 * Copyright (c) 2021 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope.sofia;

import java.util.ArrayList;

import crush.CRUSH;
import jnum.Unit;
import jnum.Util;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.EquatorialSystem;
import jnum.math.Range;
import jnum.math.Vector2D;


public class GyroDrifts extends ArrayList<GyroDrifts.Datum> {
    
    
    /**
     * 
     */
    private static final long serialVersionUID = -3751194338137621151L;

    private SofiaScan<?> scan;

    public GyroDrifts(SofiaScan<?> scan) {
        this.scan = scan;
    } 
    
    protected void validate() {
        double fromUTC = scan.getFirstIntegration().getFirstFrame().utc;
        for(int i=0; i<size(); i++) {
            Datum drift = get(i);
            drift.utcRange.setMin(fromUTC);
            fromUTC = drift.nextUTC;
        }    
    }
    
    public void correct(SofiaIntegration<?> integration) {
        if(isEmpty()) {
            integration.warning("Skipping gyro drift correction. No data...");
            return;
        }
        
        if(integration.hasOption("gyrocorrect.max")) {
            double limit = integration.option("gyrocorrect.max").getDouble() * Unit.arcsec;
            if(getMax() > limit) {
                integration.warning("Skipping gyro drift correction. Drifts are too large...");
                return;
            }
        }
        
        integration.info("Correcting for gyro drifts.");
                
        validate();
        
        int k = 0;
        Datum drift = get(0);
        boolean isExtrapolated = false;
        
        for(int i=0; i<integration.size(); i++) {
            SofiaFrame frame = integration.get(i);
            if(frame == null) continue;
                    
            if(!isExtrapolated) while(!drift.utcRange.contains(frame.utc)) {
                drift = ++k < size() ? get(k) : null;
                if(drift == null) break;
            }
         
            if(drift == null) {
                integration.warning("Extrapolated drift correction after frame " + i);
                drift = get(k-1);
                isExtrapolated = true;
            }
            
            Vector2D offset = drift.delta.copy();
            
            double x = (frame.utc - drift.utcRange.min()) / drift.utcRange.span();
            offset.scale(x);
            
            frame.equatorial.addOffset(offset);
            
            frame.equatorialToHorizontal(offset);
            frame.horizontalOffset.add(offset);
            frame.horizontal.addOffset(offset);
        }
    }
    
    public double getMax() {
        if(isEmpty()) return Double.NaN;
        double max = 0.0;
        for(Datum drift : this) if(drift.delta.length() > max) max = drift.delta.length();
        return max;
    }
    
    public double getRMS() {
        if(isEmpty()) return Double.NaN;
        double sum = 0.0;
        for(Datum drift : this) sum += drift.delta.squareNorm();
        return Math.sqrt(sum / size());
    }
    
    
    
    public void parse(SofiaHeader header) {
        for(int i=0; ; i++){
            try { if(!add(header, i)) return; }
            catch(IllegalStateException e) {}            
        }
    } 
 
    protected boolean add(SofiaHeader header, int index) throws IllegalStateException {
        Datum drift = new Datum();
        
        if(drift.parse(header, index)) {
            add(drift);
            CRUSH.detail(scan, " drift " + index + ": " 
                    + Util.f1.format(drift.delta.length() / Unit.arcsec) + " arcsec.");
        }
        else return false;
        
        return true;
    }
    
    protected class Datum implements Comparable<Datum> {
        int index;
        public Range utcRange = new Range();
        public double nextUTC;
        public Vector2D delta;
        
        public boolean parse(SofiaHeader header, int index) throws IllegalStateException {
            this.index = index;
            
            if(!header.containsKey("DBRA" + index)) return false;
            if(!header.containsKey("DBDEC" + index)) return false;
            if(!header.containsKey("DARA" + index)) return false;
            if(!header.containsKey("DADEC" + index)) return false;
            
            EquatorialSystem system = EquatorialSystem.fromHeader(header.getFitsHeader());
            
            EquatorialCoordinates before = new EquatorialCoordinates(
                    header.getHMSTime("DBRA" + index) * Unit.timeAngle,
                    header.getDMSAngle("DBDEC" + index) * Unit.deg,
                    system
            );
              
            EquatorialCoordinates after = new EquatorialCoordinates(
                    header.getHMSTime("DARA" + index) * Unit.timeAngle,
                    header.getDMSAngle("DADEC" + index) * Unit.deg,
                    system
            );
                    
            delta = after.getOffsetFrom(before);
            if(Double.isNaN(delta.length())) throw new IllegalStateException("NaN drift value.");   
            
            utcRange.setMax(header.getDouble("DBTIME" + index));
            nextUTC = header.getDouble("DATIME" + index);

            
            return true;
        }


        @Override
        public int compareTo(Datum o) {
            return Double.compare(utcRange.max(), o.utcRange.max());
        }
        
    }
    

}
