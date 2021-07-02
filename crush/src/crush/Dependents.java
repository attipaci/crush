/* *****************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import jnum.CopiableContent;

/**
 * Dependents represent the partial degrees of freedom lost due to modeling of the data. Consider, some
 * modeled parameter <i>Z</i>, which can be expressed as a linear combination of data points <i>x<sub>i</sub></i>:
 * 
 * 
 * <i>Z = a<sub>1</sub> x<sub>1</sub> + a<sub>2</sub> x<sub>2</sub> ... a<sub>n</sub> x<sub>n</sub></i>
 * 
 * 
 * Then,
 * 
 * 
 * <i>p<sub>i</sub> = a<sub>i</sub></i> / (<i>a<sub>1</sub> x<sub>1</sub> + a<sub>2</sub> x<sub>2</sub> ... a<sub>n</sub> x<sub>n</sub></i>)
 * 
 * 
 * represents the fractional dependence of <i>Z</i> on the single datum <i>x<sub>i</sub></i> (i.e. the fractional contribution of
 * <i>x<sub>i</sub></i> to the estimate of <i>Z</i>). Consequently, the modeling of the parameter <i>Z</i>, results in
 * a partial loss of degrees of freedom <i>x<sub>i</sub></i>, by the amount of <i>p<sub>i</sub></i>.
 * 
 * 
 * CRUSH assumes that <i>p<sub>i</sub></i> can be separated into a per-channel term <i>p<sub>c</sub></i> and per-frame term <i>p<sub>t</sub></i>:
 * 
 * 
 * <i>p<sub>i</sub></i> = <i>p<sub>c</sub></i> <i>p<sub>t</sub></i>
 * 
 * 
 * Thus, the model dependents are accounted for on a per-channel and per-frame basis only. 
 * 
 * 
 * Accounting for the lost degrees of freedom becomes important in deriving unbiased estimates if the underlying 
 * noise variance, and hence crucial for the derivation of proper noise weights. Failure to keep precise
 * account of the degrees of freedom lost in the reduction will lead to weighting instabilities when iteration, which
 * will tend to grow exponentially with iterations.
 * 
 * 
 * Typically, developers of CRUSH will not have to worry much about dependents accounting, unless they are implementing
 * additional modeling capabilities for CRUSH.
 * 
 * 
 * 
 * 
 * @author Attila Kovacs
 *
 */
public class Dependents implements Serializable, Cloneable, CopiableContent<Dependents> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2489033861985258941L;
	private String name;
	private Integration<? extends Frame> integration;
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
	    IntStream.range(0, forChannel.length).parallel().forEach(i -> forChannel[i] += dp[i]);
	}
	
	public synchronized void addForFrames(float[] dp) {
	    IntStream.range(0, forFrame.length).parallel().forEach(i -> forFrame[i] += dp[i]);
	}
	
	public synchronized void addForChannels(double[] dp) {
	    IntStream.range(0, forChannel.length).parallel().forEach(i -> forChannel[i] += dp[i]);
	}
	
	public synchronized void addForFrames(double[] dp) {
	    IntStream.range(0, forFrame.length).parallel().forEach(i -> forFrame[i] += dp[i]);
	}
	
	public synchronized void clear(final List<? extends Channel> channels, final int from, final int to) { 
	    IntStream.range(from, to).parallel().mapToObj(integration::get).filter(f -> f != null)
	    .peek(f -> f.removeDependents(f.index))
	    .forEach(f -> forFrame[f.index] = 0.0F);
	    
	    channels.parallelStream()
	    .peek(c -> c.removeDependents(forChannel[c.index]))
	    .forEach(c -> forChannel[c.index] = 0.0F);
	}
		
	public synchronized void apply(final List<? extends Channel> channels, final int from, int to) {
	    IntStream.range(from, to).parallel().mapToObj(integration::get).filter(f -> f != null)
        .forEach(f -> f.addDependents(forFrame[f.index]));

        channels.parallelStream()
        .forEach(c -> c.addDependents(forChannel[c.index]));
	}
	
	public double get(final Frame exposure) { return forFrame[exposure.index]; }
		
	public double get(final Channel channel) { return forChannel[channel.index]; }
	

}
