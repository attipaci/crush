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
package gov.nasa.gsfc.gismo.pixelanalyzer;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * @author Attila Kovacs <attila[AT]submm.caltech.edu>
 * @version 0.1-2
 * 
 * Changes:
 * 
 * 	0.1-2
 * 		- Fixed NEFD units conversion :-). Also NEFD is now in units of mJy sqrt(s)...
 * 		- Added Pixel.getLetterCode() to return a String, which describes the pixel status
 * 	 	  by a letter code, or as an empty String "" for good pixels.
 * 		- Changed acceptable gain range, to allow more relatively high gain pixels.
 * 		- Cleaned up error messages.
 * 
 *  0.1-1
 *  	- Initial release (18 Feb 2011)
 */

public class SkyRemover {
	/** 
	 * This is the array of gismo pixels. So there will be 128 of these... 
	 */
	public ArrayList<Pixel> pixels;
	public double minGain = 0.3, maxGain = 3.0;
	public double minNoise = 0.3, maxNoise = 3.0;
	
	private boolean isCurrent = false;
	private WeightedPoint[] pixelTemp;

	/** 
	 * The Frame is a representation of an array at a given time, or in a given
	 * time interval. Think of it as a snapshot. As such it contains 128 DAC values
	 * one for each pixel... Apart from that, the frame can have descriptive field
	 * such as time stamps, flags, etc., but we don't need any of it here...
	 */
	static class Frame {
		float[] data;
		double correlated = 0.0; // this is the correlated sky component;
		public boolean isLevelled = false;
		
		public void setData(float[] data) {
			this.data = data;
			correlated = 0.0;
			isLevelled = false;
		}
		
		public float[] getData() {
			return data;
		}
	}
	
	/** 
	 * This is the real-time sky noise removal, using the current set of pixel parameters
	 * (gains, weights, offsets and flags).
	 * 
	 * @param frame 
	 * A frame from which to remove the sky noise
	 * 
	 */
	public synchronized void decorrelate(Frame frame) {
		// Remove the pixel offsets
		if(!frame.isLevelled) {
			for(int c = pixels.size(); --c >= 0; ) frame.data[c] -= pixels.get(c).offset;
			frame.isLevelled = true;
		}
		// Then remove the correlated component
		getCorrelated(frame);
	}

	
	/** This method updates the pixel weights, gains and flags, based on a set of
	 *  Frames covering ~30s--1min time window..
	 *
	 *  @param buffer 
	 *  is the buffered data, for some amount of time. Each frame is a
	 *  time average snapshot of the array, at say ~10 Hz, and the full buffer 
	 *  is maybe ~30s or 1 minute long. Thus typically you would have a few 
	 *  hundred frames in it...
	 *  
	 *  @param iterations
	 *  A single iteration might be sufficient. Additional iterations might
	 *  improve a little bit, but no more that a few (3-5) should be ever needed...
	 */
	public synchronized void updatePixels(ArrayList<Frame> buffer, int iterations) {
		if(buffer.size() <= 1) return;
		
		final Frame firstFrame = buffer.get(0);
		if(pixels == null) initPixels(firstFrame);
		else if(pixels.size() != firstFrame.data.length) initPixels(firstFrame);
			
		// Remove the DC offset. No need to iterate on it, since no despiking/flagging
		// of samples takes place to alter it...
		try { getOffsets(buffer); }
		catch(IllegalStateException e) { 
			System.err.println("WARNING! " + getClass().getSimpleName() + "> " + e.getMessage());
		}
		
		// Now iterate to solve gains and weights...
		for(int i=iterations; --i >= 0; ) {
			getCorrelated(buffer);
			getGains(buffer);
			getWeights(buffer);
			isCurrent = true;
		}
	}
	
	/** Allows to set the range of acceptable gains (default is 0.3, 3.0). If the
	 * pixel gain falls below the minimum, it is flagged with Pixel.FLAG_BLIND.
	 * When above the maximum value, the pixel is flagged with Pixel.FLAG_HIGAIN.
	 * Too high gains probably indicate a pixel that went nuts...
	 * 
	 * @param min 
	 * The minimum relative gain to accept (default is 0.3).
	 * 
	 * @param max
	 * The maximum relative gain to accept (default is 3.0).
	 */
	
	public void setGainRange(double min, double max) {
		minGain = min;
		maxGain = max;
	}
	
	
	/**
	 * Set the relative noise levels, compared to the average pixel noise, which
	 * are acceptable. The default is 0.3, 3.0. When the pixel noise is below
	 * the minimum value, it is flagged with Pixel.FLAG_QUIET. Above the maximum
	 * value the pixel is flagged with Pixel.FLAG_NOISY.
	 * 
	 * @param min 
	 * The minimum relative noise level to accept (default is 0.3).
	 * 
	 * @param max
	 * The maximum relative noise level to accept (default is 3.0).
	 */
	public void setNoiseRange(double min, double max) {
		minNoise = min;
		maxNoise = max;
	}
	
	
	/**
	 * Returns the pixel sensitivities (NEFD) as an array, with one number per pixel.
	 *  
	 * @param countsPerJansky
	 * is the calibration factor, which converts counts to janskys
	 * E.g. for April 2010 it was around 7.2 [counts/jansky].
	 * 
	 * @param samplingInterval
	 * The sampling interval (i.e. the length of each frame) in seconds.
	 * 
	 * @return 
	 * An array of the pixel nefd values in mJy sqrt(s). 
	 * The array may contain Double.NaNs for pixels, whose noise/gain was not measured.
	 */
	public double[] getSensitivities(double countsPerJansky, double samplingInterval) {
		double[] nefd = new double[pixels.size()];
		for(int c = nefd.length; --c >= 0; ) {
			final Pixel pixel = pixels.get(c);
			if(pixel.isNoiseWeight) nefd[c] = 1000.0 * Math.sqrt(samplingInterval / pixel.weight) / (pixel.gain * countsPerJansky);
			else nefd[c] = Double.NaN;
		}
		return nefd;
	}
	
	/**
	 * 
	 * @return 
	 * true if the gains and weights were derived from actual data, false if these quantities
	 * are set to their initial defaults.
	 */
	public boolean isCharacterized() {
		return isCurrent;
	}
	
	/**
	 * Resets the analyzer state, by returning all pixels to their default values.
	 */
	public synchronized void reset() {
		for(Pixel pixel : pixels) pixel.reset();	
	}

	private void initPixels(Frame frame) {
		final int n = frame.data.length;
		pixels = new ArrayList<Pixel>(n);
		pixelTemp = new WeightedPoint[n];
		for(int i=0; i<n; i++ ) {
			pixels.add(new Pixel());
			pixelTemp[i] = new WeightedPoint();
		}
	}

	private void getOffsets(ArrayList<Frame> buffer) throws IllegalStateException {
		// Temporary storage for calculating medians...
		final float[] temp = new float[buffer.size()];
		
		for(int c = pixels.size(); --c >= 0; ) {
			// First calculate the DC offset for each pixel.
			for(int t = buffer.size(); --t >= 0; ) {
				final Frame frame = buffer.get(t);
				if(frame.isLevelled) throw new IllegalStateException("Frame was already leveled.");
				temp[t] = frame.data[c];
			}
			final float level = Statistics.median(temp);
			
			final Pixel pixel = pixels.get(c);
			pixel.offset = level;
			
			pixel.unflag(Pixel.DAC_FLAGS);
			if(pixel.offset < Pixel.DAC_LO) pixel.flag(Pixel.FLAG_DAC_LO);
			else if(pixel.offset > Pixel.DAC_HI) pixel.flag(Pixel.FLAG_DAC_HI);
		}
		
		for(Frame frame : buffer) {
			for(int c = pixels.size(); --c >= 0; ) frame.data[c] -= pixels.get(c).offset;
			frame.isLevelled = true;
		}
	}

	public synchronized void getCorrelated(Frame frame) {
		if(pixels == null) initPixels(frame);
		else if(pixels.size() != frame.data.length) initPixels(frame);
		
		int n = 0;
		for(int c = pixels.size(); --c >= 0; ) {
			final Pixel pixel = pixels.get(c);
			if(pixel.flag != 0) continue; // Skip over flagged pixels...
			if(pixel.gain == 0.0) continue;
			if(pixel.weight == 0.0) continue;
			
			final WeightedPoint point = pixelTemp[n++];
			point.value = frame.data[c] / pixel.gain;
			point.weight = pixel.weight * pixel.gain * pixel.gain;
			
			// Safety check...
			if(Double.isNaN(point.value)) point.noData();
		}
		if(n == 0) return;
		
		final float dC = (float) Statistics.smartMedian(pixelTemp, 0, n, 0.25);
		
		frame.correlated += dC;
		for(int c = pixels.size(); --c >= 0; ) frame.data[c] -= pixels.get(c).gain * dC;

	}
	
	private void getCorrelated(ArrayList<Frame> buffer) {
		for(Frame frame : buffer) decorrelate(frame);	
	}
	
	private void getGains(ArrayList<Frame> buffer) {
		WeightedPoint[] temp = new WeightedPoint[buffer.size()];
		for(int i=temp.length; --i >= 0; ) temp[i] = new WeightedPoint();
		
		for(int c = pixels.size(); --c >= 0; ) {
			final Pixel pixel = pixels.get(c);
			
			int n = 0;
			for(Frame frame : buffer) if(frame.correlated != 0.0) {
				WeightedPoint point = temp[n++];
				point.value = frame.data[c] / frame.correlated;
				point.weight = frame.correlated * frame.correlated;
			}
			if(n == 0) {				
				System.err.println("WARNING! " + getClass().getSimpleName() + "> has no correlated noise.");
				return;
			}
			
			final float dG = (float)Statistics.smartMedian(temp, 0, n, 0.25);
			
			pixel.gain += dG;
			pixel.isGainMeasured = true;
			
			for(Frame frame : buffer) frame.data[c] -= dG * frame.correlated;			
		}
		System.err.println();
	
		normalizeGains(buffer);
		flagGains();
	}
	
	private void normalizeGains(ArrayList<Frame> buffer) {
		final double[] temp = new double[pixels.size()];
		final int excludeFlags = Pixel.DAC_FLAGS;
		
		// Use the gains from pixels, which are not flagged otherwise
		int n=0;
		for(Pixel pixel : pixels) if(pixel.isUnflagged(excludeFlags)) temp[n++] = Math.abs(pixel.gain);
		Arrays.sort(temp, 0, n);
		
		// Ignore the 5% on both tails of the gain distribution...
		final int dn = (int) Math.round(0.05*n);
		final int N = n - 2*dn;
		if(N == 0) return;

		// Average over the middle 90% of gains...
		double sum = 0.0;
		for(int i = n-dn; --i >= dn; ) sum += temp[i];
		final double aveG = sum / N;
		if(aveG == 0.0) return;
	
		for(Pixel pixel : pixels) pixel.gain /= aveG;
		for(Frame frame : buffer) frame.correlated *= aveG;
	}
	
	private void flagGains() {
		for(Pixel pixel : pixels) {
			pixel.unflag(Pixel.GAIN_FLAGS);
			if(pixel.gain < minGain) pixel.flag(Pixel.FLAG_BLIND);
			else if(pixel.gain > maxGain) pixel.flag(Pixel.FLAG_HIGAIN);
		}
	}
	
	private void getWeights(ArrayList<Frame> buffer) {
		// Temporary storage for calculating medians...
		final float[] temp = new float[buffer.size()];
		
		for(int c = pixels.size(); --c >= 0; ) {
			for(int t = buffer.size(); --t >= 0; ) {
				final float value = buffer.get(t).data[c];
				temp[t] = value * value;
			}
			final Pixel pixel = pixels.get(c);
			final double var = Math.sqrt(Statistics.median(temp));
			pixel.weight = var > 0.0 ? 1.0 / Math.sqrt(var / 0.454937) : 0.0;
			pixel.isNoiseWeight = true;
		}
		
		flagNoise();
	}
	
	private void flagNoise() {
		final double[] temp = new double[pixels.size()];
		final int excludeFlags = Pixel.DAC_FLAGS;
		
		int n=0;
		for(Pixel pixel : pixels) if(pixel.isUnflagged(excludeFlags)) temp[n++] = pixel.weight;		
		Arrays.sort(temp);
		
		final int dn = (int) Math.round(0.05*n);
		final int N = pixels.size() - 2*dn;
		if(N == 0) return;
		
		double sum = 0.0;
		for(int i = pixels.size()-dn; --i >= dn; ) sum += temp[i];
		final double avew = sum / N;
		
		final double minw = avew / (maxNoise * maxNoise);
		final double maxw = avew / (minNoise * minNoise);
		
		for(Pixel pixel : pixels) {
			pixel.unflag(Pixel.NOISE_FLAGS);
			if(pixel.weight < minw) pixel.flag(Pixel.FLAG_NOISY);
			else if(pixel.weight > maxw) pixel.flag(Pixel.FLAG_QUIET);
		}
	}

}



// A The pixel class contains information on a specific pixel, such as gains
// weights, flags, etc...
// Typically, you may want to be able to read/write this information into some
// simple ASCII output. Even better, if this is in the CRUSH format :-)
class Pixel {
	public double gain = 1.0;
	public double weight = 1.0;
	public int flag = 0;
	public double offset = 0.0;
	
	public boolean isNoiseWeight = false; // whether the weight is a proper noise weight...
	public boolean isGainMeasured = false; // whether the gain is measured from data...
	
	// This is just a simple function to set the flag bits of pattern...
	public void flag(int pattern) {
		flag |= pattern;
	}
	
	// ... and a simple function to clear the flag bits of pattern...
	public void unflag(int pattern) {
		flag &= ~pattern;
	}
	
	public boolean isFlagged(int pattern) {
		return (flag & pattern) != 0;
	}
	
	public boolean isUnflagged(int pattern) {
		return (flag & pattern) == 0;
	}
	
	public String getLetterCode() {
		if(flag == 0) return "";
		else if(isFlagged(FLAG_DAC_LO)) return "L"; // [L]ow-end of DAC range
		else if(isFlagged(FLAG_DAC_HI)) return "H"; // [H]igh-end of DAC range
		else if(isFlagged(FLAG_BLIND)) return "B"; // [B]lind
		else if(isFlagged(FLAG_NOISY)) return "N"; // [N]oisy
		else if(isFlagged(FLAG_HIGAIN)) return "G"; // wacky [G]ain
		else if(isFlagged(FLAG_QUIET)) return "Q"; // too [Q]uiet
		else return "?"; // Some other problem[?]
	}
	
	public void reset() {
		gain = 1.0;
		weight = 1.0;
		offset = 0.0;
		flag = 0;
	}

	/*
	private void check(ArrayList<Frame> buffer, String label) {
		for(Frame frame : buffer) check(frame, label);
	}
	
	private void check(Frame frame, String label) {
		for(int c=pixels.size(); --c >= 0; ) if(Float.isNaN(frame.data[c])) {
			System.err.println("NaN: " + label);
			System.exit(0);
		}
	}
	*/
		
	
	// Some example flags for the pixel. We may think of defining more ways that
	// a pixel can be dead... So let's leave it open. Let's just assume that
	// good pixels are unflagged, i.e. that flag == 0.
	public static int FLAG_BLIND = 1<<0;
	public static int FLAG_HIGAIN = 1<<1;
	public static int FLAG_NOISY = 1<<2;
	public static int FLAG_QUIET = 1<<3;
	public static int FLAG_DAC_HI = 1<<4;
	public static int FLAG_DAC_LO = 1<<5;
	
	public static int GAIN_FLAGS = FLAG_BLIND | FLAG_HIGAIN;	
	public static int NOISE_FLAGS = FLAG_NOISY | FLAG_QUIET;
	public static int DAC_FLAGS = FLAG_DAC_HI | FLAG_DAC_LO;
	
	public static int DAC_HI = 16284;
	public static int DAC_LO = 100;
	
}


class Statistics {
	public static double median(double[] data) { return median(data, 0, data.length); }

	public static double median(double[] data, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		int n = toIndex - fromIndex;
		return n % 2 == 0 ? 0.5 * (data[fromIndex + n/2-1] + data[fromIndex + n/2]) : data[fromIndex + (n-1)/2];
	}

	public static float median(float[] data) { return median(data, 0, data.length); }

	public static float median(float[] data, int fromIndex, int toIndex) {
		Arrays.sort(data, fromIndex, toIndex);
		int n = toIndex - fromIndex;
		return n % 2 == 0 ? 0.5F * (data[fromIndex + n/2-1] + data[fromIndex + n/2]) : data[fromIndex + (n-1)/2];
	}

	public static double median(WeightedPoint[] data) { return median(data, 0, data.length); }
	
	public static double median(WeightedPoint[] data, int fromIndex, int toIndex) {
		return smartMedian(data, fromIndex, toIndex, 1.0);		
	}
	
	public static double smartMedian(final WeightedPoint[] data, final int fromIndex, final int toIndex, final double maxDependence) {
		if(toIndex - fromIndex == 1) return data[fromIndex].value;

		Arrays.sort(data, fromIndex, toIndex);

		// wt is the sum of all weights
		// wi is the integral sum including the current point.
		double wt = 0.0, wmax = 0.0;
		
		for(int i=fromIndex; i<toIndex; i++) {
			final double w = data[i].weight;
			if(w > wmax) wmax = w;
			if(w > 0.0) wt += w;
		}

		// If a single datum dominates, then return the weighted mean...
		if(wmax >= maxDependence * wt) {
			double sum=0.0, sumw=0.0;
			for(int i=fromIndex; i<toIndex; i++) {
				final double w = data[i].weight;
				if(w > 0.0) {
					sum += w * data[i].value;
					sumw += w;
				}
			}
			return sum/sumw;
		}
		
		// If all weights are zero return the arithmetic median...
		// This should never happen, but just in case...
		if(wt == 0.0) {
			final int n = toIndex - fromIndex;
			return n % 2 == 0 ? 0.5F * (data[fromIndex + n/2-1].value + data[fromIndex + n/2].value) : data[fromIndex + (n-1)/2].value;
		}


		final double midw = wt / 2.0; 
		int ig = fromIndex; 
		
		WeightedPoint last = WeightedPoint.NaN;
		WeightedPoint point = data[fromIndex];

		double wi = point.weight;	
		
		while(wi < midw) if(data[++ig].weight > 0.0) {
			last = point;
			point = data[ig];	    
			wi += 0.5 * (last.weight + point.weight);    
		}
		
		double wplus = wi;
		double wminus = wi - 0.5 * (last.weight + point.weight);
		
		double w1 = (wplus - midw) / (wplus + wminus);
		return w1 * last.value + (1.0-w1) * point.value;
	}
	
}

// Just a convenient data class, for noise weighted data. The weight is 1/variance.
class WeightedPoint implements Comparable<WeightedPoint> {
	public double value, weight;
	
	public WeightedPoint() { this(0.0, 0.0); }
	
	public WeightedPoint(double value, double weight) {
		this.value = value;
		this.weight = weight;
	}
	
	public int compareTo(WeightedPoint point) throws ClassCastException {
		return Double.compare(value, point.value);
	}
	
	public void noData() {
		value = 0.0;
		weight = 0.0;
	}
	
	public final static WeightedPoint NaN = new WeightedPoint(0.0, 0.0);

}
