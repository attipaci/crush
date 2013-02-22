package util.data;

import java.util.ArrayList;


public abstract class LocalAverage<DataType extends LocalizedData> extends ArrayList<DataType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 786022102371734003L;
	public double span = 3.0;	
		
	public int indexBefore(Locality loc) throws ArrayIndexOutOfBoundsException {
		int i = 0;
		int step = size() >> 1;

		
		if(get(0).compareTo(loc) > 0) 
			throw new ArrayIndexOutOfBoundsException("Specified point precedes lookup range.");
		
		if(get(size() - 1).compareTo(loc) < 0) 
			throw new ArrayIndexOutOfBoundsException("Specified point is beyond lookup range.");
		
		
		while(step > 0) {
			if(get(i + step).compareTo(loc) < 0) i += step;
			step >>= 1;
		}
		
		return i;
	}
	
	public double getRelativeWeight(double normalizedDistance) {
		return Math.exp(-0.5 * normalizedDistance * normalizedDistance);
	}
	
	public abstract DataType getLocalizedDataInstance();
	
	public DataType getLocalAverage(Locality loc) throws ArrayIndexOutOfBoundsException {
		return getLocalAverage(loc, null);
	}
	
	public DataType getLocalAverage(Locality loc, Object env) throws ArrayIndexOutOfBoundsException {
		int i0 = indexBefore(loc);
	
		DataType mean = getLocalizedDataInstance();
		mean.setLocality(loc);
		mean.measurements = 0;
		
		for(int i = i0; i >= 0; i--) {
			if(get(i).sortingDistanceTo(loc) > span) break;		
			DataType point = get(i);
			mean.average(point, env, getRelativeWeight(point.distanceTo(loc)));
		}
	
		for(int i = i0+1; i<size(); i++) {
			if(get(i).sortingDistanceTo(loc) > span) break;
			DataType point = get(i);
			mean.average(point, env, getRelativeWeight(point.distanceTo(loc)));
		}
			
		return mean;
		
	}
	
	
}
