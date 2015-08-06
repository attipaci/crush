/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import kovacs.math.Vector2D;
import kovacs.text.TableFormatter;
import kovacs.util.*;
import crush.*;
import crush.array.*;
import nom.tam.fits.*;
import nom.tam.util.*;

public class Scuba2 extends Array<Scuba2Pixel, Scuba2Pixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7284330045075075340L;
	private static final int version = 4;
	
	// Array is 32x40 (rows x cols)
	double focusXOffset, focusYOffset, focusZOffset;
	String filter;
	boolean shutterOpen, isMirrored;
	Scuba2Subarray[] subarray;
	int refArrayNo;

	public static int pixels = 32*40;
	
	public Scuba2() {
		super("scuba2", new SingleColorLayout<Scuba2Pixel>(), pixels);
		setResolution(14.3 * Unit.arcsec);
		//setResolution(7.6 * Unit.arcsec);	
		integrationTime = samplingInterval = 1.0/200.0 * Unit.sec;
		mount = Mount.RIGHT_NASMYTH;
	}

	@Override
	public Instrument<Scuba2Pixel> copy() {
		Scuba2 copy = (Scuba2) super.copy();
		if(subarray != null) {
			copy.subarray = new Scuba2Subarray[subarray.length];
			for(int i=subarray.length; --i >= 0; ) copy.subarray[i] = subarray[i].copy();
		}
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
	public void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("subarrays", Scuba2Pixel.class.getField("subarrayNo"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
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
	public void initModalities() {
		super.initModalities();
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("subarrays", "S", divisions.get("subarrays"), Scuba2Pixel.class.getField("subarrayGain"));		
			muxMode.setGainFlag(Scuba2Pixel.FLAG_SUBARRAY);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }	
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), Scuba2Pixel.class.getField("muxGain"));		
			muxMode.setGainFlag(Scuba2Pixel.FLAG_MUX);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }	
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("pins", "p", divisions.get("pins"), Scuba2Pixel.class.getField("pinGain"));		
			muxMode.setGainFlag(Scuba2Pixel.FLAG_PIN);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }	
			
		try { addModality(new Modality<Scuba2TempResponse>("temperature", "T", divisions.get("detectors"), Scuba2Pixel.class.getField("temperatureGain"), Scuba2TempResponse.class));	}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("blocks", "b", divisions.get("blocks"), Scuba2Pixel.class.getField("gain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
	}
	
	@Override
	public void loadChannelData() {
		calcPixelPositions();
		super.loadChannelData();
	}
	
	public void calcPixelPositions() {	
		for(Scuba2Pixel pixel : this) {
			pixel.position = subarray[pixel.subarrayNo].getPixelPosition(pixel.mux, pixel.pin);
			//pixel.position.subtract(refarray.apertureOffset);
		}
	}
	
	@Override
	public void readWiring(String fileName) throws IOException {
		//System.err.println(" Loading wiring data from " + fileName);
		System.err.println("WARNING! processing of wiring data not (yet) implemented!.");
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
		
		Hashtable<Integer, Scuba2Pixel> lookup = getFixedIndexLookup();
		
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
		for(Scuba2Pixel pixel : this) out.println(pixel.getFixedIndex() + "\t" + Util.e6.format(pixel.temperatureGain));
		
		out.flush();
		out.close();
		System.err.println(" Written temperature gain data to " + fileName + ".");
		
	}
	
	static {
		System.err.println("Loading SCUBA-2 modules: version " + version);
		//if(!System.getProperty(valueOf(p)).equals(valueOf(y))) reconfigure(valueOf(a), valueOf(b));
	}
	
	@Override
	public void editImageHeader(List<Scan<?,?>> scans, Header header, Cursor cursor) throws HeaderCardException {	
		super.editImageHeader(scans, header, cursor);
		cursor.add(new HeaderCard("SC2VER", version, "CRUSH-SCUBA2 Plugin Modules Version."));
		cursor.add(new HeaderCard("SC2UID", serialVersionUID, "CRUSH-SCUBA2 Modules ID."));
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("filter")) return filter;
		else if(name.equals("foc.X")) return Util.defaultFormat(focusXOffset, f);
		else if(name.equals("foc.Y")) return Util.defaultFormat(focusYOffset, f);
		else if(name.equals("foc.Z")) return Util.defaultFormat(focusZOffset, f);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	@Override
	public String getCommonHelp() {
		return super.getCommonHelp() + 
				"     -450um         Select 450um imaging mode.\n" +
				"     -850um         Select 850um imaging mode (default).\n";
	}
	
	@Override
	public String getDataLocationHelp() {
		return super.getDataLocationHelp() +
				"     -date=         YYYYMMDD when the data was collected.\n" +
				"     -ndf2fits=     The path to the ndf2fits executable. Required for\n" +
				"                    reading native SDF data.\n" +
				"     -convert       Convert the listed scans from SDF to FITS and exit.\n" +
				"                    (no reduction wil take place with this option.)\n" +
				"                    The FITS files will be writter to the location set\n" +
				"                    by the 'outpath' option.\n";
	}
	
	


	
}
