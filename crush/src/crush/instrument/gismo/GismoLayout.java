/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.instrument.gismo;

import java.util.List;
import java.util.StringTokenizer;

import crush.Channel;
import crush.Instrument;
import crush.Pixel;
import crush.PixelLayout;
import crush.instrument.GridIndexed;
import crush.instrument.SingleEndedLayout;
import jnum.Unit;
import jnum.math.Vector2D;
import nom.tam.fits.Header;

class GismoLayout extends SingleEndedLayout implements GridIndexed {

    /**
     * 
     */
    private static final long serialVersionUID = 318768587167673507L;
    
    Vector2D arrayPointingCenter; // row,col
    private Vector2D pixelSize = GismoPixel.defaultSize;

    
    GismoLayout(Gismo instrument) {
        super(instrument);
        arrayPointingCenter = GismoLayout.defaultPointingCenter.copy();
    }
    
    @Override
    public GismoLayout copyFor(Instrument<? extends Channel> instrument) {
        GismoLayout copy = (GismoLayout) super.copyFor(instrument);
        if(pixelSize != null) copy.pixelSize = pixelSize.copy();
        if(arrayPointingCenter != null) copy.arrayPointingCenter = arrayPointingCenter.copy();
        return copy;
    }
    
    @Override
    public Gismo getInstrument() { return (Gismo) super.getInstrument(); }
    
    void parseHeader(Header header) {
        arrayPointingCenter.setX(header.getDoubleValue("PNTROW", 8.5));
        arrayPointingCenter.setY(header.getDoubleValue("PNTCOL", 4.5));
    }
        
    @Override
    public void validate() {
        // Update the pointing centers...
        if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
        
        pixelSize = GismoPixel.defaultSize;
        
        // Set the pixel size...
        if(hasOption("pixelsize")) {
            pixelSize = new Vector2D();
            StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
            pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
            pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
        }
            
        super.validate();
        
        // AK: FIXME
        // Fallout from straightening out some of the coordinate jumble, GISMO ended up upside-down... 
        // But how? And, why only GISMO?
        getInstrument().stream().map(Channel::getPixel).map(Pixel::getPosition).forEach(Vector2D::flip);
    }
    

    
    @Override
    public void setDefaultPixelPositions() {
        for(GismoPixel channel : getInstrument()) channel.getPixel().setPosition(GismoPixel.getPosition(pixelSize, channel.row, channel.col));
        
        Vector2D center = GismoPixel.getPosition(pixelSize, arrayPointingCenter.x() - 1.0, arrayPointingCenter.y() - 1.0);           
        
        setReferencePosition(center);
    }
    
    // Calculates the offset of the pointing center from the nominal center of the array
    @Override
    public Vector2D getMountOffset() {
        Vector2D offset = arrayPointingCenter.copy();
        final Vector2D pCenter = getDefaultPointingCenter();
        offset.subtract(pCenter);
        if(hasOption("rotation")) offset.rotate(option("rotation").getDouble() * Unit.deg);
        return offset;
    }
    
    @Override
    public int rows() {
        return Gismo.rows;
    }


    @Override
    public int cols() {
        return Gismo.cols;
    }
    
    @Override
    public Vector2D getPixelSize() {
        return pixelSize;
    }
    
    
    @Override
    public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
        PixelLayout.addLocalFixedIndices(this, fixedIndex, radius, toIndex);
    }

    
    Vector2D getDefaultPointingCenter() { return defaultPointingCenter; }



    static Vector2D defaultSize;
    
    static Vector2D defaultPointingCenter = new Vector2D(8.5, 4.5); // row, col

}
