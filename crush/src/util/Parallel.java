package util;

import java.util.Vector;

public abstract class Parallel {

	public abstract class Process<ReturnType> implements Runnable, Cloneable {
		private Thread thread;
		private int index;
		private boolean isInterrupted = false;
		
		private Manager<ReturnType> parallel;
		private Exception exception = null;
		
		@Override
		public Object clone() {
			try { return super.clone(); }
			catch(CloneNotSupportedException e) { return null; }
		}
		
		public void process(int threadCount) throws Exception {
			parallel = new Manager<ReturnType>(this);
			parallel.process(threadCount);
			for(Process<?> process : getWorkers()) if(process.exception != null) throw process.exception;
		}
		
		public Vector<Process<ReturnType>> getWorkers() {
			return parallel.processes;
		}
		
		protected Manager<ReturnType> getManager() { return parallel; }
		
		public Thread getThread() { return thread; }
		
		public void init() {}
			
		public synchronized void interruptAll() {
			for(Process<?> process : getWorkers()) process.interrupt();
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
				System.err.println("WARNING! Parallel processing error:");
				//e.printStackTrace();
				exception = e;
				interruptAll();
			}
			// Clear the interrupt status for reuse...
			isInterrupted = false;
		}

		protected abstract void processIndex(int i, int threadCount) throws Exception;
	}
	
	
	private static class Manager<ReturnType> {
		/**
		 * 
		 */
		private Process<ReturnType> template;
		public Vector<Process<ReturnType>> processes = new Vector<Process<ReturnType>>();
		private int threadCount;
		
		private Manager(Process<ReturnType> task) {
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
				Process<ReturnType> t = (Process<ReturnType>) template.clone();
				t.setIndex(i);
				processes.add(t);
			}
			
			for(Process<?> task : processes) task.start();
			
			for(Process<?> task : processes) {
				try { 
					task.thread.join(); 
					if(task.thread.isAlive()) {
						System.err.println("WARNING! Premature conclusion of parallel image processing.");
						System.err.println("         Please notify Attila Kovacs <kovacs@astro.umn.edu>.");
						new Exception().printStackTrace();
					}
				}
				catch(InterruptedException e) { 
					System.err.println("WARNING! Parallel image processing was unexpectedly interrupted.");
					System.err.println("         Please notify Attila Kovacs <kovacs@astro.umn.edu>.");
					new Exception().printStackTrace();
				}
				
			}
			
			// Check again to make sure all tasks have been completed...
			for(Process<?> task : processes) if(task.thread.isAlive()) {
				System.err.println("!!! " + task.getClass().getSimpleName() + " still Alive...");
				new IllegalThreadStateException().printStackTrace();
			}
		}
	}
	
}
