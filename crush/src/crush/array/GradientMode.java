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
// Copyright (c) 2009 Attila Kovacs 

package crush.array;

import crush.Channel;
import crush.ChannelGroup;
import crush.CorrelatedMode;

public class GradientMode extends CorrelatedMode {
	
	public GradientMode(ChannelGroup<?> group, boolean isHorizontal) {
		super(group);
		this.horizontal = isHorizontal;
		fixedGains = true;
		name = group.name + "-" + (horizontal ? "x" : "y");
	}

	boolean horizontal = true;

	@Override
	public float[] getGains() throws IllegalAccessException {
		float[] gains = super.getGains();

		double aveg = 0.0, sumw = 0.0;
		for(Channel channel : channels) {
			SimplePixel pixel = (SimplePixel) channel;
			if(pixel.flag == 0) {
				aveg += pixel.weight * (horizontal ? pixel.position.x : pixel.position.y);
				sumw += pixel.weight;
			}
		}
		if(sumw > 0.0) aveg /= sumw;

		for(int c=0; c<channels.size(); c++) {
			SimplePixel pixel = (SimplePixel) channels.get(c);
			gains[c] = (float)((horizontal ? pixel.position.x : pixel.position.y) - aveg);
		}

		return gains;
	}
}

