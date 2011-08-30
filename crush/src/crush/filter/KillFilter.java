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

package crush.filter;

import crush.Channel;
import crush.Integration;

public abstract class KillFilter extends Filter {
	boolean[] reject;
	int components = 0;
	
	// TODO noiseFiltering to be replaced by Dependents accounting...
	protected double noiseFiltering = 1.0, pointSourceThroughput = 1.0;
	
	KillFilter(Integration<?,?> integration) {
		super(integration);
	}
	
	protected KillFilter(Integration<?,?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);
		reject = new boolean[nf+1];
	}
	
	public void kill(double freq, double deltaf) {
		freq = Math.abs(freq);
		deltaf = Math.abs(deltaf);
		
		int fromf = Math.max(0, (int) Math.floor((freq - deltaf) / df));
		int tof = Math.min(nf, (int) Math.ceil((freq + deltaf) / df));
		
		for(int f=fromf; f<=tof; f++) {
			if(!reject[f]) components++;
			reject[f] = true;
		}
		
		autoDFT();
	}
	
	@Override
	public void filter(Channel channel) {
		// TODO How to do this better with applied rejection...
		// Discount the effect of the prior filtering...
		channel.noiseWhitening /= noiseFiltering;
		channel.directFiltering /= pointSourceThroughput;
		
		super.filter(channel);
		
		noiseFiltering = getNoiseWhitening();
		pointSourceThroughput = getPointSourceThroughput();
		
		// Apply the effect of the current filtering...
		channel.noiseWhitening *= noiseFiltering;
		channel.directFiltering *= pointSourceThroughput;
		
	}
	
	public void autoDFT() {
		// DFT 51 ops per datum, per rejected complex frequency...
		int dftreq = 51 * components * integration.size();
		
		// FFT with 31 ops each loop, 9.5 ops per datum, 34.5 ops per datum rearrange...
		int fftreq = 31 * (int) Math.round(Math.log(data.length) / Math.log(2.0)) * data.length + 44 * data.length; 
	
		setDFT(dftreq < fftreq);
	}
		
	@Override
	public double throughputAt(int fch) {
		if(fch == reject.length) return reject[1] ? 0.0 : 1.0;
		return reject[fch] ? 0.0 : 1.0;		
	}

	@Override
	public double countParms() {
		int n = 0;
		for(int i=getMinIndex(); i<reject.length; i++) if(reject[i]) n++;
		return n;
	}

	
}
