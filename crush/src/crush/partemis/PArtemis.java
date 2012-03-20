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
// Copyright (c) 2010 Attila Kovacs 

package crush.partemis;

import crush.*;
import crush.array.*;
import util.*;
import crush.apex.*;

import java.util.*;

public class PArtemis extends APEXArray<PArtemisPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7163408261887093751L;

	public PArtemis() {
		super("p-artemis", 256);
		resolution = 9.6 * Unit.arcsec;
	}
	
	@Override
	public void addDivisions() {
		super.addDivisions();
		
		try { addDivision(getDivision("rows", PArtemisPixel.class.getField("row"), nonDetectorFlags)); }
		catch(Exception e) { e.printStackTrace(); }	
		
		try { addDivision(getDivision("cols", PArtemisPixel.class.getField("col"), nonDetectorFlags)); }
		catch(Exception e) { e.printStackTrace(); }	
	}
	
	@Override
	public void addModalities() {
		super.addModalities();
		
		try { addModality(new CorrelatedModality("rows", "r", divisions.get("rows"), PArtemisPixel.class.getField("rowGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("cols", "c", divisions.get("cols"), PArtemisPixel.class.getField("colGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		modalities.get("rows").setGainFlag(PArtemisPixel.FLAG_ROW);
		modalities.get("cols").setGainFlag(PArtemisPixel.FLAG_COL);
		//modalities.get("mux").setSkipFlags(~(PArtemisPixel.FLAG_RESISTOR | PArtemisPixel.FLAG_SENSITIVITY));
	}
	
	@Override
	public void loadChannelData() {
		Vector2D pixelSize = PArtemisPixel.defaultSize;
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.getX());
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
		for(PArtemisPixel pixel : this) {
			pixel.size = size;		
			pixel.calcPosition();
		}
			
		// Set the pointing center...
		setReferencePosition(referencePixel.getPosition());
	}
	

	@Override
	public void flagInvalidPositions() {
		for(SimplePixel pixel : this) if(pixel.position.length() > 2.0 * Unit.arcmin) pixel.flag(Channel.FLAG_BLIND);
	}

	@Override
	public PArtemisPixel getChannelInstance(int backendIndex) {
		return new PArtemisPixel(this, backendIndex);
	}
	
}
