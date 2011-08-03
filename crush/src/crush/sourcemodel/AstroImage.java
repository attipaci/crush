/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2009 Attila Kovacs 

package crush.sourcemodel;

import nom.tam.fits.*;
import nom.tam.util.*;

import util.*;
import util.astro.EclipticCoordinates;
import util.astro.EquatorialCoordinates;
import util.astro.GalacticCoordinates;
import util.astro.HorizontalCoordinates;
import util.astro.SuperGalacticCoordinates;
import util.data.GridImage;

import java.io.*;

import crush.*;


public class AstroImage extends GridImage<SphericalGrid> implements Cloneable {
	public SphericalGrid grid = new SphericalGrid();

	public Instrument<?> instrument;

	public Header header;
	public String sourceName;

	public AstroImage() {
	}

	public AstroImage(int sizeX, int sizeY) {
		super(sizeX, sizeY);
	}

	public AstroImage(double[][] data) {
		super(data);
	}

	public AstroImage(double[][] data, int[][] flag) {
		super(data, flag);
	}

	public SphericalProjection getProjection() {
		return grid.getProjection();
	}

	public SphericalCoordinates getReference() {
		return grid.getReference();
	}

	public boolean isHorizontal() {
		return getReference() instanceof HorizontalCoordinates;
	}

	public boolean isEquatorial() {
		return getReference() instanceof EquatorialCoordinates;
	}

	public boolean isEcliptic() {
		return getReference() instanceof EclipticCoordinates;
	}

	public boolean isGalactic() {
		return getReference() instanceof GalacticCoordinates;
	}

	public boolean isSuperGalactic() {
		return getReference() instanceof SuperGalacticCoordinates;
	}


	@Override
	public double getImageFWHM() {
		return Math.hypot(instrument.resolution, smoothFWHM);
	}

	public double getInstrumentBeamArea() {
		double size = instrument.resolution * fwhm2size;
		return size*size;
	}

	public void setProjection(SphericalProjection projection) {
		grid.projection = projection;
	}

	@Override
	public GridImage<SphericalGrid> getRegrid(final SphericalGrid toGrid) throws IllegalStateException {	
		GridImage<SphericalGrid> regrid = super.getRegrid(toGrid);
		if(regrid instanceof AstroImage) ((AstroImage) regrid).setUnit(unit.name);
		return regrid;
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException, FitsException, IOException {
		super.editHeader(cursor);
		cursor.add(new HeaderCard("OBJECT", sourceName, "Source name as it appear in the raw data."));	
		if(instrument != null) {
			instrument.editImageHeader(cursor);
			if(instrument.options != null) instrument.options.editHeader(cursor);
		}
	}

	@Override
	public void parseHeader(Header header) throws Exception {	
		this.header = header;

		correctingFWHM = Double.NaN;
		extFilterFWHM = Double.NaN;

		super.parseHeader(header);
		parseBasicHeader(header);
		parseCrushHeader(header);

		setUnit(header.getStringValue("BUNIT"));
	}

	protected void parseCrushHeader(Header header) throws HeaderCardException {
		correctingFWHM = header.getDoubleValue("CORRECTN", Double.NaN) * Unit.arcsec;
		creatorVersion = header.getStringValue("CRUSHVER");

		// get the map resolution
		smoothFWHM = header.getDoubleValue("SMOOTH") * Unit.arcsec;
		if(smoothFWHM < Math.sqrt(grid.getPixelArea()) / fwhm2size) smoothFWHM = Math.sqrt(grid.getPixelArea()) / fwhm2size;
		extFilterFWHM = header.getDoubleValue("EXTFLTR", Double.NaN) * Unit.arcsec;		
	}


	private void parseBasicHeader(Header header) throws HeaderCardException, InstantiationException, IllegalAccessException {
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

		instrument.parseHeader(header);

		// get the beam and calculate derived quantities
		if(header.containsKey("BEAM")) 
			instrument.resolution = header.getDoubleValue("BEAM", instrument.resolution / Unit.arcsec) * Unit.arcsec;
		else if(header.containsKey("BMAJ"))
			instrument.resolution =  header.getDoubleValue("BMAJ", instrument.resolution / Unit.deg) * Unit.deg;
		else 
			instrument.resolution = 3.0 * Math.sqrt(grid.getPixelArea());
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


	public void setKey(String key, String value) throws HeaderCardException {
		String comment = header.containsKey(key) ? header.findCard(key).getComment() : "Set bu user.";

		// Try add as boolean, int or double -- fall back to String...
		try{ header.addValue(key, Util.parseBoolean(value), comment); }
		catch(NumberFormatException e1) { 
			try{ header.addValue(key, Integer.parseInt(value), comment); }
			catch(NumberFormatException e2) {
				try{ header.addValue(key, Double.parseDouble(value), comment); }
				catch(NumberFormatException e3) { header.addValue(key, value, comment); }
			}
		}
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
	public int clean(GridImage<SphericalGrid> search, double[][] beam, double gain, double replacementFWHM) {
		int components = super.clean(search, beam, gain, replacementFWHM);
		
		// Reset the beam and resolution... 
		instrument.resolution = replacementFWHM;
		
		return components;
	}
	
	public void flag(Region region) { flag(region, 1); }

	public void flag(Region region, int pattern) {
		final Bounds bounds = region.getBounds(this);
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(region.isInside(grid, i, j)) flag[i][j] |= pattern;
	}

	public void unflag(Region region, int pattern) {
		final Bounds bounds = region.getBounds(this);
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(region.isInside(grid, i, j)) flag[i][j] &= ~pattern;
	}



	public double getIntegral(Region region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j)) sum += data[i][j];	
		return sum;			
	}

	public double getLevel(Region region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		int n = 0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j)) {
				sum += data[i][j];
				n++;
			}
		return sum / n;			
	}

	public double getRMS(Region region) {
		final Bounds bounds = region.getBounds(this);
		double var = 0.0;
		int n = 0;
		double level = getLevel(region);

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j))  {
				double value = data[i][j] - level;
				var += value * value;
				n++;
			}
		var /= (n-1);

		return Math.sqrt(var);
	}


	@Override
	public SphericalGrid getGrid() {
		return grid;
	}

	@Override
	public void setGrid(SphericalGrid grid) {
		this.grid = grid;
		double fwhm = Math.sqrt(grid.getPixelArea()) / GridImage.fwhm2size;
		if(smoothFWHM < fwhm) smoothFWHM = fwhm;
	}
	
	public void printShortInfo() {
		System.err.println("\n\n  [" + sourceName + "]\n" + super.toString());
	}
	
	@Override
	public String toString() {
		String info = fileName == null ? "\n" : " Image File: " + fileName + ". ->" + "\n\n" + 
			"  [" + sourceName + "]\n" +
			super.toString() + 
			"  Instrument Beam FWHM: " + Util.f2.format(instrument.resolution / Unit.arcsec) + " arcsec." + "\n" +
			"  Applied Smoothing: " + Util.f2.format(smoothFWHM / Unit.arcsec) + " arcsec." + " (includes pixelization)\n" +
			"  Image Resolution (FWHM): " + Util.f2.format(getImageFWHM() / Unit.arcsec) + " arcsec. (includes smoothing)" + "\n";
			
		return info;
	}
	
	
}

