/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush;

import java.util.Arrays;

import util.Range;
import util.Util;
import util.Vector2D;
import util.data.FFT;
import util.data.Statistics;

// TODO account for point-source filtering, and Dependents...
// TODO write filter data into scan header?...
// TODO motion filter to disable whiten.below, or even whitening altogether...

public class MotionFilter {
	Integration<?,?> integration;
	float critical = 10.0F;
	float[] data;
	boolean[] reject;
	
	MotionFilter() {}
	
	MotionFilter(Integration<?,?> integration) {
		this();
		setIntegration(integration);
	}
	
	public void setIntegration(Integration<?,?> integration) {
		this.integration = integration;

		System.err.print("   Motion filter: ");
		
		if(integration.hasOption("filter.motion.s2n")) critical = integration.option("filter.motion.level").getFloat();
		
		data = new float[FFT.getPaddedSize(integration.size())];
		reject = new boolean[data.length];
		Vector2D[] pos = integration.getSmoothPositions(Motion.SCANNING);
		
		addFilter(pos, Motion.X);
		addFilter(pos, Motion.Y);
		
		rangeCheck();
		
		int pass = 0;
		for(int i=reject.length; --i >= 0; ) if(!reject[i]) pass++;
		
		System.err.println(Util.f2.format(100.0 * pass / reject.length) + "% information preserved.");
	}
	
	public void rangeCheck() {
		if(!integration.hasOption("filter.motion.range")) return;
		
		Range range = integration.option("filter.motion.range").getRange(true);
		
		double df = 1.0 / (integration.instrument.samplingInterval * data.length);
		int mini = 2 * ((int) Math.floor(range.min / df));
		int maxi = 2 * ((int) Math.ceil(range.max / df));
		
		Arrays.fill(reject, 0, Math.min(mini, reject.length), false);
		if(maxi < reject.length) Arrays.fill(reject, maxi, reject.length, false);
	}
	
	
	public void addFilter(Vector2D[] pos, Motion dir) {	
		for(int t=pos.length; --t >= 0; )
			data[t] = pos[t] == null ? 0.0F : (float) dir.getValue(pos[t]);

		Arrays.fill(data, pos.length, data.length, 0.0F);
		
		// Remove any constant scanning offset
		level(data);
		
		// FFT to get the scanning spectra
		FFT.forwardRealInplace(data);
		
		// Never
		data[0] = 0.0F;
		reject[0] = true;
		
		// Determine the noise level in the scanning spectra
		float criticalLevel = critical * getRMS(data);	
	
		int peakIndex = 0;
		double max = 0.0;
		for(int i=0; i<reject.length; i += 2) {
			double value = Math.hypot(data[i], data[i+1]);
			if(value > max) {
				max = value;
				peakIndex = i;
			}
		}
		
		if(integration.hasOption("filter.motion.cutoff")) {
			float cutoff = integration.option("filter.motion.cutoff").getFloat() * (float) max;
			if(cutoff > criticalLevel) criticalLevel = cutoff;
		}
		

		for(int i=0; i<reject.length; i += 2) {
			double value = Math.hypot(data[i], data[i+1]);
			if(value > criticalLevel) reject[i] = reject[i+1] = true;	
		}
		
		double df = 1.0 / (integration.instrument.samplingInterval * data.length);
		double f = peakIndex * df;
	
		System.err.print(dir.id + " @ " + Util.f3.format(f) + "Hz, ");
	}
	
	protected float getRMS(float[] spectrum) {
		float[] vars = new float[spectrum.length];
		for(int i=0; i < spectrum.length; i += 2) {
			vars[i] = spectrum[i] * spectrum[i] + spectrum[i+1] * spectrum[i+1];
		}
		return (float) Math.sqrt(Statistics.median(vars, 0, (spectrum.length >> 1) - 1) / 0.454937);
	}
	
	public void filter() {
		integration.comments += "FM";
		for(Channel channel : integration.instrument) filter(channel);		
	}
	
	public synchronized void filter(Channel channel) {
		final int c = channel.index;
		for(int i = integration.size(); --i >= 0; ) {
			final Frame exposure = integration.get(i);

			if(exposure == null) data[i] = 0.0F;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[i] = 0.0F;
			else if(exposure.sampleFlag[c] != 0) data[i] = 0.0F;
			else data[i] = exposure.relativeWeight * exposure.data[c];
		}
		
		Arrays.fill(data, integration.size(), data.length, 0.0F);
		
		level(data);
		
		FFT.forwardRealInplace(data);
		
		for(int i=0; i<reject.length; i += 2) if(!reject[i]) data[i] = data[i+1] = 0.0F; 	
		
		FFT.backRealInplace(data);
		
		level(data);
		
		for(int i = integration.size(); --i >= 0; ) {
			final Frame exposure = integration.get(i);
			if(exposure != null) exposure.data[c] -= data[i];	
		}
	}
	
	protected void level(float[] data) {
		double sum = 0.0;
		for(int i=integration.size(); --i >= 0; ) sum += data[i];
		double level = sum / integration.size();
		for(int i=integration.size(); --i >= 0; ) data[i] -= level;
	}
	
	
}
