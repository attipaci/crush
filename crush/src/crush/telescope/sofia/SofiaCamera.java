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
 ******************************************************************************/

package crush.telescope.sofia;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.FitsFactory;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;
import crush.CRUSH;
import crush.Channel;
import crush.Scan;
import crush.array.Camera;
import crush.instrument.ColorArrangement;
import crush.telescope.GroundBased;
import crush.telescope.Mount;
import jnum.Constant;
import jnum.Unit;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;

public abstract class SofiaCamera<ChannelType extends Channel> extends Camera<ChannelType> implements GroundBased {
    /**
     * 
     */
    private static final long serialVersionUID = -7272751629186147371L;

    public SofiaInstrumentData instrumentData;
    public SofiaArrayData array;

    Set<String> configFiles = new HashSet<String>();
    Vector<String> history = new Vector<String>();

    static {
        FitsFactory.setLongStringsEnabled(true);
    }


    public SofiaCamera(String name, ColorArrangement<? super ChannelType> layout) {
        super(name, layout);
        mount = Mount.NASMYTH_COROTATING;
    }


    public SofiaCamera(String name, ColorArrangement<? super ChannelType> layout, int size) {
        super(name, layout, size);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public SofiaCamera<ChannelType> clone() {
        SofiaCamera<ChannelType> clone = (SofiaCamera<ChannelType>) super.clone();
        if(history != null) clone.history = (Vector<String>) history.clone();
        return clone;
    }

    @Override
    public SofiaCamera<ChannelType> copy() {
        SofiaCamera<ChannelType> copy = (SofiaCamera<ChannelType>) super.copy();
        
        if(instrumentData != null) copy.instrumentData = instrumentData.copy();
        if(array != null) copy.array = array.copy();
        if(configFiles != null) copy.configFiles = new HashSet<String>(configFiles);
        
        return copy;
    }

    public abstract String getFileID();

    @Override
    public String getTelescopeName() {
        return "SOFIA";
    }


    @Override
    public void registerConfigFile(String fileName) {
        super.registerConfigFile(fileName);
        
        if(configFiles.contains(fileName)) return; 
        
        configFiles.add(fileName);
        history.add("AUX: " + fileName); 
    }

    @Override
    protected void loadChannelData(String fileName) throws IOException {
        super.loadChannelData(fileName);
        registerConfigFile(fileName);
    }

    @Override
    public void readRCP(String fileName)  throws IOException {
        super.readRCP(fileName);
        registerConfigFile(fileName);
    }

    public abstract void readData(BasicHDU<?>[] fits) throws Exception;

    @Override
    public String getName() {
        if(instrumentData == null) return super.getName();
        return (instrumentData.instrumentName != null) ? instrumentData.instrumentName : super.getName();
    }
    
    
    public void parseHeader(SofiaHeader header) {		
        instrumentData = new SofiaInstrumentData(header);

        // Set the default angular resolution given the telescope size...
        double D = hasOption("aperture") ? option("aperture").getDouble() * Unit.m : telescopeDiameter;
        setResolution(1.22 * instrumentData.wavelength / D);

        setFrequency(Constant.c / instrumentData.wavelength);
        
        array = new SofiaArrayData(header);

        samplingInterval = integrationTime = 1.0 / (header.getDouble("SMPLFREQ", Double.NaN) * Unit.Hz);
        if(samplingInterval < 0.0) samplingInterval = integrationTime = Double.NaN;
    }


    public void editHeader(Header header) throws HeaderCardException {
        if(instrumentData != null) instrumentData.editHeader(header);
        if(array != null) array.editHeader(header);

        //if(hasOption("pixeldata")) 
        //	c.add(new HeaderCard("FLATFILE", option("pixeldata").getValue(), "pixel data file."));

    }

    @Override
    public void editImageHeader(List<Scan<?,?>> scans, Header header) throws HeaderCardException {
        super.editImageHeader(scans, header);	

        int level = hasOption("calibrated") ? 3 : 2;
        // TODO if multiple mission IDs, then Level 4...

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        // Add SOFIA processing keys
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Data Processing Keys ------>", false));
        c.add(new HeaderCard("PROCSTAT", "LEVEL_" + level, SofiaProcessingData.getComment(level)));
        c.add(new HeaderCard("HEADSTAT", "UNKNOWN", "See original header values in the scan HDUs."));
        c.add(new HeaderCard("PIPELINE", "crush v" + CRUSH.getVersion(), "Software that produced this file."));
        c.add(new HeaderCard("PIPEVERS", "crush v" + CRUSH.getFullVersion(), "Full software version information.")); 
        c.add(new HeaderCard("PRODTYPE", "CRUSH-IMAGE", "Type of product produced by the software."));
        c.add(new HeaderCard("DATAQUAL", getQualityString(scans), "Lowest quality input scan."));

        // Add required keys and prior history
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Additional Required Primary Header Keys ------>", false));

        // TODO workaround for updates...
        // -----------------------------------------------------------------------------------------------------
        Header required = new Header();
        ((SofiaScan<?,?>) scans.get(0)).addRequiredPrimaryHeaderKeysTo(required);
        updateMultiScanKeys(scans, required);

        Cursor<String, HeaderCard> from = required.iterator();
        while(from.hasNext()) c.add(from.next());
        // -----------------------------------------------------------------------------------------------------
    }	


    public void updateMultiScanKeys(List<Scan<?,?>> scans, Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        // Add mandatory TRACERR entry...
        boolean hasTrackingError = false;
        for(Scan<?,?> scan : scans) hasTrackingError |= ((SofiaScan<?,?>) scan).telescope.hasTrackingError;      
        c.add(new HeaderCard("TRACERR", hasTrackingError, "Whether any input data had tracking errors."));

        // EXPTIME
        double expTime = getTotalExposureTime(scans);
        if(!Double.isNaN(expTime)) c.add(new HeaderCard("EXPTIME", expTime, "(s) Total effective on-source time."));

        // AOR_ID, ASSC_AOR
        addAssociatedAORIDs(scans, header);

        // TELEL, TELXEL, TELLOS to earliest input.
        //getEarliestScan(scans).telescope.updateElevationKeys(header);

        // TSC-STAT, FBS-STAT from latest input
        //getLatestScan(scans).telescope.updateStatusKeys(header);

    }

    public double getTotalExposureTime(List<Scan<?,?>> scans) {
        double expTime = 0.0;
        for(Scan<?,?> scan : scans) expTime += ((SofiaCamera<?>) scan.instrument).instrumentData.exposureTime;
        return expTime;
    }

    public boolean containsDithering(List<Scan<?,?>> scans) {
        for(Scan<?,?> scan : scans) if(((SofiaScan<?,?>) scan).isDithering) return true;
        return false;
    }

    public void addAssociatedAORIDs(List<Scan<?,?>> scans, Header header) throws HeaderCardException {
        ArrayList<String> aorIDs = getAORIDs(scans);
        if(aorIDs.size() < 2) return;

        StringBuffer buf = new StringBuffer();
        buf.append(aorIDs.get(1));

        for(int i=2; i<aorIDs.size(); i++) {
            buf.append(", ");
            buf.append(aorIDs.get(i));
        }

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("ASSC_AOR", new String(buf), "Associated AOR IDs."));
    }

    public String getQualityString(List<Scan<?,?>> scans) {
        int overall = ((SofiaScan<?,?>) scans.get(0)).processing.qualityLevel;
        for(int i=scans.size(); --i > 0; ) {
            int level = ((SofiaScan<?,?>) scans.get(i)).processing.qualityLevel;
            if(level < overall) overall = level;
        }
        return SofiaProcessingData.qualityNames[overall];
    }

    public ArrayList<String> getAORIDs(List<Scan<?,?>> scans) {
        ArrayList<String> aorIDs = new ArrayList<String>();

        for(int i=0; i<scans.size(); i++) {
            SofiaScan<?,?> scan = (SofiaScan<?,?>) scans.get(i);
            String aorID = scan.observation.aorID;
            if(!aorIDs.contains(aorID)) aorIDs.add(aorID);
        }

        return aorIDs;
    }

    public SofiaScan<?,?> getEarliestScan(List<Scan<?,?>> scans) {
        double firstMJD = scans.get(0).getMJD();
        Scan<?,?> earliestScan = scans.get(0);

        for(int i=scans.size(); --i > 1; ) if(scans.get(i).getMJD() < firstMJD) {
            earliestScan = scans.get(i);
            firstMJD = earliestScan.getMJD();
        }

        return (SofiaScan<?, ?>) earliestScan;
    }


    public SofiaScan<?,?> getLatestScan(List<Scan<?,?>> scans) {
        double lastMJD = scans.get(0).getMJD();
        Scan<?,?> latestScan = scans.get(0);

        for(int i=scans.size(); --i > 1; ) if(scans.get(i).getMJD() > lastMJD) {
            latestScan = scans.get(i);
            lastMJD = latestScan.getMJD();
        }

        return (SofiaScan<?, ?>) latestScan;
    }

    @Override
    public void addHistory(Header header, List<Scan<?,?>> scans) throws HeaderCardException {	
        super.addHistory(header, scans);			

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        // Add auxiliary file information
        try { FitsToolkit.addHistory(c, " PWD: " + new File(".").getCanonicalPath()); }
        catch(Exception e) { warning("Could not determine PWD for HISTORY entry..."); }

        for(int i=0; i<history.size(); i++) FitsToolkit.addHistory(c, " " + history.get(i));

        // Add obs-IDs for all input scans...
        if(scans != null) for(int i=0; i<scans.size(); i++)
            FitsToolkit.addHistory(c, " OBS-ID[" + (i+1) + "]: " + scans.get(i).getID());	
    }

    @Override
    public void validate(Vector<Scan<?,?>> scans) throws Exception {
        final SofiaScan<?,?> firstScan = (SofiaScan<?,?>) scans.get(0);
        
        if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);

        super.validate(scans);
    }


    public abstract Vector2D getPixelSize();

    public static final double telescopeDiameter = 2.5 * Unit.m;

}
