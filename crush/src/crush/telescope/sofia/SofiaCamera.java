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
import java.util.Collections;
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
        
        boolean isChopping = first.isChopping;
        boolean isNodding = first.isNodding;
        boolean isDithering = first.isDithering;
        boolean isMapping = first.isMapping;
        boolean isScanning = first.isScanning;
        
        SofiaAircraftData aircraft = (SofiaAircraftData) first.aircraft.clone();
        SofiaEnvironmentData environment = (SofiaEnvironmentData) first.environment.clone();
        SofiaMissionData mission = (SofiaMissionData) first.mission.clone();  
        SofiaObservationData observation = (SofiaObservationData) first.observation.clone();
        SofiaOriginationData origin = (SofiaOriginationData) first.origin.clone();
        SofiaTelescopeData telescope = (SofiaTelescopeData) first.telescope.clone();
        
        SofiaChopperData chopper = first.chopper == null ? null : (SofiaChopperData) first.chopper.clone();
        SofiaNoddingData nodding = first.nodding == null ? null : (SofiaNoddingData) first.nodding.clone();
        SofiaDitheringData dither = first.dither == null ? null : (SofiaDitheringData) first.dither.clone();
        SofiaMappingData mapping = first.mapping == null ? null : (SofiaMappingData) first.mapping.clone();
        SofiaScanningData scanning = first.scanning == null ? null : (SofiaScanningData) first.scanning.clone();
        
        HashSet<String> aors = new HashSet<String>();
        HashSet<String> missionIDs = new HashSet<String>();
        HashSet<Double> freqs = new HashSet<Double>();
         
        for(Scan<?,?> scan : scans) {
            SofiaScan<?,?> sofiaScan = (SofiaScan<?,?>) scan; 
            
            if(SofiaHeader.isValid(sofiaScan.observation.aorID)) aors.add(sofiaScan.observation.aorID);
            if(SofiaHeader.isValid(sofiaScan.mission.missionID)) missionIDs.add(sofiaScan.mission.missionID);
            freqs.add(sofiaScan.instrument.getFrequency());
            
            if(scan == first) continue;
             
            boolean isSameFlight = sofiaScan.getFlightNumber() == first.getFlightNumber();
      
            if(array != null) array.merge(sofiaScan.instrument.array, isSameFlight);
            if(instrumentData != null) instrumentData.merge(sofiaScan.instrument.instrumentData, isSameFlight);
            if(spectral != null) spectral.merge(sofiaScan.instrument.spectral, isSameFlight);
            
            isChopping |= sofiaScan.isChopping;
            isNodding |= sofiaScan.isNodding;
            isDithering |= sofiaScan.isDithering;
            isMapping |= sofiaScan.isMapping;
            isScanning |= sofiaScan.isScanning;   
            
            aircraft.merge(sofiaScan.aircraft, isSameFlight);
            environment.merge(sofiaScan.environment, isSameFlight);
            mission.merge(sofiaScan.mission, isSameFlight);
            observation.merge(sofiaScan.observation, isSameFlight);
            telescope.merge(sofiaScan.telescope, isSameFlight);
             
            if(chopper != null) chopper.merge(sofiaScan.chopper, isSameFlight);
            if(nodding != null) nodding.merge(sofiaScan.nodding, isSameFlight);
            if(dither != null) dither.merge(sofiaScan.dither, isSameFlight);
            if(mapping != null) mapping.merge(sofiaScan.mapping, isSameFlight);   
            if(scanning != null) scanning.merge(sofiaScan.scanning, isSameFlight);  
        }
        
        observation.aorID = first.observation.aorID;
        mission.missionID = first.mission.missionID;
            
        observation.editHeader(header);
        mission.editHeader(header);
        origin.editHeader(header);
        environment.editHeader(header);
        aircraft.editHeader(header);
        telescope.editHeader(header);  
     
        editHeader(header);

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Observing Mode Switches ------>", false));
        c.add(new HeaderCard("CHOPPING", isChopping, "Was chopper in use?"));   
        c.add(new HeaderCard("NODDING", isNodding, "Was nodding used?"));   
        c.add(new HeaderCard("DITHER", isDithering, "Was dithering used?"));    
        c.add(new HeaderCard("MAPPING", isMapping, "Was mapping?"));    
        c.add(new HeaderCard("SCANNING", isScanning, "Was scanning?"));
   
        
        if(chopper != null) if(isChopping) chopper.editHeader(header);
        if(nodding != null) if(isNodding) nodding.editHeader(header);
        if(dither != null) if(isDithering) dither.editHeader(header);
        if(mapping != null) if(isMapping) mapping.editHeader(header);
        if(scanning != null) if(isScanning) scanning.editHeader(header);
  
        c = FitsToolkit.endOf(header);
           
        // Add SOFIA processing keys
        String productType = "UNKNOWN";
        if(header.containsKey("NAXIS3")) productType = "CUBE";
        else if(header.containsKey("NAXIS2")) productType = "IMAGE";
        else if(header.containsKey("NAXIS1")) productType = "1D";
        
        
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Data Processing Keys ------>", false));
        int level = hasOption("calibrated") ? 3 : 2;
        // TODO if multiple mission IDs, then Level 4...
    
        c.add(new HeaderCard("PROCSTAT", "LEVEL_" + level, SofiaProcessingData.getComment(level)));
        c.add(new HeaderCard("HEADSTAT", SofiaProcessingData.MODIFIED, "See original header values in the scan HDUs."));
        c.add(new HeaderCard("PIPELINE", "crush v" + CRUSH.getVersion(), "Software that produced this file."));
        c.add(new HeaderCard("PIPEVERS", "crush v" + CRUSH.getFullVersion(), "Full software version information.")); 
        c.add(new HeaderCard("PRODTYPE", "CRUSH-" + productType, "Type of product produced by the software."));
        c.add(new HeaderCard("DATAQUAL", getQualityString(scans), "Lowest quality input scan."));
        
        // Remove first scan's info from list of associated properties...
        if(SofiaHeader.isValid(observation.aorID)) aors.remove(observation.aorID);
        if(SofiaHeader.isValid(mission.missionID)) missionIDs.remove(mission.missionID);
        freqs.remove(first.instrument.getFrequency());
        
        if(!aors.isEmpty()) FitsToolkit.addLongKey(c, "ASSC_AOR", SofiaProcessingData.toString(aors), "Associated AOR IDs.");
        if(!missionIDs.isEmpty()) FitsToolkit.addLongKey(c, "ASSC_MSN", SofiaProcessingData.toString(missionIDs), "Associated Mission IDs.");
        if(!freqs.isEmpty()) FitsToolkit.addLongKey(c, "ASSC_FRQ", SofiaProcessingData.toString(freqs), "Associated frequencies.");
               
        // Update/add EXPTIME
        double expTime = getTotalExposureTime(scans);
        if(!Double.isNaN(expTime)) header.addValue("EXPTIME", expTime, "(s) Total effective on-source time.");        
    }	
    


    public ArrayList<String> getAORIDs(List<Scan<?,?>> scans) {
        ArrayList<String> aorIDs = new ArrayList<String>();

        for(int i=0; i<scans.size(); i++) {
            SofiaScan<?,?> scan = (SofiaScan<?,?>) scans.get(i);
            String ID = scan.observation.aorID;
            if(!aorIDs.contains(ID)) aorIDs.add(ID);
        }

        return aorIDs;
    }
    
    public ArrayList<String> getMissionIDs(List<Scan<?,?>> scans) {
        ArrayList<String> missionIDs = new ArrayList<String>();

        for(int i=0; i<scans.size(); i++) {
            SofiaScan<?,?> scan = (SofiaScan<?,?>) scans.get(i);
            String ID = scan.mission.missionID;
            if(!missionIDs.contains(ID)) missionIDs.add(ID);
        }

        return missionIDs;
    }
    
    public ArrayList<Double> getFrequencies(List<Scan<?,?>> scans) {
        ArrayList<Double> freqs = new ArrayList<Double>();

        for(int i=0; i<scans.size(); i++) {
            SofiaScan<?,?> scan = (SofiaScan<?,?>) scans.get(i);
            double f = scan.instrument.getFrequency();
            if(!freqs.contains(f)) freqs.add(f);
        }

        return freqs;
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

   
    
   
    public String getQualityString(List<Scan<?,?>> scans) {
        int overall = ((SofiaScan<?,?>) scans.get(0)).processing.qualityLevel;
        for(int i=scans.size(); --i > 0; ) {
            int level = ((SofiaScan<?,?>) scans.get(i)).processing.qualityLevel;
            if(level < overall) overall = level;
        }
        return SofiaProcessingData.qualityNames[overall];
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
