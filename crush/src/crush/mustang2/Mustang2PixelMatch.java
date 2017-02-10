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

package crush.mustang2;

import crush.resonators.ToneIdentifier;
import jnum.Configurator;
import jnum.math.Range;

public class Mustang2PixelMatch extends ToneIdentifier<Mustang2PixelID> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8074371996590081658L;
	
	int readoutIndex;
	
	public Mustang2PixelMatch(Configurator options) {
		super(options);
	}
	
	@Override
	public Range getDefaultShiftRange() {
		return new Range(-1e-3, 1e-3);
	}
	
	

}
