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
// Copyright (c) 2009 Attila Kovacs 

package crush.saboca;

import crush.*;
import crush.apex.APEXPixel;

import java.util.StringTokenizer;

import kovacs.util.Util;



public class SabocaPixel extends APEXPixel {
	public double squidGain = 1.0;
	
	public int squid;
	public int pin;
	
	public SabocaPixel(Saboca array, int backendIndex) { 
		super(array, backendIndex); 	
	}
	
	@Override
	public void parseValues(StringTokenizer tokens) {
		super.parseValues(tokens);
		squidGain = Double.parseDouble(tokens.nextToken());
	}

	@Override
	public String toString() {
		return super.toString() + "\t" + Util.f3.format(squidGain);
	}
	
	@Override
	public final double overlap(Channel channel, double pointSize) {
		return channel == this ? 1.0 : 0.0;
	}

	@Override
	public void uniformGains() {
		super.uniformGains();
		squidGain = 1.0;
	}
	
	public final static int FLAG_SQUID = 1 << nextSoftwareFlag++;
	
}
