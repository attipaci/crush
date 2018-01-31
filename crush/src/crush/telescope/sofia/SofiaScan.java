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

import crush.*;
import crush.telescope.GroundBased;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.fits.FitsToolkit;
import jnum.math.Offset2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;
import jnum.util.*;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

import java.io.*;
import java.util.*;


public abstract class SofiaScan<InstrumentType extends SofiaCamera<? extends Channel>, IntegrationType extends SofiaIntegration<InstrumentType, ? extends SofiaFrame>> 
extends Scan<InstrumentType, IntegrationType> implements Weather, GroundBased {
    /**
     * 
     */
    private static final long serialVersionUID = -6344037367939085571L;

    public EquatorialCoordinates objectCoords;
    
    public String fileDate, date;
    public String checksum, checksumVersion;

    public BracketedValues utc = new BracketedValues();
    public boolean isChopping = false, isNodding = false, isDithering = false, isMapping = false, isScanning = false;

    Vector<String> history = new Vector<String>();

    public SofiaObservationData observation;
    public SofiaProcessingData processing;
    public SofiaMissionData mission;
    public SofiaOriginationData origin;
    public SofiaEnvironmentData environment;
    public SofiaAircraftData aircraft;
    public SofiaTelescopeData telescope;

    public SofiaChopperData chopper;
    public SofiaNoddingData nodding;
    public SofiaDitheringData dither;
    public SofiaMappingData mapping;
    public SofiaScanningData scanning;
    
    public Fits fits;

    public SofiaScan(InstrumentType instrument) {
        super(instrument);
    }
    
    public boolean useChopper() {
        return isChopping || hasOption("chopped");
    }

    @Override
    public void read(String scanDescriptor, boolean readFully) throws Exception {
        fits = getFits(scanDescriptor);
        if(fits == null) throw new IllegalStateException("Invalid or unreadable FITS (null).");
        read(fits.read(), readFully);
        closeFits();
    }

    public void closeFits() {
        if(fits == null) return;
        try { fits.close(); }
        catch(IOException e) {}
        fits = null;
        System.gc();
    }

    public File getFile(String scanDescriptor) throws FileNotFoundException { 
        try { return getFileByName(scanDescriptor); }
        catch(FileNotFoundException e) {}
        
        // Try locate the files by flight number and scan number
        
        try {
            if(scanDescriptor.contains(".") || scanDescriptor.contains(":")) {
                SmartTokenizer tokens = new SmartTokenizer(scanDescriptor, ".:");
                return getFileByIDs(tokens.nextInt(), tokens.nextInt());
            }
            else if(hasOption("flight")) {
                return getFileByIDs(option("flight").getInt(), Integer.parseInt(scanDescriptor)); 
            }
        }
        catch(Exception e) {}

        throw notFound(scanDescriptor);
    }   

    /** 
     * E.g. F0004_HC_IMA_0_HAWC_HWPC_RAW_109.fits or  2016-10-04_HA_F334_0105_CAL_0_HAWE_HWPE_RAW.fits
     * 
     * @param flightNo
     * @param scanNo
     * @return
     * @throws FileNotFoundException
     */
    public File getFileByIDs(int flightNo, int scanNo) throws FileNotFoundException {
            
        // Check for any FITS files in the path that match...
        String path = getDataPath();
        String[] fileName = new File(path).list();
        
        if(fileName == null) throw new FileNotFoundException("Incorrect 'datapath'.");
           
        for(int i=0; i<fileName.length; i++) if(isFileNameMatching(fileName[i], flightNo, scanNo)) 
            return new File(path + fileName[i]);
            
        throw new FileNotFoundException("flight " + flightNo + ", scan " + scanNo);
    }
    
    protected boolean isFileNameMatching(String fileName, int flightNo, int scanNo) {
        String upperCaseName = fileName.toUpperCase();
    
        // check consistency with standard format...
        // E.g. 2016-10-04_HA_F334_105_CAL_0_HAWE_HWPE_RAW.fits
        
        // 1. check that it's a FITS file (has a '.fits' type extension)
        if(!upperCaseName.contains(".FITS")) return false;
        
        // 2. Check if the file contains the instrument ID...
        if(!upperCaseName.contains("_" + instrument.getFileID().toUpperCase())) return false;    
        
        // 3. Check if the file name contains the flight ID...                 
        String flightID = "_F" + Util.d3.format(flightNo) + "_";
        if(!upperCaseName.contains(flightID)) return false;
       
        // 4. Check if the file name contains the scan ID in NNN format...
        String scanID = "_" + Util.d3.format(scanNo) + "_";        
        if(!upperCaseName.contains(scanID)) return false;
   
        return true;
    }
        
    protected FileNotFoundException notFound(String scanDescriptor) {
        return new FileNotFoundException("Cannot find file for: '" + scanDescriptor + "'");
    }
    
    public boolean isAORValid() {
        return SofiaHeader.isValid(observation.aorID);
    }
    
    public boolean isValid(SphericalCoordinates coords) {
        if(coords == null) return false;
        return SofiaHeader.isValid(coords.x()) && SofiaHeader.isValid(coords.y());
    }
    
    public boolean isRequestedValid(SofiaHeader header) {
        double obsRA = header.getDouble("OBSRA");
        double obsDEC = header.getDouble("OBSDEC");
        
        if(!SofiaHeader.isValid(obsRA)) return false;
        if(!SofiaHeader.isValid(obsDEC)) return false;
        if(obsRA == 0.0 && obsDEC == 0.0) return false;
        
        return true;
    }
    
  
    protected EquatorialCoordinates guessReferenceCoordinates(SofiaHeader header) {
        if(isValid(objectCoords)) {
            info("Referencing scan to object coordinates OBJRA/OBJDEC.");
            return (EquatorialCoordinates) objectCoords.copy();
        }
        else if(isRequestedValid(header)) {
            info("Referencing scan to requested coordinates.");
            return telescope.requestedEquatorial;
        }
        else if(isValid(telescope.boresightEquatorial)) {
            warning("Referencing scan to initial telescope boresight TELRA/TELDEC.");
            return (EquatorialCoordinates) telescope.boresightEquatorial.copy();
        }
   
        warning("Referencing scan to initial scan position.");
        return null;  
    }
  
    
    public void parseHeader(SofiaHeader header) throws Exception {
        // Load any options based on the FITS header...
        instrument.setFitsHeaderOptions(header.getFitsHeader());

        fileDate = header.getString("DATE");
        date = header.getString("DATE-OBS");
        
        String startTime = header.getString("UTCSTART", null);
        String endTime = header.getString("UTCEND", null);

        if(startTime != null) utc.start = Util.parseTime(startTime);
        if(endTime != null) utc.end = Util.parseTime(endTime);

        if(date.contains("T")) {
            timeStamp = date;
            date = timeStamp.substring(0, timeStamp.indexOf('T'));
            startTime = timeStamp.substring(timeStamp.indexOf('T') + 1);
        }
        else if(startTime == null) timeStamp = date + "T" + startTime;
        else timeStamp = date;
  
        AstroTime time = new AstroTime();
        time.parseFitsTimeStamp(timeStamp);
        setMJD(time.getMJD());	
        calcPrecessions(CoordinateEpoch.J2000);

        checksum = header.getString("DATASUM");				// not in 3.0
        checksumVersion = header.getString("CHECKVER");		// not in 3.0

        isChopping = header.getBoolean("CHOPPING", false);
        isNodding = header.getBoolean("NODDING", false);
        isDithering = header.getBoolean("DITHER", false);
        isMapping = header.getBoolean("MAPPING", false);
        isScanning = header.getBoolean("SCANNING", false);

        observation = new SofiaObservationData(header);
        setSourceName(observation.sourceName);
        project = observation.aorID;
        //descriptor = observation.obsID;

        processing = new SofiaProcessingData(header);
        mission = new SofiaMissionData(header);

        origin = new SofiaOriginationData(header);
        creator = origin.creator;
        observer = origin.observer;

        environment = new SofiaEnvironmentData(header);	
        aircraft = new SofiaAircraftData(header);
        telescope = new SofiaTelescopeData(header); 
        isTracking = telescope.isTracking();

        info("[" + getSourceName() + "] of AOR " + observation.aorID);
        info("Observed on " + date + " at " + startTime + " by " + observer);
        
        if(isRequestedValid(header)) {
            equatorial = (EquatorialCoordinates) telescope.requestedEquatorial.copy();  
            calcPrecessions(telescope.epoch);
        }
        else {
            warning("No valid OBSRA/OBSDEC in header.");
            telescope.requestedEquatorial = null;
            equatorial = guessReferenceCoordinates(header);
        }
        
        if(equatorial != null) info("Equatorial: " + equatorial);
        if(telescope.boresightEquatorial != null) info("Boresight: " + telescope.boresightEquatorial);  
        if(telescope.requestedEquatorial != null) info("Requested: " + telescope.requestedEquatorial);

        info("Altitude: " + Util.f2.format(aircraft.altitude.midPoint() / (1000.0 * Unit.ft)) + " kft, "
           + "Tamb: " + Util.f1.format((environment.ambientT + Constant.zeroCelsius) / Unit.K) + " K"
        );
        
        info("Focus: " + telescope.focusT.toString(Util.f1, Unit.get("um")));

        instrument.parseHeader(header);

        if(isChopping) chopper = new SofiaChopperData(header);
        if(isNodding) nodding = new SofiaNoddingData(header);
        if(isDithering) dither = new SofiaDitheringData(header);
        if(isMapping) mapping = new SofiaMappingData(header);
        if(isScanning) scanning = new SofiaScanningData(header);	

        parseHistory(header.getFitsHeader());
    }

 
    @Override
    public void editScanHeader(Header header) throws HeaderCardException {
        super.editScanHeader(header);		
 
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        // Add the system descriptors...   
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
        c.add(new HeaderCard("COMMENT", " Section for preserved SOFIA header data", false));
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
          
        if(fileDate != null) c.add(new HeaderCard("DATE", fileDate, "Scan file creation date."));

        if(checksum != null) c.add(new HeaderCard("DATASUM", checksum, "Data file checksum."));
        if(checksumVersion != null) c.add(new HeaderCard("CHECKVER", checksumVersion, "Checksum method version."));

        observation.editHeader(header);
        processing.editHeader(header);
        mission.editHeader(header);
        origin.editHeader(header);
        environment.editHeader(header);
        aircraft.editHeader(header);
        telescope.editHeader(header);
        
        
        c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("CHOPPING", isChopping, "Was chopper in use?"));	
        c.add(new HeaderCard("NODDING", isNodding, "Was nodding used?"));	
        c.add(new HeaderCard("DITHER", isDithering, "Was dithering used?"));	
        c.add(new HeaderCard("MAPPING", isMapping, "Was mapping?"));	
        c.add(new HeaderCard("SCANNING", isScanning, "Was scanning?"));

        if(chopper != null) chopper.editHeader(header);
        if(nodding != null) nodding.editHeader(header);
        if(dither != null) dither.editHeader(header);
        if(mapping != null) mapping.editHeader(header);
        if(scanning != null) scanning.editHeader(header);

        instrument.editHeader(header);

        c = FitsToolkit.endOf(header);
        //c.add(new HeaderCard("PROCSTAT", "LEVEL_" + level, SofiaProcessingData.getComment(level)));
        //c.add(new HeaderCard("HEADSTAT", "UNKNOWN", "See original header values in the scan HDUs."));
        //c.add(new HeaderCard("PIPELINE", "crush v" + CRUSH.getReleaseVersion(), "Software that produced this file."));
        //c.add(new HeaderCard("PIPEVERS", CRUSH.getFullVersion(), "Full software version information.")); 
        //c.add(new HeaderCard("PRODTYPE", "CRUSH-SCAN-META", "Type of product produced by the software."));
       
        addHistory(c);
        instrument.addHistory(header, null);
    }


    public ArrayList<String> getRequiredPrimaryHeaderKeys() {
        ArrayList<String> keys = new ArrayList<String>(requiredKeys.length);
        for(String key : requiredKeys) keys.add(key.toUpperCase());

        // Add any additional keys specified by the 'fits.addkeys
        if(hasOption("fits.addkeys")) for(String key : option("fits.addkeys").getList()) {
            key = key.toUpperCase();
            if(!keys.contains(key)) keys.add(key);			
        }
        return keys;
    }

    public void addRequiredPrimaryHeaderKeysTo(Header header) throws HeaderCardException {
        Header h = new Header();
        editScanHeader(h);

        for(String key : getRequiredPrimaryHeaderKeys()) {
            HeaderCard card = h.findCard(key);
            if(card == null) continue;
            header.addLine(card);
        }

        // Copy the subarray specs (if defined)
        if(instrument.array.subarrays > 0) for(int i=0; i<instrument.array.subarrays; i++) {
            String key = "SUBARR" + Util.d2.format(i);
            HeaderCard card = h.findCard(key);
            if(card == null) continue;	
            header.addLine(card);
        }

        Cursor<String, HeaderCard> cursor = header.iterator();

        // Add the observing mode keywords at the end...
        while(cursor.hasNext()) cursor.next();

        // Add the observing mode keywords at the end...
        //if(chopper != null) chopper.editHeader(header, cursor);
        //if(nodding != null) nodding.editHeader(header, cursor);
        //if(dither != null) dither.editHeader(header, cursor);
        //if(mapping != null) mapping.editHeader(header, cursor);
        //if(scanning != null) scanning.editHeader(header, cursor);
    }

    public void addHistory(Cursor<String, HeaderCard> c) throws HeaderCardException {
        for(int i=0; i<history.size(); i++) FitsToolkit.addHistory(c, history.get(i));
    }

    public void parseHistory(Header header) {
        history.clear();

        Cursor<String, HeaderCard> cursor = header.iterator();

        while(cursor.hasNext()) {
            HeaderCard card = cursor.next();
            if(card.getKey().equalsIgnoreCase("HISTORY")) {
                String comment = card.getComment();
                if(comment != null) history.add(comment);
            }
        }

        if(!history.isEmpty()) {
            info("Processing History: " + history.size() + " entries found.");
            CRUSH.detail(this, "Last: " + history.get(history.size() - 1));
        }

        //for(int i=0; i<history.size(); i++) debug("  " + history.get(i));
    }



    @Override
    public void validate() {
        if(!hasOption("lab")) validateAstrometry();
        super.validate();
    }
    
    protected void validateAstrometry() {
        SofiaFrame first = getFirstIntegration().getFirstFrame();
        SofiaFrame last = getLastIntegration().getLastFrame();

        if(isNonSidereal) {
            objectCoords = new EquatorialCoordinates(
                    0.5 * (first.objectEq.RA() + last.objectEq.RA()),
                    0.5 * (first.objectEq.DEC() + last.objectEq.DEC()),
                    first.objectEq.epoch
            );  
            equatorial = (EquatorialCoordinates) objectCoords.copy();
        }
        
        horizontal = new HorizontalCoordinates(
                0.5 * (first.horizontal.x() + last.horizontal.x()),
                0.5 * (first.horizontal.y() + last.horizontal.y())
                );
            
        site = new GeodeticCoordinates(
                0.5 * (first.site.x() + last.site.x()), 
                0.5 * (first.site.y() + last.site.y())
                );
        info("Location: " + site.toString(2));
        
        info("Mean telescope VPA is " + Util.f1.format(getTelescopeVPA() / Unit.deg) + " deg.");
    }


    public double getTelescopeVPA() {
        return 0.5 * (getFirstIntegration().getFirstFrame().telescopeVPA + getLastIntegration().getLastFrame().telescopeVPA);	    
    }

    public double getInstrumentVPA() {
        return 0.5 * (getFirstIntegration().getFirstFrame().instrumentVPA + getLastIntegration().getLastFrame().instrumentVPA);       
    }

    
    public File getFileByName(String scanDescriptor) throws FileNotFoundException {
        File scanFile;

        String path = getDataPath();

        scanFile = new File(scanDescriptor) ;	
        if(!scanFile.exists()) {
            scanFile = new File(path + scanDescriptor);
            if(!scanFile.exists()) throw new FileNotFoundException("Could not find scan " + scanDescriptor);
        } 	

        return scanFile;
    }	


    public Fits getFits(String scanDescriptor) throws FileNotFoundException, FitsException {
        File file = getFile(scanDescriptor);
        boolean isCompressed = file.getName().endsWith(".gz");
        info("Reading " + file.getPath() + "...");
        return new Fits(getFile(scanDescriptor), isCompressed);
    }


    protected void read(BasicHDU<?>[] hdu, boolean readFully) throws Exception {	
        parseHeader(new SofiaHeader(hdu[0].getHeader()));

        instrument.readData(hdu);
        instrument.validate(this);	
        
        clear();

        addIntegrationsFrom(hdu);

        instrument.samplingInterval = get(0).instrument.samplingInterval;
        instrument.integrationTime = get(0).instrument.integrationTime;
    }

    public abstract void addIntegrationsFrom(BasicHDU<?>[] hdu) throws Exception;


    @Override
    public double getAmbientHumidity() {
        return Double.NaN;
    }

    @Override
    public double getAmbientPressure() {
        return Double.NaN;
    }

    @Override
    public double getAmbientKelvins() {
        return environment.ambientT + Constant.zeroCelsius;
    }

    @Override
    public double getWindDirection() {
        return aircraft.groundSpeed > aircraft.airSpeed ? -Math.PI : 0.0;	// tail vs head wind...
    }

    @Override
    public double getWindPeak() {
        return Double.NaN;
    }

    @Override
    public double getWindSpeed() {
        return Math.abs(aircraft.groundSpeed - aircraft.airSpeed);
    }

    @Override
    public String getID() {
        return observation.obsID.startsWith("UNKNOWN") ? date + "." + observation.obsID.substring(7) : observation.obsID;
    }

    @Override
    public DataTable getPointingData() {
        DataTable data = super.getPointingData();

        Offset2D relative = getNativePointingIncrement(pointing);
        Vector2D siOffset = getSIPixelOffset(relative);
        //Unit sizeUnit = instrument.getSizeUnit();

        data.new Entry("dSIBSX", siOffset.x(), Unit.unity);
        data.new Entry("dSIBSY", siOffset.y(), Unit.unity);
        
        return data;
    }


    public int getFlightNumber() {
        String missionID = mission.missionID;
        int i = missionID.lastIndexOf("_F");
        if(i < 0) return -1;
        try { return Integer.parseInt(missionID.substring(i+2)); }
        catch(NumberFormatException e) { return -1; }
    }
    
    public int getScanNumber() {
        String obsID = observation.obsID;
        int i = obsID.lastIndexOf('-');
        if(i < 0) return -1;
        try { return Integer.parseInt(obsID.substring(i+1)); }
        catch(NumberFormatException e) { return -1; }
    }
    
    @Override
    public Object getTableEntry(String name) {     
        if(name.equals("obstype")) return observation.obsType;
        if(name.equals("flight")) return getFlightNumber();
        if(name.equals("scanno")) return getScanNumber();
        if(name.equals("date")) return date;
        
        // TODO Add Sofia Header data...  
        SofiaData[] groups = new SofiaData[] { 
                aircraft, chopper, dither, environment, instrument.instrumentData, instrument.array, mapping, 
                mission, nodding, observation, origin, processing, scanning, telescope 
        };
        
        for(SofiaData group : groups) if(group != null) if(name.startsWith(group.getLogPrefix())) 
            return group.getTableEntry(name.substring(group.getLogPrefix().length()));
            
        return super.getTableEntry(name);
    }
    
    
    public Vector2D getSIPixelOffset(Offset2D nativePointing) {
        Vector2D siOffset = new Vector2D(nativePointing); 
        siOffset.rotate(getTelescopeVPA() - getInstrumentVPA());
        
        // Correct for the residual instrument rotation...
        siOffset.rotate(-instrument.getRotationAngle());
        
        // convert offset to pixels
        // The SI y axis is upside down relative to the elevation axis
        Vector2D pixelSize = instrument.getPixelSize();  
        siOffset.scaleX(1.0 / pixelSize.x());
        siOffset.scaleY(-1.0 / pixelSize.y());
       
        return siOffset;
    }

    @Override
    public String getPointingString(Offset2D nativePointing) {  
        Vector2D siOffset = getSIPixelOffset(nativePointing);
        
        return super.getPointingString(nativePointing) + "\n\n" +
            "  SIBS offset --> " + Util.f2.format((siOffset.x())) + ", " + Util.f2.format(siOffset.y()) + " pixels";        
    }
    
    @Override
    public void editPointingHeaderInfo(Header header) throws HeaderCardException {
        super.editPointingHeaderInfo(header);    
        
        if(pointing == null) return; 

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        Vector2D siOffset = getSIPixelOffset(getNativePointingIncrement(pointing));
         
        c.add(new HeaderCard("SIBS_DX", siOffset.x(), "(pixels) SIBS pointing increment in X."));
        c.add(new HeaderCard("SIBS_DY", siOffset.y(), "(pixels) SIBS pointing increment in Y."));
  
    }
    

    public final static String[] requiredKeys = { 
            "DATASRC", "KWDICT", 
            "DATAQUAL", 
            "AOR_ID", "PLANID", "DEPLOY", "MISSN-ID", "FLIGHTLG", 
            "ORIGIN", "OBSERVER", "OPERATOR", "FILENAME", 
            "TELESCOP", "TELCONF",
            "CHOPPING", "NODDING", "DITHER", "MAPPING", "SCANNING",
            "DATATYPE", "INSTCFG", "INSTMODE", "MCCSMODE", "WAVECENT", "RESOLUN", 
            "DETECTOR", "DETSIZE", "PIXSCAL", "SUBARRNO"
    };

    //		These are added by CRUSH (not copied!)
    //		"PROCSTAT", "HEADSTAT", "N_SPEC", "PIPELINE", "PIPEVERS", "PRODTYPE", "FILEREV", 		
    //		"CREATOR"

    //      This is an old list of what was understood to be required primary header keys...
    //		"OBS_ID", "IMAGEID", "AOT_ID", "AOR_ID", "PLANID", "MISSN-ID", "DATE-OBS", 
    //		"TRACERR", "TRACMODE", "CHOPPING", "NODDING", "DITHER", "MAPPING", "SCANNING",
    //		"EXPTIME", "SPECTEL1", "SPECTEL2", "SLIT", "WAVECENT", "RESOLUN",
    //		"DETECTOR", "DETSIZE", "PIXSCAL", "SUBARRNO", "SIBS_X", "SIBS_Y"

    // N_SPEC, FILEREV (in changed post processing)

    // INSTRUME excluded from this list since it is added automatically...
    

}
