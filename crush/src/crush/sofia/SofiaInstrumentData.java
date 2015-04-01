/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sofia;

import kovacs.util.Copiable;
import kovacs.util.Unit;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaInstrumentData extends SofiaHeaderData implements Copiable<SofiaInstrumentData> {

	public String dataType;
	public String instrumentConfig, instrumentMode, mccsMode;
	public float exposureTime = Float.NaN;
	public String spectralElement1, spectralElement2, slitID;
	public float wavelength;
	public float spectralResolution;
	
	public SofiaInstrumentData() {}
	
	public SofiaInstrumentData(Header header) throws FitsException, HeaderCardException {
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
		if(spectralElement1 != null) copy.spectralElement1 = new String(spectralElement1);
		if(spectralElement2 != null) copy.spectralElement2 = new String(spectralElement2);
		if(slitID != null) copy.slitID = new String(slitID);
		return copy;
	}
	
	@Override
	public void parseHeader(Header header) throws FitsException, HeaderCardException {
		dataType = getStringValue(header, "DATATYPE");
		instrumentConfig = getStringValue(header, "INSTCFG");
		instrumentMode = getStringValue(header, "INSTMODE");
		mccsMode = getStringValue(header, "MCCSMODE");
		exposureTime = header.getFloatValue("EXPTIME", Float.NaN) * (float) Unit.s;
		spectralElement1 = getStringValue(header, "SPECTEL1");
		spectralElement2 = getStringValue(header, "SPECTEL2");
		slitID = getStringValue(header, "SPECTEL2");
		wavelength = header.getFloatValue("WAVECENT", Float.NaN) * (float) Unit.um;
		spectralResolution = header.getFloatValue("RESOLUN", Float.NaN);

	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(dataType != null) cursor.add(new HeaderCard("DATATYPE", dataType, "Data type."));
		if(instrumentConfig != null) cursor.add(new HeaderCard("INSTCFG", instrumentConfig, "Instrument configuration."));
		if(instrumentMode != null) cursor.add(new HeaderCard("INSTMODE", instrumentMode, "Instrument observing mode."));
		if(mccsMode != null) cursor.add(new HeaderCard("MCCSMODE", instrumentMode, "MCCS mode."));
		if(!Float.isNaN(exposureTime)) cursor.add(new HeaderCard("EXPTIME", exposureTime / Unit.s, "(s) total effective on-source time."));
		if(spectralElement1 != null) cursor.add(new HeaderCard("SPECTEL1", spectralElement1, "First spectral element."));
		if(spectralElement2 != null) cursor.add(new HeaderCard("SPECTEL2", spectralElement2, "Second spectral element."));
		if(slitID != null) cursor.add(new HeaderCard("SLIT", slitID, "Slit identifier."));
		if(!Float.isNaN(wavelength)) cursor.add(new HeaderCard("WAVECENT", wavelength / Unit.um, "(um) wavelength at passband center."));
		if(!Float.isNaN(spectralResolution)) cursor.add(new HeaderCard("RESOLUN", spectralResolution, "Spectral resolution."));

	}

	

}
