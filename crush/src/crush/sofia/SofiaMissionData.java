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

public class SofiaMissionData extends SofiaData {
	public String obsPlanID, aircraft, missionID;
	public int flightLeg = SofiaHeader.UNKNOWN_INT_VALUE;
	
	public SofiaMissionData() {}
	
	public SofiaMissionData(SofiaHeader header) {
		this();
		parseHeader(header);
	}
	

	public void parseHeader(SofiaHeader header) {
		obsPlanID = header.getString("PLANID");	// TODO map to project?
		aircraft = header.getString("DEPLOY");
		missionID = header.getString("MISSN-ID");
		flightLeg = header.getInt("FLIGHTLG");
	}

	@Override
	public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
		//cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Mission Data ------>", false));
		if(aircraft != null) cursor.add(new HeaderCard("DEPLOY", aircraft, "aircraft base of operation."));
		if(obsPlanID != null) cursor.add(new HeaderCard("PLANID", obsPlanID, "observing plan containing all AORs."));
		if(missionID != null) cursor.add(new HeaderCard("MISSN-ID", missionID, "unique Mission ID in Mission Plan from MCCS."));
		if(flightLeg >= 0) cursor.add(new HeaderCard("FLIGHTLG", flightLeg, "Flight leg identifier."));
	}

}
 