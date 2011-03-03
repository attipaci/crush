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

import crush.*;

public class TwistingMode extends CorrelatedMode {

	public TwistingMode() {
		fixedGains = true;
	}

	@Override
	public float[] getGains() throws IllegalAccessException {
		float[] gains = super.getGains();

		double sumwg = 0.0, sumw = 0.0;
		for(Channel channel : channels) {
			LabocaPixel pixel = (LabocaPixel) channel;
			if(pixel.flag == 0) {
				sumwg += pixel.weight * pixel.cableGain * pixel.pin;
				sumw += pixel.weight;
			}
		}
		float aveg = sumw > 0.0 ?  (float) (sumwg / sumw) : 0.0F;

		for(int c=0; c<channels.size(); c++) {
			LabocaPixel pixel = (LabocaPixel) channels.get(c);
			gains[c] = (float) pixel.cableGain * pixel.pin - aveg;
		}

		return gains;
	}
}
