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

import jnum.fits.FitsToolkit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;


public class SofiaObservationData extends SofiaData {
    public String sourceName, obsID, dataSource, obsType, sourceType;
    private boolean isPrimaryObsID = false;
    public String dictionaryVersion;
    public String imageID, aotID, aorID, fileGroupID, redGroupID, blueGroupID;
  
    public SofiaObservationData() {}

    public SofiaObservationData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        dataSource = header.getString("DATASRC");
        obsType = header.getString("OBSTYPE");
        sourceType = header.getString("SRCTYPE");
        dictionaryVersion = header.getString("KWDICT");
        obsID = header.getString("OBS_ID");	
        imageID = header.getString("IMAGEID");
        sourceName = header.getString("OBJECT");
        aotID = header.getString("AOT_ID");
        aorID = header.getString("AOR_ID");
        fileGroupID = header.getString("FILEGPID");
        redGroupID = header.getString("FILEGP_R");
        blueGroupID = header.getString("FILEGP_B");       
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Observation Data ------>", false));
        c.add(makeCard("OBJECT", sourceName, "Object catalog name."));
        c.add(makeCard("DATASRC", dataSource, "data source category."));
        c.add(makeCard("OBSTYPE", obsType, "type of observation."));
        c.add(makeCard("SRCTYPE", sourceType, "AOR source type."));
        c.add(makeCard("KWDICT", dictionaryVersion, "SOFIA keword dictionary version."));
        c.add(makeCard("OBS_ID", (isPrimaryObsID ? "P_" : "") + obsID, "Sofia observation ID."));
       
        if(imageID != null) c.add(makeCard("IMAGEID", imageID, "Image ID within an observation."));
        if(aotID != null) c.add(makeCard("AOT_ID", aotID, "unique Astronomical Observation Template ID."));
        if(aorID != null) c.add(makeCard("AOR_ID", aorID, "unique Astronomical Observation Request ID."));
        if(fileGroupID != null) c.add(makeCard("FILEGPID", fileGroupID, "User ID for grouping files together."));
        if(redGroupID != null) c.add(makeCard("FILEGP_R", redGroupID, "User ID for grouping red filter files together."));
        if(blueGroupID != null) c.add(makeCard("FILEGP_B", blueGroupID, "User ID for grouping blue filter files together."));
    }


    @Override
    public String getLogID() {
        return "obs";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("aor")) return aorID;
        else if(name.equals("aot")) return aotID;
        else if(name.equals("obsid")) return obsID;
        else if(name.equals("src")) return dataSource;
        else if(name.equals("dict")) return dictionaryVersion;
        else if(name.equals("fgid")) return fileGroupID;
        else if(name.equals("imgid")) return imageID;
        else if(name.equals("obj")) return sourceName;
        else if(name.equals("objtype")) return sourceType;
        
        return super.getTableEntry(name);
    }

}
