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
package crush.sourcemodel;

import java.io.File;
import java.io.IOException;
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
import jnum.Configurator;
import jnum.Unit;
import jnum.Util;
import jnum.data.Data2D;
import jnum.data.GaussianPSF;
import jnum.data.Grid2D;
import jnum.data.GridImage2D;
import jnum.data.GridMap2D;
import jnum.math.Coordinate2D;
import jnum.util.HashCode;


public abstract class GridSource<CoordinateType extends Coordinate2D> extends GridMap2D<CoordinateType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7928156781161894347L;
	private Instrument<?> instrument;
	public Vector<Scan<?, ?>> scans = new Vector<Scan<?, ?>>();
	
	public int generation = 0;
	public double integrationTime = 0.0;	
	
	private Unit jansky = new Jansky();
	private Unit kelvin = new Kelvin();
	
	public GridSource() {}
	

	public GridSource(int sizeX, int sizeY) {
		super(sizeX, sizeY);
	}
	
	@Override
	public int hashCode() { 
		int hash = super.hashCode() ^ generation ^ HashCode.get(integrationTime);
		if(instrument != null) hash ^= instrument.hashCode();
		if(scans != null) hash ^= HashCode.sampleFrom(scans); 
		return hash;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof GridSource)) return false;
		if(!super.equals(o)) return false;
		
		GridSource<?> source = (GridSource<?>) o;
		if(generation != source.generation) return false;
		if(integrationTime != source.integrationTime) return false;
		if(!Util.equals(instrument, source.instrument)) return false;
		if(!Util.equals(scans, source.scans)) return false;
		return true;
	}
	
	@Override
	public void reset(boolean clearContent) {
		super.reset(clearContent);
		integrationTime = 0.0;
	}
	
	public Instrument<?> getInstrument() { return instrument; }
	
	public void setInstrument(Instrument<?> instrument) {
		this.instrument = instrument;
		
	}
	
	@Override
	public synchronized void addWeightedDirect(final Data2D data, final double w) {
		super.addWeightedDirect(data, w);
		if(data instanceof GridSource) integrationTime += ((GridSource<?>) data).integrationTime;
	}

	public double getInstrumentBeamArea() {
		double size = instrument.getResolution() * fwhm2size;
		return size*size;
	}
	
	// Normalize assuming weighting was sigma weighting
	public void normalize() {
		new Task<Void>() {
			@Override
			protected void process(int i, int j) { normalize(i, j); }
		}.process();
	}

	public void normalize(final int i, final int j) { 
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
			for(Scan<?,?> scan : scans) fits.addHDU(scan.getSummaryHDU(instrument.getOptions()));
		
		return fits;
	}
	
	
	@Override
	public void write() throws HeaderCardException, FitsException, IOException {
		if(fileName == null) fileName = CRUSH.workPath + File.separator + getName() + ".fits";  
		super.write();
	}
	
	@Override
	public GridImage2D<CoordinateType> getRegrid(final Grid2D<CoordinateType> toGrid) throws IllegalStateException {	
		GridSource<CoordinateType> regrid = (GridSource<CoordinateType>) super.getRegrid(toGrid);
		return regrid;
	}
	
	@Override
	public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException, FitsException, IOException {
		super.editHeader(header, cursor);
		
		cursor.add(new HeaderCard("SCANS", scans.size(), "The number of scans in this composite image."));
		cursor.add(new HeaderCard("INTEGRTN", integrationTime / Unit.s, "The total integration time in seconds."));
		
		cursor.add(new HeaderCard("V2JY", instrument.janskyPerBeam(), "1 Jy/beam in instrument data units."));	
		
		if(instrument != null) {
			instrument.editImageHeader(scans, header, cursor);
			if(instrument.getOptions() != null) instrument.getOptions().editHeader(cursor);
			
		}
		
		CRUSH.addHistory(cursor);
		if(instrument != null) instrument.addHistory(cursor, scans);
		
	}

	@Override
	public void parseHeader(Header header, String alt) throws Exception {		
		super.parseHeader(header, alt);
		
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
					if(header.containsKey("TELESCOP")) ((GenericInstrument) instrument).setTelescopeName(header.getStringValue("TELESCOP"));
				}
			}
			else {
				instrument = new GenericInstrument("unknown");
				if(header.containsKey("TELESCOP")) ((GenericInstrument) instrument).setTelescopeName(header.getStringValue("TELESCOP"));
			}
		}
		
		if(instrument.getOptions() == null) instrument.setOptions(new Configurator());

		instrument.parseImageHeader(header);

		// get the beam and calculate derived quantities
		// TODO revise... (use GaussianPSF...)
		if(header.containsKey("BEAM")) 
			instrument.setResolution(header.getDoubleValue("BEAM", instrument.getResolution() / Unit.arcsec) * Unit.arcsec);
		else if(header.containsKey("BMAJ"))
			instrument.setResolution(header.getDoubleValue("BMAJ", instrument.getResolution() / Unit.deg) * Unit.deg);
		else 
			instrument.setResolution(3.0 * Math.sqrt(getGrid().getPixelArea()));
	}

	
	@Override
	public void addBaseUnits() {
		super.addBaseUnits();
		addBaseUnit(jansky, "Jy, jansky");
		addBaseUnit(kelvin, "K, kelvin");
	}

	
	@Override
	public void fastSmooth(double[][] beam, int stepX, int stepY) {
		super.fastSmooth(beam, stepX, stepY);
		setUnit(getUnit().name());
	}

	@Override
	public void filterAbove(double FWHM, int[][] skip) {
		super.filterAbove(FWHM, skip);
	}
	
	@Override
	public void fftFilterAbove(double FWHM, int[][] skip) {
		super.fftFilterAbove(FWHM, skip);
	}

	
	@Override
	// TODO....
	public int clean(GridImage2D<CoordinateType> search, double[][] beam, double gain, GaussianPSF replacementBeam) {
		int components = super.clean(search, beam, gain, replacementBeam);
		
		// Reset the beam and resolution... 
		// TODO elliptical beam shape...
		instrument.setResolution(replacementBeam.getCircularEquivalentFWHM());
		
		return components;
	}
	
	
	public void printShortInfo() {
		System.err.println("\n\n  [" + getName() + "]\n" + super.toString());
	}
	
	@Override
	public String toString() {	
		String info = fileName == null ? "\n" : " Image File: " + fileName + ". ->" + "\n\n" + 
			"  [" + getName() + "]\n" +
			super.toString();
		
		return info;
	}
	
		
	private class Jansky extends Unit {
		/**
		 * 
		 */
		private static final long serialVersionUID = -2228932903204574146L;

		private Jansky() { super("Jy", Double.NaN); }
		
		@Override
		public double value() { return instrument.janskyPerBeam() * getInstrumentBeamArea(); }
	}
	
	private class Kelvin extends Unit {
		/**
		 * 
		 */
		private static final long serialVersionUID = -847015844453378556L;
		

		private Kelvin() { super("K", Double.NaN); }
		
		@Override
		public double value() { return instrument.kelvin(); }	
	}
	
}
