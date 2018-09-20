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


public class SofiaMissionData extends SofiaData {
    public String obsPlanID, base, missionID;
    public int flightLeg = UNKNOWN_INT_VALUE;

    public SofiaMissionData() {}

    public SofiaMissionData(SofiaHeader header) {
        this();
        parseHeader(header);
    }


    public void parseHeader(SofiaHeader header) {
        obsPlanID = header.getString("PLANID");	// TODO map to project?	
        base = header.getString("DEPLOY");	
        missionID = header.getString("MISSN-ID");
        flightLeg = header.getInt("FLIGHTLG");
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Mission Data ------>", false));
        c.add(makeCard("DEPLOY", base, "aircraft base of operation."));
        c.add(makeCard("MISSN-ID", missionID, "unique Mission ID in Mission Plan from MCCS."));
        c.add(makeCard("FLIGHTLG", flightLeg, "Flight leg identifier."));
        if(obsPlanID != null) c.add(new HeaderCard("PLANID", obsPlanID, "observing plan containing all AORs."));
    }

    @Override
    public String getLogID() {
        return "missn";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("leg")) return flightLeg;
        else if(name.equals("id")) return missionID;
        else if(name.equals("plan")) return obsPlanID;
        
        return super.getTableEntry(name);
    }    
    
    @Override
    public void merge(SofiaData other, boolean isSameFlight) {
        if(other == this) return;
        
        String origMissionID = missionID;
        
        super.merge(other, isSameFlight);
        
        missionID = origMissionID;
    }

}
