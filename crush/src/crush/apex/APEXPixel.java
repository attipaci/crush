/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2010 Attila Kovacs 

package crush.apex;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import kovacs.data.DataPoint;
import kovacs.data.WeightedPoint;
import kovacs.fft.DoubleFFT;
import kovacs.math.Vector2D;
import kovacs.util.ExtraMath;
import kovacs.util.Unit;
import kovacs.util.Util;

import crush.Channel;
import crush.Frame;
import crush.PhaseData;
import crush.PhaseDespiking;
import crush.PhaseSet;
import crush.PhaseWeighting;
import crush.array.SimplePixel;

public abstract class APEXPixel extends SimplePixel implements PhaseWeighting, PhaseDespiking {
	public Vector2D fitsPosition;
	public double relativePhaseWeight = 1.0;
	
	public APEXPixel(APEXArray<?> array, int backendIndex) {
		super(array, backendIndex);
	}
	
	@Override
	public Channel copy() {
		APEXPixel copy = (APEXPixel) super.copy();
		if(fitsPosition != null) copy.fitsPosition = (Vector2D) fitsPosition.clone();
		return copy;
	}
	

	@Override
	public void deriveRelativePhaseWeights(final PhaseSet phases) {	
		double chi2 = getLRChi2(phases, getLROffset(phases).value());
		
		if(Double.isNaN(chi2)) {
			flag(FLAG_PHASE_DOF);
			return;
		}
		
		unflag(FLAG_PHASE_DOF);
		
		relativePhaseWeight /= chi2;

		// Do not allow relative phaseWeights to become larger than 1.0
		if(relativePhaseWeight > 1.0) {
			chi2 *= relativePhaseWeight;
			relativePhaseWeight = 1.0;		
		}
		
		for(PhaseData offsets : phases) offsets.weight[this.index] /= chi2;
	}
	
	@Override
	public double getRelativePhaseWeight() { return relativePhaseWeight; }
	
	
	public WeightedPoint getChopSignal(final PhaseSet phases, final int i) {
		final PhaseData A = phases.get(i);
		final PhaseData B = phases.get(i-1);
		
		final int phaseA = A.phase;	
		final WeightedPoint signal = A.getChannelValue(this);		
		
		if(A.channelFlag[this.index] != 0) signal.setWeight(0.0);
		
		if(B.phase != phaseA) {
			signal.subtract(B.getChannelValue(this));
			if(B.channelFlag[this.index] != 0) signal.setWeight(0.0);
		}
		else signal.setWeight(0.0);
		
		if((phaseA & Frame.CHOP_LEFT) == 0) signal.scale(-1.0);
		return signal;
	}
	
	
	public WeightedPoint getCorrectedChopSignal(final PhaseSet phases, final int i, final Collection<APEXPixel> bgPixels, final double[] G) {
		final WeightedPoint bg = new WeightedPoint();
		
		for(final APEXPixel pixel : bgPixels) if(!pixel.isFlagged()) if(pixel != this) if(pixel.sourcePhase == 0) {
			final WeightedPoint lr = pixel.getChopSignal(phases, i);
			if(G[pixel.index] == 0.0) continue;
			lr.scale(1.0 / G[pixel.index]);
			bg.average(lr);
		}

		final WeightedPoint value = getChopSignal(phases, i);
		
		bg.scale(G[this.index]);	
		if(bg.weight() > 0.0) value.subtract(bg);
		
		return value;
	}

	
	public void writeLROffset(final PhaseSet phases, String fileName, final Collection<APEXPixel> bgPixels, final double[] sourceGain) throws IOException {
		final PrintWriter out = new PrintWriter(new FileOutputStream(fileName));	
		final APEXArraySubscan<?,?> subscan = (APEXArraySubscan<?,?>) phases.getIntegration();
		
		out.println("# CRUSH APEX Photometry Nod-cycle Data");
		out.println("# =============================================================================");
		out.println(subscan.getASCIIHeader());
		out.println("# Chop Frequency: " + Util.f3.format(subscan.getChopper().frequency / Unit.Hz) + "Hz"); 
		out.println("# Pixel: " + getFixedIndex());
		out.println("# Source Phase: " + sourcePhase);
		out.println();
		out.println("# chop#\tSignal\t\tCorrected");
		
		final int N = phases.size();
		for(int i=1; i < N; i+=2) out.println("  " + (i>>1)
				+ "\t" + getChopSignal(phases, i).toString(Util.e5)
				+ "\t" + getCorrectedChopSignal(phases, i, bgPixels, sourceGain).toString(Util.e5));
		
		out.println();
		out.println();
		out.close();
	}
	
	public void writeLRSpectrum(final PhaseSet phases, String fileName) throws IOException {
		final PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		final APEXArraySubscan<?,?> subscan = (APEXArraySubscan<?,?>) phases.getIntegration();
	
		out.println("# CRUSH APEX Photometry Nod-cycle Spectrum");
		out.println("# =============================================================================");
		out.println(subscan.getASCIIHeader());
		out.println("# Chop Frequency: " + Util.f3.format(subscan.getChopper().frequency / Unit.Hz) + "Hz"); 
		out.println("# Pixel: " + getFixedIndex());
		out.println("# Source Phase: " + sourcePhase);
		out.println();
		out.println("# Freq(Hz)\tAmplitude\tPhase(deg)");
	
		final double[] data = new double[ExtraMath.pow2ceil(phases.size()>>1)];
		final double dF = 0.5 * subscan.getChopper().frequency / data.length;
		final int N = phases.size();
		final double mean = getLROffset(phases).value();
		
		for(int i=1; i < N; i+=2) {
			final WeightedPoint point = getChopSignal(phases, i);
			point.subtract(mean);
			data[i>>1] = DataPoint.significanceOf(point);
		}
			
		new DoubleFFT().real2Amplitude(data);
			
		out.println(data[0]);
		for(int i=2; i < data.length; i+=2) {
			out.println("  " + (i*dF) + "\t" + Util.e5.format(Math.hypot(data[i], data[i+1])) + "\t" + Util.f5.format(Math.atan2(data[i+1], data[i])));
		}
		out.println(data[1]);
			
		out.println();
		out.println();
		out.close();
	}
	
	public WeightedPoint getLROffset(final PhaseSet phases) {
		int N = phases.size();
		final WeightedPoint lr = new WeightedPoint();
		for(int i=1; i < N; i+=2) lr.average(getChopSignal(phases, i));
		//lr.scaleWeight(0.5);
		return lr;
	}
	
	public WeightedPoint getCorrectedLROffset(final PhaseSet phases, final Collection<APEXPixel> bgPixels, final double[] sourceGain) {
		int N = phases.size();
		final WeightedPoint lr = new DataPoint();
		for(int i=1; i < N; i+=2) lr.average(getCorrectedChopSignal(phases, i, bgPixels, sourceGain));
		//lr.scaleWeight(0.5);
		return lr;
	}

	
	public double getLRChi2(final PhaseSet phases, final double mean) {	
		final int N = phases.size();
		double chi2 = 0.0;
		int n = 0;
		
		for(int i=1; i < N; i+=2) {
			final WeightedPoint LR = getChopSignal(phases, i);
			if(LR.weight() == 0.0) continue;
			
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		
		//double dof = n * (1.0 - (double) phases.driftParms / phases.size());
		dof = n * (1.0 - phases.channelParms[this.index] / phases.size());
		dof = Math.min(dof, n - 1);
		
		return dof > 0.0 ? chi2/dof : Double.NaN;
	}

	public double getCorrectedLRChi2(final PhaseSet phases, final Collection<APEXPixel> bgPixels, final double mean, final double[] sourceGain) {	
		final int N = phases.size();
		double chi2 = 0.0;
		int n = 0;
		
		for(int i=1; i < N; i+=2) {
			final WeightedPoint LR = getCorrectedChopSignal(phases, i, bgPixels, sourceGain);
			if(LR.weight() == 0.0) continue;
			
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		//double dof = n * (1.0 - (double) phases.driftParms / phases.size());
		dof = n * (1.0 - phases.channelParms[this.index] / phases.size());
		dof = Math.min(dof, n - 1);
		
		return dof > 0.0 ? chi2/dof : Double.NaN;
	}
	
	@Override
	public int despike(PhaseSet phases, double level) {
		final int N = phases.size();
		final double mean = getLROffset(phases).value();
		
		int spikes = 0;
		
		for(int i=1; i < N; i+=2) {
			phases.get(i).channelFlag[this.index] &= ~PhaseData.FLAG_SPIKE;
			phases.get(i-1).channelFlag[this.index] &= ~PhaseData.FLAG_SPIKE;
			
			final WeightedPoint LR = getChopSignal(phases, i);
			if(LR.weight() == 0.0) continue;
			
			LR.subtract(mean);
			if(Math.abs(DataPoint.significanceOf(LR)) > level) {
				phases.get(i).channelFlag[this.index] |= PhaseData.FLAG_SPIKE;
				phases.get(i-1).channelFlag[this.index] |= PhaseData.FLAG_SPIKE;
				spikes++;
			}
		}
		return spikes;
	}
	
	public final static int FLAG_PHASE_DOF = 1<<nextSoftwareFlag++;

}
