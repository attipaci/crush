
package test;

import util.Util;
import util.fft.FloatFFT;

public class ParFFTTestA {
public static void main(String[] args) {
		System.err.println("FFT test for floats...");
	
		float[] data = new float[32];

		FloatFFT fft = new FloatFFT();
		fft.setSequential();
		
		System.err.println("delta[0]:");
		data[0] = 1.0F;
		try { fft.complexTransform(data, true); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		System.err.println("constant(1.0):");
		for(int i=0; i<data.length; i+=2)  {
			data[i] = 1.0F;
			data[i+1] = 0.0F;
		}
		try { fft.complexTransform(data, true); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		
		System.err.println("cos1:");
		for(int i=0; i<data.length; i+=2) {
			data[i] = (float) Math.cos(2.0 * Math.PI * i / data.length);
			data[i+1] = 0.0F;
		}
		try { fft.complexTransform(data, true); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		System.err.println("sin2:");
		for(int i=0; i<data.length; i+=2) {
			data[i] = (float) Math.sin(4.0 * Math.PI * i / data.length);
			data[i+1] = 0.0F;
		}
		try { fft.complexTransform(data, true); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		System.err.println("cos2:");
		for(int i=0; i<data.length; i+=2) {
			data[i] = (float) Math.cos(4.0 * Math.PI * i / data.length);
			data[i+1] = 0.0F;
		}
		try { fft.realTransform(data, true); }
		catch(Exception e) { e.printStackTrace(); }
			
		int m = 5, k = 7;
		System.err.println("amp real cos" + m + ", sin" + k);
		for(int i=0; i<data.length; i++) data[i] = (float) Math.cos(2.0 * m * Math.PI * i / data.length) + (float) Math.sin(2.0 * k * Math.PI * i / data.length);
		print(data);
		
		System.err.println("r2a:");
		try { fft.real2Amplitude(data); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
		System.err.println("a2r:");
		try { fft.amplitude2Real(data); }
		catch(Exception e) { e.printStackTrace(); }
		print(data);
		
	
		fft.shutdown();
		
		int i = 1<<20;
		System.out.println("1<<20  :" + Util.log2floor(i) + ", " + Util.log2round(i) + ", " + Util.log2ceil(i));
		
		i-=1;
		System.out.println("1<<20-1:" + Util.log2floor(i) + ", " + Util.log2round(i) + ", " + Util.log2ceil(i));
		
		i+=2;
		System.out.println("1<<20+1:" + Util.log2floor(i) + ", " + Util.log2round(i) + ", " + Util.log2ceil(i));
		
	}

	
	public static void print(float[] data) {
		for(int i=0; i<data.length; i+=2) 
			System.out.println("  " + (i>>1) + ":\t" + Util.f6.format(data[i]) + ", " + Util.f6.format(data[i+1]));
		System.out.println();
	}
	
	
}
