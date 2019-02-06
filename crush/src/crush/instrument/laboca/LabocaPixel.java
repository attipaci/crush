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
// Copyright (c) 2007 Attila Kovacs 

package crush.instrument.laboca;

import crush.*;
import jnum.Util;
import jnum.text.SmartTokenizer;


public class LabocaPixel extends Channel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 5745477721698573346L;
	public int box = -1;
	public int amp = -1;
	public int cable = -1;
	public int pin = -1;

	public double boxGain = 1.0;
	public double ampGain = 1.0;
	public double cableGain = 1.0;
	
	public double temperatureGain = 0.0;
	
	public LabocaPixel(Laboca array, int backendChannel) {
		super(array, backendChannel);
	}
	
	@Override
	public String toString() {
		return super.toString() + "\t" + 
			Util.f3.format(boxGain) + "\t" +
			Util.f3.format(cableGain) + "\t" +
			box + "\t" + cable + "\t" + amp;
	}
	
	@Override
	public void parseValues(SmartTokenizer tokens, int criticalFlags) {	
		super.parseValues(tokens, criticalFlags);

		boxGain = tokens.nextDouble();
		cableGain = tokens.nextDouble();
		
		// Make sure resistors are also flagged as blind... 
		//if(isFlagged(FLAG_RESISTOR)) flag(FLAG_BLIND);

		/*
		channel[ch].box = tokens.nextInt();
		channel[ch].cable = tokens.nextint();
		channel[ch].amp = tokens.nextInt();
		*/
	}
	
	@Override
	public final double overlap(Channel channel, double pointSize) {
		return channel == this ? 1.0 : 0.0;
	}
	
	@Override
	public int getCriticalFlags() {
		return super.getCriticalFlags() | FLAG_RESISTOR;
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		ampGain = 1.0;
		boxGain = 1.0;
		cableGain = 1.0;
	}
	
	public final static int FLAG_RESISTOR = softwareFlags.next('R', "Resistor").value();
	
	public final static int FLAG_CABLE = softwareFlags.next('c', "Bad cable gain").value();
	public final static int FLAG_BOX = softwareFlags.next('b', "Bad amplifier box gain").value();
	public final static int FLAG_AMP = softwareFlags.next('a', "Bad amp gain").value();
}
