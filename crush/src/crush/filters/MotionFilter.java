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

package crush.filters;

import java.util.Arrays;

import crush.Integration;
import crush.Motion;

import util.Range;
import util.Util;
import util.Vector2D;
import util.data.FFT;
import util.data.Statistics;

// TODO account for point-source filtering, and Dependents...
// TODO write filter data into scan header?...
// TODO motion filter to disable whiten.below, or even whitening altogether...

public class MotionFilter extends KillFilter {
	float critical = 10.0F;
	
	public MotionFilter(Integration<?,?> integration) {
		super(integration);
	}
	
	protected MotionFilter(Integration<?,?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {
		super.setIntegration(integration);

		System.err.print("   Motion filter: ");
		
		if(hasOption("level")) critical = option("level").getFloat();
		
		Vector2D[] pos = integration.getSmoothPositions(Motion.SCANNING);
		
		addFilter(pos, Motion.X);
		addFilter(pos, Motion.Y);
		
		rangeCheck();
		
		int pass = 0;
		for(int i=reject.length; --i >= 0; ) if(!reject[i]) pass++;
		
		System.err.print(Util.f2.format(100.0 * pass / reject.length) + "% pass. ");

		components = reject.length - pass;
		
		autoDFT();
		
		System.err.println("Preferring " + (dft ? "DFT" : "FFT") + ".");
	}
	
	
	private void rangeCheck() {
		if(!hasOption("range")) return;
		
		Range range = option("range").getRange(true);
				
		int mini = ((int) Math.floor(range.min / df));
		int maxi = ((int) Math.ceil(range.max / df));
		
		Arrays.fill(reject, 0, Math.min(mini, reject.length), false);
		if(maxi < reject.length) Arrays.fill(reject, maxi, reject.length, false);
	}
	
	
	private void addFilter(Vector2D[] pos, Motion dir) {	
		for(int t=pos.length; --t >= 0; )
			data[t] = pos[t] == null ? Float.NaN : (float) dir.getValue(pos[t]);

		Arrays.fill(data, pos.length, data.length, 0.0F);
		
		// Remove any constant scanning offset
		levelData();
		
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
		
		if(hasOption("cutoff")) {
			float cutoff = option("cutoff").getFloat() * (float) max;
			if(cutoff > criticalLevel) criticalLevel = cutoff;
		}
		

		for(int i=2; i<data.length; i += 2) {
			double value = Math.hypot(data[i], data[i+1]);
			if(value > criticalLevel) reject[i>>1] = true;	
		}
		
		double df = 1.0 / (integration.instrument.samplingInterval * data.length);
		double f = peakIndex/2 * df;
	
		System.err.print(dir.id + " @ " + Util.f1.format(1000.0 * f) + " mHz, ");
	}
	
	private float getRMS(float[] spectrum) {
		float[] vars = new float[spectrum.length];
		
		for(int i=0; i < spectrum.length; i += 2) {
			vars[i] = spectrum[i] * spectrum[i] + spectrum[i+1] * spectrum[i+1];
		}
		return (float) Math.sqrt(Statistics.median(vars, 0, (spectrum.length >> 1) - 1) / 0.454937);
	}
	
	
	@Override
	public void apply() {
		setChannels(integration.instrument.getObservingChannels());
		super.apply();
	}

	@Override
	public String getID() {
		return "Mf";
	}
	
	@Override
	public String getConfigName() {
		return "filter.motion";
	}
	
}
