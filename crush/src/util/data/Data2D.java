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

package util.data;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import util.Range;
import util.Unit;
import util.Util;
import util.Vector2D;
import crush.sourcemodel.AstroImage;

public class Data2D implements Cloneable {
	public double[][] data;
	public int[][] flag;
	public Unit unit = Unit.unity;

	public String contentType = "";
	
	public boolean verbose = false;
	
	// 2 pi sigma^2 = a^2
	// a = sqrt(2 pi) sigma
	//   = sqrt(2 pi) fwhm / 2.35
	public static double fwhm2size = Math.sqrt(2.0 * Math.PI) / Util.sigmasInFWHM;

	public int interpolationType = BICUBIC_SPLINE;
	
	public final static int NEAREST_NEIGHBOR = 0;
	public final static int BILINEAR = 1;
	public final static int PIECEWISE_QUADRATIC = 2;
	public final static int BICUBIC_SPLINE = 3;
		
	public Data2D() {
		Locale.setDefault(Locale.US);
	}
	
	public Data2D(int sizeX, int sizeY) {
		this();
		setSize(sizeX, sizeY);
	}
	
	public Data2D(double[][] data) {
		this();
		this.data = data;
		noFlag();
	}
	
	public Data2D(double[][] data, int[][] flag) {
		this();
		this.data = data;
		this.flag = flag;
	}
	
	@Override
	public Object clone() {
		try { 
			Data2D clone = (Data2D) super.clone(); 
			clone.ax = null;
			clone.ay = null;
			clone.interpolating = new AtomicBoolean(false);
			return clone;
		}
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public Data2D copy() {
		Data2D copy = (Data2D) clone();
		copy.copyImageOf(this);
		return copy;
	}
	

	public void copyImageOf(Data2D image) {
		// Make a copy of the fundamental data
		data = (double[][]) copyOf(image.data);
		flag = (int[][]) copyOf(image.flag);		
	}
	
	public void setImage(Data2D image) {
		data = image.data;
		flag = image.flag;		
	}
	
	public final int sizeX() { return data.length; }
	
	public final int sizeY() { return data[0].length; }
	
	public void setSize(int x, int y) {
		data = new double[x][y];
		flag = new int[x][y];
		while(--x >= 0) Arrays.fill(flag[x], 1); 
	}
	
	public void noFlag() {
		flag = new int[sizeX()][sizeY()];		
	}
	
	

	public void copyImageOf(double[][] image) { 
		for(int i=sizeX(); --i >= 0; ) System.arraycopy(image[i], 0, data[i], 0, sizeY());
	}

	public void addImage(double[][] image, double scale) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) data[i][j] += scale * image[i][j];
	}

	public void addImage(double[][] image) { addImage(image, 1.0); }

	public void addValue(double x) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) data[i][j] += x;
	}
	
	
	public void clear() {
		for(int i=sizeX(); --i >= 0; ) {
			Arrays.fill(data[i], 0.0);
			Arrays.fill(flag[i], 1);
		}
	}


	public final double valueAt(final Index2D index) {
		return valueAtIndex(index.i, index.j);
	}
	
	public double valueAtIndex(int i, int j) { return flag[i][j] == 0 ? data[i][j] : Double.NaN; }
	
	public double valueAtIndex(Vector2D index) {
		return valueAtIndex(index.x, index.y);
	}
	
	
	public double valueAtIndex(double ic, double jc) {
		
		// The nearest data point (i,j)
		final int i = (int) Math.round(ic);
		final int j = (int) Math.round(jc);
		
		if(!isValid(i, j)) return Double.NaN;
		
		switch(interpolationType) {
		case NEAREST_NEIGHBOR : return data[i][j];
		case BILINEAR : return bilinearAt(ic, jc);
		case PIECEWISE_QUADRATIC : return piecewiseQuadraticAt(ic, jc);
		case BICUBIC_SPLINE : return splineAt(ic, jc);
		}
		
		return Double.NaN;
	}
	
	// Bilinear interpolation
	public double bilinearAt(double ic, double jc) {		
		final int i = (int)Math.floor(ic);
		final int j = (int)Math.floor(jc);
		
		final double di = ic - i;
		final double dj = jc - j;
		
		double sum = 0.0, sumw = 0.0;
		
		if(isValid(i, j)) {
			double w = (1.0 - di) * (1.0 - dj);
			sum += w * data[i][j];
			sumw += w;			
		}
		if(isValid(i+1, j)) {
			double w = di * (1.0 - dj);
			sum += w * data[i+1][j];
			sumw += w;	
		}
		if(isValid(i, j+1)) {
			double w = (1.0 - di) * dj;
			sum += w * data[i][j+1];
			sumw += w;	
		}
		if(isValid(i+1, j+1)) {
			double w = di * dj;
			sum += w * data[i+1][j+1];
			sumw += w;	
		}

		return sum / sumw;
	}
	
	
	// Interpolate (linear at edges, quadratic otherwise)	
	// Piecewise quadratic...
	public double piecewiseQuadraticAt(double ic, double jc) {
		// Find the nearest data point (i,j)
		final int i = (int)Math.round(ic);
		final int j = (int)Math.round(jc);
		
		final double y0 = data[i][j];
		double ax=0.0, ay=0.0, bx=0.0, by=0.0;

		if(isValid(i+1,j)) {
			if(isValid(i-1, j)) {
				ax = 0.5 * (data[i+1][j] + data[i-1][j]) - y0;
				bx = 0.5 * (data[i+1][j] - data[i-1][j]);
			}
			else bx = data[i+1][j] - y0; // Fall back to linear...
		}
		else if(isValid(i-1, j)) bx = y0 - data[i-1][j];
	
		if(isValid(i,j+1)) {
			if(isValid(i,j-1)) {
				ay = 0.5 * (data[i][j+1] + data[i][j-1]) - y0;
				by = 0.5 * (data[i][j+1] - data[i][j-1]);
			}
			else by = data[i][j+1] - y0; // Fall back to linear...
		}
		else if(isValid(i,j-1)) by = y0 - data[i][j-1];
		
		ic -= i;
		jc -= j;
		
		return (ax*ic+bx)*ic + (ay*jc+by)*jc + y0;
	}
	
	private double[] ax, ay;
	private AtomicBoolean interpolating = new AtomicBoolean(false);
	// Performs a bicubic spline interpolation...
	public double splineAt(double ic, double jc) {
		
		final int i0 = (int)Math.floor(ic-1.0);
		final int j0 = (int)Math.floor(jc-1.0);
		
		final int fromi = Math.max(0, i0);
		final int toi = Math.min(sizeX(), i0+4);
		
		final int fromj = Math.max(0, j0);
		final int toj = Math.min(sizeY(), j0+4);
		
		
		// Check to make sure the coefficients aren't being used by another call...
		// Get an exclusive lock if possible...
		if(!interpolating.compareAndSet(false, true)) 
			throw new ConcurrentModificationException("concurrent interpolation conflict.");
		
		if(ax == null) ax = new double[4];
		if(ay == null) ay = new double[4];
		
		// Calculate the spline coefficients....
		for(int i=toi; --i >= fromi; ) {
			final double dx = Math.abs(i - ic);
			ax[i-i0] = dx > 1.0 ? 
					((-0.5 * dx + 2.5) * dx - 4.0) * dx + 2.0 : (1.5 * dx - 2.5) * dx * dx + 1.0;
		}
		
		for(int j=toj; --j >= fromj; ) {
			final double dy = Math.abs(j - jc);
			ay[j-j0] = dy > 1.0 ? 
					((-0.5 * dy + 2.5) * dy - 4.0) * dy + 2.0 : (1.5 * dy - 2.5) * dy * dy + 1.0;
		}
		
		// Do the spline convolution...
		double sum = 0.0, sumw = 0.0;
		for(int i=toi; --i >= fromi; ) for(int j=toj; --j >= fromj; ) if(flag[i][j] == 0) {
			final double w = ax[i-i0]*ay[j-j0];
			sum += w * data[i][j];
			sumw += w;
		}
		
		// Release the lock on the interpolation. Others may call it now...
		interpolating.set(false);
		
		return sum / sumw;
	}
	
	public void resample(Data2D from) {
		final Vector2D stretch = new Vector2D(sizeX() / from.sizeX(), sizeY() / from.sizeY());
	
		// Antialias filter
		if(stretch.x > 1.0 || stretch.y > 1.0) {
			from = (Data2D) from.copy();
			double a = Math.sqrt(stretch.x * stretch.x - 1.0);
			double b = Math.sqrt(stretch.y * stretch.y - 1.0);
			from.smooth(getGaussian(a, b));
		}
		
		// Interpolate to new array...
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) {		
			data[i][j] = from.valueAtIndex(i*stretch.x, j*stretch.y);
			if(Double.isNaN(data[i][j])) flag[i][j] = 1;
		}
	}
	
	public double[][] getGaussian(double pixelRadius) {
		return getGaussian(pixelRadius, pixelRadius);		
	}
	
	public double[][] getGaussian(double a, double b) {
		int nx = 1 + 2 * (int)(Math.ceil(3.0 * a));
		int ny = 1 + 2 * (int)(Math.ceil(3.0 * b));
		
		double[][] beam = new double[nx][ny];
		int ic = nx/2;
		int jc = ny/2;
		
		for(int di=ic; --di > 0; ) {
			final double devx = di / a;
			for(int dj=jc; --dj > 0;) {
				final double devy = dj / b;	
				beam[ic-di][jc-dj] = beam[ic-di][jc+dj] = beam[ic+di][jc-dj] = beam[ic+di][jc+dj] = 
					Math.exp(-0.5 * (devx*devx + devy*devy));
			}
		}
		beam[ic][jc] = 1.0;
		
		return beam;
	}
	
	public double weightAt(int i, int j) { return 1.0; }
	
	public boolean containsIndex(final int i, final int j) {
		if(i < 0) return false;
		if(j < 0) return false;
		if(i >= sizeX()) return false;
		if(j >= sizeY()) return false;
		return true;
	}
	
	public boolean containsIndex(final double i, final double j) {
		if(i < 0) return false;
		if(j < 0) return false;
		if(i >= sizeX()-0.5) return false;
		if(j >= sizeY()-0.5) return false;
		return true;
	}
	
	public boolean isValid(final int i, final int j) {
		if(!containsIndex(i, j)) return false;
		if(flag[i][j] != 0) return false;
		return true;
	}
	
	public boolean isValid(final double i, final double j) {
		if(!containsIndex(i, j)) return false;
		if(flag[(int)Math.round(i)][(int)Math.round(j)] != 0) return false;
		return true;
	}
	 
	
	public String getPixelInfo(final int i, final int j) {
		if(!isValid(i, j)) return "";
		String type = "";
		if(contentType != null) if(contentType.length() != 0) type = contentType + "> ";
		return type + Util.getDecimalFormat(1e3).format(data[i][j]) + " " + unit.name;
	}
	
	public void scale(double value) {
		if(value == 1.0) return;
		for(int i=sizeX(); --i >=0; ) for(int j=sizeY(); --j >= 0; ) data[i][j] *= value;
	}
	
	public void setUnit(Unit u) {
		unit = u;
	}
	
	public double getMin() { 
		double min=Double.POSITIVE_INFINITY;
		for(int i=sizeX(); --i >=0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) 
			if(data[i][j] < min) min = data[i][j];
		return min;
	}

	
	public double getMax() {
		double max=Double.NEGATIVE_INFINITY;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) 
				if(data[i][j] > max) max = data[i][j];
		return max;
	}
	
	public double getMedian() {
		float[] temp = new float[sizeX() * sizeY()];
		int n=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) temp[n++] = (float) data[i][j];
		return Statistics.median(temp, 0, n);
	}
	
	public double select(double fraction) {
		float[] temp = new float[sizeX() * sizeY()];
		int n=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) temp[n++] = (float) data[i][j];
		return Statistics.select(temp, fraction, 0, n);
	}
	
	public Range getRange() {
		final Range range = new Range();
		range.empty();
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) range.include(data[i][j]);
		return range;
	}
	
	public Index2D indexOfMax() {
		Index2D index = new Index2D();

		double peak = 0.0;

		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(data[i][j] > peak) {
				peak = data[i][j];
				index.i = i;
				index.j = j;
			}

		return index;
	}
	
	public Index2D indexOfMaxDev() {
		Index2D index = new Index2D();

		double dev = 0.0;

		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(Math.abs(data[i][j]) > dev) {
				dev = Math.abs(data[i][j]);
				index.i = i;
				index.j = j;
			}

		return index;
	}
	
	public double mean() {
		double sum = 0.0;
		int n=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			sum += data[i][j];
			n++;
		}
		return sum / n;
	}
	
	
	public double median() {
		double[] point = new double[countPoints()];

		if(point.length == 0) return 0.0;

		for(int i=sizeX(), k=0; --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) point[k++] = data[i][j];

		Arrays.sort(point);
		int n = point.length;

		return n % 2 == 0 ? (point[n/2-1] + point[n/2]) / 2.0 : point[(n - 1) / 2]; 
	}
	
	public double getRMS() {
		double sum = 0.0;
		int n=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			sum += data[i][j] * data[i][j];
			n++;
		}
		return n < 0 ? Double.NaN : Math.sqrt(sum / n);
	}
	
	public double getRobustRMS() {
		float[] chi2 = new float[countPoints()];
		if(chi2.length == 0) return 0.0;

		for(int i=sizeX(), k=0; --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			final float value = (float) data[i][j];
			chi2[k++] = value * value;
		}
		// median(x^2) = 0.454937 * sigma^2 
		return Math.sqrt(Statistics.median(chi2) / 0.454937);	
	}
	

	public void clipBelow(double level) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(data[i][j] < level) flag[i][j] = 1;
	}
	
	public void clipAbove(double level) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(data[i][j] > level) flag[i][j] = 1;
	}
	
	public int countPoints() {
		int points = 0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) points++;
		return points;
	}
	
	protected void crop(int imin, int jmin, int imax, int jmax) {
		if(verbose) System.err.println("Cropping to " + (imax - imin + 1) + "x" + (jmax - jmin + 1));

		double[][] olddata = data;
		int[][] oldflag = flag;
		
		final int fromi = Math.max(0, imin);
		final int fromj = Math.max(0, jmin);
		final int toi = Math.min(sizeX()-1, imax);
		final int toj = Math.min(sizeY()-1, jmax);		
		
		setSize(imax-imin+1, jmax-jmin+1);
		
		for(int i=fromi, i1=fromi-imin; i<=toi; i++, i1++) for(int j=fromj, j1=fromj-jmin; j<=toj; j++, j1++) {
			data[i1][j1] = olddata[i][j];
			flag[i1][j1] = oldflag[i][j];
		}
	}	
	

	public int[] getHorizontalIndexRange() {
		int min = sizeX(), max = -1;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			if(i < min) min = i;
			if(i > max) max = i;
			break;
		}
		return max > min ? new int[] { min, max } : null;
	}
	
	public int[] getVerticalIndexRange() {
		int min = sizeY(), max = -1;
		for(int j=sizeY(); --j >= 0; ) for(int i=sizeX(); --i >= 0; ) if(flag[i][j] == 0) {
			if(j < min) min = j;
			if(j > max) max = j;
			break;
		}
		return max > min ? new int[] { min, max } : null;
	}
	
	public void autoCrop() {
		if(verbose) System.err.print("Auto-cropping. ");
		int[] hRange = getHorizontalIndexRange();
		int[] vRange = getVerticalIndexRange();
		if(verbose) System.err.println((hRange[1] - hRange[0] + 1) + "x" + (vRange[1] - vRange[0] + 1));
		this.crop(hRange[0], vRange[0], hRange[1], vRange[1]);
	}
	
	public boolean[][] getBooleanFlag() {
		boolean[][] mask = new boolean[sizeX()][sizeY()];
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) mask[i][j] = flag[i][j] > 0;
		return mask;
	}
	
	public void flag(boolean[][] mask, int pattern) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(mask[i][j]) flag[i][j] |= pattern;	 
	}
	
	public void unflag(boolean[][] mask, int pattern) {
		pattern = ~pattern;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(mask[i][j]) flag[i][j] &= pattern;	 
	}
	

	
	public double level(boolean robust) {
		double level = robust ? median() : mean();
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) data[i][j] -= level;
		return level;
	}
	
	
	public void smooth(double[][] beam) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getSmoothed(beam, beamw);
	}
	
	public void fastSmooth(double[][] beam, int stepX, int stepY) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getFastSmoothed(beam, beamw, stepX, stepY);
	}
	
	public double[][] getSmoothed(double[][] beam, double[][] beamw) {
		double[][] convolved = new double[sizeX()][sizeY()];
		final int ic = (beam.length-1) / 2;
		final int jc = (beam[0].length-1) / 2;
		
		final WeightedPoint result = new WeightedPoint();
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			getSmoothedValueAt(i, j, beam, ic, jc, result);
			convolved[i][j] = result.value;
			if(beamw != null) beamw[i][j] = result.weight;
		}
		
		return convolved;
	}
	
	
	// Do the convolution proper at the specified intervals (step) only, and interpolate (quadratic) inbetween
	public double[][] getFastSmoothed(double[][] beam, double[][] beamw, int stepX, int stepY) {
		if(stepX < 2 && stepY < 2) return getSmoothed(beam, beamw);
		
		final int ic = (beam.length-1) / 2;
		final int jc = (beam[0].length-1) / 2;
		
		final WeightedPoint result = new WeightedPoint();
		
		final int nx = sizeX()/stepX + 1;
		final int ny = sizeY()/stepY + 1;
 
		final AstroImage signalImage = new AstroImage(nx, ny);
		signalImage.interpolationType = interpolationType;
		
		AstroImage weightImage = null;
		
		if(beamw != null) {
			weightImage = (AstroImage) signalImage.clone();
			weightImage.data = new double[nx][ny];
			weightImage.interpolationType = interpolationType;
		}
			
		for(int i=0, i1=0; i<sizeX(); i+=stepX, i1++) for(int j=0, j1=0; j<sizeY(); j+=stepY, j1++) {
			getSmoothedValueAt(i, j, beam, ic, jc, result);
			signalImage.data[i1][j1] = result.value;
			if(beamw != null) weightImage.data[i1][j1] = result.weight;
			signalImage.flag[i1][j1] = result.weight > 0.0 ? 0 : 1;
		}
		
		final double[][] convolved = new double[sizeX()][sizeY()];
		final double istepX = 1.0 / stepX;
		final double istepY = 1.0 / stepY;
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			final double i1 = i * istepX;
			final double j1 = j * istepY;
			final double value = signalImage.valueAtIndex(i1, j1);
			if(!Double.isNaN(value)) {		
				convolved[i][j] = value;
				if(beamw != null) beamw[i][j] = weightImage.valueAtIndex(i1, j1);
			}
			else flag[i][j] = 1;
		}
		
		return convolved;
	}
	
	
	// Beam fitting: I' = C * sum(wBI) / sum(wB2)
	// I(x) = I -> I' = I -> C = sum(wB2) / sum(wB)
	// I' = sum(wBI)/sum(wB)
	// rms = Math.sqrt(1 / sum(wB))
	public void getSmoothedValueAt(final int i, final int j, final double[][] beam, int ic, int jc, WeightedPoint result) {
		final int i0 = i - ic;
		final int fromi = Math.max(0, i0);
		final int toi = Math.min(sizeX(), i0 + beam.length);
		
		final int j0 = j - jc;
		final int fromj = Math.max(0, j0);
		final int toj = Math.min(sizeY(), j0 + beam[0].length);

		double sum = 0.0, sumw = 0.0;
		for(int i1=toi; --i1 >= fromi; ) for(int j1=toj; --j1 >= fromj; ) if(flag[i1][j1] == 0) {
			final double wB = weightAt(i1, j1) * beam[i1-i0][j1-j0];
			sum += wB * data[i1][j1];
			sumw += Math.abs(wB);		    
		}

		result.value = sum / sumw;
		result.weight = sumw;
	}
	
	public void scale(int i, int j, double factor) {
		data[i][j] *= factor;
	}
	
	public void sanitize() {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] != 0) sanitize(i, j);
	}
	
	protected void sanitize(final int i, final int j) { 
		flag[i][j] |= 1;
		data[i][j] = 0.0;		
	}
	

	public Vector2D[] getHistogram(double[][] image, double binSize) {
		Range range = getRange();
		
		int bins = 1 + (int)Math.round(range.max / binSize) - (int)Math.round(range.min / binSize);
		Vector2D[] bin = new Vector2D[bins];
		for(int i=0; i<bins; i++) bin[i] = new Vector2D(i*binSize, 0.0);
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			bin[(int)Math.round(image[i][j] / binSize)].y++;
		}
		
		return bin;
	}
	
	
	public static Object copyOf(final Object image) {
		if(image == null) return null;

		if(image instanceof double[][]) {
			final double[][] orig = (double[][]) image;
			final double[][] copy = new double[orig.length][orig[0].length];
			for(int i=orig.length; --i >= 0; ) System.arraycopy(orig[i], 0, copy[i], 0, orig[0].length);	
			return copy;
		}	
		else if(image instanceof int[][]) {
			final int[][] orig = (int[][]) image;
			final int[][] copy = new int[orig.length][orig[0].length];
			for(int i=orig.length; --i >= 0; ) System.arraycopy(orig[i], 0, copy[i], 0, orig[0].length);	
			return copy;
		}

		return null;
	}
	
	
}
