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

package crush.instrument.hirmes;

import java.io.IOException;
import java.util.Arrays;

import crush.Channel;
import crush.Instrument;
import crush.Pixel;
import crush.instrument.SingleEndedLayout;
import crush.telescope.sofia.SofiaData;
import crush.telescope.sofia.SofiaHeader;
import jnum.Unit;
import jnum.fits.FitsHeaderEditing;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

class HirmesLayout extends SingleEndedLayout implements FitsHeaderEditing {
    /**
     * 
     */
    private static final long serialVersionUID = -1476710269379169494L;
    
    
    double plateScale = defaultPlateScale;
    Vector2D loresPixelSize;                // (arcsec)
    Vector2D[] hiresPixelSize = new Vector2D[Hirmes.hiresCols];      // (arcsec)

    Vector2D focalPlaneReference;           // (mm) on the focal-plane coordinate system

    Vector2D[] subarrayPixelOffset;         // (lowres pixels)
    double[] subarrayOrientation;   
    Vector2D hiresFocalPlaneOffset;         // (mm) Hires-array offset, calculated from subarrayPixelOffset & loresPixelSpacing

    
    HirmesLayout(Instrument<? extends Channel> instrument) {
        super(instrument);
        // TODO Auto-generated constructor stub
    }


    @Override
    public HirmesLayout copyFor(Instrument<?> instrument) {
        HirmesLayout copy = (HirmesLayout) super.copyFor(instrument);

        if(loresPixelSize != null) copy.loresPixelSize = loresPixelSize.copy();
        if(hiresPixelSize != null) copy.hiresPixelSize = Vector2D.copyOf(hiresPixelSize);
        if(focalPlaneReference != null) copy.focalPlaneReference = focalPlaneReference.copy();
        if(hiresFocalPlaneOffset != null) copy.hiresFocalPlaneOffset = hiresFocalPlaneOffset.copy();
        if(subarrayPixelOffset != null) copy.subarrayPixelOffset = Vector2D.copyOf(subarrayPixelOffset);
        if(subarrayOrientation != null) copy.subarrayOrientation = Arrays.copyOf(subarrayOrientation, subarrayOrientation.length);

        return copy;
    }

    @Override
    public Hirmes getInstrument() { return (Hirmes) super.getInstrument(); }
    
    @Override
    public void validate() {
        // The subarrays orientations
        subarrayOrientation = new double[Hirmes.subarrays];
        subarrayOrientation[Hirmes.LORES_BLUE_SUBARRAY] = hasOption("rotation.blue") ? option("rotation.blue").getDouble() * Unit.deg : 0.0;
        subarrayOrientation[Hirmes.LORES_RED_SUBARRAY] = hasOption("rotation.red") ? option("rotation.red").getDouble() * Unit.deg : 0.0;
        subarrayOrientation[Hirmes.HIRES_SUBARRAY] = hasOption("rotation.hires") ? option("rotation.hires").getDouble() * Unit.deg : 0.0;

        // The subarray offsets (after rotation, in pixels)
        subarrayPixelOffset = new Vector2D[Hirmes.subarrays];
        subarrayPixelOffset[Hirmes.LORES_BLUE_SUBARRAY] = hasOption("offset.blue") ? option("offset.blue").getVector2D() : new Vector2D(-7.816, 0.0);
        subarrayPixelOffset[Hirmes.LORES_RED_SUBARRAY] = hasOption("offset.red") ? option("offset.red").getVector2D() : new Vector2D(-40.175, 0.0);
        subarrayPixelOffset[Hirmes.HIRES_SUBARRAY] = hasOption("offset.hires") ? option("offset.hires").getVector2D() : new Vector2D();

        hiresFocalPlaneOffset = subarrayPixelOffset[Hirmes.HIRES_SUBARRAY].copy();
        hiresFocalPlaneOffset.multiplyByComponentsOf(loresPixelSpacing);

        super.validate();
        
        final int blindFlag = hasOption("blinds") ? Channel.FLAG_BLIND : Channel.FLAG_DEAD;

        Vector2D imageAperture = hasOption("imaging.aperture") ? option("imaging.aperture").getDimension2D(Unit.arcsec) : defaultImagingAperture.copy(); 
        imageAperture.add(new Vector2D(loresPixelSize.x(), loresPixelSize.y())); // Include partially illuminated pixels.
        imageAperture.scale(0.5);

        Hirmes hirmes = getInstrument();

        for(HirmesPixel channel : hirmes) {   
            if(channel.detArray != hirmes.detArray) channel.flag(Channel.FLAG_DEAD);
            else if(channel.isDarkSQUID()) channel.flag(blindFlag);
            else if(channel.sub == Hirmes.HIRES_SUBARRAY) {
                if(channel.subcol != hirmes.hiresColUsed) channel.flag(blindFlag);
            }
            else if(hirmes.mode == Hirmes.IMAGING_MODE) {
                Pixel pixel = channel.getPixel();
                if(pixel.getPosition() == null) continue;
                if(Math.abs(pixel.getPosition().x()) > imageAperture.x()) channel.flag(blindFlag);
                if(Math.abs(pixel.getPosition().y()) > imageAperture.y()) channel.flag(blindFlag);
            }
        }

    }



    @Override
    public void setDefaultPixelPositions() { 
        Hirmes hirmes = getInstrument();
        
        // Set the pixel sizes...
        if(hasOption("pixelsize.lores")) {
            loresPixelSize = option("pixelsize.lores").getDimension2D(Unit.arcsec);
            plateScale = Math.sqrt(loresPixelSize.x() / loresPixelSpacing.x() * loresPixelSize.y() / loresPixelSpacing.y()); 
        }
        else {
            plateScale = hasOption("platescale") ? option("platescale").getDouble() * Unit.arcsec / Unit.mm : defaultPlateScale;
            loresPixelSize = loresPixelSpacing.copy();
            loresPixelSize.scale(plateScale);
        }

        for(int i=0; i<Hirmes.hiresCols; i++) {
            if(hasOption("pixelsize.hires" + (i+1))) hiresPixelSize[i] = option("pixelsize.hires" + (i+1)).getVector2D(Unit.arcsec);
            else {
                hiresPixelSize[i] = new Vector2D(hiresWidthMicrons[i], hiresHeightMicrons[i]);
                hiresPixelSize[i].scale(Unit.um * plateScale);
            }
        }

        // Update the SOFIA standard pixel size...
        if(hirmes.detArray == Hirmes.LORES_ARRAY) hirmes.array.pixelSize = Math.sqrt(loresPixelSize.x() * loresPixelSize.y());
        else hirmes.array.pixelSize = Math.sqrt(hiresPixelSize[hirmes.hiresColUsed].x() * hiresPixelSize[hirmes.hiresColUsed].y());
        
        Vector2D center = getSIBSPosition(focalPlaneReference);  
        for(HirmesPixel pixel : hirmes) pixel.calcSIBSPosition3D();

        // Set the pointing center...
        setReferencePosition(center);
    }

    

    @Override
    public void readRCP(String fileName)  throws IOException {
        super.readRCP(fileName);
        getInstrument().registerConfigFile(fileName);
    }

    /*
    private Vector2D getPixelSize(int sub, int col) {
        if(sub == Hirmes.HIRES_SUBARRAY) return hiresPixelSize[col];
        return loresPixelSize;
    }
    */
    
    
    Vector2D getFocalPlanePosition(int sub, double row, double col) {      
        Vector2D v = (sub == Hirmes.HIRES_SUBARRAY) ? new Vector2D(0.0, row-7.5) : new Vector2D(-col, row-7.5); // xp, yp
        v.rotate(subarrayOrientation[sub]);
        v.add(subarrayPixelOffset[sub]);
        v.multiplyByComponentsOf(loresPixelSpacing); // Offsets are in lores pixels...
        return v;
    }
    
    Vector2D getHiresFocalPlanePosition(int strip, int row) {
        Vector2D v = new Vector2D(0.0, (row-7.5) * hiresHeightMicrons[strip] * Unit.um);
        v.rotate(subarrayOrientation[Hirmes.HIRES_SUBARRAY]);
        v.addX(hiresColOffsetMillis[strip] * Unit.mm);
        v.add(hiresFocalPlaneOffset);  
        
        return v;
    }

    



   
    public Vector2D getImagingPosition(Vector2D focalPlanePosition) {
        Vector2D v = focalPlanePosition.copy();
        // Simple spectral imaging with linear position along y...
        if(getInstrument().mode != Hirmes.IMAGING_MODE) v.setX(0.0); 
        return v;
    }

    public Vector2D getSIBSPosition(Vector2D focalPlanePosition) {
        Vector2D v = getImagingPosition(focalPlanePosition);
        v.scale(plateScale);       
        //v.scaleX(-1.0);
        return v;
    }


    @Override
    public Object getTableEntry(String name) {
        if(name.equals("ref.x")) return focalPlaneReference.x() / Unit.mm;
        if(name.equals("ref.y")) return focalPlaneReference.y() / Unit.mm;
        return super.getTableEntry(name);     
    }
   
    
    public void parseHeader(SofiaHeader header) {
        focalPlaneReference = new Vector2D(header.getDouble("CRX") * Unit.mm, header.getDouble("CRY") * Unit.mm);
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(SofiaData.makeCard("CRX", focalPlaneReference.x() / Unit.mm, "(mm) Focal plane center x."));
        c.add(SofiaData.makeCard("CRY", focalPlaneReference.y() / Unit.mm, "(mm) Focal plane center y"));
    }



    final static Vector2D loresPixelSpacing = new Vector2D(1.180 * Unit.mm, 1.180 * Unit.mm);

    final static double defaultPlateScale = 6.203 * Unit.arcsec / Unit.mm;

    final static double hiresWidthMicrons[] = { 480, 574, 686, 821, 982, 1175, 1405, 1680 };
    final static double hiresHeightMicrons[] = { 410, 488, 582, 694, 828, 989, 1181, 1410 };
    
    final static double[] hiresColOffsetMillis = { 0.0, 0.9922, 2.1161, 3.5049, 5.1582, 7.2088, 9.5228, 12.4433 }; 
    final static Vector2D defaultImagingAperture = new Vector2D(119.0 * Unit.arcsec, 103.0 * Unit.arcsec);




}
