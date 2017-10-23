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
import java.util.*;

import jnum.Configurator;



public class Pipeline implements Runnable, Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4031114728363827458L;

	CRUSH crush;
	
	List<Scan<?,?>> scans = new ArrayList<Scan<?,?>>();
	List<String> ordering = new ArrayList<String>();
	SourceModel scanSource;
	
	
	private int threadCount;
	
	public Pipeline(CRUSH crush, int threadCount) {
		this.crush = crush;
		this.threadCount = threadCount;
	}
	
	public void setSourceModel(SourceModel source) {	    
	    if(source != null) { 
	        scanSource = crush.source.getWorkingCopy(false);
	        scanSource.setExecutor(null);  // TODO use executor?
	        scanSource.setParallel(threadCount);
	    }
	    else scanSource = null;
	}
	
	public int getThreadCount() { return threadCount; }

	public boolean hasOption(String name) {
		return crush.isConfigured(name);
	}
	
	public Configurator option(String name) {
		return crush.get(name);
	}
	
	public void setOrdering(List<String> ordering) { this.ordering = ordering; }
	
	@Override
	public void run() {
		try { iterate(); }
		catch(InterruptedException e) { CRUSH.warning(this, "Interrupted!"); }
		catch(Exception e) { 
			CRUSH.error(this, e); 
			CRUSH.info(this, "Exiting.");
			System.exit(1);
		}
	}
	
	
	public void iterate() throws InterruptedException {	
		for(int i=0; i<scans.size(); i++) iterate(scans.get(i));
	}
	
	private void iterate(Scan<?,?> scan) throws InterruptedException {	
		for(Integration<?, ?> integration: scan) {
		    integration.nextIteration();
			integration.setThreadCount(threadCount);
		}

		for(int i=0; i < ordering.size(); i++) {
			final String task = ordering.get(i);
			if(scan.hasOption(task)) scan.perform(task);
		}
			
		// Extract source ALWAYS at the end, independently of what was requested...
		// The supplier of the tasks should generally make sure that the source
		// is extracted at the end.
		if(ordering.contains("source")) if(scan.hasOption("source")) updateSource(scan);

		for(Integration<?, ?> integration: scan) crush.checkout(integration);
	}
	
	private void updateSource(Scan<?,?> scan) {	
	      
		if(crush.source == null) return;
				
		// Reset smoothing etc. for raw map.
		scanSource.renew();		
		
		scanSource.setInstrument(scan.instrument);
		
		for(Integration<?, ?> integration: scan) {						
			if(integration.hasOption("jackknife")) integration.comments += integration.gain > 0.0 ? "+" : "-";
			else if(integration.gain < 0.0) integration.comments += "-";
			scanSource.add(integration);
		}		
		
		if(scan.getSourceGeneration() > 0) scanSource.enableLevel = false;
       
		scanSource.process(scan);	
		
		crush.source.add(scanSource, scan.weight);
	
		scanSource.postProcess(scan);
	}
	

	
}
	
