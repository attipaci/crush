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

package crush.instrument.hawcplus;


import java.io.IOException;
import java.util.List;
import java.util.StringTokenizer;

import crush.instrument.GridIndexed;
import crush.instrument.SingleEndedLayout;
import jnum.Unit;
import jnum.math.Vector2D;

public class HawcLayout extends SingleEndedLayout implements GridIndexed { 
    /**
     * 
     */
    private static final long serialVersionUID = 7518391378648096316L;

    private Vector2D pixelSize;
    
    private Vector2D[] subarrayOffset;
    private double[] subarrayOrientation;
    
    private double[] polZoom;
    
    public HawcLayout(Hawc instrument) {
        super(instrument);
    }
    
    @Override
    public Vector2D getPixelSize() { return pixelSize; }
    
    @Override
    public Hawc getInstrument() { return (Hawc) super.getInstrument(); }
    
    @Override
    public void validate() {
        Hawc hawc = getInstrument();
        
        // The subarrays orientations
        subarrayOrientation = new double[Hawc.subarrays];
        subarrayOrientation[Hawc.R0] = hasOption("rotation.r0") ? option("rotation.r0").getDouble() * Unit.deg : 0.0;
        subarrayOrientation[Hawc.R1] = hasOption("rotation.r1") ? option("rotation.r1").getDouble() * Unit.deg : Math.PI;
        subarrayOrientation[Hawc.T0] = hasOption("rotation.t0") ? option("rotation.t0").getDouble() * Unit.deg : 0.0;
        subarrayOrientation[Hawc.T1] = hasOption("rotation.t1") ? option("rotation.t1").getDouble() * Unit.deg : Math.PI;

        // The subarray offsets (after rotation, in pixels)
        subarrayOffset = new Vector2D[Hawc.subarrays];
        subarrayOffset[Hawc.R0] = hasOption("offset.r0") ? option("offset.r0").getVector2D() : new Vector2D();
        subarrayOffset[Hawc.R1] = hasOption("offset.r1") ? option("offset.r1").getVector2D() : new Vector2D(67.03, -39.0);
        subarrayOffset[Hawc.T0] = hasOption("offset.t0") ? option("offset.t0").getVector2D() : new Vector2D();
        subarrayOffset[Hawc.T1] = hasOption("offset.t1") ? option("offset.t1").getVector2D() : new Vector2D(67.03, -39.0);

        // The relative zoom of the polarization planes...
        polZoom = new double[Hawc.polArrays];
        polZoom[Hawc.R_ARRAY] = hasOption("zoom.r") ? option("zoom.r").getDouble() : 1.0;
        polZoom[Hawc.T_ARRAY] = hasOption("zoom.t") ? option("zoom.t").getDouble() : 1.0;
        
        // The default pixelSizes...
        pixelSize = new Vector2D(hawc.array.pixelSize, hawc.array.pixelSize);

        // Set the pixel size...
        if(hasOption("pixelsize")) {
            pixelSize = new Vector2D();
            StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
            pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
            pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
        }

        hawc.array.pixelSize = Math.sqrt(pixelSize.x() * pixelSize.y());

        super.validate();
        
        // TODO load bias gains? ...
    }
    

    @Override
    public void setDefaultPixelPositions() {  
        Hawc hawc = getInstrument();


        hawc.info("Boresight pixel from FITS is " + hawc.array.boresightIndex);

        if(hasOption("pcenter")) {
            hawc.array.boresightIndex = option("pcenter").getVector2D(); 
            hawc.info("Boresight override --> " + hawc.array.boresightIndex);
        } 
        else if(Double.isNaN(hawc.array.boresightIndex.x())) {
            hawc.array.boresightIndex = Hawc.defaultBoresightIndex;
            hawc.warning("Missing FITS boresight --> " + hawc.array.boresightIndex);
        }
        Vector2D center = getSIBSPosition(0, 39.0 - hawc.array.boresightIndex.y(), hawc.array.boresightIndex.x());

        for(HawcPixel pixel : hawc) pixel.calcSIBSPosition();

        // Set the pointing center...
        setReferencePosition(center);
    }


    @Override
    public void readRCP(String fileName)  throws IOException {
        super.readRCP(fileName);
        getInstrument().registerConfigFile(fileName);
    }

    public Vector2D getSIBSPosition(int sub, double row, double col) {
        Vector2D v = new Vector2D(col, 39.0 - row); // X, Y
        v.rotate(subarrayOrientation[sub]);
        v.add(subarrayOffset[sub]);
        // X is oriented like AZ (tXEL), whereas Y is oriented like -tEL.
        v.scaleX(pixelSize.x());       
        v.scaleY(-pixelSize.y());
        v.scale(polZoom[sub>>1]);
        // v is now in proper tXEL,tEL coordinates...
        return v;
    }



    // TODO... currently treating all subarrays as non-overlapping -- which is valid for point sources...
    @Override
    public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
        addLocalFixedIndices(this, fixedIndex, radius, toIndex);
        for(int sub=1; sub < Hawc.subarrays; sub++) {
            final int subOffset = sub * Hawc.subarrayPixels;
            for(int i = toIndex.size(); --i >= 0; ) toIndex.add(toIndex.get(i) + subOffset);
        }
    }


    @Override
    public final int rows() { return Hawc.rows; }

    @Override
    public final int cols() { return Hawc.subarrayCols; }

    
}
