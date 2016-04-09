/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.hawcplus;

import java.io.IOException;
import java.util.*;

import crush.*;
import crush.array.Camera;
import crush.array.GridIndexed;
import crush.array.SingleColorArrangement;
import crush.sofia.SofiaCamera;
import jnum.Configurator;
import jnum.Unit;
import jnum.io.fits.FitsExtras;
import jnum.math.Vector2D;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

public class HawcPlus extends SofiaCamera<HawcPlusPixel> implements GridIndexed {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3009881856872575936L;


	private Vector2D arrayPointingCenter; // row, col
	Vector2D pixelSize;
	
	boolean[] hasSubarray;
	Vector2D[] subarrayOffset;
	double[] subarrayOrientation;
	
	double[] polZoom;
	
	
	public HawcPlus() {
		super("hawc+", new SingleColorArrangement<HawcPlusPixel>());
		arrayPointingCenter = (Vector2D) defaultPointingCenter.clone();
		mount = Mount.NASMYTH_COROTATING;
	}
	
	@Override
	public void setOptions(Configurator options) {
		super.setOptions(options);
		if(drp == null) if(hasOption("drp")) initDRPMessages();
	}
	
	private void initDRPMessages() {
		System.err.println(" Activating DRP messages over TCP/IP.");
		try { drp = new DRPMessenger(option("drp")); }
		catch(IOException e) { warning(e); }
	}
	
	@Override
	public void shutdown() {
		super.shutdown();
		if(drp != null) drp.shutdown();
	}
	
	@Override
	public Instrument<HawcPlusPixel> copy() {
		HawcPlus copy = (HawcPlus) super.copy();
		
		if(arrayPointingCenter != null) copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.clone();
		if(pixelSize != null) copy.pixelSize = (Vector2D) pixelSize.copy();
		
        if(hasSubarray != null) {
            copy.hasSubarray = new boolean[subarrays];
            for(int i=subarrays; --i >= 0; ) copy.hasSubarray[i] = hasSubarray[i];
        }
        
		if(subarrayOffset != null) {
			copy.subarrayOffset = new Vector2D[subarrays];
			for(int i=subarrays; --i >= 0; ) copy.subarrayOffset[i] = (Vector2D) subarrayOffset[i].clone();
		}
	
		if(subarrayOrientation != null) {
            copy.subarrayOrientation = new double[subarrays];
            for(int i=subarrays; --i >= 0; ) copy.subarrayOrientation[i] = subarrayOrientation[i];
        }

		if(polZoom != null) {
		    copy.polZoom = new double[polArrays];
		    for(int i=polArrays; --i >= 0; ) copy.polZoom[i] = polZoom[i];
		}
		
		return copy;
	}
	

	@Override
	public HawcPlusPixel getChannelInstance(int backendIndex) {
		return new HawcPlusPixel(this, backendIndex);
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new HawcPlusScan(this);
	}

	@Override
	public void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("polarrays", HawcPlusPixel.class.getField("pol"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("subarrays", HawcPlusPixel.class.getField("sub"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
		catch(Exception e) { e.printStackTrace(); }	
				
		try { addDivision(getDivision("mux", HawcPlusPixel.class.getField("mux"), Channel.FLAG_DEAD)); 
			ChannelDivision<HawcPlusPixel> muxDivision = divisions.get("mux");
			
			// Order mux channels in pin order...
			for(ChannelGroup<HawcPlusPixel> mux : muxDivision) {
				Collections.sort(mux, new Comparator<HawcPlusPixel>() {
					@Override
					public int compare(HawcPlusPixel o1, HawcPlusPixel o2) {
						if(o1.pin == o2.pin) return 0;
						return o1.pin > o2.pin ? 1 : -1;
					}	
				});
			}
		}
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("pins", HawcPlusPixel.class.getField("pin"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
	}
	
	@Override
	public void initModalities() {
		super.initModalities();
		
		try { 
			Modality<?> pinMode = new CorrelatedModality("polarrays", "P", divisions.get("pols"), HawcPlusPixel.class.getField("polGain")); 
			pinMode.setGainFlag(HawcPlusPixel.FLAG_POL);
			addModality(pinMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { 
			Modality<?> pinMode = new CorrelatedModality("subarrays", "S", divisions.get("subarrays"), HawcPlusPixel.class.getField("subGain")); 
			pinMode.setGainFlag(HawcPlusPixel.FLAG_SUB);
			addModality(pinMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), HawcPlusPixel.class.getField("muxGain"));		
			muxMode.solveGains = false;
			muxMode.setGainFlag(HawcPlusPixel.FLAG_MUX);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }	
			
		try { 
			Modality<?> pinMode = new CorrelatedModality("pins", "p", divisions.get("pins"), HawcPlusPixel.class.getField("pinGain")); 
			pinMode.setGainFlag(HawcPlusPixel.FLAG_PIN);
			addModality(pinMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
	}
	
	@Override
    public void parseHeader(Header header) {
	    super.parseHeader(header);
	    
	    hasSubarray = new boolean[subarrays];
	    
	    String mceMap = header.getStringValue("MCEMAP");
	    if(mceMap != null) {
	        StringTokenizer tokens = new StringTokenizer(mceMap, " \t,;:");
	        for(int sub=0; sub<subarrays; sub++) if(tokens.hasMoreTokens()) {
	            String assignment = tokens.nextToken();
	            try { 
	                int mce = Integer.parseInt(assignment);
	                hasSubarray[sub] = mce >= 0;
	            }
	            catch(NumberFormatException e) { System.err.println("   WARNING! Invalid MCE assignment: " + assignment);}
	        }       
	    }   
	}
	
	@Override
	public void loadChannelData() {
		if(hasOption("subarray")) selectSubarrays(option("subarray").getValue());
		
		// Update the pointing centers...
		if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
		else arrayPointingCenter = array.arrayPointingCenter;
		
		// The subarrays orientations
		subarrayOrientation = new double[subarrays];
		subarrayOrientation[R0] = hasOption("rotation.r0") ? option("offset.r0").getDouble() * Unit.deg : 0.0;
		subarrayOrientation[R1] = hasOption("rotation.r1") ? option("offset.r1").getDouble() * Unit.deg : Math.PI;
		subarrayOrientation[T0] = hasOption("rotation.t0") ? option("offset.t0").getDouble() * Unit.deg : 0.0;
		subarrayOrientation[T1] = hasOption("rotation.t1") ? option("offset.t1").getDouble() * Unit.deg : Math.PI;

		// The subarray offsets (after rotation, in pixels)
		subarrayOffset = new Vector2D[subarrays];
		subarrayOffset[R0] = hasOption("offset.r0") ? option("offset.r0").getVector2D() : new Vector2D();
		subarrayOffset[R1] = hasOption("offset.r1") ? option("offset.r1").getVector2D() : new Vector2D(67.03, -39.0);
		subarrayOffset[T0] = hasOption("offset.t0") ? option("offset.t0").getVector2D() : new Vector2D();
		subarrayOffset[T1] = hasOption("offset.t1") ? option("offset.t1").getVector2D() : new Vector2D(67.03, -39.0);
	    
		// The relative zoom of the polarization planes...
		polZoom = new double[polArrays];
		polZoom[R_ARRAY] = hasOption("zoom.r") ? option("zoom.r").getDouble() : 1.0;
		polZoom[T_ARRAY] = hasOption("zoom.t") ? option("zoom.t").getDouble() : 1.0;
		
		// The default pixelSizes...
		Vector2D pixelSize = new Vector2D(array.pixelScale, array.pixelScale);
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
		}
		
		// Convert subarray pixel offsets into angular offsets  
        for(int sub=subarrays; --sub >= 0; ) {
            subarrayOffset[sub].scaleX(pixelSize.x());
            subarrayOffset[sub].scaleY(pixelSize.y());
        }
        
		setPlateScale(pixelSize);
			
		super.loadChannelData();
	}
	
	public void selectSubarrays(String spec) {
		for(HawcPlusPixel pixel : this) pixel.flag(Channel.FLAG_DISCARD);
		
		StringTokenizer tokens = new StringTokenizer(spec, ",; \t");
		while(tokens.hasMoreTokens()) {
		    String subSpec = tokens.nextToken();
			String value = subSpec.toUpperCase();
			char pol = value.charAt(0);
			int sub = value.length() > 1 ? value.charAt(1) - '0' : -1;
			
			int polarray = -1;
			if(pol == 'R') polarray = R0;
			else if(pol == 'T') polarray = T0;
			
			if(polarray < 0) System.err.println("   WARNING! invalid subarray selection: " + value);
			else {
				if(sub < 0) for(int i=polSubarrays; --i >= 0; ) selectSubarray(polarray + i);
				else selectSubarray(polarray + sub);
			}
		}
		
		for(HawcPlusPixel pixel : this) if(pixel.isFlagged(Channel.FLAG_DISCARD)) pixel.flag(Channel.FLAG_DEAD);
	}
	
	private void selectSubarray(int sub) {
	    for(HawcPlusPixel pixel : this) if(pixel.sub == sub) pixel.unflag(Channel.FLAG_DISCARD);
	}
	
	private void setPlateScale(Vector2D size) {	
		pixelSize = size;
		
		Vector2D center = HawcPlusPixel.getPosition(
		        size, polZoom[0], subarrayOffset[0], subarrayOrientation[0], 
		        arrayPointingCenter.x(), arrayPointingCenter.y()
		);
		
		for(HawcPlusPixel pixel : this) pixel.calcPosition();
		
		// Set the pointing center...
		setReferencePosition(center);
	}
	
	// Calculates the offset of the pointing center from the nominal center of the array
	@Override
	public Vector2D getPointingCenterOffset() {
		Vector2D offset = (Vector2D) arrayPointingCenter.clone();
		offset.subtract(defaultPointingCenter);
		if(hasOption("rotation")) offset.rotate(option("rotation").getDouble() * Unit.deg);
		return offset;
	}
	
	// TODO
	/*
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
	 */

		
	@Override
	public int maxPixels() {
		return storeChannels;
	}    
	
	
	/*
	private String toString(int[] values) {
		StringBuffer buf = new StringBuffer();
		for(int i=0; i<values.length; i++) {
			if(i > 0) buf.append(' ');
			buf.append(Integer.toString(values[i]));
		}
		return new String(buf);
	}
	*/
	
	@Override
	public String getChannelDataHeader() {
		return super.getChannelDataHeader() + "\tGmux";
	}
	
	@Override
	public void editImageHeader(List<Scan<?,?>> scans, Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
		super.editImageHeader(scans, header, cursor);
		// Add HAWC+ specific keywords
		cursor.add(new HeaderCard("COMMENT", "<------ HAWC+ Header Keys ------>", false));
		cursor.add(new HeaderCard("PROCLEVL", "crush", "Last pipeline processing step on the data."));
	}

	@Override
	public void readData(Fits fits) throws Exception {
		// TODO Auto-generated method stub
	}
		
	@Override
	public void validate(Scan<?,?> scan) {
		
		clear();
		final int pixels = pixels();
		
		ensureCapacity(pixels);
		for(int c=0; c<pixels; c++) add(new HawcPlusPixel(this, c));

		// Flag missing subarrays as 'dead'
		for(HawcPlusPixel pixel : this) if(!hasSubarray[pixel.sub]) pixel.flag(Channel.FLAG_DEAD);	
		
		if(!hasOption("filter")) getOptions().parseSilent("filter " + instrumentData.wavelength + "um");	
		System.err.println(" HAWC+ Filter set to " + option("filter").getValue());
		
		super.validate(scan);
	}
	
	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		final HawcPlusScan firstScan = (HawcPlusScan) scans.get(0);
	
		for(int i=scans.size(); --i >= 1; ) {
			HawcPlusScan scan = (HawcPlusScan) scans.get(i);
			
			if(scan.instrument.instrumentData.instrumentConfig.equals(firstScan.instrument.instrumentData.instrumentConfig)) {
				warning("Scan " + scans.get(i).getID() + " is in different instrument configuration. Removing from set.");
				scans.remove(i);				
			}		
		}
		
		super.validate(scans);
	}
	
	
	@Override
	// TODO do it better with relative offset and rotation?
	public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
		Camera.addLocalFixedIndices(this, fixedIndex, radius, toIndex);
		for(int i = toIndex.size(); --i >= 0; ) toIndex.add(toIndex.get(i) + polArrayPixels);
	}


	@Override
	public final int rows() { return rows; }


	@Override
	public final int cols() { return polCols; }
	
	public final int pixels() { return polArrays * polArrayPixels; }
	
	@Override
	public final Vector2D getPixelSize() { return pixelSize; }

	
	public final int getArrayIndex(final int row, final int col) {
		return row * cols() + col;
	}
	
	/**
	 * Writes a flatfield file, used for the chop-nod pipelines, according to the specifications by Marc Berthoud.
	 * 
	 * @param The FITS file name (and path) where the flatfield data is destined.
	 * @throws IOException
	 * @throws FitsException
	 */
	public void writeFlatfield(String fileName) throws IOException, FitsException {
		final int cols = cols();
		
		final int FLAG_R = 1;
		final int FLAG_T = 2;
		
		final float[][] gainR = new float[rows][cols];
		final float[][] gainT = new float[rows][cols];
		final int[][] flagR = new int[rows][cols];
		final int[][] flagT = new int[rows][cols];
		
		// By default flag all pixels, then unflag as appropriate.
		for(int i=rows; --i >= 0; ) {
			Arrays.fill(flagR[i], FLAG_R);
			Arrays.fill(flagT[i], FLAG_T);
			Arrays.fill(gainR[i], Float.NaN);
			Arrays.fill(gainT[i], Float.NaN);
		}
		
		for(HawcPlusPixel pixel : this) {
			if(pixel.pol == T_ARRAY) {
				gainT[pixel.row][pixel.col] = (float) (pixel.gain * pixel.coupling);
				if(pixel.isUnflagged()) flagT[pixel.row][pixel.col] = 0; 
			}
			else if(pixel.pol == R_ARRAY) {
				gainR[pixel.row][pixel.col] = (float) (pixel.gain * pixel.coupling);
				if(pixel.isUnflagged()) flagR[pixel.row][pixel.col] = 0; 
			}
		}
		
		final Fits fits = new Fits();
		
		addHDU(fits, Fits.makeHDU(gainR), "R array gain");
		addHDU(fits, Fits.makeHDU(gainT), "T array gain");
		addHDU(fits, Fits.makeHDU(flagR), "R bad pixel mask");
		addHDU(fits, Fits.makeHDU(flagT), "T bad pixel mask");
		
		FitsExtras.write(fits, fileName);
		fits.close();
		
		System.err.println(" Written flatfield to " + fileName);
	}
	
	private void addHDU(Fits fits, BasicHDU<?> hdu, String extName) throws FitsException {
		hdu.addValue("EXTNAME", extName, "image content ID");
		editHeader(hdu.getHeader(), hdu.getHeader().iterator());
		fits.addHDU(hdu);
	}
	
	/*
	public int fitsToFixedIndex(int i, int j) {  
	    return getSubarrayForFitsCol(j) * subarrayPixels() + i * subarrayCols() + j;	    
	}
	
	
	public static int getSubarrayForFitsCol(int fitsCol) {
	    return fitsCol >> 5;
	}
	
	public static int fitsToSubarrayCol(int col) {
       return col & 31;
	}
	
	public static int subarrayToFitsCol(int subarray, int col) {
	    if(subarray < 0 || subarray >= 4) throw new IndexOutOfBoundsException("invalid subarray index: " + subarray);
	    if(col < 0 || col >= 32) throw new IndexOutOfBoundsException("invalid col index: " + col);
	    return (subarray << 5) | col;
	}
	*/
	
	@Override
    public void error(String message) {
        super.error(message);
        if(drp != null) drp.error(message);
    }
    
    @Override
    public void warning(String message) {
        super.warning(message);
        if(drp != null) drp.warning(message);
    }
    
    @Override
    public void status(String message) {
        super.status(message);
        if(drp != null) drp.info(message);
    }
    
	
	
	final static int polArrays = 2;
	final static int polSubarrays = 2;
	final static int subarrays = polArrays * polSubarrays;
	
	final static int subarrayCols = 32;
	final static int rows = 41;
	final static int subarrayPixels = rows * subarrayCols;
	
	final static int polCols = polSubarrays * subarrayCols;
	final static int polArrayPixels = rows * polCols;
		
    static int R0 = 0;
    static int R1 = 1;
    static int T0 = 2;
    static int T1 = 3;
    
    final static String[] polID = { "R", "T" };

	static double hwpStep = 0.25 * Unit.deg;	
	
	private static DRPMessenger drp;
	
	private static int R_ARRAY = 0;
	private static int T_ARRAY = 1;
	
	// array center assuming a 40 x 67 pixel virtual layout...
    private static Vector2D defaultPointingCenter = new Vector2D(19.5, 32.5); // row, col

}

