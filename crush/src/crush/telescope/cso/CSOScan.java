/*******************************************************************************
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.telescope.cso;

import java.io.IOException;

import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;
import crush.telescope.GroundBasedScan;
import crush.telescope.HorizontalFrame;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.fits.FitsToolkit;
import jnum.math.Offset2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.util.DataTable;

public abstract class CSOScan<IntegrationType extends CSOIntegration<? extends HorizontalFrame>> extends GroundBasedScan<IntegrationType> implements Weather {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4194847199755728474L;

	protected double tau225GHz;
	protected double ambientT, pressure, humidity;
	
	public Class<? extends SphericalCoordinates> scanSystem;
	
	public Vector2D horizontalOffset, fixedOffset;
	public double elevationResponse = 1.0;

	public boolean addStaticOffsets = true;
	public int iMJD;	

	
	protected CSOScan(CSOInstrument<?> instrument) {
		super(instrument);
		
		/* Legacy crush coordinates, based on another telescope and map...
		site = new GeodeticCoordinates(
				-(155.0 * Unit.deg + 28.0 * Unit.arcmin + 33.0 * Unit.arcsec), 
				19.0 * Unit.deg  + 49.0 * Unit.arcmin + 21.0 * Unit.arcsec);
		*/
		
		
		// from the CSO website
		site = new GeodeticCoordinates(
				-(155.0 * Unit.deg + 28.0 * Unit.arcmin + 42.124 * Unit.arcsec), 
				19.0 * Unit.deg  + 49.0 * Unit.arcmin + 31.698 * Unit.arcsec,
				4070.0 * Unit.m);
	    
	    
		// Google
		//site = new GeodeticCoordinates(-155.4760399 * Unit.deg, 19.8225053 * Unit.deg);
		
		
		// Google
		/*
		site = new GeodeticCoordinates(
				-(155.0 * Unit.deg + 28.0 * Unit.arcmin + 30.89 * Unit.arcsec), 
				19.0 * Unit.deg  + 49.0 * Unit.arcmin + 17.70 * Unit.arcsec);
		*/
		
		
		isTracking = true;
	}



	
	@Override
	public void validate() {
		super.validate();	
		
		if(hasOption("elevation-response")) {
			try { 
				String fileName = option("elevation-response").getPath();	
				elevationResponse = new ElevationCouplingCurve(fileName).getValue(horizontal.elevation()); 
				info("Relative beam efficiency is " + Util.f3.format(elevationResponse));
				for(CSOIntegration<?> integration : this) integration.gain *= elevationResponse;
			}
			catch(IOException e) { 
				warning("Cannot read elevation response table..."); 
				warning(e);
			}
		}
	}
	
	
	@Override
	public void editScanHeader(Header header) throws HeaderCardException {	
		super.editScanHeader(header);
		Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
		c.add(new HeaderCard("MJD", iMJD, "Modified Julian Day."));
		c.add(new HeaderCard("FAZO", fixedOffset.x() / Unit.arcsec, "Fixed AZ pointing offset."));
		c.add(new HeaderCard("FZAO", -fixedOffset.y() / Unit.arcsec, "Fixed ZA pointing offset."));
		c.add(new HeaderCard("ELGAIN", elevationResponse, "Relative response at elevation."));
		c.add(new HeaderCard("TEMPERAT", ambientT / Unit.K, "Ambient temperature (K)."));
		c.add(new HeaderCard("PRESSURE", pressure / Unit.mbar, "Atmospheric pressure (mbar)."));
		c.add(new HeaderCard("HUMIDITY", humidity, "Humidity (%)."));
	}
	
	
	@Override
	public DataTable getPointingData() {
		DataTable data = super.getPointingData();
		Offset2D relative = getNativePointingIncrement(pointing);
		
		Unit sizeUnit = getInstrument().getSizeUnit();
		
		data.new Entry("FAZO", (relative.x() + fixedOffset.x()), sizeUnit);
		data.new Entry("FZAO", -(relative.y() + fixedOffset.y()), sizeUnit);
		
		return data;
	}
	
	@Override
	public String getPointingString(Offset2D pointing) {	
		return super.getPointingString(pointing) + "\n\n" +
			"  FAZO --> " + Util.f1.format((pointing.x() + fixedOffset.x()) / Unit.arcsec) +
			", FZAO --> " + Util.f1.format(-(pointing.y() + fixedOffset.y()) / Unit.arcsec);		
	}
	
	@Override
	public Object getTableEntry(String name) {
		if(name.equals("FAZO")) return fixedOffset.x() / Unit.arcsec;
		if(name.equals("FZAO")) return -fixedOffset.y() / Unit.arcsec;
		if(name.equals("dir")) return AstroSystem.getID(scanSystem);
		return super.getTableEntry(name);
	}
	
	@Override
	public double getAmbientHumidity() {
		return humidity;
	}

	@Override
	public double getAmbientPressure() {
		return pressure;
	}

	@Override
	public double getAmbientKelvins() {
		return ambientT;
	}

	@Override
	public double getWindDirection() {
		return Double.NaN;
	}

	@Override
	public double getWindPeak() {
		return Double.NaN;
	}

	@Override
	public double getWindSpeed() {
		return Double.NaN;
	}
	
	public static Class<? extends SphericalCoordinates> getScanSystem(int id) {
		switch(id) {
		case SCAN_ALTAZ: return HorizontalCoordinates.class;
		case SCAN_EQ2000:
		case SCAN_EQ1950: 
		case SCAN_APPARENT_EQ: return EquatorialCoordinates.class;
		case SCAN_GAL: return GalacticCoordinates.class;
		default: return null;
		}
	}
	
	
	public static final int SCAN_UNDEFINED = -1;
	public static final int SCAN_ALTAZ = 0;
	public static final int SCAN_EQ2000 = 1;
	public static final int SCAN_GAL = 2;
	public static final int SCAN_APPARENT_EQ = 3;
	public static final int SCAN_EQ1950 = 4;
	
}
