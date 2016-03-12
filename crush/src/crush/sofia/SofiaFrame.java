/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import crush.HorizontalFrame;
import crush.Scan;
import jnum.astro.AstroProjector;
import jnum.astro.GeodeticCoordinates;
import jnum.astro.TelescopeCoordinates;
import jnum.math.Vector2D;

public class SofiaFrame extends HorizontalFrame {
    /**
     * 
     */
    private static final long serialVersionUID = -180851195016374209L;
    
    public GeodeticCoordinates site;
    public TelescopeCoordinates telescopeCoords;
    
    public double instrumentVPA;
    public double telescopeVPA;
    public double PWV;

    public SofiaFrame(Scan<?, ?> parent) {
        super(parent);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void project(final Vector2D position, final AstroProjector projector) {
        if(projector.getCoordinates() instanceof TelescopeCoordinates) {
            projector.setReferenceCoords();
            getNativeOffset(position, projector.offset);
            projector.getCoordinates().addNativeOffset(projector.offset);
            projector.project();
        } 
        // TODO handle native coordinates...
        else super.project(position, projector);        
    }
    
    
}
