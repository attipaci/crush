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
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaEnvironmentData extends SofiaHeaderData {
	public ScanBounds pwv = new ScanBounds();
	
	public float ambientT = Float.NaN;
	public float primaryT1 = Float.NaN, primaryT2 = Float.NaN, primaryT3 = Float.NaN, secondaryT = Float.NaN;

	public SofiaEnvironmentData() {}
	
	public SofiaEnvironmentData(Header header) throws FitsException, HeaderCardException {
		this();
		parseHeader(header);
	}

	@Override
	public void parseHeader(Header header) throws FitsException, HeaderCardException {
		pwv.start = header.getDoubleValue("WVZ_STA", Double.NaN) * Unit.um;
		pwv.end = header.getDoubleValue("WVZ_END", Double.NaN) * Unit.um;
		ambientT = header.getFloatValue("TEMP_OUT", Float.NaN);
		primaryT1 = header.getFloatValue("TEMPPRI1", Float.NaN);
		primaryT2 = header.getFloatValue("TEMPPRI2", Float.NaN);
		primaryT3 = header.getFloatValue("TEMPPRI3", Float.NaN);
		secondaryT = header.getFloatValue("TEMPSEC1", Float.NaN);
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(!Double.isNaN(pwv.start)) cursor.add(new HeaderCard("WVZ_STA", pwv.start / Unit.um, "(um) Precipitable Water Vapor at start."));
		if(!Double.isNaN(pwv.end)) cursor.add(new HeaderCard("WVZ_END", pwv.start / Unit.um, "(um) Precipitable Water Vapor at start."));
		if(!Float.isNaN(ambientT)) cursor.add(new HeaderCard("TEMP_OUT", ambientT, "(C) Ambient air temperature."));
		if(!Float.isNaN(primaryT1)) cursor.add(new HeaderCard("TEMPPRI1", primaryT1, "(C) Primary mirror temperature #1."));
		if(!Float.isNaN(primaryT2)) cursor.add(new HeaderCard("TEMPPRI2", primaryT2, "(C) Primary mirror temperature #2."));
		if(!Float.isNaN(primaryT3)) cursor.add(new HeaderCard("TEMPPRI3", primaryT3, "(C) Primary mirror temperature #3."));
		if(!Float.isNaN(secondaryT)) cursor.add(new HeaderCard("TEMPSEC1", secondaryT, "(C) Secondary mirror temperature."));
	}

}
