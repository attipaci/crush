package util.data;

import util.Metric;

public abstract class Locality implements Metric<Locality>, Comparable<Locality> {
	
		public abstract double sortingDistanceTo(Locality other);
	
}


