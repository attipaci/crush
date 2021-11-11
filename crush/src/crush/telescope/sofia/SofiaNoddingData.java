/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.telescope.sofia;

import jnum.Unit;
import jnum.fits.FitsToolkit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaNoddingData extends SofiaData {
    public double dwellTime = Double.NaN;
    public int cycles = UNKNOWN_INT_VALUE;
    public double settlingTime = Double.NaN;
    public double amplitude = Double.NaN;
    public String beamPosition, pattern, style, coordinateSystem;
    public double angle = Double.NaN;

    public SofiaNoddingData() {}

    public SofiaNoddingData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        dwellTime = header.getDouble("NODTIME") * Unit.s;
        cycles = header.getInt("NODN");
        settlingTime = header.getDouble("NODSETL") * Unit.s;
        amplitude = header.getDouble("NODAMP") * Unit.arcsec;
        beamPosition = header.getString("NODBEAM");
        pattern = header.getString("NODPATT");
        style = header.getString("NODSTYLE");
        coordinateSystem = header.getString("NODCRSYS");
        angle = header.getDouble("NODANGLE", Double.NaN) * Unit.deg;
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(HeaderCard.createCommentCard("<------ SOFIA Nodding Data ------>"));
        c.add(makeCard("NODN", cycles, "Number of nod cycles."));
        c.add(makeCard("NODAMP", amplitude / Unit.arcsec, "(arcsec) Nod amplitude on sky."));
        c.add(makeCard("NODANGLE", angle / Unit.deg, "(deg) Nod angle on sky."));
        c.add(makeCard("NODTIME", dwellTime / Unit.s, "(s) Total dwell time per nod position."));
        c.add(makeCard("NODSETL", settlingTime / Unit.s, "(s) Nod settling time."));
        c.add(makeCard("NODPATT", pattern, "Pointing sequence for one nod cycle."));
        c.add(makeCard("NODCRSYS", coordinateSystem, "Nodding coordinate system."));
        c.add(makeCard("NODBEAM", beamPosition, "Nod beam position."));
        
        if(style != null) c.add(makeCard("NODSTYLE", style, "Nodding style."));
    }

    @Override
    public String getLogID() {
        return "nod";
    }
    
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("amp")) return amplitude / Unit.arcsec;
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
