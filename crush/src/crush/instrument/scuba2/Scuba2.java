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

package crush.instrument.scuba2;

import java.util.*;

import crush.*;
import crush.array.*;
import crush.telescope.GroundBased;
import crush.telescope.Mount;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import nom.tam.fits.*;

public class Scuba2 extends Camera<Scuba2Pixel> implements GroundBased, GridIndexed {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7284330045075075340L;
	
	Scuba2Subarray[] subarray;
	
	double focusXOffset, focusYOffset, focusZOffset;
	String filter;
	boolean shutterOpen, isMirrored;
	
	double physicalPixelSize;		// e.g. mm 
	double plateScale;				// e.g. arcseconds / mm;
	
	Vector2D pointingCenter;
	Vector2D pointingCorrection;
	Vector2D userPointingOffset;
	
	
	public Scuba2() {
		super("scuba2", new SingleColorArrangement<Scuba2Pixel>(), SUBARRAYS * Scuba2Subarray.PIXELS);
		//integrationTime = samplingInterval = 1.0/200.0 * Unit.sec;
		mount = Mount.RIGHT_NASMYTH;
		subarray = new Scuba2Subarray[SUBARRAYS];
	}

	@Override
	public Scuba2 copy() {
		Scuba2 copy = (Scuba2) super.copy();
		
		if(filter != null) copy.filter = new String(filter);
		
		if(pointingCenter != null) copy.pointingCenter = pointingCenter.copy();
		if(pointingCorrection != null) copy.pointingCorrection = pointingCorrection.copy();
		if(userPointingOffset != null) copy.userPointingOffset = userPointingOffset.copy();
		
		if(subarray != null) {
			copy.subarray = new Scuba2Subarray[subarray.length];
			for(int i=subarray.length; --i >= 0; ) if(subarray[i] != null) {
				copy.subarray[i] = subarray[i].copy();
				copy.subarray[i].scuba2 = copy;
			}
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
    protected void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("subarrays", Scuba2Pixel.class.getField("subarrayNo"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("rows", Scuba2Pixel.class.getField("row"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("cols", Scuba2Pixel.class.getField("col"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }	
		
		if(hasOption("block")) {
			StringTokenizer tokens = new StringTokenizer(option("block").getValue(), " \t:x");
			int sizeX = Integer.parseInt(tokens.nextToken());
			int sizeY = tokens.hasMoreTokens() ? Integer.parseInt(tokens.nextToken()) : sizeX;
			int nx = (int)Math.ceil(40.0 / sizeX);
			for(Scuba2Pixel pixel : this) pixel.block = (pixel.row / sizeY) * nx + (pixel.col / sizeX); 
		}
		
		try { addDivision(getDivision("blocks", Scuba2Pixel.class.getField("block"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }	
	}
	
	@Override
    protected void initModalities() {
		super.initModalities();
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("subarrays", "S", divisions.get("subarrays"), Scuba2Pixel.class.getField("subarrayGain"));		
			muxMode.setGainFlag(Scuba2Pixel.FLAG_SUBARRAY);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { error(e); }	
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("rows", "r", divisions.get("rows"), Scuba2Pixel.class.getField("rowGain"));		
			muxMode.setGainFlag(Scuba2Pixel.FLAG_ROW);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { error(e); }	
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("cols", "c", divisions.get("cols"), Scuba2Pixel.class.getField("colGain"));		
			muxMode.setGainFlag(Scuba2Pixel.FLAG_COL);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { error(e); }	
			
		try { addModality(new Modality<Scuba2He3Response>("he3", "T", divisions.get("detectors"), Scuba2Pixel.class.getField("he3Gain"), Scuba2He3Response.class));	}
		catch(NoSuchFieldException e) { error(e); }
		
		try { addModality(new CorrelatedModality("blocks", "b", divisions.get("blocks"), Scuba2Pixel.class.getField("gain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		
	}
	
	@Override
    protected void loadChannelData() {
		calcPixelPositions();
		super.loadChannelData();
	}
	
	public void calcPixelPositions() {	
		physicalPixelSize = hasOption("pixelmm") ? option("pixelmm").getDouble() * Unit.mm : DEFAULT_PIXEL_SIZE;
		double plateScale = hasOption("platescale") ? option("platescale").getDouble() * Unit.arcsec / Unit.mm : DEFAULT_PLATE_SCALE;
		
		DistortionModel distortion = hasOption("distortion") ? new DistortionModel(option("distortion")) : null;
		if(distortion != null) {
			distortion.setUnit(Unit.get("mm"));
			info("Applying distortion model: " + distortion.getName());
		}
				
		for(Scuba2Pixel pixel : this) {	
			pixel.position = subarray[pixel.subarrayNo].getPhysicalPixelPosition(pixel.row % Scuba2.SUBARRAY_ROWS, pixel.col % Scuba2.SUBARRAY_COLS);
			
			// Apply the distortion model (if specified).
			if(distortion != null) pixel.position = distortion.getValue(pixel.position);
			
			// scale to arcseconds
			pixel.position.scale(plateScale);
			
			// pointing center offset...
			if(pointingCenter != null) pixel.position.subtract(pointingCenter);
		}
		
		if(hasOption("flip")) for(Scuba2Pixel pixel : this) pixel.position.scaleX(-1.0);
		
		if(hasOption("rotate")) {
			double angle = option("rotate").getDouble() * Unit.deg;
			for(Scuba2Pixel pixel : this) pixel.position.rotate(angle);
		}
		
		if(hasOption("zoom")) {
			double zoom = option("zoom").getDouble();
			for(Scuba2Pixel pixel : this) pixel.position.scale(zoom);
		}
		
		if(hasOption("skew")) {
			double skew = option("skew").getDouble();
			for(Scuba2Pixel pixel : this) { pixel.position.scaleX(skew); pixel.position.scaleY(1.0/skew); }
		}
		
		
		
	}
	
	public ArrayList<Scuba2Pixel> getSubarrayPixels(int subarrayIndex) {
		ArrayList<Scuba2Pixel> pixels = new ArrayList<Scuba2Pixel>(Scuba2Subarray.PIXELS);
		for(Scuba2Pixel pixel : this) if(pixel.subarrayNo == subarrayIndex) pixels.add(pixel);
		return pixels;
	}
	
	
	public void addPixelsFor(boolean[] hasSubarray) {
		int subarrays = 0;
		for(int i=0; i < hasSubarray.length; i++) if(hasSubarray[i]) subarrays++;
		
		clear();
		ensureCapacity(subarrays * Scuba2Subarray.PIXELS);
		
		int fixedIndexOffset = 0;
		for(int i=0; i<hasSubarray.length; i++) {
			if(hasSubarray[i]) for(int k=0; k<Scuba2Subarray.PIXELS; k++) add(new Scuba2Pixel(this, fixedIndexOffset + k));
			fixedIndexOffset += Scuba2Subarray.PIXELS;
		}
		
		reindex();
	}
	
	
	
	protected void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException {
		Header header = hdu.getHeader();
		
		String subarrayPrefix = hasOption("450um") ? "s4" : "s8"; 
		
		// nSubarray = (header.getStringValue("SUBARRAY"));
		for(int i=0; i<subarray.length; i++) subarray[i] = new Scuba2Subarray(this, subarrayPrefix + (char)('a' + i));
		
		// INSTAP_X, Y instrument aperture offsets. Kinda like FAZO, FZAO?
		pointingCenter = new Vector2D(header.getDoubleValue("INSTAP_X", 0.0), header.getDoubleValue("INSTAP_Y", 0.0));
		pointingCenter.scale(-Unit.arcsec);
		
		filter = header.getStringValue("FILTER");
		double shutter = header.getDoubleValue("SHUTTER");
		shutterOpen = shutter > 0.0;
		
		// nominal integration time, but real sampling is slower and jittery...
		//integrationTime = samplingInterval = header.getDoubleValue("STEPTIME");
		
		// Focus
		focusXOffset = header.getDoubleValue("ALIGN_DX");
		focusYOffset = header.getDoubleValue("ALIGN_DY");
		focusZOffset = header.getDoubleValue("ALIGN_DZ");
	
		info("Focus :"
				+ " XOff=" + Util.f2.format(focusXOffset)
				+ " YOff=" + Util.f2.format(focusYOffset) 
				+ " ZOff=" + Util.f2.format(focusZOffset)
		);		
		
		// DAZ, DEL total pointing corrections
		pointingCorrection = new Vector2D(header.getDoubleValue("DAZ", 0.0), header.getDoubleValue("DEL", 0.0));
		pointingCorrection.scale(Unit.arcsec);
		
		// UAZ, UEL pointing
		userPointingOffset = new Vector2D(header.getDoubleValue("UAZ", 0.0), header.getDoubleValue("UEL", 0.0));
		userPointingOffset.scale(Unit.arcsec);
		
	}
	
	/*
	public void readTemperatureGains(String fileName) throws IOException {
		info("Loading He3 gains from " + fileName);
		
		final Hashtable<Integer, Scuba2Pixel> lookup = getFixedIndexLookup();
        	
		new AsciiReader() {
            @Override
            protected boolean parse(String line) throws Exception {
                StringTokenizer tokens = new StringTokenizer(line);
                Scuba2Pixel pixel = lookup.get(Integer.parseInt(tokens.nextToken()));
                if(pixel == null) return false;
                pixel.temperatureGain = tokens.nextDouble();
                return true;
            }
		}.read(fileName);
			
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
	    notify("Written temperature gain data to " + fileName + ".");
		
	}
	*/
	
	
	@Override
	public Object getTableEntry(String name) {
		if(name.equals("filter")) return filter;
		if(name.equals("foc.X")) return focusXOffset;
		if(name.equals("foc.Y")) return focusYOffset;
		if(name.equals("foc.Z")) return focusZOffset;
		if(name.equals("shutter?")) return Boolean.toString(shutterOpen);
		return super.getTableEntry(name);
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
				"     -date=         YYYY-MM-DD when the data was collected.\n" +
				"     -ndf2fits=     The path to the ndf2fits executable. Required for reading\n" +
				"                    native NDF (.sdf) files.\n";
	}

		
	@Override
	public int maxPixels() { return SUBARRAYS * Scuba2Subarray.PIXELS; }
	

	@Override
	public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
		Camera.addLocalFixedIndices(this, fixedIndex, radius, toIndex);
	}

	@Override
	public Vector2D getPixelSize() {
		final double size = physicalPixelSize * plateScale;
		return new Vector2D(size, size);
	}

	@Override
	public int rows() {
		return SUBARRAYS * SUBARRAY_ROWS;
	}

	@Override
	public int cols() {
		return SUBARRAY_COLS;
	}
	

	public final static double DEFAULT_PIXEL_SIZE = 1.135 * Unit.mm;
	public final static double DEFAULT_PLATE_SCALE = 5.1453 * Unit.arcsec / Unit.mm;
	
	public final static int SUBARRAY_COLS = 40;
	public final static int SUBARRAY_ROWS = 32;
	public final static int SUBARRAYS = 4;

}
