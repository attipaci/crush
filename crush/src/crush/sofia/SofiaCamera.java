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

package crush.sofia;


import java.util.List;
import java.util.Vector;

import kovacs.astro.AstroTime;
import kovacs.util.Unit;
import nom.tam.fits.Fits;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;
import crush.CRUSH;
import crush.GroundBased;
import crush.Instrument;
import crush.InstrumentLayout;
import crush.Scan;
import crush.array.Array;
import crush.array.SingleColorPixel;

public abstract class SofiaCamera<ChannelType extends SingleColorPixel> extends Array<ChannelType, ChannelType> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7272751629186147371L;

	public SofiaInstrumentData instrumentData;
	public SofiaArrayData array;
	
	
	public SofiaCamera(String name, InstrumentLayout<? super ChannelType> layout, int size) {
		super(name, layout, size);
	}

	@Override
	public Instrument<ChannelType> copy() {
		SofiaCamera<ChannelType> copy = (SofiaCamera<ChannelType>) super.copy();
		if(instrumentData != null) copy.instrumentData = instrumentData.copy();
		if(array != null) copy.array = array.copy();
		return copy;
	}
	
	
	public abstract void readData(Fits fits) throws Exception;
	
	@Override
	public void parseHeader(Header header) {	
		super.parseHeader(header);
		
		instrumentData = new SofiaInstrumentData(header);
		
		// Set the default angular resolution given the telescope size...
		setResolution(1.22 * instrumentData.wavelength * Unit.um / telescopeDiameter);
		
		array = new SofiaArrayData(header);
		
		samplingInterval = integrationTime = 1.0 / (header.getDoubleValue("SMPLFREQ", Double.NaN) * Unit.Hz);	
	}
	

	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(instrumentData != null) instrumentData.editHeader(cursor);
		if(array != null) array.editHeader(cursor);
		
		//if(hasOption("pixeldata")) 
		//	cursor.add(new HeaderCard("FLATFILE", option("pixeldata").getValue(), "pixel data file."));
	
	}

	@Override
	public void editImageHeader(List<Scan<?,?>> scans, Cursor cursor) throws HeaderCardException {
		super.editImageHeader(scans, cursor);
		
		boolean hasTrackingError = false;
		for(Scan<?,?> scan : scans) hasTrackingError |= ((SofiaScan<?,?>) scan).telescope.hasTrackingError;
		cursor.add(new HeaderCard("TRACERR", hasTrackingError, "Whether any of the data has tracking errors."));
		
		int level = hasOption("calibrated") ? 3 : 2;
		
		// Add SOFIA processing keys
		cursor.add(new HeaderCard("PROCSTAT", "LEVEL_" + level, SofiaProcessingData.getComment(level)));
		cursor.add(new HeaderCard("HEADSTAT", "UNKNOWN", "See original header values in the scan HDUs."));
		cursor.add(new HeaderCard("PIPELINE", "crush v" + CRUSH.getReleaseVersion(), "Software that produced this file."));
		cursor.add(new HeaderCard("PIPEVERS", CRUSH.getFullVersion(), "Full software version information.")); 
		cursor.add(new HeaderCard("PRODTYPE", "CRUSH-IMAGE", "Type of product produced by the software."));
			
		// Add the reduction to the history...
		AstroTime timeStamp = new AstroTime();
		timeStamp.now();
	
		// Add required keys and prior history
		((SofiaScan<?,?>) scans.get(0)).addRequiredKeys(cursor);
		
		cursor.setKey("HISTORY");	
		//cursor.add(new HeaderCard("HISTORY", "Reduced: crush v" + CRUSH.getFullVersion(), false));
		cursor.add(new HeaderCard("HISTORY", "Reduced: crush v" + CRUSH.getFullVersion() + " @ " + timeStamp.getFitsTimeStamp(), false));
		
		for(int i=0; i<scans.size(); i++)
			cursor.add(new HeaderCard("HISTORY", " OBS-ID[" + (i+1) + "]: " + scans.get(i).getID(), false));
		
		
		// Go back to before the history...
		cursor.setKey("HISTORY");
	}	
	
	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		
		final SofiaScan<?,?> firstScan = (SofiaScan<?,?>) scans.get(0);
		double wavelength = firstScan.instrument.instrumentData.wavelength;
		for(int i=scans.size(); --i >= 1; ) if(((SofiaScan<?,?>) scans.get(i)).instrument.instrumentData.wavelength != wavelength) {
			System.err.println("  WARNING! Scan " + scans.get(i).getID() + " in a different band. Removing from set.");
			scans.remove(i);
		}
		
			
		if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);
		
		// Any options dynamically set for the first scan will be made global also...
		String[] dynamicOptions = { "instrument.config", "instrument.mode", "filter", "filter2", "slit" };
		for(String key : dynamicOptions)
		if(firstScan.hasOption(key)) getOptions().parse(key + " " + firstScan.option(key));
		
		super.validate(scans);
	}
	
	public static final double telescopeDiameter = 2.5 * Unit.m;
	
}
