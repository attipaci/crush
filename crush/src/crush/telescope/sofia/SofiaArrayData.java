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
 ******************************************************************************/package crush.telescope.sofia;

 import java.text.DecimalFormat;

 import crush.CRUSH;
 import jnum.Copiable;
 import jnum.Unit;
import jnum.data.image.Grid2D;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;
 import nom.tam.fits.Header;
 import nom.tam.fits.HeaderCard;
 import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;


 public class SofiaArrayData extends SofiaData implements Copiable<SofiaArrayData> {
     public String detectorName, detectorSizeString;
     public double pixelScale = Double.NaN;
     public int subarrays = 0;
     public String[] subarraySize;
     public double saturationValue = Double.NaN;
     public double detectorAngle = Double.NaN;
     public int averagedFrames = -1;
     public Vector2D boresightIndex = new Vector2D();	// boresight
     public Grid2D<?> grid;									// the WCS coordinate system

     public SofiaArrayData() {}

     public SofiaArrayData(SofiaHeader header) {
         this();
         parseHeader(header);
     }

     @Override
     public SofiaArrayData copy() {
         SofiaArrayData copy = (SofiaArrayData) clone();
         if(detectorName != null) copy.detectorName = new String(detectorName);
         if(detectorSizeString != null) copy.detectorSizeString = new String(detectorSizeString);
         if(subarraySize != null) {
             copy.subarraySize = new String[subarraySize.length];
             for(int i=subarraySize.length; --i >= 0; ) copy.subarraySize[i] = new String(subarraySize[i]);
         }
         if(boresightIndex != null) copy.boresightIndex = boresightIndex.copy();
         if(grid != null) copy.grid = grid.copy();	
         return copy;
     }



     public void parseHeader(SofiaHeader header) {
         detectorName = header.getString("DETECTOR");
         detectorSizeString = header.getString("DETSIZE");
         pixelScale = header.getDouble("PIXSCAL", Double.NaN) * Unit.arcsec / Unit.mm;
         subarrays = header.getInt("SUBARRNO", 0);

         if(subarrays > 0) {
             subarraySize = new String[subarrays];
             DecimalFormat d2 = new DecimalFormat("00");
             for(int i=0; i<subarrays; i++) subarraySize[i] = header.getString("SUBARR" + d2.format(i+1));	
         }

         saturationValue = header.getDouble("SATURATE", Double.NaN);
         detectorAngle = header.getDouble("DET_ANGL", Double.NaN);
         averagedFrames = header.getInt("COADDS", -1);

         boresightIndex.setX(header.getDouble("SIBS_X", Double.NaN));
         boresightIndex.setY(header.getDouble("SIBS_Y", Double.NaN));

         if(header.containsKey("CTYPE1") && header.containsKey("CTYPE2")) {
             try { grid = Grid2D.fromHeader(header.getFitsHeader(), ""); } 
             catch (Exception e) { CRUSH.error(this, e); }
         }
         else grid = null;

     }

     @Override
     public void editHeader(Header header) throws HeaderCardException {
         Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
         
         c.add(new HeaderCard("COMMENT", "<------ SOFIA Array Data ------>", false));
         if(detectorName != null) c.add(new HeaderCard("DETECTOR", detectorName, "Detector name"));
         if(detectorSizeString != null) c.add(new HeaderCard("DETSIZE", detectorSizeString, "Detector size"));
         if(!Double.isNaN(pixelScale)) c.add(new HeaderCard("PIXSCAL", pixelScale * Unit.mm / Unit.arcsec, "(arcsec/mm) Pixel scale on sky."));
         if(subarrays > 0) {
             c.add(new HeaderCard("SUBARRNO", subarrays, "Number of subarrays."));
             DecimalFormat d2 = new DecimalFormat("00");
             for(int i=0; i<subarrays; i++) if(subarraySize[i] != null)
                 c.add(new HeaderCard("SUBARR" + d2.format(i+1), subarraySize[i], "Subarray " + (i+1) + " location and size."));
         }

         if(!Double.isNaN(saturationValue)) c.add(new HeaderCard("SATURATE", saturationValue, "Detector saturation level."));
         if(!Double.isNaN(detectorAngle)) c.add(new HeaderCard("DET_ANGL", detectorAngle, "(deg) Detector angle wrt North."));
         if(averagedFrames > 0) c.add(new HeaderCard("COADDS", averagedFrames, "Number of raw frames per sample."));

         if(!Double.isNaN(boresightIndex.x())) c.add(new HeaderCard("SIBS_X", boresightIndex.x(), "(pixel) boresight pixel x."));
         else c.add(new HeaderCard("SIBS_X", SofiaHeader.UNKNOWN_FLOAT_VALUE, "Undefined value."));

         if(!Double.isNaN(boresightIndex.y())) c.add(new HeaderCard("SIBS_Y", boresightIndex.y(), "(pixel) boresight pixel y."));
         else c.add(new HeaderCard("SIBS_Y", SofiaHeader.UNKNOWN_FLOAT_VALUE, "Undefined value."));

         if(grid != null) grid.editHeader(header); // TODO...
     }

     @Override
     public String getLogID() {
         return "array";
     }

     @Override
     public Object getTableEntry(String name) {
         if(name.equals("sibsx")) return boresightIndex.x();
         else if(name.equals("sibsy")) return boresightIndex.y();
         else if(name.equals("angle")) return detectorAngle / Unit.deg;
         else if(name.equals("ave")) return averagedFrames;

         return super.getTableEntry(name);
     }


 }
