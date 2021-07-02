/* *****************************************************************************
 * Copyright (c) 2021 Attila Kovacs <attila[AT]sigmyne.com>.
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
import jnum.astro.EquatorialCoordinates;
import jnum.astro.EquatorialSystem;
import jnum.fits.FitsToolkit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaTelescopeData extends SofiaData {
    public String telescope = "SOFIA 2.5m";
    public String telConfig;
    public EquatorialSystem system;
    public EquatorialCoordinates boresightEquatorial = new EquatorialCoordinates(Double.NaN, Double.NaN, EquatorialSystem.ICRS);
    public EquatorialCoordinates requestedEquatorial = new EquatorialCoordinates(Double.NaN, Double.NaN, EquatorialSystem.ICRS);
    public double VPA = Double.NaN;
    public String lastRewind;
    public BracketedValues focusT = new BracketedValues();
    public double relElevation = Double.NaN, crossElevation = Double.NaN, lineOfSightAngle = Double.NaN;
    public String tascuStatus, fbcStatus;
    public BracketedValues zenithAngle = new BracketedValues();
    public String trackingMode;
    public boolean hasTrackingError = false;

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

        system = EquatorialSystem.fromHeader(header.getFitsHeader());
        
        requestedEquatorial = new EquatorialCoordinates(Double.NaN, Double.NaN, system);
        boresightEquatorial = new EquatorialCoordinates(Double.NaN, Double.NaN, system);
        
        if(header.containsKey("TELEQUI")) boresightEquatorial.setSystem(EquatorialSystem.forString(header.getString("TELEQUI", "J2000")));


        try { 
            double RA = header.getHMSTime("TELRA");
            double DEC = header.getDMSAngle("TELDEC");
            if(!Double.isNaN(RA) && !Double.isNaN(DEC)) boresightEquatorial.set(RA * Unit.timeAngle, DEC); 
        }
        catch(Exception e) {}

        try { 
            double RA = header.getHMSTime("OBSRA");
            double DEC = header.getDMSAngle("OBSDEC");      
            if(!Double.isNaN(RA) && !Double.isNaN(DEC)) requestedEquatorial.set(RA * Unit.timeAngle, DEC); 
        }
        catch(Exception e) {}


        VPA = header.getDouble("TELVPA") * Unit.deg;

        lastRewind = header.getString("LASTREW");
        focusT.start = header.getDouble("FOCUS_ST") * Unit.um;
        focusT.end = header.getDouble("FOCUS_EN") * Unit.um;

        relElevation = header.getDouble("TELEL") * Unit.deg;
        crossElevation = header.getDouble("TELXEL") * Unit.deg;
        lineOfSightAngle = header.getDouble("TELLOS") * Unit.deg;

        tascuStatus = header.getString("TSC-STAT");
        fbcStatus = header.getString("FBC-STAT");

        zenithAngle.start = header.getDouble("ZA_START") * Unit.deg;
        zenithAngle.end = header.getDouble("ZA_END") * Unit.deg;

        trackingMode = header.getString("TRACMODE", null);
        hasTrackingError = header.getBoolean("TRACERR", false);


    }


    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Telescope Data ------>", false));

        c.add(makeCard("TELESCOP", telescope, "observatory name."));
        c.add(makeCard("TELCONF", telConfig, "telescope configuration."));

        EquatorialCoordinates eq = boresightEquatorial == null ? new EquatorialCoordinates(Double.NaN, Double.NaN) : boresightEquatorial;

        c.add(makeCard("TELRA", eq.RA() / Unit.hourAngle, "(hour) Boresight RA."));
        c.add(makeCard("TELDEC", eq.DEC() / Unit.deg, "(deg) Boresight DEC."));
        c.add(makeCard("TELEQUI", eq.getSystem().getJulianYear(), "Boresight epoch."));

        c.add(makeCard("TELVPA", VPA / Unit.deg, "(deg) Boresight position angle."));

        c.add(makeCard("LASTREW", lastRewind, "UTC time of last telescope rewind."));

        c.add(makeCard("FOCUS_ST", focusT.start / Unit.um, "(um) Focus T value at start."));
        c.add(makeCard("FOCUS_EN", focusT.end / Unit.um, "(um) Focus T value at end."));

        c.add(makeCard("TELEL", relElevation / Unit.deg, "(deg) Telescope elevation in cavity."));
        c.add(makeCard("TELXEL", crossElevation / Unit.deg, "(deg) Telescope cross elevation in cavity."));
        c.add(makeCard("TELLOS", lineOfSightAngle / Unit.deg, "(deg) Telescope line-of-sight angle in cavity."));

        c.add(makeCard("TSC-STAT", tascuStatus, "TASCU system status at end."));
        c.add(makeCard("FBC-STAT", fbcStatus, "flexible body compensation system status at end."));

        eq = requestedEquatorial == null ? new EquatorialCoordinates(Double.NaN, Double.NaN) : requestedEquatorial;

        c.add(makeCard("OBSRA", eq.RA() / Unit.hourAngle, "(hour) Requested RA."));
        c.add(makeCard("OBSDEC", eq.DEC() / Unit.deg, "(deg) Requested DEC."));
        c.add(makeCard("EQUINOX", eq.getSystem().getJulianYear(), "(yr) The coordinate epoch."));


        if(!Double.isNaN(zenithAngle.start)) c.add(new HeaderCard("ZA_START", zenithAngle.start / Unit.deg, "(deg) Zenith angle at start."));
        if(!Double.isNaN(zenithAngle.end)) c.add(new HeaderCard("ZA_END", zenithAngle.end / Unit.deg, "(deg) Zenith angle at end."));

        if(trackingMode != null) {
            c.add(new HeaderCard("TRACMODE", trackingMode, "SOFIA tracking mode."));
            c.add(new HeaderCard("TRACERR", hasTrackingError, "Was there a tracking error during the scan?"));
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
        else if(name.equals("epoch")) return requestedEquatorial.getSystem();
        else if(name.equals("vpa")) return VPA / Unit.deg; 
        else if(name.equals("za")) return zenithAngle.midPoint() / Unit.deg;
        else if(name.equals("los")) return lineOfSightAngle / Unit.deg;
        else if(name.equals("el")) return relElevation / Unit.deg;
        else if(name.equals("xel")) return crossElevation / Unit.deg;
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

    @Override
    public void setEnd(SofiaData last) {
        super.setEnd(last);

        SofiaTelescopeData telescope = (SofiaTelescopeData) last;
        tascuStatus = telescope.tascuStatus;
        fbcStatus = telescope.fbcStatus;
    }

}
