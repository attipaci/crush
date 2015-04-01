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

import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaObservationData extends SofiaHeaderData {

	public String sourceName;
	public String obsID, imageID, aotID, aorID;
	public String dataSource, obsType, sourceType;
	public String dictionaryVersion;

	public SofiaObservationData() {}
	
	public SofiaObservationData(Header header) throws FitsException, HeaderCardException {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) throws FitsException, HeaderCardException {
		dataSource = getStringValue(header, "DATASRC");
		obsType = getStringValue(header, "OBSTYPE");
		sourceType = getStringValue(header, "SRCTYPE");
		dictionaryVersion = getStringValue(header, "KWDICT");
		obsID = getStringValue(header, "OBS_ID");
		imageID = getStringValue(header, "IMAGEID");
		sourceName = getStringValue(header, "OBJECT");
		aotID = getStringValue(header, "AOT_ID");
		aorID = getStringValue(header, "AOR_ID");
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		if(sourceName != null) cursor.add(new HeaderCard("OBJECT", sourceName, "Object catalog name."));
		if(dataSource != null) cursor.add(new HeaderCard("DATASRC", dataSource, "data source category."));
		if(obsType != null) cursor.add(new HeaderCard("OBSTYPE", obsType, "type of observation."));
		if(sourceType != null) cursor.add(new HeaderCard("SRCTYPE", sourceType, "AOR source type."));
		if(dictionaryVersion != null) cursor.add(new HeaderCard("KWDICT", dictionaryVersion, "SOFIA keword dictionary version."));
		if(obsID != null) cursor.add(new HeaderCard("OBS_ID", obsID, "Sofia observation ID."));
		if(imageID != null) cursor.add(new HeaderCard("IMAGEID", imageID, "Image ID within an observation."));
		if(aotID != null) cursor.add(new HeaderCard("AOT_ID", aotID, "unique Astronomical Observation Template ID."));
		if(aorID != null) cursor.add(new HeaderCard("AOR_ID", aorID, "unique Astronomical Observation Request ID."));
	}

}
