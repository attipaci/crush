package test;

// This is testing the speed of access to Java fields...
// As of 2012 Mar, all methods of access perform comparably
// to 1%.

public class AccessTest {
	public final static int rounds = 1000000000;
	public final static double s3o2 = 0.5 * Math.sqrt(3.0);
	
	public static void main(String[] args) {
		AccessTest access = new AccessTest();
			
		access.test0();
		
		access.test1();
		
		access.test2();
		
		access.test3();	
	}
	
	public void test0() {
		double x = 1.0, y = 0.0;
		
		System.err.println("local variables:");
		long time = -System.currentTimeMillis();
		
		for(int i=rounds; --i >= 0; ) {
			double temp = x;
			x = 0.5 * x - s3o2 * y;
			y = 0.5 * y + s3o2 * temp;
		}
		
		time += System.currentTimeMillis();
		
		System.err.println("result = " + x + ", " + y);
		System.err.println("time = " + time + "ms");
		System.err.println();
	}
	
	public void test1() {
		final V1 v = new V1();
		
		System.err.println(v.getName() + ":");
		long time = -System.currentTimeMillis();
		
		for(int i=rounds; --i >= 0; ) {
			double temp = v.x;
			v.x = 0.5 * v.x - s3o2 * v.y;
			v.y = 0.5 * v.y + s3o2 * temp;
		}
		
		time += System.currentTimeMillis();
		
		System.err.println("result = " + v.x + ", " + v.y);
		System.err.println("time = " + time + "ms");
		System.err.println();
	}
	
	public void test2() {
		final V2 v = new V2();
		
		System.err.println(v.getName() + ":");
		long time = -System.currentTimeMillis();
		
		for(int i=rounds; --i >= 0; ) {
			double temp = v.getX();
			v.setX(0.5 * v.getX() - s3o2 * v.getY());
			v.setY(0.5 * v.getY() + s3o2 * temp);
		}
		
		time += System.currentTimeMillis();
		
		System.err.println("result = " + v.getX() + ", " + v.getY());
		System.err.println("time = " + time + "ms");
		System.err.println();
	}

	public void test3() {
		final V2 v = new V2();
		
		System.err.println(v.getName() + " (internal):");
		long time = -System.currentTimeMillis();
		
		for(int i=rounds; --i >= 0; ) v.rotate(); 
		
		time += System.currentTimeMillis();
		
		System.err.println("result = " + v.getX() + ", " + v.getY());
		System.err.println("time = " + time + "ms");
		System.err.println();
	}
	
}

class V1 {
	public double x = 1.0, y = 0.0;
	
	public String getName() { return "public direct"; }
}

class V2 {
	private double x = 1.0, y = 0.0;
	public final static double s3o2 = 0.5 * Math.sqrt(3.0);
	
	public final double getX() { return x; }
	
	public final double getY() { return y; }
	
	public final void setX(final double value) { x = value; }
	
	public final void setY(final double value) { y = value; }
	
	public final void rotate() {
		double temp = x;
		x = 0.5 * x - s3o2 * y;
		y = 0.5 * y + s3o2 * temp;
	}
	
	public String getName() { return "private via get/set"; }
	
}

