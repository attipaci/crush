package test;

import util.Util;
import util.data.FFT;
import util.data.DoubleFFT;

public class FFTTest4 {
	
	public static void main(String[] args) {
		final int total = 32 * 1024 * 1024;
		final int n = args.length > 0 ? Integer.parseInt(args[0]) : 16;
		final int N = n * 1024;
		final int repeats = Math.max(total / N, 1);
		
		boolean lookup = args.length > 1 ? Util.parseBoolean(args[1]) : false;
		int errorbits = args.length > 2 ? Integer.parseInt(args[2]) : 2;
		
		//final long ops = repeats * N * Math.round((Math.log(N) / Math.log(2.0)));

		final double[] data = new double[N];
		long time = 0L;
		double speed = Double.NaN;
		
		/*
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		long time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) FFT.powerTransform(data, (k & 1) == 0);
		time += System.currentTimeMillis();
		double speed = repeats / (1e-3*time);
		System.err.println("sequent'l " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		*/
		
		DoubleFFT fft = new DoubleFFT();
		fft.setLookup(lookup);
		fft.setErrorBits(errorbits);
		
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.powerTransform(data, (k & 1) == 0); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("seqt'l 2  " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		
		
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.powerTransform(data, (k & 1) == 0, 1); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("1 thread  " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		
			
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.powerTransform(data, (k & 1) == 0, 2); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("2 threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");

		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.powerTransform(data, (k & 1) == 0, 4); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("4 threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
	
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.powerTransform(data, (k & 1) == 0, 8); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("8 threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
	
	
		int cpus = Runtime.getRuntime().availableProcessors();
		if(cpus <= 4) { fft.wrapup(); return; }
		if(cpus == 8) { fft.wrapup(); return; }
		
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.powerTransform(data, (k & 1) == 0, cpus/2); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println((cpus/2) + " threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		
		for(int i=0; i<data.length; i++) data[i] = Math.random();
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.powerTransform(data, (k & 1) == 0, cpus); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println(cpus + " threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		
		fft.wrapup();
	}
}
