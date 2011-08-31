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

import util.Range;
import util.data.FFT;
import util.data.Statistics;

import crush.Channel;
import crush.Integration;

public class WhiteningFilter extends AdaptiveFilter {
	double level = 1.2;
	boolean symmetric = false;
	
	int nF; // The number of stacked frequency components
	
	private double[] A, temp; // The amplitude at reduced resolution, and a temp storage for medians
	private int windows;
	private int measureFrom, measureTo;
	
	public WhiteningFilter(Integration<?, ?> integration) {
		super(integration);
		// TODO Auto-generated constructor stub
	}

	protected WhiteningFilter(Integration<?,?> integration, float[] data) {
		super(integration, data);
	}
	
	@Override
	public void updateConfig() {
		nF = integration.framesFor(integration.filterTimeScale);
		
		int windowSize = 2 * nF;
		
		level = hasOption("level") ? option("level").getDouble() : 2.0;
		Range measure = hasOption("proberange") ? option("proberange").getRange(true) : null; 
		symmetric = hasOption("below");
		
		int minProbeChannels = hasOption("minchannels") ? option("minchannels").getInt() : 16;
		
		windowSize = FFT.getPaddedSize(windowSize);
		
		// convert critical whitening power level to amplitude...
		level = Math.sqrt(level);
		
		int n = data.length;
		if(n < windowSize) windowSize = n;
		windows = n/windowSize;
		
		resize(nF);
	
		final double dF = 1.0 / (windowSize * integration.instrument.samplingInterval);
		
		int measureFrom = measure == null ? 1 : Math.max(1, (int) Math.floor(measure.min / dF));
		int measureTo = measure == null ? nF : Math.min(nF, (int) Math.ceil(measure.max / dF) + 1);
		
		// Make sure the probing range is contains enough channels
		// and that the range is valid...
		if(measureFrom > measureTo - minProbeChannels + 1) measureFrom = measureTo - minProbeChannels + 1;
		if(measureFrom < 0) measureFrom = 0;
		if(measureFrom > measureTo - minProbeChannels + 1) measureTo = Math.min(minProbeChannels + 1, nF);	
	}
	
	private void resize(int nF) {
		this.nF = nF;
		
		A = new double[nF];
		profile = new float[nF];
		temp = new double[nF];
	}
	
	// TODO channel.filterResponse vs profiles[c]
	@Override
	public void updateProfile(Channel channel) {
		// If the filterResponse array does not exist, create it...
		if(channel.filterResponse == null) {
			channel.filterResponse = new float[nF];
			Arrays.fill(channel.filterResponse, 1.0F);
			// TODO create the profile...
			// TODO resample the profile if necessary...
		}
		
		// Get the coarse average spectrum...
		for(int F=nF; --F >= 1; ) {
			double sumP = 0.0;
			final int fromf = 2 * F * windows;
			int tof = Math.min(fromf + 2 * windows, data.length);
			// Sum the power inside the spectral window...
			int pts = 0;
			for(int f=fromf; f<tof; f++) if(data[f] != 0.0) {
				sumP += data[f] * data[f];
				pts++;
			}
			
			// Add the Nyquist component to the last bin...
			if(F == nF-1) {
				sumP += data[1] * data[1];
				tof++;
			}
			// Set the amplitude equal to the rms power...
			A[F] = tof > fromf ? Math.sqrt(2.0 * sumP / pts) : 0.0;
		}	

		// Calculate the median amplitude
		System.arraycopy(A, 0, temp, 0, A.length);
		final double medA = Statistics.median(temp, measureFrom, measureTo);
		
		// Save the original amplitudes for later use...
		System.arraycopy(A, 0, temp, 0, A.length);
		
		final double sigmaA = medA / Math.sqrt(windows);
		final double critical = medA * level + 2.0 * sigmaA;
		final double criticalBelow = medA / level - 2.0 * sigmaA;
		
		// Only whiten those frequencies which have a significant excess power
		// when compared to the specified level over the median spectral power.
			
		Arrays.fill(profile, 1.0F); // To be safe initialize the scaling array here...
	
		// This is the main whitening filter...
		for(int F=nF; --F >= 1; ) if(A[F] > 0.0) {
			// Check if there is excess power that needs filtering
			if(A[F] > critical) profile[F] = (float) (medA / A[F]);
			else if(symmetric) if(A[F] < criticalBelow) profile[F] = (float) (medA / A[F]);
			
			// If it was filtered prior, see if the filtering can be relaxed (not to overdo it...)
			if(channel.filterResponse[F] < 1.0) if(A[F] < medA) profile[F] = (float) (medA / A[F]);
			else if(channel.filterResponse[F] > 1.0) if(A[F] > medA) profile[F] = (float) (medA / A[F]);
			
			if(A[F] > 0.0) A[F] *= profile[F];
			else A[F] = medA;
		}
	
		
		// Do a neighbour based round as well, with different resolutions
		// (like a feature seek).
		if(integration.hasOption("whiten.neighbours")) {
			int N = A.length;
			int maxBlock = N >> 2;
			double uncertainty = Math.sqrt(2.0 / windows);
			double limit = 1.0 + 3.0 * uncertainty;
			
			// Cheat to make sure the lack of a zero-f component does not cause trouble...
			A[0] = A[1];
			
			for(int blockSize = 1; blockSize <= maxBlock; blockSize <<= 1) {	
				final int N1 = N-1;

				for(int F = 1; F < N1; F++) if(A[F] > 0.0) {
					double maxA = Math.max(A[F-1], A[F+1]);
					if(A[F] > maxA * limit) {
						final double rescale = maxA / A[F];
						for(int blockf = 0, f = F*blockSize; blockf < blockSize; blockf++, f++) profile[f] *= rescale;
						A[F] = maxA;
					}
				}				

				for(int F = 0; F < N1; F += 2) A[F>>1] = 0.5 * Math.hypot(A[F], A[F+1]);
				N >>= 1;

				uncertainty /= Math.sqrt(2.0);
			}
		}
		
		// Renormalize the whitening scaling s.t. it is median-power neutral
		for(int F=nF; --F >= 1; ) temp[F] *= profile[F];
		double norm = medA / Statistics.median(temp, measureFrom, measureTo);
		if(Double.isNaN(norm)) norm = 1.0;
		
	}
	

	@Override
	public String getID() {
		return "wh";
	}

	@Override
	public String getConfigName() {
		return "whiten";
	}

	
}
