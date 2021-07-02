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
package crush.devel;

import crush.Integration;
import crush.Scan;
import jnum.math.Coordinate2D;

public class ShadowScan extends Scan<Integration<?>> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3683079235950210496L;
	Scan<?> parent;
		
	public ShadowScan(Scan<?> parent) {
		super(parent.getInstrument());
		this.parent = parent;
	}
	
	@Override
	public Integration<?> getIntegrationInstance() {
		return parent.getIntegrationInstance();
	}

	@Override
	public void read(String descriptor, boolean readFully) throws Exception {
		throw new UnsupportedOperationException("Shadow scans cannot be read.");
	}

    @Override
    public Coordinate2D getNativeCoordinates() {
        return parent.getNativeCoordinates();
    }


}
