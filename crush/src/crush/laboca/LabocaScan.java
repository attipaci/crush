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

package crush.laboca;

import crush.apex.APEXArrayScan;

public class LabocaScan extends APEXArrayScan<Laboca, LabocaSubscan> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3989020051894803532L;

	public LabocaScan(Laboca instrument) {
		super(instrument);
	}

	@Override
	public LabocaSubscan getIntegrationInstance() {
		return new LabocaSubscan(this);
	}
	
	@Override
	public boolean readMonitor() { return hasOption("he3"); }
}
