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
// Copyright (c) 2007 Attila Kovacs 

package crush.astro;


import crush.Instrument;
import crush.sourcemodel.GridSource;
import util.*;
import util.astro.*;
import util.data.SphericalGrid;


public class AstroMap extends GridSource<SphericalCoordinates> implements SkyCoordinates {
	
	public AstroMap() { 
		setGrid(new SphericalGrid());
	}
	
	public AstroMap(Instrument<?> instrument) { 
		this(); 
		this.instrument = instrument;
	}
	
	public AstroMap(String fileName, Instrument<?> instrument) throws Exception { 
		this(instrument);
		read(fileName);		
	}

	public AstroMap(int i, int j) { 
		super(i, j);
		setGrid(new SphericalGrid());
	}
	
	public void filterCorrect() {
		filterCorrect(instrument.resolution, getSkip(filterBlanking));
	}
	
	public void undoFilterCorrect() {
		undoFilterCorrect(instrument.resolution, getSkip(filterBlanking));
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
	
}





