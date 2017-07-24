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
import jnum.astro.EclipticCoordinates;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.GalacticCoordinates;
import jnum.fits.FitsToolkit;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;


public class SofiaMappingData extends SofiaData {
    public String coordinateSystem;
    public String pattern;
    public int sizeX = 0, sizeY = 0;
    public Vector2D step = new Vector2D(Double.NaN, Double.NaN);

    public SofiaMappingData() {}

    public SofiaMappingData(SofiaHeader header) {
        this();
        parseHeader(header);
    }


    public void parseHeader(SofiaHeader header) {
        coordinateSystem = header.getString("MAPCRSYS");
        pattern = header.getString("MAPPATT");
        sizeX = header.getInt("MAPNXPOS");
        sizeY = header.getInt("MAPNYPOS");
        step.setX(header.getDouble("MAPINTX", Double.NaN) * Unit.arcmin);
        step.setY(header.getDouble("MAPINTY", Double.NaN) * Unit.arcmin);
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ SOFIA Mapping Data ------>", false));
        if(coordinateSystem != null) c.add(new HeaderCard("MAPCRSYS", coordinateSystem, "Mapping coordinate system."));
        if(pattern != null) c.add(new HeaderCard("MAPPATT", pattern, "Mapping pattern."));
        if(sizeX != SofiaHeader.UNKNOWN_INT_VALUE) c.add(new HeaderCard("MAPNXPOS", sizeX, "Number of map positions in X"));
        if(sizeY != SofiaHeader.UNKNOWN_INT_VALUE) c.add(new HeaderCard("MAPNYPOS", sizeY, "Number of map positions in Y"));
        if(Double.isNaN(step.x())) c.add(new HeaderCard("MAPINTX", step.x() / Unit.arcmin, "(arcmin) Map step interval in X"));
        if(Double.isNaN(step.y())) c.add(new HeaderCard("MAPINTY", step.y() / Unit.arcmin, "(arcmin) Map step interval in Y"));
    }

    public Class<? extends SphericalCoordinates> getBasis() {
        if(coordinateSystem == null) return null;
        else if(coordinateSystem.equalsIgnoreCase("EQUATORIAL")) return EquatorialCoordinates.class;
        else if(coordinateSystem.equalsIgnoreCase("ECLIPTIC")) return EclipticCoordinates.class;
        else if(coordinateSystem.equalsIgnoreCase("GALACTIC")) return GalacticCoordinates.class;
        else return null;	
    }

    @Override
    public String getLogID() {
        return "map";
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("stepx")) return step.x() / Unit.arcmin;
        else if(name.equals("stepy")) return step.y() / Unit.arcmin;
        else if(name.equals("nx")) return sizeX;
        else if(name.equals("ny")) return sizeY;
        else if(name.equals("sys")) return coordinateSystem;
        else if(name.equals("pattern")) return pattern;
        
        return super.getTableEntry(name);
    }    
    
}
