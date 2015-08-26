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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import nom.tam.fits.*;
import nom.tam.util.*;

import java.io.*;
import java.util.*;
import java.text.*;

import kovacs.astro.AstroSystem;
import kovacs.astro.AstroTime;
import kovacs.astro.CelestialCoordinates;
import kovacs.astro.CoordinateEpoch;
import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.GeodeticCoordinates;
import kovacs.astro.HorizontalCoordinates;
import kovacs.astro.JulianEpoch;
import kovacs.astro.Precession;
import kovacs.astro.Weather;
import kovacs.data.*;
import kovacs.math.Range;
import kovacs.math.SphericalCoordinates;
import kovacs.math.Vector2D;
import kovacs.text.TableFormatter;
import kovacs.util.*;
import kovacs.util.DataTable;
import crush.astro.AstroMap;
import crush.sourcemodel.*;

public abstract class Scan<InstrumentType extends Instrument<?>, IntegrationType extends Integration<InstrumentType, ?>>
extends Vector<IntegrationType> implements Comparable<Scan<?, ?>>, TableFormatter.Entries {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8967822331907667222L;
	public InstrumentType instrument;
	
	private int serialNo = -1;
	private double MJD = Double.NaN;
	private String sourceName;
	
	public double LST = Double.NaN;
	
	public String timeStamp;
	public String descriptor;
	public String observer, creator, project;
	
	public EquatorialCoordinates equatorial, apparent;
	public HorizontalCoordinates horizontal;
	public GeodeticCoordinates site;
	public Precession fromApparent, toApparent;
	public Vector2D pointingCorrection;
	
	public Range longitudeRange, latitudeRange;
	public boolean isTracking = false;
	public boolean isMovingObject = false;
	
	public SourceModel sourceModel;
	public double weight = 1.0;
	
	public int sourcePoints = 0;
	public boolean hasSiblings = false;
		
	public GaussianSource<SphericalCoordinates> pointing;
	
	// Creates a scan with an initialized copy of the instrument
	@SuppressWarnings("unchecked")
	public Scan(InstrumentType instrument) { 
		this.instrument = (InstrumentType) instrument.copy();
	}
	
	@Override
	public int compareTo(Scan<?, ?> other) {
		if(serialNo == other.serialNo) return 0;
		return serialNo < other.serialNo ? -1 : 1;
	}
	
	public void validate() {	
		System.err.println(" Processing scan data:");
		
		if(hasOption("segment")) {
			double segmentTime = 60.0 * Unit.s;
			try { segmentTime = option("segment").getDouble() * Unit.s; }
			catch(Exception e) {}
			segmentTo(segmentTime);
		}
		else if(hasOption("subscans.merge")) mergeSubscans();
		
		
		isMovingObject |= hasOption("moving");
		
		Frame firstFrame = getFirstIntegration().getFirstFrame();
		Frame lastFrame = getLastIntegration().getLastFrame();
		
		if(Double.isNaN(MJD)) setMJD(firstFrame.MJD);
		if(Double.isNaN(LST)) LST = 0.5 * (firstFrame.LST + lastFrame.LST);
		
		//System.err.println("### MJD: " + Util.f3.format(MJD) + " --> Epoch is J" + Util.f2.format(JulianEpoch.getYearForMJD(MJD)));
		//System.err.println("### LST: " + Util.f3.format(LST/Unit.hour));
		
		if(equatorial == null) calcEquatorial();

		// Use J2000 coordinates
		if(!equatorial.epoch.equals(CoordinateEpoch.J2000)) precess(CoordinateEpoch.J2000);
		System.err.println("   Equatorial: " + equatorial.toString());
		
		// Calculate apparent and approximate horizontal coordinates.... 
		if(apparent == null) calcApparent();
		if(horizontal == null && site != null) calcHorizontal();
		
		if(horizontal != null) System.err.println("   Horizontal: " + horizontal.toString());
		
		for(int i=0; i<size(); ) {
			Integration<InstrumentType, ?> integration = get(i);
			try { 
				integration.validate();
				i++;
			}
			catch(Exception e) {
				System.err.println("   WARNING! " + e.getMessage() + " Dropping from set.");
				if(CRUSH.debug) e.printStackTrace();
				remove(i); 
			}
		}
		
		if(instrument.getOptions().containsKey("jackknife")) sourceName += "-JK";
		
		if(instrument.getOptions().containsKey("pointing")) pointingAt(getPointingCorrection(option("pointing")));	
	}
	
	public int getSerial() { return serialNo; }

	public void setSerial(int n) { 
		serialNo = n;
		instrument.setSerialOptions(n);
	}
	
	public double getMJD() {
		return MJD;
	}
	
	public void setMJD(double MJD) {
		this.MJD = MJD;
		instrument.setDateOptions(MJD);
		instrument.setMJDOptions(MJD);
	}
	
	public String getShortDateString() {
		AstroTime time = new AstroTime();
		time.setMJD(getMJD());
		return time.getFitsShortDate();
	}
	
	public String getSourceName() {
		return sourceName;
	}
	
	public void setSourceName(String value) {
		sourceName = value;
		instrument.setObjectOptions(sourceName);
	}
	
	public Vector2D getPointingCorrection(Configurator option) {
		if(!option.isEnabled) return null;
		String value = option.getValue().toLowerCase();
		if(value.equals("auto") || value.equals("suggest")) return null;
		Vector2D correction = option.getVector2D(); 
		correction.scale(instrument.getSizeUnitValue());
		return correction;
	}
	
	public void pointingAt(Vector2D correction) {
		if(correction == null) return;
		double sizeUnit = instrument.getSizeUnitValue();
		System.err.println("   Adjusting pointing by " + 
				Util.f1.format(correction.x() / sizeUnit) + ", " + Util.f1.format(correction.y() / sizeUnit) +
				" " + instrument.getSizeName() + ".");
		
		for(Integration<?,?> integration : this) integration.pointingAt(correction);
		if(pointingCorrection == null) pointingCorrection = correction;
		else pointingCorrection.add(correction);
	}
	
	public void applyPointing() {
		Vector2D differential = getNativePointingIncrement(pointing);
		pointingAt(differential);
		
		Vector2D arcsecs = (Vector2D) differential.clone();
		arcsecs.scale(1.0 / Unit.arcsec);
		//System.err.println("### pointing at " + arcsecs);
		
		// Reset the source coordinates to the pointing center
		if(pointing.getCoordinates() instanceof HorizontalCoordinates) 
			pointing.setCoordinates((SphericalCoordinates) horizontal.clone());

		else if(pointing.getCoordinates() instanceof EquatorialCoordinates) 
			pointing.setCoordinates((SphericalCoordinates) equatorial.clone());

		else ((CelestialCoordinates) pointing.getCoordinates()).fromEquatorial(equatorial);
		
		if(pointingCorrection == null) pointingCorrection = differential;
		else pointingCorrection.add(differential);
	}
	
	
	public void precess(CoordinateEpoch epoch) {
		Precession toEpoch = new Precession(equatorial.epoch, epoch);
		toEpoch.precess(equatorial);
		for(Integration<?,?> integration : this) for(Frame frame : integration) 
			if(frame != null) if(frame.equatorial != null) toEpoch.precess(frame.equatorial);	
		calcPrecessions(epoch);
	}
	
	public void calcPrecessions(CoordinateEpoch epoch) {
		JulianEpoch apparentEpoch = JulianEpoch.forMJD(MJD);
		fromApparent = new Precession(apparentEpoch, epoch);
		toApparent = new Precession(epoch, apparentEpoch);
	}
		
	public void calcEquatorial() {
		equatorial = horizontal.toEquatorial(site, LST);
		equatorial.epoch = new JulianEpoch();
		equatorial.epoch.setMJD(MJD);
		if(fromApparent == null) calcPrecessions(CoordinateEpoch.J2000);
		fromApparent.precess(equatorial);		
	}
	
	public void calcApparent() {
		apparent = (EquatorialCoordinates) equatorial.clone();
		if(toApparent == null) calcPrecessions(equatorial.epoch);
		toApparent.precess(apparent);
	}
	
	public void calcHorizontal() {
		if(apparent == null) calcApparent();
		horizontal = apparent.toHorizontal(site, LST);
	}
	
	public IntegrationType getFirstIntegration() {
		return get(0); 
	}

	public IntegrationType getLastIntegration() {
		return get(size()-1);
	}
	
	public boolean hasOption(String key) {
		return instrument.hasOption(key);
	}
	
	public Configurator option(String key) {
		return instrument.option(key);
	}

	// Read should validate the instrument before instantiating integrations for reading...
	public abstract void read(String descriptor, boolean readFully) throws Exception;
	
	// The integration should carry a copy of the instrument s.t. the integration can freely modify it...
	// The constructor of Integration thus copies the Scan instrument for private use...
	public abstract IntegrationType getIntegrationInstance();
	
	public String getDataPath() {
		return hasOption("datapath") ? option("datapath").getPath() + File.separator : "";
	}
	
	@SuppressWarnings("unchecked")
	public void mergeSubscans() {	
		if(size() < 2) return;

		// TODO What if different sampling intervals...
		System.err.println(" Merging integrations...");
		
		Integration<InstrumentType, Frame> merged = (Integration<InstrumentType, Frame>) get(0);
		merged.trimToSize();
		
		for(int i=1; i<size(); i++) {
			IntegrationType integration = get(i);
			integration.trimToSize();
			
			int gap = (int) Math.round((integration.getFirstFrame().MJD - get(i-1).getLastFrame().MJD) / instrument.samplingInterval);
			for(; --gap >= 0; ) merged.add(null);
				
			merged.addAll(integration);	
		}
	
		merged.reindex();
		
		clear();
		add((IntegrationType) merged);
	}
	
	public double getPA() {
		HorizontalFrame first = (HorizontalFrame) get(0).getFirstFrame();
		HorizontalFrame last = (HorizontalFrame) get(size()-1).getLastFrame();
		return 0.5 * (Math.atan2(first.sinPA, first.cosPA) + Math.atan2(last.sinPA, last.cosPA));
	}
	
	public BasicHDU getSummaryHDU(Configurator global) throws FitsException, IOException {
		Object[][] table = new Object[size()][];
		boolean details = hasOption("write.scandata.details");
		
		LinkedHashMap<String, Object> first = null;

		for(int i=0; i<table.length; i++) {
			IntegrationType integration = get(i);
			LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
			if(i == 0) first = data;
			integration.getFitsData(data);
			if(details) integration.addDetails(data);
			Object[] row = new Object[data.size()];
			int k=0;
			for(Object entry : data.values()) row[k++] = entry; 
			table[i] = row;
		}

		BasicHDU hdu = Fits.makeHDU(table);
		Header header = hdu.getHeader();
		editScanHeader(header);

		Cursor cursor = header.iterator();

		int k=1;
		for(String name : first.keySet()) {
			cursor.setKey("TFORM" + k);
			cursor.add(new HeaderCard("TTYPE" + (k++), name, "The column name"));
		}

		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
		cursor.add(new HeaderCard("COMMENT", " CRUSH scan-specific configuration section", false));
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
	
		instrument.getStartupOptions().difference(global).editHeader(cursor);	
		
		return hdu;
	}

		
	public void editScanHeader(Header header) throws HeaderCardException {	
		Locale.setDefault(Locale.US);
		
		header.addValue("EXTNAME", "Scan-" + getID(), "Scan data");
		
		header.addValue("INSTRUME", instrument.getName(), "The instrument name");
		
		if(serialNo > 0) header.addValue("SCANNO", serialNo, "Serial number for the scan");
		if(descriptor != null) header.addValue("SCANSPEC", descriptor, "Specifier by which the scan was invoked.");
		if(observer != null) header.addValue("OBSERVER", observer, "Name(s) of the observer(s).");
		if(project != null) header.addValue("PROJECT", project, "Description of the project");
		if(creator != null) header.addValue("CREATOR", creator, "Software that wrote the scan data.");
		if(timeStamp != null) header.addValue("DATE-OBS", timeStamp, "Start of observation.");
		
		// The Source Descriptors
		header.addValue("OBJECT", sourceName, "Object catalog name");
		header.addValue("RADESYS", (equatorial.epoch instanceof JulianEpoch ? "FK5" : "FK4"), "World coordinate system id.");
		header.addValue("RA", Util.hf2.format(equatorial.RA()), "Human Readable Right Ascention.");
		header.addValue("DEC", Util.af1.format(equatorial.DEC()), "Human Readable Declination.");
		header.addValue("EQUINOX", equatorial.epoch.getYear(), "Precession epoch.");	
	
		if(!Double.isNaN(MJD)) header.addValue("MJD", MJD, "Modified Julian Day.");
		
		if(this instanceof GroundBased) {
			if(!Double.isNaN(LST)) header.addValue("LST", LST / Unit.hour, "Local Sidereal Time (hours).");
			header.addValue("AZ", horizontal.AZ()/Unit.deg, "Azymuth (deg).");
			header.addValue("EL", horizontal.EL()/Unit.deg, "Elevation (deg).");
			header.addValue("PA", getPA()/Unit.deg, "Direction of zenith w.r.t. North (deg).");
		
			if(site != null) {
				header.addValue("SITELON", Util.af1.format(site.longitude()), "Geographic longitude of the observing site (deg).");
				header.addValue("SITELAT", Util.af1.format(site.latitude()), "Geographic latitude of the observing site (deg).");
			}
		}
		
		header.addValue("WEIGHT", weight, "Relative source weight of the scan.");
		
		header.addValue("TRACKIN", isTracking, "Was the telescope tracking during the observation?");
		
		instrument.editScanHeader(header);
	}
	
	public void setSourceModel(SourceModel model) {
		sourceModel = model;
	}
	
	public double getObservingTime() {
		double t = 0.0;
		for(IntegrationType integration : this) t += integration.getFrameCount(~0) * integration.instrument.integrationTime;
		return t;
	}
	
	public int getFrameCount(int skipFlags) {
		int n = 0;
		for(IntegrationType integration : this) n += integration.getFrameCount(skipFlags);
		return n;
	}
	
	// separators: ' ' \t , =
	// entries:	name(format)
	//      format: e.g. d3, f1, e3, t:1 (time with colons, 1 decimal), ad2 (angle with symbols, 2 decimals)
	public void writeLog(Configurator option, String defaultFileName) throws IOException {
		// Map the optional argument to the 'file' sub-option.
		option.mapValueTo("file");
		
		String fileName = option.isConfigured("file") ? 
				option.get("file").getPath() : defaultFileName;

		String format = option.isConfigured("format") ? 
				option.get("format").getValue() : 
				"date(yyyy-MM-dd)  id\tobject\tobsmins(f1)\tAZd(f1) ELd(f1)\tRAh(f1) DECd(f1)";
		
		// Allow literal '\t' to represent a tab, like in C or Java 
		format = Util.fromEscapedString(format);
				
		int conflictPolicy = LogFile.CONFLICT_DEFAULT;
		if(option.isConfigured("conflict")) {
			String policy = option.get("conflict").getValue().toLowerCase();
			if(policy.equals("overwrite")) conflictPolicy = LogFile.CONFLICT_OVERWRITE;
			else if(policy.equals("version")) conflictPolicy = LogFile.CONFLICT_VERSION;
		}
		
		LogFile log = new LogFile(fileName, format, conflictPolicy);
		log.add(TableFormatter.format(this, format));
		
		System.err.println(" Written log to " + log.getFileName());
	}
	
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
		
		
		if(horizontal == null) horizontal = equatorial.toHorizontal(site, LST);
		
		
		String modelEntry = null;
		if(sourceModel != null) modelEntry = sourceModel.getFormattedEntry(name, formatSpec);
		if(modelEntry != null) return modelEntry;
		
		if(name.startsWith("?")) {
			name = name.substring(1).toLowerCase();
			if(!hasOption(name)) return "---";
			else {
				String value = option(name).getValue();
				if(value.length() == 0) return "<true>";
				else return value;				
			}
		}
		else if(name.startsWith("pnt.")) {
			if(pointing == null) return "---";
			return getPointingData().getFormattedEntry(name.substring(4), formatSpec);
		}
		else if(name.startsWith("src.")) {
			if(pointing == null) return "---";
			if(!(sourceModel instanceof ScalarMap)) return "---";
			AstroMap map = ((ScalarMap) sourceModel).map;
			Unit sizeUnit = new Unit(instrument.getSizeName(), instrument.getSizeUnitValue());
			return pointing.getData(map, sizeUnit).getFormattedEntry(name.substring(4), formatSpec);
		}
		else if(name.equals("object")) return sourceName;
		else if(name.equals("id")) return getID();
		else if(name.equals("serial")) return Integer.toString(serialNo);
		else if(name.equals("MJD")) return Util.defaultFormat(MJD, f);
		else if(name.equals("UT")) return Util.defaultFormat((MJD - Math.floor(MJD)) * Unit.day, f);
		else if(name.equals("UTh")) return Util.defaultFormat((MJD - Math.floor(MJD)) * 24.0, f);
		else if(name.equals("PA")) return Util.defaultFormat(getPA(), f);
		else if(name.equals("PAd")) return Util.defaultFormat(getPA() / Unit.deg, f);
		else if(name.equals("AZ")) return Util.defaultFormat(horizontal.AZ(), f);
		else if(name.equals("EL")) return Util.defaultFormat(horizontal.EL(), f);
		else if(name.equals("RA")) return Util.defaultFormat(equatorial.RA() / Unit.timeAngle, f);
		else if(name.equals("DEC")) return Util.defaultFormat(equatorial.DEC(), f);
		else if(name.equals("AZd")) return Util.defaultFormat(horizontal.AZ() / Unit.deg, f);
		else if(name.equals("ELd")) return Util.defaultFormat(horizontal.EL() / Unit.deg, f);
		else if(name.equals("RAd")) return Util.defaultFormat(equatorial.RA() / Unit.deg, f);
		else if(name.equals("RAh")) return Util.defaultFormat(((equatorial.RA() + 2.0 * Math.PI) / Unit.hourAngle) % 24.0, f);
		else if(name.equals("DECd")) return Util.defaultFormat(equatorial.DEC() / Unit.deg, f);
		else if(name.equals("epoch")) return equatorial.epoch.toString();
		else if(name.equals("epochY")) return Util.defaultFormat(equatorial.epoch.getYear(), f);
		else if(name.equals("LST")) return Util.defaultFormat(LST, f);
		else if(name.equals("LSTh")) return Util.defaultFormat(LST / Unit.hour, f);
		else if(name.equals("date")) {
			AstroTime time = new AstroTime();
			time.setMJD(MJD);
			DateFormat dateFormat = new SimpleDateFormat(formatSpec);
			dateFormat.setTimeZone(AstroTime.UTC);
			return dateFormat.format(time.getDate());
		}
		else if(name.equals("obstime")) return Util.defaultFormat(getObservingTime() / Unit.sec, f);
		else if(name.equals("obsmins")) return Util.defaultFormat(getObservingTime() / Unit.min, f);
		else if(name.equals("obshours")) return Util.defaultFormat(getObservingTime() / Unit.hour, f);
		else if(name.equals("weight")) return Util.defaultFormat(weight, f);
		else if(name.equals("frames")) return Integer.toString(getFrameCount(~0)); 
		else if(name.equals("project")) return project;
		else if(name.equals("observer")) return observer; 
		else if(name.equals("descriptor")) return descriptor;
		else if(name.equals("creator")) return creator;
		else if(name.equals("integrations")) return Integer.toString(size());
		else if(name.equals("generation")) return Integer.toString(getSourceGeneration());
		else if(this instanceof Weather) {	
			if(name.equals("Tamb")) return Util.defaultFormat(((Weather) this).getAmbientTemperature() - 273.16*Unit.K, f);
			else if(name.equals("humidity")) return Util.defaultFormat(((Weather) this).getAmbientHumidity(), f);
			else if(name.equals("pressure")) return Util.defaultFormat(((Weather) this).getAmbientPressure() / Unit.hPa, f);
			else if(name.equals("windspeed")) return Util.defaultFormat(((Weather) this).getWindSpeed() / (Unit.m / Unit.s), f);
			else if(name.equals("windpk")) return Util.defaultFormat(((Weather) this).getWindPeak() / (Unit.m / Unit.s), f);
			else if(name.equals("winddir"))	return Util.defaultFormat(((Weather) this).getWindDirection() / Unit.deg, f);
			else return getFirstIntegration().getFormattedEntry(name, formatSpec);
		}
		else return getFirstIntegration().getFormattedEntry(name, formatSpec);
	}
	
	public int getSourceGeneration() {
		int max = 0;
		for(Integration<?,?> integration : this) max = Math.max(integration.sourceGeneration, max);
		return max;		
	}
	
	public void writeProducts() {
		for(Integration<?,?> integration : this) integration.writeProducts();
		
		printFocus();
		
		printPointing();
		
		if(hasOption("log")) {
			try { writeLog(option("log"), CRUSH.workPath + File.separator + instrument.getName() + ".log"); }
			catch(IOException e) {
				System.err.println(" WARNING! Could not write log.");
				if(CRUSH.debug) e.printStackTrace();
			}
		}
	}
	
	public void printFocus() {
		if(pointing != null) {
			System.out.println(" Instant Focus Results for Scan " + getID() + ":");
			System.out.println();
			System.out.println(getFocusString());
		}
		else if(hasOption("pointing")) if(sourceModel.isValid()) {
			Configurator pointingOption = option("pointing");
 			if(pointingOption.equals("suggest") || pointingOption.equals("auto")) {
				System.out.println(" WARNING! Cannot suggest focus for scan " + getID() + ".");
			}
		}
	}
	
	
	public void printPointing() {
		if(pointing != null) {
			System.out.println();
			System.out.println(" Pointing Results for Scan " + getID() + ":");
			System.out.println();
			System.out.println(getPointingString() + "\n");
		}
		else if(hasOption("pointing")) if(sourceModel.isValid()) {
			Configurator pointingOption = option("pointing");
			if(pointingOption.equals("suggest") || pointingOption.equals("auto")) {
				System.out.println(" WARNING! Cannot suggest pointing for scan " + getID() + ".");
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public ArrayList<Scan<InstrumentType, IntegrationType>> split() {
		System.err.println(" Splitting subscans into separate scans.");
		ArrayList<Scan<InstrumentType, IntegrationType>> scans = new ArrayList<Scan<InstrumentType, IntegrationType>>();
		for(IntegrationType integration : this) {
			Scan<InstrumentType, IntegrationType> scan = (Scan<InstrumentType, IntegrationType>) clone();
			if(size() > 1) scan.hasSiblings = true;
			scan.clear();
			scan.instrument = integration.instrument;
			integration.scan = scan;
			scan.add(integration);
			scans.add(scan);
		}
		return scans;
	}

	public synchronized void updateGains(final String modalityName) {		
		boolean isRobust = false;
		if(!hasOption("gains")) return;
		if(hasOption("correlated." + modalityName + ".nogains")) return;
		
		Configurator gains = option("gains");
		gains.mapValueTo("estimator");
		if(gains.isConfigured("estimator")) if(gains.get("estimator").equals("median")) isRobust = true; 
		
		WeightedPoint[] G = new WeightedPoint[instrument.storeChannels+1];
		for(int i=G.length; --i >= 0; ) G[i] = new WeightedPoint();	
		
		// Derive the scan gains
		boolean gotGains = false;
		for(IntegrationType integration : this) {	
			try {		
				Modality<?> modality = integration.instrument.modalities.get(modalityName);
				if(modality.trigger != null) if(!hasOption(modality.trigger)) continue;
				modality.averageGains(G, integration, isRobust);
				gotGains = true;
			}	
			catch(Exception e) { e.printStackTrace(); }	
		}
		
		if(!gotGains) return;
		
		
		// Apply the gain increment
		for(IntegrationType integration : this) {
			Modality<?> modality = integration.instrument.modalities.get(modalityName);
			boolean isFlagging = false; 
			
			try { isFlagging |= modality.applyGains(G, integration); }
			catch(Exception e) { e.printStackTrace(); }
			
			if(isFlagging) {
				integration.instrument.census();
				integration.comments += integration.instrument.mappingChannels;
			}
		}
	}
	
	public void decorrelate(String modalityName) {
		boolean isRobust = false;
		if(hasOption("estimator")) if(option("estimator").equals("median")) isRobust = true;
		
		for(IntegrationType integration : this) integration.decorrelate(modalityName, isRobust);
		if(hasOption("gains.span") || hasOption("correlated." + modalityName + ".span")) updateGains(modalityName);
	}
	
	public void perform(String task) { 
		if(task.startsWith("correlated.")) {
			String modalityName = task.substring(task.indexOf('.')+1);
			decorrelate(modalityName);
			for(IntegrationType integration : this) if(integration.comments.charAt(integration.comments.length() - 1) != ' ') 
				integration.comments += " ";
		}
		else for(IntegrationType integration : this) integration.perform(task);
	}
	
	public String getID() { return Integer.toString(serialNo); }

	public DataTable getPointingData() throws IllegalStateException {
		if(pointing == null) throw new IllegalStateException("No pointing data for scan " + getID());
		Vector2D pointingOffset = getNativePointingIncrement(pointing);
		Vector2D absolute = getNativePointing(pointing);
		
		DataTable data = new DataTable();
		
		String nameX = this instanceof GroundBased ? "AZ" : "RA";
		String nameY = this instanceof GroundBased ? "EL" : "DEC";
		
		double sizeUnit = instrument.getSizeUnitValue();
		String sizeName = instrument.getSizeName();
		
		data.new Entry("dX", pointingOffset.x() / sizeUnit, sizeName);
		data.new Entry("dY", pointingOffset.y() / sizeUnit, sizeName);
		
		data.new Entry("d" + nameX, pointingOffset.x() / sizeUnit, sizeName);
		data.new Entry("d" + nameY, pointingOffset.y() / sizeUnit, sizeName);
		
		data.new Entry(nameX, absolute.x() / sizeUnit, sizeName);
		data.new Entry(nameY, absolute.y() / sizeUnit, sizeName);
		
		// Also print Nasmyth offsets if applicable...
		if(instrument.mount == Mount.LEFT_NASMYTH || instrument.mount == Mount.RIGHT_NASMYTH) {
			Vector2D nasmyth = getNasmythOffset(pointingOffset);
			
			data.new Entry("dNasX", nasmyth.x() / sizeUnit, sizeName);
			data.new Entry("dNasY", nasmyth.y() / sizeUnit, sizeName);
			
			nasmyth = getNasmythOffset(absolute);
			data.new Entry("NasX", nasmyth.x() / sizeUnit, sizeName);
			data.new Entry("NasY", nasmyth.y() / sizeUnit, sizeName);
		}
		
		Asymmetry2D asym = getSourceAsymmetry(pointing);
		data.new Entry("asymX", 100.0 * asym.getX().value(), "%");
		data.new Entry("asymY", 100.0 * asym.getY().value(), "%");
		data.new Entry("dasymX", 100.0 * asym.getX().rms(), "%");
		data.new Entry("dasymY", 100.0 * asym.getY().rms(), "%");
	
		if(pointing instanceof EllipticalSource) {
			EllipticalSource<?> ellipse = (EllipticalSource<?>) pointing;
			
			DataPoint elongation = ellipse.getElongation();
			data.new Entry("elong", 100.0 * elongation.value(), "%");
			data.new Entry("delong", 100.0 * elongation.rms(), "%");
			
			DataPoint angle = ellipse.getAngle();
			data.new Entry("angle", angle.value() / Unit.deg, "%");
			data.new Entry("dangle", angle.value() / Unit.deg, "%");
			
			DataPoint elongationX = getSourceElongationX(ellipse);
			data.new Entry("elongX", 100.0 * elongationX.value(), "%");
			data.new Entry("delongX", 100.0 * elongationX.rms(), "%");	
		}
		
		return data;
	}	
	
	public String getPointingString() {
		String info = "";
			
		if(sourceModel instanceof ScalarMap) {
			AstroMap map = ((ScalarMap) sourceModel).map;
			Unit sizeUnit = new Unit(instrument.getSizeName(), instrument.getSizeUnitValue());
			info += pointing.pointingInfo(map, sizeUnit) + "\n";
		}
		
		info += getPointingString(getNativePointingIncrement(pointing));
		return info;
	}
	
	
	public Asymmetry2D getSourceAsymmetry(CircularRegion<SphericalCoordinates> region) {
		if(!(sourceModel instanceof ScalarMap)) return null;
		
		double minr = instrument.getPointSize();
		double maxr = (hasOption("focus.r") ? option("focus.r").getDouble() : 2.5) * instrument.getPointSize();
		
		AstroMap map = ((ScalarMap) sourceModel).map; 
		AstroSystem system = map.astroSystem();
		if(!(system.isEquatorial() || system.isHorizontal())) return map.getAsymmetry(region, 0.0, minr, maxr);
		
		boolean isGroundEquatorial = this instanceof GroundBased && system.isEquatorial();
		double angle = isGroundEquatorial ? this.getPA() : 0.0;

		return map.getAsymmetry(region, angle, minr, maxr);
	}
	
	public DataPoint getSourceElongationX(EllipticalSource<?> ellipse) {
		DataPoint elongation = new DataPoint(ellipse.getElongation());
		DataPoint angle = new DataPoint(ellipse.getAngle());
		
		if(instrument instanceof GroundBased && pointing.getCoordinates() instanceof EquatorialCoordinates) {
			angle.add(-getPA());
		}
		
		elongation.scale(Math.cos(2.0 * angle.value()));
		return elongation;		
	}
	
	protected String getFocusString() {
		Asymmetry2D asym = getSourceAsymmetry(pointing);
					
		DataPoint elongation = pointing instanceof EllipticalSource ? 
				getSourceElongationX((EllipticalSource<SphericalCoordinates>) pointing) : null;
		
		return getFocusString(asym, elongation);
	}
	
	protected String getFocusString(Asymmetry2D asym, DataPoint elongation) {
		StringBuffer info = new StringBuffer(asym == null ? "" : asym.toString() + "\n");
		
		if(elongation != null) info.append("  Elongation: " + elongation.toString(Unit.get("%")) + "\n");			
		
		double relFWHM = pointing.getFWHM().value() / instrument.getPointSize();
			
		boolean force = hasOption("focus");
		
		if(force || (relFWHM > 0.8 && relFWHM <= 2.0)) {
			InstantFocus focus = new InstantFocus();
			focus.deriveFrom(asym, elongation, instrument.getOptions());
			info.append(instrument.getFocusString(focus));
		}
		else {
			info.append("\n");
			if(relFWHM <= 0.8) info.append("  WARNING! Source FWHM unrealistically low.\n");
			else {
				info.append("  WARNING! Source is either too extended or too defocused.\n");
				info.append("           No focus correction is suggested. You can force calculate\n");
				info.append("           suggested focus values by setting the 'focus' option when\n");
				info.append("           running CRUSH.\n");
			}
		}
		
		return new String(info);
	}
	
	
	
	protected String getPointingString(Vector2D pointingOffset) {	
		// Print the native pointing offsets...
		String text = "";
		
		double sizeUnit = instrument.getSizeUnitValue();
		String sizeName = instrument.getSizeName();
		
		text += "  Offset: ";
		text += Util.f1.format(pointingOffset.x() / sizeUnit) + ", " + Util.f1.format(pointingOffset.y() / sizeUnit) + " " 
			+ sizeName + " (" + (this instanceof GroundBased ? "az,el" : "ra,dec") + ")";
		
		// Also print Nasmyth offsets if applicable...
		if(instrument.mount == Mount.LEFT_NASMYTH || instrument.mount == Mount.RIGHT_NASMYTH) {
			Vector2D nasmyth = getNasmythOffset(pointingOffset);
			text += "\n  Offset: ";		
			text += Util.f1.format(nasmyth.x() / sizeUnit) + ", " + Util.f1.format(nasmyth.y() / sizeUnit) + " " 
				+ sizeName + " (nasmyth)";
		}
		
		return text;
	}	
	

	public Vector2D getEquatorialPointing(GaussianSource<SphericalCoordinates> source) {
		EquatorialCoordinates sourceCoords = null;
		
		if(source.getCoordinates() instanceof EquatorialCoordinates) {
			sourceCoords = (EquatorialCoordinates) source.getCoordinates();
			if(!sourceCoords.epoch.equals(equatorial.epoch)) sourceCoords.precess(equatorial.epoch);
		}
		else {
			sourceCoords = (EquatorialCoordinates) equatorial.clone();
			((CelestialCoordinates) source.getCoordinates()).toEquatorial(sourceCoords);
		}
			
		return sourceCoords.getOffsetFrom(equatorial);
	}
	
	public Vector2D getNativePointing(GaussianSource<SphericalCoordinates> source) {
		Vector2D offset = getNativePointingIncrement(source);
		if(pointingCorrection != null) offset.add(pointingCorrection);
		return offset;
	}
	
	public Vector2D getNativePointingIncrement(GaussianSource<SphericalCoordinates> source) {
		if(instrument instanceof GroundBased) {
			if(source.getCoordinates() instanceof HorizontalCoordinates) 
				return source.getCoordinates().getOffsetFrom(horizontal);			
			else {	
				// Equatorial offset (RA/DEC)
				Vector2D offset = getEquatorialPointing(source);
				
				// Rotate to Horizontal...
				Vector2D from = (Vector2D) offset.clone();
				((HorizontalFrame) getFirstIntegration().getFirstFrame()).equatorialToHorizontal(from);
				Vector2D to = (Vector2D) offset.clone();
				((HorizontalFrame) getLastIntegration().getLastFrame()).equatorialToHorizontal(to);
				offset.setX(0.5 * (from.x() + to.x()));
				offset.setY(0.5 * (from.y() + to.y()));
				return offset;
			}
		}	
		else if(source.getCoordinates() instanceof EquatorialCoordinates) 
			return source.getCoordinates().getOffsetFrom(equatorial);
		else {
			EquatorialCoordinates sourceEq = ((CelestialCoordinates) source.getCoordinates()).toEquatorial();
			return sourceEq.getOffsetFrom(equatorial);
		}
	}
 

	// Inverse rotation from Native to Nasmyth...
	public Vector2D getNasmythOffset(Vector2D nativeOffset) {
		SphericalCoordinates coords = this instanceof GroundBased ? horizontal : equatorial;
		double sinA = instrument.mount == Mount.LEFT_NASMYTH ? -coords.sinLat() : coords.sinLat();
		double cosA = coords.cosLat();
		
		// Inverse rotation from Native to Nasmyth...
		Vector2D nasmyth = new Vector2D();
		nasmyth.setX(cosA * nativeOffset.x() + sinA * nativeOffset.y());
		nasmyth.setY(cosA * nativeOffset.y() - sinA * nativeOffset.x());
		
		return nasmyth;
	}
	
	@Override
	public String toString() { return "Scan " + getID(); }
	
	@SuppressWarnings("unchecked")
	public void segmentTo(double segmentTime) {
		if(size() > 1) mergeSubscans();
		
		IntegrationType merged = get(0);
		clear();
		
		int nT = merged.framesFor(segmentTime);
		int N = ExtraMath.roundupRatio(merged.size(), nT);
		
		if(N <= 1) return;
		
		ensureCapacity(N);
		
		System.err.println(" Segmenting into " + N + " integrations.");
		
		clear();
		
		for(int i=0, t=0; i<N; i++) {
			Integration<InstrumentType, Frame> integration = (Integration<InstrumentType, Frame>) merged.clone();
			integration.clear();
			integration.instrument = (InstrumentType) merged.instrument.copy();
			integration.integrationNo = i;
			
			int nk = Math.min(merged.size() - t, nT);
			integration.ensureCapacity(nk);
			
			for(int k=0; k<nk; k++,t++) integration.add(merged.get(t));
			integration.reindex();
			add((IntegrationType) integration);
		}
		
		
		
	}
	
	
}
