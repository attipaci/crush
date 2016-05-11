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

public class SofiaProcessingData extends SofiaData {
	String processLevel;
	String headerStatus, softwareName, softwareFullVersion, productType, revision, quality; 
	int nSpectra = -1;
	int qualityLevel = defaultQuality;
	
	public SofiaProcessingData() {}
	
	public SofiaProcessingData(SofiaHeader header) {
		this();
		parseHeader(header);
	}

	public void parseHeader(SofiaHeader header) {
		processLevel = header.getString("PROCSTAT");
		headerStatus = header.getString("HEADSTAT");
		softwareName = header.getString("PIPELINE");
		softwareFullVersion = header.getString("PIPEVERS");
		productType = header.getString("PRODTYPE");
		revision = header.getString("FILEREV");
		quality = header.getString("DATAQUAL");				// new in 3.0
		nSpectra = header.getInt("N_SPEC", -1);				// new in 3.0
		
		qualityLevel = defaultQuality;
		if(quality != null) for(int i=qualityNames.length; --i >= 0; ) if(quality.equalsIgnoreCase(qualityNames[i])) {
			qualityLevel = i;
			break;
		}
	}

	@Override
	public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Processing Information ------>", false));
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
		if(quality != null) cursor.add(new HeaderCard("DATAQUAL", quality, "Data quality."));
		if(nSpectra >= 0) cursor.add(new HeaderCard("N_SPEC", nSpectra, "Number of spectra included."));
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
	
	public static String qualityNames[] = { "FAIL", "PROBLEM", "TEST", "USABLE", "NOMINAL" };
	public static int defaultQuality = qualityNames.length - 1;
}
