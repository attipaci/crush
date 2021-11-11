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


public class SofiaChopperData extends SofiaData {
    public double frequency = Double.NaN;
    public String profileType;
    public String symmetryType;
    public double amplitude = Double.NaN, amplitude2 = Double.NaN;
    public String coordinateSystem;
    public double angle = Double.NaN;
    public double tip = Double.NaN;
    public double tilt = Double.NaN;
    public double phase = Double.NaN;


    public SofiaChopperData() {}

    public SofiaChopperData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        frequency = header.getDouble("CHPFREQ") * Unit.Hz;
        profileType = header.getString("CHPPROF");
        symmetryType = header.getString("CHPSYM");
        amplitude = header.getDouble("CHPAMP1") * Unit.arcsec;
        amplitude2 = header.getDouble("CHPAMP2") * Unit.arcsec;
        coordinateSystem = header.getString("CHPCRSYS");
        angle = header.getDouble("CHPANGLE") * Unit.deg;
        tip = header.getDouble("CHPTIP") * Unit.arcsec;
        tilt = header.getDouble("CHPTILT") * Unit.arcsec;
        phase = header.getDouble("CHPPHASE") * Unit.ms;				// int->float in 3.0
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(HeaderCard.createCommentCard("<------ SOFIA Chopper Data ------>"));
        c.add(makeCard("CHPFREQ", frequency / Unit.Hz, "(Hz) Chop frequency."));
        c.add(makeCard("CHPAMP1", amplitude / Unit.arcsec, "(arcsec) Chop amplitude on sky."));
        c.add(makeCard("CHPAMP2", amplitude2 / Unit.arcsec, "(arcsec) Second chop amplitude on sky."));
        c.add(makeCard("CHPANGLE", angle / Unit.deg, "(deg) Chop angle on sky."));
        c.add(makeCard("CHPTIP", tip / Unit.arcsec, "(arcsec) Chopper tip on sky."));
        c.add(makeCard("CHPTILT", tilt / Unit.arcsec, "(arcsec) Chop tilt on sky."));
        c.add(makeCard("CHPPROF", profileType, "Chop profile from MCCS."));
        c.add(makeCard("CHPSYM", symmetryType, "Chop symmetry mode."));
        c.add(makeCard("CHPCRSYS", coordinateSystem, "Chop coordinate system."));
        c.add(makeCard("CHPPHASE", phase / Unit.ms, "(ms) Chop phase."));
    }

    @Override
    public String getLogID() {
        return "chop";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("flag")) return (amplitude == 0.0 || Double.isNaN(amplitude)) ? "-" : "C";
        else if(name.equals("amp")) return amplitude / Unit.arcsec;
        else if(name.equals("angle")) return angle / Unit.deg;
        else if(name.equals("frequency")) return frequency / Unit.Hz;
        else if(name.equals("tip")) return tip / Unit.arcsec;
        else if(name.equals("tilt")) return tilt / Unit.arcsec;
        else if(name.equals("profile")) return profileType;
        else if(name.equals("sys")) return coordinateSystem;

        return super.getTableEntry(name);
    }


    // Below is the nominal conversion
    //public static final double volts2Angle = 1123.0 * Unit.arcsec / (9.0 * Unit.V);

    // And here is the conversion that matches HAWC+ data...
    public static final double volts2Angle = 33.394 * Unit.arcsec / Unit.V;

}
