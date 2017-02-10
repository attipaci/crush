/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.filters;

import java.util.ArrayList;
import java.util.Arrays;

import crush.Channel;
import crush.ChannelGroup;
import crush.Integration;


public class MultiFilter extends VariedFilter {
	/**
	 * 
	 */
	private static final long serialVersionUID = -9155204147970228847L;

	private ArrayList<Filter> filters = new ArrayList<Filter>();
	
	private int enabled = 0;
	
	public MultiFilter(Integration<?, ?> integration) {
		super(integration);
	}
	
	@Override
	public Object clone() {	
		MultiFilter clone = (MultiFilter) super.clone();
		clone.filters = new ArrayList<Filter>(filters.size());
		for(int i=0; i<filters.size(); i++) clone.filters.add((Filter) filters.get(i).clone());
		return clone;
	}
		
	public ArrayList<Filter> getFilters() { return filters; }
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);
		
		if(filters != null) for(Filter filter : filters) {
			filter.setTempData(getTempData());
			filter.setIntegration(integration);
		}
			
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
		final float[] data = getTempData();
		final float[] filtered = integration.getFloats();
		
		Arrays.fill(data, integration.size(), data.length, 0.0F);
		Arrays.fill(filtered, 0.0F);
		
		integration.getSequentialFFT().real2Amplitude(data);	
		data[0] = 0.0F;
		
		// Apply the filters sequentially...
		for(int n=0; n<filters.size(); n++) {
			final Filter filter = filters.get(n);
			
			if(!filter.isEnabled()) continue;
			
			// A safety check to make sure the filter uses the spectrum from the master data array...
			if(filter.getTempData() != data) filter.setTempData(data);
			
			filter.points = points;
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
		integration.getSequentialFFT().amplitude2Real(filtered);
		
		// Remove the DC component...
		if(isPedantic) levelForChannel(channel, filtered);
		
		// Subtract the rejected signal...
		final int c = channel.index;
		for(int t = integration.size(); --t >= 0; ) remove(filtered[t], integration.get(t), c);	
		
		Integration.recycle(filtered);
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
