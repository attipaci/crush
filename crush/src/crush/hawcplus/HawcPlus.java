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

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import kovacs.data.DataPoint;
import kovacs.math.Vector2D;
import kovacs.text.TableFormatter;
import kovacs.util.*;
import crush.*;
import crush.array.*;
import crush.sofia.SofiaCamera;
import crush.sofia.SofiaScan;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

public class HawcPlus extends SofiaCamera<HawcPlusPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3009881856872575936L;

	
	private Vector2D arrayPointingCenter; // row,col
	
	
	public HawcPlus() {
		super("hawc+", pixels);	
		arrayPointingCenter = (Vector2D) defaultPointingCenter.clone();
		mount = Mount.LEFT_NASMYTH;
	}

	@Override
	public Instrument<HawcPlusPixel> copy() {
		HawcPlus copy = (HawcPlus) super.copy();
		if(arrayPointingCenter != null) copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.clone();	
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
		
		try { addDivision(getDivision("cols", HawcPlusPixel.class.getField("col"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("rows", HawcPlusPixel.class.getField("row"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }	
		
	}
	
	@Override
	public void initModalities() {
		super.initModalities();
		
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
		// Update the pointing centers...
		if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
		else arrayPointingCenter = array.arrayPointingCenter;
		
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
			
		super.loadChannelData();
	}
	
	public void setPlateScale(Vector2D size) {
		// Make all pixels the same size. Also calculate their positions...
		for(HawcPlusPixel pixel : this) pixel.size = size;
		
		Vector2D center = HawcPlusPixel.getPosition(size, arrayPointingCenter.x() - 1.0, arrayPointingCenter.y() - 1.0);			
		
		// Set the pointing center...
		setReferencePosition(center);
		
		if(hasOption("rotation")) rotate(option("rotation").getDouble() * Unit.deg);
	}
	
	public void rotate(double angle) {
		for(HawcPlusPixel pixel : this) pixel.position.rotate(angle);
	}
	
	// Calculates the offset of the pointing center from the nominal center of the array
	public Vector2D getPointingCenterOffset() {
		Vector2D offset = (Vector2D) arrayPointingCenter.clone();
		offset.subtract(defaultPointingCenter);
		if(hasOption("rotation")) offset.rotate(option("rotation").getDouble() * Unit.deg);
		return offset;
	}

	@Override
	public void setReferencePosition(Vector2D position) {
		for(HawcPlusPixel pixel : this) pixel.calcPosition();
		super.setReferencePosition(position);
	}
	
	// TODO need to incorporate into scan reading...
	protected void parseHardwareHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		Object[] row = hdu.getRow(0);
			
		if(!isEmpty()) clear();
		ensureCapacity(pixels);
		
		for(int c = 0; c<pixels; c++) add(new HawcPlusPixel(this, c));

		// TODO 
		//setBiasOptions();	
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
	public void editImageHeader(List<Scan<?,?>> scans, Cursor cursor) throws HeaderCardException {
		super.editImageHeader(scans, cursor);
		// Add HAWC+ specific keywords
		cursor.add(new HeaderCard("PROCLEVL", "crush", "Last pipeline processing step on the data."));
	}
	
	
	public static final int subarrays = 2;
	public static final int cols = 64;
	public static final int rows = 41;
	public static final int subarrayPixels = cols * rows;
	public static final int pixels = subarrays * subarrayPixels;
	
	// subarray center assuming 32x41 pixels
	private static Vector2D defaultPointingCenter = new Vector2D(31.5, 20.0); // row, col
		
	
}

	
}
