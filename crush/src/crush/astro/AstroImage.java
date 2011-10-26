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

package crush.astro;

import util.*;
import util.astro.*;
import util.data.GridImage;

public class AstroImage extends GridImage<SphericalCoordinates> implements Cloneable, SkyCoordinates {

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

