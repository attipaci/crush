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

package crush.telescope.sofia;

import jnum.Copiable;
import jnum.Unit;
import jnum.fits.FitsToolkit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaInstrumentData extends SofiaData implements Copiable<SofiaInstrumentData> {
	public String dataType;
	public String instrumentName, instrumentConfig, instrumentMode;
	public String mccsMode;
	public double exposureTime = Double.NaN;
	public String spectralElement1, spectralElement2;
	public String slitID;
	public double wavelength = Double.NaN;
	public double spectralResolution = Double.NaN;
	public String detectorChannel;                     // FORCAST
	public double totalIntegrationTime = Double.NaN;   // FORCAST
	
	public SofiaInstrumentData() {}
	
	public SofiaInstrumentData(SofiaHeader header) {
		this();
		parseHeader(header);
	}
	
	@Override
	public SofiaInstrumentData copy() {
		return (SofiaInstrumentData) clone();
	}
	
	public void parseHeader(SofiaHeader header) {
		instrumentName = header.getString("INSTRUME");
		dataType = header.getString("DATATYPE");
		instrumentConfig = header.getString("INSTCFG");
		instrumentMode =header.getString("INSTMODE");
		mccsMode = header.getString("MCCSMODE");
		exposureTime = header.getDouble("EXPTIME") * Unit.s;
		spectralElement1 = header.getString("SPECTEL1");
		spectralElement2 = header.getString("SPECTEL2");
		slitID = header.getString("SLIT");
		wavelength = header.getDouble("WAVECENT") * Unit.um;
		spectralResolution = header.getDouble("RESOLUN");
		detectorChannel = header.getString("DETCHAN");
		totalIntegrationTime = header.getDouble("TOTINT") * Unit.s;

	}

	@Override
	public void editHeader(Header header) throws HeaderCardException {
	    Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
		c.add(new HeaderCard("COMMENT", "<------ SOFIA Instrument Data ------>", false));
		c.add(makeCard("INSTRUME", instrumentName, "Name of SOFIA instrument."));
		c.add(makeCard("DATATYPE", dataType, "Data type."));
		c.add(makeCard("INSTCFG", instrumentConfig, "Instrument configuration."));
		c.add(makeCard("INSTMODE", instrumentMode, "Instrument observing mode."));
		c.add(makeCard("MCCSMODE", instrumentMode, "MCCS mode."));
		c.add(makeCard("EXPTIME", exposureTime / Unit.s, "(s) total effective on-source time."));
		c.add(makeCard("SPECTEL1", spectralElement1, "First spectral element."));
		c.add(makeCard("SPECTEL2", spectralElement2, "Second spectral element."));

		if(!Double.isNaN(wavelength)) c.add(makeCard("WAVECENT", wavelength / Unit.um, "(um) wavelength at passband center."));
		if(slitID != null) c.add(makeCard("SLIT", slitID, "Slit identifier."));
		if(!Double.isNaN(spectralResolution)) c.add(makeCard("RESOLUN", spectralResolution, "Spectral resolution."));
        if(detectorChannel != null) c.add(makeCard("DETCHAN", detectorChannel, "Detector channel ID."));
        if(!Double.isNaN(totalIntegrationTime)) c.add(makeCard("TOTINT", totalIntegrationTime / Unit.s, "(s) Total integration time."));
	}
	
	   
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("wave")) return wavelength / Unit.um;
        else if(name.equals("exp")) return exposureTime / Unit.s;
        else if(name.equals("inttime")) return totalIntegrationTime / Unit.s;
        else if(name.equals("datatype")) return dataType;
        else if(name.equals("mode")) return instrumentMode;
        else if(name.equals("cfg")) return instrumentConfig;
        else if(name.equals("slit")) return slitID;
        else if(name.equals("spec1")) return spectralElement1;
        else if(name.equals("spec2")) return spectralElement2;
        
        return super.getTableEntry(name);
    }

    @Override
    public String getLogID() {
        return "inst";
    }
   
}
