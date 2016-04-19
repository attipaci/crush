/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.apex;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import crush.Channel;
import crush.Frame;
import crush.PhaseData;
import crush.PhaseSet;
import crush.PhaseWeighting;
import crush.array.SingleColorPixel;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;
import jnum.fft.DoubleFFT;
import jnum.math.Vector2D;

public abstract class APEXContinuumPixel extends SingleColorPixel implements PhaseWeighting {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3101551324892059033L;
	public Vector2D fitsPosition;
	public double relativePhaseWeight = 1.0;
	
	
	public APEXContinuumPixel(APEXCamera<?> array, int backendIndex) {
		super(array, backendIndex);
	}
	
	@Override
	public Channel copy() {
		APEXContinuumPixel copy = (APEXContinuumPixel) super.copy();
		if(fitsPosition != null) copy.fitsPosition = (Vector2D) fitsPosition.clone();
		return copy;
	}
	
	@Override
	public void deriveRelativePhaseWeights(final PhaseSet phases) {	
	    unflag(FLAG_PHASE_DOF);
	    
		// Undo the prior relative weight correction...
		for(PhaseData offsets : phases) offsets.weight[this.index] /= relativePhaseWeight;
			
		double chi2 = getLRChi2(phases, getLROffset(phases).value());
		
		if(Double.isNaN(chi2)) {
		    flag(FLAG_PHASE_DOF);
		    return;
	    }
		
		relativePhaseWeight = 1.0 / chi2;

		// Do not allow relative phaseWeights to become larger than 1.0
		if(relativePhaseWeight > 1.0) relativePhaseWeight = 1.0;		
		
		// Reapply the new relative weight correction...
		for(PhaseData offsets : phases) offsets.weight[this.index] *= relativePhaseWeight;
	}
	
	@Override
	public double getRelativePhaseWeight() {
	    return relativePhaseWeight;    
	}
		
	public WeightedPoint getChopSignal(final PhaseSet phases, final int i) {
		final PhaseData A = phases.get(i);
		final PhaseData B = phases.get(i-1);
		
		// Check that it's a left/right chop pair...
		if(A.phase == Frame.CHOP_LEFT && B.phase != Frame.CHOP_RIGHT) return new WeightedPoint();
		if(B.phase == Frame.CHOP_LEFT && A.phase != Frame.CHOP_RIGHT) return new WeightedPoint();
		
		// Check that neither phase is flagged...
		if(A.isFlagged(this) || B.isFlagged(this)) return new WeightedPoint();
		
		final WeightedPoint signal = A.getValue(this);		
		signal.subtract(B.getValue(this));
		
		if((A.phase & Frame.CHOP_LEFT) == 0) signal.scale(-1.0);
		return signal;
	}
	
	
	public WeightedPoint getBGCorrectedChopSignal(final PhaseSet phases, final int i, final Collection<APEXContinuumPixel> bgPixels, final double[] G) {
		final WeightedPoint bg = new WeightedPoint();
		
		for(final APEXContinuumPixel pixel : bgPixels) if(!pixel.isFlagged()) if(pixel != this) if(pixel.sourcePhase == 0) {
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

	
	public void writeLROffset(final PhaseSet phases, String fileName, final Collection<APEXContinuumPixel> bgPixels, final double[] sourceGain) throws IOException {
		final PrintWriter out = new PrintWriter(new FileOutputStream(fileName));	
		final APEXSubscan<?,?> subscan = (APEXSubscan<?,?>) phases.getIntegration();
		
		out.println("# CRUSH APEX Photometry Nod-cycle Data");
		out.println("# =============================================================================");
		out.println(subscan.getASCIIHeader());
		out.println("# Chop Frequency: " + Util.f3.format(subscan.getChopper().frequency / Unit.Hz) + "Hz"); 
		out.println("# Pixel: " + getID());
		out.println("# Source Phase: " + sourcePhase);
		out.println();
		out.println("# chop#\tSignal\t\tCorrected");
		
		final int N = phases.size();
		for(int i=1; i < N; i++) out.println("  " + i
				+ "\t" + getChopSignal(phases, i).toString(Util.e5)
				+ "\t" + getBGCorrectedChopSignal(phases, i, bgPixels, sourceGain).toString(Util.e5));
		
		out.println();
		out.println();
		out.close();
	}
	
	public void writeLRSpectrum(final PhaseSet phases, String fileName) throws IOException {
		final PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		final APEXSubscan<?,?> subscan = (APEXSubscan<?,?>) phases.getIntegration();
	
		out.println("# CRUSH APEX Photometry Nod-cycle Spectrum");
		out.println("# =============================================================================");
		out.println(subscan.getASCIIHeader());
		out.println("# Chop Frequency: " + Util.f3.format(subscan.getChopper().frequency / Unit.Hz) + "Hz"); 
		out.println("# Pixel: " + getID());
		out.println("# Source Phase: " + sourcePhase);
		out.println();
		out.println("# Freq(Hz)\tAmplitude\tPhase(deg)");
	
		final double[] data = new double[ExtraMath.pow2ceil(phases.size())];
		final double dF = 0.5 * subscan.getChopper().frequency / data.length;
		final int N = phases.size();
		final double mean = getLROffset(phases).value();
		
		for(int i=1; i < N; i++) {
			final WeightedPoint point = getChopSignal(phases, i);
			point.subtract(mean);
			data[i] = DataPoint.significanceOf(point);
		}
			
		new DoubleFFT().real2Amplitude(data);
			
		out.println(data[0]);
		for(int i=2; i < data.length; i+=2) {
			out.println("  " + (i*dF) + "\t" + Util.e5.format(ExtraMath.hypot(data[i], data[i+1])) + "\t" + Util.f5.format(Math.atan2(data[i+1], data[i])));
		}
		out.println(data[1]);
			
		out.println();
		out.println();
		out.close();
	}
	
	public WeightedPoint getLROffset(final PhaseSet phases) {
		int N = phases.size();
		final WeightedPoint lr = new WeightedPoint();
		for(int i=1; i < N; i++) lr.average(getChopSignal(phases, i));
		//lr.scaleWeight(0.5);
		return lr;
	}
	
	public WeightedPoint getMedianLROffset(final PhaseSet phases) {
	    WeightedPoint[] points = new WeightedPoint[phases.size()];
	    int k=0;
	    
        int N = phases.size();
        for(int i=1; i < N; i++) points[k++] = getChopSignal(phases, i);
        return Statistics.smartMedian(points, 0, k, 0.25);
    }
	
	public WeightedPoint getBGCorrectedLROffset(final PhaseSet phases, final Collection<APEXContinuumPixel> bgPixels, final double[] sourceGain) {
		int N = phases.size();
		final WeightedPoint lr = new DataPoint();
		for(int i=1; i < N; i++) lr.average(getBGCorrectedChopSignal(phases, i, bgPixels, sourceGain));
		//lr.scaleWeight(0.5);
		return lr;
	}

	public WeightedPoint getBGCorrectedMedianLROffset(final PhaseSet phases, final Collection<APEXContinuumPixel> bgPixels, final double[] sourceGain) {
        WeightedPoint[] points = new WeightedPoint[phases.size()];
        int k=0;
        
        int N = phases.size();
        for(int i=1; i < N; i++) points[k++] = getBGCorrectedChopSignal(phases, i, bgPixels, sourceGain);
        return Statistics.smartMedian(points, 0, k, 0.25);
    }
	
	
	public double getLRChi2(final PhaseSet phases, final double mean) {	
		final int N = phases.size();
		double chi2 = 0.0;
		int n = 0;
		
		for(int i=1; i < N; i++) {
			final WeightedPoint LR = getChopSignal(phases, i);
			if(LR.weight() <= 0.0) continue;
			
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		
		dof = (n + 1) - phases.channelParms[index];
		
		return dof > 0.0 ? chi2 / dof : Double.NaN;
	}

	public double getBGCorrectedLRChi2(final PhaseSet phases, final Collection<APEXContinuumPixel> bgPixels, final double mean, final double[] sourceGain) {	
		final int N = phases.size();
		double chi2 = 0.0;
		int n = 0;
		
		for(int i=1; i < N; i++) {
			final WeightedPoint LR = getBGCorrectedChopSignal(phases, i, bgPixels, sourceGain);
			if(LR.weight() == 0.0) continue;
			
			LR.subtract(mean);

			final double chi = DataPoint.significanceOf(LR);
			chi2 += chi * chi;
			n++;
		}
		
		dof = n - phases.channelParms[index];
		
		return dof > 0.0 ? chi2 / dof : Double.NaN;
	}
	
}
