/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import nom.tam.fits.*;
import nom.tam.util.*;

import java.io.*;
import java.util.*;

import crush.sourcemodel.*;
import crush.telescope.GroundBased;
import crush.telescope.HorizontalFrame;
import crush.telescope.InstantFocus;
import crush.telescope.Mount;
import jnum.Configurator;
import jnum.Constant;
import jnum.ExtraMath;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroSystem;
import jnum.astro.AstroTime;
import jnum.astro.CelestialCoordinates;
import jnum.astro.CoordinateEpoch;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.FocalPlaneCoordinates;
import jnum.astro.GeodeticCoordinates;
import jnum.astro.HorizontalCoordinates;
import jnum.astro.JulianEpoch;
import jnum.astro.Precession;
import jnum.astro.Weather;
import jnum.data.*;
import jnum.data.image.Asymmetry2D;
import jnum.data.image.Map2D;
import jnum.data.image.Observation2D;
import jnum.data.image.region.CircularRegion;
import jnum.data.image.region.EllipticalSource;
import jnum.data.image.region.GaussianSource;
import jnum.fits.FitsToolkit;
import jnum.math.CoordinateSystem;
import jnum.math.Offset2D;
import jnum.math.Range;
import jnum.math.Range2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.reporting.BasicMessaging;
import jnum.text.TableFormatter;
import jnum.util.*;
import jnum.util.DataTable;

public abstract class Scan<InstrumentType extends Instrument<? extends Channel>, IntegrationType extends Integration<InstrumentType, ? extends Frame>>
extends Vector<IntegrationType> implements Comparable<Scan<?, ?>>, TableFormatter.Entries, BasicMessaging {
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
	
	public Range2D range;
	public boolean isTracking = false;
	public boolean isNonSidereal = false;
	
	public SourceModel sourceModel;
	public double weight = 1.0;
	
	public int sourcePoints = 0;
	public boolean isSplit = false;
		
	public GaussianSource pointing;
	
	// Creates a scan with an initialized copy of the instrument
	@SuppressWarnings("unchecked")
	public Scan(InstrumentType instrument) { 
		this.instrument = (InstrumentType) instrument.copy();
		this.instrument.setParent(this);
	}
	
	@Override
	public int compareTo(Scan<?, ?> other) {
	    return getID().compareTo(other.getID());
	}
	
	@Override
	public int hashCode() {
		int hash = super.hashCode() ^ size() ^ getID().hashCode() ^ HashCode.from(MJD);
		if(instrument != null) hash ^= instrument.hashCode();
		return hash;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof Scan)) return false;
		if(!super.equals(o)) return false;
		Scan<?,?> scan = (Scan<?,?>) o;
		
		if(size() != scan.size()) return false;
		if(getID() != scan.getID()) return false;
		if(MJD != scan.MJD) return false;
		if(!Util.equals(instrument, scan.instrument)) return false;
		return true;
	}
	
	public void validate() {	
		info("Processing scan data:");
			
		if(hasOption("subscans.merge")) mergeIntegrations();
		
		if(hasOption("segment")) {
            double segmentTime = 60.0 * Unit.s;
            try { segmentTime = option("segment").getDouble() * Unit.s; }
            catch(Exception e) {}
            segmentTo(segmentTime);
        }
		
		isNonSidereal |= hasOption("moving");
		
		Frame firstFrame = getFirstIntegration().getFirstFrame();
		Frame lastFrame = getLastIntegration().getLastFrame();
		
		if(Double.isNaN(MJD)) setMJD(firstFrame.MJD);
		if(Double.isNaN(LST)) LST = 0.5 * (firstFrame.LST + lastFrame.LST);
		
		//debug("MJD: " + Util.f3.format(MJD) + " --> Epoch is J" + Util.f2.format(JulianEpoch.getYearForMJD(MJD)));
		//debug("LST: " + Util.f3.format(LST/Unit.hour));
		
		if(!hasOption("lab")) {
		    if(equatorial == null) calcEquatorial();

		    // Use J2000 coordinates
		    if(!equatorial.epoch.equals(CoordinateEpoch.J2000)) precess(CoordinateEpoch.J2000);
		    info("  Equatorial: " + equatorial.toString());

		    // Calculate apparent and approximate horizontal coordinates.... 
		    if(apparent == null) calcApparent();
		    
		    // TODO below are only for horizontal...
		    if(Double.isNaN(LST)) LST = 0.5 * (getFirstIntegration().getFirstFrame().LST + getFirstIntegration().getFirstFrame().LST);
		    if(horizontal == null && site != null) calcHorizontal();
		    if(horizontal != null) info("  Horizontal: " + horizontal.toString());
		}
		
		for(int i=0; i<size(); ) {
			Integration<InstrumentType, ?> integration = get(i);
			try { 
		        info("Processing integration " + (i+1) + ":");
				integration.validate();
				i++;
			}
			catch(Exception e) {
				integration.warning("Integration " + (i+1) + " validation error (dropping from set):\n   --> " + e.getMessage());
				if(CRUSH.debug) CRUSH.trace(e);
				remove(i); 
			}
		}
		
		if(instrument.getOptions().containsKey("jackknife")) sourceName += "-JK";
		
		if(instrument.getOptions().containsKey("pointing")) pointingAt(getPointingCorrection(option("pointing")));	
	}
	
	public void setIteration(int i, int rounds) {
		CRUSH.setIteration(instrument.getOptions(), i, rounds);
		for(Integration<?,?> integration : this) if(integration.instrument != instrument) 
			integration.setIteration(i, rounds);
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
        Vector2D correction = option.isEnabled ? option.getVector2D() : new Vector2D();
        if(option.isConfigured("offset")) correction.add(option.get("offset").getVector2D());
        correction.scale(instrument.getSizeUnitValue());
        return correction;
    }

	public void pointingAt(Vector2D correction) {
		if(correction == null) return;
		double sizeUnit = instrument.getSizeUnitValue();
		info("Adjusting pointing by " + 
				Util.f1.format(correction.x() / sizeUnit) + ", " + Util.f1.format(correction.y() / sizeUnit) +
				" " + instrument.getSizeName() + ".");
		
		for(Integration<?,?> integration : this) integration.pointingAt(correction);
		if(pointingCorrection == null) pointingCorrection = correction;
		else pointingCorrection.add(correction);
	}
	
	public void applyPointing() {
		Offset2D differential = getNativePointingIncrement(pointing);
		pointingAt(differential);
		
		Vector2D arcsecs = (Vector2D) differential.clone();
		arcsecs.scale(1.0 / Unit.arcsec);
		//debug("pointing at " + arcsecs);
		
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
	
	public SphericalCoordinates getNativeCoordinates() { return horizontal; }
	
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
	public void mergeIntegrations() {	
		if(size() < 2) return;
	
		// TODO What if different sampling intervals...
		info("Merging " + size() + " integrations...");
		
		final double maxDiscontinuity = hasOption("subscans.merge.maxgap") ? option("subscans.merge.maxgap").getDouble() * Unit.s : Double.NaN;
		final int maxGap = Double.isNaN(maxDiscontinuity) ? Integer.MAX_VALUE : (int) Math.ceil(maxDiscontinuity / instrument.samplingInterval);
		
		Integration<InstrumentType, Frame> merged = (Integration<InstrumentType, Frame>) get(0);
		merged.trimEnd();
		
		double lastMJD = merged.getLastFrame().MJD;
		
		ArrayList<IntegrationType> parts = new ArrayList<IntegrationType>();
		
		for(int i=1; i<size(); i++) {
			IntegrationType integration = get(i);
			
			// Remove null frames from the end;
			integration.trimEnd();
			
			// Skip null frames at the beginning...
			final int nt = integration.size();
			int from = 0;
			for( ; from < nt; from++) if(integration.get(from) != null) break;			
			
			int gap = (int) Math.round((integration.get(from).MJD - lastMJD) * Unit.day / instrument.samplingInterval) - 1;
			
			// Deal with any gaps between subscans here...
			if(gap > 0) {
				if(gap < maxGap) {
					info("  > Padding with " + gap + " frames before integration " + integration.getID());
					for(; --gap >= 0; ) merged.add(null);
				}
				else {
					info("  > Large gap before integration " + integration.getID() + ". Starting new merge.");
					parts.add((IntegrationType) merged);
					merged = (Integration<InstrumentType, Frame>) integration;
				}	
			}
			
			// Do the actual merge...
			for(int t=from; t<nt; t++) merged.add(integration.get(t));
			
			lastMJD = integration.getLastFrame().MJD;
		}
	
		merged.reindex();
		
		info("  > Total esposure time: " + Util.f1.format(merged.getExposureTime() / Unit.s) + "s.");
		
		clear();
		addAll(parts);
		add((IntegrationType) merged);
	}
	
	public double getPA() {
		HorizontalFrame first = (HorizontalFrame) get(0).getFirstFrame();
		HorizontalFrame last = (HorizontalFrame) get(size()-1).getLastFrame();
		return 0.5 * (Math.atan2(first.sinPA, first.cosPA) + Math.atan2(last.sinPA, last.cosPA));
	}
	
	public BasicHDU<?> getSummaryHDU(Configurator global) throws FitsException, IOException {
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

		BasicHDU<?> hdu = Fits.makeHDU(table);
		Header header = hdu.getHeader();
		editScanHeader(header);

		Cursor<String, HeaderCard> cursor = header.iterator();

		int k=1;
		for(String name : first.keySet()) {
			cursor.setKey("TFORM" + k);
			cursor.add(new HeaderCard("TTYPE" + (k++), name, "The column name"));
		}

		
		Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
		c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
		c.add(new HeaderCard("COMMENT", " CRUSH scan-specific configuration section", false));
		c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
	
		instrument.getStartupOptions().difference(global).editHeader(header);	
		
		return hdu;
	}

		
	public void editScanHeader(Header header) throws HeaderCardException {	
		Locale.setDefault(Locale.US);
		
		Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
		c.add(new HeaderCard("EXTNAME", "Scan-" + getID(), "Scan data"));
		c.add(new HeaderCard("INSTRUME", instrument.getName(), "The instrument name"));
		c.add(new HeaderCard("SCANID", getID(), "Scan ID."));
		
		if(serialNo > 0) c.add(new HeaderCard("SCANNO", serialNo, "Serial number for the scan"));
		if(descriptor != null) FitsToolkit.addLongKey(c, "SCANSPEC", descriptor, "Scan descriptor");
		if(observer != null) c.add(new HeaderCard("OBSERVER", observer, "Name(s) of the observer(s)"));
		if(project != null) c.add(new HeaderCard("PROJECT", project, "Description of the project"));
		if(creator != null) c.add(new HeaderCard("CREATOR", creator, "Software that wrote the scan data"));
		if(timeStamp != null) c.add(new HeaderCard("DATE-OBS", timeStamp, "Start of observation"));
		
		// The Source Descriptors
		c.add(new HeaderCard("OBJECT", sourceName, "Object catalog name"));
		c.add(new HeaderCard("RADESYS", (equatorial.epoch instanceof JulianEpoch ? "FK5" : "FK4"), "World coordinate system id"));
		c.add(new HeaderCard("RA", Util.hf2.format(equatorial.RA()), "Human Readable Right Ascention"));
		c.add(new HeaderCard("DEC", Util.af1.format(equatorial.DEC()), "Human Readable Declination"));
		c.add(new HeaderCard("EQUINOX", equatorial.epoch.getYear(), "Precession epoch"));	
	
		if(!Double.isNaN(MJD)) c.add(new HeaderCard("MJD", MJD, "Modified Julian Day"));
		
		if(this instanceof GroundBased) {
			if(!Double.isNaN(LST)) c.add(new HeaderCard("LST", LST / Unit.hour, "Local Sidereal Time (hours)"));
			c.add(new HeaderCard("AZ", horizontal.AZ()/Unit.deg, "Azymuth (deg)."));
			c.add(new HeaderCard("EL", horizontal.EL()/Unit.deg, "Elevation (deg)."));
			c.add(new HeaderCard("PA", getPA()/Unit.deg, "Direction of zenith w.r.t. North (deg)"));
		
			if(site != null) {
				c.add(new HeaderCard("SITELON", Util.af1.format(site.longitude()), "Geodetic longitude of the observing site (deg)"));
				c.add(new HeaderCard("SITELAT", Util.af1.format(site.latitude()), "Geodetic latitude of the observing site (deg)"));
			}
		}
		
		c.add(new HeaderCard("WEIGHT", weight, "Relative source weight of the scan"));	
		c.add(new HeaderCard("TRACKIN", isTracking, "Was the telescope tracking during the observation?"));
		
		instrument.editScanHeader(header);
		
		if(pointing != null) if(sourceModel instanceof AstroMap) {  
		    pointing.editHeader(header, instrument.getSizeUnit());
		}
	}
	
	
	public void setSourceModel(SourceModel model) {
		sourceModel = model;
	}
	
	public double getObservingTime() {
		double t = 0.0;
		int skipFlags = ~(Frame.CHOP_LEFT | Frame.CHOP_RIGHT);
		for(IntegrationType integration : this) t += integration.getFrameCount(skipFlags) * integration.instrument.integrationTime;
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
		try { option.mapValueTo("file"); }
		catch(LockedException e) {}
		
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
		
		notify("Written log to " + log.getFileName());
	}
	
	
	@Override
	public Object getTableEntry(String name) {
		
		
		if(horizontal == null) horizontal = equatorial.toHorizontal(site, LST);
		
		Object modelEntry = null;
		if(sourceModel != null) modelEntry = sourceModel.getTableEntry(name);
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
			return getPointingData().getTableEntry(name.substring(4));
		}
		else if(name.startsWith("src.")) {
			if(pointing == null) return "---";
			if(!(sourceModel instanceof AstroMap)) return "---";
			Map2D map = ((AstroMap) sourceModel).map;
			Unit sizeUnit = new Unit(instrument.getSizeName(), instrument.getSizeUnitValue());
			return pointing.getRepresentation(map.getGrid()).getData(sizeUnit).getTableEntry(name.substring(4));
		}
		else if(name.equals("object")) return sourceName;
		else if(name.equals("id")) return getID();
		else if(name.equals("serial")) return serialNo;
		else if(name.equals("MJD")) return MJD;
		else if(name.equals("UT")) return (MJD - Math.floor(MJD)) * Unit.day;
		else if(name.equals("UTh")) return (MJD - Math.floor(MJD)) * 24.0;
		else if(name.equals("PA")) return getPA();
		else if(name.equals("PAd")) return getPA() / Unit.deg;
		else if(name.equals("AZ")) return horizontal.AZ();
		else if(name.equals("EL")) return horizontal.EL();
		else if(name.equals("RA")) return equatorial.RA() / Unit.timeAngle;
		else if(name.equals("DEC")) return equatorial.DEC();
		else if(name.equals("AZd")) return horizontal.AZ() / Unit.deg;
		else if(name.equals("ELd")) return horizontal.EL() / Unit.deg;
		else if(name.equals("RAd")) return equatorial.RA() / Unit.deg;
		else if(name.equals("RAh")) return ((equatorial.RA() + 2.0 * Math.PI) / Unit.hourAngle) % 24.0;
		else if(name.equals("DECd")) return equatorial.DEC() / Unit.deg;
		else if(name.equals("epoch")) return equatorial.epoch.toString();
		else if(name.equals("epochY")) return equatorial.epoch.getYear();
		else if(name.equals("LST")) return LST;
		else if(name.equals("LSTh")) return LST / Unit.hour;
		else if(name.equals("date")) {
			AstroTime time = new AstroTime();
			time.setMJD(MJD);
			return time.getDate();
		}
		else if(name.equals("obstime")) return getObservingTime() / Unit.sec;
		else if(name.equals("obsmins")) return getObservingTime() / Unit.min;
		else if(name.equals("obshours")) return getObservingTime() / Unit.hour;
		else if(name.equals("weight")) return weight;
		else if(name.equals("frames")) return getFrameCount(~0); 
		else if(name.equals("project")) return project;
		else if(name.equals("observer")) return observer; 
		else if(name.equals("descriptor")) return descriptor;
		else if(name.equals("creator")) return creator;
		else if(name.equals("integrations")) return size();
		else if(name.equals("generation")) return getSourceGeneration();
		else if(this instanceof Weather) {	
			if(name.equals("Tamb")) return ((Weather) this).getAmbientKelvins() - Constant.zeroCelsius;
			else if(name.equals("humidity")) return ((Weather) this).getAmbientHumidity();
			else if(name.equals("pressure")) return ((Weather) this).getAmbientPressure() / Unit.hPa;
			else if(name.equals("windspeed")) return ((Weather) this).getWindSpeed() / (Unit.m / Unit.s);
			else if(name.equals("windpk")) return ((Weather) this).getWindPeak() / (Unit.m / Unit.s);
			else if(name.equals("winddir"))	return ((Weather) this).getWindDirection() / Unit.deg;
		}
		
		return getFirstIntegration().getTableEntry(name);
	}
	
	public int getSourceGeneration() {
		int max = 0;
		for(Integration<?,?> integration : this) max = Math.max(integration.sourceGeneration, max);
		return max;		
	}
	
	public void writeProducts() {
		for(Integration<?,?> integration : this) integration.writeProducts();
		
		if(!hasOption("lab")) {
		    reportFocus();
		    reportPointing();
		}
		    
		if(hasOption("log")) {
			try { writeLog(option("log"), CRUSH.workPath + File.separator + instrument.getName() + ".log"); }
			catch(IOException e) {
				warning("Could not write log.");
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}
	}
	
	public void reportFocus() {
		if(pointing != null) {
			CRUSH.result(this, "Instant Focus Results for Scan " + getID() + ":\n\n" + getFocusString());
		}
		else if(hasOption("pointing")) if(sourceModel.isValid()) {
			Configurator pointingOption = option("pointing");
 			if(pointingOption.equals("suggest") || pointingOption.equals("auto")) 
				warning("Cannot suggest focus for scan " + getID() + ".");
		}
	}
	
	
	public void reportPointing() {
		if(pointing != null) {
			CRUSH.result(this, "Pointing Results for Scan " + getID() + ":\n\n" + getPointingString());
		}
		else if(hasOption("pointing")) if(sourceModel.isValid()) {
			Configurator pointingOption = option("pointing");
			if(pointingOption.equals("suggest") || pointingOption.equals("auto"))
				warning("Cannot suggest pointing for scan " + getID() + ".");
		}
	}
	
	@SuppressWarnings("unchecked")
	public ArrayList<Scan<InstrumentType, IntegrationType>> split() {
		info("Splitting subscans into separate scans.");
		ArrayList<Scan<InstrumentType, IntegrationType>> scans = new ArrayList<Scan<InstrumentType, IntegrationType>>();
		for(IntegrationType integration : this) {
			Scan<InstrumentType, IntegrationType> scan = (Scan<InstrumentType, IntegrationType>) clone();
			if(size() > 1) scan.isSplit = true;
			scan.clear();
			scan.instrument = integration.instrument;
			integration.scan = scan;
			scan.add(integration);
			scans.add(scan);
		}
		return scans;
	}

	public void updateGains(final String modalityName) {		
		boolean isRobust = false;
		if(!hasOption("gains")) return;
		if(hasOption("correlated." + modalityName + ".nogains")) return;
		
		Configurator gains = option("gains");
		
		try { gains.mapValueTo("estimator"); }
		catch(LockedException e) {} // TODO...
	
		if(gains.isConfigured("estimator")) if(gains.get("estimator").equals("median")) isRobust = true; 
		
		WeightedPoint[] G = new WeightedPoint[instrument.storeChannels];
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
			catch(Exception e) { error(e); }	
		}
		
		if(!gotGains) return;
		
		
		// Apply the gain increment
		for(IntegrationType integration : this) {
			Modality<?> modality = integration.instrument.modalities.get(modalityName);
			boolean isFlagging = false; 
			
			try { isFlagging |= modality.applyGains(G, integration); }
			catch(Exception e) { error(e); }
			
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
		if(hasOption("correlated." + modalityName + ".span")) updateGains(modalityName);
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
		Offset2D relative = getNativePointingIncrement(pointing);
		Offset2D absolute = getNativePointing(pointing);
		
		DataTable data = new DataTable();
		
		String nameX = "X";
		String nameY = "Y";
		Class<?> coordinateClass = relative.getCoordinateClass();
		
		try {
		    SphericalCoordinates coords = (SphericalCoordinates) coordinateClass.newInstance();
		    CoordinateSystem system = coords.getCoordinateSystem();
		    nameX = system.get(0).getShortLabel();
		    nameY = system.get(1).getShortLabel();
		}
		catch(Exception e) { error(e); }
		
		Unit sizeUnit = instrument.getSizeUnit();
		
		
		data.new Entry("dX", relative.x(), sizeUnit);
		data.new Entry("dY", relative.y(), sizeUnit);
		
		data.new Entry("d" + nameX, relative.x(), sizeUnit);
		data.new Entry("d" + nameY, relative.y(), sizeUnit);
	
		data.new Entry(nameX, absolute.x(), sizeUnit);
		data.new Entry(nameY, absolute.y(), sizeUnit);
		
		// Also print Nasmyth offsets if applicable...
		if(instrument.mount == Mount.LEFT_NASMYTH || instrument.mount == Mount.RIGHT_NASMYTH) {
			Vector2D nasmyth = getNasmythOffset(relative);
			
			data.new Entry("dNasX", nasmyth.x(), sizeUnit);
			data.new Entry("dNasY", nasmyth.y(), sizeUnit);
			
			nasmyth = getNasmythOffset(absolute);
			data.new Entry("NasX", nasmyth.x(), sizeUnit);
			data.new Entry("NasY", nasmyth.y(), sizeUnit);
		}
		
		Asymmetry2D asym = getSourceAsymmetry(pointing);
		data.new Entry("asymX", 100.0 * asym.getX().value(), "%");
		data.new Entry("asymY", 100.0 * asym.getY().value(), "%");
		data.new Entry("dasymX", 100.0 * asym.getX().rms(), "%");
		data.new Entry("dasymY", 100.0 * asym.getY().rms(), "%");
	
		if(pointing instanceof EllipticalSource) {
			EllipticalSource ellipse = (EllipticalSource) pointing;
			Unit deg = Unit.get("deg");
			
			DataPoint elongation = ellipse.getElongation();
			data.new Entry("elong", 100.0 * elongation.value(), "%");
			data.new Entry("delong", 100.0 * elongation.rms(), "%");
			
			DataPoint angle = ellipse.getAngle();
			data.new Entry("angle", angle.value(), deg);
			data.new Entry("dangle", angle.value(), deg);
			
			DataPoint elongationX = getSourceElongationX(ellipse);
			data.new Entry("elongX", 100.0 * elongationX.value(), "%");
			data.new Entry("delongX", 100.0 * elongationX.rms(), "%");	
		}
		
		return data;
	}	
	
	public String getPointingString() {
		String info = "";
			
		if(sourceModel instanceof AstroMap) {
			Observation2D map = ((AstroMap) sourceModel).map;
			info += pointing.pointingInfo(map) + "\n";
		}
		
		info += getPointingString(getNativePointingIncrement(pointing));
		return info;
	}
	
	
	public Asymmetry2D getSourceAsymmetry(CircularRegion region) {
		if(!(sourceModel instanceof AstroMap)) return null;
		
		Range radialRange = new Range(
		        instrument.getPointSize(),
		        (hasOption("focus.r") ? option("focus.r").getDouble() : 2.5) * instrument.getPointSize()
		);
		
		
		AstroMap source = ((AstroMap) sourceModel); 
		AstroSystem system = source.astroSystem();
		
		CircularRegion.Representation r = region.getRepresentation(source.getGrid());
		
		if(!(system.isEquatorial() || system.isHorizontal())) return r.getAsymmetry2D(source.map, 0.0, radialRange);
		   
		
		boolean isGroundEquatorial = this instanceof GroundBased && system.isEquatorial();
		double angle = isGroundEquatorial ? this.getPA() : 0.0;

		return r.getAsymmetry2D(source.map.getSignificance(), angle, radialRange);
	}
	
	public DataPoint getSourceElongationX(EllipticalSource ellipse) {
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
				getSourceElongationX((EllipticalSource) pointing) : null;
		
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
	
	
	
	protected String getPointingString(Offset2D nativePointing) {	
	    if(nativePointing == null) return "";
		// Print the native pointing offsets...
		String text = "";
		
		double sizeUnit = instrument.getSizeUnitValue();
		String sizeName = instrument.getSizeName();
		
		String nameX = "x";
		String nameY = "y";
		
		try {
		    SphericalCoordinates coords = (SphericalCoordinates) nativePointing.getCoordinateClass().newInstance();
		    CoordinateSystem system = coords.getLocalCoordinateSystem();
		    nameX = system.get(0).getShortLabel();
		    nameY = system.get(1).getShortLabel();
		}
		catch(Exception e) { error(e); }
			
		text += "  Offset: ";
		text += Util.f1.format(nativePointing.x() / sizeUnit) + ", " + Util.f1.format(nativePointing.y() / sizeUnit) + " " 
			+ sizeName + " (" + nameX + "," + nameY + ")";
		
		// Also print Nasmyth offsets if applicable...
		if(instrument.mount == Mount.LEFT_NASMYTH || instrument.mount == Mount.RIGHT_NASMYTH) {
			Vector2D nasmyth = getNasmythOffset(nativePointing);
			text += "\n  Offset: ";		
			text += Util.f1.format(nasmyth.x() / sizeUnit) + ", " + Util.f1.format(nasmyth.y() / sizeUnit) + " " 
				+ sizeName + " (nasmyth)";
		}
		
		return text;
	}	
	



	public Vector2D getEquatorialPointing(GaussianSource source) {
		if(!source.getCoordinates().getClass().equals(sourceModel.getReference().getClass()))
			throw new IllegalArgumentException("pointing source is in a different coordinate system from source model.");
		
		
		EquatorialCoordinates sourceCoords = null;
		EquatorialCoordinates reference = null;
		
		if(source.getCoordinates() instanceof EquatorialCoordinates) {
			sourceCoords = (EquatorialCoordinates) source.getCoordinates();
			reference = (EquatorialCoordinates) sourceModel.getReference();
			if(!sourceCoords.epoch.equals(equatorial.epoch)) sourceCoords.precess(equatorial.epoch);
		}
		else {
			sourceCoords = (EquatorialCoordinates) equatorial.clone();
			reference = (EquatorialCoordinates) equatorial.clone();
			((CelestialCoordinates) source.getCoordinates()).toEquatorial(sourceCoords);
			((CelestialCoordinates) sourceModel.getReference()).toEquatorial(reference);
		}
			
		return sourceCoords.getOffsetFrom(reference);
	}
	
	
	
	public Offset2D getNativePointing(GaussianSource source) {
		Offset2D pointing = getNativePointingIncrement(source);
		if(pointingCorrection != null) pointing.add(pointingCorrection);
		return pointing;
	}
	
	public Offset2D getNativePointingIncrement(GaussianSource source) {
		if(!source.getCoordinates().getClass().equals(sourceModel.getReference().getClass()))
			throw new IllegalArgumentException("pointing source is in a different coordinate system from source model.");
		
		SphericalCoordinates sourceCoords = (SphericalCoordinates) source.getCoordinates();
		SphericalCoordinates nativeCoords = getNativeCoordinates();
		
		SphericalCoordinates reference = (SphericalCoordinates) sourceModel.getReference();
		
		if(sourceCoords.getClass().equals(nativeCoords.getClass())) {
		    return new Offset2D(sourceModel.getReference(), sourceCoords.getOffsetFrom(reference));
        }
		else if(sourceCoords instanceof EquatorialCoordinates)
			return getNativeOffsetOf(new Offset2D(sourceModel.getReference(), sourceCoords.getOffsetFrom(reference)));
		else if(sourceCoords instanceof CelestialCoordinates) {
			EquatorialCoordinates sourceEq = ((CelestialCoordinates) sourceCoords).toEquatorial();
			EquatorialCoordinates refEq = ((CelestialCoordinates) sourceModel.getReference()).toEquatorial();
			return getNativeOffsetOf(new Offset2D(refEq, sourceEq.getOffsetFrom(refEq)));
		}
		else if(sourceCoords instanceof FocalPlaneCoordinates) {
		    Vector2D offset = sourceCoords.getOffsetFrom(reference);
		    offset.rotate(-0.5*(getFirstIntegration().getFirstFrame().getRotation() + getLastIntegration().getLastFrame().getRotation()));
		    return new Offset2D(new FocalPlaneCoordinates(), offset);
		}
		
		return null;
	}
 
	public Offset2D getNativeOffsetOf(Offset2D equatorial) {
	    if(!equatorial.getCoordinateClass().equals(EquatorialCoordinates.class))
	        throw new IllegalArgumentException("not an equatorial offset");
	    
	    // Equatorial offset (RA/DEC)
        Vector2D offset = new Vector2D(equatorial);
        
        // Rotate to Horizontal...
        Vector2D from = (Vector2D) offset.clone();
        ((HorizontalFrame) getFirstIntegration().getFirstFrame()).equatorialToNative(from);
        Vector2D to = (Vector2D) offset.clone();
        ((HorizontalFrame) getLastIntegration().getLastFrame()).equatorialToNative(to);
        offset.setX(0.5 * (from.x() + to.x()));
        offset.setY(0.5 * (from.y() + to.y()));
        return new Offset2D(getNativeCoordinates(), offset);
	}

	// Inverse rotation from Native to Nasmyth...
	public Vector2D getNasmythOffset(Offset2D pointing) {
		SphericalCoordinates coords = getNativeCoordinates();
		if(!pointing.getCoordinateClass().equals(coords.getClass())) 
		    throw new IllegalArgumentException("non-native pointing offset."); 
		
		double sinA = instrument.mount == Mount.LEFT_NASMYTH ? -coords.sinLat() : coords.sinLat();
		double cosA = coords.cosLat();
		
		
		// Inverse rotation from Native to Nasmyth...
		Vector2D nasmyth = new Vector2D();
		nasmyth.setX(cosA * pointing.x() + sinA * pointing.y());
		nasmyth.setY(cosA * pointing.y() - sinA * pointing.x());
		
		return nasmyth;
	}
	
	@Override
	public String toString() { return "Scan " + getID(); }
	
	@SuppressWarnings("unchecked")
	public void segmentTo(double segmentTime) {
		if(size() > 1) mergeIntegrations();
		
		IntegrationType merged = get(0);
		clear();
		
		int nT = merged.framesFor(segmentTime);
		int N = ExtraMath.roundupRatio(merged.size(), nT);
		
		if(N <= 1) return;
		
		ensureCapacity(N);
		
		info("Segmenting into " + N + " integrations.");
		
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

	
	@Override
    public void info(String message) { CRUSH.info(this, message); }
    
    @Override
    public void notify(String message) { CRUSH.notify(this, message); }
    
    @Override
    public void debug(String message) { CRUSH.debug(this, message); }
    
    @Override
    public void warning(String message) { CRUSH.warning(this, message); }

    @Override
    public void warning(Exception e, boolean debug) { CRUSH.warning(this, e, debug); }

    @Override
    public void warning(Exception e) { CRUSH.warning(this, e); }

    @Override
    public void error(String message) { CRUSH.error(this, message); }
    
    @Override
    public void error(Throwable e, boolean debug) { CRUSH.error(this, e, debug); }

    @Override
    public void error(Throwable e) { CRUSH.error(this, e); }
	
	
}
