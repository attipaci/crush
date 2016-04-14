/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

public abstract class ZeroMeanGains implements Serializable, GainProvider {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5916984290768232863L;
	
	private double aveG = 0.0;
	
	@Override
	public int hashCode() { return super.hashCode() ^ HashCode.from(aveG); }
	
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof ZeroMeanGains)) return false;
		if(!super.equals(o)) return false;
		ZeroMeanGains g = (ZeroMeanGains) o;
		if(aveG != g.aveG) return false;
		return true;
	}
	
	@Override
	public final double getGain(Channel c) throws Exception {
	    double g0 = getRawGain(c);
	    return Double.isNaN(g0) ? 0.0 : g0 - aveG;
	}
	
	@Override
	public final void setGain(Channel c, double value) throws Exception {
		setRawGain(c, value + aveG);
	}
	
	public abstract double getRawGain(Channel c) throws Exception;
	
	public abstract void setRawGain(Channel c, double value) throws Exception;
	
	@Override
	public void validate(Mode mode) throws Exception {	  
		double sum = 0.0, sumw = 0.0;
		
		final int skipFlags = mode instanceof CorrelatedMode ? ((CorrelatedMode) mode).skipFlags : 0;
		
		for(int k=mode.size(); --k >= 0; ) {
			final Channel channel = mode.getChannel(k);
			if(channel.isFlagged(skipFlags)) continue;

			final double x = getRawGain(channel);
			if(Double.isNaN(x)) continue;
				
			sum += channel.weight * x;
			sumw += channel.weight;
		}
		
		aveG = sumw > 0.0 ? sum / sumw : 0.0;
	}

}