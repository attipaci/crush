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
 ******************************************************************************/package crush.sofia;


 import jnum.Unit;
 import nom.tam.fits.Header;
 import nom.tam.fits.HeaderCard;
 import nom.tam.fits.HeaderCardException;
 import nom.tam.util.Cursor;

 public class SofiaChopperData extends SofiaData {
     public double frequency = Double.NaN;
     public String profileType;
     public String symmetryType;
     public double amplitude = Double.NaN, amplitude2 = Double.NaN;
     public String coordinateSystem;
     public double angle = Double.NaN;
     public double tip = Double.NaN;
     public double tilt = Double.NaN;
     public String signalSource, driveMode, waveFunction;
     public double settlingTime = Double.NaN;
     public double phase = Double.NaN;


     public SofiaChopperData() {}

     public SofiaChopperData(SofiaHeader header) {
         this();
         parseHeader(header);
     }


     public void parseHeader(SofiaHeader header) {
         frequency = header.getDouble("CHPFREQ", Double.NaN) * Unit.Hz;
         profileType = header.getString("CHPPROF");
         symmetryType = header.getString("CHPSYM");
         amplitude = header.getDouble("CHPAMP1", Double.NaN) * Unit.arcsec;
         amplitude2 = header.getDouble("CHPAMP2", Double.NaN) * Unit.arcsec;
         coordinateSystem = header.getString("CHPCRSYS");
         angle = header.getDouble("CHPANGLE", Double.NaN) * Unit.deg;
         tip = header.getDouble("CHPTIP", Double.NaN) * Unit.arcsec;
         tilt = header.getDouble("CHPTILT", Double.NaN) * Unit.arcsec;
         signalSource = header.getString("CHPSRC");								// not in 3.0
         driveMode = header.getString("CHPACDC");									// new in 3.0
         waveFunction = header.getString("CHPFUNC");								// not in 3.0
         settlingTime = header.getDouble("CHPSETL", Double.NaN) * Unit.ms;			// not in 3.0
         phase = header.getDouble("CHPPHASE", Double.NaN) * Unit.ms;				// int->float in 3.0
     }

     @Override
     public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
         //cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Chopper Data ------>", false));
         if(!Double.isNaN(frequency)) cursor.add(new HeaderCard("CHPFREQ", frequency / Unit.Hz, "(Hz) Chop frequency."));
         if(!Double.isNaN(amplitude)) cursor.add(new HeaderCard("CHPAMP1", amplitude / Unit.arcsec, "(arcsec) Chop amplitude on sky."));
         if(!Double.isNaN(amplitude2)) cursor.add(new HeaderCard("CHPAMP2", amplitude2 / Unit.arcsec, "(arcsec) Second chop amplitude on sky."));
         if(!Double.isNaN(angle)) cursor.add(new HeaderCard("CHPANGLE", angle / Unit.deg, "(deg) Chop angle on sky."));
         if(!Double.isNaN(tip)) cursor.add(new HeaderCard("CHPTIP", tip / Unit.arcsec, "(arcsec) Chopper tip on sky."));
         if(!Double.isNaN(tilt)) cursor.add(new HeaderCard("CHPTILT", tilt / Unit.arcsec, "(arcsec) Chop tilt on sky."));
         if(profileType != null) cursor.add(new HeaderCard("CHPPROF", profileType, "Chop profile from MCCS."));
         if(symmetryType != null) cursor.add(new HeaderCard("CHPSYM", symmetryType, "Chop symmetry mode."));
         if(coordinateSystem != null) cursor.add(new HeaderCard("CHPCRSYS", coordinateSystem, "Chop coordinate system."));
         if(signalSource != null) cursor.add(new HeaderCard("CHPSRC", signalSource, "Source of chopper signal."));
         if(driveMode != null) cursor.add(new HeaderCard("CHPACDC", driveMode, "Analog or Digital drive signal."));
         if(waveFunction != null) cursor.add(new HeaderCard("CHPFUNC", waveFunction, "Chopper wave function."));
         if(!Double.isNaN(settlingTime)) cursor.add(new HeaderCard("CHPSETL", settlingTime / Unit.ms, "(ms) Chopper settling time."));
         if(!Double.isNaN(phase)) cursor.add(new HeaderCard("CHPPHASE", phase / Unit.ms, "(ms) Chop phase."));
     }

     @Override
     public String getLogID() {
         return "chop";
     }

     @Override
     public Object getTableEntry(String name) {
         if(name.equals("amp")) return amplitude / Unit.arcsec;
         else if(name.equals("angle")) return angle / Unit.deg;
         else if(name.equals("frequency")) return frequency / Unit.Hz;
         else if(name.equals("tip")) return tip / Unit.arcsec;
         else if(name.equals("tilt")) return tilt / Unit.arcsec;
         else if(name.equals("profile")) return profileType;
         else if(name.equals("src")) return signalSource;
         else if(name.equals("mode")) return driveMode;
         else if(name.equals("sys")) return coordinateSystem;
         else if(name.equals("func")) return waveFunction;

         return super.getTableEntry(name);
     }


     // Below is the nominal conversion
     //public static final double volts2Angle = 1123.0 * Unit.arcsec / (9.0 * Unit.V);

     // And here is the conversion that matches HAWC+ data...
     public static final double volts2Angle = 33.394 * Unit.arcsec / Unit.V;

 }
