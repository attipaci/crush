/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
 * All rights reserved. 
 * 
 * This file is part of crush.
 * 
 *     crush is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     crush is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with crush.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/

package crush;

import java.util.concurrent.ArrayBlockingQueue;

import jnum.data.DataPoint;
import jnum.data.WeightedPoint;


public class Recycler {
	private ArrayBlockingQueue<int[]> ints;
	private ArrayBlockingQueue<float[]> floats;
	private ArrayBlockingQueue<double[]> doubles;
	private ArrayBlockingQueue<DataPoint[]> points;

	public Recycler() {}
	
	public Recycler(int capacity) {
		this();
		setSize(capacity);
	}
	
	public synchronized int[] getIntArray(int size) {
		if(ints != null) if(!ints.isEmpty()) {
			try { 
				int[] i = ints.take(); 
				if(i.length == size) return i;	
			}
			catch(InterruptedException e) { CRUSH.error(this, e); }
		}
		return new int[size];
	}
	
	
	public synchronized float[] getFloatArray(int size) {
		if(floats != null) if(!floats.isEmpty()) {
			try { 
				float[] f = floats.take(); 
				if(f.length == size) return f;	
			}
			catch(InterruptedException e) { CRUSH.error(this, e); }
		}
		return new float[size];
	}
	
	public synchronized double[] getDoubleArray(int size) {
		if(doubles != null) if(!doubles.isEmpty()) {
			try { 
				double[] d = doubles.take(); 
				if(d.length == size) return d;	
			}
			catch(InterruptedException e) { CRUSH.error(this,  e); }
		}
		return new double[size];
	}
	
	public synchronized DataPoint[] getDataPointArray(int size) {
		if(points != null) if(!points.isEmpty()) {
			try { 
				DataPoint[] p = points.take(); 
				if(p.length == size) return p;	
			}
			catch(InterruptedException e) { CRUSH.error(this, e); }
		}
		return DataPoint.createArray(size);
	}
	
	public synchronized void recycle(int[] array) { 
		if(ints == null) return;
		
		if(ints.remainingCapacity() > 0) {
			try { ints.put(array); }
			catch(InterruptedException e) {}
		}
		else CRUSH.warning(this, "int[] recycler overflow.");
	}
	
	public synchronized void recycle(float[] array) { 
		if(floats == null) return;
		
		if(floats.remainingCapacity() > 0) {
			try { floats.put(array); }
			catch(InterruptedException e) {}
		}
		else CRUSH.warning(this, "float[] recycler overflow.");
	}
	
	public synchronized void recycle(double[] array) { 
		if(doubles == null) return;
		
		if(doubles.remainingCapacity() > 0) {
			try { doubles.put(array); }
			catch(InterruptedException e) {}
		}
		else CRUSH.warning(this, "double[] recycler overflow.");
	}
	
	public synchronized void recycle(WeightedPoint[] array) {
		if(!(array instanceof DataPoint[])) return;
		if(points == null) return;
		
		if(points.remainingCapacity() > 0) {
			try { points.put((DataPoint[]) array); }
			catch(InterruptedException e) {}
		}
		else CRUSH.warning(this, "WeightedPoint[] recycler overflow.");
	}
	
	public synchronized void clear() {
	    ints.clear();
		floats.clear();
		doubles.clear();
		points.clear();
	}
	
	public int size() {
		return floats == null ? 0 : floats.size();
	}
	
	public void setSize(int capacity) {
		if(size() <= 0) {
		    ints = null;
			floats = null;
			doubles = null;
			points = null;
		}
		if(size() != capacity) {
		    ints = new ArrayBlockingQueue<int[]>(capacity);
			floats = new ArrayBlockingQueue<float[]>(capacity);
			doubles = new ArrayBlockingQueue<double[]>(capacity);
			points = new ArrayBlockingQueue<DataPoint[]>(capacity);
		}
	}
	
}
