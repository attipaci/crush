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

import java.util.ArrayList;

import crush.CRUSH;
import crush.sofia.SofiaHeader;
import crush.sofia.SofiaScan;
import jnum.Unit;
import jnum.Util;
import jnum.astro.CoordinateEpoch;
import jnum.astro.EquatorialCoordinates;
import jnum.math.Vector2D;


public class GyroDrifts extends ArrayList<GyroDrifts.Entry> {
    
    
    /**
     * 
     */
    private static final long serialVersionUID = -3751194338137621151L;

    private SofiaScan<?,?> scan;
    
    public long scanStartMillis = -1;
  
    public GyroDrifts(SofiaScan<?,?> scan) {
        this.scan = scan;
    } 
    
    public void parse(SofiaHeader header) {
        int i=0;
        while(add(header, i++)) continue;
        
    } 
 
    protected boolean add(SofiaHeader header, int index) {
        Entry entry = new Entry();
        if(entry.parse(header, index)) {
            add(entry);
        
            CRUSH.detail(scan, " drift " + index + ": " 
                    + Util.f1.format(entry.drift.length() / Unit.arcsec) + " arcsec.");
        }
        else return false;
        
        return true;
    }
    
    public class Entry {
        int index;
        public double timestampMillis;
        public Vector2D drift;
       
        
        public boolean parse(SofiaHeader header, int index) {
            this.index = index;
            
            if(!header.containsKey("DBRA" + index)) return false;
            if(!header.containsKey("DBDEC" + index)) return false;
            if(!header.containsKey("DARA" + index)) return false;
            if(!header.containsKey("DADEC" + index)) return false;
            
            CoordinateEpoch epoch = CoordinateEpoch.J2000;
            
            String spec = header.getString("EQUINOX", null);
            if(spec != null) if(SofiaHeader.isValid(spec)) epoch = CoordinateEpoch.forString(spec);
            
            EquatorialCoordinates before = new EquatorialCoordinates(
                    header.getHMSTime("DBRA" + index) * Unit.timeAngle,
                    header.getDMSAngle("DBDEC" + index) * Unit.deg,
                    epoch
            );
              
            EquatorialCoordinates after = new EquatorialCoordinates(
                    header.getHMSTime("DARA" + index) * Unit.timeAngle,
                    header.getDMSAngle("DADEC" + index) * Unit.deg,
                    epoch
            );
                    
            drift = after.getOffsetFrom(before);
            
            timestampMillis = 0.5 * (header.getDouble("DBTIME" + index) + header.getDouble("DATIME" + index));
            
            return true;
        }
        
    }
}
