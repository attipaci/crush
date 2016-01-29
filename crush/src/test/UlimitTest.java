package test;


import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class UlimitTest {
	private int activeCount = 0;
	
	public static void main(String[] args) {
		int tasks = args.length > 0 ? Integer.parseInt(args[0]) : 100000;
		int parallel = args.length > 1 ? Integer.parseInt(args[1]) : 4;
		
		UlimitTest tester = new UlimitTest();
		tester.test(tasks, parallel);	
	}
	
	public void test(int nTasks, int nParallel) {
		
		
		for(int i=0; i<nTasks; i++) {
			System.err.println(i);
			ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(nParallel);
			pool.prestartAllCoreThreads();
			for(int k=0; k<nParallel; k++) pool.submit(new Task());
			try { waitComplete(); }
			catch(Exception e) { e.printStackTrace(); }
			pool.shutdown();
		}
		
	}
	
	public synchronized void checkin() { activeCount++; }
	
	public synchronized void checkout() { 
		activeCount--; 
		notifyAll();
	}
	
	public synchronized void waitComplete() throws InterruptedException {
		while(activeCount > 0) wait();
	}
	
	class Task implements Runnable {
		public Task() { checkin(); }
		
		@Override
		public void run() {
			System.err.print(".");
			checkout();
		}
	}
	
	
	
}
