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
import crush.sofia.SofiaHeader;
import jnum.Configurator;
import jnum.LockedException;
import jnum.Unit;
import jnum.data.ArrayUtil;
import jnum.io.fits.FitsExtras;
import jnum.math.Vector2D;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

public class HawcPlus extends SofiaCamera<HawcPlusPixel, HawcPlusPixel> implements GridIndexed {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3009881856872575936L;

	Vector2D pixelSize;
	
	boolean[] hasSubarray;
	Vector2D[] subarrayOffset;
	double[] subarrayOrientation;
	boolean[] subarrayInverted;
	
	double[] polZoom;
	
	boolean darkSquidCorrection = false;
	int[][] darkSquidLookup;           // sub,col
	
	int[] mceSubarray;                 // subarray assignment for MCE 0-3
	int[][] detectorBias;              // sub [4], line [20]
	
	double hwpTelescopeVertical;
	
	public HawcPlus() {
		super("hawc+", new SingleColorArrangement<HawcPlusPixel>(), pixels);
		mount = Mount.NASMYTH_COROTATING;
		
	}
	
	@Override
	public void setOptions(Configurator options) {
		super.setOptions(options);
		if(drp == null) if(hasOption("drp")) initDRPMessages();
	}
	
	private void initDRPMessages() {
		info("Activating DRP messages over TCP/IP.");
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
		
		if(pixelSize != null) copy.pixelSize = (Vector2D) pixelSize.copy();
        if(hasSubarray != null) copy.hasSubarray = Arrays.copyOf(hasSubarray, hasSubarray.length);   
		if(subarrayOffset != null) copy.subarrayOffset = Arrays.copyOf(subarrayOffset, subarrayOffset.length);
		if(subarrayOrientation != null) copy.subarrayOrientation = Arrays.copyOf(subarrayOrientation, subarrayOrientation.length);
 		if(subarrayInverted != null) copy.subarrayInverted = Arrays.copyOf(subarrayInverted, subarrayInverted.length);
  		if(polZoom != null) copy.polZoom = Arrays.copyOf(polZoom, polZoom.length);
		if(darkSquidLookup != null) {
            try { copy.darkSquidLookup = (int[][]) ArrayUtil.copyOf(darkSquidLookup); } 
            catch(Exception e) { error(e); }
		}
		if(mceSubarray != null) copy.mceSubarray = Arrays.copyOf(mceSubarray, mceSubarray.length);
		if(detectorBias != null) copy.detectorBias = Arrays.copyOf(detectorBias, detectorBias.length);
		
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
		catch(Exception e) { error(e); }	
		
		try { addDivision(getDivision("subarrays", HawcPlusPixel.class.getField("sub"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
		catch(Exception e) { error(e); }	
				
		try { addDivision(getDivision("subarrays", HawcPlusPixel.class.getField("sub"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
        catch(Exception e) { error(e); } 
		
		try { addDivision(getDivision("bias", HawcPlusPixel.class.getField("biasLine"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
        catch(Exception e) { error(e); } 
		
		// If correction was applied at validation, then only decorrelate detectors
		// Otherwise, decorrelate including the dark SQUIDs...
		int muxSkipFlag = hasOption("darkcorrect") ? Channel.FLAG_DEAD | Channel.FLAG_BLIND : Channel.FLAG_DEAD;
		
		try { addDivision(getDivision("mux", HawcPlusPixel.class.getField("mux"), muxSkipFlag)); 
			ChannelDivision<HawcPlusPixel> muxDivision = divisions.get("mux");
			
			// Order mux channels in pin order...
			for(ChannelGroup<HawcPlusPixel> mux : muxDivision) {
				Collections.sort(mux, new Comparator<HawcPlusPixel>() {
					@Override
					public int compare(HawcPlusPixel o1, HawcPlusPixel o2) {
						if(o1.row == o2.row) return 0;
						return o1.row > o2.row ? 1 : -1;
					}	
				});
			}
		}
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("rows", HawcPlusPixel.class.getField("row"), muxSkipFlag)); }
		catch(Exception e) { error(e); }	
	}
	
	@Override
	public void initModalities() {
		super.initModalities();
		       
		addModality(modalities.get("obs-channels").new CoupledModality("polarrays", "p", new HawcPlusPolImbalance()));
		
		/* TODO subarrays should be coupled modality...
		try { 
			Modality<?> subMode = new CorrelatedModality("subarrays", "s", divisions.get("subarrays"), HawcPlusPixel.class.getField("gain")); 
			subMode.solveGains = false;
			addModality(subMode);
		}
		catch(NoSuchFieldException e) { error(e); }
		*/
		
		try {
            CorrelatedModality biasMode = new CorrelatedModality("bias", "b", divisions.get("bias"), HawcPlusPixel.class.getField("biasGain"));     
            //biasMode.solveGains = false;
            biasMode.setGainFlag(HawcPlusPixel.FLAG_BIAS);
            addModality(biasMode);
        }
        catch(NoSuchFieldException e) { error(e); }  
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), HawcPlusPixel.class.getField("muxGain"));		
			//muxMode.solveGains = false;
			muxMode.setGainFlag(HawcPlusPixel.FLAG_MUX);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { error(e); }	
			
		try { 
			Modality<?> addressMode = new CorrelatedModality("rows", "r", divisions.get("rows"), HawcPlusPixel.class.getField("pinGain")); 
			addressMode.setGainFlag(HawcPlusPixel.FLAG_ROW);
			addModality(addressMode);
		}
		catch(NoSuchFieldException e) { error(e); }
		
	}
	
	@Override
    public void parseHeader(SofiaHeader header) {
	    super.parseHeader(header);
	    
	    // TODO should not be necessary if the header is proper...
	    if(Double.isNaN(integrationTime) || integrationTime < 0.0) {
	        warning("Missing SMPLFREQ. Will assume 203.25 Hz.");
	        integrationTime = samplingInterval = 1.0 * Unit.s / 203.25;
	    }
	    
	    hasSubarray = new boolean[subarrays];
	    
	    String mceMap = header.getString("MCEMAP");
	    mceSubarray = new int[subarrays];
	    Arrays.fill(mceSubarray, -1);
	    
	    if(mceMap != null) {
	        StringTokenizer tokens = new StringTokenizer(mceMap, " \t,;:");
	        for(int sub=0; sub<subarrays; sub++) if(tokens.hasMoreTokens()) {
	            String assignment = tokens.nextToken();
	            try { 
	                int mce = Integer.parseInt(assignment);
	                if(mce >= 0) mceSubarray[mce] = sub;
	                hasSubarray[sub] = mce >= 0;
	            }
	            catch(NumberFormatException e) { warning("Invalid MCE assignment: " + assignment);}
	        }       
	    }   
	    
	    if(hasOption("subarray")) selectSubarrays(option("subarray").getValue());
	}
	
	@Override
	public void loadChannelData() {
		
			
		// The subarrays orientations
		subarrayOrientation = new double[subarrays];
		subarrayOrientation[R0] = hasOption("rotation.r0") ? option("rotation.r0").getDouble() * Unit.deg : 0.0;
		subarrayOrientation[R1] = hasOption("rotation.r1") ? option("rotation.r1").getDouble() * Unit.deg : Math.PI;
		subarrayOrientation[T0] = hasOption("rotation.t0") ? option("rotation.t0").getDouble() * Unit.deg : 0.0;
		subarrayOrientation[T1] = hasOption("rotation.t1") ? option("rotation.t1").getDouble() * Unit.deg : Math.PI;

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
		
		// subarray gains
		subarrayInverted = new boolean[subarrays];
		subarrayInverted[R0] = hasOption("sign.r0") ? option("sign.r0").getSign() < 0 : false;
        subarrayInverted[R1] = hasOption("sign.r1") ? option("sign.r1").getSign() < 0 : false;
        subarrayInverted[T0] = hasOption("sign.t0") ? option("sign.t0").getSign() < 0 : false;
        subarrayInverted[T1] = hasOption("sign.t1") ? option("sign.t1").getSign() < 0 : false;
		
		// The default pixelSizes...
		Vector2D pixelSize = new Vector2D(array.pixelScale, array.pixelScale);
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
		}
		
		setPixelSize(pixelSize);
				
		// TODO load bias gains? ...
		
		super.loadChannelData();
		
		if(hasOption("edge")) discardEdges(getOptions().get("edge"));
		
	}
	
	public void discardEdges(Configurator option) {
	    List<Integer> values = option.getIntegers();
	    int rows = values.get(0);
	    int cols = values.size() > 1 ? values.get(1) : rows;
	    discardEdges(rows, cols);
	}
		
	public void discardEdges(int eRows, int eCols) {
	    info("Cropping " + eRows + " rows & " + eCols + " cols from subarray edges.");
	    
	    for(HawcPlusPixel pixel : this) {
	        if(pixel.row < eRows || pixel.row >= rows - eRows) pixel.flag(Channel.FLAG_DISCARD | Channel.FLAG_DEAD);
	        int col = pixel.col % subarrayCols;
	        if(col < eCols || col >= subarrayCols - eCols) pixel.flag(Channel.FLAG_DISCARD | Channel.FLAG_DEAD); 
	    }
	}
	
	public void selectSubarrays(String spec) {	
		StringTokenizer tokens = new StringTokenizer(spec, ",; \t");
			
		boolean[] oldHasSubarray = hasSubarray;
		hasSubarray = new boolean[subarrays];
		
		while(tokens.hasMoreTokens()) {
		    String subSpec = tokens.nextToken();
			String value = subSpec.toUpperCase();
			char pol = value.charAt(0);
			int sub = value.length() > 1 ? value.charAt(1) - '0' : -1;
			
			int polarray = -1;
			if(pol == 'R') polarray = R0;
			else if(pol == 'T') polarray = T0;
			
			if(polarray < 0) warning("Invalid subarray selection: " + value);
			else {
				if(sub < 0) for(int i=polSubarrays; --i >= 0; ) hasSubarray[polarray + i] = oldHasSubarray[polarray + i];
				else hasSubarray[polarray + sub] = oldHasSubarray[polarray + sub];
			}
		}
		
	}
	
	
	private void setPixelSize(Vector2D size) {	
		pixelSize = size;
		
		info("Boresight pixel from FITS is " + array.boresightIndex);
		
		if(hasOption("pcenter")) {
		    array.boresightIndex = option("pcenter").getVector2D();	
		    info("Boresight override --> " + array.boresightIndex);
		} 
		else if(Double.isNaN(array.boresightIndex.x())) {
		    array.boresightIndex = defaultBoresightIndex;
		    warning("Missing FITS boresight --> " + array.boresightIndex);
		}
		Vector2D center = getPosition(0, array.boresightIndex.x(), array.boresightIndex.y());
	
		for(HawcPlusPixel pixel : this) pixel.calcPosition();
		   
		// Set the pointing center...
		setReferencePosition(center);
	}
	
	
	// TODO (couple to readData()...)
	/* 
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
			getOptions().parse(settings.get(bias + ""));
		}
	}
	 */

		
	@Override
	public int maxPixels() {
		return storeChannels;
	}    
	
	@Override
	public String getChannelDataHeader() {
		return super.getChannelDataHeader() + "\tGmux\tidx\tsub\trow\tcol";
	}
	
	@Override
	public void editImageHeader(List<Scan<?,?>> scans, Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
		super.editImageHeader(scans, header, cursor);
		// Add HAWC+ specific keywords
		cursor.add(new HeaderCard("COMMENT", "<------ HAWC+ Header Keys ------>", false));
		cursor.add(new HeaderCard("PROCLEVL", "crush", "Last pipeline processing step on the data."));
	}

	@Override
	public void readData(BasicHDU<?>[] hdu) throws Exception {      
		for(int i=1; i<hdu.length; i++) {
		    String extName = hdu[i].getHeader().getStringValue("EXTNAME").toLowerCase(); 
		    if(extName.equals("configuration")) parseConfigurationHDU(hdu[i]);
		}
	}
		
	private void parseConfigurationHDU(BasicHDU<?> hdu) {	   
	    Header header = hdu.getHeader();
	    
	    detectorBias = new int[subarrays][MCE_BIAS_LINES];
	    
	    int found = 0;
	    for(int mce=0; mce < subarrays; mce++) {
	        String key = "HIERARCH.MCE" + mce + "_TES_BIAS";
	        String values = header.getStringValue(key);
	        if(values != null) {
	            parseTESBias(mceSubarray[mce], values);
	            found++;
	        }
	    }
	    
	    info("Parsing HAWC+ TES bias. Found for " + found + " MCEs.");
	}
	
	private void parseTESBias(int sub, String values) {	    
	    StringTokenizer tokens = new StringTokenizer(values, ", \t;");
	    for(int i=0; i<20; i++) {
	        if(!tokens.hasMoreTokens()) {
	            warning("Missing TES bias values for subarray " + sub);
	            break;
	        }
	        detectorBias[sub][i] = Integer.parseInt(tokens.nextToken());
	    }
	}
	
	@Override
	public void validate(Scan<?,?> scan) {

		darkSquidCorrection = hasOption("darkcorrect");
		
		int nSub = 0;
		int polMask = 0;
		for(int i=subarrays; --i >= 0; ) {
		    polMask |= (i & 2) + 1;
		    if(hasSubarray[i]) nSub++;
		}
		
		if(polMask != 3) {
		    try { getOptions().blacklist("correlated.polarrays"); }
		    catch(LockedException e) {}
		}
		  
		/*
        if(nSub < 2 || (polMask == 3 && nSub == 2)) {
            try { getOptions().blacklist("correlated.subs"); }
            catch(LockedException e) {}
        }
        */
		
		clear();
		ensureCapacity(nSub * subarrayPixels);

		for(int c=0; c<pixels; c++) {
		    HawcPlusPixel pixel = new HawcPlusPixel(this, c);
		    if(hasSubarray[pixel.sub]) add(pixel);
		}
		
			    
		if(!hasOption("filter")) getOptions().parseSilent("filter " + instrumentData.wavelength + "um");	
		info("HAWC+ Filter set to " + option("filter").getValue());
		
		super.validate(scan);
		
		createDarkSquidLookup();
		
	}
	
	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		final HawcPlusScan firstScan = (HawcPlusScan) scans.get(0);
	
		for(int i=scans.size(); --i >= 1; ) {
			HawcPlusScan scan = (HawcPlusScan) scans.get(i);
			
			if(!scan.instrument.instrumentData.instrumentConfig.equals(firstScan.instrument.instrumentData.instrumentConfig)) {
				warning("Scan " + scans.get(i).getID() + " is in different instrument configuration. Removing from set.");
				scans.remove(i);				
			}		
		}
		
		super.validate(scans);
	}
	
	@Override
    public boolean slim(boolean reindex) {
	    boolean slimmed = super.slim(reindex);
	    if(slimmed) createDarkSquidLookup();
	    return slimmed;
	}
	
	public void createDarkSquidLookup() {
	    darkSquidLookup = new int[subarrays][subarrayCols];
	    for(int i=subarrays; --i >= 0; ) Arrays.fill(darkSquidLookup[i], -1);
	    for(HawcPlusPixel pixel : this) if(pixel.isFlagged(Channel.FLAG_BLIND)) darkSquidLookup[pixel.sub][pixel.col] = pixel.index;
	}
	
	// TODO... currently treating all subarrays as non-overlapping -- which is valid for point sources...
	@Override
	public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
		Camera.addLocalFixedIndices(this, fixedIndex, radius, toIndex);
		for(int sub=1; sub < subarrays; sub++) {
		    final int subOffset = sub * subarrayPixels;
		    for(int i = toIndex.size(); --i >= 0; ) toIndex.add(toIndex.get(i) + subOffset);
		}
	}


	@Override
	public final int rows() { return rows; }

	@Override
	public final int cols() { return subarrayCols; }
	
	@Override
	public final Vector2D getPixelSize() { return pixelSize; }


	/**
	 * Writes a flatfield file, used for the chop-nod pipelines, according to the specifications by Marc Berthoud.
	 * 
	 * @param The FITS file name (and path) where the flatfield data is destined.
	 * @throws IOException
	 * @throws FitsException
	 */
	public void writeFlatfield(String fileName) throws IOException, FitsException {	
		final int FLAG_R = 1;
		final int FLAG_T = 2;
		
		final float[][] gainR = new float[rows][polCols];
		final float[][] gainT = new float[rows][polCols];
		final float[][] nonlinearR = new float[rows][polCols];
        final float[][] nonlinearT = new float[rows][polCols];
		
		final int[][] flagR = new int[rows][polCols];
		final int[][] flagT = new int[rows][polCols];
		
		// By default flag all pixels, then unflag as appropriate.
		for(int i=rows; --i >= 0; ) {
			Arrays.fill(flagR[i], FLAG_R);
			Arrays.fill(flagT[i], FLAG_T);
			Arrays.fill(gainR[i], 1.0F);
			Arrays.fill(gainT[i], 1.0F);
		}
		
		for(HawcPlusPixel pixel : this) {
		    float iG = (float)(1.0 / (pixel.gain * pixel.coupling));
		    
		    int col = (pixel.sub & 1) * HawcPlus.subarrayCols + pixel.col;
		    
		    if(pixel.pol == R_ARRAY) {
                gainR[pixel.subrow][col] = iG;
                nonlinearR[pixel.subrow][col] = (float) pixel.nonlinearity;
                if(pixel.isUnflagged()) flagR[pixel.subrow][col] = 0; 
            }
		    else if(pixel.pol == T_ARRAY) {
				gainT[pixel.subrow][col] = iG;
				nonlinearT[pixel.subrow][col] = (float) pixel.nonlinearity;
				if(pixel.isUnflagged()) flagT[pixel.subrow][col] = 0; 
			}
			
		}
		
		final Fits fits = new Fits();
		
		addHDU(fits, Fits.makeHDU(gainR), "R array gain");
		addHDU(fits, Fits.makeHDU(gainT), "T array gain");
		addHDU(fits, Fits.makeHDU(flagR), "R bad pixel mask");
		addHDU(fits, Fits.makeHDU(flagT), "T bad pixel mask");
		addHDU(fits, Fits.makeHDU(nonlinearR), "R array nonlinearity");
        addHDU(fits, Fits.makeHDU(nonlinearT), "T array nonlinearity");
		
		FitsExtras.write(fits, fileName);
		fits.close();
		
		notify("Written flatfield to " + fileName);
	}
	
	private void addHDU(Fits fits, BasicHDU<?> hdu, String extName) throws FitsException {
		hdu.addValue("EXTNAME", extName, "image content ID");
		editHeader(hdu.getHeader(), hdu.getHeader().iterator());
		fits.addHDU(hdu);
	}
        
    public Vector2D getPosition(int sub, double row, double col) {
        Vector2D v = new Vector2D(col, 39.0 - row);
        v.rotate(subarrayOrientation[sub]);
        v.add(subarrayOffset[sub]);
        // In the geometry document X,Y is oriented like alpha,delta
        // But, in crush, focal plane positions should be oriented like native
        // systems, e.g. Az, EL. So, compared to the geOmetry document x -> -x
        v.scaleX(-pixelSize.x());       
        v.scaleY(pixelSize.y());
        v.scale(polZoom[sub>>1]);
        
        return v;
    }

	final static int polArrays = 2;
	final static int polSubarrays = 2;
	final static int subarrays = polArrays * polSubarrays;
	
	final static int subarrayCols = 32;
	final static int rows = 41;
	final static int subarrayPixels = rows * subarrayCols;
	
	final static int polCols = polSubarrays * subarrayCols;
	final static int polArrayPixels = rows * polCols;
	
	final static int pixels = polArrays * polArrayPixels;
	
	final static int DARK_SQUID_ROW = rows - 1;
	
	final static int MCE_BIAS_LINES = 20;
		
    static int R0 = 0;
    static int R1 = 1;
    static int T0 = 2;
    static int T1 = 3;
    
    final static String[] polID = { "R", "T" };

	static double hwpStep = 0.25 * Unit.deg;	
	
	private static DRPMessenger drp;
	
	private static final int R_ARRAY = 0;
	private static final int T_ARRAY = 1;

	public static final Vector2D defaultBoresightIndex = new Vector2D(15.5, 19.5);

}

