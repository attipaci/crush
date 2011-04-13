/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2009 Attila Kovacs 

package crush.gismo;

import crush.*;
import nom.tam.fits.*;
import util.*;
import util.astro.AstroTime;
import util.astro.CoordinateEpoch;
import util.astro.EquatorialCoordinates;
import util.astro.GeodeticCoordinates;
import util.astro.JulianEpoch;
import util.astro.Weather;

import java.io.*;
import java.text.*;
import java.util.*;

public class GismoScan extends Scan<Gismo, GismoIntegration> implements GroundBased, Weather {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1608680718251250629L;
	
	Vector2D horizontalOffset;
	String date, startTime, endTime;
	
	double tau225GHz;
	double ambientT, pressure, humidity, windAve, windPeak, windDirection;
	String scanID, obsType;
	IRAMPointingModel iRAMPointingModel;
	Vector2D appliedPointing = new Vector2D();
	
	
	public GismoScan(Gismo instrument) {
		super(instrument);
	}
	
	@Override
	public GismoIntegration getIntegrationInstance() {
		return new GismoIntegration(this);
	}

	@Override
	public void read(String scanDescriptor, boolean readFully) throws HeaderCardException, FitsException , FileNotFoundException{
		read(getFits(scanDescriptor), readFully);
	}

	
	@Override
	public void validate() {	
		super.validate();
		double PA = 0.5 * (getFirstIntegration().getFirstFrame().getParallacticAngle() + getLastIntegration().getLastFrame().getParallacticAngle());
		System.err.println("   Mean parallactic angle is " + Util.f1.format(PA / Unit.deg) + " deg.");		
	}
	
	@Override
	public Vector2D getPointingCorrection(Configurator option) {
		Vector2D correction = super.getPointingCorrection(option);
		
		if(!option.isConfigured("model")) return correction;
		
		try { 
			IRAMPointingModel model = new IRAMPointingModel(option.get("model").getValue());
			Vector2D modelCorr = model.getCorrection(horizontal);

			System.err.println("   Got pointing from model: " + 
					Util.f1.format(modelCorr.x / Unit.arcsec) + ", " +
					Util.f1.format(modelCorr.y / Unit.arcsec) + " arcsec."
			);

			if(correction == null) correction = modelCorr;
			else correction.add(modelCorr);
		}
		catch(IOException e) {
			System.err.println("WARNING! Cannot read pointing model from " + option.get("model").getValue());
		}

		return correction;
		
	}

	public File getFile(String scanDescriptor) throws FileNotFoundException {
		File scanFile;

		String path = getDataPath();
		descriptor = scanDescriptor;

		// Try to read scan number with the help of 'object' and 'date' keys...
		try {
			int scanNo = Integer.parseInt(scanDescriptor);
			if(hasOption("date") && hasOption("object")) {
				String dirName = path + option("object").getValue() + File.separator + option("date").getValue() + "." + scanNo;
				File directory = new File(dirName);

				if(!directory.exists()) {
					String message = "Cannot find scan directory " + dirName +
						"\n    * Check that 'object' (case sensitive!) and 'date' (YYYY-MM-DD) are correct:" + 
						"\n      --> object = '" + option("object").getValue() + "'" +
						"\n      --> date = '" + option("date").getValue() + "'";
					throw new FileNotFoundException(message);
				}
				else if(!directory.isDirectory()) {
					throw new FileNotFoundException(dirName + " is not a directory.");
				}
				else {
					String[] files = directory.list();
					for(int i=0; i<files.length; i++) if(files[i].endsWith("GISMO-IRAM-condensed.fits"))
						return new File(dirName + File.separator + files[i]);
					throw new FileNotFoundException("Cannot find a merged GISMO-IRAM-condensed.fits file in " + dirName);
				}
			}
			else {
				String message = "Cannot find scan " + scanDescriptor;

				if(!hasOption("object")) 
					message += "\n    * Specify 'object' to help locate the scan.";
				if(!hasOption("date")) 
					message += "\n    * Specify 'date' for unique IRAM scan ID.";
			
				throw new FileNotFoundException(message);
			}
		}
		// Otherwise, just read as file names...
		catch(NumberFormatException e) {
			scanFile = new File(scanDescriptor) ;	
			if(!scanFile.exists()) {
				scanFile = new File(path + scanDescriptor);
				if(!scanFile.exists()) throw new FileNotFoundException("Could not find scan " + scanDescriptor);
			} 	

			return scanFile;
		}
	}	

	
	public Fits getFits(String scanDescriptor) throws FileNotFoundException, FitsException {
		File file = getFile(scanDescriptor);
		boolean isCompressed = file.getName().endsWith(".gz");
		System.out.println(" Reading " + file.getPath() + "...");
		return new Fits(getFile(scanDescriptor), isCompressed);
	}
	
	protected void read(Fits fits, boolean readFully) throws IllegalStateException, HeaderCardException, FitsException {
		// Read in entire FITS file
		BasicHDU[] HDU = fits.read();

		parseScanPrimaryHDU(HDU[0]);
		instrument.parseScanPrimaryHDU(HDU[1]);
		instrument.parseHardwareHDU((BinaryTableHDU) HDU[1]);
		instrument.validate(MJD);	
		clear();
		
		GismoIntegration integration = new GismoIntegration(this);
		integration.isProper = readFully;
		integration.read((BinaryTableHDU) HDU[2]);
		
		try { fits.getStream().close(); }
		catch(IOException e) {}
		
		add(integration);
		
		instrument.samplingInterval = integration.instrument.samplingInterval;
		instrument.integrationTime = integration.instrument.integrationTime;
		
		validate();
	}
	

	
	protected void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();

		// Scan Info
		serialNo = header.getIntValue("SCANNO");
		
		if(instrument.options.containsKey("serial")) instrument.setSerialOptions(serialNo);
		
		site = new GeodeticCoordinates(header.getDoubleValue("TELLONGI") * Unit.deg, header.getDoubleValue("TELLATID") * Unit.deg);
		System.err.println(" Telescope Location: " + site);
		
		// IRAM Pico Veleta PDF 
		//site = new GeodeticCoordinates("-03d23m33.7s, 37d03m58.3s");
		
		// Google maps and Wikipedia...
		//site = new GeodeticCoordinates("-03d23m58.1s, 37d04m05.6s");
		
		// Antenna amymuth axis coordinates from Juan Penalver
		//latitude: N 37d 4m 6.29s
		//longitude: W 3d 23m 55.51s 
		site = new GeodeticCoordinates("-03d23m55.51s, 37d04m06.29s");
		
		creator = header.getStringValue("CREATOR");
		observer = header.getStringValue("OBSERVER");
		project = header.getStringValue("PROJECT");
		descriptor = header.getStringValue("DESCRIPT");
		scanID = header.getStringValue("SCANID");
		obsType = header.getStringValue("OBSTYPE");
		
		if(creator == null) creator = "Unknown";
		if(observer == null) observer = "Unknown";
		if(project == null) project = "Unknown";
		if(obsType == null) obsType = "Unknown";
		if(scanID == null) scanID = "Unknown";
		else {
			StringTokenizer tokens = new StringTokenizer(scanID, ".");
			tokens.nextToken(); // Date...
			serialNo = Integer.parseInt(tokens.nextToken());
		}
		serialNo = header.getIntValue("SCANNUM", serialNo);

		if(obsType.equalsIgnoreCase("tip")) {
			System.err.println(" Setting options for skydip");
			instrument.options.parse("skydip");
		}
		
		// Weather
		if(hasOption("tau.225ghz")) tau225GHz = option("tau.225ghz").getDouble();
		else {
			tau225GHz = header.getDoubleValue("TAU225GH");
			instrument.options.process("tau.225ghz", tau225GHz + "");
		}

		ambientT = header.getDoubleValue("TEMPERAT") * Unit.K + 273.16 * Unit.K;
		pressure = header.getDoubleValue("PRESSURE") * Unit.hPa;
		humidity = header.getDoubleValue("HUMIDITY");
		windAve = header.getDoubleValue("WIND_AVE") * Unit.m / Unit.s;
		windPeak = header.getDoubleValue("WIND_PK") * Unit.m / Unit.s;
		windDirection = header.getDoubleValue("WIND_DIR") * Unit.deg;
	
		// Source Information
		if(hasOption("object")) sourceName = option("object").getValue();
		else sourceName = header.getStringValue("OBJECT");
		if(sourceName == null) sourceName = descriptor;
		
		date = header.getStringValue("DATE-OBS");
		startTime = header.getStringValue("UTCSTART");
		endTime = header.getStringValue("UTCEND");
		
		if(date.contains("T")) {
			timeStamp = date;
			date = timeStamp.substring(0, timeStamp.indexOf('T'));
			startTime = timeStamp.substring(timeStamp.indexOf('T') + 1);
		}
		else if(startTime == null) timeStamp = date + "T" + startTime;
		else timeStamp = date;
		
		try { 
			MJD = AstroTime.forFitsTimeStamp(timeStamp).getMJD();
			instrument.setMJDOptions(MJD);
			instrument.setDateOptions(MJD);
		}
		catch(ParseException e) { System.err.println("WARNING! " + e.getMessage()); }
			
		CoordinateEpoch epoch = hasOption("epoch") ? 
				CoordinateEpoch.forString(option("epoch").getValue()) 
			: new JulianEpoch(header.getDoubleValue("EQUINOX"));
		
		
		equatorial = new EquatorialCoordinates(
				header.getDoubleValue("RAEP") * Unit.hourAngle,
				header.getDoubleValue("DECEP") * Unit.deg,
				epoch);
			
		System.err.println(" [" + sourceName + "] observed on " + date + " at " + startTime + " by " + observer);
		System.err.println(" Equatorial: " + equatorial.toString());	
	
		// Add on the various additional offsets
		
		horizontalOffset = new Vector2D(
				header.getDoubleValue("AZO") * Unit.arcsec,
				-header.getDoubleValue("ZAO") * Unit.arcsec);
		horizontalOffset.x += header.getDoubleValue("AZO_MAP") * Unit.arcsec;
		horizontalOffset.y -= header.getDoubleValue("ZAO_MAP") * Unit.arcsec;
		horizontalOffset.x += header.getDoubleValue("AZO_CHOP") * Unit.arcsec;		
		horizontalOffset.y -= header.getDoubleValue("ZAO_CHOP") * Unit.arcsec;
		horizontalOffset.x += header.getDoubleValue("CHPOFFST") * Unit.arcsec;
		
		Vector2D eqOffset = new Vector2D( 
				header.getDoubleValue("RAO") * Unit.arcsec,
				header.getDoubleValue("DECO") * Unit.arcsec);		
		eqOffset.x += header.getDoubleValue("RAO_MAP") * Unit.arcsec;
		eqOffset.y += header.getDoubleValue("DECO_MAP") * Unit.arcsec;
		eqOffset.x += header.getDoubleValue("RAO_FLD") * Unit.arcsec;
		eqOffset.y += header.getDoubleValue("DECO_FLD") * Unit.arcsec;
	
		DecimalFormat f3_1 = new DecimalFormat(" 0.0;-0.0");

		System.err.println("   AZO =" + f3_1.format(horizontalOffset.x/Unit.arcsec)
				+ "\tELO =" + f3_1.format(horizontalOffset.y/Unit.arcsec)
				+ "\tRAO =" + f3_1.format(eqOffset.x/Unit.arcsec)
				+ "\tDECO=" + f3_1.format(eqOffset.y/Unit.arcsec)
		);
		
		equatorial.addOffset(eqOffset);
	
		// Add pointing corrections...
		
		iRAMPointingModel = new IRAMPointingModel();
		for(int i=1; i<=9; i++) 
			iRAMPointingModel.P[i] = header.getDoubleValue("PCONST" + i, 0.0) + header.getDoubleValue("P" + i + "COR", 0.0);
		
		
		
		isTracking = true;
		
	}
	
	public double getAmbientHumidity() {
		return humidity;
	}

	public double getAmbientPressure() {
		return pressure;
	}

	public double getAmbientTemperature() {
		return ambientT;
	}

	public double getWindDirection() {
		return windDirection;
	}

	public double getWindPeak() {
		return windPeak;
	}

	public double getWindSpeed() {
		return windAve;
	}

	@Override 
	public int compareTo(Scan<Gismo, GismoIntegration> scan) {
		try {
			int dateComp = dateFormat.parse(date).compareTo(dateFormat.parse(((GismoScan) scan).date));
			if(dateComp != 0) return dateComp;
			if(serialNo == scan.serialNo) return 0;
			return serialNo < scan.serialNo ? -1 : 1;
		}
		catch(ParseException e) {
			System.err.println("WARNING! Cannot parse date: '" + date + "' or '" + ((GismoScan) scan).date + "'.");
			return 0;
		}	
	}
	
	@Override
	public String getID() {
		return scanID;
	}
	
	@Override
	public DataTable getPointingData() {
		DataTable data = super.getPointingData();

		Vector2D pointingOffset = getNativePointingIncrement(pointing);
		Vector2D nasmyth = getNasmythOffset(pointingOffset);
		
		double sizeUnit = instrument.getDefaultSizeUnit();
		String sizeName = instrument.getDefaultSizeName();
	
		Vector2D corr = pointingCorrection == null ? new Vector2D() : pointingCorrection;
		
		data.add(new Datum("X", (pointingOffset.x + corr.x) / sizeUnit, sizeName));
		data.add(new Datum("Y", (pointingOffset.y + corr.y) / sizeUnit, sizeName));
		data.add(new Datum("NasX", (instrument.nasmythOffset.x + nasmyth.x) / sizeUnit, sizeName));
		data.add(new Datum("NasY", (instrument.nasmythOffset.y + nasmyth.y) / sizeUnit, sizeName));
		
		return data;
	}
	
	
	@Override
	public String getPointingString(Vector2D pointing) {
		Vector2D corr = pointingCorrection == null ? new Vector2D() : pointingCorrection;
		
		return super.getPointingString(pointing) + "\n" +
			"  Absolute: " + 
			Util.f1.format((pointing.x + corr.x) / Unit.arcsec) + ", " + 
			Util.f1.format((pointing.y + corr.y) / Unit.arcsec) + " arcsec (az, el)";
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		if(name.equals("obstype")) return obsType;
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	public static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
}
