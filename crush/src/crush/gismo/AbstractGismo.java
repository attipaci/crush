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

package crush.gismo;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import kovacs.data.DataPoint;
import kovacs.math.Vector2D;
import kovacs.text.TableFormatter;
import kovacs.util.*;
import crush.*;
import crush.array.*;
import nom.tam.fits.*;

public abstract class AbstractGismo extends MonoArray<GismoPixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3009881856872575936L;


	public Vector2D nasmythOffset;
	protected Vector2D arrayPointingCenter; // row,col

	double focusXOffset, focusYOffset, focusZOffset;
	
	int[] detectorBias;
	int[] secondStageBias, secondStageFeedback;
	int[] thirdStageBias, thirdStageFeedback;
	
	
	public AbstractGismo(String name, int npix) {
		super(name, npix);
		resolution = 16.7 * Unit.arcsec;
		
		arrayPointingCenter = (Vector2D) getDefaultPointingCenter().clone();
		
		// TODO calculate this?
		integrationTime = samplingInterval = 0.1 * Unit.sec;
	
		mount = Mount.LEFT_NASMYTH;
	}
	
	public abstract Vector2D getDefaultPointingCenter(); 

	public abstract int pixels();
	
	@Override
	public Instrument<GismoPixel> copy() {
		AbstractGismo copy = (AbstractGismo) super.copy();
		if(arrayPointingCenter != null) copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.clone();
		if(nasmythOffset != null) copy.nasmythOffset = (Vector2D) nasmythOffset.clone();
		
		copy.detectorBias = Util.copyOf(detectorBias);
		copy.secondStageBias = Util.copyOf(secondStageBias);
		copy.secondStageFeedback = Util.copyOf(secondStageFeedback);
		copy.thirdStageBias = Util.copyOf(thirdStageBias);
		copy.thirdStageFeedback = Util.copyOf(thirdStageFeedback);
		
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
	public void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("mux", GismoPixel.class.getField("mux"), Channel.FLAG_DEAD)); 
			ChannelDivision<GismoPixel> muxDivision = divisions.get("mux");
			
			// Order mux channels in pin order...
			for(ChannelGroup<GismoPixel> mux : muxDivision) {
				Collections.sort(mux, new Comparator<GismoPixel>() {
					@Override
					public int compare(GismoPixel o1, GismoPixel o2) {
						if(o1.pin == o2.pin) return 0;
						return o1.pin > o2.pin ? 1 : -1;
					}	
				});
			}
		}
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("pins", GismoPixel.class.getField("pin"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("cols", GismoPixel.class.getField("col"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("rows", GismoPixel.class.getField("row"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
	}
	
	@Override
	public void initModalities() {
		super.initModalities();
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), GismoPixel.class.getField("muxGain"));		
			muxMode.solveGains = false;
			muxMode.setGainFlag(GismoPixel.FLAG_MUX);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }	
			
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
		
		if(hasOption("read.sae")) {
			try { addModality(new SAEModality(this)); }
			catch(NoSuchFieldException e) { e.printStackTrace(); }
		}
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
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
		}

		setPlateScale(pixelSize);
			
		super.loadChannelData();
	}
	
	public void setPlateScale(Vector2D size) {
		// Make all pixels the same size. Also calculate their positions...
		for(GismoPixel pixel : this) pixel.size = size;
		
		Vector2D center = GismoPixel.getPosition(size, arrayPointingCenter.x() - 1.0, arrayPointingCenter.y() - 1.0);			
		
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
		final Vector2D pCenter = getDefaultPointingCenter();
		offset.subtract(pCenter);
		if(hasOption("rotation")) offset.rotate(option("rotation").getDouble() * Unit.deg);
		return offset;
	}
	
	@Override
	public void readWiring(String fileName) throws IOException {
		System.err.println(" Loading wiring data from " + fileName);
			
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		Hashtable<Integer, GismoPixel> lookup = getFixedIndexLookup();
		
		int groupPins = hasOption("correlated.pins.group") ? option("correlated.pins.group").getInt() : 1;
		
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
		 	GismoPixel pixel = lookup.get(Integer.parseInt(tokens.nextToken()));
		 	if(pixel == null) continue;
			pixel.mux = Integer.parseInt(tokens.nextToken());
		 	pixel.pin = Integer.parseInt(tokens.nextToken()) / groupPins;
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
		final int pixels = pixels();	
		
		if(!isEmpty()) clear();
		ensureCapacity(pixels);
		
		for(int c = 0; c<pixels; c++) add(new GismoPixel(this, c));
		
		int iMask = hdu.findColumn("PIXMASK");
		int iBias = hdu.findColumn("DETECTORBIAS");
		int i2Bias = hdu.findColumn("SECONDSTAGEBIAS");
		int i3Bias = hdu.findColumn("THIRDSTAGEBIAS");
		int i2Feedback = hdu.findColumn("SECONDSTAGEFEEDBACK");
		int i3Feedback = hdu.findColumn("THIRDSTAGEFEEDBACK");
		
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
			detectorBias = (int[]) row[iBias]; 
			setBiasOptions();
		}
		
		if(i2Bias >= 0) secondStageBias = (int[]) row[i2Bias]; 
		if(i3Bias >= 0) thirdStageBias = (int[]) row[i3Bias]; 
		if(i2Feedback >= 0) secondStageFeedback = (int[]) row[i2Feedback]; 
		if(i3Feedback >= 0) thirdStageFeedback = (int[]) row[i3Feedback]; 
		
	}
	
	
	public void setBiasOptions() {	
		if(!getOptions().containsKey("bias")) return;
			
		int bias = detectorBias[0];
		for(int i=1; i<detectorBias.length; i++) if(detectorBias[i] != bias) {
			System.err.println(" WARNING! Inconsistent bias values. Calibration may be bad!");
			CRUSH.countdown(5);
		}
		
		Hashtable<String, Vector<String>> settings = option("bias").conditionals;
			
		if(settings.containsKey(bias + "")) {
			System.err.println(" Setting options for bias " + bias);
			getOptions().parse(settings.get(bias + ""));
		}
	}

	
	protected void parseOldHardwareHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		Object[] row = hdu.getRow(0);
		final int pixels = pixels();	
		
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
				setOption("skydip");

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
	
	
	private String toString(int[] values) {
		StringBuffer buf = new StringBuffer();
		for(int i=0; i<values.length; i++) {
			if(i > 0) buf.append(' ');
			buf.append(Integer.toString(values[i]));
		}
		return new String(buf);
	}
	
	@Override
	public String getFocusString(InstantFocus focus) {	
		double s2n = hasOption("focus.s2n") ? option("focus.s2n").getDouble() : 2.0;
		
		StringBuffer info = new StringBuffer(super.getFocusString(focus));
		
		/*
		info += "\n";
		info += "  Note: The instant focus feature of CRUSH is still very experimental.\n" +
				"        The feature may be used to guesstimate focus corrections on truly\n" +
				"        point-like sources (D < 4\"). However, the essential focusing\n" +
				"        coefficients need to be better determined in the future.\n" +
				"        Use only with extreme caution, and check suggestions for sanity!\n\n";
		*/
	
		info.append("\n");
		
		
		InstantFocus compound = new InstantFocus(focus);
		
		boolean largeLateral = false;
		
		if(focus.getX() != null) if(focus.getX().significance() > s2n) {
			DataPoint dx = compound.getX();
			//if(dx.significance() > 2.0) largeLateral = true;
			dx.add(focusXOffset);
			info.append("\n  PaKo> set focus " + Util.f1.format(dx.value() / Unit.mm) + " /dir x"); 			
		}
		if(focus.getY() != null) if(focus.getY().significance() > s2n) {
			DataPoint dy = compound.getY();
			//if(dy.significance() > 2.0) largeLateral = true;
			dy.add(focusYOffset);
			info.append("\n  PaKo> set focus " + Util.f1.format(dy.value() / Unit.mm) + " /dir y"); 
		}
		if(focus.getZ() != null) if(focus.getZ().significance() > s2n) if(!largeLateral) {
			DataPoint dz = compound.getZ();
			dz.add(focusZOffset);
			info.append("\n  PaKo> set focus " + Util.f1.format(dz.value() / Unit.mm));
				//	+ "    \t[+-" + Util.f2.format(dz.rms() / Unit.mm) + "]");
		}
			
		return new String(info);
	}
	
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("foc.dX")) return Util.defaultFormat(focusXOffset / Unit.mm, f);
		else if(name.equals("foc.dY")) return Util.defaultFormat(focusYOffset / Unit.mm, f);
		else if(name.equals("foc.dZ")) return Util.defaultFormat(focusZOffset / Unit.mm, f);
		else if(name.equals("nasX")) return Util.defaultFormat(nasmythOffset.x() / Unit.arcsec, f);
		else if(name.equals("nasY")) return Util.defaultFormat(nasmythOffset.y() / Unit.arcsec, f);
		else if(name.equals("bias")) return Integer.toString(detectorBias[0]);
		else if(name.equals("stage2.biases")) return toString(secondStageBias);
		else if(name.equals("stage2.feedbacks")) return toString(secondStageFeedback);
		else if(name.equals("stage3.biases")) return toString(thirdStageBias);	
		else if(name.equals("stage3.feedbacks")) return toString(thirdStageFeedback);	
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	@Override
	public String getDataLocationHelp() {
		return super.getDataLocationHelp() +
				"     -object=       The object catalog name as entered in PaKo.\n" +
				"     -date=         YYYY-MM-DD when the data was collected.\n";
	}
	
	@Override
	public String getChannelDataHeader() {
		return super.getChannelDataHeader() + "\tGmux";
	}
	

}
