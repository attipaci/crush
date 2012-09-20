package test;

import util.Util;
import util.fft.DoubleFFT;

public class ParFFTTest {
public static void main(String[] args) {
		double[] data = new double[16];
		
		int threads = 2;
		DoubleFFT fft = new DoubleFFT();
		fft.setErrorBits(1);
		
		System.err.println("delta[0]:");
		data[0] = 1.0;
		try { fft.complexTransform(data, true, threads); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		System.err.println("constant(1.0):");
		for(int i=0; i<data.length; i+=2)  {
			data[i] = 1.0;
			data[i+1] = 0.0;
		}
		try { fft.complexTransform(data, true, threads); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		
		System.err.println("cos1:");
		for(int i=0; i<data.length; i+=2) {
			data[i] = Math.cos(2.0 * Math.PI * i / data.length);
			data[i+1] = 0.0;
		}
		try { fft.complexTransform(data, true, threads); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		System.err.println("sin2:");
		for(int i=0; i<data.length; i+=2) {
			data[i] = Math.sin(4.0 * Math.PI * i / data.length);
			data[i+1] = 0.0;
		}
		try { fft.complexTransform(data, true, threads); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		System.err.println("cos2:");
		for(int i=0; i<data.length; i+=2) {
			data[i] = Math.cos(4.0 * Math.PI * i / data.length);
			data[i+1] = 0.0;
		}
		try { fft.complexTransform(data, true); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
			
		System.err.println("amp real cos2:");
		for(int i=0; i<data.length; i++) data[i] = Math.cos(4.0 * Math.PI * i / data.length);
		
		try { fft.real2Amplitude(data, threads); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		
		fft.shutdown();
	}

	
	public static void print(double[] data) {
		for(int i=0; i<data.length; i+=2) 
			System.out.println("  " + (i>>1) + ":\t" + Util.f6.format(data[i]) + ", " + Util.f6.format(data[i+1]));
		System.out.println();
	}
	
	
}
