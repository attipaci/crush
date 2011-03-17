/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
// Copyright (c) 2010  Attila Kovacs

package crush;

import util.Util;
import util.data.Statistics;
import util.data.WeightedPoint;

import java.io.*;

public class Signal implements Cloneable {
	Integration<?, ?> integration;
	public float[] value, drifts;
	int resolution;
	int driftN;
	
	Signal(Integration<?, ?> integration) {
		this.integration = integration;
	}
	
	Signal(Integration<?, ?> integration, double[] values) {
		this(integration);
		resolution = (int) Math.ceil((double) integration.size() / values.length);
		this.value = new float[values.length];
		for(int i=0; i<values.length; i++) this.value[i] = (float) values[i];
		driftN = values.length+1;
	}
	
	public int length() { return value.length; }
	
	public int getResolution() {
		return resolution;
	}
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public final float valueAt(final Frame frame) {
		return value[frame.index / resolution]; 
	}
		
	public void scale(double factor) {
		float fValue = (float) factor;
		for(int t=0; t<value.length; t++) value[t] *= fValue;
	}
	
	public void add(double x) {
		float fValue = (float) x;
		for(int t=0; t<value.length; t++) value[t] += fValue;
	}
	
	public void subtract(double x) {
		float fValue = (float) x;
		for(int t=0; t<value.length; t++) value[t] -= fValue;
	}
	
	public void addDrifts() {
		if(drifts == null) return;
		
		for(int fromt=0, T=0; fromt<value.length; fromt+=driftN) {
			final int tot = Math.min(fromt + driftN, value.length);
			for(int t=fromt; t<tot; t++) value[t] += drifts[T];
		}
		
		drifts = null;
	}
	
	public double getRMS() { return Math.sqrt(getVariance()); }
	
	public double getUnderlyingRMS() { return Math.sqrt(getUnderlyingVariance()); }
	
	public double getVariance() {
		double sum = 0.0;
		int n = 0;
		for(int t=0; t<value.length; t++) if(!Float.isNaN(value[t])) {
			sum += value[t] * value[t];
			n++;
		}	
		return sum / n;
	}

	public double getUnderlyingVariance() {
		double sum = 0.0;
		int n = 0;
		for(int t=0; t<value.length; t++) if(!Float.isNaN(value[t])) {
			sum += value[t] * value[t] - 1.0;
			n++;
		}	
		return sum / n;
	}
	
	
	public void removeDrifts() {
		int N = integration.framesFor(integration.filterTimeScale) / resolution;
		if(N == driftN) return;
		
		addDrifts();
		
		driftN = N;
		drifts = new float[(int) Math.ceil((double) value.length / driftN)];
		
		for(int fromt=0, T=0; fromt<value.length; fromt+=driftN) {
			double sum = 0.0;
			int n = 0;
			final int tot = Math.min(fromt + driftN, value.length);
			
			for(int t=fromt; t<tot; t++) if(!Float.isNaN(value[t])){
				sum += value[t];
				n++;
			}
			if(n > 0) {
				float fValue = (float) (sum / n);
				for(int t=fromt; t<tot; t++) value[t] -= fValue;
				drifts[T++] = fValue;
			}
		}
	}
	
	
	public WeightedPoint getMedian() {
		float[] temp = new float[value.length];
		for(int t=0; t<value.length; t++) if(!Float.isNaN(value[t])) temp[t] = value[t];
		return new WeightedPoint(Statistics.median(temp), Double.POSITIVE_INFINITY);
	}
	
	public WeightedPoint getMean() {
		double sum = 0.0;
		int n = 0;
		for(int t=0; t<value.length; t++) if(!Float.isNaN(value[t])) {
			sum += value[t];
			n++;
		}	
		return new WeightedPoint(sum / n, Double.POSITIVE_INFINITY);
	}
	
	// Use a quadratic fit...
	// 
	// f[n] = c
	// f[n-1] = a-b+c
	// f[n+1] = a+b+c
	//
	//  c=f[n]
	//  a=(f[n-1] + f[n+1])/2 - f[n]
	//  b=(f[n+1] - f[n-1])/2
	//
	//  f'=2ax+b --> f'[n]=b --> this is simply the chord!
	//
	// f[n+1] = f[n-1] + 2h f'[n]
	//
	// f[1] = f[0] + h f'[0]
	// f[n] = f[n-1] + h f'[n]
	public void differentiate() {
		float dt = (float) (resolution * integration.instrument.samplingInterval);
		for(int t=1; t<value.length; t++) value[t-1] = (value[t] - value[t-1]) / dt;
		
		float temp = value[value.length-1];
		value[value.length-1] = value[value.length-2];
		value[value.length-2] = temp;
		
		for(int t=value.length-2; t>0; t--) {
			value[t] = value[t-1];
			value[t] = 0.5F * (value[t] + value[t+1]);
		}
	}
	
	// Intergate using trapesiod rule...
	public void integrate() {
		double dt = (float) (resolution * integration.instrument.samplingInterval);		
		double I = 0.0;
		
		float halfLast = 0.0F;
		
		for(int t=0; t<value.length; t++) {
			// Calculate next half increment of h/2 * f[t]
			float halfNext = 0.5F * value[t];
			
			// Add half increments from below and above 
			I += halfLast;
			I += halfNext;
			value[t] = (float) (I * dt);
			
			halfLast = halfNext;
		}
	}
	
	public Signal getDifferential() {
		Signal d = (Signal) clone();
		d.differentiate();
		return d;
	}
	
	public Signal getIntegral() {
		Signal d = (Signal) clone();
		d.integrate();
		return d;
	}
	
	public void level(boolean isRobust) {
		WeightedPoint center = isRobust ? getMedian() : getMean();
		float fValue = (float) center.value;
		for(int t=0; t<value.length; t++) value[t] -= fValue;
	}

	
	public void smooth(double FWHM) {
		// create a window with 2 FWHM width and odd number elements;
		int N = 2 * (int)Math.ceil(FWHM / resolution) + 1;
		double[] w = new double[N];
		int ic = N/2;
		double sigma = FWHM/resolution/Util.sigmasInFWHM;
		double A = -0.5 / (sigma * sigma);
		for(int i=0; i<N; i++) w[i] = Math.exp(A*(i-ic)*(i-ic));
		smooth(w);
	}
	
	// TODO Use this in ArrayUtil...
	public void smooth(double[] w) {
		int ic = w.length / 2;
		float[] smoothed = new float[value.length];
		
		double norm = 0.0;
		for(int i=0; i<w.length; i++) norm += Math.abs(w[i]);
		
		for(int t=0; t<value.length; t++) {
			
			int t1 = Math.max(0, t-ic); // the beginning index for the convolution
			int tot = Math.min(value.length, t + w.length - ic);
			int i = ic + t - t1; // the beginning index for the weighting fn.
			int n = 0;
			
			for( ; t1<tot; t1++, i++) if(!Float.isNaN(value[t1])) {
				smoothed[t] += value[t1];
				n++;
			}
			if(n > 0) smoothed[t] /= n;
		}
		value = smoothed;
	}
	

	public void print(PrintStream out) {
		out.println("# " + (1.0 / (resolution * integration.instrument.integrationTime)));
		
		for(int t=0; t<value.length; t++) out.println(Util.e3.format(value[t]));
	}
	
}
