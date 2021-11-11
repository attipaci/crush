/* *****************************************************************************
 * Copyright (c) 2021 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope.sofia;

import crush.*;
import crush.telescope.GroundBasedScan;
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


public abstract class SofiaScan<IntegrationType extends SofiaIntegration<? extends SofiaFrame>> 
extends GroundBasedScan<IntegrationType> implements Weather {
    /**
     * 
     */
    private static final long serialVersionUID = -6344037367939085571L;

    public EquatorialCoordinates objectCoords;

    public String fileDate, date;
    public String checksum, checksumVersion;

    public BracketedValues utc = new BracketedValues();
    
    public GyroDrifts gyroDrifts;

    Vector<String> history = new Vector<>();

    public SofiaObservationData observation;
    public SofiaCollectionData mode;
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
    

    
    LinkedHashMap<String, HeaderCard> preservedKeys = new LinkedHashMap<>();

    public SofiaScan(SofiaInstrument<?> instrument) {
        super(instrument);
    }
    
    @Override
    public SofiaInstrument<?> getInstrument() { return (SofiaInstrument<?>) super.getInstrument(); }

    public boolean useChopper() {
        return mode.isChopping || hasOption("chopped");
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
        if(!upperCaseName.contains("_" + getInstrument().getFileID().toUpperCase())) return false;    

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
            return objectCoords.copy();
        }
        else if(isRequestedValid(header)) {
            info("Referencing scan to requested coordinates.");
            return telescope.requestedEquatorial;
        }
        else if(isValid(telescope.boresightEquatorial)) {
            warning("Referencing scan to initial telescope boresight TELRA/TELDEC.");
            return telescope.boresightEquatorial.copy();
        }

        warning("Referencing scan to initial scan position.");
        return null;  
    }

    
    protected SofiaScanningData getScanningDataInstance(SofiaHeader header) {
        return new SofiaScanningData(header);
    }

    public void parseHeader(SofiaHeader header) throws Exception {
        // Load any options based on the FITS header...
        getInstrument().setFitsHeaderOptions(header.getFitsHeader());

        fileDate = header.getString("DATE");
        date = header.getString("DATE-OBS");
        if(date == null) date = defaultFITSDate;

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
        time.parseFITSTimeStamp(timeStamp);
        setMJD(time.MJD());	
        
        checksum = header.getString("DATASUM");             // not in 3.0
        checksumVersion = header.getString("CHECKVER");     // not in 3.0
        
        calcEquatorialTransforms(EquatorialSystem.FK5.J2000);

        observation = new SofiaObservationData(header);
        setSourceName(observation.sourceName);
        project = observation.aorID;
        //descriptor = observation.obsID;

        
        mode = new SofiaCollectionData(header);
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
            equatorial = telescope.requestedEquatorial.copy();  
            calcEquatorialTransforms(equatorial.getSystem());
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

        getInstrument().parseHeader(header);

        if(mode.isChopping) chopper = new SofiaChopperData(header);
        if(mode.isNodding) nodding = new SofiaNoddingData(header);
        if(mode.isDithering) dither = new SofiaDitheringData(header);
        if(mode.isMapping) mapping = new SofiaMappingData(header);
        if(mode.isScanning) scanning = getScanningDataInstance(header);	

        for(String key : getInstrument().getPreservedHeaderKeys()) {
            HeaderCard card = header.getFitsHeader().findCard(key);
            if(card != null) preservedKeys.put(key, card);
        }
        
        parseHistory(header.getFitsHeader());
    }


    @Override
    public void editScanHeader(Header header) throws HeaderCardException {
        super.editScanHeader(header);		

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);

        // Add the system descriptors...   
        c.add(HeaderCard.createCommentCard(" ----------------------------------------------------"));
        c.add(HeaderCard.createCommentCard(" Section for preserved SOFIA header data"));
        c.add(HeaderCard.createCommentCard(" ----------------------------------------------------"));

        if(fileDate != null) c.add(new HeaderCard("DATE", fileDate, "Scan file creation date."));

        if(checksum != null) c.add(new HeaderCard("DATASUM", checksum, "Data file checksum."));
        if(checksumVersion != null) c.add(new HeaderCard("CHECKVER", checksumVersion, "Checksum method version."));

        observation.editHeader(header);
        mission.editHeader(header);
        origin.editHeader(header);
        environment.editHeader(header);
        aircraft.editHeader(header);
        telescope.editHeader(header);

        getInstrument().editHeader(header);

        mode.editHeader(header);

        if(chopper != null) chopper.editHeader(header);
        if(nodding != null) nodding.editHeader(header);
        if(dither != null) dither.editHeader(header);
        if(mapping != null) mapping.editHeader(header);
        if(scanning != null) scanning.editHeader(header);

        processing.editHeader(header);

        c = FitsToolkit.endOf(header);

        // Add the system descriptors...   
        c.add(HeaderCard.createCommentCard(" ----------------------------------------------------"));
        c.add(HeaderCard.createCommentCard(" Section for scan-specific processing history"));
        c.add(HeaderCard.createCommentCard(" ----------------------------------------------------"));

        addHistory(c);
        getInstrument().addHistory(header, null);
    }


    public void addPreservedHeaderKeysTo(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(HeaderCard.createCommentCard("<------ SOFIA Additional SI keys from first scan ------>"));
        
        for(String key : preservedKeys.keySet()) if(!header.containsKey(key)) c.add(preservedKeys.get(key));
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
                    first.objectEq.getSystem()
                    );  
            equatorial = objectCoords.copy();
        }

        horizontal = new HorizontalCoordinates(
                0.5 * (first.horizontal.AZ() + last.horizontal.AZ()),
                0.5 * (first.horizontal.EL() + last.horizontal.EL())
                );

        site = new GeodeticCoordinates(
                0.5 * (first.site.longitude() + last.site.longitude()), 
                0.5 * (first.site.latitude() + last.site.latitude()),
                0.0                                                     // TODO Actual flight altitude
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

        SofiaInstrument<? extends Channel> instrument = getInstrument();
        
        instrument.readData(hdu);
        instrument.configure();	
        instrument.validate();

        clear();

        addIntegrationsFrom(hdu);

        SofiaInstrument<? extends Channel> firstInstrument = get(0).getInstrument();
        
        instrument.samplingInterval = firstInstrument.samplingInterval;
        instrument.integrationTime = firstInstrument.integrationTime;
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
                aircraft, chopper, dither, environment, getInstrument().instrumentData, getInstrument().array, mapping, 
                mission, nodding, observation, origin, processing, scanning, telescope 
        };

        for(SofiaData group : groups) if(group != null) if(name.startsWith(group.getLogPrefix())) 
            return group.getTableEntry(name.substring(group.getLogPrefix().length()));

        return super.getTableEntry(name);
    }

    public Vector2D getNominalPointingOffset(Offset2D nativePointing) {
        Vector2D offset = new Vector2D(nativePointing); 

        // Add the pointing offset used in the reduction back in...
        if(hasOption("pointing")) offset.add(option("pointing").getVector2D(Unit.arcsec));

        return offset;
    }


    public Vector2D getSIPixelOffset(Offset2D nativePointing) { 
        Vector2D siOffset = getNominalPointingOffset(nativePointing);

        siOffset.rotate(getTelescopeVPA() - getInstrumentVPA());

        // Correct for the residual instrument rotation...
        siOffset.rotate(-getInstrument().getRotationAngle());

        // convert offset to pixels
        // The SI y axis is upside down relative to the elevation axis
        Vector2D pixelSize = getInstrument().getSIPixelSize();  
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
    
    /**
     * Checks if this scan should be discarded from the reduction, and returns an appropriate description of the reason
     * why it is not suitable.
     * 
     * @return      The descriptive reason wht this scan should not be reduced, or <code>null</code> if no such reason
     *              is identified.
     */
    public String getDiscardReason() {
        if(hasOption("gyrocorrect")) if(hasOption("gyrocorrect.max")) {
            double limit = option("gyrocorrect.max").getDouble() * Unit.arcsec;
            if(gyroDrifts.getMax() > limit) return "Scan " + getID() + " has too large gyro drifts.";
        }
        return null;
    }
    
    /**
     * Checks if a given scan can be co-reduced with this one, and returns an appropriate description of a mismatch
     * if not.
     * 
     * @param scan      The scan to check against this one.
     * @return          A description on how the given scan is a mismatch to this one, or <code>null</code> if the 
     *                  given scan can be co-reduced with this one.
     */
    public String getMismatchDescription(SofiaScan<?> scan) {
        SofiaInstrument<?> instrument = getInstrument();
        
        if(!instrument.isWavelengthConsistent(scan.getInstrument().instrumentData.wavelength))
            return "Scan " + scan.getID() + " is at too different of a wavelength.";

        if(!instrument.isConfigConsistent(scan.getInstrument().instrumentData.instrumentConfig))
            return "Scan " + scan.getID() + " is in different instrument configuration.";

        return null;
    }


    public static String defaultFITSDate = "1970-01-01T00:00:00.0";


}
