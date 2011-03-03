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
package util;

import java.util.*;

public abstract class Parallel<Type> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3231222039535248732L;
	int maxThreads;
	private Vector<ProcessingThread> queue = new Vector<ProcessingThread>();
	
	public Parallel(int maxThreads) {
		this.maxThreads = maxThreads;
		init();
	}
		
	public void init() {};
	
	public ProcessingThread createThread(int chunkSize) {
		return new ProcessingThread(chunkSize);
	}
	
	public synchronized void process(Collection<Type> data) throws InterruptedException {
		// Limit the threads to the available data
		final int threads = Math.min(maxThreads, data.size());
		
		if(!queue.isEmpty()) throw new IllegalStateException("Queue not empty.");
		
		queue.ensureCapacity(threads);
		int chunkSize = (int) Math.ceil((double) data.size() / threads);
			
		for(int i=0; i<threads; i++) queue.add(createThread(chunkSize));
		divide(data, queue);
		
		for(ProcessingThread thread : queue) new Thread(thread).start();
		
		while(!queue.isEmpty()) wait();
	}
	
	protected void divide(Collection<Type> data, Vector<ProcessingThread> processors) {
		int i=0;
		int threads = processors.size();
		for(Type entry : data) {
			processors.get(i++).add(entry);
			i %= threads; 
		}		
	}
	
	private synchronized void checkout(ProcessingThread thread) {
		queue.remove(thread);
		notifyAll();
		//System.err.println("### Finished processing...");
	}
	
	public abstract void process(Type data, ProcessingThread thread);
	
	public class ProcessingThread extends ArrayList<Type> implements Runnable {			
		/**
		 * 
		 */
		private static final long serialVersionUID = -3973614679104705385L;
		
		public ProcessingThread(int capacity) { super(capacity); }
	
		public void run() {
			//System.err.println("### Starting processing " + size() + " item(s).");
			for(Type entry : this) process(entry, this);
			checkout(this);
		}
	}
	
	
}
