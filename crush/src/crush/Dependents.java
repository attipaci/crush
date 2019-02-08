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

/**
 * Dependents represent the partial degrees of freedom lost due to modeling of the data. Consider, some
 * modeled parameter <i>Z</i>, which can be expressed as a linear combination of data points <i>x<sub>i</sub></i>:
 * <p>
 * 
 * <i>Z = a<sub>1</sub> x<sub>1</sub> + a<sub>2</sub> x<sub>2</sub> ... a<sub>n</sub> x<sub>n</sub></i>
 * <p>
 * 
 * Then,
 * <p>
 * 
 * <i>p<sub>i</sub> = a<sub>i</sub></i> / (<i>a<sub>1</sub> x<sub>1</sub> + a<sub>2</sub> x<sub>2</sub> ... a<sub>n</sub> x<sub>n</sub></i>)
 * <p>
 * 
 * represents the fractional dependence of <i>Z</i> on the single datum <i>x<sub>i</sub></i> (i.e. the fractional contribution of
 * <i>x<sub>i</sub></i> to the estimate of <i>Z</i>). Consequently, the modeling of the parameter <i>Z</i>, results in
 * a partial loss of degrees of freedom <i>x<sub>i</sub></i>, by the amount of <i>p<sub>i</sub></i>.
 * <p>
 * 
 * CRUSH assumes that <i>p<sub>i</sub></i> can be separated into a per-channel term <i>p<sub>c</sub></i> and per-frame term <i>p<sub>t</sub></i>:
 * <p>
 * 
 * <i>p<sub>i</sub></i> = <i>p<sub>c</sub></i> <i>p<sub>t</sub></i>
 * <p>
 * 
 * Thus, the model dependents are accounted for on a per-channel and per-frame basis only. 
 * <p>
 * 
 * Accounting for the lost degrees of freedom becomes important in deriving unbiased estimates if the underlying 
 * noise variance, and hence crucial for the derivation of proper noise weights. Failure to keep precise
 * account of the degrees of freedom lost in the reduction will lead to weighting instabilities when iteration, which
 * will tend to grow exponentially with iterations.
 * <p>
 * 
 * Typically, developers of CRUSH will not have to worry much about dependents accounting, unless they are implementing
 * additional modeling capabilities for CRUSH.
 * <p>
 * 
 * 
 * 
 * @author Attila Kovacs <attila@sigmyne.com>
 *
 */
public class Dependents implements Serializable, Cloneable, CopiableContent<Dependents> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2489033861985258941L;
	private String name;
	private Integration<?> integration;
	private float[] forFrame, forChannel;
	
	public Dependents(Integration<?> owner, String name) {
		this.name = name;
		this.integration = owner;
		
		forFrame = new float[integration.size()];
		forChannel = new float[integration.getInstrument().size()];
			
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
		
	public String getName() { return name; }
	
	public void setName(String value) { this.name = value; }
	
	public Integration<?> getIntegration() { return integration; }

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
