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

public class SofiaScanningData extends SofiaHeaderData {
	public BracketedValues RA = new BracketedValues(), DEC = new BracketedValues();
	public double speed = Double.NaN, angle = Double.NaN;
		
	public SofiaScanningData() {}
	
	public SofiaScanningData(Header header) {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) {
		RA.start = getHMSTime(header, "SCNRA0") * Unit.timeAngle;
		RA.end = getHMSTime(header, "SCNRAF") * Unit.timeAngle;
		DEC.start = getDMSAngle(header, "SCNDEC0");
		DEC.end = getDMSAngle(header, "SCNDECF");
		speed = header.getDoubleValue("SCNRATE", Double.NaN) * Unit.arcsec / Unit.s;
		angle = header.getDoubleValue("SCNDIR", Double.NaN) * Unit.deg;
	}

	@Override
	public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Scanning Data ------>", false));
		if(!Double.isNaN(RA.start)) cursor.add(new HeaderCard("SCNRA0", RA.start / Unit.hourAngle, "(hour) Initial scan RA."));
		if(!Double.isNaN(DEC.start)) cursor.add(new HeaderCard("SCNDEC0", DEC.start / Unit.deg, "(deg) Initial scan DEC."));
		if(!Double.isNaN(RA.end)) cursor.add(new HeaderCard("SCNRAF", RA.start / Unit.hourAngle, "(hour) Final scan RA."));
		if(!Double.isNaN(DEC.end)) cursor.add(new HeaderCard("SCNDECF", DEC.start / Unit.deg, "Final scan DEC."));
		if(!Double.isNaN(speed)) cursor.add(new HeaderCard("SCNRATE", speed / (Unit.arcsec / Unit.s), "(arcsec/s) Commanded slew rate on sky."));
		if(!Double.isNaN(angle)) cursor.add(new HeaderCard("SCNDIR", angle / Unit.deg, "(deg) Scan direction on sky."));	
	}

}
