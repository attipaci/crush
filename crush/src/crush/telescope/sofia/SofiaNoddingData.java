/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope.sofia;

import jnum.Unit;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaNoddingData extends SofiaData {
    public double dwellTime = Double.NaN;
    public int cycles = 0;
    public double settlingTime = Double.NaN;
    public double amplitude = Double.NaN;
    public String beamPosition, pattern, style, coordinateSystem;
    public Vector2D offset;
    public double angle = Double.NaN;

    public SofiaNoddingData() {}

    public SofiaNoddingData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        dwellTime = header.getDouble("NODTIME", Double.NaN) * Unit.s;
        cycles = header.getInt("NODN");
        settlingTime = header.getDouble("NODSETL", Double.NaN) * Unit.s;
        amplitude = header.getDouble("NODAMP", Double.NaN) * Unit.arcsec;
        beamPosition = header.getString("NODBEAM");
        pattern = header.getString("NODPATT");
        style = header.getString("NODSTYLE");
        coordinateSystem = header.getString("NODCRSYS");

        // not in 3.0
        if(header.containsKey("NODPOSX") || header.containsKey("NODPOSY")) {
            offset = new Vector2D(header.getDouble("NODPOSX", 0.0), header.getDouble("NODPOSY", 0.0));
            offset.scale(Unit.deg);
        }
        else offset = null;

        angle = header.getDouble("NODANGLE", Double.NaN) * Unit.deg;
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Nodding Data ------>", false));
        if(cycles != SofiaHeader.UNKNOWN_INT_VALUE) c.add(new HeaderCard("NODN", cycles, "Number of nod cycles."));
        if(!Double.isNaN(amplitude)) c.add(new HeaderCard("NODAMP", amplitude / Unit.arcsec, "(arcsec) Nod amplitude on sky."));
        if(!Double.isNaN(angle)) c.add(new HeaderCard("NODANGLE", angle / Unit.deg, "(deg) Nod angle on sky."));
        if(!Double.isNaN(dwellTime)) c.add(new HeaderCard("NODTIME", dwellTime / Unit.s, "(s) Total dwell time per nod position."));
        if(!Double.isNaN(settlingTime)) c.add(new HeaderCard("NODSETL", settlingTime / Unit.s, "(s) Nod settling time."));
        if(pattern != null) c.add(new HeaderCard("NODPATT", pattern, "Pointing sequence for one nod cycle."));
        if(style != null) c.add(new HeaderCard("NODSTYLE", style, "Nodding style."));
        if(coordinateSystem != null) c.add(new HeaderCard("NODCRSYS", coordinateSystem, "Nodding coordinate system."));
        if(offset != null) {
            c.add(new HeaderCard("NODPOSX", offset.x() / Unit.deg, "(deg) nod position x in nod coords."));
            c.add(new HeaderCard("NODPOSY", offset.y() / Unit.deg, "(deg) nod position y in nod coords."));
        }
        if(beamPosition != null) c.add(new HeaderCard("NODBEAM", beamPosition, "Nod beam position."));
    }

    @Override
    public String getLogID() {
        return "nod";
    }
    
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("amp")) return amplitude / Unit.arcsec;
        else if(name.equals("dx")) return offset.x() / Unit.arcsec;
        else if(name.equals("dy")) return offset.y() / Unit.arcsec;
        else if(name.equals("angle")) return angle / Unit.deg;
        else if(name.equals("dwell")) return dwellTime / Unit.s;
        else if(name.equals("settle")) return settlingTime / Unit.s;
        else if(name.equals("n")) return cycles + "";
        else if(name.equals("pos")) return beamPosition;
        else if(name.equals("sys")) return coordinateSystem;
        else if(name.equals("pattern")) return pattern;
        else if(name.equals("style")) return style;
        
        return super.getTableEntry(name);
    }    


}
