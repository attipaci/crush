/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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

import java.text.DecimalFormat;
import java.util.Arrays;

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
    public double pixelSize = Double.NaN;
    public int subarrays = 0;
    public String[] subarraySize;
    public Vector2D boresightIndex = new Vector2D();	// boresight
    public Grid2D<?> grid;								// the WCS coordinate system

    public SofiaArrayData() {}

    public SofiaArrayData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    @Override
    public SofiaArrayData copy() {
        SofiaArrayData copy = (SofiaArrayData) clone();
        if(subarraySize != null) copy.subarraySize = Arrays.copyOf(subarraySize, subarraySize.length);
        if(boresightIndex != null) copy.boresightIndex = boresightIndex.copy();
        if(grid != null) copy.grid = grid.copy();	
        return copy;
    }



    public void parseHeader(SofiaHeader header) {
        detectorName = header.getString("DETECTOR");
        detectorSizeString = header.getString("DETSIZE");
        pixelSize = header.getDouble("PIXSCAL") * Unit.arcsec;
        subarrays = header.getInt("SUBARRNO", 0);

        if(subarrays > 0) {
            subarraySize = new String[subarrays];
            DecimalFormat d2 = new DecimalFormat("00");
            for(int i=0; i<subarrays; i++) subarraySize[i] = header.getString("SUBARR" + d2.format(i+1));	
        }

        boresightIndex.setX(header.getDouble("SIBS_X"));
        boresightIndex.setY(header.getDouble("SIBS_Y"));

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
        c.add(makeCard("DETECTOR", detectorName, "Detector name"));
        c.add(makeCard("DETSIZE", detectorSizeString, "Detector size"));
        c.add(makeCard("PIXSCAL", pixelSize / Unit.arcsec, "(arcsec) Pixel sizel on sky."));
        if(subarrays > 0) {
            c.add(makeCard("SUBARRNO", subarrays, "Number of subarrays."));
            DecimalFormat d2 = new DecimalFormat("00");
            for(int i=0; i<subarrays; i++) if(subarraySize[i] != null)
                c.add(makeCard("SUBARR" + d2.format(i+1), subarraySize[i], "Subarray " + (i+1) + " location and size."));
        }

        Vector2D v = boresightIndex == null ? new Vector2D(Double.NaN, Double.NaN) : boresightIndex;
        c.add(makeCard("SIBS_X", v.x(), "(pixel) boresight pixel x."));
        c.add(makeCard("SIBS_Y", v.y(), "(pixel) boresight pixel y."));
     
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
       
        return super.getTableEntry(name);
    }
    
  

}
