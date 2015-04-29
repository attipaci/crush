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

public class SofiaNoddingData extends SofiaHeaderData {
	public double dwellTime = Double.NaN;
	public int cycles = 0;
	public double settlingTime = Double.NaN;
	public double amplitude = Double.NaN;
	public String beamPosition, pattern, style, coordinateSystem;
	public double angle = Double.NaN;
	
	public SofiaNoddingData() {}
	
	public SofiaNoddingData(Header header) {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) {
		dwellTime = header.getDoubleValue("NODTIME", Double.NaN) * Unit.s;
		cycles = header.getIntValue("NODN", UNKNOWN_INT_VALUE);
		settlingTime = header.getDoubleValue("NODSETL", Double.NaN) * Unit.s;
		amplitude = header.getDoubleValue("NODAMP", Double.NaN) * Unit.arcsec;
		beamPosition = getStringValue(header, "NODBEAM");
		pattern = getStringValue(header, "NODPATT");
		style = getStringValue(header, "NODSTYLE");
		coordinateSystem = getStringValue(header, "NODCRSYS");
		angle = header.getDoubleValue("NODANGLE", Double.NaN) * Unit.deg;
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(cycles != UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("NODN", cycles, "Number of nod cycles."));
		if(!Double.isNaN(amplitude)) cursor.add(new HeaderCard("NODAMP", amplitude / Unit.arcsec, "(arcsec) Nod amplitude on sky."));
		if(!Double.isNaN(angle)) cursor.add(new HeaderCard("NODANGLE", angle / Unit.deg, "(deg) Nod angle on sky."));
		if(!Double.isNaN(dwellTime)) cursor.add(new HeaderCard("NODTIME", dwellTime / Unit.s, "(s) Total dwell time per nod position."));
		if(!Double.isNaN(settlingTime)) cursor.add(new HeaderCard("NODSETL", settlingTime / Unit.s, "(s) Nod settling time."));
		if(pattern != null) cursor.add(new HeaderCard("NODPATT", pattern, "Pointing sequence for one nod cycle."));
		if(style != null) cursor.add(new HeaderCard("NODSTYLE", style, "Nodding style."));
		if(coordinateSystem != null) cursor.add(new HeaderCard("NODCRSYS", coordinateSystem, "Nodding coordinate system."));
		if(beamPosition != null) cursor.add(new HeaderCard("NODBEAM", beamPosition, "Nod beam position."));
	}

}
