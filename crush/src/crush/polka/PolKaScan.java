/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2010 Attila Kovacs 

package crush.polka;

import crush.laboca.*;

public class PolKaScan extends LabocaScan {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8542870092250553685L;
	
	public PolKaScan(Laboca instrument) {
		super(instrument);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public LabocaSubscan getIntegrationInstance() {
		return new PolKaSubscan(this);
	}
	
	@Override
	public void validate() {
		PolKa polka = (PolKa) instrument;
		
		if(polka.timeStamps != null)
			polka.calcWavePlate(getFirstIntegration().getFirstFrame().MJD, getLastIntegration().getLastFrame().MJD);
		
		
		super.validate();
	}
	
}
