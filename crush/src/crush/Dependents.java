/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

import jnum.CopiableContent;
import jnum.Util;


public class Dependents implements Serializable, Cloneable, CopiableContent<Dependents> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2489033861985258941L;
	private String name;
	private Integration<?, ?> integration;
	private float[] forFrame, forChannel;
	
	public Dependents(Integration<?, ?> owner, String name) {
		this.name = name;
		this.integration = owner;
		
		forFrame = new float[integration.size()];
		forChannel = new float[integration.instrument.size()];
			
		integration.dependents.put(name, this);
	}
	
	@Override
    protected Dependents clone() {
	    try { return (Dependents) super.clone(); }
	    catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
    public Dependents copy() {
	    return copy(true);
	}
	
	@Override
    public Dependents copy(boolean withContents) {
	    Dependents copy = clone();
	    if(name != null) copy.name = new String(name);
	    if(withContents) {
	        if(forFrame != null) copy.forFrame = Arrays.copyOf(forFrame, forFrame.length);
	        if(forChannel != null) copy.forChannel = Arrays.copyOf(forChannel, forChannel.length);
	    }
	    else {
	        if(forFrame != null) copy.forFrame = new float[forFrame.length];
	        if(forChannel != null) copy.forChannel = new float[forChannel.length];
	    }
	    return copy;	    
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof Dependents)) return false;
		if(!super.equals(o)) return false;
		Dependents d = (Dependents) o;
		if(!getName().equals(d.getName())) return false;
		if(!Util.equals(integration, d.integration)) return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		return super.hashCode() ^ integration.getDisplayID().hashCode() ^ getName().hashCode();
	}
	
	public String getName() { return name; }
	
	public void setName(String value) { this.name = value; }
	
	public Integration<?, ?> getIntegration() { return integration; }

	public final void addAsync(final Frame exposure, final double dp) { 
		forFrame[exposure.index] += dp; 
	}
	
	public final void addAsync(final Channel channel, final double dp) { 
		forChannel[channel.index] += dp;
	}
	
	
	public synchronized void addForChannels(float[] dp) {
		for(int i=forChannel.length; --i >= 0; ) forChannel[i] += dp[i];
	}
	
	public synchronized void addForFrames(float[] dp) {
		for(int i=forFrame.length; --i >= 0; ) forFrame[i] += dp[i];
	}
	
	public synchronized void addForChannels(double[] dp) {
		for(int i=forChannel.length; --i >= 0; ) forChannel[i] += dp[i];
	}
	
	public synchronized void addForFrames(double[] dp) {
		for(int i=forFrame.length; --i >= 0; ) forFrame[i] += dp[i];
	}
	
	public synchronized void clear(final Iterable<? extends Channel> channels, final int from, final int to) { 
	    int i=to;
	    
	    while(--i >= from) {
            final Frame exposure = integration.get(i);
            if(exposure != null) exposure.removeDependents(forFrame[i]);
        }
        
        for(final Channel channel : channels) channel.removeDependents(forChannel[channel.index]);
	    
		Arrays.fill(forFrame, from, to,  0.0F);
		Arrays.fill(forChannel,  0.0F);
	}
		
	public synchronized void apply(final Iterable<? extends Channel> channels, final int from, int to) {
		while(--to >= from) {
			final Frame exposure = integration.get(to);
			if(exposure != null) exposure.addDependents(forFrame[to]);
		}
		
		for(final Channel channel : channels) channel.addDependents(forChannel[channel.index]);
	}
	
	public double get(final Frame exposure) { return forFrame[exposure.index]; }
		
	public double get(final Channel channel) { return forChannel[channel.index]; }
	

}
