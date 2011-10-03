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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import util.*;

import java.util.StringTokenizer;


public abstract class Channel implements Cloneable, Comparable<Channel> {
	public Instrument<?> instrument;
	
	public int index;
	public int storeIndex;
	public int flag = 0;
	public int sourcePhase = 0;
	
	public double offset = 0.0; // At the readout stage! (as of 2.03)
	public double gain = 1.0;
	public double coupling = 1.0;
	public double weight = 1.0;
	public double variance = Double.NaN;
	public double dof = 1.0;
	float temp, tempG, tempWG, tempWG2;
	
	public double dependents = 0.0;
	
	public double sourceFiltering = 1.0; // filtering on source...
	public double directFiltering = 1.0; // The effect of FFT filters on source.
	
	public double filterTimeScale = Double.POSITIVE_INFINITY;
	
	public int spikes = 0;
	
	public Channel(Instrument<?> instrument, int dataIndex) {
		this.instrument = instrument;
		this.storeIndex = dataIndex;
	}
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }		
	}
	
	// By default, channels can be sorted by backend-index
	public int compareTo(Channel channel) {
		if(channel.storeIndex == storeIndex) return 0;
		else return storeIndex < channel.storeIndex ? -1 : 1;
	}
	
	public Channel copy() {
		Channel copy = (Channel) clone();
		return copy;
	}
	
	public final boolean isFlagged(final int pattern) {
		return (flag & pattern) != 0;
	}
	
	public final boolean isUnflagged(final int pattern) {
		return (flag & pattern) == 0;
	}

	public final boolean isFlagged() {
		return flag != 0;
	}
	
	public final boolean isUnflagged() {
		return flag == 0; 
	}
	
	public final void flag(final int pattern) {
		flag |= pattern;
	}
	
	public final void unflag(final int pattern) {
		flag &= ~pattern;
	}

	public final void unflag() {
		flag = 0;
	}
	
	public double getHardwareGain() {
		return instrument.gain;
	}
	
	// Write backendIndex plus whatever information....
	@Override
	public String toString() {
		String text = storeIndex + "\t" +
			Util.f3.format(gain) + " \t" +
			Util.e3.format(weight) + " \t" +
			"0x" + Integer.toHexString(flag);
		return text;
	}
	
	public abstract double overlap(Channel channel, SourceModel<?,?> model);
	
	public int getCriticalFlags() {
		return FLAG_DEAD | FLAG_BLIND | FLAG_GAIN;
	}
	
	public void parseValues(StringTokenizer tokens) {
		parseValues(tokens, getCriticalFlags());
	}
	
	public void parseValues(StringTokenizer tokens, int criticalFlags) {
		gain = Double.parseDouble(tokens.nextToken());
		weight = Double.parseDouble(tokens.nextToken());

		// Add flags from pixel data file on top of those already specified...
		flag(criticalFlags & Integer.decode(tokens.nextToken()));	
	}
	
	public double getResolution() { return instrument.resolution; }
	
	public void uniformGains() {
		gain = 1.0; 
		coupling = 1.0;
	}
	

	public static int nextHardwareFlag = 0;
	public final static int FLAG_DEAD = 1 << nextHardwareFlag++;
	public final static int FLAG_BLIND = 1 << nextHardwareFlag++;
	public final static int HARDWARE_FLAGS = 0xFF;
	
	public static int nextSoftwareFlag = 8;
	public final static int FLAG_GAIN = 1 << nextSoftwareFlag++;
	public final static int FLAG_SENSITIVITY = 1 << nextSoftwareFlag++;
	public final static int FLAG_DOF = 1 << nextSoftwareFlag++;
	public final static int FLAG_SPIKY = 1 << nextSoftwareFlag++;
	//public final static int FLAG_DISCONTINUITY = 1 << nextSoftwareFlag++;
	public final static int FLAG_FEATURES = 1 << nextSoftwareFlag++;
	public final static int FLAG_DAC_RANGE = 1 << nextSoftwareFlag++;
	
	public final static int SOFTWARE_FLAGS = ~HARDWARE_FLAGS;
	
	
}
