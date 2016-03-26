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

import java.io.Serializable;

import jnum.util.HashCode;

public abstract class GradientGains implements Serializable, GainProvider {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5916984290768232863L;
	
	private double center = 0.0;
	
	@Override
	public int hashCode() { return super.hashCode() ^ HashCode.from(center); }
	
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof GradientGains)) return false;
		if(!super.equals(o)) return false;
		GradientGains g = (GradientGains) o;
		if(center != g.center) return false;
		return true;
	}
	
	@Override
	public final double getGain(Channel c) throws Exception {
		return getRawGain(c) - center;
	}
	
	@Override
	public final void setGain(Channel c, double value) throws Exception {
		setRawGain(c, center + value);
	}
	
	public abstract double getRawGain(Channel c) throws Exception;
	
	public abstract void setRawGain(Channel c, double value) throws Exception;
	
	@Override
	public void validate(Mode mode) throws Exception {	  
		final float[] gains = mode.getGains(false);

		double sum = 0.0, sumw = 0.0;
		for(int k=gains.length; --k >= 0; ) {
			final Channel channel = mode.getChannel(k);
			if(channel.isFlagged()) continue;

			final double x = getGain(channel);
			
			if(x == 0.0) continue;
			else if(Double.isNaN(x)) continue;
			
			final double g = gains[k] / x;
			final double wg2 = channel.weight * g * g;
			
			sum += wg2 * x;
			sumw += wg2;
		}
		
		if(sumw > 0.0) center += sum / sumw;
	}

}
