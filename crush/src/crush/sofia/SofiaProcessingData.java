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

import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaProcessingData extends SofiaHeaderData {
	String processLevel;
	String headerStatus, softwareName, softwareFullVersion, productType, revision; 
	
	public SofiaProcessingData() {}
	
	public SofiaProcessingData(Header header) {
		this();
		parseHeader(header);
	}
	
	@Override
	public void parseHeader(Header header) {
		processLevel = getStringValue(header, "PROCSTAT");
		headerStatus = getStringValue(header, "HEADSTAT");
		softwareName = getStringValue(header, "PIPELINE");
		softwareFullVersion = getStringValue(header, "PIPEVERS");
		productType = getStringValue(header, "PRODTYPE");
		revision = getStringValue(header, "FILEREV");
	}

	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException {
		int level = 0;
		if(processLevel.toUpperCase().startsWith("LEVEL_")) {
			try { level = Integer.parseInt(processLevel.substring(6)); }
			catch(NumberFormatException e) {}
		}
		
		if(processLevel != null) cursor.add(new HeaderCard("PROCSTAT", processLevel, getComment(level)));
		if(headerStatus != null) cursor.add(new HeaderCard("HEADSTAT", headerStatus, "Status of header key/value pairs."));
		if(softwareName != null) cursor.add(new HeaderCard("PIPELINE", softwareName, "Software that produced scan file."));
		if(softwareFullVersion != null) cursor.add(new HeaderCard("PIPEVERS", softwareFullVersion, "Full version info of software."));
		if(productType != null) cursor.add(new HeaderCard("PRODTYPE", productType, "Prodcu type produced by software."));
		if(revision != null) cursor.add(new HeaderCard("FILEREV", revision, "File revision identifier."));
	}
	
	public static String getComment(int level) {
		if(level < 0 || level > 4) return "Invalid processing level: " + level;
		return processLevelComment[level];
	}
	
	private final static String[] processLevelComment = {
		"Unknown processing level", 
		"Raw engineering/diagnostic data.",
		"Raw uncalibrated science data.",
		"Corrected/reduced science data.",
		"Flux-calibrated science data.",
		"Higher order product (e.g. composites)."
	};
	

}
