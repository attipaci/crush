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
	public float frequency = Float.NaN;
	public String profileType;
	public String symmetryType;
	public float amplitude = Float.NaN, amplitude2 = Float.NaN;
	public String coordinateSystem;
	public float angle = Float.NaN;
	public float tip = Float.NaN;
	public float tilt = Float.NaN;
	public int phaseMillis = UNKNOWN_INT_VALUE;
	
	public SofiaChopperData() {}
	
	public SofiaChopperData(Header header) {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) {
		frequency = header.getFloatValue("CHPFREQ", Float.NaN) * (float) Unit.Hz;
		profileType = getStringValue(header, "CHPPROF");
		symmetryType = getStringValue(header, "CHPSYM");
		amplitude = header.getFloatValue("CHPAMP1", Float.NaN) * (float) Unit.arcsec;
		amplitude2 = header.getFloatValue("CHPAMP2", Float.NaN) * (float) Unit.arcsec;
		coordinateSystem = getStringValue(header, "CHPCRSYS");
		angle = header.getFloatValue("CHPANGLE", Float.NaN) * (float) Unit.deg;
		tip = header.getFloatValue("CHPTIP", Float.NaN) * (float) Unit.arcsec;
		tilt = header.getFloatValue("CHPTILT", Float.NaN) * (float) Unit.arcsec;
		phaseMillis = header.getIntValue("CHPPHASE", UNKNOWN_INT_VALUE);
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(!Float.isNaN(frequency)) cursor.add(new HeaderCard("CHPFREQ", frequency / Unit.Hz, "(Hz) Chop frequency."));
		if(!Float.isNaN(amplitude)) cursor.add(new HeaderCard("CHPAMP1", amplitude / Unit.arcsec, "(arcsec) Chop amplitude on sky."));
		if(!Float.isNaN(amplitude2)) cursor.add(new HeaderCard("CHPAMP2", amplitude2 / Unit.arcsec, "(arcsec) Second chop amplitude on sky."));
		if(!Float.isNaN(angle)) cursor.add(new HeaderCard("CHPANGLE", angle / Unit.deg, "(deg) Chop angle on sky."));
		if(!Float.isNaN(tip)) cursor.add(new HeaderCard("CHPTIP", tip / Unit.arcsec, "(arcsec) Chopper tip on sky."));
		if(!Float.isNaN(tilt)) cursor.add(new HeaderCard("CHPTILT", tilt / Unit.arcsec, "(arcsec) Chop tilt on sky."));
		if(profileType != null) cursor.add(new HeaderCard("CHPPROF", profileType, "Chop profile from MCCS."));
		if(symmetryType != null) cursor.add(new HeaderCard("CHPSYM", symmetryType, "Chop symmetry mode."));
		if(coordinateSystem != null) cursor.add(new HeaderCard("CHPCRSYS", coordinateSystem, "Chop coordinate system."));
		if(phaseMillis != UNKNOWN_INT_VALUE) cursor.add(new HeaderCard("CHPPHASE", phaseMillis, "(ms) Chop phase."));
	}

	
	
	
}
