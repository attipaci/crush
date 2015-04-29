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
 ******************************************************************************/package crush.sofia;

import kovacs.util.Unit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaChopperData extends SofiaHeaderData {
	public double frequency = Double.NaN;
	public String profileType;
	public String symmetryType;
	public double amplitude = Double.NaN, amplitude2 = Double.NaN;
	public String coordinateSystem;
	public double angle = Double.NaN;
	public double tip = Double.NaN;
	public double tilt = Double.NaN;
	public int phaseMillis = UNKNOWN_INT_VALUE;
	
	public SofiaChopperData() {}
	
	public SofiaChopperData(Header header) {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) {
		frequency = header.getDoubleValue("CHPFREQ", Double.NaN) * Unit.Hz;
		profileType = getStringValue(header, "CHPPROF");
		symmetryType = getStringValue(header, "CHPSYM");
		amplitude = header.getDoubleValue("CHPAMP1", Double.NaN) * Unit.arcsec;
		amplitude2 = header.getDoubleValue("CHPAMP2", Double.NaN) * Unit.arcsec;
		coordinateSystem = getStringValue(header, "CHPCRSYS");
		angle = header.getDoubleValue("CHPANGLE", Double.NaN) * Unit.deg;
		tip = header.getDoubleValue("CHPTIP", Double.NaN) * Unit.arcsec;
		tilt = header.getDoubleValue("CHPTILT", Double.NaN) * Unit.arcsec;
		phaseMillis = header.getIntValue("CHPPHASE", UNKNOWN_INT_VALUE);
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(!Double.isNaN(frequency)) cursor.add(new HeaderCard("CHPFREQ", frequency / Unit.Hz, "(Hz) Chop frequency."));
		if(!Double.isNaN(amplitude)) cursor.add(new HeaderCard("CHPAMP1", amplitude / Unit.arcsec, "(arcsec) Chop amplitude on sky."));
		if(!Double.isNaN(amplitude2)) cursor.add(new HeaderCard("CHPAMP2", amplitude2 / Unit.arcsec, "(arcsec) Second chop amplitude on sky."));
		if(!Double.isNaN(angle)) cursor.add(new HeaderCard("CHPANGLE", angle / Unit.deg, "(deg) Chop angle on sky."));
		if(!Double.isNaN(tip)) cursor.add(new HeaderCard("CHPTIP", tip / Unit.arcsec, "(arcsec) Chopper tip on sky."));
		if(!Double.isNaN(tilt)) cursor.add(new HeaderCard("CHPTILT", tilt / Unit.arcsec, "(arcsec) Chop tilt on sky."));
		if(profileType != null) cursor.add(new HeaderCard("CHPPROF", profileType, "Chop profile from MCCS."));
		if(symmetryType != null) cursor.add(new HeaderCard("CHPSYM", symmetryType, "Chop symmetry mode."));
		if(coordinateSystem != null) cursor.add(new HeaderCard("CHPCRSYS", coordinateSystem, "Chop coordinate system."));
		if(phaseMillis != UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("CHPPHASE", phaseMillis, "(ms) Chop phase."));
	}

	
	
	
}
