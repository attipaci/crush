/* *****************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
package crush.filters;

import java.util.Arrays;
import java.util.stream.IntStream;

import crush.Integration;
import crush.Signal;
import crush.motion.Motion;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.data.Statistics;
import jnum.math.Range;


// TODO account for point-source filtering, and Dependents...
// TODO write filter data into scan header?...
// TODO motion filter to disable whiten.below, or even whitening altogether...

public class MotionFilter extends KillFilter {
	/**
	 * 
	 */
	private static final long serialVersionUID = 698161240086532503L;
	
	float critical = 10.0F;
	double halfWidth = 0.0;	// for AM noise on >5s.
	int harmonics = 1;
	boolean oddHarmonicsOnly = false;
	
	public MotionFilter(Integration<?> integration) {
		super(integration);
	}
	
	public MotionFilter(Integration<?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	protected void setIntegration(Integration<?> integration) {
		super.setIntegration(integration);
		if(integration.hasOption("lab")) return;

		StringBuffer buf = new StringBuffer();
		buf.append("Motion filter: ");
		
		if(hasOption("s2n")) critical = option("s2n").getFloat();
		if(hasOption("stability")) halfWidth = 0.5 / Math.abs(option("stability").getDouble() * Unit.s);
		if(hasOption("harmonics")) harmonics = Math.max(1, option("harmonics").getInt());
		oddHarmonicsOnly = hasOption("odd");
		
		addFilter(Motion.SCANNING, Motion.X, buf);
		addFilter(Motion.SCANNING, Motion.Y, buf);
		
		expandFilter();
		harmonize();
		
		rangeCheck();
		
		final boolean[] reject = getRejectMask();
		
		int pass = (int) IntStream.range(0, reject.length).parallel().filter(f -> !reject[f]).count();
		
        buf.append(Util.f2.format(100.0 * pass / reject.length) + "% pass. ");
		
        autoDFT();
		
		buf.append("Preferring " + (dft ? "DFT" : "FFT") + ".");
		
		integration.info(new String(buf));
	}
	
	public double getShortestPeriod() {
		final boolean[] reject = getRejectMask();
		for(int i=reject.length; --i >= 0; ) if(reject[i]) return 1.0 / (df * i);
		return Double.POSITIVE_INFINITY;
	}
	
	private void rangeCheck() {
		if(!hasOption("range")) return;
		
		Range range = option("range").getRange(true);
				
		int mini = ((int) Math.floor(range.min() / df));
		int maxi = ((int) Math.ceil(range.max() / df));
		
		final boolean[] reject = getRejectMask();
		Arrays.fill(reject, 0, Math.min(mini, reject.length), false);
		if(maxi < reject.length) Arrays.fill(reject, maxi, reject.length, false);
	}

	
	private void addFilter(int type, Motion dir, StringBuffer buf) {
		makeTempData(); 
		
		final float[] data = getTempData();
		final boolean[] reject = getRejectMask();
		
		Signal pos = integration.getPositionSignal(type, dir);
		
        System.arraycopy(pos.value, 0, data, 0, pos.length());
		Arrays.fill(data, pos.length(), data.length, 0.0F);
		
		// Remove any constant scanning offset
		levelData();
		
		// FFT to get the scanning spectra
		integration.getFFT().real2Amplitude(data);
		
		// Never
		data[0] = 0.0F;
		reject[0] = true;
		
		// Determine the noise level in the scanning spectra
		float criticalLevel = critical * getRMS(data);	
	
		int peakIndex = 0;
		double max = 0.0;
		for(int i=0; i<reject.length; i += 2) {
			double value = ExtraMath.hypot(data[i], data[i+1]);
			if(value > max) {
				max = value;
				peakIndex = i;
			}
		}
		
		if(hasOption("above")) {
			float cutoff = option("above").getFloat() * (float) max;
			if(cutoff > criticalLevel) criticalLevel = cutoff;
		}
		
		for(int i=2; i<data.length; i += 2) {
			double value = ExtraMath.hypot(data[i], data[i+1]);
			if(value > criticalLevel) reject[i>>1] = true;	
		}
		
		double df = 1.0 / (getInstrument().samplingInterval * data.length);
		double f = peakIndex/2 * df;
	
		discardTempData();
		
		buf.append(dir.id + " @ " + Util.f1.format(1000.0 * f) + " mHz, ");
	}
	
	private void expandFilter() {
		// Calculate the HWHM of the AM noise...
		int d = (int) Math.round(halfWidth / df);
		if(d < 1) return;
		
		final boolean[] reject = getRejectMask();
		final boolean[] expanded = new boolean[reject.length];
		
		int lastFrom = reject.length-1;
		
		for(int i=reject.length; --i >= 0; ) if(reject[i]) {
			final int from = Math.max(0, i-d);
			final int to = Math.min(lastFrom, i + d);
			for(int j=from; j<=to; j++) expanded[j] = true;		
			lastFrom = from;
		}
		
		setRejectMask(expanded);
	}
	
	private void harmonize() {
		if(harmonics < 2) return;
		
		final boolean[] reject = getRejectMask();
		final boolean[] spread = new boolean[reject.length];
		final int step = oddHarmonicsOnly ? 2 : 1;
		
		for(int i=reject.length; --i >= 0; ) if(reject[i]) {
			for(int k=1; k <= harmonics; k += step) {
				final int j = k * i;
				if(j >= spread.length) break;
				spread[j] = true;
			}
		}
		
		setRejectMask(spread);
	}
	
	private float getRMS(float[] spectrum) {
		float[] vars = new float[spectrum.length];
		
		for(int i=0; i < spectrum.length; i += 2) {
			vars[i] = spectrum[i] * spectrum[i] + spectrum[i+1] * spectrum[i+1];
		}
		return (float) Math.sqrt(Statistics.Inplace.median(vars, 0, (spectrum.length >> 1) - 1) / Statistics.medianNormalizedVariance);
	}
	
	
	
	
	@Override
	protected void preFilter() {
		setChannels(getInstrument().getObservingChannels());
		super.preFilter();
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
