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

package crush.sofia;

import kovacs.astro.EclipticCoordinates;
import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.GalacticCoordinates;
import kovacs.math.SphericalCoordinates;
import kovacs.math.Vector2D;
import kovacs.util.Unit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaMappingData extends SofiaHeaderData {
	public String coordinateSystem;
	public String pattern;
	public int sizeX = 0, sizeY = 0;
	public Vector2D step = new Vector2D(Double.NaN, Double.NaN);
	
	public SofiaMappingData() {}
	
	public SofiaMappingData(Header header) {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) {
		coordinateSystem = getStringValue(header, "MAPCRSYS");
		pattern = getStringValue(header, "MAPPATT");
		sizeX = header.getIntValue("MAPNXPOS", UNKNOWN_INT_VALUE);
		sizeY = header.getIntValue("MAPNYPOS", UNKNOWN_INT_VALUE);
		step.setX(header.getDoubleValue("MAPINTX", Double.NaN) * Unit.arcmin);
		step.setY(header.getDoubleValue("MAPINTY", Double.NaN) * Unit.arcmin);
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Mapping Data ------>", false));
		if(coordinateSystem != null) cursor.add(new HeaderCard("MAPCRSYS", coordinateSystem, "Mapping coordinate system."));
		if(pattern != null) cursor.add(new HeaderCard("MAPPATT", pattern, "Mapping pattern."));
		if(sizeX != UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("MAPNXPOS", sizeX, "Number of map positions in X"));
		if(sizeY != UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("MAPNYPOS", sizeY, "Number of map positions in Y"));
		if(Double.isNaN(step.x())) cursor.add(new HeaderCard("MAPINTX", step.x() / Unit.arcmin, "(arcmin) Map step interval in X"));
		if(Double.isNaN(step.y())) cursor.add(new HeaderCard("MAPINTY", step.y() / Unit.arcmin, "(arcmin) Map step interval in Y"));
	}

	public Class<? extends SphericalCoordinates> getBasis() {
		if(coordinateSystem == null) return null;
		else if(coordinateSystem.equalsIgnoreCase("EQUATORIAL")) return EquatorialCoordinates.class;
		else if(coordinateSystem.equalsIgnoreCase("ECLIPTIC")) return EclipticCoordinates.class;
		else if(coordinateSystem.equalsIgnoreCase("GALACTIC")) return GalacticCoordinates.class;
		else return null;	
	}
	
}
 