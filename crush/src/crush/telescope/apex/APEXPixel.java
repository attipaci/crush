/*******************************************************************************
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/

package crush.telescope.apex;


import crush.instrument.SingleColorPixel;
import jnum.math.Vector2D;

public abstract class APEXContinuumPixel extends SingleColorPixel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3101551324892059033L;

	public Vector2D fitsPosition;

	
	public APEXContinuumPixel(APEXInstrument<?> array, int backendIndex) {
		super(array, backendIndex);
	}
	
	@Override
	public APEXContinuumPixel copy() {
		APEXContinuumPixel copy = (APEXContinuumPixel) super.copy();
		if(fitsPosition != null) copy.fitsPosition = (Vector2D) fitsPosition.clone();
		return copy;
	}
	
	
	
}
