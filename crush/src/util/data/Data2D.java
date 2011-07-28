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
import java.util.Locale;

import util.Range;
import util.Unit;
import util.Util;
import util.Vector2D;
import crush.sourcemodel.AstroImage;

public class Data2D {
	public double[][] data;
	public int[][] flag;
	public Unit unit = Unit.unity;

	public String contentType = "";
	
	public boolean verbose = false;
	
	// 2 pi sigma^2 = a^2
	// a = sqrt(2 pi) sigma
	//   = sqrt(2 pi) fwhm / 2.35
	public static double fwhm2size = Math.sqrt(2.0 * Math.PI) / Util.sigmasInFWHM;

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
		try {return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public Data2D copy() {
		Data2D copy = (Data2D) clone();
		copy.copy(this);
		return copy;
	}
	
	public final void copy(Data2D image) {
		copyImageOf(image);	
		copyObjectFields(image);
	}
		
	public void copyImageOf(Data2D image) {
		// Make a copy of the fundamental data
		data = (double[][]) copyOf(image.data);
		flag = (int[][]) copyOf(image.flag);		
	}
	
	public void copyObjectFields(Data2D image) {}
	
	public final int sizeX() { return data.length; }
	
	public final int sizeY() { return data[0].length; }
	
	public void setSize(int x, int y) {
		data = new double[x][y];
		flag = new int[x][y];
		for(int i=0; i<x; i++) Arrays.fill(flag[i], 1); 
	}
	
	public void noFlag() {
		flag = new int[sizeX()][sizeY()];		
	}
	
	

	public void copyImageOf(double[][] image) { 
		for(int i=sizeX(); --i>=0; ) System.arraycopy(image[i], 0, data[i], 0, sizeY());
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
	
	public double valueAtIndex(int i, int j) { return data[i][j]; }
	
	public double valueAtIndex(Vector2D index) {
		return valueAtIndex(index.x, index.y);
	}
	
	// Interpolate (linear at edges, quadratic otherwise)	
	public double valueAtIndex(double ic, double jc) {		
		final int i = (int) Math.round(ic);
		final int j = (int) Math.round(jc);
		
		if(!isValid(i, j)) return Double.NaN;
		if(flag[i][j] != 0) return Double.NaN;
		
		final int sizeX = sizeX();
		final int sizeY = sizeY();
		
		final double y0 = data[i][j];
		double a=0.0, b=0.0, c=0.0, d=0.0;
		
		if(i > 0 && i < sizeX-1) if((flag[i+1][j] | flag[i-1][j]) == 0) {
			a = 0.5 * (data[i+1][j] + data[i-1][j]) - y0;
			c = 0.5 * (data[i+1][j] - data[i-1][j]);
		}
		if(j > 0 && j < sizeY-1) if((flag[i][j+1] | flag[i][j-1]) == 0) {
			b = 0.5 * (data[i][j+1] + data[i][j-1]) - y0;
			d = 0.5 * (data[i][j+1] - data[i][j-1]);
		}
		
		ic -= i;
		jc -= j;
		
		return (a*ic+c)*ic + (b*jc+d)*jc + y0;
	}
	
	public double getWeight(int i, int j) { return 1.0; }
	
	public boolean contains(final int i, final int j) {
		if(i < 0) return false;
		if(j < 0) return false;
		if(i >= sizeX()) return false;
		if(j >= sizeY()) return false;
		return true;
	}
	
	public boolean contains(final double i, final double j) {
		if(i < 0) return false;
		if(j < 0) return false;
		if(i >= sizeX()-0.5) return false;
		if(j >= sizeY()-0.5) return false;
		return true;
	}
	
	public boolean isValid(final int i, final int j) {
		if(!contains(i, j)) return false;
		if(flag[i][j] != 0) return false;
		return true;
	}
	
	public boolean isValid(final double i, final double j) {
		if(!contains(i, j)) return false;
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
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) 
			range.include(data[i][j]);
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
	



	public void fluxClip(double level) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(data[i][j] < level) flag[i][j] = 1;
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
	
	
	public void convolve(double[][] beam) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getConvolved(data, beam, beamw);
	}
	
	public void fastConvolve(double[][] beam, int stepX, int stepY) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getFastConvolved(data, beam, beamw, stepX, stepY);
	}
	
	public double[][] getConvolved(double[][] image, double[][] beam, double[][] beamw) {
		double[][] convolved = new double[sizeX()][sizeY()];
		final int ic = (beam.length-1) / 2;
		final int jc = (beam[0].length-1) / 2;
		
		final WeightedPoint result = new WeightedPoint();
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			getConvolved(image, i, j, beam, ic, jc, result);
			convolved[i][j] = result.value;
			if(beamw != null) beamw[i][j] = result.weight;
		}
		
		return convolved;
	}
	
	
	// Do the convolution proper at the specified intervals (step) only, and interpolate (quadratic) inbetween
	public double[][] getFastConvolved(double[][] image, double[][] beam, double[][] beamw, int stepX, int stepY) {
		if(stepX < 2 && stepY < 2) return getConvolved(image, beam, beamw);
		
		final int ic = (beam.length-1) / 2;
		final int jc = (beam[0].length-1) / 2;
		
		final WeightedPoint result = new WeightedPoint();
		
		final int nx = sizeX()/stepX + 1;
		final int ny = sizeY()/stepY + 1;
 
		final AstroImage signalImage = new AstroImage(nx, ny);
		AstroImage weightImage = null;
		
		if(beamw != null) {
			weightImage = (AstroImage) signalImage.clone();
			weightImage.data = new double[nx][ny];
		}
			
		for(int i=0, i1=0; i<sizeX(); i+=stepX, i1++) for(int j=0, j1=0; j<sizeY(); j+=stepY, j1++) {
			getConvolved(image, i, j, beam, ic, jc, result);
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
	public void getConvolved(final double[][] image, final int i, final int j, final double[][] beam, int ic, int jc, WeightedPoint result) {
		result.noData();
		
		ic += i;
		jc += j;
		
		// ib = i + ic - i1
		final int fromib = Math.min(beam.length-1, ic);
		final int fromjb = Math.min(beam[0].length-1, jc);
		final int toib = Math.max(0, ic - sizeX());
		final int tojb = Math.max(0, jc - sizeY());
			
		for(int ib=fromib, i1=ic-fromib; ib>toib; ib--,i1++) for(int jb=fromjb, j1=jc-fromjb; jb>tojb; jb--,j1++) if(flag[i1][j1] == 0) {
			final double B = beam[ib][jb];
			if(B != 0.0) {
				result.value += getWeight(i1, j1) * B * image[i1][j1];
				result.weight += getWeight(i1, j1) * Math.abs(B);		    
			}
		}

		result.value /= result.weight;
	}
	
	public void scale(int i, int j, double factor) {
		data[i][j] *= factor;
	}
	
	public void sanitize() {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] != 0) sanitize(i, j);
	}
	
	protected void sanitize(int i, int j) { 
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
	
	public boolean containsIndex(double i, double j) {
		return i >= 0 && i < sizeX() && i >= 0 && j < sizeY();
	}
	    
	public static Object copyOf(final Object image) {
		if(image == null) return null;

		if(image instanceof double[][]) {
			final double[][] orig = (double[][]) image;
			final double[][] copy = new double[orig.length][orig[0].length];
			for(int i=0; i<orig.length; i++) System.arraycopy(orig[i], 0, copy[i], 0, orig[0].length);	
			return copy;
		}	
		else if(image instanceof int[][]) {
			final int[][] orig = (int[][]) image;
			final int[][] copy = new int[orig.length][orig[0].length];
			for(int i=0; i<orig.length; i++) System.arraycopy(orig[i], 0, copy[i], 0, orig[0].length);	
			return copy;
		}

		return null;
	}
	
	
}
