package util.data;

public interface Weighted2D {

	public double[][] getWeight();
	
	public void setWeight(double[][] image);
	
	public double getWeight(int i, int j);
	
	public void setWeight(int i, int j, double weight);
	
	public void incrementWeight(int i, int j, double dw);
	
	public void scaleWeight(int i, int j, double factor);

}
