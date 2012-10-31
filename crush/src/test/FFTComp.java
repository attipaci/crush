package test;

import java.util.*;

import util.data.OldFFT;
import util.fft.FloatFFT;

public class FFTComp {

	public static void main(String[] args) {
		float[] data = new float[1024];
		Random random = new Random();
		for(int i=0; i<data.length; i++) data[i] = (float) random.nextGaussian();
		
		FloatFFT fft = new FloatFFT();
		fft.setThreads(3);
		
		float[] a = copyOf(data);
		float[] b = copyOf(data);
	
		//OldFFT.forwardRealInPlace(a);
		fft.real2Amplitude(b);
		
		//System.err.println(" Forward: " + (compare(a,b, 1e-4F) ? "OK" : "FAILED!"));
		
		//OldFFT.backRealInplace(a);
		fft.amplitude2Real(b);
		
		System.err.println(" Back: " + (compare(a,b, 1e-4F) ? "OK" : "FAILED!"));
	
		fft.shutdown();
	}
	
	
	public static float[] copyOf(float[] data) {
		float[] copy = new float[data.length];
		System.arraycopy(data,  0,  copy,  0, data.length);
		return copy;
	}
	
	public static boolean compare(float[] a, float[] b, float precision) {
		boolean pass = true;
		
		for(int i=0; i<a.length; i++) {
			double norm = Math.abs(a[i]) < 1.0 ? 1.0 : Math.abs(a[i]); 
			if(Math.abs(a[i] - b[i])/norm > precision) {
				System.err.println("E: " + i + "> " + a[i] + ", " + b[i]);
				pass = false;
			}
		}
		return pass;
		
	}
	
}
