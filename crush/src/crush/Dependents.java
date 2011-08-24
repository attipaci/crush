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

import java.util.Arrays;


public class Dependents {
	String name;
	Integration<?, ?> integration;
	private double[] forFrame, forChannel;
	
	public Dependents(Integration<?, ?> owner, String name) {
		this.name = name;
		this.integration = owner;
		forFrame = new double[integration.size()];
		forChannel = new double[integration.instrument.size()];
		integration.dependents.put(name, this);
	}
	
	public void clear(final Iterable<? extends Channel> channels, final int from, int to) { 
		while(--to >= from) {
			final Frame exposure = integration.get(to);
			if(exposure != null) exposure.dependents -= forFrame[to];
		}
		for(final Channel channel : channels) channel.dependents -= forChannel[channel.index];
		
		Arrays.fill(forFrame, 0.0);
		Arrays.fill(forChannel, 0.0);
	}
	
	public final void clear(final Frame frame) {
		frame.dependents -= forFrame[frame.index];
		forFrame[frame.index] = 0.0; 
	}
	
	public final void clear(final Channel channel) { 
		channel.dependents -= forChannel[channel.index];
		forChannel[channel.index] = 0.0; 
	}
	
	public final void add(final Frame exposure, final Channel channel, final double dp) {
		forFrame[exposure.index] += dp; 
		forChannel[channel.index] += dp; 
	}
	
	public final void add(final Frame exposure, final double dp) { 
		forFrame[exposure.index] += dp; 
	}
	
	public final void add(final Channel channel, final double dp) { 
		forChannel[channel.index] += dp; 
	}
	
	public void apply(final Iterable<? extends Channel> channels, final int from, int to) {
		while(--to >= from) {
			final Frame exposure = integration.get(to);
			if(exposure != null) exposure.dependents += forFrame[to];
		}
		
		for(final Channel channel : channels) channel.dependents += forChannel[channel.index];
		
	}
	
	public double get(final Frame exposure) { return forFrame[exposure.index]; }
		
	public double get(final Channel channel) { return forChannel[channel.index]; }
	
	
}
