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

import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaOriginationData extends SofiaData {
    public String organization, observer, creator, operator;
    public String fileName, observatory;

    public SofiaOriginationData() {}

    public SofiaOriginationData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        organization = header.getString("ORIGIN");
        observer = header.getString("OBSERVER");
        creator = header.getString("CREATOR");
        operator = header.getString("OPERATOR");
        fileName = header.getString("FILENAME");	
        //fileName = header.getString("OBSERVAT");		// not in 3.0
    }

    @Override
    public void editHeader(Header header, Cursor<String, HeaderCard> cursor) throws HeaderCardException {
        //cursor.add(new HeaderCard("COMMENT", "<------ SOFIA Origination Data ------>", false));
        if(organization != null) cursor.add(new HeaderCard("ORIGIN", organization, "Organization where data originated."));
        if(observer != null) cursor.add(new HeaderCard("OBSERVER", observer, "Name(s) of observer(s)."));
        if(creator != null) cursor.add(new HeaderCard("CREATOR", creator, "Software / Task that created the raw data."));
        if(operator != null) cursor.add(new HeaderCard("OPERATOR", operator, "Name(s) of operator(s)."));
        if(fileName != null) cursor.add(new HeaderCard("FILENAME", fileName, "Original file name."));
        if(observatory != null) cursor.add(new HeaderCard("OBSERVAT", observatory, "Observatory name."));
    }

    @Override
    public String getLogID() {
        return "orig";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("creator")) return creator;
        else if(name.equals("file")) return fileName;
        else if(name.equals("observer")) return observer;
        else if(name.equals("operator")) return operator;
        
        return super.getTableEntry(name);
    }
    
}
