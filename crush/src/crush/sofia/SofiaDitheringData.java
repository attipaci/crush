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


import kovacs.math.Vector2D;
import kovacs.util.Unit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaDitheringData extends SofiaHeaderData {
	public String coordinateSystem;
	public Vector2D offset;
	public String patternShape;
	public int positions = UNKNOWN_INT_VALUE;
	public int index = UNKNOWN_INT_VALUE;
	public double spacing = Double.NaN;
	
	public SofiaDitheringData() {}
	
	public SofiaDitheringData(Header header) {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) {
		
		coordinateSystem = getStringValue(header, "DTHCRSYS");		// new in 3.0
		
		if(header.containsKey("DTHXOFF") || header.containsKey("DTHYOFF")) {
			offset = new Vector2D(header.getDoubleValue("DTHXOFF", 0.0), header.getDoubleValue("DTHYOFF", 0.0));
			offset.scale(Unit.arcsec);
		}
		else offset = null;		// new in 3.0
		
		patternShape = getStringValue(header, "DTHPATT");
		positions = header.getIntValue("DTHNPOS", UNKNOWN_INT_VALUE);
		index = header.getIntValue("DTHINDEX", UNKNOWN_INT_VALUE);
		spacing = header.getDoubleValue("DTHOFFS", Double.NaN) * Unit.arcsec;
	}

	public void updateRequiredKeys(Header header) throws HeaderCardException {
		header.addValue("DTHINDEX", index, "Dither position index.");
	}
	
	@Override
	public void editHeader(Header header, Cursor cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Dithering Data ------>", false));
		if(coordinateSystem != null) cursor.add(new HeaderCard("DTHCRSYS", coordinateSystem, "Dither coordinate system."));
		if(offset != null) {
			cursor.add(new HeaderCard("DTHXOFF", offset.x() / Unit.arcsec, "(arcsec) Dither X offset."));
			cursor.add(new HeaderCard("DTHYOFF", offset.y() / Unit.arcsec, "(arcsec) Dither Y offset."));
		}
		if(patternShape != null) cursor.add(new HeaderCard("DTHPATT", patternShape, "Approximate shape of dither pattern."));
		if(positions != UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("DTHNPOS", positions, "Number of dither positions."));
		cursor.add(new HeaderCard("DTHINDEX", index, "Dither position index."));
		if(!Double.isNaN(spacing)) cursor.add(new HeaderCard("DTHOFFS", spacing / Unit.arcsec, "(arcsec) Dither spacing."));
	}

}
