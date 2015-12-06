
/*
 * Pseudo code demonstrating the correlated noise removal process...
 * by Attila Kovacs -- attila[AT]submm.caltech.edu.
 *
 * Arguments:
 *
 *   data[t][c]		The data at time t for detector channel c.
 *
 *   nt				The number of time samples (first dimension of data[][])
 *
 *   nc				The number of detector channels (second dimension of data[][])
 *
 *   driftT			The resolution for the 1/f drift removal as a number of
 *   				samples. Set to below the intrinsic 1/f stability timescale
 *   				of the detectors. Alternatively, set to a value >= nt
 *   				to remove DC offsets only.
 *
 *   rounds			The number of iterations. Typically ~5 should suffice
 *   				and clean the input data[][] from correlated noise
 *   				(and 1/f drifts -- if also desired).
 *
 * Returns: the data[][] array cleaned from correlated noise, and (optionally)
 *          of 1/f drifts.
 */
void decorrelate(float[][] data, int nt, int nc, int driftT, int rounds) {

	/*
	 * The correlated noise removal is an iterative process. In each iteration
	 * one must first remove DC offsets, or 1/f drifts, from the timestream of
	 * each detector. Then estimate the correlated noise, and each detector's
	 * relative response to it. Finally, one should estimate proper noise
	 * weight for each detector channel...
	 */

	float[] C[nt];			// Initial correlated signal, all zeroes...
	float[] w[nc], G[nc];	// Initial detector gains and weights...

	// Set the initial detector gains and weights to 1.0...
	for(int c=0; c<nc; c++) w[c] = G[c] = 1.0F;

	// Iterate for the desired number of rounds
	for(int i=0; i<rounds; i++) {
		// Remove DC offsets or 1/f drifts...
		removeDrifts(data, nt, nc, driftT);

		// Remove the correlated noise, and update C[]
		updateCorrelated(data, nt, nc, C, G, w);

		// Remove the residual responses to correlated noise
		// and update G[]...
		updateGains(data, nt, nc, C, G, w);

		// Update w with proper noise weights...
		updateRMSNoiseWeights(data, nt, nc, w, driftT);
	}


	// At this point data[][] should be cleaned of correlated noise
	// (and 1/f drifts, if driftT was chosen < nt)

	// C[t] now contains the correlated noise in somewhat arbitrary units.
	// G[c] now contains the unnormalized relative response of each channel
	//      to the correlated noise C[].
	// w[c] now contains proper noise weights for each channel. You can
	//      use it to get an rms noise value for each channel, simply as:
	//
	//      rms[c] = 1.0 / sqrt(w[c]);
	//
	// That's all...

}



/*
 *	Does a single round of 1/f drift removal (or DC offset removal)
 *  from the timestream of each channel.
 *
 *  Arguments:
 *
 *   data[t][c]		The data at time t for detector channel c.
 *
 *   nt				The number of time samples (first dimension of data[][])
 *
 *   nc				The number of detector channels (second dimension of data[][])
 *
 *   driftT			The resolution for the drift removal as a number of
 *   				samples. Set to below the intrinsic 1/f stability timescale
 *   				of the detectors. Alternatively, set to a value >= nt
 *   				to remove DC offsets only.
 *
 *  Note, that in this simplified version, the drift values are not kept, only removed.
 *
 */
void removeDrifts(float[][] data, int nt, int nc, int driftT) {
	// Iterate through all channels...
	for(int c=0; c < nc; c++) {

		// Step through the timestream in blocks of driftT samples...
		for(int from = 0; from < nt; from += driftT) {
			int to = min(nt, from + driftT);

			// Estimate the detector DC offset inside the given time block...
			double sum = 0.0;
			for(int t=from; t < to; t++) sum += data[t][c];

			if(to > from) {
				// Remove the DC level from the timestream block...
				float level = (float) sum / (to - from);
				for(int t=from; t < to; t++) data[t][c] -= level;
			}
		}
	}
}



/*
 * Does a single round of correlated noise eatimation and removal
 * using the supplied detector gains and weights.
 *
 * Arguments:
 *
 *  data[t][c]		The data at time t for detector channel c.
 *
 *  nt				The number of time samples (first dimension of data[][])
 *
 *  nc				The number of detector channels (second dimension of data[][])
 *
 *  C[t]			The correlated noise value at time t. Has dimension nt.
 * 					Set all to 0.0 initially.
 *
 *  G[c]			The relative detector gain (to correlated noise) for channel c/
 * 					Set all to 1.0 initially.
 *
 *  w[c]			The relative detector weight for channel c.
 * 					Set all to 1.0 initially.
 *
 * Returns: the updated correlated noise signal C[].
 */
void updateCorrelated(float[][] data, int nt, int nc, float[] C, float[] G, float[] w) {

	// Iterate over all time samples
	for(int t=0; t<nt; t++) {
		double sum = 0.0, sumw = 0.0;

		// Estimate the residual correlated noise at time t
		// from all channels
		for(int c=0; c < nc; c++) {
			sum += w[c] * G[c] * data[t][c];
			sumw += w[c] * G[c] * G[c];
		}

		if(sumw > 0.0) {
			float dC = (float) (sum / sumw);
			// Remove the correlated noise from the data...
			for(int c=0; c < nc; c++) data[t][c] -= G[c] * dC;
			// Update C[t]
			C[t] += dC;
		}
	}
}

/*
 * Does a single round of gain estimation and removal of residual
 * responses to the correlated noise C[t].
 *
 * Arguments are the same as above.
 *
 * Note, in this simplified routine, gain normalization is not enforced. As such,
 * the units of the correlated noise C[t] are somewhat arbitrary...
 *
 * Returns: the updated relative detector responses G[c]
 *
 */
void updateGains(float[][] data, int nt, int nc, float[] C, float[] G, float[] w) {

	// Iterate over all detector channels
	for(int c=0; c < nc; c++) {
		double sum = 0.0, sumw = 0.0;

		// Estimate the channel gain increment based on the
		// residual response to the correlated noise.
		for(int t=0; t < nt; t++) {
			sum += C[t] * data[t][c];
			sumw += C[t] * C[t];
		}

		if(sumw > 0.0) {
			float dG = (float) (sum / sumw);
			// Remove the residual correlated noise response from the data...
			for(int t=0; t < nt; t++) data[t][c] -= dG * C[t];
			// Update the relative detector gain G[c]
			G[c] += dG;
		}
	}
}


/*
 * Does a single round of RMS noise weighting for each detector channel
 * based on the observed RMS noise in the residuals.
 *
 * Note, you should call this after the removal of offsets or 1/f drifts, and
 * after both the correlated noise and the gains have been estimated at least once...
 *
 * Arguments are the same as above.
 *
 * Returns: The updated detector noise weights w[]
 *
 */
void updateRMSNoiseWeights(float[][] data, int nt, int nc, float[] w, int driftT) {
	// Calculate the weight sum, needed to estimate the lost
	// degrees of freedom due to correlated noise removal and
	// gain estimation...
	double sumw = 0.0;
	for(int c=0; c < nc; c++) sumw += w[c];

	// The degrees of freedom from nt timestream samples
	// is reduced by w[c]/sumw for each sample (since that is how
	// much each sample contributes to the correlated noise
	// estimate at each sample), plus 1 for the estimated gain
	// for this channels.
	// Plus, the number of 1/f drift values that were derived.
	double dof = nt * (1.0 - w[c] / sumw) - (1.0 + ceil((float) nt / driftT));

	// Iterate over all detector channels
	for(int c=0; c < nc; c++) {
		// Measure the variance in the timestream of channel c
		double var = 0.0;
		for(int t=0; t < nt; t++) var += data[t][c] * data[t][c];

		if(dof > 0.0) w[c] = (float) dof / var;
		else w[c] = 0.0F;
	}
}


