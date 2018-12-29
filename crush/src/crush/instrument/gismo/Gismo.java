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

package crush.instrument.gismo;

import java.io.*;
import java.util.*;

import crush.*;
import crush.array.*;
import crush.telescope.GroundBased;
import crush.telescope.InstantFocus;
import crush.telescope.Mount;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.io.LineParser;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;
import nom.tam.fits.*;

public class Gismo extends Camera<GismoPixel> implements GroundBased, GridIndexed {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3009881856872575936L;


	public Vector2D nasmythOffset;
	protected Vector2D arrayPointingCenter; // row,col
	Vector2D pixelSize = GismoPixel.defaultSize;

	double focusXOffset, focusYOffset, focusZOffset;
	
	int[] detectorBias;
	int[] secondStageBias, secondStageFeedback;
	int[] thirdStageBias, thirdStageFeedback;
	
	
	public Gismo() {
	    super("gismo", new SingleColorArrangement<GismoPixel>(), pixels);
        setResolution(16.7 * Unit.arcsec);
        
        arrayPointingCenter = (Vector2D) defaultPointingCenter.clone();
        
        pixelSize = GismoPixel.defaultSize;
    
        mount = Mount.LEFT_NASMYTH;
		
		// TODO calculate this?
		integrationTime = samplingInterval = 0.1 * Unit.sec;
	}
	
	
    public int pixels() { return pixels; }
    
    public Vector2D getDefaultPointingCenter() { return defaultPointingCenter; }
    
    @Override
    public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
        Camera.addLocalFixedIndices(this, fixedIndex, radius, toIndex);
    }


    @Override
    public int rows() {
        return rows;
    }


    @Override
    public int cols() {
        return cols;
    }
    
    @Override
    public Vector2D getSIPixelSize() {
        return pixelSize;
    }
    
	
	@Override
	public Gismo copy() {
		Gismo copy = (Gismo) super.copy();
		
		if(arrayPointingCenter != null) copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.clone();
		if(nasmythOffset != null) copy.nasmythOffset = (Vector2D) nasmythOffset.clone();
		if(pixelSize != null) copy.pixelSize = pixelSize.copy();
		
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
    protected void initDivisions() {
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
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("pins", GismoPixel.class.getField("pin"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }	
		
		try { addDivision(getDivision("cols", GismoPixel.class.getField("col"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }	
		
		try { addDivision(getDivision("rows", GismoPixel.class.getField("row"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }	
		
	}
	
	@Override
    protected void initModalities() {
		super.initModalities();
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), GismoPixel.class.getField("muxGain"));		
			muxMode.solveGains = false;
			muxMode.setGainFlag(GismoPixel.FLAG_MUX);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { error(e); }	
			
		try { 
			Modality<?> pinMode = new CorrelatedModality("pins", "p", divisions.get("pins"), GismoPixel.class.getField("pinGain")); 
			pinMode.setGainFlag(GismoPixel.FLAG_PIN);
			addModality(pinMode);
		}
		catch(NoSuchFieldException e) { error(e); }
		
		try { 
			Modality<?> colMode = new CorrelatedModality("cols", "c", divisions.get("cols"), GismoPixel.class.getField("colGain")); 
			colMode.setGainFlag(GismoPixel.FLAG_COL);
			addModality(colMode);
		}
		catch(NoSuchFieldException e) { error(e); }
		
		try { 
			Modality<?> rowMode = new CorrelatedModality("rows", "r", divisions.get("rows"), GismoPixel.class.getField("rowGain")); 
			rowMode.setGainFlag(GismoPixel.FLAG_ROW);
			addModality(rowMode);
		}
		catch(NoSuchFieldException e) { error(e); }

	}
	
	@Override
    protected void loadChannelData() {
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
		pixelSize = size;
		
		Vector2D center = GismoPixel.getPosition(size, arrayPointingCenter.x() - 1.0, arrayPointingCenter.y() - 1.0);			
	
		for(GismoPixel pixel : this) pixel.calcPosition();
		
		// Set the pointing center...
		setReferencePosition(center);
	}
	
	// Calculates the offset of the pointing center from the nominal center of the array
	@Override
	public Vector2D getPointingCenterOffset() {
		Vector2D offset = (Vector2D) arrayPointingCenter.clone();
		final Vector2D pCenter = getDefaultPointingCenter();
		offset.subtract(pCenter);
		if(hasOption("rotation")) offset.rotate(option("rotation").getDouble() * Unit.deg);
		return offset;
	}
	
	@Override
	public void readWiring(String fileName) throws IOException {
		info("Loading wiring data from " + fileName);
		
		final ChannelLookup<GismoPixel> lookup = new ChannelLookup<GismoPixel>(this);
		final int groupPins = hasOption("correlated.pins.group") ? option("correlated.pins.group").getInt() : 1;
		
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                String id = tokens.nextToken();
                
                GismoPixel pixel = lookup.get(id);
                if(pixel == null) return false;
                
                pixel.mux = tokens.nextInt();
                pixel.pin = tokens.nextInt() / groupPins;
                return true;
            }
		}.read(fileName);

	}
	
	protected void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException, FitsException {
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
		
		info("Focus: dZ = " + Util.f2.format(focusZOffset / Unit.mm) + " mm.");
	}
	
	
	protected void parseOldScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
			
		// Focus
		focusXOffset = header.getDoubleValue("FOCUS_XO") * Unit.mm;
		focusYOffset = header.getDoubleValue("FOCUS_YO") * Unit.mm;
		focusZOffset = header.getDoubleValue("FOCUS_ZO") * Unit.mm;
	
		nasmythOffset = new Vector2D(
				header.getDoubleValue("RXHORI", Double.NaN) + header.getDoubleValue("RXHORICO", 0.0),
				header.getDoubleValue("RXVERT", Double.NaN) + header.getDoubleValue("RXVERTCO", 0.0)
				);
		
		info("Focus: dZ = " + Util.f2.format(focusZOffset / Unit.mm) + " mm.");
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
				for(Channel channel : this) if(mask[channel.getFixedIndex()] == 0) channel.flag(Channel.FLAG_DEAD); 
			}
			catch(ClassCastException e) {
				byte[] mask = (byte[]) row[iMask];
				for(Channel channel : this) if(mask[channel.getFixedIndex()] == 0) channel.flag(Channel.FLAG_DEAD);
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
			warning("Inconsistent bias values. Calibration may be bad!");
			CRUSH.countdown(5);
		}
		
		Hashtable<String, Vector<String>> settings = option("bias").conditionals;
			
		if(settings.containsKey(bias + "")) {
			info("Setting options for bias " + bias);
			getOptions().parseAll(settings.get(bias + ""));
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
			for(Channel channel : this) if(mask[channel.getFixedIndex()] == 0) channel.flag(Channel.FLAG_DEAD); 
		}
		catch(ClassCastException e) {
			byte[] mask = (byte[]) row[iMask];
			for(Channel channel : this) if(mask[channel.getFixedIndex()] == 0) channel.flag(Channel.FLAG_DEAD);
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
				info("Setting skydip reduction mode.");
				setOption("skydip");

				if(scans.size() > 1) {
					warning("Ignoring all but first scan in list (for skydip).");
					scans.clear();
					scans.add(firstScan);
				}
			}
			else if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);
		}
		
		for(int i=scans.size(); --i > 0; ) {
			GismoScan scan = (GismoScan) scans.get(i);
			if(scan.obsType.equalsIgnoreCase("tip")) {
				warning("Scan " + scan.getID() + " is a skydip. Dropping from dataset.");
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
	public Object getTableEntry(String name) {
		if(name.equals("foc.dX")) return focusXOffset / Unit.mm;
		if(name.equals("foc.dY")) return focusYOffset / Unit.mm;
		if(name.equals("foc.dZ")) return focusZOffset / Unit.mm;
		if(name.equals("nasX")) return nasmythOffset.x() / Unit.arcsec;
		if(name.equals("nasY")) return nasmythOffset.y() / Unit.arcsec;
		if(name.equals("bias")) return detectorBias[0];
		if(name.equals("stage2.biases")) return toString(secondStageBias);
		if(name.equals("stage2.feedbacks")) return toString(secondStageFeedback);
		if(name.equals("stage3.biases")) return toString(thirdStageBias);	
		if(name.equals("stage3.feedbacks")) return toString(thirdStageFeedback);	
		return super.getTableEntry(name);
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
	
	
	   
    // Array is 16x8 (rows x cols);
    public static final int rows = 16;
    public static final int cols = 8;
    public static final int pixels = rows * cols;
    

    public static Vector2D defaultSize;
    
    private static Vector2D defaultPointingCenter = new Vector2D(8.5, 4.5); // row, col


	
}
