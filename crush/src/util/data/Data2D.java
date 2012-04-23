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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.StringTokenizer;

import crush.CRUSH;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsDate;
import nom.tam.fits.FitsException;
import nom.tam.fits.FitsFactory;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.fits.ImageHDU;
import nom.tam.util.BufferedDataOutputStream;
import nom.tam.util.Cursor;

import util.CompoundUnit;
import util.Range;
import util.Unit;
import util.Util;
import util.Vector2D;
import util.Parallel;

public class Data2D extends Parallel implements Cloneable {
	private double[][] data;
	private int[][] flag;
	private int parallelism = Runtime.getRuntime().availableProcessors();
	
	private Unit unit = Unit.unity;
	private String contentType = UNDEFINED;
	private int interpolationType = BICUBIC_SPLINE;
	private boolean verbose = false;
	
	public Header header;
	
	private String name = UNDEFINED;
	public String fileName;
	public String creator = "CRUSH " + CRUSH.getFullVersion();
		
	public Data2D() {
		Locale.setDefault(Locale.US);
	}
	
	public Data2D(int sizeX, int sizeY) {
		this();
		setSize(sizeX, sizeY);
		fillFlag(1);
	}
	
	public Data2D(double[][] data) {
		this();
		this.data = data;
		this.flag = new int[sizeX()][sizeY()];
	}
	
	public Data2D(double[][] data, int[][] flag) {
		this();
		this.data = data;
		this.flag = flag;
	}
	
	public String getName() { return name; }
	
	public void setName(String name) { this.name = name; }
	
	public Unit getUnit() { return unit; }
	
	public void setUnit(Unit u) { this.unit = u; }
	
	public CompoundUnit getCompoundUnit(String name) {
		int index = name.contains("/") ? name.lastIndexOf('/') : name.length();
		StringTokenizer numerator = new StringTokenizer(name.substring(0, index));
		StringTokenizer denominator = new StringTokenizer(name.substring(index+1, name.length()));

		CompoundUnit unit = new CompoundUnit();
		while(numerator.hasMoreTokens()) unit.factors.add(getBasicUnit(numerator.nextToken()));
		while(denominator.hasMoreTokens()) unit.divisors.add(getBasicUnit(denominator.nextToken()));
		
		return unit;
	}
	
	protected Unit getBasicUnit(String value) {
		return Unit.get(value);
	}
	
	public String getContentType() { return contentType; }
	
	public void setContentType(String value) { contentType = value; }
	
	public int getInterpolationType() { return interpolationType; }
	
	public void setInterpolationType(int value) { this.interpolationType = value; }
	
	public boolean isVerbose() { return verbose; }
	
	public void setVerbose(boolean value) { verbose = value; }
	
	public final double[][] getData() { return data; }
	
	public final int[][] getFlag() { return flag; }
	
	public void setData(double[][] image) { data = image; }
	
	public final void setFlag(int[][] image) { flag = image; }
	
	public final double getValue(int i, int j) { return data[i][j]; }
	
	public final void setValue(int i, int j, double weight) { data[i][j] = weight; }
	
	public final void increment(int i, int j, double d) { data[i][j] += d; }
	
	public final void decrement(int i, int j, double d) { data[i][j] -= d; }
	
	public final void scaleValue(int i, int j, double factor) { data[i][j] *= factor; }
	
	public final boolean isNaN(int i, int j) { return Double.isNaN(data[i][j]); }
	
	public final boolean isInfinite(int i, int j) { return Double.isInfinite(data[i][j]); }
	
	public final boolean isIndefinite(int i, int j) { return Double.isNaN(data[i][j]) || Double.isInfinite(data[i][j]); }
	
	public final int getFlag(int i, int j) { return flag[i][j]; }
	
	public final void setFlag(int i, int j, int value) { flag[i][j] = value; }
	
	public final void flag(int i, int j) { flag[i][j] |= 1; }
	
	public final void unflag(int i, int j) { flag[i][j] = 0; }
	
	public final void flag(int i, int j, int pattern) { flag[i][j] |= pattern; }
	
	public final void unflag(int i, int j, int pattern) { flag[i][j] &= ~pattern; }
	
	public final boolean isFlagged(int i, int j) { return flag[i][j] != 0; }
	
	public final boolean isUnflagged(int i, int j) { return flag[i][j] == 0; }
	
	public final boolean isFlagged(int i, int j, int pattern) { return (flag[i][j] & pattern) != 0; }
	
	public final boolean isUnflagged(int i, int j, int pattern) { return (flag[i][j] & pattern) == 0; }
	
	public void setParallel(int n) { parallelism = Math.max(1, n); }
	
	public void noParallel() { parallelism = 1; }
	
	public int getParallel() { return parallelism; }
	
	
	// TODO 
	// It may be practical to have these methods so more sophisticated algorithms can work on these
	// images. However, the case for it may not be so strong after all, and they create confusion...
	public double getWeight(int i, int j) { return 1.0; }
	
	public double getRMS(int i, int j) { return 1.0; }
	
	public double getS2N(int i, int j) { return valueAtIndex(i, j); }
	
	public final double getWeight(final Index2D index) { return getWeight(index.i(), index.j()); }
	
	public final double getRMS(final Index2D index) { return getRMS(index.i(), index.j()); }
	
	public final double getS2N(final Index2D index) { return getS2N(index.i(), index.j()); }
	
	
	
	
	@Override
	public Object clone() {
		try { 
			Data2D clone = (Data2D) super.clone(); 
			clone.reuseIpolData = new InterpolatorData();
			return clone;
		}
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public Data2D copy() {
		Data2D copy = (Data2D) clone();
		copy.setSize(sizeX(), sizeY());
		copy.copyImageOf(this);
		return copy;
	}

	public boolean conformsTo(Data2D image) {
		if(data == null) return false;
		else if(sizeX() != image.sizeX()) return false;
		else if(sizeY() != image.sizeY()) return false;
		return true;
	}
	
	public void copyImageOf(final Data2D image) {
		if(!conformsTo(image)) setSize(image.sizeX(), image.sizeY());
		
		new Task<Void>() {
			@Override
			public void processX(int i) { copy(image, i); }
			@Override
			public void process(int i, int j) {}
		}.process();	
	}
	
	protected void copy(Data2D image, int i) {
		System.arraycopy(image.data[i], 0, data[i], 0, sizeY()); 
		System.arraycopy(image.flag[i], 0, flag[i], 0, sizeY()); 
	}
	
	public void copyTo(final double[][] dst) {
		new Task<Void>() {
			final int sizeY = sizeY();
			@Override
			public void processX(int i) { System.arraycopy(data[i], 0, dst[i], 0, sizeY); }
			@Override
			public void process(int i, int j) {}
		}.process();	
	}
	
	
	
	
	
	public final int sizeX() { return data.length; }
	
	public final int sizeY() { return data[0].length; }
	
	public void setSize(int x, int y) {
		data = new double[x][y];
		flag = new int[x][y];
		
		new Task<Void>() {
			@Override
			public void processX(int i) { Arrays.fill(flag[i], 1); }
			@Override
			public void process(int i, int j) {}
		}.process();
		
	}
	
	public void noFlag() { fillFlag(0); }
	
	public void fillFlag(final int value) {
		if(flag == null) flag = new int[sizeX()][sizeY()];
		else {
			new Task<Void>() {
				@Override
				public void processX(int i) { Arrays.fill(flag[i], value); }
				@Override
				public void process(int i, int j) {}
			}.process();	
		}
	}

	public void fill(final double value) {
		if(flag == null) flag = new int[sizeX()][sizeY()];
		else {
			new Task<Void>() {
				@Override
				public void processX(int i) { Arrays.fill(data[i], value); }
				@Override
				public void process(int i, int j) {}
			}.process();	
		}
	}

	
	public void setImage(Data2D image) {
		data = image.data;
		flag = image.flag;
	}
	
	public void addImage(final double[][] image, final double scale) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				data[i][j] += scale * image[i][j];
			}
		}.process();
	}

	public void addImage(double[][] image) { addImage(image, 1.0); }

	public void addValue(final double x) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				data[i][j] += x;
			}
		}.process();
	}
	
	public void clear() {
		new Task<Void>() {
			@Override
			public void process(int i, int j) { clear(i, j); }
		}.process();	
	}

	public void clear(int i, int j) {
		data[i][j] = 0.0;
		flag[i][j] = 1;
	}

	public final double valueAt(final Index2D index) {
		return valueAtIndex(index.i(), index.j());
	}
	
	public double valueAtIndex(int i, int j) { return flag[i][j] == 0 ? data[i][j] : Double.NaN; }
	
	public double valueAtIndex(Vector2D index) {
		return valueAtIndex(index.getX(), index.getY(), null);
	}
	
	public double valueAtIndex(Vector2D index, InterpolatorData ipolData) {
		return valueAtIndex(index.getX(), index.getY(), ipolData);
	}
		
	public double valueAtIndex(double ic, double jc) {
		return valueAtIndex(ic, jc, null);
	}
	
	public double valueAtIndex(double ic, double jc, InterpolatorData ipolData) {
		
		// The nearest data point (i,j)
		final int i = (int) Math.round(ic);
		final int j = (int) Math.round(jc);
		
		if(!isValid(i, j)) return Double.NaN;
		
		switch(interpolationType) {
		case NEAREST_NEIGHBOR : return data[i][j];
		case BILINEAR : return bilinearAt(ic, jc);
		case PIECEWISE_QUADRATIC : return piecewiseQuadraticAt(ic, jc);
		case BICUBIC_SPLINE : return ipolData == null ? splineAt(ic, jc) : splineAt(ic, jc, ipolData);
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
	
	
	private InterpolatorData reuseIpolData = new InterpolatorData();
	
	public synchronized double splineAt(final double ic, final double jc) {	
		return splineAt(ic, jc, reuseIpolData);
	}
		
	// Performs a bicubic spline interpolation...
	public double splineAt(final double ic, final double jc, InterpolatorData ipolData) {	
		
		ipolData.centerOn(ic, jc);
		
		final SplineCoeffs splineX = ipolData.splineX;
		final SplineCoeffs splineY = ipolData.splineY;
			
		final int fromi = Math.max(0, splineX.minIndex());
		final int toi = Math.min(sizeX(), splineX.maxIndex());
		
		final int fromj = Math.max(0, splineY.minIndex());
		final int toj = Math.min(sizeY(), splineY.maxIndex());
		
		// Do the spline convolution...
		double sum = 0.0, sumw = 0.0;
		for(int i=toi; --i >= fromi; ) {
			final double ax = splineX.valueAt(i);
			for(int j=toj; --j >= fromj; ) if(flag[i][j] == 0) {
				final double w = ax * splineY.valueAt(j);
				sum += w * data[i][j];
				sumw += w;
			}
		}
		
		return sum / sumw;
	}
	
	public void resample(Data2D from) {
		final Vector2D stretch = new Vector2D(sizeX() / from.sizeX(), sizeY() / from.sizeY());
	
		// Antialias filter
		if(stretch.getX() > 1.0 || stretch.getY() > 1.0) {
			from = (Data2D) from.copy();
			double a = Math.sqrt(stretch.getX() * stretch.getX() - 1.0);
			double b = Math.sqrt(stretch.getY() * stretch.getY() - 1.0);
			from.smooth(getGaussian(a, b));
		}
		
		final Data2D antialiased = from;

		// Interpolate to new array...
		new InterpolatingTask() {
			@Override
			public void process(int i, int j) {
				data[i][j] = antialiased.valueAtIndex(i*stretch.getX(), j*stretch.getY(), getInterpolatorData());
				flag[i][j] = Double.isNaN(data[i][j]) ?  1 : 0;
			}
		}.process();
		
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
		return type + Util.getDecimalFormat(1e3).format(data[i][j]) + " " + unit.name();
	}
	
	public final void scale(final double value) {
		if(value == 1.0) return;
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				data[i][j] *= value;
			}
		}.process();
	}
	
	public void scale(int i, int j, double factor) {
		data[i][j] *= factor;
	}

	public double getMin() { 
		Task<Double> search = new Task<Double>() {
			private double min = Double.POSITIVE_INFINITY;
			@Override
			public void process(int i, int j) {
				if(flag[i][j]==0) if(data[i][j] < min) min = data[i][j];
			}
			@Override 
			public Double getPartialResult() { return min; }
			@Override
			public Double getResult() {
				double globalMin = Double.POSITIVE_INFINITY;
				for(Process<Double> task : getWorkers()) if(task.getPartialResult() < globalMin) globalMin = task.getPartialResult();
				return globalMin;
			}
		};
		search.process();
		return search.getResult();
	}

	public double getMax() {
		Task<Double> search = new Task<Double>() {
			private double max = Double.NEGATIVE_INFINITY;
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) if(data[i][j] > max) max = data[i][j];
			}
			@Override 
			public Double getPartialResult() { return max; }
			@Override
			public Double getResult() {
				double globalMax = Double.NEGATIVE_INFINITY;
				for(Process<Double> task : getWorkers()) if(task.getPartialResult() > globalMax) globalMax = task.getPartialResult();
				return globalMax;
			}
		};
		search.process();
		return search.getResult();
	}

	public Range getRange() {
		Task<Range> search = new Task<Range>() {
			private Range range;
			@Override
			public void init() {
				super.init();
				range = new Range();
			}
			@Override
			public void process(int i, int j) {
				if(flag[i][j]==0) range.include(data[i][j]);
			}
			@Override 
			public Range getPartialResult() { return range; }
			@Override
			public Range getResult() {
				Range globalRange = new Range();
				for(Process<Range> task : getWorkers()) globalRange.include(task.getPartialResult());
				return globalRange;
			}
		};
		search.process();
		return search.getResult();
	}
	
	
	public Index2D indexOfMax() {	
		Task<Index2D> search = new Task<Index2D>() {
			private Index2D index;
			private double peak = Double.NEGATIVE_INFINITY;
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) if(data[i][j] > peak) {
					peak = data[i][j];
					if(index == null) index = new Index2D();
					index.set(i, j);
				}
			}
			@Override 
			public Index2D getPartialResult() { return index; }
			@Override
			public Index2D getResult() {
				double globalPeak = Double.NEGATIVE_INFINITY;
				Index2D globalIndex = null;
				for(Process<Index2D> task : getWorkers()) {
					Index2D partial = task.getPartialResult();
					if(partial != null) if(data[partial.i()][partial.j()] > globalPeak) {
						globalIndex = partial;
						globalPeak = data[partial.i()][partial.j()];
					}
				}
				return globalIndex;
			}
		};
		
		search.process();
		return search.getResult();
	}
	
	public Index2D indexOfMaxDev() {
		Task<Index2D> search = new Task<Index2D>() {
			private Index2D index;
			private double dev = 0.0;
			@Override
			public void process(int i, int j) {
				final double value = Math.abs(data[i][j]);
				if(flag[i][j] == 0) if(value > dev) {
					dev = value;
					if(index == null) index = new Index2D();
					index.set(i, j);
				}
			}
			@Override 
			public Index2D getPartialResult() { return index; }
			@Override
			public Index2D getResult() {
				double globalDev = 0.0;
				Index2D globalIndex = null;
				for(Process<Index2D> task : getWorkers()) {
					Index2D partial = task.getPartialResult();
					if(partial == null) continue;
					final double value = Math.abs(data[partial.i()][partial.j()]);
					if(value > globalDev) {
						globalIndex = partial;
						globalDev = value;
					}
				}
				return globalIndex;
			}
		};
		
		search.process();
		return search.getResult();
	}

	
	public double mean() {
		Task<WeightedPoint> average = new AveragingTask() {
			private double sum = 0.0, sumw = 0.0;
			@Override
			public void process(int i, int j) { 
				if(flag[i][j] == 0) {
					sum  += data[i][j];
					sumw += getWeight(i, j);
				}
			}
			@Override
			public WeightedPoint getPartialResult() { return new WeightedPoint(sum, sumw); }
		};
		
		average.process();
		return average.getResult().value();	
	}
	
	public double median() {
		float[] temp = new float[countPoints()];
		if(temp.length == 0) return 0.0;
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

	
	public double getRMSScatter() {
		Task<WeightedPoint> rms = new AveragingTask() {
			private double sum = 0.0;
			private int n = 0;
			@Override
			public void process(int i, int j) { 
				if(flag[i][j] == 0) {
					sum += data[i][j] * data[i][j];
					n++;
				}
			}
			@Override
			public WeightedPoint getPartialResult() { return new WeightedPoint(sum, n); }
		};
		
		rms.process();
		return rms.getResult().value();	
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
	

	public void clipBelow(final double level) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) if(data[i][j] < level) flag[i][j] = 1;
			}
		}.process();
	}
	
	public void clipAbove(final double level) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) if(data[i][j] > level) flag[i][j] = 1;
			}
		}.process();
	}
	
	public int countPoints() {
		Task<Integer> counter = new Task<Integer>() {
			private int counter = 0;
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) counter++;
			}
			@Override
			public Integer getPartialResult() {
				return counter;
			}
			@Override
			public Integer getResult() {
				int globalCount = 0;
				for(Process<Integer> task : getWorkers()) globalCount += task.getPartialResult();
				return globalCount;
			}
		};
		
		counter.process();
		return counter.getResult();
	}
	
	public boolean isEmpty() { return countPoints() == 0; }
	
	protected void crop(int imin, int jmin, int imax, int jmax) {
		if(verbose) System.err.println("Cropping to " + (imax - imin + 1) + "x" + (jmax - jmin + 1));

		double[][] olddata = data;
		int[][] oldflag = flag;
		
		final int fromi = Math.max(0, imin);
		final int fromj = Math.max(0, jmin);
		final int toi = Math.min(sizeX()-1, imax);
		final int toj = Math.min(sizeY()-1, jmax);		
		
		setSize(imax-imin+1, jmax-jmin+1);
		fillFlag(1);
		
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
		final boolean[][] mask = new boolean[sizeX()][sizeY()];
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				mask[i][j] = flag[i][j] > 0;
			}
		}.process();
		return mask;
	}
	
	public void flag(final boolean[][] mask, final int pattern) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(mask[i][j]) flag[i][j] |= pattern;	
			}
		}.process(); 
	}
	
	public void unflag(final boolean[][] mask, int pattern) {
		final int ipattern = ~pattern;
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(mask[i][j]) flag[i][j] &= ipattern;	
			}
		}.process();
	}
	
	public double level(boolean robust) {
		double level = robust ? median() : mean();
		addValue(-level);
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
	
	public double[][] getSmoothed(final double[][] beam, final double[][] beamw) {
		final double[][] convolved = new double[sizeX()][sizeY()];
		final int ic = (beam.length-1) / 2;
		final int jc = (beam[0].length-1) / 2;
	
		new Task<Void>() {
			private WeightedPoint result;
			@Override
			public void init() {
				super.init();
				result = new WeightedPoint();
			}
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) {
					getSmoothedValueAt(i, j, beam, ic, jc, result);
					convolved[i][j] = result.value();
					if(beamw != null) beamw[i][j] = result.weight();
				}
			}
		}.process();
		
		return convolved;
	}
	
	
	// Do the convolution proper at the specified intervals (step) only, and interpolate (quadratic) inbetween
	public double[][] getFastSmoothed(double[][] beam, final double[][] beamw, int stepX, int stepY) {
		if(stepX < 2 && stepY < 2) return getSmoothed(beam, beamw);
		
		final int ic = (beam.length-1) / 2;
		final int jc = (beam[0].length-1) / 2;
		
		final WeightedPoint result = new WeightedPoint();
		
		final int nx = sizeX()/stepX + 1;
		final int ny = sizeY()/stepY + 1;
 
		final Data2D signalImage = new Data2D(nx, ny);
		signalImage.interpolationType = interpolationType;
		
		Data2D weightImage = null;
		
		if(beamw != null) {
			weightImage = (Data2D) signalImage.clone();
			weightImage.data = new double[nx][ny];
		}
			
		for(int i=0, i1=0; i<sizeX(); i+=stepX, i1++) for(int j=0, j1=0; j<sizeY(); j+=stepY, j1++) {
			getSmoothedValueAt(i, j, beam, ic, jc, result);
			signalImage.data[i1][j1] = result.value();
			if(beamw != null) weightImage.data[i1][j1] = result.weight();
			signalImage.flag[i1][j1] = result.weight() > 0.0 ? 0 : 1;
		}
		
		final double[][] convolved = new double[sizeX()][sizeY()];
		final double istepX = 1.0 / stepX;
		final double istepY = 1.0 / stepY;
	
		final Data2D wI = weightImage;
		
		new InterpolatingTask() {
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) {
					final double i1 = i * istepX;
					final double j1 = j * istepY;
					final double value = signalImage.valueAtIndex(i1, j1, getInterpolatorData());
					if(!Double.isNaN(value)) {		
						convolved[i][j] = value;
						if(beamw != null) beamw[i][j] = wI.valueAtIndex(i1, j1, getInterpolatorData());
					}
					else flag[i][j] = 1;
				}
			}
		}.process();
		
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
			final double wB = getWeight(i1, j1) * beam[i1-i0][j1-j0];
			sum += wB * data[i1][j1];
			sumw += Math.abs(wB);		    
		}

		result.setValue(sum / sumw);
		result.setWeight(sumw);
	}
	
	
	public int[][] getSkip(final double blankingValue) {
		final int[][] skip = (int[][]) copyOf(getFlag());
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(data[i][j] > blankingValue) skip[i][j] = 1;
			}
		}.process();
		
		return skip;
	}
	

	
	public void sanitize() {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(flag[i][j] != 0) sanitize(i, j);
			}
		}.process();
	}
	
	protected void sanitize(final int i, final int j) { 
		flag[i][j] |= 1;
		data[i][j] = 0.0;		
	}
	

	public Vector2D[] getHistogram(final double[][] image, final double binSize) {
		Range range = getRange();
		
		int bins = 1 + (int)Math.round(range.max() / binSize) - (int)Math.round(range.min() / binSize);
		final Vector2D[] bin = new Vector2D[bins];
		for(int i=0; i<bins; i++) bin[i] = new Vector2D(i*binSize, 0.0);
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) bin[(int)Math.round(image[i][j] / binSize)].addY(1.0);
			}
		}.process();
		
		return bin;
	}
	
	
	
	public final void read(String fileName) throws Exception {	
		read(fileName, 0);
	}
		
	public final void read(String fileName, int hduIndex) throws Exception {	
		read(findFits(fileName));
	}
	
	public void read(Fits fits) throws Exception {
		read(fits, 0);
	}
	
	public final void read(Fits fits, int hduIndex) throws Exception {
		read(fits.getHDU(hduIndex));
	}
	
	public void read(BasicHDU hdu) throws Exception {
		parseHeader(hdu.getHeader());
	}
	
	public void readData(BasicHDU hdu) throws FitsException {
		int sizeX = header.getIntValue("NAXIS1");
		int sizeY = header.getIntValue("NAXIS2");
		setSize(sizeX, sizeY);
		setImage(hdu);		
	}
	
	public void parseHeader(Header header) throws Exception {
		this.header = header;
		
		creator = header.getStringValue("CREATOR");
		if(creator == null) creator = UNDEFINED;
	
		name = header.getStringValue("OBJECT");
		if(name == null) name = UNDEFINED;
	}
		
	public Fits createFits() throws HeaderCardException, FitsException, IOException {
		FitsFactory.setUseHierarch(true);
		Fits fits = new Fits();	
		fits.addHDU(createHDU());
	
		return fits;
	}

	public Fits findFits(String name) throws FitsException, IOException {
		FitsFactory.setUseHierarch(true);
		Fits fits;
	
		try { 
			fits = new Fits(new File(name)); 
			fileName = name;
		}
		catch(Exception e) { 
			fileName = CRUSH.workPath + name;
			fits = new Fits(new File(fileName)); 
		}
		
		return fits;
	}
	

	public void setImage(BasicHDU HDU) throws FitsException {		
		Object image = HDU.getData().getData();
		final double u = unit.value();
	
		try {
			final float[][] fdata = (float[][]) image;
			
			new Task<Void>() {
				@Override
				public void process(int i, int j) {
					if(!Float.isNaN(fdata[j][i])) {
						setValue(i, j, fdata[j][i] * u);	    
						unflag(i, j);
					}
				}
			}.process();
		}
		catch(ClassCastException e) {
			final double[][] ddata = (double[][]) image;
			
			new Task<Void>() {
				@Override
				public void process(int i, int j) {
					if(!Double.isNaN(ddata[j][i])) {
						setValue(i, j, ddata[j][i] * u);	    
						unflag(i, j);
					}
				}
			}.process();
		}
	}
	
	public void write() throws HeaderCardException, FitsException, IOException {
		write(fileName);
	}

	public void write(String name) throws HeaderCardException, FitsException, IOException {
		Fits fits = createFits();	
		BufferedDataOutputStream file = new BufferedDataOutputStream(new FileOutputStream(name));
		fits.write(file);	
		System.err.println(" Written " + name);
	}
	
	public final void editHeader(BasicHDU hdu) throws HeaderCardException, FitsException, IOException {
		nom.tam.util.Cursor cursor = hdu.getHeader().iterator();
		
		// Go to the end of the header cards...
		while(cursor.hasNext()) cursor.next();
		editHeader(cursor);
	}
		
	// TODO what to do with CREATOR/CRUSHVER
	// TODO what about duplicate keywords (that's a cursor issue...)
	// ... Maybe check for duplicates...
	// TODO copy over existing header keys (non-conflicting...) 
	public void editHeader(Cursor cursor) throws HeaderCardException, FitsException, IOException {
		cursor.add(new HeaderCard("OBJECT", name, "The source name."));
		cursor.add(new HeaderCard("EXTNAME", contentType, "The type of data contained in this HDU"));
		cursor.add(new HeaderCard("DATE", FitsDate.getFitsDateString(), "Time-stamp of creation."));
		cursor.add(new HeaderCard("CREATOR", creator, "The software that created the image."));	
			
		Range range = getRange();

		cursor.add(new HeaderCard("DATAMIN", range.min() / unit.value(), "The lowest value in the image"));
		cursor.add(new HeaderCard("DATAMAX", range.max() / unit.value(), "The highest value in the image"));

		cursor.add(new HeaderCard("BZERO", 0.0, "Zeroing level of the image data"));
		cursor.add(new HeaderCard("BSCALE", 1.0, "Scaling of the image data"));
		cursor.add(new HeaderCard("BUNIT", unit.name(), "The image data unit."));
		
		//cursor.add(new HeaderCard("ORIGIN", "Caltech", "California Institute of Technology"));
	}

	public ImageHDU createHDU() throws HeaderCardException, FitsException, IOException {
		final float[][] fitsImage = new float[sizeY()][sizeX()];
		final double u = unit.value();
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) fitsImage[j][i] = (float) (getValue(i, j) / u);
				else fitsImage[j][i] = Float.NaN;
			}
		}.process();
		
		ImageHDU hdu = (ImageHDU)Fits.makeHDU(fitsImage);
		editHeader(hdu);
		
		return hdu;
	}
	
	public void addLongHierarchKey(Cursor cursor, String key, String value) throws FitsException, HeaderCardException {
		addLongHierarchKey(cursor, key, 0, value);
	}

	public void addLongHierarchKey(Cursor cursor, String key, int part, String value) throws FitsException, HeaderCardException {
		if(value.length() == 0) value = "true";

		String alt = part > 0 ? "." + part : "";

		int available = 69 - (key.length() + alt.length() + 3);

		if(value.length() < available) cursor.add(new HeaderCard("HIERARCH." + key + alt, value, null));
		else { 
			if(alt.length() == 0) {
				part = 1;
				alt = "." + part;
				available -= 2;
			}

			cursor.add(new HeaderCard("HIERARCH." + key + alt, value.substring(0, available), null));
			addLongHierarchKey(cursor, key, (char)(part+1), value.substring(available)); 
		}
	}
	
	

	public void setKey(String key, String value) throws HeaderCardException {
		String comment = header.containsKey(key) ? header.findCard(key).getComment() : "Set bu user.";

		// Try add as boolean, int or double -- fall back to String...
		try{ header.addValue(key, Util.parseBoolean(value), comment); }
		catch(NumberFormatException e1) { 
			try{ header.addValue(key, Integer.parseInt(value), comment); }
			catch(NumberFormatException e2) {
				try{ header.addValue(key, Double.parseDouble(value), comment); }
				catch(NumberFormatException e3) { header.addValue(key, value, comment); }
			}
		}
	}

	public void printHeader() {
		header.dumpHeader(System.out);
	}  
	
	
	
	public abstract class Task<ReturnType> extends Process<ReturnType> {			
		
		@Override
		public void process(int threadCount) {
			try { super.process(threadCount); }
			catch(Exception e) { e.printStackTrace(); }
		}
		
		public void process() {
			process(parallelism);
		}
		
		@Override
		public void processIndex(int index, int threadCount) {
			final int sizeX = sizeX();
			for(int i=index; i<sizeX; i += threadCount) {
				processX(i);
				Thread.yield();
			}
		}
	
		public void processX(int i) {
			for(int j=sizeY(); --j >= 0; ) process(i, j);
		}
		
		public abstract void process(int i, int j);
		
	}
	
	
	public abstract class AveragingTask extends Task<WeightedPoint> {
		@Override
		public WeightedPoint getResult() {
			WeightedPoint ave = new WeightedPoint();
			for(Process<WeightedPoint> task : getWorkers()) {
				WeightedPoint partial = task.getPartialResult();
				ave.add(partial.value());
				ave.addWeight(partial.weight());
			}
			if(ave.weight() > 0.0) ave.scaleValue(1.0 / ave.weight());
			return ave;
		}
	}
	
	public abstract class InterpolatingTask extends Task<Void> {
		private InterpolatorData ipolData;
		@Override
		public void init() { ipolData = new InterpolatorData(); }
		public final InterpolatorData getInterpolatorData() { return ipolData; }
	}	
	
	
	public static class InterpolatorData {
		SplineCoeffs splineX, splineY;
		
		public InterpolatorData() {
			splineX = new SplineCoeffs();
			splineY = new SplineCoeffs();
		}
		
		public void centerOn(double deltax, double deltay) {
			splineX.centerOn(deltax);
			splineY.centerOn(deltay);
		}

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
	
	// 2 pi sigma^2 = a^2
	// a = sqrt(2 pi) sigma
	//   = sqrt(2 pi) fwhm / 2.35
	public static double fwhm2size = Math.sqrt(2.0 * Math.PI) / Util.sigmasInFWHM;
	public static String UNDEFINED = "<undefined>";
	
	public final static int NEAREST_NEIGHBOR = 0;
	public final static int BILINEAR = 1;
	public final static int PIECEWISE_QUADRATIC = 2;
	public final static int BICUBIC_SPLINE = 3;
	
}

