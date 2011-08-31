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

package crush.filters;

import java.util.ArrayList;
import java.util.Arrays;

import util.data.FFT;

import crush.Channel;
import crush.Frame;
import crush.Integration;

// TODO integration should have the initialized filters, from 'filter.ordering'...

public class CompoundFilter extends AdaptiveFilter {
	ArrayList<Filter> filters = new ArrayList<Filter>();
	float[] filtered;
	
	public CompoundFilter(Integration<?, ?> integration) {
		super(integration);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);
		for(Filter filter : filters) {
			filter.data = data;
			filter.setIntegration(integration);
		}
		
		filtered = new float[data.length];
	}
	
	
	public synchronized int size() {
		return filters.size();
	}
	
	public synchronized boolean contains(Filter filter) {
		return filters.contains(filter);
	}
	
	public synchronized void addFilter(Filter filter) {
		if(filter.integration == null) filter.setIntegration(integration);
		else if(filter.integration != integration) 
			throw new IllegalStateException("Cannot compound filter from a different integration.");
		filters.add(filter);
	}

	public synchronized void setFilter(int i, Filter filter) {
		if(filter.integration == null) filter.setIntegration(integration);
		else if(filter.integration != integration) 
			throw new IllegalStateException("Cannot compound filter from a different integration.");
		filters.set(i, filter);
	}
	
	public synchronized void remove(Filter filter) {
		filters.remove(filter);
	}
	
	public synchronized Filter remove(int i) {
		return filters.remove(i);
	}
	
	@Override
	public String getID() {
		return "f";
	}
	
	@Override
	public void updateConfig() {
		super.updateConfig();
		for(Filter filter : filters) filter.updateConfig();
	}
	
	@Override
	protected synchronized void apply(Channel channel) {
		// If single filter, then just do it it's own way...
		if(filters.size() == 1) filters.get(0).apply(channel);
		else super.apply(channel);
	}
	
	@Override
	protected synchronized void fftFilter(Channel channel) {		
		Arrays.fill(data, integration.size(), data.length, 0.0F);
		Arrays.fill(filtered, 0.0F);
		
		FFT.forwardRealInplace(data);
		
		data[0] = 0.0F;
		
		// Apply the filters sequentially...
		for(Filter filter : filters) {
			if(filter instanceof AdaptiveFilter) ((AdaptiveFilter) filter).updateProfile(channel);
		
			final float nyquistReject = (float) rejectionAt(nf);
			filtered[1] = data[1] * nyquistReject;
			data[1] *= (1.0 - nyquistReject);
		
			for(int i=2; i<data.length; ) {
				final float rejection = (float) rejectionAt(i >> 1);
			
				// Apply the filter to the real part...
				filtered[i] = rejection * data[i];
				data[i++] *= (1.0 - rejection);
				
				// Apply the filter to the imaginary part
				filtered[i] = rejection * data[i];
				data[i++] *= (1.0 - rejection);
			}
		}
		
		// Convert to rejected signal...
		FFT.backRealInplace(filtered);
		
		// Remove the DC component...
		levelFor(channel, filtered);
		
		// Subtract the rejected signal...
		final int c = channel.index;
		for(int t = integration.size(); --t >= 0; ) {
			final Frame exposure = integration.get(t);
			if(exposure != null) exposure.data[c] -= filtered[t];	
		}
		
	}
	
	@Override
	protected double responseAt(int fch) {
		double pass = 1.0;
		for(Filter filter : filters) {
			pass *= filter.responseAt(fch);
			// Once the filtering is total, there is no need to continue compounding...
			if(pass == 0.0) return 0.0;
		}
		return pass;
	}

	// only effective above 1/f cutoff freq...
	@Override
	protected double countParms() {
		final int minf = getMinIndex();
		double parms = 0.0;
		for(int f = nf; --f >= minf; ) parms += rejectionAt(f);
		return parms;
	}

	@Override
	public String getConfigName() {
		return "filter";
	}

	@Override
	protected void updateProfile(Channel channel) {
		// Nothing to do...
	}

}
