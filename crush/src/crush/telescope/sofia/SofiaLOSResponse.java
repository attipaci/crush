/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
package crush.telescope.sofia;

import crush.instrument.FrameResponse;

public class SofiaLOSResponse extends FrameResponse<SofiaFrame> {
    /**
     * 
     */
    private static final long serialVersionUID = -2854400538563120633L;

    public SofiaLOSResponse() {
        setDerivative(2);
    }
    
    @Override
    protected final double getValue(SofiaFrame exposure) throws Exception {
        return exposure.getLOSAngle();
    }
}