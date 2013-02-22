package util.data;


import util.Metric;

public abstract class LocalizedData implements Comparable<LocalizedData>, Metric<LocalizedData> {
	public int measurements = 1;
	
	public abstract Locality getLocality();

	public abstract void setLocality(Locality loc);
	
	public void average(LocalizedData other, Object env, double relativeWeight) {
		averageWidth(other, env, relativeWeight);
		measurements += other.measurements;
	}
	
	protected abstract void averageWidth(LocalizedData other, Object env, double relativeWeight);
	
	public int compareTo(LocalizedData other) { return getLocality().compareTo(other.getLocality()); }
	
	public int compareTo(Locality loc) { return getLocality().compareTo(loc); }
	
	public double distanceTo(Locality loc) {
		return getLocality().distanceTo(loc);
	}
	
	public double distanceTo(LocalizedData other) {
		return distanceTo(other.getLocality());
	}

	public double sortingDistanceTo(Locality loc) {
		return getLocality().sortingDistanceTo(loc);
	}
	
	public double sortingDistanceTo(LocalizedData other) {
		return sortingDistanceTo(other.getLocality());
	}

	
}


