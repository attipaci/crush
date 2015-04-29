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

package crush.sofia;

import crush.*;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

import java.io.*;
import java.text.*;
import java.util.*;

import kovacs.astro.*;
import kovacs.math.Vector2D;
import kovacs.util.*;


public abstract class SofiaScan<InstrumentType extends SofiaCamera<?>, IntegrationType extends SofiaIntegration<InstrumentType, ?>> 
extends Scan<InstrumentType, IntegrationType> implements GroundBased, Weather {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6344037367939085571L;
	
	public String date;
	public BracketedValues utc = new BracketedValues();
	public boolean isChopping = false, isNodding = false, isDithering = false, isMapping = false, isScanning = false;
		
	Vector<String> history = new Vector<String>();
	
	public SofiaObservationData observation;
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
	
	
	public SofiaScan(InstrumentType instrument) {
		super(instrument);
	}


	@Override
	public void read(String scanDescriptor, boolean readFully) throws Exception {
		read(getFits(scanDescriptor), readFully);
	}

	public void parseHeader(Header header) throws Exception {
		// Load any options based on the FITS header...
		instrument.setFitsHeaderOptions(header);
		
		date = header.getStringValue("DATE-OBS");
		String startTime = header.getStringValue("UTCSTART");
		String endTime = header.getStringValue("UTCEND");
		
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
		time.parseFitsTimeStamp(timeStamp);
		setMJD(time.getMJD());	
		
		isChopping = header.getBooleanValue("CHOPPING", false);
		isNodding = header.getBooleanValue("NODDING", false);
		isDithering = header.getBooleanValue("DITHER", false);
		isMapping = header.getBooleanValue("MAPPING", false);
		isScanning = header.getBooleanValue("SCANNING", false);
		
		observation = new SofiaObservationData(header);
		setSourceName(observation.sourceName);
		project = observation.aorID;
		descriptor = observation.obsID;
		
		processing = new SofiaProcessingData(header);
		mission = new SofiaMissionData(header);
		
		origin = new SofiaOriginationData(header);
		creator = origin.creator;
		observer = origin.observer;
		
		environment = new SofiaEnvironmentData(header);	
		if(!hasOption("tau.pwv")) instrument.getOptions().parse("tau.pwv " + environment.pwv.midPoint());
		
		aircraft = new SofiaAircraftData(header);
		// Calculate the mean geodetic site of the observation
		site = new GeodeticCoordinates(aircraft.longitude.midPoint(), aircraft.latitude.midPoint());
		
		telescope = new SofiaTelescopeData(header);
		equatorial = (EquatorialCoordinates) telescope.requestedEquatorial.copy();	
		calcPrecessions(telescope.requestedEquatorial.epoch);
		
		isTracking = telescope.isTracking();

		System.err.println(" [" + getSourceName() + "] of AOR " + observation.aorID);
		System.err.println(" Observed on " + date + " at " + startTime + " by " + observer);
		System.err.println(" Equatorial: " + telescope.requestedEquatorial.toString());	
		
		instrument.parseHeader(header);
		
		if(isChopping) chopper = new SofiaChopperData(header);
		if(isNodding) nodding = new SofiaNoddingData(header);
		if(isDithering) dither = new SofiaDitheringData(header);
		if(isMapping) mapping = new SofiaMappingData(header);
		if(isScanning) scanning = new SofiaScanningData(header);	
		
		parseHistory(header);
	}
	
	@Override
	public void editScanHeader(Header header) throws HeaderCardException {
		super.editScanHeader(header);		
		Header sofiaHeader = new Header();
		
		Cursor cursor = sofiaHeader.iterator();
		while(cursor.hasNext()) cursor.next();
		
		observation.editHeader(cursor);
		processing.editHeader(cursor);
		mission.editHeader(cursor);
		origin.editHeader(cursor);
		environment.editHeader(cursor);
		aircraft.editHeader(cursor);
		telescope.editHeader(cursor);
		
		cursor.add(new HeaderCard("CHOPPING", isChopping, "Was chopper in use?"));	
		cursor.add(new HeaderCard("NODDING", isNodding, "Was nodding used?"));	
		cursor.add(new HeaderCard("DITHER", isDithering, "Was dithering used?"));	
		cursor.add(new HeaderCard("MAPPING", isMapping, "Was mapping?"));	
		cursor.add(new HeaderCard("SCANNING", isScanning, "Was scanning?"));
		
		if(chopper != null) chopper.editHeader(cursor);
		if(nodding != null) nodding.editHeader(cursor);
		if(dither != null) dither.editHeader(cursor);
		if(mapping != null) mapping.editHeader(cursor);
		if(scanning != null) scanning.editHeader(cursor);
		
		instrument.editHeader(cursor);
					
		//cursor.add(new HeaderCard("PROCSTAT", "LEVEL_" + level, SofiaProcessingData.getComment(level)));
		//cursor.add(new HeaderCard("HEADSTAT", "UNKNOWN", "See original header values in the scan HDUs."));
		//cursor.add(new HeaderCard("PIPELINE", "crush v" + CRUSH.getReleaseVersion(), "Software that produced this file."));
		//cursor.add(new HeaderCard("PIPEVERS", CRUSH.getFullVersion(), "Full software version information.")); 
		//cursor.add(new HeaderCard("PRODTYPE", "CRUSH-SCAN-META", "Type of product produced by the software."));
		
	
		// May overwrite existing values...
		header.updateLines(sofiaHeader);
		
	}

	
	public ArrayList<String> getPreservedKeys() {
		ArrayList<String> keys = new ArrayList<String>(requiredKeys.length);
		for(String key : requiredKeys) keys.add(key.toUpperCase());
		
		// Add any keys
		if(hasOption("fits.addkeys")) for(String key : option("fits.addkeys").getList()) {
			key = key.toUpperCase();
			if(!keys.contains(key)) keys.add(key);			
		}
		return keys;
	}
	
	public void addRequiredKeys(Cursor cursor) throws HeaderCardException {
		Header h = new Header();
		editScanHeader(h);
		
		for(String key : getPreservedKeys()) {
			HeaderCard fromCard = h.findCard(key);
			
			cursor.setKey(key);
		
			if(fromCard == null) continue;
				
			HeaderCard toCard = cursor.hasNext() ? (HeaderCard) cursor.next() : null;	
			if(toCard != null) toCard.setValue(fromCard.getValue());
			else cursor.add(fromCard);
		}
		
		
		// Copy the subarray specs (if defined)
		if(instrument.array.subarrays > 0) for(int i=0; i<instrument.array.subarrays; i++) {
			String key = "SUBARR" + Util.d2.format(i);
			cursor.setKey(key);
			HeaderCard card = cursor.hasNext() ? (HeaderCard) cursor.next() : null;
			if(card != null) card.setValue(h.findCard(key).getValue());
			else cursor.add(card);
		}
		
		// Add the observing mode keywords at the end...
		while(cursor.hasNext()) cursor.next();
		
		// Add the observing mode keywords at the end...
		if(chopper != null) chopper.editHeader(cursor);
		if(nodding != null) nodding.editHeader(cursor);
		if(dither != null) dither.editHeader(cursor);
		if(mapping != null) mapping.editHeader(cursor);
		if(scanning != null) scanning.editHeader(cursor);
	
		
		cursor.setKey("HISTORY");
		for(int i=0; i<history.size(); i++) cursor.add(new HeaderCard("HISTORY", history.get(i), false));
	
	}
	
	public void parseHistory(Header header) {
		history.clear();
		
		Cursor cursor = header.iterator();
		
		while(cursor.hasNext()) {
			HeaderCard card = (HeaderCard) cursor.next();
			if(card.getKey().equalsIgnoreCase("HISTORY")) {
				String comment = card.getComment();
				if(comment != null) history.add(comment);
			}
		}

		if(!history.isEmpty()) {
			System.err.println(" Processing History: " + history.size() + " entries found.");
			System.err.println("   --> Last: " + history.get(history.size() - 1));
		}
			
		//for(int i=0; i<history.size(); i++) System.err.println("#  " + history.get(i));
	}
	
	
	public void addHistory(Header header) throws FitsException {
		header.findCard("HISTORY");
		for(int i=0; i<history.size(); i++) header.insertHistory(history.get(i));		
	}
	

	
	@Override
	public void validate() {	
		super.validate();	
		
		double PA = 0.5 * (getFirstIntegration().getFirstFrame().getParallacticAngle() + getLastIntegration().getLastFrame().getParallacticAngle());
		System.err.println("   Mean parallactic angle is " + Util.f1.format(PA / Unit.deg) + " deg.");	
	}
	
	
	public File getFile(String scanDescriptor) throws FileNotFoundException {
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
		System.out.println(" Reading " + file.getPath() + "...");
		return new Fits(getFile(scanDescriptor), isCompressed);
	}
	
	
	protected void read(Fits fits, boolean readFully) throws Exception {	
		parseHeader(fits.getHDU(0).getHeader());
		
		instrument.readData(fits);
		instrument.validate(this);	
		clear();

		IntegrationType integration = getIntegrationInstance();
		integration.readData(fits);
		add(integration);
		
		try { fits.getStream().close(); }
		catch(IOException e) {}
		
		instrument.samplingInterval = integration.instrument.samplingInterval;
		instrument.integrationTime = integration.instrument.integrationTime;
		
		validate();
	}
	
	@Override
	public double getAmbientHumidity() {
		return Double.NaN;
	}

	@Override
	public double getAmbientPressure() {
		return Double.NaN;
	}

	@Override
	public double getAmbientTemperature() {
		return environment.ambientT;
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
	public int compareTo(Scan<?, ?> scan) {
		try {
			int dateComp = dateFormat.parse(date).compareTo(dateFormat.parse(((SofiaScan<?,?>) scan).date));
			if(dateComp != 0) return dateComp;
			if(getSerial() == scan.getSerial()) return 0;
			return getSerial() < scan.getSerial() ? -1 : 1;
		}
		catch(ParseException e) {
			System.err.println("WARNING! Cannot parse date: '" + date + "' or '" + ((SofiaScan<?, ?>) scan).date + "'.");
			return 0;
		}	
	}
	
	@Override
	public String getID() {
		return observation.obsID;
	}
	
	@Override
	public DataTable getPointingData() {
		DataTable data = super.getPointingData();

		Vector2D pointingOffset = getNativePointingIncrement(pointing);
		//Vector2D nasmyth = getNasmythOffset(pointingOffset);
		
		double sizeUnit = instrument.getSizeUnitValue();
		String sizeName = instrument.getSizeName();
		
		data.new Entry("X", pointingOffset.x() / sizeUnit, sizeName);
		data.new Entry("Y", pointingOffset.y() / sizeUnit, sizeName);
		//data.new Entry("NasX", (instrument.nasmythOffset.x() + nasmyth.x()) / sizeUnit, sizeName);
		//data.new Entry("NasY", (instrument.nasmythOffset.y() + nasmyth.y()) / sizeUnit, sizeName);
		return data;
	}
	
	@Override
	public Vector2D getNasmythOffset(Vector2D nativeOffset) {
		Vector2D nasmythOffset = super.getNasmythOffset(nativeOffset);
		return nasmythOffset;
	}
	
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		//NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
		
		// TODO Add Sofia Header data...
		if(name.equals("obstype")) return observation.obsType;
		else return super.getFormattedEntry(name, formatSpec);
	}

	
	
	public static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	
	public final static String[] requiredKeys = { 
			"DATASRC", "OBS_ID", "IMAGEID", "AOT_ID", "AOR_ID", "PLANID", "MISSN-ID", "DATE-OBS", 
			"TRACMODE", "TRACERR", "CHOPPING", "NODDING", "DITHER", "MAPPING", "SCANNING",
			"INSTRUME", "SPECTEL1", "SPECTEL2", "SLIT", "WAVECENT", "RESOLUN",
			"DETECTOR", "DETSIZE", "PIXSCAL", "SUBARRNO", "SIBS_X", "SIBS_Y"
	};
	
}