/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush;

import java.io.Serializable;
import java.util.Arrays;


public class PhaseDependents implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4030721098714832147L;
	private String name;
	private PhaseSet phases;
	private double[] forPhase, forChannel;
	
	public PhaseDependents(PhaseSet owner, String name) {
		this.name = name;
		this.phases = owner;
		forPhase = new double[phases.size()];
		forChannel = new double[phases.getIntegration().instrument.size()];
		phases.phaseDeps.put(name, this);
	}

	
	public String getName() { return name; }
	
	public void setName(String value) { this.name = value; }
	
	public PhaseSet getPhases() { return phases; }
	
	public void clear(final Iterable<? extends Channel> channels) {
	    clear(channels, 0, forPhase.length);
	}
	
	private void clear(final Iterable<? extends Channel> channels, final int from, final int to) { 
		for(int i=to; --i >= from; ) {
			final PhaseData phase = phases.get(i);
			if(phase != null) phase.dependents -= forPhase[i];
		}
		for(final Channel channel : channels) phases.channelParms[channel.index] -= forChannel[channel.index];
		
		Arrays.fill(forPhase, from, to, 0.0);
		Arrays.fill(forChannel, 0.0);
	}
	
	
	public final void addAsync(final PhaseData phase, final Channel channel, final double dp) {
		forPhase[phase.index] += dp; 
		forChannel[channel.index] += dp; 
	}
	
	public final void addAsync(final PhaseData phase, final double dp) { 
		forPhase[phase.index] += dp; 
	}
	
	public final void addAsync(final Channel channel, final double dp) { 
		forChannel[channel.index] += dp; 
	}
	
	public void apply(final Iterable<? extends Channel> channels) {
	    apply(channels, 0, forPhase.length);
	}
	
	public void apply(final Iterable<? extends Channel> channels, final int from, int to) {
		while(--to >= from) {
			final PhaseData phase = phases.get(to);
			if(phase != null) phase.dependents += forPhase[to];
		}
		
		for(final Channel channel : channels) phases.channelParms[channel.index] += forChannel[channel.index];
		
	}
	
	public double get(final PhaseData phase) { return forPhase[phase.index]; }
		
	public double get(final Channel channel) { return forChannel[channel.index]; }
	
	
}

