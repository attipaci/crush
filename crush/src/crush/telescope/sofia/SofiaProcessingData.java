/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import jnum.Util;
import jnum.fits.FitsToolkit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaProcessingData extends SofiaData {
    Set<String> associatedAORs, associatedMissionIDs;
    Set<Double> associatedFrequencies;
    String processLevel;
    String headerStatus;
    String softwareName, softwareFullVersion, productType, revision, quality; 
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
        quality = header.getString("DATAQUAL");
        nSpectra = header.getInt("N_SPEC", -1);
        softwareName = header.getString("PIPELINE");
        softwareFullVersion = header.getString("PIPEVERS");
        productType = header.getString("PRODTYPE");
        revision = header.getString("FILEREV");
        
        String list = header.getString("ASSC_AOR");
        if(list == null) associatedAORs = null;
        else {
            associatedAORs = new HashSet<String>();
            StringTokenizer tokens = new StringTokenizer(list, ",");
            while(tokens.hasMoreTokens()) associatedAORs.add(tokens.nextToken().trim());
        }
        
        list = header.getString("ASSC_MSN");
        if(list == null) associatedMissionIDs = null;
        else {
            associatedMissionIDs = new HashSet<String>();
            StringTokenizer tokens = new StringTokenizer(list, ",");
            while(tokens.hasMoreTokens()) associatedMissionIDs.add(tokens.nextToken().trim());
        }
        
        list = header.getString("ASSC_FRQ");
        if(list == null) associatedFrequencies = null;
        else {
            associatedFrequencies = new HashSet<Double>();
            StringTokenizer tokens = new StringTokenizer(list, " \t,");
            while(tokens.hasMoreTokens()) associatedFrequencies.add(Double.parseDouble(tokens.nextToken()));
        }
            
        
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
        if(processLevel != null) if(processLevel.toUpperCase().startsWith("LEVEL_")) {
            try { level = Integer.parseInt(processLevel.substring(6)); }
            catch(NumberFormatException e) {}
        }

        if(processLevel != null) c.add(makeCard("PROCSTAT", processLevel, getComment(level)));
        if(headerStatus != null) c.add(makeCard("HEADSTAT", headerStatus, "Header state."));
        if(softwareName != null) c.add(makeCard("PIPELINE", softwareName, "Software that created this file."));
        if(softwareFullVersion != null) c.add(makeCard("PIPEVERS", softwareFullVersion, "Full software version info."));
        if(productType != null) c.add(makeCard("PRODTYPE", productType, "Type of product."));
        if(revision != null) c.add(makeCard("FILEREV", revision, "File revision identifier."));
        if(quality != null) c.add(makeCard("DATAQUAL", quality, "Data quality level."));
        if(nSpectra >= 0) c.add(makeCard("N_SPEC", nSpectra, "Number of spectra included."));
        
        if(associatedAORs != null) if(!associatedAORs.isEmpty())
            FitsToolkit.addLongKey(c, "ASSC_AOR", toString(associatedAORs), "Associated AOR IDs.");
        
        if(associatedMissionIDs != null) if(!associatedMissionIDs.isEmpty()) 
            FitsToolkit.addLongKey(c, "ASSC_MSN", toString(associatedMissionIDs), "Associated Mission IDs.");
        
        if(associatedFrequencies != null) if(!associatedFrequencies.isEmpty())
            FitsToolkit.addLongKey(c, "ASSC_FRQ", toString(associatedFrequencies), "Associated Frequencies.");
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

    public void addAssociatedAOR(String id) {
        if(associatedAORs == null) associatedAORs = new HashSet<String>();
        associatedAORs.add(id);
    }
    
    public void addAssociatedMissionID(String id) {
        if(associatedMissionIDs == null) associatedMissionIDs = new HashSet<String>();
        associatedMissionIDs.add(id);
    }
    
    public void addAssociatedFrequency(double value) {
        if(associatedFrequencies == null) associatedFrequencies = new HashSet<Double>();
        associatedFrequencies.add(value);
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

    
    public static String toString(Set<?> set) {
        if(set.isEmpty()) return null;
        
        Object[] array = set.toArray();
          
        StringBuffer buf = new StringBuffer();
        buf.append(array[0]);

        for(int i=1; i<array.length; i++) if(array[i] == null) {
            Object o = array[i];
           
            if(o instanceof Short) if((Short) o == UNKNOWN_INT_VALUE) continue;
            else if(o instanceof Integer) if((Integer) o == UNKNOWN_INT_VALUE) continue;
            else if(o instanceof Long) if((Long) o == UNKNOWN_INT_VALUE) continue;
            else if(o instanceof Float) if((Float) o == UNKNOWN_FLOAT_VALUE) continue;
            else if(o instanceof Double) if((Double) o == UNKNOWN_DOUBLE_VALUE) continue;
            else if(o instanceof String) if(Util.equals(o, UNKNOWN_STRING_VALUE)) continue;
            
            buf.append(", ");
            buf.append(o);
        }

        return new String(buf);
    }
    
    
    
    @Override
    public void merge(SofiaData other, boolean isSameFlight) {
        if(other == this) return;
        
        SofiaProcessingData p = (SofiaProcessingData) other;
        int level = Math.min(qualityLevel, p.qualityLevel);
          
        super.merge(other, isSameFlight);
        
        qualityLevel = level;
        quality = qualityNames[qualityLevel];
        
        headerStatus = MODIFIED;
    }
    
    public static String getLevelName(int level) {
        return "LEVEL_" + level;
    }
    
    protected String getProductType(int dims) {
        switch(dims) {
        case 0: return "HEADER";
        case 1: return "1D";
        case 2: return "IMAGE";
        case 3: return "CUBE";
        }
        return dims > 0 ? dims + "D" : "UKNOWN";
    }

    
    public static class CRUSH extends SofiaProcessingData {
      
        public CRUSH(boolean isCalibrated, int dims, int qualityLevel) {
            processLevel = getLevelName(isCalibrated ? 3 : 2);
            headerStatus = SofiaProcessingData.MODIFIED;
            softwareName = "crush v" + crush.CRUSH.getVersion();
            softwareFullVersion = "crush v" + crush.CRUSH.getFullVersion();
            productType = "CRUSH-" + getProductType(dims);
            this.qualityLevel = qualityLevel; 
        } 
        
    }
    
    
    public static String FAIL = "FAIL";
    public static String PROBLEM = "PROBLEM";
    public static String TEST = "TEST";
    public static String USABLE = "USABLE";
    public static String NOMINAL = "NOMINAL";
    
    public static String ORIGINAL = "ORIGINAL";
    public static String MODIFIED = "MODIFIED";
    public static String CORRECTED = "CORRECTED";
    
    public static String qualityNames[] = { FAIL, PROBLEM, TEST, USABLE, NOMINAL };
    public static int defaultQuality = qualityNames.length - 1;
}
