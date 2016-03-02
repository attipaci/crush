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

import nom.tam.fits.BasicHDU;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import crush.GroundBased;
import crush.Scan;
import jnum.Unit;
import jnum.Util;
import jnum.astro.CoordinateEpoch;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.GeodeticCoordinates;

public class Mustang2Scan extends Scan<Mustang2, Mustang2Integration> implements GroundBased {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3980706181249384684L;

	String fitsVersion;
	String id;
	int average = 1;
	double zenithTau = Double.NaN;
	
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
		Fits fits = getFits(scanDescriptor);
		read(fits, readFully);
		fits.close();
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
		BasicHDU<?>[] HDU = fits.read();

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
	}
	
	public void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException {
		Header header = hdu.getHeader();
		
		// Load any options based on the FITS header...
		instrument.setFitsHeaderOptions(header);
		
		fitsVersion = header.getStringValue("FITSVER");
		if(fitsVersion.length() == 0) fitsVersion = null;
		
		// Source Information
		String sourceName = header.getStringValue("OBJECT");
		if(sourceName == null) sourceName = descriptor;
		setSourceName(sourceName);
		
		setSerial(header.getIntValue("SCAN", -1));
		project = header.getStringValue("PROJID");
		
		// GBT 79:50:23.406 W, 38:25:59.236 N
		site = new GeodeticCoordinates(
			-(79 * Unit.deg + 50 * Unit.arcmin + 23.406 * Unit.arcsec),	
			38 * Unit.deg + 25 * Unit.arcmin + 59.236 * Unit.arcsec	
		);
		// or use SITELON / SITELAT from FITS?...
		
		timeStamp = header.getStringValue("DATE-OBS");
		String date = timeStamp.substring(0, timeStamp.indexOf('T'));
		String startTime = timeStamp.substring(timeStamp.indexOf('T') + 1);
		id = date + "." + getSerial();
		
		equatorial = new EquatorialCoordinates(
				header.getDoubleValue("RA0") * Unit.hourAngle,
				header.getDoubleValue("DEC0") * Unit.deg,
				CoordinateEpoch.J2000
		); 
		
		System.err.println(" [" + sourceName + "] of project " + project + " observed on " + date + " at " + startTime);
		System.err.println(" Equatorial: " + equatorial.toString());	
		
		
		if(hasOption("tau")) {
			zenithTau = option("tau").getDouble();
			System.err.println(" Using tau: " + Util.f3.format(zenithTau));
		}
		else if(header.containsKey("TAUZ")) {
			zenithTau = header.getDoubleValue("TAUZ");
			System.err.println(" Zenith tau from skydip: " + Util.f3.format(zenithTau));
		}
		else { 
			System.err.println(" No tau in FITS, or specified. No extinction correction.");
		}
	}
	
	
	@Override
	public String getID() { return id; }

	@Override
	public Mustang2Integration getIntegrationInstance() {
		return new Mustang2Integration(this);
	}

}
