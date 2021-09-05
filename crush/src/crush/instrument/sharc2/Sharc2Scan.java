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


package crush.instrument.sharc2;

import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroSystem;
import jnum.astro.AstroTime;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.EquatorialSystem;
import jnum.astro.EquatorialTransform;
import jnum.math.Vector2D;
import nom.tam.fits.*;

import java.io.*;
import java.util.*;

import crush.telescope.cso.CSOScan;

import java.text.*;


class Sharc2Scan extends CSOScan<Sharc2Integration> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2008390740743369604L;

	public long fileSize = -1;

	private double raUnit = Unit.hourAngle;
	private double decUnit = Unit.deg;
	
	Sharc2Scan(Sharc2 instrument) {
		super(instrument);
	}

	@Override
    public Sharc2 getInstrument() { return (Sharc2) super.getInstrument(); }
	
	@Override
	public Sharc2Integration getIntegrationInstance() {
		return new Sharc2Integration(this);
	}
	
	@Override
	public void calcHorizontal() {
		super.calcHorizontal();
			
		// When the telescope is not tracking, the equatorial coordinates may be bogus...
		// Use the horizontal coordinates to make sure the equatorial ones make sense...
		EquatorialCoordinates eq2 = horizontal.toEquatorial(site, LST, apparent.getSystem());	
		
		eq2.setSystem(new EquatorialSystem.Topocentric("CSO", site, getMJD()));

		EquatorialTransform T = new EquatorialTransform(eq2.getSystem(), equatorial.getSystem());
		T.transform(eq2);
	
		if(eq2.distanceTo(equatorial) > 5.0 * Unit.deg) {
			info(">>> Fix: invalid (stale) equatorial coordinates.");
			equatorial = eq2;
			apparent = horizontal.toEquatorial(site, LST, apparent.getSystem());
		}
	}
	
	
	protected void read(Fits fits, boolean readFully) throws Exception {
		// Read in entire FITS file		    
		BasicHDU<?>[] HDU = fits.read();
		
		int i = 4; 
		BasicHDU<?> firstDataHDU = null;
		while( !(firstDataHDU = HDU[i]).getHeader().getStringValue("EXTNAME").equalsIgnoreCase("SHARC2 Data") ) i++;
		
		checkPrematureFits(HDU[0], (BinaryTableHDU) firstDataHDU);
		
		parseScanPrimaryHDU(HDU[0]);
		clear();
		
		Sharc2 sharc2 = getInstrument();
		// Load the instrument settings...
		sharc2.parseScanPrimaryHDU(HDU[0]);
		sharc2.parseHardwareHDU((BinaryTableHDU) HDU[1]);
		sharc2.parseDSPHDU((BinaryTableHDU) HDU[2]);
		sharc2.parsePixelHDU((BinaryTableHDU) HDU[3]);
		sharc2.parseDataHeader(firstDataHDU.getHeader());
		sharc2.configure();
		sharc2.validate();
		
		Sharc2Integration integration = new Sharc2Integration(this);
		integration.read(HDU, i);
		add(integration);
		
		try { fits.getStream().close(); }
		catch(IOException e) {}
		
		horizontal = null;
	}
	
	
	private File getFile(String scanDescriptor) throws FileNotFoundException {
		File scanFile;

		String path = getDataPath();
		descriptor = scanDescriptor;
		
		try { 
			int scanNo = Integer.parseInt(scanDescriptor);
			String fileName = path + "sharc2-" + Util.d6.format(scanNo) + ".fits";
			scanFile = new File(fileName);
			
			// Try with various compressed formats...
			if(!scanFile.exists()) scanFile = new File(fileName + ".gz");
			if(!scanFile.exists()) scanFile = new File(fileName + ".xz");
			if(!scanFile.exists()) scanFile = new File(fileName + ".Z");
			if(!scanFile.exists()) scanFile = new File(fileName + ".bz2");
			if(!scanFile.exists()) scanFile = new File(fileName + ".zip");
			
			if(!scanFile.exists()) throw new FileNotFoundException("Could Not find scan " + scanDescriptor); 
		} 
		catch(NumberFormatException e) {
			scanFile = new File(scanDescriptor) ;	
			if(!scanFile.exists()) {
				scanFile = new File(path + scanDescriptor);
				if(!scanFile.exists()) throw new FileNotFoundException("Could Not find scan " + scanDescriptor); 
			} 
		}	

		fileSize = scanFile.length();

		return scanFile;
	}
	
	@Override
	public void read(String scanDescriptor, boolean readFully) throws Exception {
	    try(Fits fits = getFits(scanDescriptor)) {
	        read(fits, readFully);
	    }
	}

	
	private Fits getFits(String scanDescriptor) throws FileNotFoundException, FitsException {
		File file = getFile(scanDescriptor);
		info("Reading " + file.getPath() + "...");
		return new Fits(getFile(scanDescriptor));
	}
	
	protected void checkPrematureFits(BasicHDU<?> main, BinaryTableHDU data) throws FitsException {
		equatorial = null;
		Header header = main.getHeader();
		if(header.containsKey("RAEP") && header.containsKey("DECEP")) return;
		
		info(">>> Fix: Assuming early JSharc data format.");
		info(">>> Fix: Source coordinates obtained from antenna stream.");
		
		getInstrument().earlyFITS = true;
		header = data.getHeader();
		float RA = ((float[]) data.getElement(0, data.findColumn("RA")))[0];
		float DEC = ((float[]) data.getElement(0, data.findColumn("DEC")))[0];
		
		equatorial = new EquatorialCoordinates(RA * raUnit, DEC * decUnit, EquatorialSystem.fromHeader(header));
	}
	
	@Override
	public void setSerial(int number) {
		// Fix for early Nov 2002 scans, where RA/DEC are in incorrect units.
		if(number < 7320) {
			info(">>> Fix: RA/DEC column in unexpected units.");
			raUnit = 1.0;
			decUnit = 1.0;
		}
		else {
			raUnit = Unit.hourAngle;
			decUnit = Unit.deg;
		}
	
		super.setSerial(number);
	}

	@Override
	public void setSourceName(String name) {
		// For early SHARC-2 data, the source name was sometimes lagging, and therefore not always correct. 
		// (JSharc got the source name from the UIP at the beginning of the scan, rather than at the end)
		// This happened only before scan 7320. For these early scans, we can recover the correct source name
		// based on a correction list definition...
		if(getSerial() < 7320) name = fixSourceName(name);
		super.setSourceName(name);		
	}
	
	private String fixSourceName(String name) {
		int serial = getSerial();
		String fileName = getInstrument().getConfigPath() + "sourcename-2002.fix";
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)))) {
			String line = null;
			while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
				StringTokenizer tokens = new StringTokenizer(line);
				if(Integer.parseInt(tokens.nextToken()) == serial) {
					name = tokens.nextToken();
					info("Fix: source name changed to " + name);
					in.close();
					return name;
				}
			}
			in.close();
		}
		catch(IOException e) { info(">>> Fix: WARNING! Cannot find name fix list specification file. No fix."); }
		return name;
	}
	
	protected void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException {
		Header header = hdu.getHeader();

		// Load any options based on the FITS header...
		getInstrument().setFitsHeaderOptions(header);
		
		// Scan Info
		setSerial(header.getIntValue("SCANNO"));
		
		//site = new GeodesicCoordinates(header.getDoubleValue("TELLONGI") * Unit.deg, header.getDoubleValue("TELLATID") * Unit.deg);
		//info("Location: " + site);
		
		creator = header.getStringValue("CREATOR");
		observer = header.getStringValue("OBSERVER");
		project = header.getStringValue("PROJECT");

		if(creator == null) creator = "Unknown";
		if(observer == null) observer = "Unknown";
		if(project == null) project = "Unknown";

		// Weather
		if(hasOption("tau.225ghz")) tau225GHz = option("tau.225ghz").getDouble();
		else {
			tau225GHz = header.getDoubleValue("TAU225GH");
			getInstrument().setOption("tau.225ghz=" + tau225GHz);
		}

		ambientT = header.getDoubleValue("TEMPERAT") * Unit.K + Constant.zeroCelsius;
		pressure = header.getDoubleValue("PRESSURE") * Unit.mbar;
		humidity = header.getDoubleValue("HUMIDITY");

		// Source Information
		String sourceName = header.getStringValue("OBJECT");
		if(sourceName == null) sourceName = "Unknown";
		setSourceName(sourceName);
		sourceName = getSourceName(); // Confirm the source name setting...
		
		timeStamp = header.getStringValue("DATE-OBS");
		
		// increment month by 1 to correct JSharc FITS bug
		if(creator.equalsIgnoreCase("JSharc")) {
			info(">>> Fix: FITS libraries BUG in JSharc timestamp.");
			String modDate = timeStamp.substring(0, 5);
			modDate += Util.d2.format(Integer.parseInt(timeStamp.substring(5,7)) + 1 );
			modDate += timeStamp.substring(7);
			timeStamp = modDate;
			//CRUSH.detail(this, modDate);
		}
		double epoch = header.getDoubleValue("EQUINOX");
		
		if(equatorial == null) equatorial = new EquatorialCoordinates(
				header.getDoubleValue("RAEP") * raUnit,
				header.getDoubleValue("DECEP") * decUnit,
				(epoch < 1984.0 ? "B" : "J") + epoch);
	
		
		if(header.containsKey("SCANCOORD")) scanSystem = getScanSystem(header);
		if(scanSystem == null) scanSystem = AstroSystem.horizontal;
		
		// Print out some of the information...
		StringTokenizer tokens = new StringTokenizer(timeStamp, ":T");
		String dateString = tokens.nextToken();
		String timeString = tokens.nextToken() + ":" + tokens.nextToken() + " UT";
		
		info("[" + sourceName + "] observed on " + dateString + " at " + timeString + " by " + observer);
		if(equatorial != null) info("Equatorial: " + equatorial);	
		
		try { setMJD(AstroTime.forFitsTimeStamp(timeStamp).MJD()); }
		catch(ParseException e) { error(e); }
		
		iMJD = (int) Math.floor(getMJD());
		
		// iMJD does not exist in earlier scans
		// convert DATE-OBS into MJD...
		//if(header.containsKey("JUL_DAY")) iMJD = header.getIntValue("JUL_DAY");
			
		addStaticOffsets = hasOption("offsets.add");
		
		// Add on the various additional offsets
		horizontalOffset = new Vector2D(
				header.getDoubleValue("AZO") * Unit.arcsec,
				-header.getDoubleValue("ZAO") * Unit.arcsec);
		horizontalOffset.addX(header.getDoubleValue("AZO_MAP") * Unit.arcsec);
		horizontalOffset.subtractY(header.getDoubleValue("ZAO_MAP") * Unit.arcsec);
		horizontalOffset.addX(header.getDoubleValue("AZO_CHOP") * Unit.arcsec);		
		horizontalOffset.subtractY(header.getDoubleValue("ZAO_CHOP") * Unit.arcsec);
		horizontalOffset.addX(header.getDoubleValue("CHPOFFST") * Unit.arcsec);
		
		Vector2D eqOffset = new Vector2D( 
				header.getDoubleValue("RAO") * Unit.arcsec,
				header.getDoubleValue("DECO") * Unit.arcsec);		
		eqOffset.addX(header.getDoubleValue("RAO_MAP") * Unit.arcsec);
		eqOffset.addY(header.getDoubleValue("DECO_MAP") * Unit.arcsec);
		eqOffset.addX(header.getDoubleValue("RAO_FLD") * Unit.arcsec);
		eqOffset.addY(header.getDoubleValue("DECO_FLD") * Unit.arcsec);

		fixedOffset = new Vector2D(header.getDoubleValue("FAZO") * Unit.arcsec, -header.getDoubleValue("FZAO") * Unit.arcsec);	
		
		DecimalFormat f3_1 = new DecimalFormat(" 0.0;-0.0");

		info("  AZO =" + f3_1.format(horizontalOffset.x()/Unit.arcsec)
				+ "\tELO =" + f3_1.format(horizontalOffset.y()/Unit.arcsec)
				+ "\tRAO =" + f3_1.format(eqOffset.x()/Unit.arcsec)
				+ "\tDECO=" + f3_1.format(eqOffset.y()/Unit.arcsec)
				
		);
		
		info("  FAZO=" + f3_1.format(fixedOffset.x()/Unit.arcsec)
				+ "\tFZAO=" + f3_1.format(-fixedOffset.y()/Unit.arcsec)
		);
		
		equatorial.addOffset(eqOffset);
	
		// Add pointing corrections...
		if(hasOption("fazo")) {
			double fazo = option("fazo").getDouble() * Unit.arcsec;
			horizontalOffset.addX(fixedOffset.x() - fazo);
			fixedOffset.setX(fazo);
		}
		if(hasOption("fzao")) {
			double felo = -option("fzao").getDouble() * Unit.arcsec;
			horizontalOffset.addY(fixedOffset.y() - felo);
			fixedOffset.setY(felo);
		}		
	}

}
