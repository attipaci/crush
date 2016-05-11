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

import jnum.Unit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaEnvironmentData extends SofiaData {
	public BracketedValues pwv = new BracketedValues();
	public double pwvLOS = Double.NaN;
	
	public double ambientT = Double.NaN;
	public double primaryT1 = Double.NaN, primaryT2 = Double.NaN, primaryT3 = Double.NaN, secondaryT = Double.NaN;

	public SofiaEnvironmentData() {}
	
	public SofiaEnvironmentData(SofiaHeader header) {
		this();
		parseHeader(header);
	}

	public void parseHeader(SofiaHeader header) {
		pwv.start = header.getDouble("WVZ_STA", Double.NaN) * Unit.um;
		pwv.end = header.getDouble("WVZ_END", Double.NaN) * Unit.um;
		pwvLOS = header.getDouble("WVTALOS", Double.NaN) * Unit.um;		// not in 3.0
		ambientT = header.getDouble("TEMP_OUT", Double.NaN) * Unit.K;
		primaryT1 = header.getDouble("TEMPPRI1", Double.NaN) * Unit.K;
		primaryT2 = header.getDouble("TEMPPRI2", Double.NaN) * Unit.K;
		primaryT3 = header.getDouble("TEMPPRI3", Double.NaN) * Unit.K;
		secondaryT = header.getDouble("TEMPSEC1", Double.NaN) * Unit.K;
	}

	@Override
	public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Environment Data ------>", false));
		if(!Double.isNaN(pwv.start)) cursor.add(new HeaderCard("WVZ_STA", pwv.start / Unit.um, "(um) Precipitable Water Vapor at start."));
		if(!Double.isNaN(pwv.end)) cursor.add(new HeaderCard("WVZ_END", pwv.start / Unit.um, "(um) Precipitable Water Vapor at start."));
		if(!Double.isNaN(pwvLOS)) cursor.add(new HeaderCard("WVTALOS", pwvLOS / Unit.um, "(um) PWV at TA line-of-sight."));
		if(!Double.isNaN(ambientT)) cursor.add(new HeaderCard("TEMP_OUT", ambientT, "(C) Ambient air temperature."));
		if(!Double.isNaN(primaryT1)) cursor.add(new HeaderCard("TEMPPRI1", primaryT1, "(C) Primary mirror temperature #1."));
		if(!Double.isNaN(primaryT2)) cursor.add(new HeaderCard("TEMPPRI2", primaryT2, "(C) Primary mirror temperature #2."));
		if(!Double.isNaN(primaryT3)) cursor.add(new HeaderCard("TEMPPRI3", primaryT3, "(C) Primary mirror temperature #3."));
		if(!Double.isNaN(secondaryT)) cursor.add(new HeaderCard("TEMPSEC1", secondaryT, "(C) Secondary mirror temperature."));
	}

}
