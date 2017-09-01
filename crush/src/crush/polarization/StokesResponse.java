/*******************************************************************************
 * Copyright (c) 2017 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.polarization;

import jnum.astro.Stokes;

public class StokesResponse extends Stokes {
    /**
     * 
     */
    private static final long serialVersionUID = -7492564624593958265L;
    
    private boolean isInvertedQ, isInvertedU;
    
    public StokesResponse() { 
        super();
        isInvertedQ = isInvertedU = false;
    }
    
    @Override
    public int hashCode() { return super.hashCode() ^ (isInvertedQ ? 1 : 0) ^ (isInvertedU ? 1 : 0); }
    
    @Override
    public boolean equalsStokes(Stokes s) {
        if(!(s instanceof StokesResponse)) return false;
        if(((StokesResponse) s).isInvertedQ != isInvertedQ) return false;
        if(((StokesResponse) s).isInvertedU != isInvertedU) return false;
        return super.equalsStokes(s); 
    }
    
    
    public boolean isInvertedQ() { return isInvertedQ; }
    
    public void setInvertedQ(boolean value) { isInvertedQ = value; }
    
    public boolean isInvertedU() { return isInvertedU; }
    
    public void setInvertedU(boolean value) { isInvertedU = value; }
    
    public final void setInverted(boolean value) {
        setInvertedQ(value);
        setInvertedU(value);
    }
    
}
