/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope;


import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.astro.CoordinateEpoch;
import jnum.astro.GeodeticCoordinates;
import jnum.astro.HorizontalCoordinates;
import jnum.astro.JulianEpoch;
import jnum.astro.Weather;
import jnum.fits.FitsToolkit;
import jnum.math.SphericalCoordinates;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public abstract class GroundBasedScan<IntegrationType extends GroundBasedIntegration<?>> extends TelescopeScan<IntegrationType> implements Weather { 
    /**
     * 
     */
    private static final long serialVersionUID = 5664914987667749424L;

    public HorizontalCoordinates horizontal;
    public GeodeticCoordinates site;

    public double LST = Double.NaN;


    protected GroundBasedScan(TelescopeInstrument<?> instrument) {
        super(instrument);
    }

    
    @Override
    public SphericalCoordinates getNativeCoordinates() { return horizontal; }
    
    @Override
    public SphericalCoordinates getReferenceCoordinates() { return equatorial; }

    @Override
    public void validate() {
        if(!hasOption("lab")) {
            if(equatorial == null) calcEquatorial();
            if(apparent == null) calcApparent();
            // TODO below are only for horizontal...
            if(Double.isNaN(LST)) LST = 0.5 * (getFirstIntegration().getFirstFrame().LST + getFirstIntegration().getFirstFrame().LST);
            if(horizontal == null && site != null) calcHorizontal();
            if(horizontal != null) info("  Horizontal: " + horizontal);
        }

        super.validate();
    }

    public void calcEquatorial() {
        equatorial = horizontal.toEquatorial(site, LST);
        equatorial.epoch = JulianEpoch.forMJD(getMJD());
        if(fromApparent == null) calcPrecessions(CoordinateEpoch.J2000);
        fromApparent.precess(equatorial);       
    }


    public void calcHorizontal() {  
        if(apparent == null) calcApparent();
        horizontal = apparent.toHorizontal(site, LST);
    }
    
    @Override
    public double getPositionAngle() {
        return 0.5 * (getFirstIntegration().getFirstFrame().getParallacticAngle().value() + getLastIntegration().getLastFrame().getParallacticAngle().value());
    }
    
    @Override
    public SphericalCoordinates getPositionReference(String system) {
        if(system.equals("horizontal")) return horizontal;
        return super.getPositionReference(system);
    }

    @Override
    public void editScanHeader(Header header) throws HeaderCardException {
        super.editScanHeader(header);

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);

        if(!Double.isNaN(LST)) c.add(new HeaderCard("LST", LST / Unit.hour, "Local Sidereal Time (hours)"));
        if(!Double.isNaN(horizontal.AZ())) c.add(new HeaderCard("AZ", horizontal.AZ()/Unit.deg, "Azymuth (deg)."));
        if(!Double.isNaN(horizontal.EL())) c.add(new HeaderCard("EL", horizontal.EL()/Unit.deg, "Elevation (deg)."));
        if(!Double.isNaN(getPositionAngle())) c.add(new HeaderCard("PA", getPositionAngle()/Unit.deg, "Direction of zenith w.r.t. North (deg)"));

        if(site != null) {
            c.add(new HeaderCard("SITELON", Util.af1.format(site.longitude()), "Geodetic longitude of the observing site (deg)"));
            c.add(new HeaderCard("SITELAT", Util.af1.format(site.latitude()), "Geodetic latitude of the observing site (deg)"));
        }
    }

    @Override
    public void applyPointing() {
        super.applyPointing();

        // Reset the source coordinates to the pointing center
        if(pointing.getCoordinates() instanceof HorizontalCoordinates) 
            pointing.setCoordinates(horizontal.clone());

    }
    
    
    @Override
    public String getASCIIHeader() {
        return super.getASCIIHeader() + 
                "# Horizontal: " + horizontal + "\n";
    }
    

    @Override
    public Object getTableEntry(String name) {
        if(horizontal == null) if(equatorial != null) if(site != null)
            horizontal = equatorial.toHorizontal(site, LST);

        if(name.equals("AZ")) return horizontal.AZ();
        if(name.equals("EL")) return horizontal.EL();
        if(name.equals("AZd")) return horizontal.AZ() / Unit.deg;
        if(name.equals("ELd")) return horizontal.EL() / Unit.deg;
        if(name.equals("LST")) return LST;
        if(name.equals("LSTh")) return LST / Unit.hour;  
        if(name.equals("Tamb")) return getAmbientKelvins() - Constant.zeroCelsius;
        if(name.equals("humidity")) return getAmbientHumidity();
        if(name.equals("pressure")) return getAmbientPressure() / Unit.hPa;
        if(name.equals("windspeed")) return getWindSpeed() / (Unit.m / Unit.s);
        if(name.equals("windpk")) return getWindPeak() / (Unit.m / Unit.s);
        if(name.equals("winddir"))  return getWindDirection() / Unit.deg;
        
        
        return super.getTableEntry(name);
    }


}
