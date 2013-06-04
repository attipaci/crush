/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package kovacs.util;

import java.util.Vector;

public abstract class Parallel<ReturnType> implements Runnable, Cloneable {
	private Thread thread;
	private int index;
	private boolean isInterrupted = false;
	
	private Manager parallel;
	private Exception exception = null;
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public void process(int threadCount) throws Exception {
		parallel = new Manager(this);
		parallel.process(threadCount);
		for(Parallel<?> process : getWorkers()) if(process.exception != null) throw process.exception;
	}
	
	public Vector<Parallel<ReturnType>> getWorkers() {
		return parallel.processes;
	}
	
	protected Manager getManager() { return parallel; }
	
	public Thread getThread() { return thread; }
	
	public void init() {}
		
	public synchronized void interruptAll() {
		for(Parallel<?> process : getWorkers()) process.interrupt();
	}
	
	private synchronized void interrupt() {
		isInterrupted = true;
		notifyAll(); // Notify all blocked operations to make them aware of the interrupt.
	}
	
	protected boolean isInterrupted() { return isInterrupted; }
	
	private void setIndex(int index) {
		if(thread != null) if(thread.isAlive()) 
			throw new IllegalThreadStateException("Cannot change task index while running.");
		this.index = index;
	}
	
	public int getIndex() { return index; }
	
	public ReturnType getPartialResult() {
		return null;
	}
	
	public ReturnType getResult() {
		return null;
	}	
	
	public void start() {
		if(thread != null) if(thread.isAlive())
			throw new IllegalThreadStateException("Current thread is still running.");
		thread = new Thread(this);
		thread.start();		
	}
	
	public final void run() {
		// clear the exception for reuse...
		exception = null;
		init();
		try { 
			// Don't even start in case it has been interrupted already
			if(!isInterrupted()) processIndex(index, parallel.getThreadCount()); 	
		}
		catch(Exception e) {
			System.err.println("WARNING! Parallel processing error.");
			//e.printStackTrace();
			exception = e;
			interruptAll();
		}
		// Clear the interrupt status for reuse...
		isInterrupted = false;
	}

	protected abstract void processIndex(int i, int threadCount) throws Exception;
	
	
	private class Manager {
		/**
		 * 
		 */
		private Parallel<ReturnType> template;
		public Vector<Parallel<ReturnType>> processes = new Vector<Parallel<ReturnType>>();
		private int threadCount;
		
		private Manager(Parallel<ReturnType> task) {
			this.template = task;
		}
		
		private int getThreadCount() { return threadCount; }
		
		private synchronized void process(int threadCount) {
			this.threadCount = threadCount;
			processes.ensureCapacity(threadCount);
			
			// Use only copies of the task for calculation, leaving the template
			// task in its original state, s.t. it may be reused again...
			for(int i=0; i<threadCount; i++) {
				@SuppressWarnings("unchecked")
				Parallel<ReturnType> t = (Parallel<ReturnType>) template.clone();
				t.setIndex(i);
				processes.add(t);
			}
			
			for(Parallel<?> task : processes) task.start();
			
			for(Parallel<?> task : processes) {
				try { 
					task.thread.join(); 
					if(task.thread.isAlive()) {
						System.err.println("WARNING! Premature conclusion of parallel processing.");
						System.err.println("         Please notify Attila Kovacs <attila@submm.caltech.edu>.");
						new Exception().printStackTrace();
					}
				}
				catch(InterruptedException e) { 
					System.err.println("WARNING! Parallel processing was unexpectedly interrupted.");
					System.err.println("         Please notify Attila Kovacs <attila@submm.caltech.edu>.");
					new Exception().printStackTrace();
				}
				
			}
			
			// Check again to make sure all tasks have been completed...
			for(Parallel<?> task : processes) if(task.thread.isAlive()) {
				System.err.println("!!! " + task.getClass().getSimpleName() + " still Alive...");
				new IllegalThreadStateException().printStackTrace();
			}
		}
	}

}
