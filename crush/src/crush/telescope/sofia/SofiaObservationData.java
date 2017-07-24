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

import jnum.Unit;
import jnum.Util;
import jnum.fits.FitsToolkit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;


public class SofiaObservationData extends SofiaData {
    public String sourceName;
    public String errorStatus;
    public String obsID, imageID, aotID, aorID, fileGroupID;
    public String dataSource, obsType, sourceType;
    public String dictionaryVersion;
    public int serialNo = -1;

    public double startMJD, startLST = Double.NaN;

    public SofiaObservationData() {}

    public SofiaObservationData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        dataSource = header.getString("DATASRC");
        obsType = header.getString("OBSTYPE");
        errorStatus = header.getString("OBSSTAT");			// new in rev. F
        sourceType = header.getString("SRCTYPE");
        dictionaryVersion = header.getString("KWDICT");
        obsID = header.getString("OBS_ID");	
        serialNo = header.getInt("OBSERNO", -1);			// not in 3.0
        imageID = header.getString("IMAGEID");
        sourceName = header.getString("OBJECT");			// not in 3.0
        aotID = header.getString("AOT_ID");
        aorID = header.getString("AOR_ID");
        fileGroupID = header.getString("FILEGPID");		// not in 3.0

        if(header.containsKey("LST-OBS")) startLST = Util.parseTime(header.getString("LST-OBS"));	// not in 3.0
        startMJD = header.getDouble("MJD-OBS", Double.NaN);										// not in 3.0

    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Observation Data ------>", false));
        if(sourceName != null) c.add(new HeaderCard("OBJECT", sourceName, "Object catalog name."));
        if(!Double.isNaN(startMJD)) c.add(new HeaderCard("MJD-OBS", startMJD, "MJD at the start of observation."));
        if(!Double.isNaN(startLST)) c.add(new HeaderCard("LST-OBS", Util.HMS(startLST), "LST at the start of observation"));
        if(dataSource != null) c.add(new HeaderCard("DATASRC", dataSource, "data source category."));
        if(obsType != null) c.add(new HeaderCard("OBSTYPE", obsType, "type of observation."));
        if(errorStatus != null) c.add(new HeaderCard("OBSSTAT", errorStatus, "Observation error status."));
        if(sourceType != null) c.add(new HeaderCard("SRCTYPE", sourceType, "AOR source type."));
        if(dictionaryVersion != null) c.add(new HeaderCard("KWDICT", dictionaryVersion, "SOFIA keword dictionary version."));
        if(obsID != null) c.add(new HeaderCard("OBS_ID", obsID, "Sofia observation ID."));
        if(serialNo >= 0) c.add(new HeaderCard("OBSERNO", serialNo, "Observation serial number."));
        if(imageID != null) c.add(new HeaderCard("IMAGEID", imageID, "Image ID within an observation."));
        if(aotID != null) c.add(new HeaderCard("AOT_ID", aotID, "unique Astronomical Observation Template ID."));
        if(aorID != null) c.add(new HeaderCard("AOR_ID", aorID, "unique Astronomical Observation Request ID."));
        if(fileGroupID != null) c.add(new HeaderCard("FILEGPID", fileGroupID, "User ID for grouping files together."));
    }


    @Override
    public String getLogID() {
        return "obs";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("mjd")) return startMJD;
        else if(name.equals("lsth")) return startLST / Unit.hour;
        else if(name.equals("serial")) return serialNo;
        else if(name.equals("aor")) return aorID;
        else if(name.equals("aot")) return aotID;
        else if(name.equals("obsid")) return obsID;
        else if(name.equals("src")) return dataSource;
        else if(name.equals("dict")) return dictionaryVersion;
        else if(name.equals("err")) return errorStatus;
        else if(name.equals("fgid")) return fileGroupID;
        else if(name.equals("imgid")) return imageID;
        else if(name.equals("obj")) return sourceName;
        else if(name.equals("objtype")) return sourceType;
        
        return super.getTableEntry(name);
    }

}
