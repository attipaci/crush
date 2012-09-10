package test;


public class FloatTest {

	public static void main(String[] args) {
		long N = 1000000000;
		long start, end;
		
		long k = 0;
		float f = 1.0F;
		double d = 1.0;
		
		start = System.currentTimeMillis();
		for(int i=0; i<N; i++) k++; 		
		end = System.currentTimeMillis();
		long base = end - start;
		
		start = System.currentTimeMillis();
		for(int i=0; i<N; i++) {
			f = (1.0F * (f+0.5F)) - 0.5F; 
			k++;
		}
		end = System.currentTimeMillis();
		
		System.err.println("float: " + (end-start-base) + " ms");
		
		start = System.currentTimeMillis();
		for(int i=0; i<N; i++) {
			d = (1.0 * (d+0.5)) - 0.5; 		
			k++;
		}
		end = System.currentTimeMillis();
		
		System.err.println("double: " + (end-start-base) + " ms");
	}
	
	
	
}
