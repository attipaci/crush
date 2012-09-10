package test;

import java.awt.Color;

public class ColorTest {
	
	public static void main(String[] args) {
		
		System.err.println("R : " + Integer.toHexString(Color.RED.getRGB()));
		System.err.println("G : " + Integer.toHexString(Color.GREEN.getRGB()));
		System.err.println("B : " + Integer.toHexString(Color.BLUE.getRGB()));
		
		int N = 1000000;
		int r = 0, g = 0, b = 0;
		int result = 0;
		
		System.err.print("Running base test: ");		
		long basetime = -System.currentTimeMillis();
		for(int i=N; --i >= 0; ) {
			r = i & 0x00FF0000 >> 16;
			g = i & 0x0000FF00 >> 8;
			b = i & 0x000000FF;
			result ^= r;
		}
		basetime += System.currentTimeMillis();
		System.err.println(basetime + " ms");
		
		System.err.print("Running direct compose: ");
		long directtime = -System.currentTimeMillis();
		for(int i=N; --i >= 0; ) {
			r = i & 0x00FF0000 >> 16;
			g = i & 0x0000FF00 >> 8;
			b = i & 0x000000FF;
			result ^= 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
		}
		directtime += System.currentTimeMillis() - basetime;
		System.err.println(directtime + " ms");
		
		System.err.print("Running Color compose: ");
		long colortime = -System.currentTimeMillis();
		for(int i=N; --i >= 0; ) {
			r = i & 0x00FF0000 >> 16;
			g = i & 0x0000FF00 >> 8;
			b = i & 0x000000FF;
			result ^= new Color(r, g, b).getRGB();
		}
		colortime += System.currentTimeMillis() - basetime;
		System.err.println(colortime + " ms");
		
		System.err.println(result);
		
	}
	
	
}
