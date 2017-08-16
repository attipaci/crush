/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
// Copyright (c) 2009 Attila Kovacs 

package crush.instrument.gismo;

import crush.*;
import crush.astro.PointingTable;
import crush.telescope.GroundBased;
import crush.telescope.iram.IRAMPointingModel;
import crush.telescope.iram.IRAMScanID;
import crush.telescope.iram.IRAMTauTable;
import jnum.Configurator;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.data.DataPoint;
import jnum.data.image.Asymmetry2D;
import jnum.fits.FitsToolkit;
import jnum.math.Offset2D;
import jnum.math.Range;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.util.*;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

import java.io.*;
import java.text.*;
import java.util.*;


public class GismoScan extends Scan<AbstractGismo, GismoIntegration> implements GroundBased, Weather {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1608680718251250629L;
	
	String date, startTime, endTime;
	
	double tau225GHz;
	double ambientT, pressure, humidity, windAve, windPeak, windDirection;
	double fitsVersion = Double.NaN;
	String scanID, obsType, operator;
	IRAMPointingModel observingModel, tiltCorrections;
		
	public CoordinateEpoch epoch;
	public Class<? extends SphericalCoordinates> basisSystem;
	public Class<? extends SphericalCoordinates> offsetSystem;
	public boolean projectedOffsets = true;
	public boolean skipReconstructed = false;
	
	public Vector2D nasmythOffset, equatorialOffset, horizontalOffset, pakoOffsets;
	public Vector2D basisOffset = new Vector2D();
	
	public GismoScan(AbstractGismo instrument) {
		super(instrument);
	}
	
	@Override
	public GismoIntegration getIntegrationInstance() {
		return new GismoIntegration(this);
	}

	@Override
	public void read(String scanDescriptor, boolean readFully) throws Exception {
		read(getFits(scanDescriptor), readFully);
	}

	
	@Override
	public void validate() {	
		super.validate();
		double PA = 0.5 * (getFirstIntegration().getFirstFrame().getParallacticAngle() + getLastIntegration().getLastFrame().getParallacticAngle());
		info("Mean parallactic angle is " + Util.f1.format(PA / Unit.deg) + " deg.");	
	}
	
	
	@Override
	public Vector2D getPointingCorrection(Configurator option) {
		Vector2D correction = super.getPointingCorrection(option);
		IRAMPointingModel model = null;
		
		if(option.isConfigured("model")) {
			try { 
				double UT = (getMJD() % 1.0) * Unit.day;
				model = new IRAMPointingModel(option.get("model").getPath());
			
				// Keep the pointing model referenced to the nominal array center even if
				// pointing on a different location on the array...
				model.addNasmythOffset(instrument.getPointingCenterOffset());	
				
				if(option.isConfigured("model.static")) model.setStatic(true);
				
				Vector2D modelCorr = model.getCorrection(horizontal, UT, ambientT);
				if(!option.isConfigured("model.incremental")) modelCorr.subtract(observingModel.getCorrection(horizontal, UT, ambientT));	
				
				info("Got pointing from model: " + 
						Util.f1.format(modelCorr.x() / Unit.arcsec) + ", " +
						Util.f1.format(modelCorr.y() / Unit.arcsec) + " arcsec."
				);

				if(correction == null) correction = modelCorr;
				else correction.add(modelCorr);
			}
			catch(IOException e) {
				warning("Cannot read pointing model from " + option.get("model").getValue());
			}
		}
			
		if(option.isConfigured("table")) {
			try { 
				if(model == null) model = new IRAMPointingModel();
				String logName = option.get("table").getPath();
				correction.add(PointingTable.get(logName).getIncrement(getMJD(), ambientT, horizontal, model));
			}
			catch(Exception e) {
				warning("No incremental correction: " + e.getMessage());
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}

		return correction;
		
	}
	
	@Override
	public String getPointingString(Offset2D pointing) {
		String info = super.getPointingString(pointing) + "\n\n";
		
		if(hasOption("pointing.model") || hasOption("pointing.log")) 
			info += "  For PaKo pointing corrections blacklist 'pointing.model' and 'pointing.table'";
		else
			info += "  PaKo> set pointing " + Util.f1.format((pointing.x() + pakoOffsets.x()) / Unit.arcsec) + " " 
				+ Util.f1.format((pointing.y() + pakoOffsets.y()) / Unit.arcsec);
		
		return info;
	}

	public File getFile(String scanDescriptor) throws FileNotFoundException {
		File scanFile;

		String path = getDataPath();
		descriptor = scanDescriptor;

		List<String> endings = null;
		
		if(hasOption("dataname.end")) endings = option("dataname.end").getList();
		else {
			endings = new ArrayList<String>();
			endings.add("GISMO.fits");
		}
			
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
					if(files == null) throw new FileNotFoundException("Not a directory or other I/O error.");
					
					for(int i=files.length; --i >= 0; ) for(String ending : endings)
						if(files[i].endsWith(ending))
							return new File(dirName + File.separator + files[i]);
					
					String message = "Cannot find file with the specified endings in " + dirName +
						"\n    * Check that 'datapath' and 'dataname.end' are correct:" +
						"\n      --> datapath = '" + path + "'" +
						"\n      --> dataname.end = '";
					
					if(hasOption("dataname.end")) message += option("dataname.end").getValue() + "'";
					else message += endings.get(0);
					
					throw new FileNotFoundException(message);
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
		info("Reading " + file.getPath() + "...");
		return new Fits(getFile(scanDescriptor), isCompressed);
	}
	
	public void setVersionOptions(double ver) {
		// Make options an independent set of options, setting version specifics...
		if(!instrument.getOptions().containsKey("ver")) return;
			
		instrument.setOptions(instrument.getOptions().copy());
		fitsVersion = ver;
		
		Hashtable<String, Vector<String>> settings = option("ver").conditionals;
		
		for(String rangeSpec : settings.keySet()) 
			if(Range.parse(rangeSpec, true).contains(fitsVersion)) instrument.getOptions().parseAll(settings.get(rangeSpec));
	}
	
	public void setScanIDOptions(String id) {
		if(!instrument.getOptions().containsKey("id")) return;
	
		// Make options an independent set of options, setting MJD specifics...
		instrument.setOptions(instrument.getOptions().copy());
		double fid = new IRAMScanID(id).asDouble();
		
		Hashtable<String, Vector<String>> settings = option("id").conditionals;
			
		for(String rangeSpec : settings.keySet()) {
			if(IRAMScanID.rangeFor(rangeSpec).contains(fid)) {
				//debug("Setting options for " + rangeSpec);
				instrument.getOptions().parseAll(settings.get(rangeSpec));
			}
		}
	}
	
	protected void read(Fits fits, boolean readFully) throws Exception {
		// Read in entire FITS file
		BasicHDU<?>[] HDU = fits.read();

		if(hasOption("ver")) setVersionOptions(option("ver").getDouble());
		else setVersionOptions(HDU[0].getHeader().getDoubleValue("MRGVER", Double.POSITIVE_INFINITY));
			
		boolean isOldFormat = fitsVersion < 1.7;
		
		skipReconstructed = hasOption("skipfwfix");
		
		if(isOldFormat) {
			instrument.parseOldScanPrimaryHDU(HDU[0]);
			parseOldScanPrimaryHDU(HDU[0]);
			instrument.parseOldHardwareHDU((BinaryTableHDU) HDU[1]);	
		}
		else {
			instrument.parseScanPrimaryHDU(HDU[0]);
			parseScanPrimaryHDU(HDU[0]);
			instrument.parseHardwareHDU((BinaryTableHDU) HDU[1]);
		}
		
		instrument.validate(this);	
		clear();

		GismoIntegration integration = new GismoIntegration(this);
		integration.read((BinaryTableHDU) HDU[2], isOldFormat);
		add(integration);
		
		try { fits.getStream().close(); }
		catch(IOException e) {}
		
		instrument.samplingInterval = integration.instrument.samplingInterval;
		instrument.integrationTime = integration.instrument.integrationTime;
		
	}
	

	
	protected void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
		
		// Load any options based on the FITS header...
		instrument.setFitsHeaderOptions(header);
		
		// Scan Info
		int serial = header.getIntValue("SCANNO");
		
		site = new GeodeticCoordinates(header.getDoubleValue("TELLONGI") * Unit.deg, header.getDoubleValue("TELLATID") * Unit.deg);
		
		// IRAM Pico Veleta PDF 
		//site = new GeodeticCoordinates("-03d23m33.7s, 37d03m58.3s");
		
		// Google maps and Wikipedia...
		//site = new GeodeticCoordinates("-03d23m58.1s, 37d04m05.6s");
		
		// Antenna amymuth axis coordinates from Juan Penalver
		//latitude: N 37d 4m 6.29s
		//longitude: W 3d 23m 55.51s 
		//site = new GeodeticCoordinates("-03d23m55.51s, 37d04m06.29s");
		
		//info("Telescope Location: " + site);
	    
		
		creator = header.getStringValue("CREATOR");
		observer = header.getStringValue("OBSERVER");
		operator = header.getStringValue("OPERATOR");
		project = header.getStringValue("PROJECT");
		descriptor = header.getStringValue("DESCRIPT");
		scanID = header.getStringValue("SCANID");
		obsType = header.getStringValue("OBSTYPE");
		
		if(creator == null) creator = "Unknown";
		if(observer == null) observer = "Unknown";
		if(project == null) project = "Unknown";
		if(obsType == null) obsType = "Unknown";
		
		if(scanID == null) scanID = "Unknown";
		else if(!scanID.equals("undefined")) {
			setScanIDOptions(scanID);
			StringTokenizer tokens = new StringTokenizer(scanID, ".");
			tokens.nextToken(); // Date...
			serial = Integer.parseInt(tokens.nextToken());
		}
		serial = header.getIntValue("SCANNUM", serial);
		
		setSerial(serial);
		
		// Weather
		

		ambientT = header.getDoubleValue("TEMPERAT") * Unit.K + Constant.zeroCelsius;
		pressure = header.getDoubleValue("PRESSURE") * Unit.hPa;
		humidity = header.getDoubleValue("HUMIDITY");
		windAve = header.getDoubleValue("WIND_AVE") * Unit.m / Unit.s;
		windPeak = header.getDoubleValue("WIND_PK") * Unit.m / Unit.s;
		windDirection = header.getDoubleValue("WIND_DIR") * Unit.deg;
	
		// Source Information
		String sourceName = null;
		if(hasOption("object")) sourceName = option("object").getValue();
		else sourceName = header.getStringValue("OBJECT");
		if(sourceName == null) sourceName = descriptor;
		setSourceName(sourceName);
		
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
	
		setMJD(header.getDoubleValue("MJD-OBS"));
				
		// TODO use UTC, TAI, TT offsets to configure AstroTime?...
		/*
		try { setMJD(AstroTime.forFitsTimeStamp(timeStamp).getMJD()); }
		catch(ParseException e) { warning(e); }
		*/

		
		double lon = header.getDoubleValue("LONGOBJ", Double.NaN) * Unit.deg;
		double lat = header.getDoubleValue("LATOBJ", Double.NaN) * Unit.deg;
		
		basisSystem = SphericalCoordinates.getFITSClass(header.getStringValue("CTYPE1"));
		
		LST = header.getDoubleValue("LST", Double.NaN) * Unit.hour;
		
		epoch = hasOption("epoch") ? 
				CoordinateEpoch.forString(option("epoch").getValue()) 
			: new JulianEpoch(header.getDoubleValue("EQUINOX", 2000.0));
		
		if(basisSystem == EquatorialCoordinates.class) {
			equatorial = new EquatorialCoordinates(lon, lat, epoch);	
			calcHorizontal();
			isTracking = true;
		}
		else if(basisSystem == HorizontalCoordinates.class) {
			horizontal = new HorizontalCoordinates(lon, lat);
			calcEquatorial();
			isTracking = false;
		}
		else {
			try { 
				CelestialCoordinates basisCoords = (CelestialCoordinates) basisSystem.newInstance(); 
				basisCoords.set(lon, lat);
				if(basisCoords instanceof Precessing) ((Precessing) basisCoords).setEpoch(epoch);
				equatorial = basisCoords.toEquatorial();
				calcHorizontal();
				isTracking = true;
			}
			catch(Exception e) {
				throw new IllegalStateException("Error instantiating " + basisSystem.getName() +
						": " + e.getMessage());
			}
		}
		
		info("[" + sourceName + "] of project " + project);
		info("Observed on " + date + " at " + startTime + " by " + observer);
		info("Equatorial: " + equatorial.toString());	
		info("Scanning in '" + header.getStringValue("SYSTEMOF") + "'.");
		
		// Figure out how scanning offsets are mean to be used...
		String offsetSystemName = header.getStringValue("SYSTEMOF").toLowerCase();
		projectedOffsets = true;
		if(offsetSystemName.equals("horizontal")) {
			offsetSystem = HorizontalCoordinates.class;
			projectedOffsets = false;
		}
		else if(offsetSystemName.equals("horizontaltrue")) offsetSystem = HorizontalCoordinates.class;
		else if(offsetSystemName.equals("projection")) offsetSystem = EquatorialCoordinates.class;
		else if(offsetSystemName.equals("equatorial")) offsetSystem = EquatorialCoordinates.class;
		else throw new IllegalStateException("Offset system '" + offsetSystemName + "' is not implemented.");
		
		info("Angles are " + (projectedOffsets ? "" : "not ") + "projected");
		
		// NOT Used...
		//isPlanetary = header.getBooleanValue("MOVEFRAM", false);
		
		// parse the static offsets
		for(int n = 0; ; n++) {
			if(!header.containsKey("SYSOFF" + n)) break;
			
			String type = header.getStringValue("SYSOFF" + n);
			
			Vector2D offset = new Vector2D(
					header.getDoubleValue("XOFFSET" + n),
					header.getDoubleValue("YOFFSET" + n)
			);
			
			CRUSH.values(this, type + " offset --> " + Vector2D.toString(offset, Unit.get("arcsec"), 1));
			
			type = type.toLowerCase();
			
			if(type.equals("nasmyth")) nasmythOffset = offset;
			else if(type.equals("truehorizontal")) horizontalOffset = offset;
			else if(type.equals("projection")) equatorialOffset = offset;
			// The following are not yet implemented in PAKO (as of 2012-03).
			else if(type.equals("equatorial")) equatorialOffset = offset;
			else if(type.equals("basis")) basisOffset = offset;
		}
		
		
		// Tau
		tau225GHz = Double.NaN;
		
		if(hasOption("tau.225ghz")) {
			try { tau225GHz = option("tau.225ghz").getDouble(); }
			catch(NumberFormatException e) {
				try {
					String timeZone = hasOption("tau.timezone") ? option("tau.timezone").getValue() : "UTC";
					IRAMTauTable table = IRAMTauTable.get(option("tau.225ghz").getPath(), timeZone);
					if(hasOption("tau.window")) table.timeWindow = option("tau.window").getDouble() * Unit.hour;
					tau225GHz = table.getTau(getMJD());
					instrument.getOptions().processSilent("tau.225ghz", tau225GHz + "");
				}
				catch(IOException e2) {
					warning("Cannot read tau table: " + e2.getMessage());
					if(CRUSH.debug) CRUSH.trace(e2); 
					tau225GHz = Double.NaN;
				}
			}
		}
		
		if(Double.isNaN(tau225GHz)) {
			tau225GHz = header.getDoubleValue("TAU225GH");
			instrument.getOptions().processSilent("tau.225ghz", tau225GHz + "");
		}
	
		
		// Read the effective pointing model
		// Static constant *AND* tilt-meter corrections...
		observingModel = new IRAMPointingModel();
		tiltCorrections = new IRAMPointingModel();
				
		for(int i=1; i<=9; i++) {
			observingModel.P[i] = (header.getDoubleValue("PCONST" + i, 0.0) + header.getDoubleValue("P" + i + "COR", 0.0)) / Unit.arcsec;			
			tiltCorrections.P[i] = header.getDoubleValue("P" + i + "CORINC", 0.0) / Unit.arcsec;
		}
	
		// The pointing offsets entered into PaKo
		pakoOffsets = new Vector2D(header.getDoubleValue("P2COR"), header.getDoubleValue("P7COR"));
		
		// IRAM Nasmyth offsets are inverted from CRUSH definition... Ooops...
		observingModel.P[10] = -(header.getDoubleValue("RXHORI", 0.0) - header.getDoubleValue("RXHORICO", 0.0)) / Unit.arcsec;
		observingModel.P[11] = -(header.getDoubleValue("RXVERT", 0.0) - header.getDoubleValue("RXVERTCO", 0.0)) / Unit.arcsec;
	
		// Add in the PaKo pointing offsets... 
		// XOFFSET, and YOFFSET are the same as the Nasmyth offsets!!!
		
		// Works with 2011 April data. 
		if(nasmythOffset != null) {
			observingModel.P[10] -= nasmythOffset.x() / Unit.arcsec;
			observingModel.P[11] -= nasmythOffset.y() / Unit.arcsec;
		}	
		
		// Keep the pointing model referenced to the nominal array center even if
		// pointing on a different location on the array...
		observingModel.addNasmythOffset(instrument.getPointingCenterOffset());
		
		isTracking = true;
	}
	
	protected void parseOldScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
	
		// Load any options based on the FITS header...
		instrument.setFitsHeaderOptions(header);
		
		// Scan Info
		int serial = header.getIntValue("SCANNO");
		
		//site = new GeodeticCoordinates(header.getDoubleValue("TELLONGI") * Unit.deg, header.getDoubleValue("TELLATID") * Unit.deg);
		
		// IRAM Pico Veleta PDF 
		//site = new GeodeticCoordinates("-03d23m33.7s, 37d03m58.3s");
		
		// Google maps and Wikipedia...
		//site = new GeodeticCoordinates("-03d23m58.1s, 37d04m05.6s");
		
		// Antenna azimuth axis coordinates from Juan Penalver
		//latitude: N 37d 4m 6.29s
		//longitude: W 3d 23m 55.51s 
		site = new GeodeticCoordinates("-03d23m55.51s, 37d04m06.29s");
		
		  
        //info("Telescope Location: " + site);
    
		
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
		else if(!scanID.equals("undefined")) {
			setScanIDOptions(scanID);
			StringTokenizer tokens = new StringTokenizer(scanID, ".");
			tokens.nextToken(); // Date...
			serial = Integer.parseInt(tokens.nextToken());
		}
		serial = header.getIntValue("SCANNUM", serial);
		
		setSerial(serial);
		
		// Weather
		ambientT = header.getDoubleValue("TEMPERAT") * Unit.K + Constant.zeroCelsius;
		pressure = header.getDoubleValue("PRESSURE") * Unit.hPa;
		humidity = header.getDoubleValue("HUMIDITY");
		windAve = header.getDoubleValue("WIND_AVE") * Unit.m / Unit.s;
		windPeak = header.getDoubleValue("WIND_PK") * Unit.m / Unit.s;
		windDirection = header.getDoubleValue("WIND_DIR") * Unit.deg;
	
		// Source Information
		String sourceName = null;
		if(hasOption("object")) sourceName = option("object").getValue();
		else sourceName = header.getStringValue("OBJECT");
		if(sourceName == null) sourceName = descriptor;
		setSourceName(sourceName);
		
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
		
		epoch = hasOption("epoch") ? 
				CoordinateEpoch.forString(option("epoch").getValue()) 
			: new JulianEpoch(header.getDoubleValue("EQUINOX"));
		
		
		equatorial = new EquatorialCoordinates(
				header.getDoubleValue("RAEP") * Unit.hourAngle,
				header.getDoubleValue("DECEP") * Unit.deg,
				epoch);
	
		try { setMJD(AstroTime.forFitsTimeStamp(timeStamp).getMJD()); }
		catch(ParseException e) { warning(e); }
	
		
		info(" [" + sourceName + "] observed on " + date + " at " + startTime + " by " + observer);
		info(" Equatorial: " + equatorial.toString());	
		info(" Scanning in '" + header.getStringValue("SYSTEMOF") + "'.");
		
		// Tau
		tau225GHz = Double.NaN;
		
		if(hasOption("tau.225ghz")) {
			try { tau225GHz = option("tau.225ghz").getDouble(); }
			catch(NumberFormatException e) {
				try {
					String timeZone = hasOption("tau.timezone") ? option("tau.timezone").getValue() : "UTC";
					IRAMTauTable table = IRAMTauTable.get(option("tau.225ghz").getPath(), timeZone);
					if(hasOption("tau.window")) table.timeWindow = option("tau.window").getDouble() * Unit.hour;
					tau225GHz = table.getTau(getMJD());
					instrument.getOptions().processSilent("tau.225ghz", tau225GHz + "");
				}
				catch(IOException e2) { 
					warning("Cannot read tau table.");
					if(CRUSH.debug) CRUSH.trace(e); 
					tau225GHz = Double.NaN;
				}
			}
		}
		
		// Read the effective pointing model
		// Static constant *AND* tilt-meter corrections...
		observingModel = new IRAMPointingModel();
		tiltCorrections = new IRAMPointingModel();
		for(int i=1; i<=9; i++) {
			observingModel.P[i] = (header.getDoubleValue("PCONST" + i, 0.0) + header.getDoubleValue("P" + i + "COR", 0.0)) / Unit.arcsec;			
			tiltCorrections.P[i] = header.getDoubleValue("P" + i + "CORINC", 0.0) / Unit.arcsec;
		}

		observingModel.P[10] = (header.getDoubleValue("RXHORI", 0.0) + header.getDoubleValue("RXHORICO", 0.0)) / Unit.arcsec;
		observingModel.P[11] = (header.getDoubleValue("RXVERT", 0.0) + header.getDoubleValue("RXVERTCO", 0.0)) / Unit.arcsec;
	
		isTracking = true;
		
	}
	
	
	@Override
	public double getAmbientHumidity() {
		return humidity;
	}

	@Override
	public double getAmbientPressure() {
		return pressure;
	}

	@Override
	public double getAmbientKelvins() {
		return ambientT;
	}

	@Override
	public double getWindDirection() {
		return windDirection;
	}

	@Override
	public double getWindPeak() {
		return windPeak;
	}

	@Override
	public double getWindSpeed() {
		return windAve;
	}

	@Override
	public String getID() {
		return scanID;
	}
	
	@Override
	public DataTable getPointingData() {
		DataTable data = super.getPointingData();

		Offset2D relative = getNativePointingIncrement(pointing);
		Vector2D nasmyth = getNasmythOffset(relative);
		
		Unit sizeUnit = instrument.getSizeUnit();
	
		// X and Y are absolute pointing offsets including the static pointing model...
		Vector2D obs = observingModel.getCorrection(horizontal, (getMJD() % 1.0) * Unit.day, ambientT);
		if(pointingCorrection != null) obs.add(pointingCorrection);
			
		data.new Entry("X", (relative.x() + obs.x()), sizeUnit);
		data.new Entry("Y", (relative.y() + obs.y()), sizeUnit);
		data.new Entry("NasX", (instrument.nasmythOffset.x() + nasmyth.x()), sizeUnit);
		data.new Entry("NasY", (instrument.nasmythOffset.y() + nasmyth.y()), sizeUnit);
		return data;
	}
	
	// 2012-03-06 Conforms to IRAM pointing model convention (P11/P12)
	@Override
	public Vector2D getNasmythOffset(Offset2D nativeOffset) {
		Vector2D nasmythOffset = super.getNasmythOffset(nativeOffset);
		return nasmythOffset;
	}
	
	
	@Override
	public Object getTableEntry(String name) {	
		if(name.equals("obstype")) return obsType;
		else if(name.equals("modelX")) return observingModel.getDX(horizontal, (getMJD() % 1) * Unit.day, ambientT);
		else if(name.equals("modelY")) return observingModel.getDY(horizontal, (getMJD() % 1) * Unit.day, ambientT);
		else if(name.equals("tiltX")) return tiltCorrections.getDX(horizontal, (getMJD() % 1) * Unit.day, ambientT);
		else if(name.equals("tiltY")) return tiltCorrections.getDY(horizontal, (getMJD() % 1) * Unit.day, ambientT);
		else if(name.equals("dir")) return AstroSystem.getID(basisSystem);
		else return super.getTableEntry(name);
	}

	@Override
	public void editScanHeader(Header header) throws HeaderCardException {	
		super.editScanHeader(header);
		Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
		c.add(new HeaderCard("PROJECT", project, "The project ID for this scan"));
		if(basisSystem != null) c.add(new HeaderCard("BASIS", basisSystem.getSimpleName(), "The coordinates system of the scan."));
		
	}
	
	// IRAM's x and y focus directions are inverted, x being vertical, y being horizontal...
	@Override
	protected String getFocusString(Asymmetry2D asym, DataPoint elongation) {
		Asymmetry2D a2 = new Asymmetry2D();
		a2.setX(asym.getY());
		a2.setY(asym.getX());
		return super.getFocusString(a2, elongation);
	}
	
}
