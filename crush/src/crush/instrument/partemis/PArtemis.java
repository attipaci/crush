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
// Copyright (c) 2010 Attila Kovacs 

package crush.instrument.partemis;

import crush.*;
import crush.array.*;
import crush.telescope.apex.*;
import jnum.Unit;
import jnum.math.Vector2D;

import java.util.*;

public class PArtemis extends APEXCamera<PArtemisPixel> implements GridIndexed {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7163408261887093751L;

	public static int rows = 16;
	public static int cols = 16;
	
	public Vector2D pixelSize = PArtemisPixel.defaultSize;
	
	public PArtemis() {
		super("p-artemis", 256);
		setResolution(9.6 * Unit.arcsec);
	}
	
	@Override
	public PArtemis copy() {
		PArtemis copy = (PArtemis) super.copy();
		if(pixelSize != null) copy.pixelSize = pixelSize.copy();
		return copy;
	}
	
	@Override
    protected void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("rows", PArtemisPixel.class.getField("row"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }	
		
		try { addDivision(getDivision("cols", PArtemisPixel.class.getField("col"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }	
	}
	
	@Override
    protected void initModalities() {
		super.initModalities();
		
		try { addModality(new CorrelatedModality("rows", "r", divisions.get("rows"), PArtemisPixel.class.getField("rowGain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		try { addModality(new CorrelatedModality("cols", "c", divisions.get("cols"), PArtemisPixel.class.getField("colGain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		modalities.get("rows").setGainFlag(PArtemisPixel.FLAG_ROW);
		modalities.get("cols").setGainFlag(PArtemisPixel.FLAG_COL);
		//modalities.get("mux").setSkipFlags(~(PArtemisPixel.FLAG_RESISTOR | PArtemisPixel.FLAG_SENSITIVITY));
	}
	
	@Override
    protected void loadChannelData() {
		Vector2D pixelSize = PArtemisPixel.defaultSize;
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
		}

		setPlateScale(pixelSize);

		if(hasOption("rotation")) {
			double angle = option("rotation").getDouble() * Unit.deg;
			for(PArtemisPixel pixel : this) pixel.position.rotate(angle);
		}
		
		super.loadChannelData();
	}
	
	public void setPlateScale(Vector2D size) {
		// Make all pixels the same size. Also calculate their positions...
		pixelSize = size;
		for(PArtemisPixel pixel : this) pixel.calcPosition();
		
		// Set the pointing center...
		setReferencePosition(referencePixel.getPosition());
	}
	

	@Override
	public void flagInvalidPositions() {
		for(SingleColorPixel pixel : this) if(pixel.position.length() > 2.0 * Unit.arcmin) pixel.flag(Channel.FLAG_BLIND);
	}

	@Override
	public PArtemisPixel getChannelInstance(int backendIndex) {
		return new PArtemisPixel(this, backendIndex);
	}

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
	public Vector2D getPixelSize() {
		return pixelSize;
	}
	
	
}
