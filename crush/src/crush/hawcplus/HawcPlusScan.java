/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.hawcplus;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import crush.CRUSH;
import crush.sofia.SofiaHeader;
import crush.sofia.SofiaScan;
import jnum.Unit;
import jnum.Util;
import jnum.astro.EquatorialCoordinates;

public class HawcPlusScan extends SofiaScan<HawcPlus, HawcPlusIntegration> {	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3732251029215505308L;
	
	GyroDrifts gyroDrifts;
	String priorPipelineStep;
	boolean useBetweenScans;	
		
	double transitTolerance = Double.NaN;
	
	double focusTOffset;

	
	public HawcPlusScan(HawcPlus instrument) {
		super(instrument);
		if(!CRUSH.debug) Logger.getLogger(Header.class.getName()).setLevel(Level.SEVERE);
	}

	@Override
	public HawcPlusIntegration getIntegrationInstance() {
		return new HawcPlusIntegration(this);
	}

	@Override
    public boolean isAORValid() {
	    // In the early comissioning runs CDH defaulted to AOR being '0' instead of 'UNKNOWN'
        return super.isAORValid() && !observation.aorID.equals("0");
    }
	
	@Override
    protected boolean isFileNameMatching(String fileName, int flightNo, int scanNo) {
	    if(super.isFileNameMatching(fileName, flightNo, scanNo)) return true;
	    
	    // E.g. F0004_HC_IMA_0_HAWC_HWPC_RAW_109.fits
	    String upperCaseName = fileName.toUpperCase();
	    
	    // 1. Check if the file name contains the instrument ID...
	    if(!upperCaseName.contains("_" + instrument.getFileID().toUpperCase())) return false;
	    	
	    // 2. Check if the file name starts with the flight ID...
        String oldFlightID = "F" + Util.d4.format(flightNo) + "_"; 
        if(!upperCaseName.startsWith(oldFlightID)) return false;
        
        // 3. Check if the file name contains the scans ID and a '.fits' extension...
        String oldScanID = "_" + Util.d3.format(scanNo) + ".FITS";
        return upperCaseName.contains(oldScanID);         
	}
	    
	@Override
    public boolean isRequestedValid(SofiaHeader header) {
	    if(!super.isRequestedValid(header)) return false;
	    
	    // Early CDH workaround when OBSRA=1.0 and OBSDEC=2.0 hardcoded...
        double obsRA = header.getDouble("OBSRA");
        double obsDEC = header.getDouble("OBSDEC");
        
        return !(obsRA == 1.0 && obsDEC == 2.0);
	}
	
	@Override
	public void parseHeader(SofiaHeader header) throws Exception {
	    /*
	    if(header.containsKey("CMTFILE")) {
	        String type = header.getString("CMTFILE").toLowerCase();
	        if(!type.contains("scan")) throw new IllegalStateException("File does not appear to be a scan: " + type);
	    }
	    */
	    
	    priorPipelineStep = header.getString("PROCLEVL");
		
	    // If using real-time object coordinates regardless of whether sidereal or not, then treat as if non-sidereal...
		// Update: (Nov 2016)
	    //   NONSIDE has been removed from the FITS as there was no automatic way of setting it
	    //           it relied on a manual checkbox in the CDH GUI.
	    isNonSidereal = header.getBoolean("NONSIDE", false) || hasOption("rtoc");
	    
		if(hasOption("OBJRA") && hasOption("OBJDEC")) 
		    objectCoords = new EquatorialCoordinates(header.getHMSTime("OBJRA") * Unit.timeAngle, header.getDMSAngle("OBJDEC"), telescope.epoch);
		
		focusTOffset = header.getDouble("FCSTOFF") * Unit.um;
	        
		super.parseHeader(header);	
		
		gyroDrifts = new GyroDrifts(this);
		gyroDrifts.parse(header);
	}
	
	@Override
    protected EquatorialCoordinates guessReferenceCoordinates(SofiaHeader header) {
	    if(isNonSidereal) {
	        info("Referencing images to real-time object coordinates.");
	        return null;
	    }
	    return super.guessReferenceCoordinates(header);
	}
	
	    
	@Override
	public void editScanHeader(Header header) throws HeaderCardException {
		super.editScanHeader(header);
		if(priorPipelineStep != null) header.addLine(new HeaderCard("PROCLEVL", priorPipelineStep, "Last processing step on input scan."));	
	}

	@Override
    public void addIntegrationsFrom(BasicHDU<?>[] HDU) throws Exception {
        ArrayList<BinaryTableHDU> dataHDUs = new ArrayList<BinaryTableHDU>();
        
        for(int i=1; i<HDU.length; i++) if(HDU[i] instanceof BinaryTableHDU) {
            Header header = HDU[i].getHeader();
            String extName = header.getStringValue("EXTNAME");
            if(extName.equalsIgnoreCase("Timestream")) dataHDUs.add((BinaryTableHDU) HDU[i]);
        }
        
        HawcPlusIntegration integration = this.getIntegrationInstance();
        integration.read(dataHDUs);
        add(integration);
      
    }
	
	@Override
    public void validate() {
	    if(hasOption("chopper.tolerance")) transitTolerance = Math.abs(option("chopper.tolerance").getDouble());
	    useBetweenScans = hasOption("betweenscans");
	    
	    super.validate();
	}
	
	 @Override
	 public Object getTableEntry(String name) {
	     if(name.equals("hawc.dfoc")) return focusTOffset / Unit.um;
	     return super.getTableEntry(name);
	 }
}
