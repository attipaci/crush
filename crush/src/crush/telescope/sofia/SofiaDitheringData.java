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

import jnum.Unit;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaDitheringData extends SofiaData {
    public String coordinateSystem;
    public Vector2D offset = new Vector2D(Double.NaN, Double.NaN);
    public String patternShape;
    public int positions = UNKNOWN_INT_VALUE;
    public int index = UNKNOWN_INT_VALUE;

    public SofiaDitheringData() {}

    public SofiaDitheringData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    public void parseHeader(SofiaHeader header) {
        coordinateSystem = header.getString("DTHCRSYS");		// new in 3.0

        offset = new Vector2D(Double.NaN, Double.NaN);
        offset.setX(header.getDouble("DTHXOFF"));
        offset.setY(header.getDouble("DTHYOFF"));
     
        patternShape = header.getString("DTHPATT");
        positions = header.getInt("DTHNPOS");
        index = header.getInt("DTHINDEX");
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(HeaderCard.createCommentCard("<------ SOFIA Dithering Data ------>"));
        
        c.add(makeCard("DTHCRSYS", coordinateSystem, "Dither coordinate system."));
        
        Vector2D v = offset == null ? new Vector2D(Double.NaN, Double.NaN) : offset;
        c.add(makeCard("DTHXOFF", v.x() / Unit.arcsec, "(arcsec) Dither X offset."));
        c.add(makeCard("DTHYOFF", v.y() / Unit.arcsec, "(arcsec) Dither Y offset."));
        
        c.add(makeCard("DTHPATT", patternShape, "Approximate shape of dither pattern."));
        c.add(makeCard("DTHNPOS", positions, "Number of dither positions."));
        c.add(makeCard("DTHINDEX", index, "Dither position index."));
    }

    @Override
    public String getLogID() {
        return "dither";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("dx")) return offset.x() / Unit.arcsec;
        else if(name.equals("dy")) return offset.y() / Unit.arcsec;
        else if(name.equals("index")) return index;
        else if(name.equals("pattern")) return patternShape;
        else if(name.equals("npos")) return positions;
        else if(name.equals("sys")) return coordinateSystem;
       
        return super.getTableEntry(name);
    }
    
    
}
