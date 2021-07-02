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
 *******************************************************************************/

package crush.telescope.sofia;

import jnum.Constant;
import jnum.Copiable;
import jnum.Unit;
import jnum.fits.FitsToolkit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;


public class SofiaSpectroscopyData extends SofiaData implements Copiable<SofiaSpectroscopyData> {
    public String frontEnd, backEnd;
    public double bandwidth = Double.NaN;
    public double frequencyResolution = Double.NaN; // (MHz) Observing frame ? // TODO check...
    public double Tsys = Double.NaN;
    public double observingFrequency = Double.NaN;  // (MHz) Frequency at the reference pixel of the frequency axis.
    public double imageFrequency = Double.NaN;      // (MHz) Image sideband frequency at the reference pixel.
    public double restFrequency = Double.NaN;       
    public String velocityType;
    public double frameVelocity = Double.NaN, sourceVelocity = Double.NaN;


    public SofiaSpectroscopyData() {}

    public SofiaSpectroscopyData(SofiaHeader header) {
        this();
        parseHeader(header);
    }


    @Override
    public SofiaSpectroscopyData copy() {
        return (SofiaSpectroscopyData) super.clone();
    }

    
    public void parseHeader(SofiaHeader header) {
        frontEnd = header.getString("FRONTEND");
        backEnd = header.getString("BACKEND");
               
        bandwidth = header.getDouble("BANDWID") * Unit.MHz;
        frequencyResolution = header.getDouble("FREQRES") * Unit.MHz;
        Tsys = header.getDouble("TSYS") * Unit.K;
        
        observingFrequency = header.getDouble("OBSFREQ") * Unit.MHz;
        imageFrequency = header.getDouble("IMAGFREQ") * Unit.MHz;
        restFrequency = header.getDouble("RESTFREQ") * Unit.MHz;
        
        velocityType = header.getString("VELDEF");
        frameVelocity = header.getDouble("VFRAME") * Unit.km / Unit.s;
        sourceVelocity = header.getDouble("RVSYS") * Unit.km / Unit.s;
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);

        c.add(new HeaderCard("COMMENT", "<------ SOFIA Spectroscopy Data ------>", false));
        
        c.add(makeCard("FRONTEND", frontEnd, "Frontend device name."));
        c.add(makeCard("BACKEND", backEnd, "Backend device name."));
        
        c.add(makeCard("BANDWID", bandwidth / Unit.MHz, "(MHz) Total spectral bandwidth."));
        c.add(makeCard("FREQRES", frequencyResolution / Unit.MHz, "(MHz) Spectral frequency resolution."));
        c.add(makeCard("TSYS", Tsys / Unit.K, "(K) System temperature."));
        
        c.add(makeCard("OBSFREQ", observingFrequency / Unit.MHz, "(MHz) Observing frequency at reference channel."));
        c.add(makeCard("IMAGFREQ", imageFrequency / Unit.MHz, "(MHz) Image frequency at reference channel."));
        c.add(makeCard("RESTFREQ", imageFrequency / Unit.MHz, "(MHz) Rest frequency at reference channel."));
        
        c.add(makeCard("VELDEF", velocityType, "Velocity system definition."));

        c.add(makeCard("VFRAME", frameVelocity / (Unit.km / Unit.s), "(km/s) Radial velocity of reference frame wrt observer."));
        c.add(makeCard("RVSYS", sourceVelocity / (Unit.km / Unit.s), "(km/s) Source radial velocity wrt observer."));
    }
    
    public double getRedshift() {
        return Math.sqrt((1.0 + sourceVelocity / Constant.c) / (1.0 - sourceVelocity / Constant.c)) - 1.0;
    }

    @Override
    public String getLogID() {
        return "spec";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("bw")) return bandwidth / Unit.GHz;
        else if(name.equals("df")) return frequencyResolution / Unit.MHz;
        else if(name.equals("tsys")) return Tsys / Unit.K;
        else if(name.equals("fobs")) return observingFrequency / Unit.GHz;
        else if(name.equals("frest")) return restFrequency / Unit.GHz;
        else if(name.equals("vsys")) return velocityType;
        else if(name.equals("vframe")) return frameVelocity / (Unit.km / Unit.s);
        else if(name.equals("vrad")) return sourceVelocity / (Unit.km / Unit.s);
        else if(name.equals("z")) return getRedshift();
        return super.getTableEntry(name);
    }


}
