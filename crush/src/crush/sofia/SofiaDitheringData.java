/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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


import jnum.Unit;
import jnum.math.Vector2D;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaDitheringData extends SofiaData {
	public String coordinateSystem;
	public Vector2D offset;
	public String patternShape;
	public int positions = SofiaHeader.UNKNOWN_INT_VALUE;
	public int index = SofiaHeader.UNKNOWN_INT_VALUE;
	public double spacing = Double.NaN;
	
	public SofiaDitheringData() {}
	
	public SofiaDitheringData(SofiaHeader header) {
		this();
		parseHeader(header);
	}
	
	public void parseHeader(SofiaHeader header) {
		
		coordinateSystem = header.getString("DTHCRSYS");		// new in 3.0
		
		if(header.containsKey("DTHXOFF") || header.containsKey("DTHYOFF")) {
			offset = new Vector2D(header.getDouble("DTHXOFF", 0.0), header.getDouble("DTHYOFF", 0.0));
			offset.scale(Unit.arcsec);
		}
		else offset = null;		// new in 3.0
		
		patternShape = header.getString("DTHPATT");
		positions = header.getInt("DTHNPOS");
		index = header.getInt("DTHINDEX");
		spacing = header.getDouble("DTHOFFS", Double.NaN) * Unit.arcsec;
	}

	@Override
	public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Dithering Data ------>", false));
		if(coordinateSystem != null) cursor.add(new HeaderCard("DTHCRSYS", coordinateSystem, "Dither coordinate system."));
		if(offset != null) {
			cursor.add(new HeaderCard("DTHXOFF", offset.x() / Unit.arcsec, "(arcsec) Dither X offset."));
			cursor.add(new HeaderCard("DTHYOFF", offset.y() / Unit.arcsec, "(arcsec) Dither Y offset."));
		}
		if(patternShape != null) cursor.add(new HeaderCard("DTHPATT", patternShape, "Approximate shape of dither pattern."));
		if(positions != SofiaHeader.UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("DTHNPOS", positions, "Number of dither positions."));
		cursor.add(new HeaderCard("DTHINDEX", index, "Dither position index."));
		if(!Double.isNaN(spacing)) cursor.add(new HeaderCard("DTHOFFS", spacing / Unit.arcsec, "(arcsec) Dither spacing."));
	}

}
