/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.ebex;

import crush.Scan;
import crush.Integration;

public class EBEXIntegration extends Integration<EBEX, EBEXFrame> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2919571597992421531L;

	public EBEXIntegration(Scan<EBEX, ?> parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public boolean hasGaps(int tolerance) { return false; }

	@Override
	public double getCrossingTime(double sourceSize) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public EBEXFrame getFrameInstance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getNodPhase() {
		// TODO Auto-generated method stub
		return 0;
	}

}
