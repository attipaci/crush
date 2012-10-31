package test;

import util.Util;
import util.fft.*;

public class FFTTest4A {
	
	public static void main(String[] args) {
		final int total = 256 * 1024 * 1024;
		final int n = args.length > 0 ? Integer.parseInt(args[0]) : 16;
		final int N = n * 1024;
		final int repeats = Math.max(total / N, 1);
		
		
		//final long ops = repeats * N * Math.round((Math.log(N) / Math.log(2.0)));

		final float[] data = new float[N];
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
		
		FloatFFT fft = new FloatFFT();
		System.err.println("error bits: " + Util.f2.format(fft.getMaxErrorBitsFor(data)));
		System.err.println("precision: " + Util.e2.format(fft.getMinPrecisionFor(data)));
		System.err.println("dynamic range: " + Util.f1.format(-20.0 * Math.log10(fft.getMinPrecisionFor(data))) + " dB");
	
		
		
		for(int i=0; i<data.length; i++) data[i] = (float) Math.random();
		fft.setThreads(1);
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.complexTransform(data, (k & 1) == 0); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("1 thread  " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		
			
		for(int i=0; i<data.length; i++) data[i] = (float) Math.random();
		fft.setThreads(2);
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.complexTransform(data, (k & 1) == 0); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("2 threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");

		for(int i=0; i<data.length; i++) data[i] = (float) Math.random();
		fft.setThreads(4);
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.complexTransform(data, (k & 1) == 0); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("4 threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
	
		for(int i=0; i<data.length; i++) data[i] = (float) Math.random();
		fft.setThreads(8);
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.complexTransform(data, (k & 1) == 0); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println("8 threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
	
	
		int cpus = Runtime.getRuntime().availableProcessors();
		if(cpus <= 4) { fft.shutdown(); return; }
		if(cpus == 8) { fft.shutdown(); return; }
		
		for(int i=0; i<data.length; i++) data[i] = (float) Math.random();
		fft.setThreads(cpus/2);
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.complexTransform(data, (k & 1) == 0); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println((cpus/2) + " threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		
		for(int i=0; i<data.length; i++) data[i] = (float) Math.random();
		fft.setThreads(cpus);
		time = -System.currentTimeMillis();
		for(int k=repeats; --k>=0; ) {
			try { fft.complexTransform(data, (k & 1) == 0); }
			catch(Exception e) { 
				e.printStackTrace();
				System.exit(1);
			}
		}
		time += System.currentTimeMillis();
		speed = repeats / (1e-3*time);
		System.err.println(cpus + " threads " + repeats + " x " + n + "k points: " + Util.f2.format(speed) + " FFTs/s");
		
		fft.shutdown();
	}
}
