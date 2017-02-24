/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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

import crush.Scan;
import crush.telescope.HorizontalFrame;
import jnum.astro.AstroProjector;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.GeodeticCoordinates;
import jnum.astro.TelescopeCoordinates;
import jnum.math.Vector2D;

public class SofiaFrame extends HorizontalFrame {
    /**
     * 
     */
    private static final long serialVersionUID = -180851195016374209L;
    
    public EquatorialCoordinates objectEq;
    public GeodeticCoordinates site;
     
    public double instrumentVPA;
    public double telescopeVPA;
    public double chopVPA;
     
    public double PWV;

    public SofiaFrame(Scan<?, ?> parent) {
        super(parent);
    }
    
    @Override
    public void project(final Vector2D position, final AstroProjector projector) {
        if(projector.getCoordinates() instanceof TelescopeCoordinates) {
            projector.setReferenceCoords();
            getNativeOffset(position, projector.offset);
            projector.getCoordinates().addNativeOffset(projector.offset);
            projector.project();
        } 
        else super.project(position, projector);        
    }    
    
    public void telescopeToEquatorial(Vector2D offset) {
        telescopeToNativeEquatorial(offset);
        offset.scaleX(-1.0);
    }
    
    public void equatorialToTelescope(Vector2D offset) {
        offset.scaleX(-1.0);
        nativeEquatorialToTelescope(offset);
    }
    
    public void telescopeToNativeEquatorial(Vector2D offset) {
        offset.rotate(Math.PI - telescopeVPA);
    }
    
    public void nativeEquatorialToTelescope(Vector2D offset) {
        offset.rotate(Math.PI + telescopeVPA);
    }
    
    
}
