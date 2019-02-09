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

package crush.instrument.sharc2;

import java.util.List;

import crush.Channel;
import crush.Instrument;
import crush.instrument.GridIndexed;
import crush.instrument.SingleEndedLayout;
import jnum.Unit;
import jnum.math.Vector2D;

class Sharc2Layout extends SingleEndedLayout implements GridIndexed {
    /**
     * 
     */
    private static final long serialVersionUID = 8207415775023210749L;

    private Vector2D pixelSize = Sharc2Pixel.defaultSize;

    
    Sharc2Layout(Sharc2 instrument) {
        super(instrument);
    }

    @Override
    public Sharc2 getInstrument() { return (Sharc2) super.getInstrument(); }
    
    @Override
    public Sharc2Layout copyFor(Instrument<? extends Channel> instrument) {
        Sharc2Layout copy = (Sharc2Layout) super.copyFor(instrument);
        if(pixelSize != null) copy.pixelSize = pixelSize.copy();
        return copy;
    }
    
    @Override
    public void setDefaultPixelPositions() {
        pixelSize = hasOption("pixelsize") ? option("pixelsize").getDimension2D(Unit.arcsec) : Sharc2Pixel.defaultSize;

        for(Sharc2Pixel p : getInstrument()) p.getPixel().setPosition(getFocalPlanePosition(p.row, p.col));

        Vector2D arrayPointingCenter = getInstrument().getArrayPointingCenter();
        Vector2D center = getFocalPlanePosition(arrayPointingCenter.x() - 1.0, arrayPointingCenter.y() - 1.0);
        
        setReferencePosition(center);
    }
    
    double getAreaFactor() {
        return pixelSize.x() * pixelSize.y() / (Sharc2Pixel.defaultSize.x() * Sharc2Pixel.defaultSize.y());   
    }
    

    @Override
    public int rows() {
        return Sharc2.rows;
    }


    @Override
    public int cols() {
        return Sharc2.cols;
    }


    @Override
    public Vector2D getPixelSize() {
        return pixelSize;
    }

    @Override
    public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
        addLocalFixedIndices(this, fixedIndex, radius, toIndex);
    }

    
    Vector2D getFocalPlanePosition(double row, double col) {
        return new Vector2D(-pixelSize.x() * col, pixelSize.y() * row);
    }

}
