/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
// Copyright (c) 2009 Attila Kovacs 

package crush.instrument.saboca;

import crush.*;
import jnum.Util;
import jnum.text.SmartTokenizer;



public class SabocaPixel extends Channel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5970759467554165572L;

	public int squid = -1, pin = -1;
	public double squidGain = 1.0;

	SabocaPixel(Saboca array, int backendIndex) { 
		super(array, backendIndex); 	
	}
	
	@Override
	public void parseValues(SmartTokenizer tokens, int criticalFlags) {
		super.parseValues(tokens, criticalFlags);
		squidGain = tokens.nextDouble();
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
	
	public static final int FLAG_SQUID = softwareFlags.next('m', "Bad SQUID gain").value();
	
}
