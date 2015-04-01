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
 ******************************************************************************/package crush.sofia;

import java.text.NumberFormat;

import kovacs.text.TableFormatter;
import kovacs.util.Unit;
import kovacs.util.Util;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaAircraftData extends SofiaHeaderData implements TableFormatter.Entries {

	public ScanBounds altitude = new ScanBounds();
	public ScanBounds latitude = new ScanBounds();
	public ScanBounds longitude = new ScanBounds();
	public float airSpeed = Float.NaN, groundSpeed = Float.NaN, heading = Float.NaN, trackAngle = Float.NaN;
	
	public SofiaAircraftData() {}
	
	public SofiaAircraftData(Header header) throws FitsException, HeaderCardException {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) throws FitsException, HeaderCardException {
		altitude.start = header.getDoubleValue("ALTI_STA", Double.NaN) * Unit.ft;
		altitude.end = header.getDoubleValue("ALTI_END", Double.NaN) * Unit.ft;
		airSpeed = header.getFloatValue("AIRSPEED", Float.NaN) * (float) Unit.kn;
		groundSpeed = header.getFloatValue("GRDSPEED", Float.NaN) * (float) Unit.kn;
		latitude.start = header.getDoubleValue("LAT_STA", Double.NaN) * Unit.deg;
		latitude.end = header.getDoubleValue("LAT_END", Double.NaN) * Unit.deg;
		longitude.start = header.getDoubleValue("LON_STA", Double.NaN) * Unit.deg;
		longitude.end = header.getDoubleValue("LON_END", Double.NaN) * Unit.deg;
		heading = header.getFloatValue("HEADING", Float.NaN) * (float) Unit.deg;
		trackAngle = header.getFloatValue("TRACKANG", Float.NaN) * (float) Unit.deg;
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(!Double.isNaN(longitude.start)) cursor.add(new HeaderCard("LON_STA", longitude.start / Unit.deg, "(deg) Longitude at start of observation."));
		if(!Double.isNaN(longitude.end)) cursor.add(new HeaderCard("LON_END", longitude.end / Unit.deg, "(deg) Longitude at end of observation."));
		if(!Double.isNaN(latitude.start)) cursor.add(new HeaderCard("LAT_STA", latitude.start / Unit.deg, "(deg) Latitude at start of observation."));
		if(!Double.isNaN(latitude.end)) cursor.add(new HeaderCard("LAT_END", latitude.end / Unit.deg, "(deg) Latitude at end of observation."));
		if(!Double.isNaN(altitude.start)) cursor.add(new HeaderCard("ALTI_STA", altitude.start / Unit.ft, "(ft) Altitude at start of observation."));
		if(!Double.isNaN(altitude.end)) cursor.add(new HeaderCard("ALTI_END", altitude.end / Unit.ft, "(ft) Altitude at end of observation."));
		if(!Float.isNaN(airSpeed)) cursor.add(new HeaderCard("AIRSPEED", airSpeed / Unit.kn, "(kn) Airspeed at start of observation."));
		if(!Float.isNaN(groundSpeed)) cursor.add(new HeaderCard("GRDSPEED", groundSpeed / Unit.kn, "(kn) Ground speed at start of observation."));
		if(!Float.isNaN(heading)) cursor.add(new HeaderCard("HEADING", heading / Unit.deg, "(deg) True aircraft heading at start of observation."));
		if(!Float.isNaN(trackAngle)) cursor.add(new HeaderCard("TRACKANG", trackAngle / Unit.deg, "(deg) Aircraft tracking angle at start of observation."));	
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("alt")) return Util.defaultFormat(altitude.midPoint() / Unit.m, f);
		else if(name.equals("alt0")) return Util.defaultFormat(altitude.start / Unit.m, f);
		else if(name.equals("altf")) return Util.defaultFormat(altitude.end / Unit.m, f);
		else if(name.equals("lon")) return Util.defaultFormat(longitude.midPoint() / Unit.deg, f);
		else if(name.equals("lon0")) return Util.defaultFormat(longitude.start / Unit.deg, f);
		else if(name.equals("lonf")) return Util.defaultFormat(longitude.end / Unit.deg, f);
		else if(name.equals("lat")) return Util.defaultFormat(latitude.midPoint() / Unit.deg, f);
		else if(name.equals("lat0")) return Util.defaultFormat(latitude.start / Unit.deg, f);
		else if(name.equals("latf")) return Util.defaultFormat(latitude.end / Unit.deg, f);
		else if(name.equals("airspeed")) return Util.defaultFormat(airSpeed / Unit.kmh, f);
		else if(name.equals("gndspeed")) return Util.defaultFormat(groundSpeed / Unit.kmh, f);
		else if(name.equals("dir")) return Util.defaultFormat(heading / Unit.deg, f);
		else if(name.equals("trAngle")) return Util.defaultFormat(trackAngle / Unit.deg, f);
		
		return null;
	}

}
