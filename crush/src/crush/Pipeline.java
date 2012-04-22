/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2007,2008,2009,2010 Attila Kovacs

package crush;

import java.util.*;

import util.*;

public class Pipeline extends Thread {
	Vector<Scan<?, ?>> scans = new Vector<Scan<?, ?>>();
	CRUSH crush;
	
	List<String> ordering = new ArrayList<String>();
	boolean isRobust = false, isGainRobust = false;
	
	SourceModel scanSource;
	
	public Pipeline(CRUSH crush) {
		this.crush = crush;
		if(crush.source != null) {
			scanSource = crush.source.copy();
			scanSource.noParallel();
		}
	}

	public boolean hasOption(String name) {
		return crush.isConfigured(name);
	}
	
	public Configurator option(String name) {
		return crush.get(name);
	}
	
	public void addScan(Scan<?, ?> scan) { 
		scans.add(scan); 
		for(Integration<?, ?> integration : scan) crush.queue.add(integration);
	}

	@Override
	public void run() {
		try { iterate(); }
		catch(InterruptedException e) { System.err.println("\nInterrupted!"); }
	}

	public void setOrdering(List<String> ordering) { this.ordering = ordering; }
	
	public synchronized void iterate() throws InterruptedException {					
		for(Scan<?, ?> scan : scans) iterate(scan);
	}
	
	public synchronized void iterate(Scan<?,?> scan) throws InterruptedException {		
		for(Integration<?, ?> integration: scan) integration.comments = new String();

		for(String task : ordering) if(scan.hasOption(task)) scan.perform(task);
				
		// Extract source ALWAYS at the end, independently of what was requested...
		// The supplier of the tasks should generally make sure that the source
		// is extracted at the end.
		if(ordering.contains("source")) if(scan.hasOption("source")) getSource(scan);

		for(Integration<?, ?> integration : scan) crush.checkout(integration);
	}
	
	
	protected void getSource(Scan<?,?> scan) {
		if(scanSource == null) return;
		
		scanSource.reset();
		boolean contributeSource = false;
			
		scanSource.setInstrument(scan.instrument);
		
		for(Integration<?, ?> integration: scan) {						
			boolean mapping = true;
			
			if(integration.hasOption("source.nefd")) if(!integration.option("source.nefd").getRange(true).contains(integration.nefd)) mapping = false;
			if(mapping) {
				if(integration.hasOption("jackknife")) integration.comments += integration.gain > 0.0 ? "+" : "-";
				else if(integration.gain < 0.0) integration.comments += "-";
				
				scanSource.add(integration);
				contributeSource = true;
			}
		}
			
		if(contributeSource) {
			scanSource.process(scan);	
			crush.source.add(scanSource, scan.weight);
			scanSource.postprocess(scan);
			Thread.yield();
		}
	}
	
}
	
