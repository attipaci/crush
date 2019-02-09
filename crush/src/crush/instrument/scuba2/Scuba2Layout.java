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

package crush.instrument.scuba2;

import java.util.List;

import crush.Channel;
import crush.Instrument;
import crush.Pixel;
import crush.instrument.DistortionModel;
import crush.instrument.GridIndexed;
import crush.instrument.SingleEndedLayout;
import jnum.Unit;
import jnum.math.Vector2D;
import nom.tam.fits.Header;

class Scuba2Layout extends SingleEndedLayout implements GridIndexed {

    /**
     * 
     */
    private static final long serialVersionUID = 8629717377259576173L;
    
    double physicalPixelSize;       // e.g. mm 
    double plateScale;              // e.g. arcseconds / mm;
    
    Vector2D pointingCenter;
    Vector2D pointingCorrection;
    Vector2D userPointingOffset;

    Scuba2Layout(Instrument<? extends Channel> instrument) {
        super(instrument);
    }
    
    @Override
    public Scuba2Layout copyFor(Instrument<? extends Channel> instrument) {
        Scuba2Layout copy = (Scuba2Layout) super.copyFor(instrument);

        if(pointingCenter != null) copy.pointingCenter = pointingCenter.copy();
        if(pointingCorrection != null) copy.pointingCorrection = pointingCorrection.copy();
        if(userPointingOffset != null) copy.userPointingOffset = userPointingOffset.copy();
        
        return copy;
    }

    @Override
    public Scuba2 getInstrument() { return (Scuba2) super.getInstrument(); }
    
    public void parseHeader(Header header) {
        // INSTAP_X, Y instrument aperture offsets. Kinda like FAZO, FZAO?
        pointingCenter = new Vector2D(header.getDoubleValue("INSTAP_X", 0.0), header.getDoubleValue("INSTAP_Y", 0.0));
        pointingCenter.scale(-Unit.arcsec);
        
        // DAZ, DEL total pointing corrections
        pointingCorrection = new Vector2D(header.getDoubleValue("DAZ", 0.0), header.getDoubleValue("DEL", 0.0));
        pointingCorrection.scale(Unit.arcsec);
        
        // UAZ, UEL pointing
        userPointingOffset = new Vector2D(header.getDoubleValue("UAZ", 0.0), header.getDoubleValue("UEL", 0.0));
        userPointingOffset.scale(Unit.arcsec);
    }
    
    
    @Override
    public void setDefaultPixelPositions() {  
        physicalPixelSize = hasOption("pixelmm") ? option("pixelmm").getDouble() * Unit.mm : DEFAULT_PIXEL_SIZE;
        double plateScale = hasOption("platescale") ? option("platescale").getDouble() * Unit.arcsec / Unit.mm : DEFAULT_PLATE_SCALE;
        
        DistortionModel distortion = hasOption("distortion") ? new DistortionModel(option("distortion")) : null;
        if(distortion != null) {
            distortion.setUnit(Unit.get("mm"));
            getInstrument().info("Applying distortion model: " + distortion.getName());
        }
             
        for(Scuba2Pixel channel : getInstrument()) {
            Pixel pixel = channel.getPixel();
            Scuba2Subarray subarray = getInstrument().subarray[channel.subarrayNo];
            
            pixel.setPosition(subarray.getPhysicalPixelPosition(channel.row % Scuba2.SUBARRAY_ROWS, channel.col % Scuba2.SUBARRAY_COLS));
            
            // Apply the distortion model (if specified).
            if(distortion != null) pixel.setPosition(distortion.getValue(pixel.getPosition()));
            
            // scale to arcseconds
            pixel.getPosition().scale(plateScale);
            
            // pointing center offset...
            if(pointingCenter != null) pixel.getPosition().subtract(pointingCenter);
        }
        
        if(hasOption("flip")) for(Pixel pixel : getPixels()) pixel.getPosition().scaleX(-1.0);
        
        if(hasOption("rotate")) {
            double angle = option("rotate").getDouble() * Unit.deg;
            for(Pixel pixel : getPixels()) pixel.getPosition().rotate(angle);
        }
        
        if(hasOption("zoom")) {
            double zoom = option("zoom").getDouble();
            for(Pixel pixel : getPixels()) pixel.getPosition().scale(zoom);
        }
        
        if(hasOption("skew")) {
            double skew = option("skew").getDouble();
            for(Pixel pixel : getPixels()) { pixel.getPosition().scaleX(skew); pixel.getPosition().scaleY(1.0/skew); }
        }
            
    }
    
    
    @Override
    public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
        addLocalFixedIndices(this, fixedIndex, radius, toIndex);
    }

    @Override
    public Vector2D getPixelSize() {
        final double size = physicalPixelSize * plateScale;
        return new Vector2D(size, size);
    }

    @Override
    public int rows() {
        return Scuba2.SUBARRAYS * Scuba2.SUBARRAY_ROWS;
    }

    @Override
    public int cols() {
        return Scuba2.SUBARRAY_COLS;
    }
    
    


    public final static double DEFAULT_PIXEL_SIZE = 1.135 * Unit.mm;
    public final static double DEFAULT_PLATE_SCALE = 5.1453 * Unit.arcsec / Unit.mm;
    
    
}
