/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/

package crush.telescope.sofia;

import jnum.fits.FitsToolkit;
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
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Processing Information ------>", false));
        int level = 0;
        if(processLevel.toUpperCase().startsWith("LEVEL_")) {
            try { level = Integer.parseInt(processLevel.substring(6)); }
            catch(NumberFormatException e) {}
        }

        if(processLevel != null) c.add(new HeaderCard("PROCSTAT", processLevel, getComment(level)));
        if(headerStatus != null) c.add(new HeaderCard("HEADSTAT", headerStatus, "Status of header key/value pairs."));
        if(softwareName != null) c.add(new HeaderCard("PIPELINE", softwareName, "Software that produced scan file."));
        if(softwareFullVersion != null) c.add(new HeaderCard("PIPEVERS", softwareFullVersion, "Full version info of software."));
        if(productType != null) c.add(new HeaderCard("PRODTYPE", productType, "Prodcu type produced by software."));
        if(revision != null) c.add(new HeaderCard("FILEREV", revision, "File revision identifier."));
        if(quality != null) c.add(new HeaderCard("DATAQUAL", quality, "Data quality."));
        if(nSpectra >= 0) c.add(new HeaderCard("N_SPEC", nSpectra, "Number of spectra included."));
    }

    @Override
    public String getLogID() {
        return "proc";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("q")) return qualityLevel;
        else if(name.equals("nspec")) return nSpectra;
        else if(name.equals("quality")) return quality;
        else if(name.equals("level")) return processLevel;
        else if(name.equals("head")) return headerStatus;
        else if(name.equals("product")) return productType;
        
        return super.getTableEntry(name);
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
