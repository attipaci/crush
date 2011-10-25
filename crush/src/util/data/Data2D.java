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
import java.util.Vector;

import util.Range;
import util.Unit;
import util.Util;
import util.Vector2D;

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

	public int parallelism = Runtime.getRuntime().availableProcessors();
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
	
	public void setParallel(int n) { parallelism = Math.max(1, n); }
	
	public void noParallel() { parallelism = 1; }
	
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
		copy.copyImageOf(this);
		return copy;
	}

	// TODO make it work with parallel... (but really...)
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
		
		new Task<Void>() {
			@Override
			public void processIndex(int i) { Arrays.fill(flag[i], 1); }
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
				public void processIndex(int i) { Arrays.fill(flag[i], value); }
				@Override
				public void process(int i, int j) {}
			}.process();	
		}
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
		return valueAtIndex(index.i, index.j);
	}
	
	public double valueAtIndex(int i, int j) { return flag[i][j] == 0 ? data[i][j] : Double.NaN; }
	
	public double valueAtIndex(Vector2D index) {
		return valueAtIndex(index.x, index.y, null);
	}
	
	public double valueAtIndex(Vector2D index, InterpolatorData ipolData) {
		return valueAtIndex(index.x, index.y, ipolData);
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
		final double[] ax = ipolData.splineX.coeffs;
		final double[] ay = ipolData.splineY.coeffs;
		
		final int i0 = (int)Math.floor(ic-1.0);
		final int fromi = Math.max(0, i0);
		final int toi = Math.min(sizeX(), i0+4);
		
		// Calculate the spline coefficients (as necessary)...
		if(ipolData.ic != ic) for(int i=toi; --i >= fromi; ) {
			final double dx = Math.abs(i - ic);
			ax[i-i0] = dx > 1.0 ? 
					((-0.5 * dx + 2.5) * dx - 4.0) * dx + 2.0 : (1.5 * dx - 2.5) * dx * dx + 1.0;
			ipolData.ic = ic;
		}
	
		final int j0 = (int)Math.floor(jc-1.0);
		final int fromj = Math.max(0, j0);
		final int toj = Math.min(sizeY(), j0+4);
		
		// Calculate the spline coefficients (as necessary)...
		if(ipolData.jc != jc) for(int j=toj; --j >= fromj; ) {
			final double dy = Math.abs(j - jc);
			ay[j-j0] = dy > 1.0 ? 
					((-0.5 * dy + 2.5) * dy - 4.0) * dy + 2.0 : (1.5 * dy - 2.5) * dy * dy + 1.0;
			ipolData.jc = jc;
		}
		
		// Do the spline convolution...
		double sum = 0.0, sumw = 0.0;
		for(int i=toi; --i >= fromi; ) for(int j=toj; --j >= fromj; ) if(flag[i][j] == 0) {
			final double w = ax[i-i0]*ay[j-j0];
			sum += w * data[i][j];
			sumw += w;
		}
		
		
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
		
		final Data2D antialiased = from;
		
		// Interpolate to new array...
		new InterpolatingTask() {
			@Override
			public void process(int i, int j) {
				if(flag[i][j]==0) {		
					data[i][j] = antialiased.valueAtIndex(i*stretch.x, j*stretch.y, getInterpolatorData());
					if(Double.isNaN(data[i][j])) flag[i][j] = 1;
				}
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
	
	public void setUnit(Unit u) {
		unit = u;
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
				for(Task<Double> task : parent.tasks) if(task.getPartialResult() < globalMin) globalMin = task.getPartialResult();
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
				for(Task<Double> task : parent.tasks) if(task.getPartialResult() > globalMax) globalMax = task.getPartialResult();
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
				for(Task<Range> task : parent.tasks) globalRange.include(task.getPartialResult());
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
					index.i = i;
					index.j = j;
				}
			}
			@Override 
			public Index2D getPartialResult() { return index; }
			@Override
			public Index2D getResult() {
				double globalPeak = Double.NEGATIVE_INFINITY;
				Index2D globalIndex = null;
				for(Task<Index2D> task : parent.tasks) {
					Index2D partial = task.getPartialResult();
					if(partial != null) if(data[partial.i][partial.j] > globalPeak) {
						globalIndex = partial;
						globalPeak = data[partial.i][partial.j];
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
					index.i = i;
					index.j = j;
				}
			}
			@Override 
			public Index2D getPartialResult() { return index; }
			@Override
			public Index2D getResult() {
				double globalDev = 0.0;
				Index2D globalIndex = null;
				for(Task<Index2D> task : parent.tasks) {
					Index2D partial = task.getPartialResult();
					if(partial == null) continue;
					final double value = Math.abs(data[partial.i][partial.j]);
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
					sumw += weightAt(i, j);
				}
			}
			@Override
			public WeightedPoint getPartialResult() { return new WeightedPoint(sum, sumw); }
		};
		
		average.process();
		return average.getResult().value;	
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

	
	public double getRMS() {
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
		return rms.getResult().value;	
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
				for(Task<Integer> task : parent.tasks) globalCount += task.getPartialResult();
				return globalCount;
			}
		};
		
		counter.process();
		return counter.getResult();
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
					convolved[i][j] = result.value;
					if(beamw != null) beamw[i][j] = result.weight;
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
			final double wB = weightAt(i1, j1) * beam[i1-i0][j1-j0];
			sum += wB * data[i1][j1];
			sumw += Math.abs(wB);		    
		}

		result.value = sum / sumw;
		result.weight = sumw;
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
		
		int bins = 1 + (int)Math.round(range.max / binSize) - (int)Math.round(range.min / binSize);
		final Vector2D[] bin = new Vector2D[bins];
		for(int i=0; i<bins; i++) bin[i] = new Vector2D(i*binSize, 0.0);
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(flag[i][j] == 0) bin[(int)Math.round(image[i][j] / binSize)].y++;
			}
		}.process();
		
		return bin;
	}
	
	protected class Parallel<ReturnType> {
		/**
		 * 
		 */
		int maxThreads;
		public Vector<Task<ReturnType>> tasks = new Vector<Task<ReturnType>>();
		
		public Parallel(Task<ReturnType> task) {
			this(task, parallelism);
			//this(task, 1);
		}
		
		public Parallel(Task<ReturnType> task, int maxThreads) {
			this.maxThreads = maxThreads;
			
			tasks.ensureCapacity(maxThreads);
			task.setOwner(this);
			
			// Use only copies of the task for calculation, leaving the template
			// task in its original state, s.t. it may be reused again...
			for(int i=0; i<maxThreads; i++) {
				@SuppressWarnings("unchecked")
				Task<ReturnType> t = (Task<ReturnType>) task.clone();
				t.setIndex(i);
				t.init();
				tasks.add(t);
			}
		}
		
		public int size() { return tasks.size(); }
		
		public synchronized void process() {
			for(Task<?> task : tasks) task.start();
			
			for(Task<?> task : tasks) {
				try { 
					task.join(); 
					if(task.isAlive) {
						System.err.println("WARNING! Premature conclusion of parallel image processing.");
						System.err.println("         Please notify Attila Kovacs <kovacs@astro.umn.edu>.");
						new Exception().printStackTrace();
					}
				}
				catch(InterruptedException e) { 
					System.err.println("WARNING! Parallel image processing was unexpectedly interrupted.");
					System.err.println("         Please notify Attila Kovacs <kovacs@astro.umn.edu>.");
					new Exception().printStackTrace();
				}
				
			}
			
			for(Task<?> task : tasks) if(task.isAlive) System.err.println("!!! Alive...");
		}
	}
	
	public abstract class Task<ReturnType> extends Thread implements Cloneable {			
		/**
		 * 
		 */
		
		private static final long serialVersionUID = -3973614679104705385L;
		public Parallel<ReturnType> parent;
		
		boolean isAlive = false;
		int offset;
		
		public void process() {
			Parallel<ReturnType> parallel = new Parallel<ReturnType>(this);
			parallel.process();
		}
		
		@Override
		public Object clone() {
			try { return super.clone(); }
			catch(CloneNotSupportedException e) { return null; }
		}
		
		public void init() {}
		
		public void setOwner(Parallel<ReturnType> p) {
			this.parent = p;
		}
		
		public void setIndex(int index) {
			if(isAlive) throw new IllegalThreadStateException("Cannot change task index while running.");
			this.offset = index;
		}
		
		@Override
		public void run() {
			isAlive = true;
			int step = parent.size();
			final int sizeX = sizeX();
			for(int i=offset; i<sizeX; i += step) {
				processIndex(i);
				Thread.yield();
			}
			isAlive = false;
		}
	
		public void processIndex(int i) {
			for(int j=sizeY(); --j >= 0; ) process(i, j);
		}
		
		public abstract void process(int i, int j);
		
		public ReturnType getPartialResult() {
			return null;
		}
		
		public ReturnType getResult() {
			return null;
		}	
	}
	
	
	public abstract class AveragingTask extends Task<WeightedPoint> {
		@Override
		public WeightedPoint getResult() {
			WeightedPoint ave = new WeightedPoint();
			for(Task<WeightedPoint> task : parent.tasks) {
				WeightedPoint partial = task.getPartialResult();
				ave.value += partial.value;
				ave.weight += partial.weight;
			}
			if(ave.weight > 0.0) ave.value /= ave.weight;
			return ave;
		}
	}
	
	public abstract class InterpolatingTask extends Task<Void> {
		private InterpolatorData ipolData;
		@Override
		public void init() { ipolData = new InterpolatorData(); }
		public final InterpolatorData getInterpolatorData() { return ipolData; }
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
	
	
	public class InterpolatorData {
		double ic = Double.NaN, jc = Double.NaN;
		SplineCoeffs splineX, splineY;
		
		public InterpolatorData() {
			refresh();
		}
		
		public void refresh() {
			if(interpolationType == BICUBIC_SPLINE) {
				if(splineX == null) splineX = new SplineCoeffs();
				if(splineY == null) splineY = new SplineCoeffs();
			}
		}	
	}
	
}

