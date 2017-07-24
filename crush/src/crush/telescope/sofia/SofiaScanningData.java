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
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaScanningData extends SofiaData {
    public BracketedValues RA = new BracketedValues(), DEC = new BracketedValues();
    public double speed = Double.NaN, angle = Double.NaN;

    public SofiaScanningData() {}

    public SofiaScanningData(SofiaHeader header) {
        this();
        parseHeader(header);
    }


    public void parseHeader(SofiaHeader header) {
        RA.start = header.getHMSTime("SCNRA0") * Unit.timeAngle;
        RA.end = header.getHMSTime("SCNRAF") * Unit.timeAngle;
        DEC.start = header.getDMSAngle("SCNDEC0");
        DEC.end = header.getDMSAngle("SCNDECF");
        speed = header.getDouble("SCNRATE", Double.NaN) * Unit.arcsec / Unit.s;
        angle = header.getDouble("SCNDIR", Double.NaN) * Unit.deg;
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Scanning Data ------>", false));
        if(!Double.isNaN(RA.start)) c.add(new HeaderCard("SCNRA0", RA.start / Unit.hourAngle, "(hour) Initial scan RA."));
        if(!Double.isNaN(DEC.start)) c.add(new HeaderCard("SCNDEC0", DEC.start / Unit.deg, "(deg) Initial scan DEC."));
        if(!Double.isNaN(RA.end)) c.add(new HeaderCard("SCNRAF", RA.start / Unit.hourAngle, "(hour) Final scan RA."));
        if(!Double.isNaN(DEC.end)) c.add(new HeaderCard("SCNDECF", DEC.start / Unit.deg, "Final scan DEC."));
        if(!Double.isNaN(speed)) c.add(new HeaderCard("SCNRATE", speed / (Unit.arcsec / Unit.s), "(arcsec/s) Commanded slew rate on sky."));
        if(!Double.isNaN(angle)) c.add(new HeaderCard("SCNDIR", angle / Unit.deg, "(deg) Scan direction on sky."));	
    }

    @Override
    public String getLogID() {
        return "scan";
    } 
    
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("angle")) return angle / Unit.deg;
        else if(name.equals("ra")) return RA.midPoint() / Unit.hourAngle;
        else if(name.equals("dec")) return DEC.midPoint() / Unit.deg;
        else if(name.equals("speed")) return speed / (Unit.arcsec / Unit.s);
      
        return super.getTableEntry(name);
    }

}
