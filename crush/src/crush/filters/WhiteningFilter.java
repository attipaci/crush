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

import util.Configurator;
import util.Range;
import util.data.DataPoint;
import util.data.OldFFT;
import util.data.Statistics;

import crush.Channel;
import crush.Integration;

public class WhiteningFilter extends AdaptiveFilter {
	double level = 1.2;
	double significance = 2.0;
	double maxBoost = 2.0;
	boolean boost = false;
	//boolean neighbours = false;
	
	private int nF; // The number of stacked frequency components
	private DataPoint[] A, temp; // The amplitude at reduced resolution, and a temp storage for median
	private int windows; // The number of stacked windows...
	private int whiteFrom, whiteTo; // The frequency channel indexes of the white-level measuring range
	
	
	public WhiteningFilter(Integration<?, ?> integration) {
		super(integration);
	}

	public WhiteningFilter(Integration<?,?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	protected void setIntegration(Integration<?,?> integration) {	
		super.setIntegration(integration);
	}
	
	@Override
	public void updateConfig() {	
		super.updateConfig();
		
		setSize(integration.framesFor(integration.filterTimeScale));
		
		boost = hasOption("below");
		
		// Specify maximum boost as power, just like level...
		if(hasOption("below.max")) maxBoost = Math.max(1.0, Math.sqrt(option("below.max").getDouble()));
		
		//neighbours = hasOption("neighbours");
		
		// Specify critical level as power, but use as amplitude...
		if(hasOption("level")) level = Math.max(1.0, Math.sqrt(option("level").getDouble()));
		
		int windowSize = OldFFT.getPaddedSize(2 * nF);
		int n = data.length;
		if(n < windowSize) windowSize = n;
		windows = n / windowSize;
		
		Range probe = new Range(0.0, nF * dF);
				
		if(hasOption("proberange")) { 
			Configurator spec = option("proberange");
			if(spec.getValue().equalsIgnoreCase("auto")) {
				double fPnt = 1.0 / integration.getPointCrossingTime();
				probe = new Range(0.5 * fPnt, fPnt);
			}
			else probe = option("proberange").getRange(true);
		}
		
		probe.restrict(0, nF);
		
		whiteFrom = Math.max(1, (int) Math.floor(probe.min() / dF));
		whiteTo = Math.min(nF, (int) Math.ceil(probe.max() / dF) + 1);
		
		// Make sure the probing range is contains enough channels
		// and that the range is valid...
		int minProbeChannels = hasOption("minchannels") ? option("minchannels").getInt() : 16;
		if(whiteFrom > whiteTo - minProbeChannels + 1) whiteFrom = whiteTo - minProbeChannels + 1;
		if(whiteFrom < 0) whiteFrom = 0;
		if(whiteFrom > whiteTo - minProbeChannels + 1) whiteTo = Math.min(minProbeChannels + 1, nF);
		
		
	}
	
	@Override
	protected void setSize(int nF) {
		if(this.nF == nF) return;
	
		super.setSize(nF);
		
		this.nF = nF;
		
		A = new DataPoint[nF];
		temp = new DataPoint[nF];
	
		for(int i=nF; --i >= 0; ) A[i] = new DataPoint();	
	}	
	
	@Override
	public void updateProfile(Channel channel) {
		calcMeanAmplitudes(channel);
		whitenProfile(channel);
	}
	
	private void calcMeanAmplitudes(Channel channel) {
		// If the filterResponse array does not exist, create it...
		if(profiles[channel.index] == null) {
			profiles[channel.index] = new float[nF];
			Arrays.fill(profiles[channel.index], 1.0F);
		}
		
		// Get the coarse average spectrum...
		for(int F=nF; --F >= 0; ) {
			final int fromf = Math.max(2, 2 * F * windows);
			final int tof = Math.min(fromf + 2 * windows, data.length);
			
			double sumP = 0.0;
			int pts = 0;
			
			// Sum the power inside the spectral window...
			// Skip over channels that have been killed by KillFilter...
			for(int f=tof; --f >= fromf; ) if(data[f] != 0.0) {
				sumP += data[f] * data[f];
				pts++;
			}
			
			// Set the amplitude equal to the rms power...
			// The full power is the sum of real and imaginary components...
			A[F].setValue(pts > 0 ? Math.sqrt(2.0 * sumP / pts) : 0.0);
			A[F].setWeight(pts);
		}	

		// Add the Nyquist component to the last bin...
		// Skip if it has been killed by KillFilter...
		if(data[nF - 1] != 0.0) {
			final DataPoint nyquist = A[nF-1];
			nyquist.setValue(nyquist.weight() * nyquist.value() * nyquist.value() + 2.0 * data[1] * data[1]);
			nyquist.addWeight(1.0);
			nyquist.setValue(Math.sqrt(nyquist.value() / nyquist.weight()));
		}		
	
	}
	
	private void whitenProfile(Channel channel) {

		Arrays.fill(profile, 1.0F); // To be safe initialize the scaling array here...
		final float[] lastProfile = profiles[channel.index];
		
		// Calculate the median amplitude
		System.arraycopy(A, 0, temp, 0, A.length);	
		final double medA = Statistics.median(temp, whiteFrom, whiteTo).value();
		final double critical = level * medA;
		
		// sigmaP = medP / sqrt(pts)
		// A = sqrt(P)
		// dA = 0.5 / sqrtP * dP
		// sigmaA = 0.5 / medA * sigmaP = 0.5 * medA / sqrt(pts)
		// wA = 4 * pts / (medA * medA) 
		final double weightScale = 4.0 / (medA * medA);
		for(int F=nF; --F >= 0; ) A[F].scaleWeight(weightScale);
		
		// Only whiten those frequencies which have a significant excess power
		// when compared to the specified level over the median spectral power.
		for(int F=nF; --F >= 1; ) {
			if(A[F].weight() > 0.0) {
				final double dev = (A[F].value() / lastProfile[F] - critical) / A[F].rms();
				
				// Check if there is excess/deficit power that needs filtering...
				if(dev > significance) profile[F] = (float) (medA / A[F].value());
				else if(boost && dev < -significance) {
					profile[F] = (float) (medA / A[F].value());
					// Make sure not too overboost...
					if(profile[F]*lastProfile[F] > maxBoost) profile[F] = (float) maxBoost / lastProfile[F];
				}
				// If there is no significant deviation, undo the last filtering...
				else profile[F] = 1.0F / lastProfile[F];
					
				A[F].scale(profile[F]);
			}
			else A[F].setValue(medA);
		}
		
		// Do a neighbour based round as well, with different resolutions
		// (like a feature seek).
		// TODO Fix or eliminate neighbour-based whitening...
		//if(neighbours) profileNeighbours();
		
		// TODO replace neighbour-based whitening
		// with multires whitening...
		// decimate by averaging power in neighbouring bins
		// and reapply standard whitening fn...
		
		// Renormalize the whitening scaling s.t. it is median-power neutral
		//double norm = medA / Statistics.median(temp, whiteFrom, whiteTo).value;
		//if(Double.isNaN(norm)) norm = 1.0;		
		//for(int F = nF; --F >= 0; ) profile[F] *= norm;
		
	}
	
	/*
	protected void profileNeighbours() {
		int N = A.length;
		int maxBlock = N >> 2;
		
		// Cheat to make sure the lack of a zero-f component does not cause trouble...
		A[0].copy(A[1]);
		DataPoint d1 = new DataPoint();
		DataPoint d2 = new DataPoint();
		
		for(int blockSize = 1; blockSize <= maxBlock; blockSize <<= 1) {	
			final int N1 = N-1;

			for(int F = 1; F < N1; F++) if(A[F].weight > 0.0) {
				d1.copy(A[F]);
				d1.subtract(A[F-1]);
				
				d2.copy(A[F]);
				d2.subtract(A[F+1]);
				
				double s1 = Math.signum(d1.value) * d1.significance();
				double s2 = Math.signum(d2.value) * d2.significance();
				
				double max = s1 > s2 ? A[F-1].value : A[F+1].value;				
				double sig = Math.max(s1, s2);
						
				if(sig > 3.0) {
					final double rescale = max / A[F].value;
					for(int blockf = 0, f = F*blockSize; blockf < blockSize; blockf++, f++) profile[f] *= rescale;
					A[F].value = max;
				}
			}				

			// A' = sqrt(A1^2 + A2^2)
			// dA' / dA1 = 0.5 / A' * 2*A1 = A1/A';
			// s'2 = (A1/A' * s1)^2 + (A2/A' * s2)^2
			// s'   = hypot(A1*s1, A2*s2) / A'
			// 1.0 / wA' = (A1/A')^2 / w1 + (A2/A')^2 / w2
			
			for(int F = 0; F < N1; F += 2) {
				final double A1 = Math.hypot(A[F].value, A[F+1].value);
				final double var = (A[F].value * A[F].value / A[F].weight + A[F+1].value * A[F+1].value / A[F+1].weight) / (A1 * A1);
				final int F1 = F >> 1;
				A[F1].value = A1;
				A[F1].weight = 1.0 / var;
			}
			N >>= 1;
		}
	}
	*/
	
	@Override
	public String getID() {
		return "wh";
	}

	@Override
	public String getConfigName() {
		return "filter.whiten";
	}

	
}
