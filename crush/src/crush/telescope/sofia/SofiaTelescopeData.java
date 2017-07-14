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
import jnum.astro.CoordinateEpoch;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.JulianEpoch;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;

public class SofiaTelescopeData extends SofiaData {
	public String telescope = "SOFIA 2.5m";
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
	
	public CoordinateEpoch epoch = CoordinateEpoch.J2000;
	
	public SofiaTelescopeData() {}
	
	public SofiaTelescopeData(SofiaHeader header) {
		this();
		parseHeader(header);
	}
	
	public boolean isTracking() { 
		if(trackingMode == null) return false;
		return !trackingMode.equalsIgnoreCase("OFF");
	}

	public void parseHeader(SofiaHeader header) {
		
		if(header.containsKey("TELESCOP")) telescope = header.getString("TELESCOP");
		
		telConfig = header.getString("TELCONF");
		
		boresightEquatorial = new EquatorialCoordinates();
		if(header.containsKey("TELEQUI")) boresightEquatorial.setEpoch(new JulianEpoch(header.getDouble("TELEQUI")));
		
		try { boresightEquatorial.set(header.getHMSTime("TELRA") * Unit.timeAngle, header.getDMSAngle("TELDEC")); }
		catch(Exception e) { boresightEquatorial = null; }
		
		VPA = header.getDouble("TELVPA", Double.NaN) * Unit.deg;
		
		lastRewind = header.getString("LASTREW");
		focusT.start = header.getDouble("FOCUS_ST", Double.NaN) * Unit.um;
		focusT.end = header.getDouble("FOCUS_EN", Double.NaN) * Unit.um;
		
		relElevation = header.getDouble("TELEL", Double.NaN) * Unit.deg;
		crossElevation = header.getDouble("TELXEL", Double.NaN) * Unit.deg;
		lineOfSightAngle = header.getDouble("TELLOS", Double.NaN) * Unit.deg;
		
		coarseElevation = header.getDouble("COARSEEL", Double.NaN) * Unit.deg;			// new in 3.0
		fineDriveElevation = header.getDouble("FD_EL", Double.NaN) * Unit.deg;			// new in 3.0
		fineDriveCrossElevation = header.getDouble("FD_XEL", Double.NaN) * Unit.deg;	// new in 3.0
		fineDriveLOS = header.getDouble("FD_LOS", Double.NaN) * Unit.deg;				// new in 3.0
			
		tascuStatus = header.getString("TSC-STAT");
		fbcStatus = header.getString("FBC-STAT");
		
		double epochYear = header.getDouble("EQUINOX");
		epoch = Double.isNaN(epochYear) ? CoordinateEpoch.J2000 : CoordinateEpoch.forString(epochYear + "");
		
		requestedEquatorial = null;
		boresightEquatorial = null;
		
		if(epoch != null) {
		    try { 
                double RA = header.getHMSTime("TELRA");
                double DEC = header.getDMSAngle("TELDEC");
                if(!Double.isNaN(RA) && !Double.isNaN(DEC)) 
                    boresightEquatorial = new EquatorialCoordinates(RA * Unit.timeAngle, DEC, epoch); 
            }
            catch(Exception e) {}
		    
		    try { 
		        double RA = header.getHMSTime("OBSRA");
		        double DEC = header.getDMSAngle("OBSDEC");      
		        if(!Double.isNaN(RA) && !Double.isNaN(DEC)) 
		            requestedEquatorial = new EquatorialCoordinates(RA * Unit.timeAngle, DEC, epoch); 
		    }
		    catch(Exception e) {}
		}
		
		zenithAngle.start = header.getDouble("ZA_START", Double.NaN) * Unit.deg;
		zenithAngle.end = header.getDouble("ZA_END", Double.NaN) * Unit.deg;
		
		sunAngle = header.getDouble("SUNANGL", Double.NaN) * Unit.deg;				// not in 3.0
		moonAngle = header.getDouble("MOONANGL", Double.NaN) * Unit.deg;			// not in 3.0
		
		userCoordinateSystem = header.getString("USRCRDSY");						// not in 3.0
		userReferenceSystem = header.getString("USRREFCR");						    // not in 3.0
		
		userRefLon = header.getDouble("USRORIGX", Double.NaN) * Unit.deg;			// not in 3.0
		userRefLat = header.getDouble("USRORIGY", Double.NaN) * Unit.deg;			// not in 3.0
		userRefAngle = header.getDouble("USRCRROT", Double.NaN) * Unit.deg;		    // not in 3.0
		
		userLongitude = header.getDouble("USRX", Double.NaN) * Unit.deg;			// not in 3.0
		userLatitude = header.getDouble("USRY", Double.NaN) * Unit.deg;			    // not in 3.0
		userEquinox = header.getDouble("USREQNX", Double.NaN);						// not in 3.0
		
		vHelio = header.getDouble("HELIOCOR", Double.NaN) * Unit.km / Unit.s;		// not in 3.0
		vLSR = header.getDouble("LSR_COR", Double.NaN) * Unit.km / Unit.s;			// not in 3.0
		
		trackingMode = header.getString("TRACMODE");
		hasTrackingError = header.getBoolean("TRACERR", false);
		
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
	public void editHeader(Header header) throws HeaderCardException {
		//header.addLine(new HeaderCard("COMMENT", "<------ SOFIA Telescope Data ------>", false));
		
		if(telescope != null) header.addLine(new HeaderCard("TELESCOP", telescope, "observatory name."));
		if(telConfig != null) header.addLine(new HeaderCard("TELCONF", telConfig, "telescope configuration."));
		
		if(boresightEquatorial != null) {
			header.addLine(new HeaderCard("TELRA", boresightEquatorial.RA() / Unit.hourAngle, "(hour) Boresight RA."));
			header.addLine(new HeaderCard("TELDEC", boresightEquatorial.DEC() / Unit.deg, "(deg) Boresight DEC."));
			header.addLine(new HeaderCard("TELEQUI", boresightEquatorial.epoch.getYear(), "(yr) Boresight epoch."));
		}
		
		if(!Double.isNaN(VPA)) header.addLine(new HeaderCard("TELVPA", VPA / Unit.deg, "(deg) Boresight position angle."));
		
		if(lastRewind != null) header.addLine(new HeaderCard("LASTREW", lastRewind, "UTC time of last telescope rewind."));
		
		if(!Double.isNaN(focusT.start)) header.addLine(new HeaderCard("FOCUS_ST", focusT.start / Unit.um, "(um) Focus T value at start."));
		if(!Double.isNaN(focusT.end)) header.addLine(new HeaderCard("FOCUS_EN", focusT.end / Unit.um, "(um) Focus T value at end."));
		
		if(!Double.isNaN(relElevation)) header.addLine(new HeaderCard("TELEL", relElevation / Unit.deg, "(deg) Telescope elevation in cavity."));
		if(!Double.isNaN(crossElevation)) header.addLine(new HeaderCard("TELXEL", crossElevation / Unit.deg, "(deg) Telescope cross elevation in cavity."));
		if(!Double.isNaN(lineOfSightAngle)) header.addLine(new HeaderCard("TELLOS", lineOfSightAngle / Unit.deg, "(deg) Telescope line-of-sight angle in cavity."));
	
		if(!Double.isNaN(coarseElevation)) header.addLine(new HeaderCard("COARSEEL", coarseElevation / Unit.deg, "(deg) Coarse drive elevation."));
		if(!Double.isNaN(fineDriveElevation)) header.addLine(new HeaderCard("FD_EL", fineDriveElevation / Unit.deg, "(deg) Fine drive elevation."));
		if(!Double.isNaN(fineDriveCrossElevation)) header.addLine(new HeaderCard("FD_XEL", fineDriveCrossElevation / Unit.deg, "(deg) Fine drive cross elevation."));
		if(!Double.isNaN(fineDriveLOS)) header.addLine(new HeaderCard("FD_LOS", fineDriveLOS / Unit.deg, "(deg) Fine drive line-of-sight angle."));
	
		if(tascuStatus != null) header.addLine(new HeaderCard("TSC_STAT", tascuStatus, "TASCU system status at end."));
		if(fbcStatus != null) header.addLine(new HeaderCard("FBC_STAT", fbcStatus, "flexible body compensation system status at end."));
	
		if(requestedEquatorial != null) {
			header.addLine(new HeaderCard("OBSRA", requestedEquatorial.RA() / Unit.hourAngle, "(hour) Requested RA."));
			header.addLine(new HeaderCard("OBSDEC", requestedEquatorial.DEC() / Unit.deg, "(deg) Requested DEC."));
			header.addLine(new HeaderCard("EQUINOX", epoch.getYear(), "(yr) The coordinate epoch."));
		}
		
		if(!Double.isNaN(zenithAngle.start)) header.addLine(new HeaderCard("ZA_START", zenithAngle.start / Unit.deg, "(deg) Zenith angle at start."));
		if(!Double.isNaN(zenithAngle.end)) header.addLine(new HeaderCard("ZA_END", zenithAngle.end / Unit.deg, "(deg) Zenith angle at end."));
		
		if(!Double.isNaN(sunAngle)) header.addLine(new HeaderCard("SUNANGL", sunAngle / Unit.deg, "(deg) Angle btw tel. pointing and Sun."));
		if(!Double.isNaN(moonAngle)) header.addLine(new HeaderCard("MOONANGL", moonAngle / Unit.deg, "(deg) Angle btw tel. pointing and Moon ."));
		
		if(userCoordinateSystem != null) header.addLine(new HeaderCard("USRCRDSY", userCoordinateSystem, "User coordinate system name."));
		if(userReferenceSystem != null) header.addLine(new HeaderCard("USRREFCR", userReferenceSystem, "User reference system name."));
		
		if(!Double.isNaN(userRefLon)) header.addLine(new HeaderCard("USRORIGX", userRefLon / Unit.deg, "(deg) user origin LON in ref. sys."));
		if(!Double.isNaN(userRefLat)) header.addLine(new HeaderCard("USRORIGY", userRefLat / Unit.deg, "(deg) user origin LAT in ref. sys."));
		if(!Double.isNaN(userRefAngle)) header.addLine(new HeaderCard("USRCRROT", userRefAngle / Unit.deg, "(deg) rotation of user system to reference."));

		if(!Double.isNaN(userLongitude)) header.addLine(new HeaderCard("USRX", userLongitude / Unit.deg, "(deg) Object longitude in user system."));
		if(!Double.isNaN(userLatitude)) header.addLine(new HeaderCard("USRY", userLatitude / Unit.deg, "(deg) Object latitude in user system."));
		if(!Double.isNaN(userEquinox)) header.addLine(new HeaderCard("USREQNX", userEquinox, "(yr) User coordinate epoch."));
		
		if(!Double.isNaN(vHelio)) header.addLine(new HeaderCard("HELIOCOR", vHelio, "(km/s) Heliocentric velocity correction."));
		if(!Double.isNaN(vLSR)) header.addLine(new HeaderCard("LSR_COR", vLSR, "(km/s) LSR velocity correction."));
		
		if(trackingMode != null) {
			header.addLine(new HeaderCard("TRACMODE", trackingMode, "SOFIA tracking mode."));
			header.addLine(new HeaderCard("TRACERR", hasTrackingError, "Was there a tracking error during the scan?"));
		}
		
	}
	
	
	// TODO complete...
	@Override
	public Object getTableEntry(String name) {      
	    
	    if(name.equals("focus")) return focusT.midPoint() / Unit.um;
	    else if(name.equals("bra")) return boresightEquatorial.RA() / Unit.hourAngle;
	    else if(name.equals("bdec")) return boresightEquatorial.DEC() / Unit.deg;
	    else if(name.equals("rra")) return requestedEquatorial.RA() / Unit.hourAngle;
        else if(name.equals("rdec")) return requestedEquatorial.DEC() / Unit.deg;
        else if(name.equals("epoch")) return epoch.toString();
	    else if(name.equals("vpa")) return VPA / Unit.deg; 
	    else if(name.equals("za")) return zenithAngle.midPoint() / Unit.deg;
	    else if(name.equals("los")) return lineOfSightAngle / Unit.deg;
	    else if(name.equals("el")) return relElevation / Unit.deg;
	    else if(name.equals("xel")) return crossElevation / Unit.deg;
	    else if(name.equals("sunang")) return sunAngle / Unit.deg;
	    else if(name.equals("moonang")) return moonAngle / Unit.deg;
	    else if(name.equals("vlsr")) return vLSR / (Unit.km / Unit.s);
        else if(name.equals("vhelio")) return vHelio / (Unit.km / Unit.s);
	    else if(name.equals("trkerr")) return hasTrackingError;
        else if(name.equals("trkmode")) return trackingMode;
        else if(name.equals("cfg")) return telConfig;
	    else if(name.equals("fbc")) return fbcStatus;	    
	    else if(name.equals("rew")) return lastRewind;
	   
	    return super.getTableEntry(name);
	}

    @Override
    public String getLogID() {
        return "tel";
    }

}
