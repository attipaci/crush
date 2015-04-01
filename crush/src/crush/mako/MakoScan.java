/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush.mako;

import crush.*;
import crush.cso.CSOScan;
import nom.tam.fits.*;

import java.io.*;
import java.util.*;
import java.text.*;

import kovacs.astro.AstroTime;
import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.HorizontalCoordinates;
import kovacs.math.Vector2D;
import kovacs.util.*;


public class MakoScan<MakoType extends AbstractMako<?>> extends CSOScan<MakoType, MakoIntegration<MakoType>> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7119511779899537289L;
	
	String id;

	public long fileSize = -1;

	private double raUnit = Unit.hourAngle;
	private double decUnit = Unit.deg;

	File file;
	
	public MakoScan(MakoType instrument) {
		super(instrument);
		setSerial(++serialCounter);
	}

	@Override
	public MakoIntegration<MakoType> getIntegrationInstance() {
		return new MakoIntegration<MakoType>(this);
	}
	
	@Override
	public int compareTo(Scan<?, ?> other) {
		return Double.compare(getMJD(), other.getMJD());
	}
	


	
	protected void read(Fits fits, boolean readFully) throws Exception {
		// Read in entire FITS file		
		Header header = fits.getHDU(0).getHeader();
		
		// Check that FITS file is MAKO FITS...
		String instrumentName = header.getStringValue("INSTRUME").toUpperCase();
		
		if(!instrumentName.startsWith("MAKO"))
			throw new IllegalStateException("Not a MAKO FITS file.");
		
		boolean isChirp = hasOption("chirp");
		
		// If it's not a converted file, try see if a converted version exists, 
		if(!isChirp) if(!header.containsKey("CONVSOFT")) {
			String fileName = file.getPath();
			int iExt = fileName.lastIndexOf('.');
			String naming = hasOption("convert.naming") ? option("convert.naming").getValue() : "converted";
			String convertedName = fileName.substring(0, iExt) + "." + naming + fileName.substring(iExt);
			File converted = new File(convertedName);
			
			if(converted.exists()) {
				System.err.println(" Will read existing IQ --> shift converted file instead...");
			}
			else if(hasOption("convert")) {	
				String iqconv = option("convert").getPath();
				
				if(CRUSH.details) System.err.println(" Converting I/Q --> frequency shift...");
				System.err.println("> " + iqconv + " " + file.getPath());
				Process process = Runtime.getRuntime().exec(iqconv + " " + file.getPath());
				process.waitFor();
				if(process.exitValue() != 0) throw new IllegalStateException("I/Q --> frequency shift conversion error.");
			}
			else throw new IllegalStateException("Not an I/Q --> frequency shift converted FITS.");
		
			// Close the IQ fits.
			fits.getStream().close();
			
			// Read the converted FITS instead...
			read(convertedName, readFully);
				
			return;
		}
			
		
		BasicHDU[] HDU = fits.read();
		
		int i = isChirp ? 2 : 4; 
		BasicHDU firstDataHDU = null;
		while(!(firstDataHDU = HDU[i]).getHeader().getStringValue("EXTNAME").toUpperCase().startsWith("STREAM")) i++;
		
		parseScanPrimaryHDU(HDU[0]);
		clear();
		
		// Load the instrument settings...
		instrument.parseScanPrimaryHDU(HDU[0]);
		instrument.parseReadoutHDU((BinaryTableHDU) HDU[1]);
		instrument.parseDataHeader(firstDataHDU.getHeader());
		instrument.validate(this);
		
		parseScanDataHDU((BinaryTableHDU) HDU[i]);
		
		MakoIntegration<MakoType> integration = new MakoIntegration<MakoType>(this);
		integration.read(HDU, i);
		add(integration);
		
		try { fits.getStream().close(); }
		catch(IOException e) {}
		
		horizontal = null;
		
		validate();
	}
	
	
	public File getFile(String scanDescriptor) throws FileNotFoundException {
		
		String path = getDataPath();
		descriptor = scanDescriptor;
		
		if(hasOption("date")) {
			String dateSpec = option("date").getValue().replaceAll("-", "");
			String timeSpec = scanDescriptor.replaceAll(":", "");
			String id = "mako" + dateSpec + "_" +  timeSpec;
			File datadir = new File(path);
			for(File file : datadir.listFiles()) {
				String name = file.getName();
				if(name.startsWith(id)) if(name.endsWith("Stream.fits") || name.endsWith("Stream.fits.gz")) {
					this.file = file;
					return file;				
				}
			}		
		}
			
		File scanFile = new File(scanDescriptor) ;	
		if(!scanFile.exists()) {
			scanFile = new File(path + scanDescriptor);
			if(!scanFile.exists()) throw new FileNotFoundException("Could Not find scan " + scanDescriptor); 
		}
		
		file = scanFile;
		return scanFile;
	}

	public void readScanInfo(String scanDescriptor) throws IOException, HeaderCardException, FitsException {
		readScanInfo(new Fits(getFile(scanDescriptor)));
	}

	protected void readScanInfo(Fits fits) throws IOException, HeaderCardException, FitsException {
		parseScanPrimaryHDU(fits.readHDU());		
		fits.skipHDU(3);

		BasicHDU nextHDU = fits.readHDU();
		while(!nextHDU.getHeader().getStringValue("EXTNAME").startsWith("STREAM") ) nextHDU = fits.readHDU();
		instrument.parseDataHeader(nextHDU.getHeader());
	}
	
	
	@Override
	public void read(String scanDescriptor, boolean readFully) throws Exception {
		read(getFits(scanDescriptor), readFully);
	}

	
	public Fits getFits(String scanDescriptor) throws FileNotFoundException, FitsException {
		File file = getFile(scanDescriptor);
		fileSize = file.length();
		
		boolean isCompressed = file.getName().endsWith(".gz");
		System.out.println(" Reading " + file.getPath() + "...");
		return new Fits(getFile(scanDescriptor), isCompressed);
	}
	
	protected void parseScanDataHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		int iEq = hdu.findColumn("Equatorial Offset");
		if(iEq > 0) {
			boolean isEquatorial = ((boolean[]) hdu.getElement(0, iEq))[0];
			scanSystem = isEquatorial ? EquatorialCoordinates.class : HorizontalCoordinates.class;
		}
		else {
			System.err.println(" WARNING! Scan direction undefined. Assuming /altaz.");
			scanSystem = HorizontalCoordinates.class;		
		}
	}
		
	protected void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();

		// Load any options based on the FITS header...
		instrument.setFitsHeaderOptions(header);
		
		// Scan Info
		setSerial(header.getIntValue("SCANNO"));
			
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
			instrument.setOption("tau.225ghz=" + tau225GHz);
		}

		ambientT = header.getDoubleValue("TEMPERAT") * Unit.K + 273.16 * Unit.K;
		pressure = header.getDoubleValue("PRESSURE") * Unit.mbar;
		humidity = header.getDoubleValue("HUMIDITY");

		// Source Information
		String sourceName = header.getStringValue("OBJECT");
		if(sourceName == null) sourceName = "Unknown";
		setSourceName(sourceName);
		sourceName = getSourceName(); // Confirm the source name setting...
		
		timeStamp = header.getStringValue("DATE-OBS");
	
		
		double epoch = header.getDoubleValue("EQUINOX");
		
		if(equatorial == null) equatorial = new EquatorialCoordinates(
				header.getDoubleValue("RAEP") * raUnit,
				header.getDoubleValue("DECEP") * decUnit,
				(epoch < 1984.0 ? "B" : "J") + epoch);


		//if(header.containsKey("SCANCOORD")) scanSystem = getScanSystem(header.getIntValue("SCANCOORD", SCAN_UNDEFINED));
		//if(scanSystem == null) scanSystem = HorizontalCoordinates.class;

		
		// Print out some of the information...
		StringTokenizer tokens = new StringTokenizer(timeStamp, ":T");
		String dateString = tokens.nextToken();
		String timeMins = tokens.nextToken() + ":" + tokens.nextToken();
		String timeString = timeMins + " UT";
		
		id = hasOption("chirp") ? Integer.toString(header.getIntValue("SCANNO")) : dateString + "." + timeMins;
		
		System.err.println(" [" + sourceName + "] observed on " + dateString + " at " + timeString + " by " + observer);
		if(equatorial != null) System.err.println(" Equatorial: " + equatorial.toString());	
		
		try { setMJD(AstroTime.forFitsTimeStamp(timeStamp).getMJD()); }
		catch(ParseException e) { e.printStackTrace(); }
		
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

		System.err.println("   AZO =" + f3_1.format(horizontalOffset.x()/Unit.arcsec)
				+ "\tELO =" + f3_1.format(horizontalOffset.y()/Unit.arcsec)
				+ "\tRAO =" + f3_1.format(eqOffset.x()/Unit.arcsec)
				+ "\tDECO=" + f3_1.format(eqOffset.y()/Unit.arcsec)
				
		);
		
		System.err.println("   FAZO=" + f3_1.format(fixedOffset.x()/Unit.arcsec)
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

		
	@Override
	public String getID() { return id; }
	
	private static int serialCounter = 0;
	
}
