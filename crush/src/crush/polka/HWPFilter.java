/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.polka;

import util.Range;
import util.Util;
import crush.Integration;
import crush.filters.KillFilter;


public class HWPFilter extends KillFilter {

	public HWPFilter(Integration<?, ?> integration) {
		super(integration);
	}

	public HWPFilter(Integration<?, ?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);
		
		System.err.print("   Half-waveplate filter: ");
		
		// Use waveplate PolKa.frequency and PolKa.jitter 
		// Use filter.hwp.harmonics
		PolKa polka = (PolKa) integration.instrument;
		
		double f0 = polka.wavePlateFrequency;
		double df = f0 * polka.jitter;
		
		int harmonics = hasOption("harmonics") ? option("harmonics").getInt() : 2;
		
		System.err.print(harmonics + " harmonics, ");
		
		for(int n=1; n<=harmonics; n++)	
			kill(new Range(n * (f0 - 2.0 * df), n * (f0 + 2.0 * df)));
		
		System.err.println(Util.f2.format(100.0 * (1.0 - countParms() / reject.length)) + "% pass. ");
	}

	@Override
	public String getID() {
		return "hwp";
	}

	@Override
	public String getConfigName() {
		return "filter.hwp";
	}


}
