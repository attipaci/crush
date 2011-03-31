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
package crush;

import java.util.*;

import util.data.WeightedPoint;

public class PhaseSet extends ArrayList<PhaseOffsets> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3448515171055358173L;
	
	protected Integration<?,?> integration;
	protected Hashtable<Mode, PhaseSignal> signals = new Hashtable<Mode, PhaseSignal>();

	public PhaseSet(Integration<?,?> integration) {
		this.integration = integration;		
	}
	
	public void update(ChannelGroup<?> channels) {	
		String name = "phases";
		Dependents parms = integration.dependents.containsKey(name) ? integration.dependents.get(name) : new Dependents(integration, name);
		
		for(PhaseOffsets offsets : this) offsets.update(channels, parms);	
		
		// Discard the DC component of the phases...
		for(Channel channel : channels) level(channel); 
	}
	
	public WeightedPoint[] getGainIncrement(Mode mode) {
		return signals.get(mode).getGainIncrement();
	}
	
	protected void syncGains(final Mode mode) throws IllegalAccessException {		
		signals.get(mode).syncGains();
	}
	
	
	protected void level(final Channel channel) {
		final int c = channel.index;
		double sum = 0.0, sumw = 0.0;
		for(PhaseOffsets offsets : this) if(offsets.flag == 0) {			
			sum += offsets.weight[c] * offsets.value[c];
			sumw += offsets.weight[c];
		}
		if(sumw == 0.0) return;
		final float d = (float) (sum / sumw);
		
		for(PhaseOffsets offsets : this) offsets.value[c] -= d;
	}

	public WeightedPoint getLROffset(Channel channel) {
		final WeightedPoint bias = new WeightedPoint();
		
		for(int i=size() - 1; --i > 0; ) {
			final PhaseOffsets offsets = get(i);
			if(offsets.phase != Frame.CHOP_LEFT) continue;
		
			final WeightedPoint left = get(i).getValue(channel);
			final WeightedPoint right = get(i-1).getValue(channel);
					
			right.average(get(i+1).getValue(channel));
			left.subtract(right);
		
			bias.average(left);
		}
		
		return bias;
	}
	
	
	public double getLRChi2(Channel channel, double bias) {	
		double chi2 = 0.0;
		int n = 0;
		for(int i=size() - 1; --i > 0; ) {
			final PhaseOffsets offsets = get(i);
			if(offsets.phase != Frame.CHOP_LEFT) continue;

			final WeightedPoint left = get(i).getValue(channel);
			final WeightedPoint right = get(i-1).getValue(channel);
			right.average(get(i+1).getValue(channel));
			left.subtract(right);
			left.value -= bias;

			final double chi = left.significance();
			chi2 += chi * chi;
			n++;
		}
		
		return n > 1 ? chi2/(n - 1) : Double.NaN;
	}
	
}
