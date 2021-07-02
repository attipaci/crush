/* *****************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.instrument.laboca;

import crush.instrument.FrameResponse;

public class LabocaHe3Response extends FrameResponse<LabocaFrame> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4390450615227012414L;

	public LabocaHe3Response() {
	    super(true);
	}
	
    @Override
    protected final double getValue(LabocaFrame exposure) throws Exception {
        return exposure.he3Temp;
    }

	
}
