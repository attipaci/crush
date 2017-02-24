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
// Copyright (c) 2009 Attila Kovacs 

package crush.instrument.aszca;

import crush.*;
import crush.telescope.apex.*;
import jnum.Util;
import jnum.text.SmartTokenizer;


public class AszcaPixel extends APEXContinuumPixel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4607548660132765868L;
	public double waferGain = 1.0;
	public double squidGain = 1.0;
	public double squidGroupGain = 1.0;
	public double cableGain = 1.0;
	
	public int wafer;
	public int squid;
	public int squidGroup;
	public int cable;
	public int pin;
	
	public AszcaPixel(Aszca array, int backendIndex) {
		super(array, backendIndex);
	}

	@Override
	public String toString() {
		return super.toString() + " \t" +
			Util.f3.format(waferGain) + "\t" +
			Util.f3.format(squidGain);
	}
	
	@Override
	public void parseValues(SmartTokenizer tokens, int criticalFlags) {	
		super.parseValues(tokens, criticalFlags);
		waferGain = tokens.nextDouble();
		squidGain = tokens.nextDouble();
	}	
	
	@Override
	public final double overlap(Channel channel, double pointSize) {
		return channel == this ? 1.0 : 0.0;
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		cableGain = 1.0;
		squidGain = 1.0;
		squidGroupGain = 1.0;
	}

	public final static int FLAG_WAFER = softwareFlags.next('w', "Bad wedge gain.").value();
	public final static int FLAG_SQUID = softwareFlags.next('m', "Bad SQUID gain").value();
	public final static int FLAG_SQUIDGROUP = softwareFlags.next('q', "Bad SQUID-group gain.").value();
	public final static int FLAG_CABLE = softwareFlags.next('c', "Bad cable gain").value();
}
