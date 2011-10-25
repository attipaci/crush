package util.data;

public interface Timed2D {

	public double[][] getTime();
	
	public void setTime(double[][] image);
	
	public double getTime(int i, int j);
	
	public void setTime(int i, int j, double t);
	
	public void incrementTime(int i, int j, double dt);
	
}
