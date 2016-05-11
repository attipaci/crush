/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/

package crush.sofia;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.FitsFactory;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;
import crush.CRUSH;
import crush.Channel;
import crush.GroundBased;
import crush.Instrument;
import crush.Pixel;
import crush.ColorArrangement;
import crush.Scan;
import crush.array.Camera;
import jnum.Unit;

public abstract class SofiaCamera<PixelType extends Pixel, ChannelType extends Channel> extends Camera<PixelType, ChannelType> implements GroundBased {
    /**
     * 
     */
    private static final long serialVersionUID = -7272751629186147371L;

    public SofiaInstrumentData instrumentData;
    public SofiaArrayData array;

    Vector<String> history = new Vector<String>();
   
    static {
        FitsFactory.setLongStringsEnabled(true);
    }


    public SofiaCamera(String name, ColorArrangement<? super ChannelType> layout) {
        super(name, layout);
    }


    public SofiaCamera(String name, ColorArrangement<? super ChannelType> layout, int size) {
        super(name, layout, size);
    }

    @Override
    public Instrument<ChannelType> copy() {
        @SuppressWarnings("unchecked")
        SofiaCamera<PixelType, ChannelType> copy = (SofiaCamera<PixelType, ChannelType>) super.copy();
        if(instrumentData != null) copy.instrumentData = instrumentData.copy();
        if(array != null) copy.array = array.copy();
        return copy;
    }

    @Override
    public String getTelescopeName() {
        return "SOFIA";
    }


    @Override
    public void registerConfigFile(String fileName) {
        super.registerConfigFile(fileName);
        history.add("AUX: " + fileName); 
    }

    @Override
    public void loadChannelData(String fileName) throws IOException {
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
        setResolution(1.22 * instrumentData.wavelength * Unit.um / telescopeDiameter);

        array = new SofiaArrayData(header);

        samplingInterval = integrationTime = 1.0 / (header.getDouble("SMPLFREQ", Double.NaN) * Unit.Hz);
        if(samplingInterval < 0.0) samplingInterval = integrationTime = Double.NaN;
    }


    public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
        if(instrumentData != null) instrumentData.editHeader(header, cursor);
        if(array != null) array.editHeader(header, cursor);

        //if(hasOption("pixeldata")) 
        //	cursor.add(new HeaderCard("FLATFILE", option("pixeldata").getValue(), "pixel data file."));

    }

    @Override
    public void editImageHeader(List<Scan<?,?>> scans, Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
        super.editImageHeader(scans, header, cursor);	

        int level = hasOption("calibrated") ? 3 : 2;
        // TODO if multiple mission IDs, then Level 4...

        // Add SOFIA processing keys
        cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Data Processing Keys ------>", false));
        cursor.add(new HeaderCard("PROCSTAT", "LEVEL_" + level, SofiaProcessingData.getComment(level)));
        cursor.add(new HeaderCard("HEADSTAT", "UNKNOWN", "See original header values in the scan HDUs."));
        cursor.add(new HeaderCard("PIPELINE", "crush v" + CRUSH.getVersion(), "Software that produced this file."));
        cursor.add(new HeaderCard("PIPEVERS", "crush v" + CRUSH.getFullVersion(), "Full software version information.")); 
        cursor.add(new HeaderCard("PRODTYPE", "CRUSH-IMAGE", "Type of product produced by the software."));
        cursor.add(new HeaderCard("DATAQUAL", getQualityString(scans), "Lowest quality input scan."));

        // Add required keys and prior history
        cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Additional Required Primary Header Keys ------>", false));

        // TODO workaround for updates...
        // -----------------------------------------------------------------------------------------------------
        Header required = new Header();
        ((SofiaScan<?,?>) scans.get(0)).addRequiredPrimaryHeaderKeysTo(required);
        updateMultiScanKeys(scans, required);

        Cursor<String, HeaderCard> c2 = required.iterator();
        while(c2.hasNext()) cursor.add(c2.next());
        // -----------------------------------------------------------------------------------------------------	
    }	


    public void updateMultiScanKeys(List<Scan<?,?>> scans, Header header) throws HeaderCardException {
        // Add mandatory TRACERR entry...
        boolean hasTrackingError = false;
        for(Scan<?,?> scan : scans) hasTrackingError |= ((SofiaScan<?,?>) scan).telescope.hasTrackingError;
        header.addValue("TRACERR", hasTrackingError, "Whether any input data had tracking errors.");

        // EXPTIME
        header.addValue("EXPTIME", getTotalExposureTime(scans), "(s) Total effective on-source time.");

        // AOR_ID, ASSC_AOR
        addAssociatedAORIDs(scans, header);

        // TELEL, TELXEL, TELLOS to earliest input.
        //getEarliestScan(scans).telescope.updateElevationKeys(header);

        // TSC-STAT, FBS-STAT from latest input
        //getLatestScan(scans).telescope.updateStatusKeys(header);

    }

    public double getTotalExposureTime(List<Scan<?,?>> scans) {
        double expTime = 0.0;
        for(Scan<?,?> scan : scans) expTime += ((SofiaCamera<?,?>) scan.instrument).instrumentData.exposureTime;
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

        header.addValue("ASSC_AOR", new String(buf), "Associated AOR IDs.");
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
    public void addHistory(Cursor<String, HeaderCard> cursor, List<Scan<?,?>> scans) throws HeaderCardException {	
        super.addHistory(cursor, scans);			

        // Add auxiliary file information
        try { cursor.add(new HeaderCard("HISTORY", " PWD: " + new File(".").getCanonicalPath(), false)); }
        catch(Exception e) { warning("Could not determine PWD for HISTORY entry..."); }

        for(int i=0; i<history.size(); i++) cursor.add(new HeaderCard("HISTORY", " " + history.get(i), false));

        // Add obs-IDs for all input scans...
        if(scans != null) for(int i=0; i<scans.size(); i++)
            cursor.add(new HeaderCard("HISTORY", " OBS-ID[" + (i+1) + "]: " + scans.get(i).getID(), false));	
    }

    @Override
    public void validate(Vector<Scan<?,?>> scans) throws Exception {
        final SofiaScan<?,?> firstScan = (SofiaScan<?,?>) scans.get(0);
        double wavelength = firstScan.instrument.instrumentData.wavelength;
        for(int i=scans.size(); --i >= 1; ) if(((SofiaScan<?,?>) scans.get(i)).instrument.instrumentData.wavelength != wavelength) {
            warning("Scan " + scans.get(i).getID() + " in a different band. Removing from set.");
            scans.remove(i);
        }

        if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);

        super.validate(scans);
    }


    public static final double telescopeDiameter = 2.5 * Unit.m;

}
