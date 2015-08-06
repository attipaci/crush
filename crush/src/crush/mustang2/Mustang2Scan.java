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

package crush.mustang2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import kovacs.astro.CoordinateEpoch;
import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.GeodeticCoordinates;
import kovacs.util.Unit;
import kovacs.util.Util;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import crush.GroundBased;
import crush.Scan;

public class Mustang2Scan extends Scan<Mustang2, Mustang2Integration> implements GroundBased {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3980706181249384684L;

	String fitsVersion;
	String ID;
	int average = 1;
	
	public Mustang2Scan(Mustang2 instrument) {
		super(instrument);
	}
	
	@Override
	public void validate() {	
		super.validate();
		
		Mustang2Frame firstFrame = getFirstIntegration().getFirstFrame();
		Mustang2Frame lastFrame = getLastIntegration().getLastFrame();
		
		double PA = 0.5 * (firstFrame.getParallacticAngle() + lastFrame.getParallacticAngle());
		System.err.println("   Mean parallactic angle is " + Util.f1.format(PA / Unit.deg) + " deg.");
	}
	
	
	

	@Override
	public void read(String scanDescriptor, boolean readFully) throws Exception {
		read(getFits(scanDescriptor), readFully);
	}
	
	public Fits getFits(String scanDescriptor) throws FileNotFoundException, FitsException {
		File file = getFile(scanDescriptor);
		boolean isCompressed = file.getName().endsWith(".gz");
		System.out.println(" Reading " + file.getPath() + "...");
		return new Fits(getFile(scanDescriptor), isCompressed);
	}

	public File getFile(String scanDescriptor) throws FileNotFoundException {
		File scanFile;

		String path = getDataPath();
		descriptor = scanDescriptor;
		
	
		scanFile = new File(scanDescriptor) ;	
		if(!scanFile.exists()) {
			scanFile = new File(path + scanDescriptor);
			if(!scanFile.exists()) throw new FileNotFoundException("Could Not find scan " + scanDescriptor); 
		} 	

		return scanFile;
	}

	
	protected void read(Fits fits, boolean readFully) throws Exception {
		// Read in entire FITS file
		BasicHDU[] HDU = fits.read();

		parseScanPrimaryHDU(HDU[0]);
		instrument.parseScanPrimaryHDU(HDU[0]);
		instrument.parseHardwareHDU((BinaryTableHDU) HDU[1]);
		instrument.validate(this);	
		clear();

		Mustang2Integration integration = getIntegrationInstance();
		integration.read((BinaryTableHDU) HDU[2]);
		add(integration);
		
		try { fits.getStream().close(); }
		catch(IOException e) {}
		
		instrument.samplingInterval = integration.instrument.samplingInterval;
		instrument.integrationTime = integration.instrument.integrationTime;
		
		validate();
	}
	
	public void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException {
		// GBT 79:50:23.406 W, 38:25:59.236 N
		
		Header header = hdu.getHeader();
		
		fitsVersion = header.getStringValue("FITSVER");
		if(fitsVersion.length() == 0) fitsVersion = null;
		
		// Source Information
		String sourceName = header.getStringValue("OBJECT");
		if(sourceName == null) sourceName = descriptor;
		setSourceName(sourceName);
		
		setSerial(header.getIntValue("SCAN", -1));
		project = header.getStringValue("PROJID");
		
		site = new GeodeticCoordinates(
			-(79 * Unit.deg + 50 * Unit.arcmin + 23.406 * Unit.arcsec),	
			38 * Unit.deg + 25 * Unit.arcmin + 59.236 * Unit.arcsec	
		);
		
		
		timeStamp = header.getStringValue("DATE-OBS");
		String date = timeStamp.substring(0, timeStamp.indexOf('T'));
		String startTime = timeStamp.substring(timeStamp.indexOf('T') + 1);
		ID = date + "." + getSerial();
		
		
		equatorial = new EquatorialCoordinates(
				header.getDoubleValue("RA0") * Unit.hourAngle,
				header.getDoubleValue("DEC0") * Unit.deg,
				CoordinateEpoch.J2000
		); 
		
		System.err.println(" [" + sourceName + "] of project " + project);
		System.err.println(" Observed on " + date + " at " + startTime + " by " + observer);
		System.err.println(" Equatorial: " + equatorial.toString());		
	}
	
	
	@Override
	public String getID() { return ID; }

	@Override
	public Mustang2Integration getIntegrationInstance() {
		return new Mustang2Integration(this);
	}

}
