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

package crush.scuba2;

import crush.*;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroSystem;
import jnum.astro.AstroTime;
import jnum.astro.CoordinateEpoch;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.GalacticCoordinates;
import jnum.astro.GeodeticCoordinates;
import jnum.astro.HorizontalCoordinates;
import jnum.astro.JulianEpoch;
import jnum.astro.Weather;
import jnum.math.SphericalCoordinates;
import nom.tam.fits.*;

import java.io.*;
import java.util.*;


public class Scuba2Scan extends Scan<Scuba2, Scuba2Subscan> implements GroundBased, Weather {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1608680718251250629L;

	String date, endTime;
	String obsMode, scanPattern, obsType;
	int iDate;
	
	double tau225GHz, tau183GHz;
	double ambientT, pressure, humidity, windAve, windPeak, windDirection;
	String scanID;
	int blankingValue;
	
	double dUT1 = 0.0;
	
	boolean[] hasSubarray = new boolean[Scuba2.SUBARRAYS];
	int subarrays;
	
	public Class<? extends SphericalCoordinates> trackingClass;
	
	public Scuba2Scan(Scuba2 instrument) {
		super(instrument);
	}
	
	@Override
	public Scuba2Subscan getIntegrationInstance() {
		return new Scuba2Subscan(this);
	}

	public Scuba2Fits getFirstFits() {
		return getFirstIntegration().files.get(0);
	}
	

	
	@Override
	public void mergeIntegrations() {
		calcSamplingRate();
		
		super.mergeIntegrations();
		
		for(int i=size(); --i >= 0; ) {
			Scuba2Subscan subscan = get(i);
			subscan.integrationNo = i;
			subscan.instrument.integrationTime = instrument.integrationTime;
			subscan.instrument.samplingInterval = instrument.samplingInterval;
		}
	}
	
	
	@Override
	public void read(String scanDescriptor, boolean readFully) throws Exception {
		ArrayList<Scuba2Fits> files = getFitsFiles(scanDescriptor);
		if(files.isEmpty()) return;
		
		// Check fow which subarrays there is data in the dataset...
		Arrays.fill(hasSubarray, false);
		for(Scuba2Fits file : files) hasSubarray[file.getSubarrayIndex()] = true;
		
		// Sort by subscan and then by subarray index
		Collections.sort(files);
		
		BasicHDU<?> mainHDU = files.get(0).getHDUs()[0];
		parseScanPrimaryHDU(mainHDU);
		((Scuba2) instrument).addPixelsFor(hasSubarray);
		instrument.parseScanPrimaryHDU(mainHDU);
		instrument.validate(this);	
		
		// Count how many subarrays are active
		subarrays = 0;
		int channelOffset = 0;
		for(int i=0; i<hasSubarray.length; i++) if(hasSubarray[i]) {
			subarrays++;
			instrument.subarray[i].channelOffset = channelOffset;
			channelOffset += Scuba2Subarray.PIXELS;
		}
		info("Found data for " + subarrays + " subarrays.");
		
		clear();
		
		// Read all the files for a given subscan...
		Scuba2Subscan subscan = new Scuba2Subscan(this);
		subscan.integrationNo = files.get(0).getSubscanNo();
		
		for(int i=0; i<files.size(); i++) {
			Scuba2Fits file = files.get(i);
			int subscanNo = file.getSubscanNo();
	
			if(subscanNo != subscan.integrationNo) {
				addSubscan(subscan);
				
				subscan = new Scuba2Subscan(this);
				subscan.integrationNo = subscanNo;
			}
			subscan.files.add(file);
		}
		addSubscan(subscan);
		
		Collections.sort(this);
	}
	
	private void addSubscan(Scuba2Subscan subscan) throws FitsException {
		if(subscan.files.isEmpty()) return;
		try { 
			subscan.read(); 
			if(!subscan.isEmpty()) add(subscan);	
		}
		catch(DarkSubscanException e) { subscan.info("Subscan " + subscan.getID() + " is a dark measurement. Skipping."); }
		catch(FastFlatSubscanException e) { subscan.info("Subscan " + subscan.getID() + " is a flatfield measurement. Skipping."); }
		catch(NoiseSubscanException e) { subscan.info("Subscan " + subscan.getID() + " is a noise measurement. Skipping."); }
		catch(UnsupportedIntegrationException e) {  subscan.info("Subscan " + subscan.getID() + " is not supported. Skipping."); }
		catch(IllegalStateException e) { subscan.warning(e); }
		catch(IOException e) { subscan.warning("FITS was not be closed."); }
	}
	
	
	private void calcSamplingRate() {
		double totalTime = 0.0;
		int nFrames = 0;
		for(Scuba2Subscan subscan : this) {
			totalTime += subscan.totalIntegrationTime;
			nFrames += subscan.rawFrames;
		}
		instrument.samplingInterval = instrument.integrationTime = totalTime / nFrames;
	
	}
		
	@Override
	public void validate() {
		if(isEmpty()) return;	// TODO warning?
		
		calcSamplingRate();
		info("Typical sampling rate is " + Util.f2.format(1.0 / instrument.integrationTime) + " Hz.");
	
		
		Scuba2Frame firstFrame = getFirstIntegration().getFirstFrame();
		Scuba2Frame lastFrame = getLastIntegration().getLastFrame();

		if(horizontal == null) {
			horizontal = new HorizontalCoordinates();
			horizontal.setLongitude(0.5*(firstFrame.horizontal.x() + lastFrame.horizontal.x()));
			horizontal.setLatitude(0.5*(firstFrame.horizontal.y() + lastFrame.horizontal.y()));
		}
			
		double PA = 0.5 * (firstFrame.getParallacticAngle() + lastFrame.getParallacticAngle());
		info("   Mean parallactic angle is " + Util.f1.format(PA / Unit.deg) + " deg.");
		
		super.validate();
	}

	public ArrayList<Scuba2Fits> getFitsFiles(String scanDescriptor) throws FileNotFoundException{
		List<String> subIDs = hasOption("subarray") ? option("subarray").getList() : null;
		
		ArrayList<Scuba2Fits> files = null;
		
		if(subIDs == null) files = getFitsFiles(scanDescriptor, '*');
		else {
			files = new ArrayList<Scuba2Fits>();
			for(String id : subIDs) files.addAll(getFitsFiles(scanDescriptor, id.toLowerCase().charAt(0)));
		}
		return files;
	}
	
	public ArrayList<Scuba2Fits> getFitsFiles(String scanDescriptor, char subarrayCode) throws FileNotFoundException {
		ArrayList<Scuba2Fits> scanFiles = new ArrayList<Scuba2Fits>();

		String path = getDataPath();
		descriptor = scanDescriptor;

		String prefix = hasOption("450um") ? "s4" : "s8";
		if(subarrayCode != '*') prefix += subarrayCode;
		
		// Try to read scan number with the help of 'object' and 'date' keys...
		try {
			String scanNo = Util.d5.format(Integer.parseInt(scanDescriptor));
			if(hasOption("date")) {
				File directory = new File(path);
				String date = option("date").getValue().replaceAll("-", "");
				String scanID = date + "_" + scanNo + "_";
				
				if(!directory.exists()) {
					String message = "Cannot find scan directory " + path +
						"\n    * Check that 'datapath' is correct:" + 
						"\n      --> datapath = '" + option("datapath").getValue() + "'";
					throw new FileNotFoundException(message);
				}
				else if(!directory.isDirectory()) {
					throw new FileNotFoundException(path + " is not a directory.");
				}
				else {
					String[] files = directory.list();
					
					for(int i=0; i<files.length; i++) if(files[i].startsWith(prefix)) if(files[i].substring(3).startsWith(scanID)) {
						try { scanFiles.add(new Scuba2Fits(path + File.separator + files[i], instrument.getOptions())); }
						catch(IllegalStateException e) { 
							// there is a FITS alternative already...
						}
						catch(Exception e) { warning(e); }
					}
						
					if(scanFiles.isEmpty()) 
						throw new FileNotFoundException("Cannot find matching files in " + path);
				}
			}
			else {
				String message = "Cannot find scan " + scanDescriptor;

				if(!hasOption("date")) message += "\n    * Specify 'date' for unique JCMT scan ID.";
			
				throw new FileNotFoundException(message);
			}
		}
		
		// Otherwise, just read as file names...
		catch(NumberFormatException e) {
			File scanFile = new File(scanDescriptor) ;	
			
			if(!scanFile.exists()) {
				scanFile = new File(path + scanDescriptor);
				if(!scanFile.exists()) throw new FileNotFoundException("Could not find scan " + scanDescriptor);
			} 	
			
			try { scanFiles.add(new Scuba2Fits(scanFile.getPath(), instrument.getOptions())); }
			catch(IllegalStateException e2) {
				// There is a FITS alternative already...
			}
			catch(Exception e2) { warning(e2); }
		}

		
		return scanFiles;
	}	
	

	
	protected void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException, FitsException, UnsupportedScanException {
		Header header = hdu.getHeader();
		
		// Load any options based on the FITS header...
		instrument.setFitsHeaderOptions(header);
		
		// Scan Info
		setSerial(header.getIntValue("OBSNUM"));
		if(instrument.getOptions().containsKey("serial")) instrument.setSerialOptions(getSerial());
	
		site = new GeodeticCoordinates(header.getDoubleValue("LONG-OBS") * Unit.deg, header.getDoubleValue("LAT-OBS") * Unit.deg);
		creator = header.getStringValue("ORIGIN");
		project = header.getStringValue("PROJECT");
		
		if(creator == null) creator = "Unknown";
		if(observer == null) observer = "Unknown";
		if(project == null) project = "Unknown";
		
		setSourceName(header.getStringValue("OBJECT"));
		date = header.getStringValue("DATE-OBS");
		endTime = header.getStringValue("DATE-END");
		if(date == null) date = endTime;
		
		iDate = header.getIntValue("UTDATE");
		scanID = header.getStringValue("OBSID");
		
		dUT1 = header.getDoubleValue("DUT1") * Unit.day;
		
		AstroTime timeStamp = new AstroTime();
		try { 
			timeStamp.parseFitsDate(date); 
			setMJD(timeStamp.getMJD());
		}
		catch(Exception e) { warning("Could not parse DATE-OBS..."); }
		
		blankingValue = header.getIntValue("BLANK");
		
		info("[" + getSourceName() + "] observed on " + date);
		
		obsType = header.getStringValue("OBS_TYPE");	
		if(obsType.equalsIgnoreCase("focus")) throw new UnsupportedScanException("Focus reduction not (yet) implemented.");
		
		// Weather
		if(hasOption("tau.183ghz")) tau183GHz = option("tau.183ghz").getDouble();
		else {
			tau183GHz = 0.5 * (header.getDoubleValue("WVMTAUST") + header.getDoubleValue("WVMTAUEN"));
			instrument.setOption("tau.183ghz=" + tau183GHz);
		}
		
		if(hasOption("tau.225ghz")) tau225GHz = option("tau.225ghz").getDouble();
		else {
			tau225GHz = 0.5 * (header.getDoubleValue("TAU225ST") + header.getDoubleValue("TAU225EN"));
			instrument.setOption("tau.225ghz=" + tau225GHz);
		}
		
		ambientT = 0.5 * (header.getDoubleValue("ATSTART") + header.getDoubleValue("ATEND")) * Unit.K + 273.16 * Unit.K;
		pressure = 0.5 * (header.getDoubleValue("BPSTART") + header.getDoubleValue("BPEND")) * Unit.mbar;
		humidity = 0.5 * (header.getDoubleValue("HUMSTART") + header.getDoubleValue("HUMEND"));
		windAve = 0.5 * (header.getDoubleValue("WINDSPDST") + header.getDoubleValue("WINDSPDEN")) * Unit.km / Unit.hour;
		windDirection = 0.5 * (header.getDoubleValue("WINDDIRST") + header.getDoubleValue("WINDDIREN")) * Unit.deg;
		
		obsMode = header.getStringValue("SAM_MODE");
		// TODO + Switching mode
		// TODO + Chopper, jiggler parameters
		// TODO + Scan details
		scanPattern = header.getStringValue("SCAN_PAT");
		
		isTracking = true;		
	}
	
	public void parseCoordinateInfo(Header header) {
		String trackingSystem = header.getStringValue("TRACKSYS");
		
		final double lon = header.getDoubleValue("BASEC1", Double.NaN) * Unit.deg;
		final double lat = header.getDoubleValue("BASEC2", Double.NaN) * Unit.deg;
		
		if(trackingSystem == null) {
			trackingClass = null;
			return;
		}
		else isTracking = true;
		
		if(trackingSystem.equals("AZEL")) {
			trackingClass = HorizontalCoordinates.class;
			horizontal = new HorizontalCoordinates(lon, lat);
			info("Horizontal: " + horizontal.toString(1));
			isTracking = false;
		}
		else if(trackingSystem.equals("APP")) {
			trackingClass = EquatorialCoordinates.class;
			apparent = new EquatorialCoordinates(lon, lat, JulianEpoch.forMJD(getMJD()));
			equatorial = (EquatorialCoordinates) apparent.clone();
			equatorial.precess(CoordinateEpoch.J2000);
			info("Apparent: " + apparent.toString(1));
			info("Equatorial: " + equatorial.toString(1));
		}
		else if(trackingSystem.equals("J2000")) {
			trackingClass = EquatorialCoordinates.class;
			equatorial = new EquatorialCoordinates(lon, lat, CoordinateEpoch.J2000);
			info("Equatorial: " + equatorial.toString(1));
		}
		else if(trackingSystem.equals("B1950")) {
			trackingClass = EquatorialCoordinates.class;
			equatorial = new EquatorialCoordinates(lon, lat, CoordinateEpoch.B1950);
			info("Equatorial: " + equatorial.toString(1));
		}
		else if(trackingSystem.equals("GAL")) {
			trackingClass = GalacticCoordinates.class;
			GalacticCoordinates galactic = new GalacticCoordinates(lon, lat);
			equatorial = galactic.toEquatorial();
			info("Galactic: " + galactic.toString(1));
			info("Equatorial: " + equatorial.toString(1));
		}
		else {
			trackingClass = null;
			warning("Unsupported tracking system: " + trackingSystem);
		}
		
		// GAPPT ?
		
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
	public double getAmbientTemperature() {
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
		return iDate + "." + getSerial();
	}
	
	@Override
	public void setSourceModel(SourceModel model) {
		super.setSourceModel(model);
		sourceModel.id = instrument.filter;
	}	
	
	@Override
	public void editScanHeader(Header header) throws HeaderCardException {	
		super.editScanHeader(header);
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		if(name.equals("obsmode")) return obsMode;
		else if(name.equals("obstype")) return obsMode;
		else if(name.equals("obspattern")) return scanPattern;
		else if(name.equals("dir")) return AstroSystem.getID(trackingClass);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
}
