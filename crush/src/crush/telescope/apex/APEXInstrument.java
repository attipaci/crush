/* *****************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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


package crush.telescope.apex;

import crush.*;
import crush.sourcemodel.ChopNodPhotometry;
import crush.telescope.Mount;
import crush.telescope.TelescopeInstrument;
import jnum.Unit;
import jnum.astro.EquatorialCoordinates;
import nom.tam.fits.*;

import java.io.*;
import java.util.List;
import java.util.Vector;


public abstract class APEXInstrument<ChannelType extends Channel> extends TelescopeInstrument<ChannelType> {
    /**
     * 
     */
    private static final long serialVersionUID = 4654355516742128832L;

    private int maxFeeds;
    int pixelChannels = 1;

    public int[] activeBands;
    public int band;
    
    
    String sideband = null;
    double frequencyResolution; // Hz
    

    public APEXInstrument(String name, int size) {
        super(name, size);
    }

    public APEXInstrument(String name) {
        super(name);
    }

    
    @Override
    public String getTelescopeName() {
        return "APEX";
    }

    @Override
    public APEXInstrument<ChannelType> copy() {
        APEXInstrument<ChannelType> copy = (APEXInstrument<ChannelType>) super.copy();
        return copy;
    }

    
    @Override
    protected APEXLayout getLayoutInstance() { return new APEXLayout(this); }
    
    @Override
    public APEXLayout createLayout() { return (APEXLayout) super.createLayout(); }
    
    @Override
    public APEXLayout getLayout() { return (APEXLayout) super.getLayout(); }
    
    public int getReferencePixelFixedIndex() { return getLayout().getReferencePixelFixedIndex(); }
    

    public void readPar(String fileName) throws IOException, FitsException {
        try(Fits fits = new Fits(new File(fileName), fileName.endsWith(".gz"))) {

            BinaryTableHDU hdu = (BinaryTableHDU) fits.getHDU(1);
            readPar(hdu);

            fits.close();
        }
    }

    public final void readPar(Fits fits) throws IOException, FitsException {
        readPar((BinaryTableHDU) fits.getHDU(1));
        fits.close();
    }

    
    public void readPar(BinaryTableHDU hdu) throws IOException, FitsException {
        Header header = hdu.getHeader();

        maxFeeds = header.getIntValue("FEBEFEED", 1);
        
        Object[] data = hdu.getRow(0);
       
        activeBands = (int[]) data[hdu.findColumn("USEBAND")];
        
        // Read in the instrument location...
        String cabin = header.getStringValue("DEWCABIN").toLowerCase();
        if(cabin.startsWith("c")) mount = Mount.CASSEGRAIN;
        else if(cabin.startsWith("a") || cabin.equals("nasmyth_a")) mount = Mount.LEFT_NASMYTH;
        else if(cabin.startsWith("b") || cabin.equals("nasmyth_b")) mount = Mount.RIGHT_NASMYTH;
        else throw new IllegalStateException("Instrument cabin undefined.");

        info("Instrument mounted in " + mount.name + " cabin.");
        
        createLayout().parseHDU(hdu);   
    }
    
    public void parseArrayDataHeader(Header header) {
        pixelChannels = header.getIntValue("CHANNELS");
        storeChannels = maxFeeds * pixelChannels;
        band = header.getIntValue("BASEBAND", 0);
        frequencyResolution = header.getDoubleValue("FREQRES", 0.0) * Unit.Hz;
        sideband = header.getStringValue("SIDEBAND");
    }

    @Override
    public APEXScan<? extends APEXSubscan<?>> getScanInstance() {
        return new APEXScan<APEXSubscan<?>>(this);
    }
    

    @Override
    public void validate(Vector<Scan<?>> scans) throws Exception {

        final APEXScan<?> firstScan = (APEXScan<?>) scans.get(0);
        final APEXSubscan<?> firstSubscan = firstScan.get(0);
        final EquatorialCoordinates reference = firstScan.equatorial;
        final String sourceName = firstScan.getSourceName();

        final double pointingTolerance = getPointSize() / 5.0;
        final boolean isChopped = firstSubscan.getChopper() != null;


        if(isChopped) {
            info("Chopped photometry reduction mode.");
            info("Target is [" + sourceName + "] at " + reference);
            setOption("chopped");
        }
        else if(sourceName.equalsIgnoreCase("SKYDIP")) {
            info("Skydip reduction mode.");
            setOption("skydip");

            if(scans.size() > 1) {
                info("Ignoring all but first scan in list (for skydip).");
                scans.clear();
                scans.add(firstScan);
            }
        }

        if(firstScan.type.equalsIgnoreCase("POINT")) if(scans.size() == 1) firstScan.setSuggestPointing();

        if(hasOption("nochecks")) return;

        // Make sure the rest of the list conform to the first scan...
        for(int i=scans.size(); --i > 0; ) {
            APEXScan<?> scan = (APEXScan<?>) scans.get(i);
            APEXSubscan<?> subscan = scan.get(0);

            // Throw out any subsequent skydips...
            if(scan.getSourceName().equalsIgnoreCase("SKYDIP")) {
                warning("Scan " + scan.getID() + " is a skydip. Dropping from dataset.");
                scans.remove(i);
            }

            boolean subscanChopped = subscan.getChopper() != null;

            if(subscanChopped != isChopped) {	
                if(isChopped) warning("Scan " + scan.getID() + " is not a chopped scan. Dropping from dataset.");
                else warning("Scan " + scan.getID() + " is a chopped scan. Dropping from dataset.");
                scans.remove(i);
                continue;
            }

            if(isChopped) {
                if(!scan.isNonSidereal) {
                    if(scan.equatorial.distanceTo(reference) > pointingTolerance) {
                        warning("Scan " + scan.getID() + " observed at a different position. Dropping from dataset.");
                        CRUSH.suggest(this, "           (You can use 'moving' to keep and reduce anyway.)");
                        scans.remove(i);
                    }
                }
                else if(!scan.getSourceName().equalsIgnoreCase(sourceName)) {
                    warning("Scan " + scan.getID() + " is on a different object. Dropping from dataset.");
                    scans.remove(i);
                }
            }
        }


        super.validate(scans);		
    }


    @Override
    public int maxPixels() {
        return maxFeeds;
    }

    @Override
    public SourceModel getSourceModelInstance(List<Scan<?>> scans) {
        if(hasOption("chopped")) return new ChopNodPhotometry(this);
        return super.getSourceModelInstance(scans);
    }


    @Override
    public String getDataLocationHelp() {
        return super.getDataLocationHelp() +
                "                    'datapath' can be either the directory containing the\n" +
                "                    scans (FITS files or scan folders) themselves, or the\n" +
                "                    location in which project sub-directories reside.\n" +
                "     -project=      The project ID (case insensitive). E.g. 'T-79.F-0002-2007'.\n";
    }

}
