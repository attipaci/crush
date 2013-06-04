/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package kovacs.util.data;

import kovacs.util.SphericalCoordinates;
import kovacs.util.SphericalProjection;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;

public class SphericalGrid extends Grid2D<SphericalCoordinates> {

	@Override
	public boolean isReverseX() { return getReference().isReverseLongitude(); }
	
	@Override
	public boolean isReverseY() { return getReference().isReverseLatitude(); }
	
	@Override
	public void parseProjection(Header header) throws HeaderCardException {
		String type = header.getStringValue("CTYPE1" + getFITSAlt());
	
		try { setProjection(SphericalProjection.forName(type.substring(5, 8))); }
		catch(Exception e) { System.err.println("ERROR! Unknown projection " + type.substring(5, 8)); }
	}
	
	@Override
	public SphericalCoordinates getCoordinateInstanceFor(String type) throws InstantiationException, IllegalAccessException {
		Class<? extends SphericalCoordinates> coordClass = SphericalCoordinates.getFITSClass(type);
		return coordClass.newInstance();
	}
	
}
