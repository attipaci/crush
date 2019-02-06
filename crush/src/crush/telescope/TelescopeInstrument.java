/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope;


import crush.Channel;
import crush.Instrument;
import jnum.astro.AstroProjector;
import jnum.data.image.SphericalGrid;
import jnum.math.Coordinate2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.projection.Gnomonic;
import jnum.projection.SphericalProjection;

public abstract class TelescopeInstrument<ChannelType extends Channel> extends Instrument<ChannelType> {
    /**
     * 
     */
    private static final long serialVersionUID = 4331535366684833861L;
    public Mount mount;
    
    private SphericalProjection projection; // Set by the first call to getProjection()
    
    
    public TelescopeInstrument(String name, int size) {
        super(name, size);
    }

    public TelescopeInstrument(String name) {
        super(name);
    }
    
    
    @Override
    public AstroProjector getProjectorInstance(Coordinate2D reference) {
        return new AstroProjector(getProjection((SphericalCoordinates) reference)); 
    }
    
    public SphericalProjection getProjection(SphericalCoordinates reference) {
        if(projection == null) {       
            try { projection = hasOption("projection") ? SphericalProjection.forName(option("projection").getValue()) : new Gnomonic(); }
            catch(Exception e) { projection = new Gnomonic(); }
        
            projection.setReference(reference);
        }
        return projection;
    }
    
    @Override
    public SphericalGrid getGridInstance() { return new SphericalGrid(); }
    

    // Returns the offset of the pointing center from the the rotation center for a given rotation...
    @Override
    public Vector2D getPointingOffset(double rotationAngle) {
        Vector2D offset = getLayout().getPointingCenterOffset();
        
        final double sinA = Math.sin(rotationAngle);
        final double cosA = Math.cos(rotationAngle);
        
        if(mount == Mount.CASSEGRAIN) {
            Vector2D dP = getLayout().getPointingCenterOffset();    
            offset.addX(dP.x() * (1.0 - cosA) + dP.y() * sinA);
            offset.addY(dP.x() * sinA + dP.y() * (1.0 - cosA));
        }
        return offset;
    }
        
  

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("mount")) return mount.name();
        return super.getTableEntry(name);        
    }
    

    
}
