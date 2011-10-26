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

package util;

import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class DefaultProjection2D extends Projection2D<CoordinatePair> {

	@Override
	public CoordinatePair getCoordinateInstance() {
		return new CoordinatePair();
	}

	@Override
	public void project(CoordinatePair coords, CoordinatePair toProjected) {
		toProjected.copy(coords);
	}

	@Override
	public void deproject(CoordinatePair projected, CoordinatePair toCoords) {
		toCoords.copy(projected);
	}

	@Override
	public String getFitsID() {
		return null;
	}

	@Override
	public String getFullName() {
		return "Cartesian";
	}

	@Override
	public void parse(Header header, String alt) {
		// TODO Auto-generated method stub
	}

	@Override
	public void edit(Cursor cursor, String alt) throws HeaderCardException {
		// TODO Auto-generated method stub
		
	}

}
