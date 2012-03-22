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

import java.io.IOException;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;
import util.CoordinatePair;
import util.Unit;
import util.Util;
import util.Vector2D;

public class GridMap<CoordinateType extends CoordinatePair> extends GridImage<CoordinateType> implements Noise2D, Timed2D {
	private double[][] weight, count;
	public double weightFactor = 1.0;
	
	public double filterBlanking = Double.NaN;
	public double clippingS2N = Double.NaN;
	
	private Vector2D reuseOffset = new Vector2D();
	
	public GridMap() { 
		setContentType("Signal");
	}
	
	public GridMap(String fileName) throws Exception { 
		read(fileName);		
	}

	public GridMap(int i, int j) { 
		super(i, j);
	}
	
	@Override
	public Object clone() {
		GridMap<?> clone = (GridMap<?>) super.clone();
		clone.reuseOffset = new Vector2D();
		return clone;
	}
	
	@Override
	protected void copy(Data2D other, int i) {
		super.copy(other, i); 
		if(!(other instanceof GridMap)) return;
		
		final GridMap<?> image = (GridMap<?>) other;
		System.arraycopy(image.weight[i], 0, weight[i], 0, sizeY()); 
		System.arraycopy(image.count[i], 0, count[i], 0, sizeY()); 
	}
	

	@Override
	public void setImage(Data2D other) {
		super.setImage(other);
		if(!(other instanceof GridMap)) return;
		
		GridMap<?> image = (GridMap<?>) other;

		// Make a copy of the fundamental data
		setWeight(image.getWeight());
		setTime(image.getTime());
	}
	
	@Override
	public void setSize(int i, int j) {
		super.setSize(i, j);
		setWeight(new double[i][j]);
		setTime(new double[i][j]);
	}
	
	@Override
	public String getPixelInfo(int i, int j) {
		if(!isValid(i, j)) return "";
		String type = "";
		if(getContentType() != null) if(getContentType().length() != 0) type = getContentType() + "> ";
		return type + Util.getDecimalFormat(getS2N(i, j)).format(getValue(i, j)) + " +- " + Util.s2.format(getRMS(i, j)) + " " + getUnit().name();
	}
	
	@Override
	protected void crop(int imin, int jmin, int imax, int jmax) {
		double[][] oldweight = getWeight();
		double[][] oldcount = getTime();
		
		final int fromi = Math.max(0, imin);
		final int fromj = Math.max(0, jmin);
		final int toi = Math.min(sizeX()-1, imax);
		final int toj = Math.min(sizeY()-1, jmax);
		
		super.crop(imin, jmin, imax, jmax);
			
		for(int i=fromi, i1=fromi-imin; i<=toi; i++, i1++) for(int j=fromj, j1=fromj-jmin; j<=toj; j++, j1++) {
			setWeight(i1, j1, oldweight[i][j]);
			setTime(i1, j1, oldcount[i][j]);
		}
	}


	@Override
	public void clear(int i, int  j) {
		super.clear(i, j);
		setWeight(i, j, 0.0);
		setTime(i, j, 0.0);
	}
	
	public final void addPointAt(Vector2D mapOffset, final double value, final double g, final double w, final double time) {
		double x = mapOffset.getX();
		double y = mapOffset.getY();
		toIndex(mapOffset);
		addPointAt((int)Math.round(mapOffset.getX()), (int)Math.round(mapOffset.getY()), value, g, w, time);
		mapOffset.set(x, y);
	}
	
	public final void addPointAt(CoordinateType coords, final double value, final double g, final double w, final double time) {
		getProjection().project(coords, reuseOffset);
		toIndex(reuseOffset);
		addPointAt((int)Math.round(reuseOffset.getX()), (int)Math.round(reuseOffset.getY()), value, g, w, time);
	}

	public final void addPointAt(final int i, final int j, final double value, final double g, double w, final double time) {
		w *= g;
		increment(i, j, w * value);
		incrementWeight(i, j, w * g);
		incrementTime(i, j, time);
	}
		
	public synchronized void addDirect(final GridMap<?> map, final double w) {
		new Task<Void>() {		
			@Override
			public void process(int i, int j) {
				if(map.isUnflagged(i, j)) {
					final double ww = w * map.getWeight(i, j);
					increment(i, j, ww * map.getValue(i, j));
					incrementWeight(i, j, ww);
					incrementTime(i, j, map.getTime(i, j));
					unflag(i, j);
				}
			}
		}.process();
	}
	
	@Override
	protected void sanitize(int i, int j) {
		super.sanitize(i, j);
		setWeight(i, j, 0.0);
	}
	

	public void applyCorrection(double filtering, final double[][] significance) {
		final double ifiltering = 1.0 / filtering;
		final double filtering2 = filtering * filtering;
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(getWeight(i, j) > 0.0) if(significance[i][j] <= clippingS2N) {
					scaleValue(i, j, ifiltering);
					scaleWeight(i, j, filtering2);
				}
			}
		}.process();
	}

	// It's important to completely reset clipped points, otherwise they can be used...
	// One possibility for future is to raise flag only, then call sanitize()
	public void clipAboveRelativeRMS(double maxRelativeRMS) {
		clipAboveRelativeRMS(maxRelativeRMS, 0.0);
	}
	
	public void clipAboveRelativeRMS(double maxRelativeRMS, double refPercentile) {
		final double[][] rms = getRMSImage().getData();
		final double maxRMS = maxRelativeRMS * new Data2D(rms).select(refPercentile);
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) if(rms[i][j] > maxRMS) flag(i, j);
			}
		}.process();
	}
	
	public void clipAboveRMS(final double value) {
		final double[][] rms = getRMSImage().getData();
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) if(rms[i][j] > value) flag(i, j);
			}
		}.process();
	}
	
	public void clipBelowRelativeExposure(double minRelativeExposure) {
		clipBelowRelativeExposure(minRelativeExposure, 1.0);
	}
	
	public void clipBelowRelativeExposure(double minRelativeExposure, double refPercentile) {
		double minIntTime = minRelativeExposure * new Data2D(getTime()).select(refPercentile);
		clipBelowExposure(minIntTime);
	}
	
	public void clipBelowExposure(final double minIntTime) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) if(getTime(i, j) < minIntTime) flag(i, j);
			}
		}.process();
	}

	public void s2nClipBelow(final double level) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) if(getS2N(i, j) < level) flag(i, j);
			}
		}.process();
	}

	public void s2nClipAbove(final double level) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) if(getS2N(i,j) > level) flag(i, j);
			}
		}.process();
	}


	public boolean[][] getMask(final double minS2N, final int minNeighbours) {
		final boolean[][] mask = new boolean[sizeX()][sizeY()];
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) if(getS2N(i,j) > minS2N) mask[i][j] = true;
			}
		}.process();
			
		if(minNeighbours > 0) {
			final boolean[][] cleaned = new boolean[sizeX()][sizeY()];
		
			new Task<Void>() {
				@Override
				public void process(int i, int j) {
					if(mask[i][j]) {
						int neighbours = -1;
						final int fromi = Math.max(0, i-1);
						final int toi = Math.min(sizeX(), i+1);
						final int fromj = Math.max(0, j-1);
						final int toj = Math.min(sizeY(), j+1);
						for(int i1=toi; --i1 >= fromi; ) for(int j1=toj; --j1 >= fromj; ) if(mask[i1][j1]) neighbours++;
						if(neighbours >= minNeighbours) cleaned[i][j] = true;
					}
				}
			}.process();
			return cleaned;
		}
		
		return mask;
	}
	
	@Override
	public void smooth(double[][] beam) {
		double[][] beamw = new double[sizeX()][sizeY()];
		setData(getSmoothed(beam, beamw));
		setTime(getTimeImage().getSmoothed(beam, null));
		setWeight(beamw);
	}
	
	@Override
	public void fastSmooth(double[][] beam, int stepX, int stepY) {
		double[][] beamw = new double[sizeX()][sizeY()];
		setData(getFastSmoothed(beam, beamw, stepX, stepY));
		setTime(getTimeImage().getFastSmoothed(beam, null, stepX, stepY));
		setWeight(beamw);
	}
	
	@Override
	public void resample(final GridImage<CoordinateType> from) {
			
		if(!(from instanceof GridMap)) {
			super.resample(from);
			return;
		}
		
		if(isVerbose()) System.err.println(" Resampling map to "+ sizeX() + "x" + sizeY() + ".");
		
		final GridMap<CoordinateType> fromMap = (GridMap<CoordinateType>) from;
		final GridImage<CoordinateType> fromWeight = fromMap.getWeightImage();
		final GridImage<CoordinateType> fromCount = fromMap.getTimeImage();
		
		new InterpolatingTask() {
			private Vector2D v;
			@Override
			public void init() { 
				super.init();
				v = new Vector2D(); 	
			}
			@Override
			public void process(int i, int j) {
				v.set(i, j);
				toOffset(v);
				from.toIndex(v);
				
				//System.err.println(i + "," + j + " <--" + Util.f1.format(v.x) + "," + Util.f1.format(v.y));
		
				final InterpolatorData ipolData = getInterpolatorData();
				setValue(i, j, fromMap.valueAtIndex(v.getX(), v.getY(), ipolData));
				setWeight(i, j, fromWeight.valueAtIndex(v.getX(), v.getY(), ipolData));
				setTime(i, j, fromCount.valueAtIndex(v.getX(), v.getY(), ipolData));		
				
				if(isNaN(i, j)) flag(i, j);
				else { unflag(i, j); }
			}
		}.process();
	
	}
	
	public GridImage<CoordinateType> getWeightImage() { 
		double unit = getUnit().value();
		return getImage(getWeight(), "Weight", new Unit("[" + getUnit().name() + "]**(-2)", 1.0 / (unit * unit)));
	}
	
	public GridImage<CoordinateType> getTimeImage() { 
		return getImage(getTime(), "Exposure", new Unit("s/pixel", Unit.s));
	}
	
	public GridImage<CoordinateType> getRMSImage() {
		return getImage(getRMS(), "Noise", getUnit());
	}
	
	public GridImage<CoordinateType> getS2NImage() { 
		return getImage(getS2N(), "S/N", Unit.unity);
	}
	
	
	@Override
	public final double getRMS(final int i, final int j) {
		return 1.0 / Math.sqrt(getWeight(i, j));		
	}
	
	@Override
	public final double getS2N(final int i, final int j) {
		return getValue(i, j) / getRMS(i,j);		
	}
	
	public double getTypicalRMS() {
		Task<WeightedPoint> avew = new AveragingTask() {
			private double sumw = 0.0;
			private int n = 0;
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) {
					sumw += getWeight(i, j);
					n++;
				}
			}
			@Override
			public WeightedPoint getPartialResult() { return new WeightedPoint(sumw, n); }
		};
			
		avew.process();		
		return Math.sqrt(1.0 / avew.getResult().value());
	}
	
	
		
	@Override
	public int[][] getSkip(final double blankingValue) {
		final int[][] skip = (int[][]) copyOf(getFlag());
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(getS2N(i,j) > blankingValue) skip[i][j] = 1;
			}
		}.process();
		
		return skip;
	}
	
	public void filterAbove(double extendedFWHM, double blankingValue) {
		filterAbove(extendedFWHM, getSkip(blankingValue));
		filterBlanking = blankingValue;
	}


	public void fftFilterAbove(double extendedFWHM, double blankingValue) {
		fftFilterAbove(extendedFWHM, getSkip(blankingValue));
		filterBlanking = blankingValue;
	}
	
	
	public double getMeanIntegrationTime() {
		Task<WeightedPoint> meanIntTime = new AveragingTask() {
			private double sum = 0.0, sumw = 0.0;	
			@Override
			public void process(final int i, final int j) {
				if(isUnflagged(i, j)) {
					final double w = getWeight(i, j);
					sum += w * getTime(i, j);
					sumw += w;
				}
			}
			@Override
			public WeightedPoint getPartialResult() { return new WeightedPoint(sum, sumw); }
		};
		
		meanIntTime.process();
		return meanIntTime.getResult().value();
	}
	
	@Override
	public double median() {
		WeightedPoint[] point = new WeightedPoint[countPoints()];

		if(point.length == 0) return 0.0;

		int k=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(isUnflagged(i, j))
			point[k++] = new WeightedPoint(getValue(i, j), getWeight(i, j));
		
		return Statistics.median(point).value();
	}

	public void reweight(boolean robust) {
		double weightCorrection = 1.0 / (robust ? getRobustChi2() : getChi2());
		scaleWeight(weightCorrection);
		weightFactor *= weightCorrection;
	}

	// Return to calculated weights...
	public void dataWeight() {
		scaleWeight(1.0/weightFactor);
		weightFactor = 1.0;
	}

	public double getChi2(boolean robust) {
		return robust ? getRobustChi2() : getChi2();
	}
	
	protected double getRobustChi2() {
		float[] chi2 = new float[sizeX() * sizeY()];
		if(chi2.length == 0) return 0.0;
		
		int k=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(isUnflagged(i, j)) {
			final float s2n = (float) getS2N(i,j);
			chi2[k++] = s2n * s2n;
		}
	
		// median(x^2) = 0.454937 * sigma^2 
		return k > 0 ? Statistics.median(chi2, 0, k) / 0.454937 : 0.0;	
	}

	protected double getChi2() {
		Task<WeightedPoint> rChi2 = new AveragingTask() {
			private double chi2 = 0.0;
			private int n = 0;	
			@Override
			public void process(final int i, final int j) {
				if(isUnflagged(i, j)) {
					final double s2n = getS2N(i,j);
					chi2 += s2n * s2n;
					n++;
				}
			}
			@Override
			public WeightedPoint getPartialResult() { return new WeightedPoint(chi2, n); }
		};
		
		rChi2.process();
		return rChi2.getResult().value();
	}
	
	
	// TODO redo with parallel... Need global interrupt (static interrupt()?)
	public boolean containsNaN() {
		boolean hasNaN = false;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
			if(isNaN(i, j)) hasNaN = true;
			if(Double.isNaN(getWeight(i, j))) hasNaN = true;
		}
		return hasNaN;
	}


	// derive the MEM correction if MEM modeling
	
	public void MEM(double lambda, boolean forceNonNegative) {
		//double[][] smoothed = getConvolvedTo(data, 5.0*instrument.resolution);
		double[][] smoothed = new double[sizeX()][sizeY()];
		//if(forceNonNegative) for(int x=0; x<sizeX(); x++) for(int y=0; y<sizeY(); y++) if(smoothed[x][y] < 0.0) smoothed[x][y] = 0.0;
		MEM(smoothed, lambda);
	}
		
	public void MEM(final double[][] model, final double lambda) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) {
					final double sigma = getRMS(i, j);
					final double memValue = Math.hypot(sigma, getValue(i, j)) / Math.hypot(sigma, model[i][j]) ;
					decrement(i, j, Math.signum(getValue(i, j)) * lambda * sigma * Math.log(memValue));
				}
			}
		}.process();
	}	
	
	@Override
	public Fits createFits() throws HeaderCardException, FitsException, IOException {
		Fits fits = super.createFits();
		fits.addHDU(getTimeImage().createHDU());
		fits.addHDU(getRMSImage().createHDU());
		fits.addHDU(getS2NImage().createHDU());
		return fits;
	}

	@Override
	public void read(Fits fits) throws Exception {
		// Get the coordinate system information
		BasicHDU[] HDU = fits.read();
		parseHeader(HDU[0].getHeader());	
		readData(HDU);
	}

	@Override
	public void parseHeader(Header header) throws Exception {			
		super.parseHeader(header);
		
		weightFactor =  header.getDoubleValue("XWEIGHT", 1.0);
		filterBlanking = header.getDoubleValue("FLTRBLNK", header.getDoubleValue("MAP_XBLK", Double.NaN));
		clippingS2N = header.getDoubleValue("CLIPS2N", header.getDoubleValue("MAP_CLIP", Double.NaN));
	}


	private void readData(BasicHDU[] HDU) throws FitsException {
		super.readData(HDU[0]);
		
		getTimeImage().setImage(HDU[1]);
		
		GridImage<CoordinateType> noise = getWeightImage();
		noise.setUnit(getUnit());
		noise.setImage(HDU[2]);
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				final double w = getWeight(i, j);
				setWeight(i, j, 1.0 / (w * w)); 
			}
		}.process();
	}

	
	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException, FitsException, IOException {
		super.editHeader(cursor);
		
		if(!Double.isNaN(filterBlanking))
			cursor.add(new HeaderCard("FLTRBLNK", filterBlanking, "The S/N blanking of LSS filter."));
		if(!Double.isNaN(clippingS2N))
			cursor.add(new HeaderCard("CLIPS2N", clippingS2N, "The S/N clipping level used in reduction."));
		
	}
	
	    

	public void despike(final double significance) {
		final double[][] neighbours = {{ 0, 1, 0 }, { 1, 0, 1 }, { 0, 1, 0 }};
		final GridMap<?> diff = (GridMap<?>) copy();
		diff.smooth(neighbours);
		
		new Task<Void>() {	
			private WeightedPoint point, surrounding;
			@Override
			public void init() {
				point = new WeightedPoint();
				surrounding = new WeightedPoint();
			}
			@Override
			public void process(final int i, final int j) {
				if(isUnflagged(i, j)) {
					point.setValue(getValue(i, j));
					point.setWeight(getWeight(i, j));
					surrounding.setValue(diff.getValue(i, j));
					surrounding.setWeight(diff.getWeight(i, j));
					point.subtract(surrounding);
					if(DataPoint.significanceOf(point) > significance) flag(i, j);			
				}	
			}
		}.process();
	}
	
	public DataPoint getFlux(Region<CoordinateType> region) {
		final Bounds bounds = region.getBounds(this);
		double flux = 0.0, var = 0.0;

		double A = 1.0 / getPointsPerSmoothingBeam();

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j)) if(region.isInside(getGrid(), i, j))  {
				flux += getValue(i, j);
				var += 1.0 / getWeight(i, j);
			}
		
		return new DataPoint(A * flux, A * Math.sqrt(var));
	}

	public double getRMS(Region<CoordinateType> region) {
		final Bounds bounds = region.getBounds(this);
		double var = 0.0;
		int n = 0;
		double level = getLevel(region);

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j)) if(region.isInside(getGrid(), i, j))  {
				double value = getValue(i, j) - level;
				var += value * value;
				n++;
			}
		var /= (n-1);

		return Math.sqrt(var);
	}	
	
	
	public double getMeanNoise(Region<CoordinateType> region) {
		final Bounds bounds = region.getBounds(this);
		double var = 0.0;
		int n = 0;

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j))  if(region.isInside(getGrid(), i, j)) {
				final double rms = getRMS(i,j);
				var += rms * rms;
				n++;
			}
		var /= (n-1);
		
		return Math.sqrt(var);
	}
	
	public double getMeanExposure(Region<CoordinateType> region) {	
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		int n = 0;

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j)) if(region.isInside(getGrid(), i, j)) {
				sum += getTime(i, j);
				n++;
			}
		
		return n > 0 ? sum / n : 0.0;
	}
	
	@Override
	public int clean(double[][] beam, double gain, double replacementFWHM) {
	    	return clean(getS2NImage(), beam, gain, replacementFWHM);
	}
	
	
	@Override
	public String toString() {	
		String info = super.toString() +
			"  Noise Estimate from: " + (weightFactor == 1.0 ? "data" : "image (" + Util.f2.format(1.0 / weightFactor) + "x data)") + "\n"; 

		return info;
	}

	public final double[][] getTime() {
		return count;
	}

	public final void setTime(final double[][] image) {
		count = image;
	}

	public final double getTime(final int i, final int j) {
		return count[i][j];
	}

	public final void setTime(final int i, final int j, final double t) {
		count[i][j] = t;
	}

	public final void incrementTime(final int i, final int j, final double dt) {
		count[i][j] += dt;
	}

	public final double[][] getWeight() {
		return weight;
	}

	public final void setWeight(final double[][] image) {
		weight = image;
	}

	@Override
	public final double getWeight(final int i, final int j) {
		return weight[i][j];
	}

	public void scaleWeight(final double scalar) {
		if(scalar == 1.0) return;
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) { scaleWeight(i, j, scalar); }
		}.process();
	}
	
	public final void setWeight(final int i, final int j, final double w) {
		weight[i][j] = w;
	}

	public final void incrementWeight(final int i, final int j, final double dw) {
		weight[i][j] += dw;
	}
	
	public void scaleWeight(final int i, final int j, final double factor) {
		weight[i][j] *= factor;
	}


	public double[][] getRMS() {
		final double[][] rms = new double[sizeX()][sizeY()];
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) { rms[i][j] = getRMS(i,j); }
		}.process();
		
		return rms;
	}
	
	public void setRMS(final double[][] image) {
		new Task<Void>() {
			@Override
			public void process(int i, int j) { setRMS(i,j, image[i][j]); }
		}.process();
	}

	public void scaleRMS(final double scalar) {
		scaleWeight(1.0 / (scalar*scalar));
	}
	
	public void setRMS(int i, int j, double sigma) {
		setWeight(i, j, 1.0 / (sigma * sigma));	
	}

	public void scaleRMS(int i, int j, double factor) {
		scaleWeight(i, j, 1.0 / (factor * factor));
	}

	public double[][] getS2N() {
		final double[][] s2n = new double[sizeX()][sizeY()];
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) { s2n[i][j] = getS2N(i,j); }
		}.process();
		
		return s2n;
	}
	
}

