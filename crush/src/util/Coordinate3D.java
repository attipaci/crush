package util;

public class Coordinate3D {
	private double x, y, z;
	
	public Coordinate3D() {
		this(0.0, 0.0, 0.0);
	}
	
	public Coordinate3D(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public double getX() { return x; }
	
	public double getY() { return y; }
	
	public double getZ() { return z; }
	
	public void setX(double value) { this.x = value; }
	
	public void setY(double value) { this.y = value; }
	
	public void setZ(double value) { this.z = value; }
	
}
