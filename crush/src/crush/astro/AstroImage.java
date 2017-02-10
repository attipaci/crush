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

package crush.astro;


import jnum.Unit;
import jnum.astro.*;
import jnum.data.GridImage2D;
import jnum.data.SphericalGrid;
import jnum.math.SphericalCoordinates;

public class AstroImage extends GridImage2D<SphericalCoordinates> implements Cloneable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5580580559151175614L;

	public AstroImage() {
		setGrid(new SphericalGrid());
		setPreferredGridUnit(Unit.get("arcsec"));
	}

	public AstroImage(int sizeX, int sizeY) {
		super(sizeX, sizeY);
		setGrid(new SphericalGrid());
	}

	public AstroImage(double[][] data) {
		super(data);
		setGrid(new SphericalGrid());
	}

	public AstroImage(double[][] data, int[][] flag) {
		super(data, flag);
		setGrid(new SphericalGrid());
	}
	
	public AstroImage(String fileName) throws Exception {
		super();
		read(fileName);
	}
	
	@SuppressWarnings("unchecked")
	public AstroSystem astroSystem() {
		return new AstroSystem((Class<? extends SphericalCoordinates>) getCoordinateClass());
	}
	
	@Override
	public Object getTableEntry(String name) {
		if(name.equals("system")) return astroSystem().getID();
		else return super.getTableEntry(name);
	}
	

	
}

