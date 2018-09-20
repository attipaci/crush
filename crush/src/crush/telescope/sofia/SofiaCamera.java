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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
import crush.Channel;
import crush.Scan;
import crush.array.Camera;
import crush.instrument.ColorArrangement;
import crush.telescope.GroundBased;
import crush.telescope.Mount;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;

public abstract class SofiaCamera<ChannelType extends Channel> extends Camera<ChannelType> implements GroundBased {
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

        spectral = new SofiaSpectroscopyData(header);
        
        samplingInterval = integrationTime = 1.0 / (header.getDouble("SMPLFREQ", Double.NaN) * Unit.Hz);
        if(samplingInterval < 0.0) samplingInterval = integrationTime = Double.NaN;
    }


    public void editHeader(Header header) throws HeaderCardException {
        if(instrumentData != null) instrumentData.editHeader(header);
        if(array != null) array.editHeader(header);
        if(spectral != null) spectral.editHeader(header);

        //if(hasOption("pixeldata")) 
        //	c.add(new HeaderCard("FLATFILE", option("pixeldata").getValue(), "pixel data file."));
    }

    @Override
    public void editImageHeader(List<Scan<?,?>> scans, Header header) throws HeaderCardException {
        super.editImageHeader(scans, header);	
       
        scans = new ArrayList<Scan<?,?>>(scans);
        Collections.sort(scans);
        SofiaScan<?,?> first = (SofiaScan<?,?>) scans.get(0);
        
        boolean isChopping = false, isNodding = false, isDithering = false, isMapping = false, isScanning = false;
                     
        // Associated IDs...
        TreeSet<String> aors = new TreeSet<String>();
        TreeSet<String> missionIDs = new TreeSet<String>();
        TreeSet<Double> freqs = new TreeSet<Double>();
         
        for(int i=0; i<scans.size(); i++) {
            final SofiaScan<?,?> scan = (SofiaScan<?,?>) scans.get(i);       
            
            if(scan == null) continue;
            
            if(SofiaHeader.isValid(scan.observation.aorID)) if(!Util.equals(scan.observation.aorID, first.observation.aorID)) 
                aors.add(scan.observation.aorID);
            
            if(SofiaHeader.isValid(scan.mission.missionID)) if(!Util.equals(scan.mission.missionID, first.mission.missionID))
                missionIDs.add(scan.mission.missionID);
            
            if(first.instrument.getFrequency() != scan.instrument.getFrequency()) 
                freqs.add(scan.instrument.getFrequency());
             
            isChopping |= scan.isChopping;
            isNodding |= scan.isNodding;
            isDithering |= scan.isDithering;
            isMapping |= scan.isMapping;
            isScanning |= scan.isScanning;
        }
                 
        editHeader(SofiaObservationData.class, header, scans);
        editHeader(SofiaMissionData.class, header, scans);
        editHeader(SofiaOriginationData.class, header, scans);
        editHeader(SofiaEnvironmentData.class, header, scans);
        editHeader(SofiaAircraftData.class, header, scans);
        editHeader(SofiaTelescopeData.class, header, scans);
     
        instrumentData = (SofiaInstrumentData) getMerged(SofiaInstrumentData.class, scans);
        array = (SofiaArrayData) getMerged(SofiaArrayData.class, scans);
        spectral = (SofiaSpectroscopyData) getMerged(SofiaSpectroscopyData.class, scans);
        
        editHeader(header);

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Data Collection Keywords ------>", false));
        c.add(new HeaderCard("CHOPPING", isChopping, "Was chopper in use?"));   
        c.add(new HeaderCard("NODDING", isNodding, "Was nodding used?"));   
        c.add(new HeaderCard("DITHER", isDithering, "Was dithering used?"));    
        c.add(new HeaderCard("MAPPING", isMapping, "Was mapping?"));    
        c.add(new HeaderCard("SCANNING", isScanning, "Was scanning?"));
   
        if(isChopping) editHeader(SofiaChopperData.class, header, scans);
        if(isNodding) editHeader(SofiaNoddingData.class, header, scans);
        if(isDithering) editHeader(SofiaDitheringData.class, header, scans);
        if(isMapping) editHeader(SofiaMappingData.class, header, scans);
        if(isScanning) editHeader(SofiaScanningData.class, header, scans);

        SofiaProcessingData processing = new SofiaProcessingData.CRUSH(hasOption("calibrated"), header.getIntValue("NAXIS"), getLowestQuality(scans));
        processing.associatedAORs = aors;
        processing.associatedMissionIDs = missionIDs;
        processing.associatedFrequencies = freqs;
       
        processing.editHeader(header);
    }	
    
    public SofiaData getMerged(Class<? extends SofiaData> type, Collection<Scan<?,?>> scans) throws HeaderCardException {
        ArrayList<Scan<?,?>> ordered = new ArrayList<Scan<?,?>>(scans);
        Collections.sort(ordered);
        
        SofiaData merged = null;
        int flightNo = 0;
        
        for(int i=0; i<ordered.size(); i++) {
            SofiaScan<?,?> scan = (SofiaScan<?,?>) ordered.get(i);
            if(scan == null) continue;
            if(scan.getData(type) == null) continue;
            
            if(merged == null) {
                merged = scan.getData(type).clone();
                flightNo = scan.getFlightNumber();
            }
            else merged.merge(scan.getData(type), scan.getFlightNumber() == flightNo);
        }    
        
        return merged;
    }        

    public void editHeader(Class<? extends SofiaData> type, Header header, Collection<Scan<?,?>> scans) throws HeaderCardException {
        SofiaData merged = getMerged(type, scans);        
        if(merged != null) merged.editHeader(header);
    }
    

  
    public int getLowestQuality(List<Scan<?,?>> scans) {
        int overall = ((SofiaScan<?,?>) scans.get(0)).processing.qualityLevel;
        for(int i=scans.size(); --i > 0; ) {
            int level = ((SofiaScan<?,?>) scans.get(i)).processing.qualityLevel;
            if(level < overall) overall = level;
        }
        return overall;
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
        SofiaScan<?,?> firstScan = (SofiaScan<?,?>) scans.get(0);
        
        if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);
     
        super.validate(scans);
    }


    public abstract Vector2D getPixelSize();

    public static final double telescopeDiameter = 2.5 * Unit.m;

}
