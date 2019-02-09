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

package crush.telescope.apex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;

import crush.Channel;
import crush.Instrument;
import crush.Pixel;
import crush.PixelLayout;
import jnum.Unit;
import jnum.math.Vector2D;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;

public class APEXLayout extends PixelLayout {
    /**
     * 
     */
    private static final long serialVersionUID = -8237785228686714439L;
    
    private Vector2D referencePosition;
    private int referencePixelFixedIndex;
    
    private Hashtable<Integer, String> feedTypes;
    
    protected APEXLayout(APEXInstrument<? extends Channel> instrument) {
        super(instrument);
    }

    @Override
    public APEXLayout copyFor(Instrument<? extends Channel> instrument) {
        APEXLayout copy = (APEXLayout) super.copyFor(instrument);
        copy.referencePosition = referencePosition.copy();
        return copy;
    }
    
    public int getReferencePixelFixedIndex() { return referencePixelFixedIndex; }
    
    public void parseHDU(BinaryTableHDU hdu) throws FitsException {
        Header header = hdu.getHeader();

        // Parse the feed type descriptions...
        feedTypes = new Hashtable<Integer, String>();
        StringTokenizer typeCodes = new StringTokenizer(header.getStringValue("FDTYPCOD"), ",");
        while(typeCodes.hasMoreTokens()) {
            StringTokenizer assignment = new StringTokenizer(typeCodes.nextToken(), ":");
            feedTypes.put(Integer.parseInt(assignment.nextToken()), assignment.nextToken().trim());
        }      
        
        Object[] data = hdu.getRow(0);

        int[] beSection = (int[]) data[hdu.findColumn("BESECTS")];
        int[] feedType = (int[]) data[hdu.findColumn("FEEDTYPE")];
        
        double[] xOffset = (double[]) data[hdu.findColumn("FEEDOFFX")];
        double[] yOffset = (double[]) data[hdu.findColumn("FEEDOFFY")];

        float[][] beamEff = (float[][]) data[hdu.findColumn("BEAMEFF")];    // [ch][pix]
        float[][] forwardEff = (float[][]) data[hdu.findColumn("ETAFSS")];  // [ch][pix]
        //float[][] gain = (float[][]) data[hdu.findColumn("FLATFIEL")];
        
        // TODO gain-elevation correction...
        
        int np = beSection.length;
        
        ArrayList<Pixel> pixels = getPixels();
        pixels.clear();
        pixels.ensureCapacity(np);
        
        APEXInstrument<? extends Channel> instrument = getInstrument();
 
        for(int p=0; p < np; p++) {
            final APEXPixel pixel = new APEXPixel(instrument, Integer.toString(p+1), p+1);
            pixel.fitsPosition = new Vector2D(xOffset[p] * Unit.deg, yOffset[p] * Unit.deg);
            pixel.setPosition(pixel.fitsPosition.copy());
            pixel.coupling = beamEff[0][p] / forwardEff[0][p]; // TODO channel-wise...
            pixel.type = feedType[p];
            pixel.beIndex = beSection[p] - 1;
            pixels.add(pixel);
        }

        referencePixelFixedIndex = ((int[]) data[hdu.findColumn("REFFEED")])[0];
        instrument.info(np + " pixels found. Reference pixel is " + referencePixelFixedIndex);

        APEXPixel ref = (APEXPixel) pixels.get(referencePixelFixedIndex - 1);
        referencePosition = ref.fitsPosition;

        // DEWRTMOD: system CABIN, EQUA, or HORIZ
        
        double rotation = header.getDoubleValue("DEWUSER", 0.0) - header.getDoubleValue("DEWZERO", 0.0);
        if(rotation != 0.0) {
            instrument.info("Dewar rotated at " + rotation + " deg.");
            rotation *= Unit.deg;
        }
        instrument.setRotationAngle(rotation);

        // Take instrument rotation into account
        for(Pixel pixel : pixels) pixel.getPosition().rotate(rotation);
    }
 
    

    public void assignChannels() {
        int npc = getInstrument().pixelChannels;
        
        for(Pixel p : getPixels()) {
            APEXPixel pixel = (APEXPixel) p;
            pixel.clear();
            int startChannel = pixel.beIndex * npc;
            for(int c=0; c < npc; c++) pixel.add(getInstrument().get(startChannel + c));
        }
    }
    
    @Override
    public APEXInstrument<? extends Channel> getInstrument() { return (APEXInstrument<? extends Channel>) super.getInstrument(); }

    @Override
    public Pixel getPixelInstance(int fixedIndex, String id) { return new APEXPixel(getInstrument(), id, fixedIndex); }


    @Override
    public void setDefaultPixelPositions() {
        for(Pixel p : getPixels()) p.setPosition(((APEXPixel) p).fitsPosition.copy());
    }
    
    @Override
    public void readRCP(String fileName)  throws IOException {      
        super.readRCP(fileName);
        recenter(); 
    }
    
    
    public ArrayList<APEXPixel> getNeighbours(APEXPixel pixel, double radius) {
        ArrayList<APEXPixel> neighbours = new ArrayList<APEXPixel>();
        for(Pixel p2 : getMappingPixels(0)) if(p2 != pixel) if(p2.distanceTo(pixel) <= radius) neighbours.add((APEXPixel) p2);
        return neighbours;      
    }

    public void recenter() {
        setReferencePosition(referencePosition);
    }
    
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("ref")) return referencePixelFixedIndex;
        return super.getTableEntry(name);
    }
    
}
