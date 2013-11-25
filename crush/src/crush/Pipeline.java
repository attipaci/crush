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
// Copyright (c) 2007,2008,2009,2010 Attila Kovacs

package crush;

import java.util.*;

import kovacs.util.*;


public class Pipeline extends Thread {
	Vector<Scan<?, ?>> scans = new Vector<Scan<?, ?>>();
	CRUSH crush;
	
	List<String> ordering = new ArrayList<String>();
	boolean isRobust = false, isGainRobust = false;
	
	SourceModel scanSource;
	boolean isReused = false;
	
	public Pipeline(CRUSH crush) {
		this.crush = crush;
		scanSource = crush.source.copy(false);
		scanSource.noParallel();
		scanSource.reset(false);
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
		catch(Exception e) { 
			System.err.println("ERROR! " + e.getMessage()); 
			e.printStackTrace();
			System.err.println("Exiting.");
			System.exit(1);
		}
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
		if(crush.source == null) return;
	
		// Reset smoothing etc. for raw map.
		if(scanSource == null) {
			scanSource = crush.source.copy(false);
			scanSource.noParallel();
			scanSource.reset(false);
		}
		else scanSource.reset(true);
		
		// TODO why doesn't this work...
		scanSource.setInstrument(scan.instrument);
		
		for(Integration<?, ?> integration: scan) {						
			if(integration.hasOption("jackknife")) integration.comments += integration.gain > 0.0 ? "+" : "-";
			else if(integration.gain < 0.0) integration.comments += "-";
			scanSource.add(integration);
		}		

		scanSource.process(scan);	
		crush.source.add(scanSource, scan.weight);	
		scanSource.postprocess(scan);
		
		Thread.yield();
	}
	
}
	
