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

import kovacs.util.Unit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaDitheringData extends SofiaHeaderData {
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
		patternShape = getStringValue(header, "DTHPATT");
		positions = header.getIntValue("DTHNPOS", UNKNOWN_INT_VALUE);
		index = header.getIntValue("DTHINDEX", UNKNOWN_INT_VALUE);
		spacing = header.getDoubleValue("DTHOFFS", Double.NaN) * Unit.arcsec;
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(patternShape != null) cursor.add(new HeaderCard("DTHPATT", patternShape, "Approximate shape of dither pattern."));
		if(positions != UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("DTHNPOS", positions, "Number of dither positions."));
		if(index != UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("DTHINDEX", index, "Dither position index."));
		if(!Double.isNaN(spacing)) cursor.add(new HeaderCard("DTHOFFS", spacing / Unit.arcsec, "(arcsec) Dither spacing."));
	}

}
