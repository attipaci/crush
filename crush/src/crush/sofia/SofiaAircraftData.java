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


 import jnum.Unit;
 import jnum.text.TableFormatter;
 import nom.tam.fits.Header;
 import nom.tam.fits.HeaderCard;
 import nom.tam.fits.HeaderCardException;
 import nom.tam.util.Cursor;

 public class SofiaAircraftData extends SofiaData implements TableFormatter.Entries {

     public BracketedValues altitude = new BracketedValues();
     public BracketedValues latitude = new BracketedValues();
     public BracketedValues longitude = new BracketedValues();
     public double airSpeed = Double.NaN, groundSpeed = Double.NaN, heading = Double.NaN, trackAngle = Double.NaN;

     public SofiaAircraftData() {}

     public SofiaAircraftData(SofiaHeader header) {
         this();
         parseHeader(header);
     }

     public void parseHeader(SofiaHeader header) {
         altitude.start = header.getDouble("ALTI_STA", Double.NaN) * Unit.ft;
         altitude.end = header.getDouble("ALTI_END", Double.NaN) * Unit.ft;
         airSpeed = header.getDouble("AIRSPEED", Double.NaN) * Unit.kn;
         groundSpeed = header.getDouble("GRDSPEED", Double.NaN) * Unit.kn;
         latitude.start = header.getDMSAngle("LAT_STA");
         latitude.end = header.getDMSAngle("LAT_END");
         longitude.start = header.getDMSAngle("LON_STA");
         longitude.end = header.getDMSAngle("LON_END");
         heading = header.getDouble("HEADING", Double.NaN) * Unit.deg;
         trackAngle = header.getDouble("TRACKANG", Double.NaN) * Unit.deg;
     }

     @Override
     public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
         //cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Aircraft Data ------>", false));
         if(!Double.isNaN(longitude.start)) cursor.add(new HeaderCard("LON_STA", longitude.start / Unit.deg, "(deg) Longitude at start of observation."));
         if(!Double.isNaN(longitude.end)) cursor.add(new HeaderCard("LON_END", longitude.end / Unit.deg, "(deg) Longitude at end of observation."));
         if(!Double.isNaN(latitude.start)) cursor.add(new HeaderCard("LAT_STA", latitude.start / Unit.deg, "(deg) Latitude at start of observation."));
         if(!Double.isNaN(latitude.end)) cursor.add(new HeaderCard("LAT_END", latitude.end / Unit.deg, "(deg) Latitude at end of observation."));
         if(!Double.isNaN(altitude.start)) cursor.add(new HeaderCard("ALTI_STA", altitude.start / Unit.ft, "(ft) Altitude at start of observation."));
         if(!Double.isNaN(altitude.end)) cursor.add(new HeaderCard("ALTI_END", altitude.end / Unit.ft, "(ft) Altitude at end of observation."));
         if(!Double.isNaN(airSpeed)) cursor.add(new HeaderCard("AIRSPEED", airSpeed / Unit.kn, "(kn) Airspeed at start of observation."));
         if(!Double.isNaN(groundSpeed)) cursor.add(new HeaderCard("GRDSPEED", groundSpeed / Unit.kn, "(kn) Ground speed at start of observation."));
         if(!Double.isNaN(heading)) cursor.add(new HeaderCard("HEADING", heading / Unit.deg, "(deg) True aircraft heading at start of observation."));
         if(!Double.isNaN(trackAngle)) cursor.add(new HeaderCard("TRACKANG", trackAngle / Unit.deg, "(deg) Aircraft tracking angle at start of observation."));	
     }

     @Override
     public Object getTableEntry(String name) {
         if(name.equals("alt")) return altitude.midPoint() / Unit.m;
         else if(name.equals("lon")) return longitude.midPoint() / Unit.deg;
         else if(name.equals("lat")) return latitude.midPoint() / Unit.deg;
         else if(name.equals("airspeed")) return airSpeed / Unit.kmh;
         else if(name.equals("gndspeed")) return groundSpeed / Unit.kmh;
         else if(name.equals("dir")) return heading / Unit.deg;
         else if(name.equals("trkangle")) return trackAngle / Unit.deg;

         return null;
     }

     @Override
     public String getLogID() {
         return "ac";
     }

 }
