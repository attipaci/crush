/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/


package crush.telescope.sofia;

import jnum.fits.FitsToolkit;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaCollectionData extends SofiaData {
    public boolean isChopping = false, isNodding = false, isDithering = false, isMapping = false, isScanning = false;


    public SofiaCollectionData() {}

    public SofiaCollectionData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        isChopping = header.getBoolean("CHOPPING");
        isNodding = header.getBoolean("NODDING");
        isDithering = header.getBoolean("DITHER");
        isMapping = header.getBoolean("MAPPING");
        isScanning = header.getBoolean("SCANNING");
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Data Collection Keywords ------>", false));
        c.add(new HeaderCard("CHOPPING", isChopping, "Was chopper in use?"));   
        c.add(new HeaderCard("NODDING", isNodding, "Was nodding used?"));   
        c.add(new HeaderCard("DITHER", isDithering, "Was dithering used?"));    
        c.add(new HeaderCard("MAPPING", isMapping, "Was mapping?"));    
        c.add(new HeaderCard("SCANNING", isScanning, "Was scanning?"));
    }

    @Override
    public String getLogID() {
        return "mode";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("chop")) return isChopping;
        else if(name.equals("nod")) return isNodding;
        else if(name.equals("dither")) return isDithering;
        else if(name.equals("map")) return isMapping;
        else if(name.equals("scan")) return isScanning;


        return super.getTableEntry(name);
    }


}
