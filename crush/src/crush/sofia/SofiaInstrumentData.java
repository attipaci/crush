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

package crush.sofia;

import jnum.Copiable;
import jnum.Unit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaInstrumentData extends SofiaData implements Copiable<SofiaInstrumentData> {
	public String dataType;
	public String instrumentName, instrumentConfig, instrumentMode, mccsMode, hardwareVersion, softwareVersion;
	public double exposureTime = Double.NaN;
	public String spectralElement1, spectralElement2, slitID;
	public double wavelength = Double.NaN;
	public double bandwidthMicrons = Double.NaN;
	public double spectralResolution = Double.NaN;
	public String detectorChannel;
	public double totalIntegrationTime = Double.NaN;
	
	public SofiaInstrumentData() {}
	
	public SofiaInstrumentData(SofiaHeader header) {
		this();
		parseHeader(header);
	}
	
	@Override
	public SofiaInstrumentData copy() {
		SofiaInstrumentData copy = (SofiaInstrumentData) clone();
		if(dataType != null) copy.dataType = new String(dataType);
		if(instrumentConfig != null) copy.instrumentConfig = new String(instrumentConfig);
		if(instrumentMode != null) copy.instrumentMode = new String(instrumentMode);
		if(mccsMode != null) copy.mccsMode = new String(mccsMode);
		if(hardwareVersion != null) copy.hardwareVersion = new String(hardwareVersion);
		if(softwareVersion != null) copy.softwareVersion = new String(softwareVersion);
		if(spectralElement1 != null) copy.spectralElement1 = new String(spectralElement1);
		if(spectralElement2 != null) copy.spectralElement2 = new String(spectralElement2);
		if(slitID != null) copy.slitID = new String(slitID);
		if(detectorChannel != null) copy.detectorChannel = new String(detectorChannel);
		return copy;
	}
	
	public void parseHeader(SofiaHeader header) {
		instrumentName = header.getString("INSTRUME");
		dataType = header.getString("DATATYPE");
		instrumentConfig = header.getString("INSTCFG");
		instrumentMode =header.getString("INSTMODE");
		mccsMode = header.getString("MCCSMODE");
		hardwareVersion = header.getString("INSTHWV");							// not in 3.0
		softwareVersion = header.getString("INSTSWV");							// not in 3.0
		exposureTime = header.getDouble("EXPTIME", Double.NaN) * Unit.s;
		spectralElement1 = header.getString("SPECTEL1");
		spectralElement2 = header.getString("SPECTEL2");
		slitID = header.getString("SLIT");
		wavelength = header.getDouble("WAVECENT", Double.NaN) * Unit.um;
		bandwidthMicrons = header.getDouble("BANDWDTH", Double.NaN);
		spectralResolution = header.getDouble("RESOLUN", Double.NaN);
		detectorChannel = header.getString("DETCHAN");							// new in 3.0
		totalIntegrationTime = header.getDouble("TOTINT", Double.NaN) * Unit.s;	// new in 3.0

	}

	@Override
	public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Instrument Data ------>", false));
		if(instrumentName != null) cursor.add(new HeaderCard("INSTRUME", instrumentName, "Name of SOFIA instrument."));
		if(dataType != null) cursor.add(new HeaderCard("DATATYPE", dataType, "Data type."));
		if(instrumentConfig != null) cursor.add(new HeaderCard("INSTCFG", instrumentConfig, "Instrument configuration."));
		if(instrumentMode != null) cursor.add(new HeaderCard("INSTMODE", instrumentMode, "Instrument observing mode."));
		if(mccsMode != null) cursor.add(new HeaderCard("MCCSMODE", instrumentMode, "MCCS mode."));
		if(hardwareVersion != null) cursor.add(new HeaderCard("INSTHWV", hardwareVersion, "Instrument hardware version."));
		if(softwareVersion != null) cursor.add(new HeaderCard("INSTSWV", softwareVersion, "Instrument software version."));
		if(!Double.isNaN(exposureTime)) cursor.add(new HeaderCard("EXPTIME", exposureTime / Unit.s, "(s) total effective on-source time."));
		if(spectralElement1 != null) cursor.add(new HeaderCard("SPECTEL1", spectralElement1, "First spectral element."));
		if(spectralElement2 != null) cursor.add(new HeaderCard("SPECTEL2", spectralElement2, "Second spectral element."));
		if(slitID != null) cursor.add(new HeaderCard("SLIT", slitID, "Slit identifier."));
		if(!Double.isNaN(wavelength)) cursor.add(new HeaderCard("WAVECENT", wavelength / Unit.um, "(um) wavelength at passband center."));
		if(!Double.isNaN(bandwidthMicrons)) cursor.add(new HeaderCard("BANDWDTH", bandwidthMicrons, "(um) total bandwith."));
		if(!Double.isNaN(spectralResolution)) cursor.add(new HeaderCard("RESOLUN", spectralResolution, "Spectral resolution."));
		if(detectorChannel != null) cursor.add(new HeaderCard("DETCHAN", detectorChannel, "Detector channel ID."));
		if(!Double.isNaN(totalIntegrationTime)) cursor.add(new HeaderCard("TOTINT", totalIntegrationTime / Unit.s, "(s) Total integration time."));
	}
	
	   
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("wave")) return wavelength / Unit.um;
        else if(name.equals("bw")) return bandwidthMicrons;
        else if(name.equals("exp")) return exposureTime / Unit.s;
        else if(name.equals("inttime")) return totalIntegrationTime / Unit.s;
        else if(name.equals("datatype")) return dataType;
        else if(name.equals("mode")) return instrumentMode;
        else if(name.equals("cfg")) return instrumentConfig;
        else if(name.equals("slit")) return slitID;
        else if(name.equals("spec1")) return spectralElement1;
        else if(name.equals("spec2")) return spectralElement2;
        else if(name.equals("hwver")) return hardwareVersion;
        else if(name.equals("swver")) return softwareVersion; 
        
        return super.getTableEntry(name);
    }

    @Override
    public String getLogID() {
        return "inst";
    }
    


}
