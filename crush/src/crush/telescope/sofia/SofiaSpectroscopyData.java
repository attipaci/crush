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
               
        bandwidth = header.getDouble("BANDWID", Double.NaN) * Unit.MHz;
        frequencyResolution = header.getDouble("FREQRES", Double.NaN) * Unit.MHz;
        Tsys = header.getDouble("TSYS", Double.NaN) * Unit.K;
        
        observingFrequency = header.getDouble("OBSFREQ", Double.NaN) * Unit.MHz;
        imageFrequency = header.getDouble("IMAGFREQ", Double.NaN) * Unit.MHz;
        restFrequency = header.getDouble("RESTFREQ", Double.NaN) * Unit.MHz;
        
        velocityType = header.getString("VELDEF");
        frameVelocity = header.getDouble("VFRAME", Double.NaN) * Unit.km / Unit.s;
        sourceVelocity = header.getDouble("RVSYS", Double.NaN) * Unit.km / Unit.s;
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);

        c.add(new HeaderCard("COMMENT", "<------ SOFIA Spectroscopy Data ------>", false));
        
        if(frontEnd != null) c.add(new HeaderCard("FRONTEND", frontEnd, "Frontend device name."));
        if(backEnd != null) c.add(new HeaderCard("BACKEND", backEnd, "Backend device name."));
        
        if(!Double.isNaN(bandwidth)) c.add(new HeaderCard("BANDWID", bandwidth / Unit.MHz, "(MHz) Total spectral bandwidth."));
        if(!Double.isNaN(frequencyResolution)) c.add(new HeaderCard("FREQRES", frequencyResolution / Unit.MHz, "(MHz) Spectral frequency resolution."));
        if(!Double.isNaN(Tsys)) c.add(new HeaderCard("TSYS", Tsys / Unit.K, "(K) System temperature."));
        
        if(!Double.isNaN(observingFrequency)) c.add(new HeaderCard("OBSFREQ", observingFrequency / Unit.MHz, "(MHz) Observing frequency at reference channel."));
        if(!Double.isNaN(imageFrequency)) c.add(new HeaderCard("IMAGFREQ", imageFrequency / Unit.MHz, "(MHz) Image frequency at reference channel."));
        if(!Double.isNaN(restFrequency)) c.add(new HeaderCard("RESTFREQ", imageFrequency / Unit.MHz, "(MHz) Rest frequency at reference channel."));
        
        if(velocityType != null) c.add(new HeaderCard("VELDEF", velocityType, "Velocity system definition."));

        if(!Double.isNaN(frameVelocity)) c.add(new HeaderCard("VFRAME", frameVelocity / (Unit.km / Unit.s), "(km/s) Radial velocity of reference frame wrt observer."));
        if(!Double.isNaN(sourceVelocity)) c.add(new HeaderCard("RVSYS", sourceVelocity / (Unit.km / Unit.s), "(km/s) Source radial velocity wrt observer."));
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
