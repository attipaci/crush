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

import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.JulianEpoch;
import kovacs.util.Unit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaTelescopeData extends SofiaHeaderData {
	public String telescope = "SOFIA";
	public String telConfig;
	public EquatorialCoordinates boresightEquatorial, requestedEquatorial;
	public double VPA = Double.NaN;
	public String lastRewind;
	public BracketedValues focusT = new BracketedValues();
	public double relElevation = Double.NaN, crossElevation = Double.NaN, lineOfSightAngle = Double.NaN;
	public String tascuStatus, fbcStatus;
	public BracketedValues zenithAngle = new BracketedValues();
	public String trackingMode;
	public boolean hasTrackingError = false;
	
	public SofiaTelescopeData() {}
	
	public SofiaTelescopeData(Header header) {
		this();
		parseHeader(header);
	}
	
	public boolean isTracking() { 
		if(trackingMode == null) return false;
		return !trackingMode.equalsIgnoreCase("OFF");
	}
	
	
	@Override
	public void parseHeader(Header header) {
		
		if(header.containsKey("TELESCOP")) telescope = header.getStringValue("TELESCOPE");
		telConfig = getStringValue(header, "TELCONF");
		
		boresightEquatorial = new EquatorialCoordinates();
		if(header.containsKey("TELEQUI")) boresightEquatorial.setEpoch(new JulianEpoch(header.getDoubleValue("TELEQUI")));
		
		try { 
			boresightEquatorial.set(
					getHMSTime(header, "TELRA") * Unit.timeAngle, 
					getDMSAngle(header, "TELDEC")
			);
		}
		catch(Exception e) { boresightEquatorial = null; }
		
		VPA = header.getDoubleValue("TELVPA", Double.NaN) * Unit.deg;
		
		lastRewind = getStringValue(header, "LASTREW");
		focusT.start = header.getDoubleValue("FOCUS_ST", Double.NaN) * Unit.um;
		focusT.end = header.getDoubleValue("FOCUS_EN", Double.NaN) * Unit.um;
		
		relElevation = header.getDoubleValue("TELEL", Double.NaN) * Unit.deg;
		crossElevation = header.getDoubleValue("TELXEL", Double.NaN) * Unit.deg;
		lineOfSightAngle = header.getDoubleValue("TELLOS", Double.NaN) * Unit.deg;
		
		tascuStatus = getStringValue(header, "TSC-STAT");
		fbcStatus = getStringValue(header, "FBC-STAT");
		
		requestedEquatorial = new EquatorialCoordinates();
		if(header.containsKey("EQUINOX")) requestedEquatorial.setEpoch(new JulianEpoch(header.getDoubleValue("EQUINOX")));
		
		try { 
			requestedEquatorial.set(
					getHMSTime(header, "OBSRA") * Unit.timeAngle, 
					getDMSAngle(header, "OBSDEC")
			);
		}
		catch(Exception e) { boresightEquatorial = null; }
		
		zenithAngle.start = header.getDoubleValue("ZA_START", Double.NaN) * Unit.deg;
		zenithAngle.end = header.getDoubleValue("ZA_END", Double.NaN) * Unit.deg;
		
		trackingMode = getStringValue(header, "TRACMODE");
		hasTrackingError = header.getBooleanValue("TRACERR", false);
		
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(telescope != null) cursor.add(new HeaderCard("TELESCOP", telescope, "observatory name."));
		if(telConfig != null) cursor.add(new HeaderCard("TELCONF", telConfig, "telescope configuration."));
		
		if(boresightEquatorial != null) {
			cursor.add(new HeaderCard("TELRA", boresightEquatorial.RA() / Unit.hourAngle, "(hour) Boresight RA."));
			cursor.add(new HeaderCard("TELDEC", boresightEquatorial.DEC() / Unit.deg, "(deg) Boresight DEC."));
			cursor.add(new HeaderCard("TELEQUI", boresightEquatorial.epoch.getYear(), "(yr) Boresight epoch."));
		}
		
		if(!Double.isNaN(VPA)) cursor.add(new HeaderCard("TELVPA", VPA / Unit.deg, "(deg) Boresight position angle."));
		
		if(lastRewind != null) cursor.add(new HeaderCard("LASTREW", lastRewind, "UTC time of last telescope rewind."));
		
		if(!Double.isNaN(focusT.start)) cursor.add(new HeaderCard("FOCUS_ST", focusT.start / Unit.um, "(um) Focus T value at start."));
		if(!Double.isNaN(focusT.end)) cursor.add(new HeaderCard("FOCUS_EN", focusT.end / Unit.um, "(um) Focus T value at end."));
		
		if(!Double.isNaN(relElevation)) cursor.add(new HeaderCard("TELEL", relElevation / Unit.deg, "(deg) Telescope elevation in cavity."));
		if(!Double.isNaN(crossElevation)) cursor.add(new HeaderCard("TELXEL", crossElevation / Unit.deg, "(deg) Telescope cross elevation in cavity."));
		if(!Double.isNaN(lineOfSightAngle)) cursor.add(new HeaderCard("TELLOS", lineOfSightAngle / Unit.deg, "(deg) Telescope line-of-sight angle in cavity."));
		
		if(tascuStatus != null) cursor.add(new HeaderCard("TSC_STAT", tascuStatus, "TASCU system status at end."));
		if(fbcStatus != null) cursor.add(new HeaderCard("FBC_STAT", fbcStatus, "flexible body compensation system status at end."));
		
		if(requestedEquatorial != null) {
			cursor.add(new HeaderCard("OBSRA", requestedEquatorial.RA() / Unit.hourAngle, "(hour) Requested RA."));
			cursor.add(new HeaderCard("OBSDEC", requestedEquatorial.DEC() / Unit.deg, "(deg) Requested DEC."));
			cursor.add(new HeaderCard("EQUINOX", requestedEquatorial.epoch.getYear(), "(yr) Requested epoch."));
		}
		
		if(!Double.isNaN(zenithAngle.start)) cursor.add(new HeaderCard("ZA_START", zenithAngle.start / Unit.deg, "(deg) Zenith angle at start."));
		if(!Double.isNaN(zenithAngle.end)) cursor.add(new HeaderCard("ZA_END", zenithAngle.end / Unit.deg, "(deg) Zenith angle at end."));
		
		if(trackingMode != null) {
			cursor.add(new HeaderCard("TRACMODE", trackingMode, "SOFIA tracking mode."));
			cursor.add(new HeaderCard("TRACERR", hasTrackingError, "Was there a tracking error during the scan?"));
		}
		
	}

}
