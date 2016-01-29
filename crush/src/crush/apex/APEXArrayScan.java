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
// Copyright (c) 2009 Attila Kovacs

package crush.apex;

import crush.*;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroSystem;
import jnum.astro.CelestialCoordinates;
import jnum.astro.CoordinateEpoch;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.GeodeticCoordinates;
import jnum.astro.HorizontalCoordinates;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import nom.tam.fits.*;

import java.io.*;
import java.util.*;



public class APEXArrayScan<InstrumentType extends APEXCamera<?>, SubscanType extends APEXArraySubscan<InstrumentType, ?>> 
extends Scan<InstrumentType, SubscanType> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6352446637906429368L;

	//public boolean isChopped = false;
	
	public Class<? extends SphericalCoordinates> nativeSystem;
	public Class<? extends SphericalCoordinates> basisSystem;
	
	//public double chopperThrow, chopperFrequency;
	
	public Chopper chopper;
	
	public String type, mode, geometry, direction;
	
	Vector2D pointingOffset;
	
	private Vector<Fits> openFits = new Vector<Fits>();
	
	public APEXArrayScan(InstrumentType instrument) {
		super(instrument);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Object clone() {
		APEXArrayScan<InstrumentType, SubscanType> clone = (APEXArrayScan<InstrumentType, SubscanType>) super.clone();
		clone.openFits = new Vector<Fits>();
		return clone;
	}
	
	@Override
	public void mergeIntegrations() {
		super.mergeIntegrations();
		get(0).nodPhase = 0;
	}
	
	
	public boolean readMonitor() { return false; }
	
	@Override
	public void read(String descriptor, boolean readFully) throws Exception {
		String project = hasOption("project") ? option("project").getValue() : null;
		
		try { read(project, descriptor, readFully); }
		catch(FileNotFoundException e) {
			if(hasOption("debug")) e.printStackTrace();
			
			String message = "Cannot read scan " + descriptor + "\n"
				+ "     : project = " + project + "\n"
				+ "     : datapath = " + getDataPath() + "\n"
				+ " ----> Check that 'datapath' and 'project' settings are correct.\n"
				+ " ----> Check capitalization in project directory names.\n"
				+ " ----> Check that the datafile or directory exists in the specified location.";
			throw new FileNotFoundException(message);		
		}
		
		if(readFully) if(chopper == null) mergeIntegrations();
	}
	
	public String getTelescopeName() { return "APEX"; }
		
	public String getFEBECombination() {
		return option("febe").getValue();
	}	

	@SuppressWarnings("unchecked")
	@Override
	public SubscanType getIntegrationInstance() {
		return (SubscanType) new APEXArraySubscan<InstrumentType, APEXFrame>(this);
	}
	
	public String getFileName(String path, String spec, String projectID) throws FileNotFoundException {
		if(path == null) throw new FileNotFoundException("Undefined 'datapath'. ");
		if(spec == null) throw new FileNotFoundException("Undefined scan specification. Need a filename, scan number, or range. ");
		if(projectID == null) throw new FileNotFoundException("Undefined 'project'. ");
		
		projectID = projectID.toUpperCase();
		
		// Check if there's an exact match to the specification...
		File match = new File(spec);
		if(match.exists()) return spec;
		
		// Try see if there is an exact match with datapath prepended...
		match = new File(path + spec);
		if(match.exists()) return path + spec;
		
		// Otherwise, see if anything in the path matches...
		File root = new File(path);
		String[] fileName = root.list();
		
		if(fileName == null) throw new FileNotFoundException("Incorrect path.");
		
		for(int i=0; i<fileName.length; i++) {
			// If there is a project subdirectory, then try in there...
			// (keep going if that did not work...)
			if(fileName[i].startsWith(projectID)) {
				try { return getFileName(path + fileName[i] + File.separator, spec, projectID); }
				catch(FileNotFoundException e) {}
			}
			// Otherwise, check for matches in here...
			if(fileName[i].startsWith(getTelescopeName() + "-" + spec)) {
				String core = fileName[i];
				if(core.endsWith(".Z")) core = core.substring(0, core.length() - 2);
				if(core.endsWith(".gz")) core = core.substring(0, core.length() - 3);
				if(core.endsWith(".fits")) core = core.substring(0, core.length() - 5);
				if(core.endsWith(projectID)) return path + fileName[i];
			}
		}
		throw new FileNotFoundException("Cannot find FITS data file. ");
	}
	

	public void read(String projectID, String spec, boolean readFully) throws IOException, FitsException, HeaderCardException {		
		String name = getFileName(getDataPath(), spec, projectID);	
	
		File file = new File(name);
	
		if(file.exists()) {
			System.err.println();
			System.err.println("Scan " + spec + " (details follow...)");
		}
		
		if(file.isDirectory()) {
			System.err.println(" From directory '" + name + "'.");
			try { readScanDirectory(name, "", readFully); }
			catch(Exception e) {
				if(hasOption("debug")) e.printStackTrace();
				try { readScanDirectory(name, ".gz", readFully); }
				catch(Exception e2) { 
					if(hasOption("debug")) e.printStackTrace();
					readScanDirectory(name, ".Z", readFully); 
				}
			}
		}
		else {
			System.err.println(" From file '" + name + "'.");
			readScan(name, readFully);
		}
	}
	
	
	// TODO Currently only the first FEBE combination is read from the data...
	public void readScanDirectory(String dir, String ext, boolean readFully) throws IOException, FitsException, HeaderCardException {	
		ext = ".fits" + ext;
		dir += File.separator;
		
		int subscans = readScanInfo(getHDU(dir + "SCAN" + ext));
		instrument.readPar(getHDU(dir + getFEBECombination() + "-FEBEPAR" + ext));
		instrument.validate(this);
		clear();
		
		closeStreams();
		
		for(int i=0; i<subscans; i++) {
			try {
				SubscanType subscan = getIntegrationInstance();

				System.err.println(" Integration {" + (i+1) + "}:");
				subscan.integrationNo = i;
				
				subscan.readDataPar(getHDU(dir + (i+1) + File.separator + getFEBECombination() + "-DATAPAR" + ext));
				subscan.readData(getHDU(dir + (i+1) + File.separator + getFEBECombination() + "-ARRAYDATA-1" + ext));
				if(readMonitor()) subscan.readMonitor(getHDU(dir + (i+1) + File.separator + "MONITOR" + ext));
				closeStreams();
				
				add(subscan);
			}
			catch(Exception e) { error(e); }
		}
	
	}
	
	public void closeStreams() {
		for(Fits fits : openFits) {
			try { fits.getStream().close();	}
			catch(IOException e) { e.printStackTrace(); }
		}
		openFits.clear();
	}
	
	public void readScan(String fileName, boolean readFully) throws IOException, FitsException, HeaderCardException {	
		File file = new File(fileName);
		if(!file.exists()) throw new FileNotFoundException("Cannot find data file.");
		
		Fits fits = new Fits(file, fileName.endsWith(".gz") | fileName.endsWith(".Z"));	
		BasicHDU<?>[] hdu = fits.read();
		
		// TODO Pick scan and instrument hdu's by name
		int subscans = readScanInfo((BinaryTableHDU) hdu[1]);
		instrument.readPar((BinaryTableHDU) hdu[2]);
		instrument.validate(this);
		clear();
		
		int k=3;
		for(int i=0; i<subscans; i++) {
			try {
				SubscanType subscan = getIntegrationInstance();
				subscan.integrationNo = i;
				
				System.err.println(" Integration {" + (i+1) + "}:");
				
				// HDUs for each integration can come in any order, so check EXTNAME...
				for(int m=0; m<3; m++, k++) {
					BinaryTableHDU table = (BinaryTableHDU) hdu[k];
					String extName = table.getHeader().getStringValue("EXTNAME");
					
					if(extName.equalsIgnoreCase("DATAPAR-MBFITS")) subscan.readDataPar(table);
					else if(extName.equalsIgnoreCase("ARRAYDATA-MBFITS")) subscan.readData(table);
					else if(extName.equalsIgnoreCase("MONITOR-MBFITS")) if(readMonitor()) subscan.readMonitor(table);
				}
				
				add(subscan);
			}
			catch(Exception e) { error(e); }
		}
	
		try { fits.close(); }
		catch(IOException e) {}
		
	}
	
	
	public BinaryTableHDU getHDU(String fileName) throws IOException, FitsException, HeaderCardException {
		File file = new File(fileName);
		if(!file.exists()) throw new FileNotFoundException("Cannot find data file " + fileName);
		
		Fits fits = new Fits(new File(fileName), fileName.endsWith(".gz"));	
		openFits.add(fits);
		BasicHDU<?>[] hdu = fits.read();
		
		return (BinaryTableHDU) hdu[1];
	}
	
	
	public int readScanInfo(BinaryTableHDU hdu) throws IOException, FitsException, HeaderCardException {
		Header header = hdu.getHeader();
		
		// Load any options based on the FITS header...
		instrument.setFitsHeaderOptions(header);
		
		setSerial(header.getIntValue("SCANNUM"));
		type = header.getStringValue("SCANTYPE");
		mode = header.getStringValue("SCANMODE");
		geometry = header.getStringValue("SCANGEOM");
		direction = header.getStringValue("SCANDIR");
		String sourceName = header.getStringValue("OBJECT");
		timeStamp = header.getStringValue("DATE-OBS");
		if(sourceName == null) sourceName = "Undefined";
		setSourceName(sourceName);
		
		pointingOffset = new Vector2D(header.getDoubleValue("IA"), header.getDoubleValue("IE"));
		pointingOffset.scale(Unit.deg);
				
		String dateString = timeStamp.substring(0, timeStamp.indexOf('T'));
		String timeString = timeStamp.substring(timeStamp.indexOf('T') + 1);
		
		observer = header.getStringValue("OBSID");
		project = header.getStringValue("PROJID");
		if(observer == null) observer = "Anonymous";
		else {
			// Remove quotation marks from observer names to avoid errors when writing headers.
			if(observer.contains("'")) observer = observer.replaceAll("'", "");
			if(observer.contains("\"")) observer = observer.replaceAll("\"", "");
		}
		
		if(project == null) project = "Unknown";
		
		System.err.println(" [" + getSourceName() + "] observed on " + dateString + " at " + timeString + " UT by " + observer);
		
		site = new GeodeticCoordinates(header.getDoubleValue("SITELONG") * Unit.deg, 
					header.getDoubleValue("SITELAT") * Unit.deg);
		
		LST = header.getDoubleValue("LST") * Unit.sec;
		setMJD(header.getDoubleValue("MJD"));
	
		if(header.getBooleanValue("WOBUSED")) {
			chopper = new Chopper();
			// WOBDIR ('NONE', ?)
			chopper.amplitude = header.getDoubleValue("WOBTHROW") * Unit.deg;
			chopper.frequency = 1.0 / (header.getDoubleValue("WOBCYCLE") * Unit.sec);
			chopper.positions = 2;
			chopper.angle = 0.0 * Unit.degree;
			System.err.println(" Setting options for chopped photometry.");
			instrument.setOption("chopped");
		}
					
		//isPlanetary = header.getBooleanValue("MOVEFRAM");		
		equatorial = null; horizontal = null;
		
		boolean hasNative = header.findCard("CTYPE1").getComment().contains("Native");
		
		// If there is information on the native frame, then use it. Else, assume horizontal...
		if(hasNative) {
			String label = header.getStringValue("CTYPE1");
			nativeSystem = SphericalCoordinates.getFITSClass(label);
		}
		else nativeSystem = HorizontalCoordinates.class;
			
		// In some cases the reference values of main coordinate system are given in the alternative basis system...
		// This is rather confusing and wrong, but using the comment field one can try to figure out what belongs where...
		boolean confused = hasNative && header.findCard("CRVAL1").getComment().contains("basis");
		
		// The scan coordinates are set to those of the tracking object....
		String keyword = confused ? "CTYPE1B" : "CTYPE1";
		basisSystem = SphericalCoordinates.getFITSClass(header.getStringValue(keyword));
						
		double lon = header.getDoubleValue("BLONGOBJ") * Unit.deg;
		double lat = header.getDoubleValue("BLATOBJ") * Unit.deg;
		
		if(basisSystem == EquatorialCoordinates.class) {
			equatorial = new EquatorialCoordinates(lon, lat, CoordinateEpoch.J2000);	
			calcHorizontal();	
		}
		else if(basisSystem == HorizontalCoordinates.class) {
			horizontal = new HorizontalCoordinates(lon, lat);
			calcEquatorial();
		}
		else {
			try { 
				CelestialCoordinates basisCoords = (CelestialCoordinates) basisSystem.newInstance();
				basisCoords.set(lon, lat);
				equatorial = basisCoords.toEquatorial();
				calcHorizontal();
			}
			catch(Exception e) {
				throw new IllegalStateException("Cannot instantiate " + basisSystem.getName() + ": " + e.getMessage());
			}
		}
		
		// Tracking assumed for equatorial...
		isTracking = !HorizontalCoordinates.class.isAssignableFrom(basisSystem);
		
		return header.getIntValue("NOBS");	
	}	
	
	@Override
	public void editScanHeader(Header header) throws HeaderCardException {	
		super.editScanHeader(header);
		header.addValue("PROJECT", project, "The project ID for this scan");
		header.addValue("BASIS", basisSystem.getSimpleName(), "The coordinates system of the scan.");
	}
	
	@Override
	public String getPointingString(Vector2D pointing) {
		return super.getPointingString(pointing) + "\n\n" +
			"  >>> pcorr " + Util.f1.format(pointing.x() / Unit.arcsec) + "," + Util.f1.format(pointing.y() / Unit.arcsec);
		
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		if(name.equals("chop?")) return Boolean.toString(chopper == null ? false : true);
		else if(name.equals("dir")) return direction;
		else if(name.equals("geom")) return geometry;
		else if(name.equals("planet?")) return Boolean.toString(isMovingObject);
		else if(name.equals("obsmode")) return mode;
		else if(name.equals("obstype")) return type;
		else if(name.equals("dir")) return AstroSystem.getID(basisSystem);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
}
