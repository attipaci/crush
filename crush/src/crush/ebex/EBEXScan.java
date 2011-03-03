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

package crush.ebex;

import crush.Scan;
import util.*;

public class EBEXScan extends Scan<EBEX, EBEXIntegration> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8869984594579070285L;

	ACSData acs;
	
	public EBEXScan(EBEX instrument) {
		super(instrument);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void validate() {
		super.validate();
		double PA = 0.5 * (getFirstIntegration().getFirstFrame().getParallacticAngle() + getLastIntegration().getLastFrame().getParallacticAngle());
		System.err.println("   Mean parallactic angle is " + Util.f1.format(PA / Unit.deg) + " deg.");
	}

	@Override
	public EBEXIntegration getIntegrationInstance() {
		return new EBEXIntegration(this);
	}

	@Override
	public void read(String descriptor) throws Exception {
		// TODO Auto-generated method stub
		
	}

}
