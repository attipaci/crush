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
import crush.ChannelGroup;
import crush.Frame;
import crush.Integration;


public class MultiFilter extends VariedFilter {
	ArrayList<Filter> filters = new ArrayList<Filter>();
	float[] filtered;
	
	private int enabled = 0;
	
	public MultiFilter(Integration<?, ?> integration) {
		super(integration);
	}
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);
		
		if(filters != null) for(Filter filter : filters) {
			filter.data = data;
			filter.setIntegration(integration);
		}
		
		filtered = new float[data.length];
		
		updateSourceProfile();
	}
	
	
	@Override
	public void setChannels(ChannelGroup<?> channels) {
		super.setChannels(channels);
		if(filters != null) for(Filter filter : filters) filter.setChannels(channels);		
	}
	
	public synchronized int size() {
		return filters.size();
	}
	
	public synchronized boolean contains(Filter filter) {
		return filters.contains(filter);
	}
	
	public synchronized boolean contains(Class<? extends Filter> filterClass) {
		for(Filter filter : filters) if(filter.getClass().equals(filterClass)) return true;
		return false;
	}
	
	public Filter get(Class<? extends Filter> filterClass) {
		for(Filter filter : filters) if(filter.getClass().equals(filterClass)) return filter;
		return null;
	}
	
	public synchronized void addFilter(Filter filter) {
		if(filter.integration == null) filter.setIntegration(integration);
		else if(filter.integration != integration) 
			throw new IllegalStateException("Cannot compound filter from a different integration.");
		filter.setChannels(getChannels());
		filters.add(filter);
	}

	public synchronized void setFilter(int i, Filter filter) {
		if(filter.integration == null) filter.setIntegration(integration);
		else if(filter.integration != integration) 
			throw new IllegalStateException("Cannot compound filter from a different integration.");
		filter.setChannels(getChannels());
		filters.set(i, filter);
	}
	
	public synchronized void remove(Filter filter) {
		filters.remove(filter);
	}
	
	public synchronized Filter remove(int i) {
		return filters.remove(i);
	}
	
	
	@Override
	public void updateConfig() {
		super.updateConfig();
		for(Filter filter : filters) filter.updateConfig();			
	}
	
	@Override 
	public boolean isEnabled() {
		if(!super.isEnabled()) return false;
		
		enabled = 0;
		for(Filter filter : filters) if(filter.isEnabled()) enabled++;
		return enabled > 0;
	}
	
	/*
	@Override
	public boolean apply(boolean report) {
		updateConfig();
		
		if(enabled > 1) return super.apply(report);
		else for(Filter filter : filters) if(filter.isEnabled()) return filter.apply(report);
		return false;
	}
	*/
	
	@Override
	protected void preFilter() {
		super.preFilter();
		for(Filter filter : filters) if(filter.isEnabled()) filter.preFilter();		
	}
	
	@Override
	protected void postFilter() {
		for(Filter filter : filters) if(filter.isEnabled()) filter.postFilter();	
		super.postFilter();
	}
	
	@Override
	protected synchronized void fftFilter(Channel channel) {				
		Arrays.fill(data, integration.size(), data.length, 0.0F);
		Arrays.fill(filtered, 0.0F);
		
		FFT.forwardRealInplace(data);	
		data[0] = 0.0F;
		
		// Apply the filters sequentially...
		for(int n=0; n<filters.size(); n++) {
			final Filter filter = filters.get(n);
			
			if(!filter.isEnabled()) continue;
			
			// A safety check to make sure the filter uses the spectrum from the master data array...
			if(filter.data != data) filter.data = data;
			
			filter.preFilter(channel);
			filter.updateProfile(channel);
			
			final float nyquistPass = (float) filter.responseAt(nf);
			filtered[1] = data[1] * (1.0F - nyquistPass);
			data[1] *= nyquistPass;
		
			for(int i=2; i<data.length; ) {
				final float pass = (float) filter.responseAt(i >> 1);
			
				// Apply the filter to the real part...
				filtered[i] = (1.0F - pass) * data[i];
				data[i++] *= pass;
				
				// Apply the filter to the imaginary part
				filtered[i] = (1.0F - pass) * data[i];
				data[i++] *= pass;
			}
			
			filter.postFilter(channel);
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
		for(Filter filter : filters) if(filter.isEnabled()) {
			pass *= filter.responseAt(fch);
			// Once the filtering is total, there is no need to continue compounding...
			if(pass == 0.0) return 0.0;
		}
		return pass;
	}

	
	@Override
	public String getID() {
		String id = "";
		for(Filter filter : filters) if(filter.isEnabled()) {
			if(id.length() != 0) id += ":";
			id += filter.getID(); 
		}
		return id;
	}

	@Override
	public String getConfigName() {
		return "filter";
	}

}
