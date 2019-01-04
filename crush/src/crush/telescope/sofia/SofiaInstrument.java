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


import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import crush.instrument.PixelLayout;
import crush.telescope.Mount;
import crush.telescope.TelescopeInstrument;
import jnum.Constant;
import jnum.Unit;
import jnum.astro.AstroTime;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;

public abstract class SofiaInstrument<ChannelType extends Channel> extends TelescopeInstrument<ChannelType> {
    /**
     * 
     */
    private static final long serialVersionUID = -7272751629186147371L;

    public SofiaInstrumentData instrumentData;
    public SofiaArrayData array;
    public SofiaSpectroscopyData spectral;

    Set<String> configFiles = new HashSet<String>();
    Vector<String> history = new Vector<String>();

    static {
        FitsFactory.setLongStringsEnabled(true);
    }

    public SofiaInstrument(String name, PixelLayout<? super ChannelType> layout) {
        super(name, layout);
        mount = Mount.NASMYTH_COROTATING;
    }


    public SofiaInstrument(String name, PixelLayout<? super ChannelType> layout, int size) {
        super(name, layout, size);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public SofiaInstrument<ChannelType> clone() {
        SofiaInstrument<ChannelType> clone = (SofiaInstrument<ChannelType>) super.clone();
        if(history != null) clone.history = (Vector<String>) history.clone();
        return clone;
    }

    @Override
    public SofiaInstrument<ChannelType> copy() {
        SofiaInstrument<ChannelType> copy = (SofiaInstrument<ChannelType>) super.copy();
        
        if(instrumentData != null) copy.instrumentData = instrumentData.copy();
        if(array != null) copy.array = array.copy();
        if(spectral != null) copy.spectral = spectral.copy();
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
        double D = hasOption("aperture") ? option("aperture").getDouble() * Unit.m : telescopeDiameter;
        setResolution(1.22 * instrumentData.wavelength / D);

        setFrequency(Constant.c / instrumentData.wavelength);
        
        array = new SofiaArrayData(header);

        spectral = new SofiaSpectroscopyData(header);
    }


    public void editHeader(Header header) throws HeaderCardException {
        if(instrumentData != null) instrumentData.editHeader(header);
        if(array != null) array.editHeader(header);
        if(spectral != null) spectral.editHeader(header);

        //if(hasOption("pixeldata")) 
        //	c.add(new HeaderCard("FLATFILE", option("pixeldata").getValue(), "pixel data file."));
    }

    @Override
    public void editImageHeader(List<Scan<?>> scans, Header header) throws HeaderCardException {
        super.editImageHeader(scans, header);	
       
        SofiaScan<?> first = (SofiaScan<?>) Scan.getEarliest(scans);
        SofiaScan<?> last = (SofiaScan<?>) Scan.getLatest(scans);
              
        // Associated IDs...
        TreeSet<String> aors = new TreeSet<String>();
        TreeSet<String> missionIDs = new TreeSet<String>();
        TreeSet<Float> freqs = new TreeSet<Float>();
         
        for(int i=0; i<scans.size(); i++) {
            final SofiaScan<?> scan = (SofiaScan<?>) scans.get(i);       
            if(SofiaHeader.isValid(scan.observation.aorID)) aors.add(scan.observation.aorID);      
            if(SofiaHeader.isValid(scan.mission.missionID)) missionIDs.add(scan.mission.missionID);
            if(!Double.isNaN(scan.getInstrument().getFrequency())) freqs.add((float) scan.getInstrument().getFrequency());
        }
        
        // SOFIA date and time keys...
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(SofiaData.makeCard("DATE-OBS", first.timeStamp, "Start of observation"));
        c.add(SofiaData.makeCard("UTCSTART", AstroTime.FITSTimeFormat.format(first.utc.start), "UTC start of first scan"));
        c.add(SofiaData.makeCard("UTCEND", AstroTime.FITSTimeFormat.format(last.utc.end), "UTC end of last scan"));
        // DATE is added automatically...  
        
        // SOFIA observation keys...
        // Make the OBS_ID processed!
        SofiaObservationData observation = (SofiaObservationData) SofiaData.getMerged(first.observation, last.observation);
        if(observation.obsID != null) if(!observation.obsID.startsWith("P_")) observation.obsID = "P_" + first.observation.obsID;
        observation.editHeader(header);
        
        // SOFIA mission keys...
        first.mission.editHeader(header);
        
        // SOFIA origination keys....
        SofiaOriginationData origin = (SofiaOriginationData) first.origin.clone();
        
        if(hasOption("organization")) origin.organization = option("organization").getValue();
        else {
            try { origin.organization = InetAddress.getLocalHost().getCanonicalHostName(); } 
            catch (UnknownHostException e) { origin.organization = null; }
        }
        
        origin.creator = "crush " + CRUSH.getVersion();
        origin.fileName = null; // FILENAME fills automatically at writing...
        origin.editHeader(header);
        
        // SOFIA environmental keys...
        SofiaData.getMerged(first.environment, last.environment).editHeader(header);
      
        // SOFIA aircraft keys...
        SofiaData.getMerged(first.aircraft, last.aircraft).editHeader(header);
        
        // SOFIA telescope keys...
        SofiaTelescopeData tel = (SofiaTelescopeData) SofiaData.getMerged(first.telescope, last.telescope);
        tel.requestedEquatorial = first.objectCoords;
        tel.hasTrackingError = hasTrackingError(scans);   
        tel.editHeader(header);
     
        // SOFIA instrument keys..
        instrumentData.exposureTime = getTotalExposureTime(scans);
        
        // SOFIA array keys...
        if(array != null) array.boresightIndex = scans.size() == 1 ? first.getInstrument().array.boresightIndex : new Vector2D(Double.NaN, Double.NaN);
                      
        editHeader(header);
        
        // SOFIA collection keys...
        first.mode.editHeader(header);
        
        // SOFIA keys specific to collection modes...
        if(first.mode.isChopping) first.chopper.editHeader(header);
        if(first.mode.isNodding) first.nodding.editHeader(header);
        if(first.mode.isDithering) {
            SofiaDitheringData dither = (SofiaDitheringData) first.dither.clone();
            if(scans.size() > 1) dither.index = SofiaData.UNKNOWN_INT_VALUE;
            dither.editHeader(header);
        }
        if(first.mode.isMapping) first.mapping.editHeader(header);
        if(first.mode.isScanning) SofiaData.getMerged(first.scanning, last.scanning).editHeader(header);
      
        // SOFIA data processing keys
        SofiaProcessingData processing = new SofiaProcessingData.CRUSH(hasOption("calibrated"), header.getIntValue("NAXIS"), getLowestQualityScan(scans));
        processing.associatedAORs = aors;
        processing.associatedMissionIDs = missionIDs;
        processing.associatedFrequencies = freqs;
        processing.editHeader(header);
        
        first.addPreservedHeaderKeysTo(header);     
    }	
    
    
    
    public LinkedHashSet<String> getPreservedHeaderKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
    
         // Add any additional keys that should be added to the FITS header can specified by the 'fits.addkeys' option
        if(hasOption("fits.addkeys")) for(String key : option("fits.addkeys").getList()) {
            key = key.toUpperCase();
            if(!keys.contains(key)) keys.add(key);          
        }
        return keys;
    }
   
    @Override
    public void addHistory(Header header, List<Scan<?>> scans) throws HeaderCardException {	
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
    public void validate(Vector<Scan<?>> scans) throws Exception {
        SofiaScan<?> firstScan = (SofiaScan<?>) scans.get(0);
        
        if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);
     
        super.validate(scans);
    }

    /**
     * 
     * @param angularSize   (radian) Projected angular size on sky. 
     * @param physicalSize  (m) Physical/geometric size on focal plane unit.
     * @return plate scaling (radians/m) for the focal plane projected through the telescope.
     */
    public static double getPlateScale(Vector2D angularSize, Vector2D physicalSize) {
        return Math.sqrt(angularSize.x() * angularSize.y() / (physicalSize.x() * physicalSize.y()));
    }

    public abstract Vector2D getSIPixelSize();
    
    @Override
    public String getMapConfigHelp() {
        return super.getMapConfigHelp() + 
                "     -calibrated    Produce Level 3 calibrated data (instead of Level 2).\n" +
                "     -organization= Specify the organization where data is being reduced.\n";
    }
    
    
    @Override
    public String getScanOptionsHelp() {
        return super.getScanOptionsHelp() + 
                "     -PWV=          Manually set the water vapor level (microns).\n" +
                "     -tau=atran     Use the standard ATRAN model for opacity correction.\n" +
                "     -tau=pwv       Use a PWV value (via PWV option) for tau correction.\n" +
                "     -tau=pwvmodel  Use an empirical PWV model to provide tau correction.\n";
    }
    
    
    @Override
    public String getDataLocationHelp() {
        return super.getDataLocationHelp() +
                "     -flight=       Flight number to use with scan numbers and ranges.\n";
    }
    
    
    
    
    
    public static SofiaScan<?> getEarliestScan(List<Scan<?>> scans) {
        double firstMJD = scans.get(0).getMJD();
        Scan<?> earliestScan = scans.get(0);

        for(int i=1; i < scans.size(); i++) if(scans.get(i).getMJD() < firstMJD) {
            earliestScan = scans.get(i);
            firstMJD = earliestScan.getMJD();
        }

        return (SofiaScan<?>) earliestScan;
    }


    public static SofiaScan<?> getLatestScan(List<Scan<?>> scans) {
        double lastMJD = scans.get(0).getMJD();
        Scan<?> latestScan = scans.get(0);

        for(int i=scans.size(); --i > 1; ) if(scans.get(i).getMJD() > lastMJD) {
            latestScan = scans.get(i);
            lastMJD = latestScan.getMJD();
        }

        return (SofiaScan<?>) latestScan;
    }
    
    
    public static int getLowestQualityScan(List<Scan<?>> scans) {
        int overall = ((SofiaScan<?>) scans.get(0)).processing.qualityLevel;
        for(int i=scans.size(); --i > 0; ) {
            int level = ((SofiaScan<?>) scans.get(i)).processing.qualityLevel;
            if(level < overall) overall = level;
        }
        return overall;
    }
 
    
    public static boolean hasTrackingError(Collection<Scan<?>> scans) {
        for(Scan<?> scan : scans) if(((SofiaScan<?>) scan).telescope.hasTrackingError) return true;
        return false;
    }

    public static double getTotalExposureTime(Collection<Scan<?>> scans) {
        double t = 0.0;
        for(Scan<?> scan : scans) t += ((SofiaInstrument<?>) scan.getInstrument()).instrumentData.exposureTime;
        return t;
    }


    
    
    public static final double telescopeDiameter = 2.5 * Unit.m;

}
