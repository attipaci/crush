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

package crush;


public class Dependents {
	private String name;
	private Integration<?, ?> integration;
	private Entry[] forFrame, forChannel;
	
	public Dependents(Integration<?, ?> owner, String name) {
		this.name = name;
		this.integration = owner;
		
		forFrame = new Entry[integration.size()];
		for(int i=forFrame.length; --i >= 0; ) forFrame[i] = new Entry();
		
		forChannel = new Entry[integration.instrument.size()];
		for(int i=forChannel.length; --i >= 0; ) forChannel[i] = new Entry();
		
		integration.dependents.put(name, this);
	}
	
	public String getName() { return name; }
	
	public void setName(String value) { this.name = value; }
	
	public Integration<?, ?> getIntegration() { return integration; }
	
	public void clear(final Iterable<? extends Channel> channels, final int from, int to) { 
		while(--to >= from) forFrame[to].clearFrom(integration.get(to));
		for(final Channel channel : channels) forChannel[channel.index].clearFrom(channel);
	}
	
	public final void clear(final Frame frame) { forFrame[frame.index].clearFrom(frame); }
	
	public final void clear(final Channel channel) { forChannel[channel.index].clearFrom(channel); }
	
	public final void add(final Frame exposure, final Channel channel, final double dp) {
		forFrame[exposure.index].increment(dp); 
		forChannel[channel.index].increment(dp); 
	}
	
	public final void add(final Frame exposure, final double dp) { 
		forFrame[exposure.index].increment(dp); 
	}
	
	public final void addForFrame(final int index, final double dp) { 
		forFrame[index].increment(dp);
	}
	
	
	public final void add(final Channel channel, final double dp) { 
		forChannel[channel.index].increment(dp);
	}
	
	public final void addForChannel(final int index, final double dp) { 
		forChannel[index].increment(dp); 
	}
	
	public void apply(final Iterable<? extends Channel> channels, final int from, int to) {
		while(--to >= from) {
			final Frame exposure = integration.get(to);
			if(exposure != null) exposure.addDependents(forFrame[to].getValue());
		}
		
		for(final Channel channel : channels) channel.addDependents(forChannel[channel.index].getValue());
	}
	
	public double get(final Frame exposure) { return forFrame[exposure.index].getValue(); }
		
	public double get(final Channel channel) { return forChannel[channel.index].getValue(); }
	

	public static class Entry {
		private double value = 0.0;
		
		public final synchronized void increment(double dp) { value += dp; }
		
		public final synchronized void decrement(double dp) { value -= dp; }
		
		public final synchronized double getValue() { return value; }
		
		public final synchronized void clear() { value = 0.0; }
		
		public final synchronized void clearFrom(Frame exposure) { 
			if(exposure != null) exposure.removeDependents(value);
			value = 0.0;
		}
		
		public final synchronized void clearFrom(Channel channel) { 
			channel.removeDependents(value);
			value = 0.0;
		}	
	}
}
