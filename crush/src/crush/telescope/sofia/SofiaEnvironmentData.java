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


public class SofiaEnvironmentData extends SofiaData {
    public BracketedValues pwv = new BracketedValues();

    public double ambientT = Double.NaN;
    public double primaryT1 = Double.NaN, primaryT2 = Double.NaN;
    public double primaryT3 = Double.NaN, secondaryT = Double.NaN;

    public SofiaEnvironmentData() {}

    public SofiaEnvironmentData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        pwv.start = header.getDouble("WVZ_STA") * Unit.um;
        pwv.end = header.getDouble("WVZ_END") * Unit.um;
        ambientT = header.getDouble("TEMP_OUT") * Unit.K;
        primaryT1 = header.getDouble("TEMPPRI1") * Unit.K;
        primaryT2 = header.getDouble("TEMPPRI2") * Unit.K;
        primaryT3 = header.getDouble("TEMPPRI3") * Unit.K;
        secondaryT = header.getDouble("TEMPSEC1") * Unit.K;
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Environment Data ------>", false));
        c.add(makeCard("WVZ_STA", pwv.start / Unit.um, "(um) Precipitable Water Vapor at start."));
        c.add(makeCard("WVZ_END", pwv.end / Unit.um, "(um) Precipitable Water Vapor at end."));
        c.add(makeCard("TEMP_OUT", ambientT, "(C) Ambient air temperature."));
        c.add(makeCard("TEMPPRI1", primaryT1, "(C) Primary mirror temperature #1."));
        c.add(makeCard("TEMPPRI2", primaryT2, "(C) Primary mirror temperature #2."));
        c.add(makeCard("TEMPPRI3", primaryT3, "(C) Primary mirror temperature #3."));
        c.add(makeCard("TEMPSEC1", secondaryT, "(C) Secondary mirror temperature."));
    }

    @Override
    public String getLogID() {
        return "env";
    }
    
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("tamb")) return ambientT / Unit.K;
        else if(name.equals("pwv")) return pwv.midPoint() / Unit.um;
        else if(name.equals("t1")) return primaryT1 / Unit.K;
        else if(name.equals("t2")) return primaryT2 / Unit.K;
        else if(name.equals("t3")) return primaryT3 / Unit.K;
        else if(name.equals("sect")) return secondaryT / Unit.K;
        
        return super.getTableEntry(name);
    }


}
