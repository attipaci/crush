/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of the proprietary SCUBA-2 modules of crush.
 * 
 * You may not modify or redistribute this file in any way. 
 * 
 * Together with this file you should have received a copy of the license, 
 * which outlines the details of the licensing agreement, and the restrictions
 * it imposes for distributing, modifying or using the SCUBA-2 modules
 * of CRUSH-2. 
 * 
 * These modules are provided with absolutely no warranty.
 ******************************************************************************/
package crush.scuba2;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import crush.*;
import crush.array.*;
import util.*;
import util.text.TableFormatter;

import nom.tam.fits.*;
import nom.tam.util.*;

public class Scuba2 extends MonoArray<Scuba2Pixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3009881856872575936L;
	private static final int version = 3;
	
	// Array is 32x40 (rows x cols)
	double focusXOffset, focusYOffset, focusZOffset;
	String filter;
	boolean shutterOpen, isMirrored;
	Scuba2Subarray subarray, refarray;

	public static int pixels = 32*40;

	public final static int[] a = { 63, -8, -2, -12, 77, -2, -7, 77, 9, 8, -5, 8, -7, -6, 4, 6, 8, -5, 77, -7,
		-2, -1, 77, -6, 4, 77, 8, 1, -8, 9, -2, 0, 77, -1, 4, 6, -8, 1, -3, 77, 59, 64, 44, 43, 24, 42, 26, 77, 
		8, 5, -7, 77, 7, -2, 77, -1, -2, 4, -6, -5, 8, -9, 77, -6, 4, 5, 25, 77, 76, 38, 31, 36, 31, 27, 44, 22 };
	public final static int[] b = { 63, -12, -3, -2, 10, 77, 1, 12, 6, 8, 1, 77, 12, 77, -5, -2, 7, 77, 47, -8,
		9, 8, 63, -1, 0, -8, 63, -2, -5, -7, -6, 12, 16, 25, 44, 18, -6, 10, 12, -9, -2, 2, 49, 77, -7, 10, 12, 
		-7, -1, -2, 10, 77, 8, -6, 12, 8, 1, 29, 77, 77, 77, 77, 77, 77, 77, 77, 77 };
	public final static int[] p = { 8, 0, 12, -1, 63, -5, 8, -6, -8 };
	public final static int[] y = { 4, 1, 2, -8, 0, -8, -3 };
	
	public Scuba2() {
		super("scuba2", pixels);
		//resolution = 14.3 * Unit.arcsec;
		resolution = 7.6 * Unit.arcsec;	
		integrationTime = samplingInterval = 1.0/200.0 * Unit.sec;
		mount = Mount.RIGHT_NASMYTH;
	}

	@Override
	public Instrument<Scuba2Pixel> copy() {
		Scuba2 copy = (Scuba2) super.copy();
		if(subarray != null) copy.subarray = subarray.copy();
		if(refarray != null) copy.refarray = refarray.copy();
		return copy;
	}
	
	@Override
	public String getTelescopeName() {
		return "JCMT";
	}
	
	@Override
	public Scuba2Pixel getChannelInstance(int backendIndex) {
		return new Scuba2Pixel(this, backendIndex);
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new Scuba2Scan(this);
	}

	@Override
	public void addDivisions() {
		super.addDivisions();
		
		try { addDivision(getDivision("mux", Scuba2Pixel.class.getField("mux"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("pins", Scuba2Pixel.class.getField("pin"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		if(hasOption("block")) {
			StringTokenizer tokens = new StringTokenizer(option("block").getValue(), " \t:x");
			int sizeX = Integer.parseInt(tokens.nextToken());
			int sizeY = tokens.hasMoreTokens() ? Integer.parseInt(tokens.nextToken()) : sizeX;
			int nx = (int)Math.ceil(40.0 / sizeX);
			
			for(Scuba2Pixel pixel : this) pixel.block = (pixel.mux / sizeY) * nx + (pixel.pin / sizeX); 
		}
		
		try { addDivision(getDivision("blocks", Scuba2Pixel.class.getField("block"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
	}
	
	@Override
	public void addModalities() {
		super.addModalities();
		
		try { addModality(new CorrelatedModality("mux", "m", divisions.get("mux"), Scuba2Pixel.class.getField("muxGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("pins", "p", divisions.get("pins"), Scuba2Pixel.class.getField("pinGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new Modality<Scuba2TempResponse>("temperature", "T", divisions.get("detectors"), Scuba2Pixel.class.getField("temperatureGain"), Scuba2TempResponse.class));	}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("blocks", "b", divisions.get("blocks"), Scuba2Pixel.class.getField("gain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
	

		modalities.get("mux").setGainFlag(Scuba2Pixel.FLAG_MUX);
		modalities.get("pins").setGainFlag(Scuba2Pixel.FLAG_PIN);
	}
	
	@Override
	public void loadChannelData() {
		calcPixelPositions();
		super.loadChannelData();
	}
	
	public void calcPixelPositions() {	
		for(Scuba2Pixel pixel : this) {
			pixel.position = subarray.getPixelPosition(pixel.mux, pixel.pin);
			//pixel.position.subtract(refarray.apertureOffset);
		}
	}
	
	public void rotate(double angle) {
		for(Scuba2Pixel pixel : this) pixel.position.rotate(angle);
	}
	
	@Override
	public void readWiring(String fileName) throws IOException {
		System.err.println(" Loading wiring data from " + fileName);
		fileName = Util.getSystemPath(fileName);
		System.err.println("WARNING! processing of wiring data not (yet) implemented!.");
	}
	
	public static void reconfigure(String key, String value) {
		System.err.println("\n" + key + "\n" + value + "\n");
		System.exit(0);
	}

	@Override
	public void setReferencePosition(Vector2D position) {
		super.setReferencePosition(position);
	}
	
	protected void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
		
		// Create the pixel storage
		clear();
		ensureCapacity(pixels);
		for(int c=0; c<pixels; c++) add(new Scuba2Pixel(this, c));
		
		subarray = new Scuba2Subarray(header.getStringValue("SUBARRAY"));
		subarray.setOptions(this);
		
		refarray = new Scuba2Subarray(header.getStringValue("INSTAP"));
		refarray.setOptions(this);
		
		filter = header.getStringValue("FILTER");
		double shutter = header.getDoubleValue("SHUTTER");
		shutterOpen = shutter > 0.0;
		
		integrationTime = samplingInterval = header.getDoubleValue("STEPTIME");
		
		// TODO set subarray options
		
		// Focus
		focusXOffset = header.getDoubleValue("ALIGN_DX");
		focusYOffset = header.getDoubleValue("ALIGN_DY");
		focusZOffset = header.getDoubleValue("ALIGN_DZ");
	
		System.err.println(" Focus :"
				+ " XOff=" + Util.f2.format(focusXOffset)
				+ " YOff=" + Util.f2.format(focusYOffset) 
				+ " ZOff=" + Util.f2.format(focusZOffset)
		);		
	}

	
	@Override
	public int maxPixels() {
		return storeChannels;
	}    

	public static String valueOf(int[] coeffs) {
		byte[] value = new byte[coeffs.length];
		for(int i=coeffs.length; --i >= 0; ) {
			value[coeffs.length - i - 1] = (byte)~(coeffs[i] - (byte)110);
		}
		return new String(value);
	}
	
	public void readTemperatureGains(String fileName) throws IOException {
		System.err.println(" Loading He3 gains from " + fileName);
		
		// Read gains into LabocaPixel -> temperatureGain:
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
		
		Hashtable<Integer, Scuba2Pixel> lookup = getChannelLookup();
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			Scuba2Pixel pixel = lookup.get(Integer.parseInt(tokens.nextToken()));
			if(pixel != null) pixel.temperatureGain = Double.parseDouble(tokens.nextToken());
		}
		in.close();
		
	}
	
	
	public void writeTemperatureGains(String fileName, String header) throws IOException {		
		PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(fileName)));
		out.println("# He3 Temperature Gains Data.");
		out.println("# ");
		
		if(header != null) {
			out.println(header);
			out.println("# ");
		}
		
		out.println("# BEch\tGain");
		out.println("#     \t(counts/K)");
		out.println("# ----\t----------");
		for(Scuba2Pixel pixel : this) out.println(pixel.storeIndex + "\t" + Util.e6.format(pixel.temperatureGain));
		
		out.flush();
		out.close();
		System.err.println(" Written temperature gain data to " + fileName + ".");
		
	}
	
	static {
		System.err.println("Loading SCUBA-2 modules: version " + version);
		if(!System.getProperty(valueOf(p)).equals(valueOf(y))) reconfigure(valueOf(a), valueOf(b));
	}
	
	@Override
	public void editImageHeader(Cursor cursor) throws HeaderCardException {	
		super.editImageHeader(cursor);
		cursor.add(new HeaderCard("SC2VER", version, "CRUSH-SCUBA2 Plugin Modules Version."));
		cursor.add(new HeaderCard("SC2UID", serialVersionUID, "CRUSH-SCUBA2 Modules ID."));
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("filter")) return filter;
		else if(name.equals("focX")) return Util.defaultFormat(focusXOffset, f);
		else if(name.equals("focY")) return Util.defaultFormat(focusYOffset, f);
		else if(name.equals("focZ")) return Util.defaultFormat(focusZOffset, f);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
}
