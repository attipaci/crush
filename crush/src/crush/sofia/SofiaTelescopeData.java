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
	public double coarseElevation = Double.NaN;
	public double fineDriveElevation = Double.NaN, fineDriveCrossElevation = Double.NaN, fineDriveLOS = Double.NaN;
	public String tascuStatus, fbcStatus;
	public BracketedValues zenithAngle = new BracketedValues();
	public double sunAngle = Double.NaN, moonAngle = Double.NaN;
	public String userCoordinateSystem, userReferenceSystem;
	public double userRefLon = Double.NaN, userRefLat = Double.NaN, userRefAngle = Double.NaN;
	public double userLongitude = Double.NaN, userLatitude = Double.NaN, userEquinox = Double.NaN;
	public double vHelio = Double.NaN, vLSR = Double.NaN;
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
		
		coarseElevation = header.getDoubleValue("COARSEEL", Double.NaN) * Unit.deg;			// new in 3.0
		fineDriveElevation = header.getDoubleValue("FD_EL", Double.NaN) * Unit.deg;			// new in 3.0
		fineDriveCrossElevation = header.getDoubleValue("FD_XEL", Double.NaN) * Unit.deg;	// new in 3.0
		fineDriveLOS = header.getDoubleValue("FD_LOS", Double.NaN) * Unit.deg;				// new in 3.0
			
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
		
		sunAngle = header.getDoubleValue("SUNANGL", Double.NaN) * Unit.deg;				// not in 3.0
		moonAngle = header.getDoubleValue("MOONANGL", Double.NaN) * Unit.deg;			// not in 3.0
		
		userCoordinateSystem = getStringValue(header, "USRCRDSY");						// not in 3.0
		userReferenceSystem = getStringValue(header, "USRREFCR");						// not in 3.0
		
		userRefLon = header.getDoubleValue("USRORIGX", Double.NaN) * Unit.deg;			// not in 3.0
		userRefLat = header.getDoubleValue("USRORIGY", Double.NaN) * Unit.deg;			// not in 3.0
		userRefAngle = header.getDoubleValue("USRCRROT", Double.NaN) * Unit.deg;		// not in 3.0
		
		userLongitude = header.getDoubleValue("USRX", Double.NaN) * Unit.deg;			// not in 3.0
		userLatitude = header.getDoubleValue("USRY", Double.NaN) * Unit.deg;			// not in 3.0
		userEquinox = header.getDoubleValue("USREQNX", Double.NaN);						// not in 3.0
		
		vHelio = header.getDoubleValue("HELIOCOR", Double.NaN) * Unit.km / Unit.s;		// not in 3.0
		vLSR = header.getDoubleValue("LSR_COR", Double.NaN) * Unit.km / Unit.s;			// not in 3.0
		
		trackingMode = getStringValue(header, "TRACMODE");
		hasTrackingError = header.getBooleanValue("TRACERR", false);
		
	}

	public void updateStatusKeys(Header header) throws HeaderCardException {
		if(tascuStatus != null) header.addValue("TSC_STAT", tascuStatus, "TASCU system status at end.");
		if(fbcStatus != null) header.addValue("FBC_STAT", fbcStatus, "flexible body compensation system status at end.");
	}
	
	public void updateElevationKeys(Header header) throws HeaderCardException {
		if(!Double.isNaN(relElevation)) header.addValue("TELEL", relElevation / Unit.deg, "(deg) Telescope elevation in cavity.");
		if(!Double.isNaN(crossElevation)) header.addValue("TELXEL", crossElevation / Unit.deg, "(deg) Telescope cross elevation in cavity.");
		if(!Double.isNaN(lineOfSightAngle)) header.addValue("TELLOS", lineOfSightAngle / Unit.deg, "(deg) Telescope line-of-sight angle in cavity.");
	}
	
	@Override
	public void editHeader(Header header, Cursor cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Telescope Data ------>", false));
		
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
	
		if(!Double.isNaN(coarseElevation)) cursor.add(new HeaderCard("COARSEEL", coarseElevation / Unit.deg, "(deg) Coarse drive elevation."));
		if(!Double.isNaN(fineDriveElevation)) cursor.add(new HeaderCard("FD_EL", fineDriveElevation / Unit.deg, "(deg) Fine drive elevation."));
		if(!Double.isNaN(fineDriveCrossElevation)) cursor.add(new HeaderCard("FD_XEL", fineDriveCrossElevation / Unit.deg, "(deg) Fine drive cross elevation."));
		if(!Double.isNaN(fineDriveLOS)) cursor.add(new HeaderCard("FD_LOS", fineDriveLOS / Unit.deg, "(deg) Fine drive line-of-sight angle."));
	
		if(tascuStatus != null) cursor.add(new HeaderCard("TSC_STAT", tascuStatus, "TASCU system status at end."));
		if(fbcStatus != null) cursor.add(new HeaderCard("FBC_STAT", fbcStatus, "flexible body compensation system status at end."));
	
		if(requestedEquatorial != null) {
			cursor.add(new HeaderCard("OBSRA", requestedEquatorial.RA() / Unit.hourAngle, "(hour) Requested RA."));
			cursor.add(new HeaderCard("OBSDEC", requestedEquatorial.DEC() / Unit.deg, "(deg) Requested DEC."));
			cursor.add(new HeaderCard("EQUINOX", requestedEquatorial.epoch.getYear(), "(yr) Requested epoch."));
		}
		
		if(!Double.isNaN(zenithAngle.start)) cursor.add(new HeaderCard("ZA_START", zenithAngle.start / Unit.deg, "(deg) Zenith angle at start."));
		if(!Double.isNaN(zenithAngle.end)) cursor.add(new HeaderCard("ZA_END", zenithAngle.end / Unit.deg, "(deg) Zenith angle at end."));
		
		if(!Double.isNaN(sunAngle)) cursor.add(new HeaderCard("SUNANGL", sunAngle / Unit.deg, "(deg) Angle btw tel. pointing and Sun."));
		if(!Double.isNaN(zenithAngle.end)) cursor.add(new HeaderCard("MOONANGL", moonAngle / Unit.deg, "(deg) Angle btw tel. pointing and Moon ."));
		
		if(userCoordinateSystem != null) cursor.add(new HeaderCard("USRCRDSY", userCoordinateSystem, "User coordinate system name."));
		if(userReferenceSystem != null) cursor.add(new HeaderCard("USRREFCR", userReferenceSystem, "User reference system name."));
		
		if(!Double.isNaN(userRefLon)) cursor.add(new HeaderCard("USRORIGX", userRefLon / Unit.deg, "(deg) user origin LON in ref. sys."));
		if(!Double.isNaN(userRefLat)) cursor.add(new HeaderCard("USRORIGY", userRefLat / Unit.deg, "(deg) user origin LAT in ref. sys."));
		if(!Double.isNaN(userRefAngle)) cursor.add(new HeaderCard("USRCRROT", userRefAngle / Unit.deg, "(deg) rotation of user system to reference."));

		if(!Double.isNaN(userLongitude)) cursor.add(new HeaderCard("USRX", userLongitude / Unit.deg, "(deg) Object longitude in user system."));
		if(!Double.isNaN(userLatitude)) cursor.add(new HeaderCard("USRY", userLatitude / Unit.deg, "(deg) Object latitude in user system."));
		if(!Double.isNaN(userEquinox)) cursor.add(new HeaderCard("USREQNX", userEquinox, "(yr) User coordinate epoch."));
		
		if(!Double.isNaN(vHelio)) cursor.add(new HeaderCard("HELIOCOR", vHelio, "(km/s) Heliocentric velocity correction."));
		if(!Double.isNaN(vLSR)) cursor.add(new HeaderCard("LSR_COR", vLSR, "(km/s) LSR velocity correction."));
		
		if(trackingMode != null) {
			cursor.add(new HeaderCard("TRACMODE", trackingMode, "SOFIA tracking mode."));
			cursor.add(new HeaderCard("TRACERR", hasTrackingError, "Was there a tracking error during the scan?"));
		}
		
	}

}
