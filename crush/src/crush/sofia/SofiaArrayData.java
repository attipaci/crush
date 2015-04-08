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

import java.text.DecimalFormat;

import kovacs.math.Vector2D;
import kovacs.projection.Projection2D;
import kovacs.util.Copiable;
import kovacs.util.Unit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaArrayData extends SofiaHeaderData implements Copiable<SofiaArrayData> {
	public String detectorName, detectorSizeString;
	public float pixelScale = Float.NaN;
	public int subarrays = 0;
	public String[] subarraySize;
	public Vector2D arrayPointingCenter = new Vector2D();	// boresight
	public Projection2D<?> projection;		// the WCS coordinate system

	public SofiaArrayData() {}
	
	public SofiaArrayData(Header header) {
		this();
		parseHeader(header);
	}
	
	@Override
	public SofiaArrayData copy() {
		SofiaArrayData copy = (SofiaArrayData) clone();
		if(detectorName != null) copy.detectorName = new String(detectorName);
		if(detectorSizeString != null) copy.detectorSizeString = new String(detectorSizeString);
		if(subarraySize != null) {
			copy.subarraySize = new String[subarraySize.length];
			for(int i=subarraySize.length; --i >= 0; ) copy.subarraySize[i] = new String(subarraySize[i]);
		}
		if(arrayPointingCenter != null) copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.copy();
		if(projection != null) copy.projection = projection.copy();	
		return copy;
	}

	
	
	@Override
	public void parseHeader(Header header) {
		detectorName = SofiaHeaderData.getStringValue(header, "DETECTOR");
		detectorSizeString = SofiaHeaderData.getStringValue(header, "DETSIZE");
		pixelScale = header.getFloatValue("PIXSCAL", Float.NaN) * (float) Unit.arcsec;
		subarrays = header.getIntValue("SUBARRNO", 0);
		
		if(subarrays > 0) {
			subarraySize = new String[subarrays];
			DecimalFormat d2 = new DecimalFormat("00");
			for(int i=0; i<subarrays; i++) subarraySize[i] = SofiaHeaderData.getStringValue(header, "SUBARR" + d2.format(i+1));	
		}
		
		arrayPointingCenter.setX(header.getDoubleValue("SIBS_X", Double.NaN));
		arrayPointingCenter.setY(header.getDoubleValue("SIBS_Y", Double.NaN));
		
		// TODO Array WCS
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(detectorName != null) cursor.add(new HeaderCard("DETECTOR", detectorName, "Detector name"));
		if(detectorSizeString != null) cursor.add(new HeaderCard("DETSIZE", detectorSizeString, "Detector size"));
		if(!Float.isNaN(pixelScale)) cursor.add(new HeaderCard("PIXSCAL", pixelScale / Unit.arcsec, "(arcsec) Pixel scale on sky."));
		if(subarrays > 0) {
			cursor.add(new HeaderCard("SUBARRNO", subarrays, "Number of subarrays."));
			DecimalFormat d2 = new DecimalFormat("00");
			for(int i=0; i<subarrays; i++) if(subarraySize[i] != null)
				cursor.add(new HeaderCard("SUBARR" + d2.format(i+1), subarraySize[i], "Subarray " + (i+1) + " location and size."));
		}
		if(!Double.isNaN(arrayPointingCenter.x())) cursor.add(new HeaderCard("SIBS_X", arrayPointingCenter.x(), "(pixel) boresight pixel x."));
		if(!Double.isNaN(arrayPointingCenter.y())) cursor.add(new HeaderCard("SIBS_Y", arrayPointingCenter.y(), "(pixel) boresight pixel y."));
	}

	
}
