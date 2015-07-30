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

import kovacs.util.Util;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaObservationData extends SofiaHeaderData {
	public String sourceName;
	public String errorStatus;
	public String obsID, imageID, aotID, aorID, fileGroupID;
	public String dataSource, obsType, sourceType;
	public String dictionaryVersion;
	public int serialNo = -1;

	public double startMJD, startLST = Double.NaN;
	
	public SofiaObservationData() {}
	
	public SofiaObservationData(Header header) {
		this();
		parseHeader(header);
	}
	
	
	@Override
	public void parseHeader(Header header) {
		dataSource = getStringValue(header, "DATASRC");
		obsType = getStringValue(header, "OBSTYPE");
		errorStatus = getStringValue(header, "OBSSTAT");			// new in rev. F
		sourceType = getStringValue(header, "SRCTYPE");
		dictionaryVersion = getStringValue(header, "KWDICT");
		obsID = getStringValue(header, "OBS_ID");
		serialNo = header.getIntValue("OBSERNO", -1);			// not in 3.0
		imageID = getStringValue(header, "IMAGEID");
		sourceName = getStringValue(header, "OBJECT");			// not in 3.0
		aotID = getStringValue(header, "AOT_ID");
		aorID = getStringValue(header, "AOR_ID");
		fileGroupID = getStringValue(header, "FILEGPID");		// not in 3.0
		
		if(header.containsKey("LST-OBS")) startLST = Util.parseTime(header.getStringValue("LST-OBS"));	// not in 3.0
		startMJD = header.getDoubleValue("MJD-OBS", Double.NaN);										// not in 3.0
	
	}

	@Override
	public void editHeader(Header header, Cursor cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Observation Data ------>", false));
		if(sourceName != null) cursor.add(new HeaderCard("OBJECT", sourceName, "Object catalog name."));
		if(!Double.isNaN(startMJD)) cursor.add(new HeaderCard("MJD-OBS", startMJD, "MJD at the start of observation."));
		if(!Double.isNaN(startLST)) cursor.add(new HeaderCard("LST-OBS", Util.HMS(startLST), "LST at the start of observation"));
		if(dataSource != null) cursor.add(new HeaderCard("DATASRC", dataSource, "data source category."));
		if(obsType != null) cursor.add(new HeaderCard("OBSTYPE", obsType, "type of observation."));
		if(errorStatus != null) cursor.add(new HeaderCard("OBSSTAT", errorStatus, "Observation error status."));
		if(sourceType != null) cursor.add(new HeaderCard("SRCTYPE", sourceType, "AOR source type."));
		if(dictionaryVersion != null) cursor.add(new HeaderCard("KWDICT", dictionaryVersion, "SOFIA keword dictionary version."));
		if(obsID != null) cursor.add(new HeaderCard("OBS_ID", obsID, "Sofia observation ID."));
		if(serialNo >= 0) cursor.add(new HeaderCard("OBSERNO", serialNo, "Observation serial number."));
		if(imageID != null) cursor.add(new HeaderCard("IMAGEID", imageID, "Image ID within an observation."));
		if(aotID != null) cursor.add(new HeaderCard("AOT_ID", aotID, "unique Astronomical Observation Template ID."));
		if(aorID != null) cursor.add(new HeaderCard("AOR_ID", aorID, "unique Astronomical Observation Request ID."));
		if(fileGroupID != null) cursor.add(new HeaderCard("FILEGPID", fileGroupID, "User ID for grouping files together."));
	}

}
