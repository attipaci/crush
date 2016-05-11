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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import crush.CRUSH;
import crush.sofia.SofiaHeader;
import crush.sofia.SofiaScan;
import jnum.Util;

public class HawcPlusScan extends SofiaScan<HawcPlus, HawcPlusIntegration> {	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3732251029215505308L;
	
	String priorPipelineStep;
	boolean useBetweenScans;
	
	public HawcPlusScan(HawcPlus instrument) {
		super(instrument);
		if(!CRUSH.debug) Logger.getLogger(Header.class.getName()).setLevel(Level.SEVERE);
	}

	@Override
	public HawcPlusIntegration getIntegrationInstance() {
		return new HawcPlusIntegration(this);
	}

	@Override
	public void parseHeader(SofiaHeader header) throws Exception {
		super.parseHeader(header);
		
		priorPipelineStep = header.getString("PROCLEVL");
		isNonSidereal = header.getBoolean("NONSIDE", false);
		
		// TODO Data without AORs -- should not happen...
		if(observation.aorID.equals("0")) {
		    System.err.println(" WARNING! No AOR, will use initial scan position as reference.");
		    equatorial = null;
		}
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
    public File getFile(String scanDescriptor) throws FileNotFoundException {
       
	    try { return super.getFile(scanDescriptor); }
	    catch(FileNotFoundException e) { if(!hasOption("date")) throw e; }
	    
	    int scanNo = -1;
	    try { scanNo = Integer.parseInt(scanDescriptor); }
	    catch(NumberFormatException e) { throw new FileNotFoundException("Cannot find file for: '" + scanDescriptor+ "'"); }
	        
	    String path = getDataPath();
	    
	    String date = option("date").getValue().replace("-", "");
	    if(date.length() != 8) throw new FileNotFoundException("Invalid date: " + option("date").getValue());
	    date = date.substring(2); // YYYYMMDD --> YYMMDD
	    
	    // Otherwise, see if anything in the path matches...
        File root = new File(path);
        String[] fileName = root.list();
        
        if(fileName == null) throw new FileNotFoundException("Incorrect 'datapath'.");
           
        String part = "_raw_" + Util.d3.format(scanNo) + ".fits";
         
        for(int i=0; i<fileName.length; i++) {
            String lowerCaseName = fileName[i].toLowerCase();
            
            if(lowerCaseName.length() < 20) continue; // Minimum filename is: xYYMMDD_RAW_nnn.fits
            if(!lowerCaseName.substring(1, 7).equals(date)) continue;
            if(!lowerCaseName.contains("_haw")) continue;
            if(!lowerCaseName.contains(part)) continue;
            
            return new File(path + fileName[i]);
        }
        throw new FileNotFoundException("Cannot find file for: '" + scanDescriptor + "'");
	   
    }   
	
	@Override
    public void validate() {
	    useBetweenScans = hasOption("betweenscans");
	    
	    super.validate();
	}
	
	
}
