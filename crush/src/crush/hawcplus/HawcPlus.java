/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import crush.array.Array;
import crush.array.GeometricRowColIndexed;
import crush.array.SingleColorLayout;
import crush.sofia.SofiaCamera;
import jnum.Configurator;
import jnum.Unit;
import jnum.math.Vector2D;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

public class HawcPlus extends SofiaCamera<HawcPlusPixel> implements GeometricRowColIndexed {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3009881856872575936L;

	int polarrays = 2;
	int polsubarrays = 2;
	int subarrayCols = 32;
	int rows = 41;

	private Vector2D arrayPointingCenter; // row,col
	Vector2D pixelSize = HawcPlusPixel.defaultSize;
	Vector2D[][] subarrayOffset = new Vector2D[polarrays][polsubarrays];
	
	
	public HawcPlus() {
		super("hawc+", new SingleColorLayout<HawcPlusPixel>());
		arrayPointingCenter = (Vector2D) defaultPointingCenter.clone();
		for(int i=0; i<polarrays; i++) for(int j=0; j<polsubarrays; j++) subarrayOffset[i][j] = new Vector2D();
		mount = Mount.LEFT_NASMYTH;
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
		
		if(subarrayOffset != null) {
			copy.subarrayOffset = new Vector2D[polarrays][polsubarrays];
			for(int i=0; i<polarrays; i++) for(int j=0; j<polsubarrays; j++) copy.subarrayOffset[i][j] = (Vector2D) subarrayOffset[i][j].clone();
		}
		return copy;
	}
	
	@Override
	public String getTelescopeName() {
		return "SOFIA";
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
		
		try { addDivision(getDivision("polarrays", HawcPlusPixel.class.getField("polarray"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("subarrays", HawcPlusPixel.class.getField("subarray"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("cols", HawcPlusPixel.class.getField("col"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("rows", HawcPlusPixel.class.getField("row"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
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
			Modality<?> pinMode = new CorrelatedModality("polarrays", "P", divisions.get("polarrays"), HawcPlusPixel.class.getField("polarrayGain")); 
			pinMode.setGainFlag(HawcPlusPixel.FLAG_POL);
			addModality(pinMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { 
			Modality<?> pinMode = new CorrelatedModality("subarrays", "S", divisions.get("subarrays"), HawcPlusPixel.class.getField("subarrayGain")); 
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
		
		try { 
			Modality<?> colMode = new CorrelatedModality("cols", "c", divisions.get("cols"), HawcPlusPixel.class.getField("colGain")); 
			colMode.setGainFlag(HawcPlusPixel.FLAG_COL);
			addModality(colMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { 
			Modality<?> rowMode = new CorrelatedModality("rows", "r", divisions.get("rows"), HawcPlusPixel.class.getField("rowGain")); 
			rowMode.setGainFlag(HawcPlusPixel.FLAG_ROW);
			addModality(rowMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
	}
	
	@Override
	public void loadChannelData() {
		if(hasOption("subarray")) selectSubarrays(option("subarray").getValue());
		
		// Update the pointing centers...
		if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
		else arrayPointingCenter = array.arrayPointingCenter;
		
		subarrayOffset[0][0] = hasOption("offset.r1") ? option("offset.r1").getVector2D() : new Vector2D();
		subarrayOffset[0][1] = hasOption("offset.r2") ? option("offset.r2").getVector2D() : new Vector2D();
		subarrayOffset[1][0] = hasOption("offset.t1") ? option("offset.t1").getVector2D() : new Vector2D();
		subarrayOffset[1][1] = hasOption("offset.t2") ? option("offset.t2").getVector2D() : new Vector2D();
		
		Vector2D pixelSize = HawcPlusPixel.defaultSize;
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
		}
		else pixelSize = new Vector2D(array.pixelScale, array.pixelScale);

		setPlateScale(pixelSize);
		
		if(hasOption("rotation.t")) rotateT(option("rotation.t").getDouble() * Unit.deg);
			
		super.loadChannelData();
	}
	
	public void selectSubarrays(String spec) {
		for(HawcPlusPixel pixel : this) pixel.flag(Channel.FLAG_DISCARD);
		
		StringTokenizer tokens = new StringTokenizer(spec, ",; \t");
		while(tokens.hasMoreTokens()) {
			String value = tokens.nextToken().toUpperCase();
			char pol = value.charAt(0);
			int index = value.length() > 1 ? value.charAt(1) - '1' : 0;
			
			int polIndex = -1;
			if(pol == 'R') polIndex = 0;
			else if(pol == 'T') polIndex = 1;
			
			if(polIndex >= 0) {
				if(index <= 0) for(int i=polSubArrays(); --i >= 0; ) selectSubarray(pol, i);
				else selectSubarray(pol, index);
			}
		}
		
		for(HawcPlusPixel pixel : this) if(pixel.isFlagged(Channel.FLAG_DISCARD)) pixel.flag(Channel.FLAG_DEAD);
		
	}
	
	public void selectSubarray(int pol, int index) {
		int from = pol * polArrayPixels() + index * subarrayPixels();
		for(int k=subarrayPixels(); --k >= 0; ) get(from + k).unflag(Channel.FLAG_DISCARD);
	}
	
	public void setPlateScale(Vector2D size) {	
		pixelSize = size;
		
		Vector2D center = HawcPlusPixel.getPosition(size, subarrayOffset[0][0], arrayPointingCenter.x() - 1.0, arrayPointingCenter.y() - 1.0);			
		for(HawcPlusPixel pixel : this) pixel.calcPosition();
		
		// Set the pointing center...
		setReferencePosition(center);
	}
	
	public void rotateT(double angle) {
		final int polArrayPixels = polArrayPixels();
		for(int i=pixels(); --i >= polArrayPixels; ) get(i).position.rotate(angle);
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
			
		if(!hasOption("filter")) getOptions().parse("filter " + instrumentData.wavelength + "um");	
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
	// TODO do it better with relative offset and rotation...
	public void addLocalFixedIndices(int fixedIndex, double radius, List<Integer> toIndex) {
		Array.addLocalFixedIndices(this, fixedIndex, radius, toIndex);
		final int polArrayPixels = polArrayPixels();
		for(int i = toIndex.size(); --i >= 0; ) toIndex.add(toIndex.get(i) + polArrayPixels);
	}


	@Override
	public int rows() {
		return rows;
	}


	@Override
	public int cols() {
		return polsubarrays * subarrayCols;
	}

	public int subarrayCols() {
		return subarrayCols;
	}
	
	public int polArrays() {
		return polarrays;
	}
	
	public int polSubArrays() {
		return polsubarrays;
	}
	
	public int polArrayPixels() {
		return cols() * rows();
	}
	
	public int subarrayPixels() {
		return rows() * subarrayCols();
	}
	
	public int pixels() {
		return polarrays * polArrayPixels();
	}
	
	@Override
	public Vector2D getPixelSize() {
		return pixelSize;
	}

	
	public final int getArrayIndex(final int row, final int col) {
		return row * cols() + col;
	}
	
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
	
	
	// subarray center assuming 41x32 pixels
	private static Vector2D defaultPointingCenter = new Vector2D(20.5, 32.5); // row, col

	private static DRPMessenger drp;
	
	
}

