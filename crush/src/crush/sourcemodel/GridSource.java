/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sourcemodel;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;

import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;
import crush.CRUSH;
import crush.GenericInstrument;
import crush.Instrument;
import crush.Scan;
import util.Configurator;
import util.CoordinatePair;
import util.Unit;
import util.Util;
import util.data.Grid2D;
import util.data.GridImage;
import util.data.GridMap;


public abstract class GridSource<CoordinateType extends CoordinatePair> extends GridMap<CoordinateType> implements Cloneable {
	public Instrument<?> instrument;
	public Vector<Scan<?, ?>> scans = new Vector<Scan<?, ?>>();

	public String commandLine;
	
	public int generation = 0;
	public double integrationTime = 0.0;	
	
	
	public GridSource() {
	}
	
	public GridSource(String fileName, Instrument<?> instrument) throws Exception { 
		read(fileName);		
	}

	public GridSource(int sizeX, int sizeY) {
		super(sizeX, sizeY);
	}
	
	@Override
	public void reset() {
		super.reset();
		integrationTime = 0.0;
	}
	
	@Override
	public void addDirect(final GridMap<?> map, final double w) {
		super.addDirect(map, w);
		if(map instanceof GridSource) integrationTime += ((GridSource<?>) map).integrationTime;
	}
	
	@Override
	public double getImageFWHM() {
		return Math.hypot(instrument.resolution, smoothFWHM);
	}

	public double getInstrumentBeamArea() {
		double size = instrument.resolution * fwhm2size;
		return size*size;
	}
	
	// Normalize assuming weighting was sigma weighting
	public void normalize() {
		new Task<Void>() {
			@Override
			public void process(int i, int j) { normalize(i, j); }
		}.process();
	}

	public void normalize(int i, int j) { 
		if(getWeight(i, j) <= 0.0) {
			setValue(i, j, 0.0);
			setWeight(i, j, 0.0);
			flag(i, j);
		} 
		else {
			scaleValue(i, j, 1.0 / getWeight(i, j));	
			unflag(i, j);	    
		}
	}
	
	
	@Override
	public Fits createFits() throws HeaderCardException, FitsException, IOException {
		Fits fits = super.createFits();
		
		if(instrument != null) if(instrument.hasOption("write.scandata")) 
			for(Scan<?,?> scan : scans) fits.addHDU(scan.getSummaryHDU(instrument.options));
		
		return fits;
	}
	
	
	@Override
	public void write() throws HeaderCardException, FitsException, IOException {
		if(fileName == null) fileName = CRUSH.workPath + File.separator + name + ".fits";  
		super.write();
	}
	
	@Override
	public GridImage<CoordinateType> getRegrid(final Grid2D<CoordinateType> toGrid) throws IllegalStateException {	
		GridSource<CoordinateType> regrid = (GridSource<CoordinateType>) super.getRegrid(toGrid);
		regrid.setUnit(unit.name);
		return regrid;
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException, FitsException, IOException {
		super.editHeader(cursor);
		
		cursor.add(new HeaderCard("SCANS", scans.size(), "The number of scans in this composite image."));
		cursor.add(new HeaderCard("INTEGRTN", integrationTime / Unit.s, "The total integration time in seconds."));
		
		// Add the command-line reduction options
		if(commandLine != null) {
			StringTokenizer args = new StringTokenizer(commandLine);
			cursor.add(new HeaderCard("ARGS", args.countTokens(), "The number of arguments passed from the command line."));
			int i=1;
			while(args.hasMoreTokens()) Util.addLongFitsKey(cursor, "ARG" + (i++), args.nextToken(), "Command-line argument.");
		}
		
		cursor.add(new HeaderCard("V2JY", instrument.janskyPerBeam(), "1 Jy/beam in instrument data units."));	
		
		if(instrument != null) {
			instrument.editImageHeader(cursor);
			if(instrument.options != null) instrument.options.editHeader(cursor);
		}
	}

	@Override
	public void parseHeader(Header header) throws Exception {		
		super.parseHeader(header);
		
		integrationTime = header.getDoubleValue("INTEGRTN", Double.NaN) * Unit.s;
		parseInstrumentData(header);

		setUnit(header.getStringValue("BUNIT"));
	}
	
	private void parseInstrumentData(Header header) throws HeaderCardException, InstantiationException, IllegalAccessException {
		if(instrument == null) {
			if(header.containsKey("INSTRUME")) {
				instrument = Instrument.forName(header.getStringValue("INSTRUME"));
				if(instrument == null) {
					instrument = new GenericInstrument(header.getStringValue("INSTRUME"));
					if(header.containsKey("TELESCOP")) ((GenericInstrument) instrument).telescope = header.getStringValue("TELESCOP");
				}
			}
			else {
				instrument = new GenericInstrument("unknown");
				if(header.containsKey("TELESCOP")) ((GenericInstrument) instrument).telescope = header.getStringValue("TELESCOP");
			}
		}
		
		if(instrument.options == null) instrument.options = new Configurator();

		instrument.parseHeader(header);

		// get the beam and calculate derived quantities
		if(header.containsKey("BEAM")) 
			instrument.resolution = header.getDoubleValue("BEAM", instrument.resolution / Unit.arcsec) * Unit.arcsec;
		else if(header.containsKey("BMAJ"))
			instrument.resolution =  header.getDoubleValue("BMAJ", instrument.resolution / Unit.deg) * Unit.deg;
		else 
			instrument.resolution = 3.0 * Math.sqrt(getGrid().getPixelArea());
	}



	protected double getUnit(String name) {
		if(name.contains("/")) {
			int index = name.lastIndexOf('/');
			String baseUnit = name.substring(0, index);
			String area = name.substring(index + 1).toLowerCase();
			return getUnit(baseUnit) / getAreaUnit(area);
		}
		else {
			Unit dataUnit = instrument.getDataUnit();
			if(name.equalsIgnoreCase(dataUnit.name)) return dataUnit.value * getInstrumentBeamArea();
			else if(name.equalsIgnoreCase("Jy") || name.equalsIgnoreCase("jansky")) return instrument.janskyPerBeam() * getInstrumentBeamArea();
			else {
				Unit u = Unit.get(name);
				if(u != null) return u.value;
				// Else assume there is a standard multiplier in front, such as k, M, m, u...
				else return  Unit.getMultiplier(name.charAt(0)) * getUnit(name.substring(1));
			}
		}
	}

	public double getAreaUnit(String area) {	
		if(area.equals("beam") || area.equals("bm"))
			return getImageBeamArea();
		if(area.equals("arcsec**2") || area.equals("arcsec2") || area.equals("arcsec^2") || area.equals("sqarcsec"))
			return Unit.arcsec2;
		else if(area.equals("arcmin**2") || area.equals("arcmin2") || area.equals("arcmin^2") || area.equals("sqarcmin"))
			return Unit.arcmin2;
		else if(area.equals("deg**2") || area.equals("deg2") || area.equals("deg^2") || area.equals("sqdeg"))
			return Unit.deg2;
		else if(area.equals("rad**2") || area.equals("rad2") || area.equals("rad^2") || area.equals("sr"))
			return Unit.sr;
		else if(area.equals("mas") || area.equals("mas2") || area.equals("mas^2") || area.equals("sqmas"))
			return Unit.mas * Unit.mas;
		else if(area.equals("pixel") || area.equals("pix"))
			return getPixelArea();
		else return Double.NaN;
	}


	public void setUnit(String name) {
		unit = new Unit(name, getUnit(name));
	}


	
	@Override
	public void smooth(double FWHM) {
		super.smooth(FWHM);
		setUnit(unit.name);
	}

	@Override
	public void filterAbove(double FWHM, int[][] skip) {
		super.filterAbove(FWHM, skip);
		filterCorrect(instrument.resolution, skip);
	}
	
	@Override
	public void fftFilterAbove(double FWHM, int[][] skip) {
		super.fftFilterAbove(FWHM, skip);
		filterCorrect(instrument.resolution, skip);
	}

	
	@Override
	public int clean(GridImage<CoordinateType> search, double[][] beam, double gain, double replacementFWHM) {
		int components = super.clean(search, beam, gain, replacementFWHM);
		
		// Reset the beam and resolution... 
		instrument.resolution = replacementFWHM;
		
		return components;
	}
	
	
	public void printShortInfo() {
		System.err.println("\n\n  [" + name + "]\n" + super.toString());
	}
	
	@Override
	public String toString() {
		String info = fileName == null ? "\n" : " Image File: " + fileName + ". ->" + "\n\n" + 
			"  [" + name + "]\n" +
			super.toString() + 
			"  Instrument Beam FWHM: " + Util.f2.format(instrument.resolution / Unit.arcsec) + " arcsec." + "\n" +
			"  Applied Smoothing: " + Util.f2.format(smoothFWHM / Unit.arcsec) + " arcsec." + " (includes pixelization)\n" +
			"  Image Resolution (FWHM): " + Util.f2.format(getImageFWHM() / Unit.arcsec) + " arcsec. (includes smoothing)" + "\n";
			
		return info;
	}
	

	
}
