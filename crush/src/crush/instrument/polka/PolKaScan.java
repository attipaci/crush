/*******************************************************************************
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
// Copyright (c) 2010 Attila Kovacs 

package crush.instrument.polka;

import crush.instrument.laboca.*;

class PolKaScan extends LabocaScan {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8542870092250553685L;
	
	PolKaScan(Laboca instrument) {
		super(instrument);
	}
	
	@Override
    public PolKa getInstrument() { return (PolKa) super.getInstrument(); }
	
	@Override
	public LabocaSubscan getIntegrationInstance() {
		return new PolKaSubscan(this);
	}
	
	
}
