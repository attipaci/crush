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

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import crush.*;
import crush.array.*;
import util.*;
import util.text.TableFormatter;
import nom.tam.fits.*;

public class Gismo extends MonoArray<GismoPixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3009881856872575936L;

	public static int pixels = 128;
	public Vector2D nasmythOffset;
	
	private Vector2D arrayPointingCenter; // row,col

	double focusXOffset, focusYOffset, focusZOffset;
	int[] biasValue;

	public Gismo() {
		super("gismo", pixels);
		resolution = 16.7 * Unit.arcsec;
		
		arrayPointingCenter = (Vector2D) defaultPointingCenter.clone();
		
		// TODO calculate this?
		integrationTime = samplingInterval = 0.1 * Unit.sec;
	
		mount = Mount.LEFT_NASMYTH;
	}

	@Override
	public Instrument<GismoPixel> copy() {
		Gismo copy = (Gismo) super.copy();
		if(arrayPointingCenter != null) copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.clone();
		if(nasmythOffset != null) copy.nasmythOffset = (Vector2D) nasmythOffset.clone();
		if(biasValue != null) {
			copy.biasValue = new int[biasValue.length];
			System.arraycopy(biasValue, 0, copy.biasValue, 0, biasValue.length);
		}
		return copy;
	}
	
	@Override
	public String getTelescopeName() {
		return "IRAM-30M";
	}
	
	@Override
	public GismoPixel getChannelInstance(int backendIndex) {
		return new GismoPixel(this, backendIndex);
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new GismoScan(this);
	}

	@Override
	public void addDivisions() {
		super.addDivisions();
		
		try { addDivision(getDivision("mux", GismoPixel.class.getField("mux"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("pins", GismoPixel.class.getField("pin"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("cols", GismoPixel.class.getField("col"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("rows", GismoPixel.class.getField("row"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
	}
	
	@Override
	public void addModalities() {
		super.addModalities();
		
		try { 
			CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), GismoPixel.class.getField("muxGain"));
			muxMode.setGainFlag(GismoPixel.FLAG_MUX);
			addModality(muxMode);		
			addModality(muxMode.new CoupledModality("flips", "f", new TraceFlip()));
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }	
		
		// Flips not coupled to MUX gains?		
		//try { addModality(new CorrelatedModality("flips", "f", divisions.get("mux"), GismoPixel.class.getField("flipGain"))); }
		//catch(NoSuchFieldException e) { e.printStackTrace(); }	

		
		try { 
			Modality<?> pinMode = new CorrelatedModality("pins", "p", divisions.get("pins"), GismoPixel.class.getField("pinGain")); 
			pinMode.setGainFlag(GismoPixel.FLAG_PIN);
			addModality(pinMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { 
			Modality<?> colMode = new CorrelatedModality("cols", "c", divisions.get("cols"), GismoPixel.class.getField("colGain")); 
			colMode.setGainFlag(GismoPixel.FLAG_COL);
			addModality(colMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { 
			Modality<?> rowMode = new CorrelatedModality("rows", "r", divisions.get("rows"), GismoPixel.class.getField("rowGain")); 
			rowMode.setGainFlag(GismoPixel.FLAG_ROW);
			addModality(rowMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
	}
	
	@Override
	public void loadChannelData() {
		// Update the pointing centers...
		if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
		
		Vector2D pixelSize = GismoPixel.defaultSize;
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.getX());
		}

		setPlateScale(pixelSize);
			
		super.loadChannelData();
	}
	
	public void setPlateScale(Vector2D size) {
		// Make all pixels the same size. Also calculate their positions...
		for(GismoPixel pixel : this) pixel.size = size;
		
		Vector2D center = GismoPixel.getPosition(size, arrayPointingCenter.getX() - 1.0, arrayPointingCenter.getY() - 1.0);			
		
		// Set the pointing center...
		setReferencePosition(center);
		
		if(hasOption("rotation")) rotate(option("rotation").getDouble() * Unit.deg);
	}
	
	public void rotate(double angle) {
		for(GismoPixel pixel : this) pixel.position.rotate(angle);
	}
	
	// Calculates the offset of the pointing center from the nominal center of the array
	public Vector2D getPointingCenterOffset() {
		Vector2D offset = (Vector2D) arrayPointingCenter.clone();
		offset.subtract(defaultPointingCenter);
		if(hasOption("rotation")) offset.rotate(option("rotation").getDouble() * Unit.deg);
		return offset;
	}
	
	@Override
	public void readWiring(String fileName) throws IOException {
		System.err.println(" Loading wiring data from " + fileName);
			
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		Hashtable<Integer, GismoPixel> lookup = getChannelLookup();
		
		int groupPins = hasOption("correlated.pins.group") ? option("correlated.pins.group").getInt() : 1;
		
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
		 	GismoPixel pixel = lookup.get(Integer.parseInt(tokens.nextToken()));
			pixel.mux = Integer.parseInt(tokens.nextToken());
		 	pixel.pin = Integer.parseInt(tokens.nextToken()) % groupPins;
		}
		
		in.close();
	}

	@Override
	public void setReferencePosition(Vector2D position) {
		for(GismoPixel pixel : this) pixel.calcPosition();
		super.setReferencePosition(position);
	}
	
	
	protected void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
			
		// Focus
		focusXOffset = header.getDoubleValue("FOCUS_XO", Double.NaN) * Unit.mm;
		focusYOffset = header.getDoubleValue("FOCUS_YO", Double.NaN) * Unit.mm;
		focusZOffset = header.getDoubleValue("FOCUS_ZO", Double.NaN) * Unit.mm;

		arrayPointingCenter.setX(header.getDoubleValue("PNTROW", 8.5));
		arrayPointingCenter.setY(header.getDoubleValue("PNTCOL", 4.5));
		
		nasmythOffset = new Vector2D(
				header.getDoubleValue("RXHORI", Double.NaN) + header.getDoubleValue("RXHORICO", 0.0),
				header.getDoubleValue("RXVERT", Double.NaN) + header.getDoubleValue("RXVERTCO", 0.0)
				);
		
		System.err.println(" Focus: dZ = " + Util.f2.format(focusZOffset / Unit.mm) + " mm.");
	}
	
	
	protected void parseOldScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
			
		// Focus
		focusXOffset = header.getDoubleValue("FOCUS_XO") * Unit.mm;
		focusYOffset = header.getDoubleValue("FOCUS_YO") * Unit.mm;
		focusZOffset = header.getDoubleValue("FOCUS_ZO") * Unit.mm;
	
		nasmythOffset = new Vector2D(
				header.getDoubleValue("RXHORI", Double.NaN) + header.getDoubleValue("RXHORICO", 0.0),
				header.getDoubleValue("RXVERT", Double.NaN) + header.getDoubleValue("RXVERTCO", 0.0)
				);
		
		System.err.println(" Focus: dZ = " + Util.f2.format(focusZOffset / Unit.mm) + " mm.");
	}
	
	
	
	
	protected void parseHardwareHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		Object[] row = hdu.getRow(0);
			
		if(!isEmpty()) clear();
		ensureCapacity(pixels);
		for(int c=0; c<pixels; c++) add(new GismoPixel(this, c));
		
		int iMask = hdu.findColumn("PIXMASK");
		int iBias = hdu.findColumn("DETECTORBIAS");
		
		if(iMask >= 0) {
			try {
				short[] mask = (short[]) row[iMask];
				for(Channel channel : this) if(mask[channel.getFixedIndex()-1] == 0) channel.flag(Channel.FLAG_DEAD); 
			}
			catch(ClassCastException e) {
				byte[] mask = (byte[]) row[iMask];
				for(Channel channel : this) if(mask[channel.getFixedIndex()-1] == 0) channel.flag(Channel.FLAG_DEAD);
			}
		}
		
		if(iBias >= 0) {
			biasValue = (int[]) row[iBias]; 
			setBiasOptions();
		}
	}
	
	
	public void setBiasOptions() {	
		if(!options.containsKey("bias")) return;
			
		int bias = biasValue[0];
		for(int i=1; i<biasValue.length; i++) if(biasValue[i] != bias) {
			System.err.println(" WARNING! Inconsistent bias values. Calibration may be bad!");
			CRUSH.countdown(5);
		}
			
		Hashtable<String, Vector<String>> settings = option("bias").conditionals;
			
		if(settings.containsKey(bias + "")) {
			System.err.println(" Setting options for bias " + bias);
			// Make options an independent set of options, setting MJD specifics...
			options = options.copy();
			options.parse(settings.get(bias + ""));
		}
	}

	
	protected void parseOldHardwareHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		Object[] row = hdu.getRow(0);
			
		if(!isEmpty()) clear();
		ensureCapacity(pixels);
		for(int c=0; c<pixels; c++) add(new GismoPixel(this, c));
		
		int iMask = hdu.findColumn("PIXMASK");
		
		if(iMask < 0) return;
		
		try {
			short[] mask = (short[]) row[iMask];
			for(Channel channel : this) if(mask[channel.getFixedIndex()-1] == 0) channel.flag(Channel.FLAG_DEAD); 
		}
		catch(ClassCastException e) {
			byte[] mask = (byte[]) row[iMask];
			for(Channel channel : this) if(mask[channel.getFixedIndex()-1] == 0) channel.flag(Channel.FLAG_DEAD);
		}
	
	}


	@Override
	public int maxPixels() {
		return storeChannels;
	}    
	
	
	
	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		
		final GismoScan firstScan = (GismoScan) scans.get(0);
			
		if(scans.size() == 1) {
			if(firstScan.obsType.equalsIgnoreCase("tip")) {
				System.err.println("Setting skydip reduction mode.");
				options.parse("skydip");

				if(scans.size() > 1) {
					System.err.println("Ignoring all but first scan in list (for skydip).");
					scans.clear();
					scans.add(firstScan);
				}
			}
			else if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);
		}
		
		for(int i=scans.size(); --i > 0; ) {
			GismoScan scan = (GismoScan) scans.get(i);
			if(scan.obsType.equalsIgnoreCase("tip")) {
				System.err.println("  WARNING! Scan " + scan.getID() + " is a skydip. Dropping from dataset.");
				scans.remove(i);
			}
		}
		
		super.validate(scans);
	}
	
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("foc.dX")) return Util.defaultFormat(focusXOffset / Unit.mm, f);
		else if(name.equals("foc.dY")) return Util.defaultFormat(focusYOffset / Unit.mm, f);
		else if(name.equals("foc.dZ")) return Util.defaultFormat(focusZOffset / Unit.mm, f);
		else if(name.equals("nasX")) return Util.defaultFormat(nasmythOffset.getX() / Unit.arcsec, f);
		else if(name.equals("nasY")) return Util.defaultFormat(nasmythOffset.getY() / Unit.arcsec, f);
		else if(name.equals("bias")) return Integer.toString(biasValue[0]);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	@Override
	public String getDataLocationHelp() {
		return super.getDataLocationHelp() +
				"     -object=      The object catalog name as entered in PaKo.\n" +
				"     -date=        YYYY-MM-DD when the data was collected.\n";
	}
	
	@Override
	public String getChannelDataHeader() {
		return super.getChannelDataHeader() + "\tGmux";
	}
	

	// Array is 16x8 (rows x cols);
	private static Vector2D defaultPointingCenter = new Vector2D(8.5, 4.5); // row, col
}
