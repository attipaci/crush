/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2007 Attila Kovacs 

package crush.sourcemodel;

import nom.tam.fits.*;
import nom.tam.util.*;



import java.io.*;
import java.util.*;

import crush.CRUSH;
import crush.GenericInstrument;
import crush.Instrument;
import crush.Scan;

import util.*;
import util.data.DataPoint;
import util.data.FFT;
import util.data.Statistics;
import util.data.WeightedPoint;


public class AstroMap extends AstroImage {
	public double[][] weight, count;
	
	public Vector<Scan<?, ?>> scans = new Vector<Scan<?, ?>>();
	public String commandLine;
	
	public int generation = 0;
	public double integrationTime = 0.0;	
	public double weightFactor = 1.0;
 
	public double filterBlanking = Double.NaN;
	public double clippingS2N = Double.NaN;
		
	private Vector2D reuseOffset = new Vector2D();

	public AstroMap() { 
		contentType = "Signal";
	}
	
	public AstroMap(Instrument<?> instrument) { 
		this(); 
		this.instrument = instrument;
	}
	
	public AstroMap(String fileName, Instrument<?> instrument) throws FitsException, HeaderCardException, IOException { 
		this(instrument);
		read(fileName);		
	}

	public AstroMap(int i, int j) { 
		this();
		setSize(i, j);
	}
	
	@Override
	public Object clone() {
		AstroMap clone = (AstroMap) super.clone();
		clone.reuseOffset = new Vector2D();
		return clone;
	}
	
	@Override
	public void copyImageOf(AstroImage other) {
		super.copyImageOf(other);
		if(!(other instanceof AstroMap)) return;
		
		AstroMap image = (AstroMap) other;
		
		// Make a copy of the fundamental data
		weight = (double[][]) copyOf(image.weight);
		count = (double[][]) copyOf(image.count);
	}
	
	public void shortInfo() {
		System.err.println("\n\n  [" + sourceName + "]\n" + super.toString());
	}

	@Override
	public void setSize(int i, int j) {
		super.setSize(i, j);
		weight = new double[i][j];
		count = new double[i][j];
	}
	
	@Override
	public String getPixelInfo(int i, int j) {
		if(!isValid(i, j)) return "";
		String type = "";
		if(contentType != null) if(contentType.length() != 0) type = contentType + "> ";
		return type + Util.getDecimalFormat(getS2N(i, j)).format(data[i][j]) + " +- " + Util.s2.format(getRMS(i, j)) + " " + unit.name;
	}
	
	
	@Override
	protected void crop(int imin, int jmin, int imax, int jmax) {
		double[][] oldweight = weight;
		double[][] oldcount = count;
		
		final int fromi = Math.max(0, imin);
		final int fromj = Math.max(0, jmin);
		final int toi = Math.min(sizeX()-1, imax);
		final int toj = Math.min(sizeY()-1, jmax);
		
		super.crop(imin, jmin, imax, jmax);
			
		for(int i=fromi, i1=fromi-imin; i<=toi; i++, i1++) for(int j=fromj, j1=fromj-jmin; j<=toj; j++, j1++) {
			weight[i1][j1] = oldweight[i][j];
			count[i1][j1] = oldcount[i][j];
		}
	}


	@Override
	public void clear() {
		super.clear();
		for(int i=sizeX(); --i >= 0; ) {
			Arrays.fill(weight[i], 0.0);
			Arrays.fill(count[i], 0.0);
		}
	}

	@Override
	public void reset() {
		super.reset();
		integrationTime = 0.0;
	}
	
	@Override
	public double getWeight(int i, int j) { return weight[i][j]; }
	
	@Override
	public void scale(int i, int j, double factor) {
		data[i][j] *= factor;
		weight[i][j] /= factor * factor;
	}

	public final void addPointAt(Vector2D mapOffset, final double value, final double g, final double w, final double time) {
		double x = mapOffset.x;
		double y = mapOffset.y;
		toIndex(mapOffset);
		addPointAt((int)Math.round(mapOffset.x), (int)Math.round(mapOffset.y), value, g, w, time);
		mapOffset.x = x;
		mapOffset.y = y;
	}
	
	public final void addPointAt(SphericalCoordinates coords, final double value, final double g, final double w, final double time) {
		getProjection().project(coords, reuseOffset);
		toIndex(reuseOffset);
		addPointAt((int)Math.round(reuseOffset.x), (int)Math.round(reuseOffset.y), value, g, w, time);
	}

	public final void addPointAt(final int i, final int j, final double value, final double g, double w, final double time) {
		w *= g;
		data[i][j] += w * value;
		weight[i][j] += w * g;
		count[i][j] += time;
	}
	
	public synchronized void addDirect(AstroMap map, double w) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(map.flag[i][j] == 0) {
			data[i][j] += w * map.weight[i][j] * map.data[i][j];
			weight[i][j] += w * map.weight[i][j];
			count[i][j] += map.count[i][j];
			flag[i][j] = 0;
		}
		integrationTime += map.integrationTime;
	}

	// Normalize assuming weighting was sigma weighting
	public void normalize() {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; )  {
			if(weight[i][j] <= 0.0) {
				data[i][j] = 0.0;
				weight[i][j] = 0.0;
				flag[i][j] = 1;
			} 
			else {
				data[i][j] /= weight[i][j];	
				flag[i][j] = 0;	    
			}	    
		}
	}

	@Override
	protected void sanitize(int i, int j) {
		flag[i][j] |= 1;
		data[i][j] = 0.0;
		weight[i][j] = 0.0;
	}
	
	public void applyCorrection(double filtering, double[][] significance) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(weight[i][j] > 0.0) if(significance[i][j] <= clippingS2N) {
			data[i][j] /= filtering;
			weight[i][j] *= filtering * filtering;
		}	
	}

	// It's important to completely reset clipped points, otherwise they can be used...
	// One possibility for future is to raise flag only, then call sanitize()
	public void rmsClip(double maxRelativeRMS) {
		rmsClip(maxRelativeRMS, 0.0);
	}
	
	public void rmsClip(double maxRelativeRMS, double refPercentile) {
		double[][] rms = getRMSImage().data;
		double maxRMS = maxRelativeRMS * new AstroImage(rms).select(refPercentile);
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(rms[i][j] > maxRMS) flag[i][j] = 1;
	}
	

	public void exposureClip(double minRelativeExposure) {
		exposureClip(minRelativeExposure, 1.0);
	}
	
	public void exposureClip(double minRelativeExposure, double refPercentile) {
		double minIntTime = minRelativeExposure * new AstroImage(count).select(refPercentile);
		directExposureClip(minIntTime);
	}
	
	public void directExposureClip(double minIntTime) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(count[i][j] < minIntTime) flag[i][j] = 1;
	}

	public void s2nClip(double level) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(data[i][j] / getRMS(i,j) < level) flag[i][j] = 1;
	}

	
	public boolean[][] getMask(double minS2N, int minNeighbours) {
		boolean[][] mask = new boolean[sizeX()][sizeY()];
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) if(data[i][j] / getRMS(i,j) > minS2N) mask[i][j] = true;
		
		if(minNeighbours > 0) {
			boolean[][] cleaned = new boolean[sizeX()][sizeY()];
			
			for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(mask[i][j]) {
				int neighbours = -1;
				final int fromi = Math.max(0, i-1);
				final int toi = Math.min(sizeX(), i+1);
				final int fromj = Math.max(0, j-1);
				final int toj = Math.min(sizeY(), j+1);
				for(int i1=fromi; i1<toi; i1++) for(int j1=fromj; j1<toj; j1++) if(mask[i1][j1]) neighbours++;
				if(neighbours >= minNeighbours) cleaned[i][j] = true;
			}
			return cleaned;
		}
		
		return mask;
	}

	@Override
	public void convolve(double[][] beam) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getConvolved(data, beam, beamw);
		count = getConvolved(count, beam, null);
		weight = beamw;
		this.beam = beam;
	}
	
	@Override
	public void fastConvolve(double[][] beam, int stepX, int stepY) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getFastConvolved(data, beam, beamw, stepX, stepY);
		count = getFastConvolved(count, beam, null, stepX, stepY);
		weight = beamw;
		this.beam = beam;
	}
	
	
	private void addGaussianAt(AstroMap from, int fromi, int fromj, double FWHM, double beamRadius, float[][] renorm) {
		final double xFWHM = FWHM / grid.delta.x;	
		final double sigmaX = xFWHM / Util.sigmasInFWHM;
		
		final double yFWHM = FWHM / grid.delta.y;	
		final double sigmaY = yFWHM / Util.sigmasInFWHM;
		
		final double Ax = -0.5 / (sigmaX*sigmaX);
		final double Ay = -0.5 / (sigmaY*sigmaY);
		
		final Vector2D fromIndex = new Vector2D(fromi, fromj);
		from.toOffset(fromIndex);
		toIndex(fromIndex);
		final double i0 = fromIndex.x;
		final double j0 = fromIndex.y;
		final int di = (int)Math.ceil(beamRadius * xFWHM);
		final int dj = (int)Math.ceil(beamRadius * yFWHM);
		
		final int imin = Math.max(0, (int)Math.floor(i0-di));
		final int imax = Math.min(sizeX()-1, (int)Math.ceil(i0+di));
		final int jmin = Math.max(0, (int)Math.floor(j0-dj));
		final int jmax = Math.min(sizeY()-1, (int)Math.ceil(j0+dj));
		
		for(int i=imin; i<=imax; i++) {
			double dx2 = i - i0;
			dx2 *= Ax * dx2;
			
			for(int j=jmin; j<=jmax; j++) {
				double dy2 = j - j0;
				dy2 *= Ay * dy2;
				
				final double a = Math.exp(dx2+dy2);
				
				final double aw = a * from.weight[fromi][fromj];
				data[i][j] += aw * from.data[fromi][fromj];
				weight[i][j] += aw;
				count[i][j] += a * from.count[fromi][fromj];
				renorm[i][j] += (float) a;
			}
		}
	}
	
	
	@Override
	protected AstroImage getRawRegrid(SphericalGrid toGrid, int nx, int ny, double FWHM, double beams) {
		AstroMap regrid = (AstroMap) clone();	
		regrid.copyObjectFields(this);
		regrid.setSize(nx, ny);
		float renorm[][] = new float[nx][ny];
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) 
			regrid.addGaussianAt(this, i, j, FWHM, beams, renorm);

		// renormalize the weights with the taper...
		final double wscale = regrid.getPixelArea() / getPixelArea();
		final double tscale = Math.sqrt(wscale);
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) if(regrid.weight[i][j] > 0.0) {
			regrid.data[i][j] /= regrid.weight[i][j];
			regrid.weight[i][j] *= wscale / renorm[i][j];
			regrid.count[i][j] *= tscale / renorm[i][j];
		}
		
		return regrid;
	}
	
	public AstroImage getS2NImage() {
		AstroImage image = getRMSImage();
		image.contentType = "S/N";
		image.unit = Unit.unity;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) image.data[i][j] = data[i][j] / image.data[i][j]; 
		return image;
	}
	
	public AstroImage getRMSImage() {
		double[][] rms = new double[sizeX()][sizeY()];
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) rms[i][j] = getRMS(i,j);
		return getImage(rms, "Noise", unit);
	}
	
	public AstroImage getWeightImage() { 
		return getImage(weight, "Weight", new Unit("[" + unit.name + "]**(-2)", 1.0 / (unit.value * unit.value)));
	}
	
	public AstroImage getTimeImage() { 
		return getImage(count, "Exposure",new Unit("s/pixel", Unit.s));
	}
	
	private AstroImage getImage(double[][] data, String contentType, Unit unit) {
		AstroImage image = (AstroImage) clone();
		image.data = data;
		image.contentType = contentType;
		image.unit = unit;
		return image;		
	}

	public final double getRMS(final MapIndex index) {
		return getRMS(index.i, index.j);
	}
	
	public final double getRMS(final int i, final int j) {
		return 1.0 / Math.sqrt(weight[i][j]);		
	}
	
	public final double getS2N(final MapIndex index) {
		return getS2N(index.i, index.j);
	}
	
	public final double getS2N(final int i, final int j) {
		return data[i][j] / getRMS(i,j);		
	}
	
	public int[][] getSkip(double blankingValue) {
		int[][] skip = (int[][]) copyOf(flag);
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(data[i][j] / getRMS(i,j) > blankingValue) skip[i][j] = 1;
		return skip;
	}

	@Override
	public void filterAbove(double extendedFWHM, int[][] skip) {
		super.filterAbove(extendedFWHM, skip);
		double r = getImageFWHM() / extendedFWHM;
		weightScale(1.0 / (1.0 - r*r));
	}
	
	public void filterAbove(double extendedFWHM, double blankingValue) {
		filterAbove(extendedFWHM, getSkip(blankingValue));
	}

	
	//	 8/20/07 Changed to use blanking 
	//         Using Gaussian taper.
	//         Robust re-levelling at the end.
	public void fftFilterAbove(double FWHM, double blankingValue) {
		// sigma_x sigma_w = 1
		// FWHM_x sigma_w = 2.35
		// FWHM_x * 2Pi sigma_f = 2.35
		// sigma_f = 2.35/2Pi * 1.0/FWHM_x
		// delta_f = 1.0/(Nx * delta_x);
		// sigma_nf = sigma_f / delta_x = 2.35 * Nx * delta_x / (2Pi * FWHM_x)
		
		// Try to get an honest estimate of the extended structures using FFT (while blaning bright sources).
		// Then remove it from the original image...
		
		final double[][] extended = new double[sizeX()][sizeY()];
		final int[][] skip = getSkip(blankingValue);
		
		double avew = 0.0;
		int n=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			double rms = getRMS(i,j);
			
			if(skip[i][j] > 0) extended[i][j] = 0.0;
			else {
				double w = 1.0 / (rms * rms);
				extended[i][j] = w * data[i][j];
				avew += w;
				n++;
			}
		}
		if(n > 0) avew /= n;	
		
		Complex[][] cdata = FFT.load(extended);
		FFT.uncheckedForward(cdata, true);

		final int nx = cdata.length;
		final int ny = cdata[0].length;

		double sigmax = Util.sigmasInFWHM *  nx * grid.delta.x / (2.0*Math.PI * FWHM);
		double sigmay = Util.sigmasInFWHM *  ny * grid.delta.y / (2.0*Math.PI * FWHM);
		
		final double ax = -0.5/(sigmax*sigmax);
		final double ay = -0.5/(sigmay*sigmay);

		for(int fx=nx; --fx>0; ) {
			final double axfx2 = ax*fx*fx;
			final Complex[] r1 = cdata[fx];
			final Complex[] r2 = cdata[nx-fx];
			
			for(int fy=1; fy<ny; fy++) {
				final double A = Math.exp(axfx2 + ay*fy*fy);
				final int fy1 = ny - fy;
				
				r1[fy].scale(A);
				r2[fy].scale(A);
				r1[fy1].scale(A);
				r2[fy1].scale(A);
			}
		}
		cdata[0][0].zero();
		cdata[1][0].scale(Math.exp(ax));
		cdata[0][1].scale(Math.exp(ay));
		
		FFT.uncheckedForward(cdata, false);
		FFT.unload(cdata, extended);
		
		// Scale weights for filtering in the white noise assumption/
		double r = getImageFWHM() / FWHM;
		final double weightScale = 1.0 / (1.0 - r*r);
		
		if(avew > 0.0) for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
			data[i][j] -= extended[i][j] / avew;
			weight[i][j] *= weightScale;
		}

		if(Double.isNaN(extFilterFWHM)) extFilterFWHM = FWHM;
		else extFilterFWHM = 1.0/Math.sqrt(1.0/(extFilterFWHM * extFilterFWHM) + 1.0/(FWHM*FWHM));
		
		filterCorrect(instrument.resolution, skip);
	}
	

	public double getMeanIntegrationTime() {
		double sum = 0.0, sumw = 0.0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			sum += count[i][j] * weight[i][j];
			sumw += weight[i][j];
		}
		return sum > 0.0 ? sum/sumw : 0.0;
	}
	
	@Override
	public void scale(final double scalar) {
		if(scalar == 1.0) return;		
		final double scalar2 = scalar*scalar;
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
			data[i][j] *= scalar;
			weight[i][j] /= scalar2;
		}
	}  
	
	@Override
	public double mean() {
		double sum = 0.0, sumw = 0.0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			final double w = weight[i][j];
			sum += w * data[i][j];
			sumw += w;
		}
		return sum / sumw;
	}
	
	@Override
	public double median() {
		WeightedPoint[] point = new WeightedPoint[countPoints()];

		if(point.length == 0) return 0.0;

		int k=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			point[k++] = new WeightedPoint(data[i][j], weight[i][j]);
		
		return Statistics.median(point);
	}
	
	public void rmsScale(final double scalar) {
		final double scalar2 = scalar*scalar;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) weight[i][j] /= scalar2;
	}

	public void weightScale(double scalar) {
		if(scalar == 1.0) return;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) weight[i][j] *= scalar;
	}

	public void weight(boolean robust) {
		double weightCorrection = robust ? getRobustChi2() : getChi2();
		weightScale(weightCorrection);
		weightFactor *= weightCorrection;
	}

	// Return to calculated weights...
	public void dataWeight() {
		weightScale(1.0/weightFactor);
		weightFactor = 1.0;
	}

	public double getChi2(boolean robust) {
		return robust ? getRobustChi2() : getChi2();
	}
	
	protected double getRobustChi2() {
		float[] chi2 = new float[sizeX() * sizeY()];
		if(chi2.length == 0) return 0.0;
		
		int k=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			final float s2n = (float) (data[i][j] / getRMS(i,j));
			chi2[k++] = s2n * s2n;
		}
		
		
		// median(x^2) = 0.454937 * sigma^2 
		return k > 0 ? 0.454937 / Statistics.median(chi2, 0, k) : Double.NaN;	
	}

	protected double getChi2() {
		double chi2 = 0.0;
		int n=0;
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			double s2n = data[i][j] / getRMS(i,j);
			chi2 += s2n * s2n;
			n++;
		}

		return n / chi2;	
	}
		
	public boolean containsNaN() {
		boolean hasNaN = false;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
			if(Double.isNaN(data[i][j])) hasNaN = true;
			if(Double.isNaN(weight[i][j])) hasNaN = true;
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
		
	public void MEM(double[][] model, double lambda) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			double sigma = Math.sqrt(1.0/weight[i][j]);
			double memValue = Math.hypot(sigma, data[i][j]) / Math.hypot(sigma, model[i][j]) ;
			data[i][j] -= Math.signum(data[i][j]) * lambda * sigma * Math.log(memValue);
		}
	}
	
	
	public Fits getFits() throws HeaderCardException, FitsException, IOException {
		FitsFactory.setUseHierarch(true);
		Fits fits = new Fits();	

		fits.addHDU(makeHDU());
		fits.addHDU(getTimeImage().makeHDU());
		fits.addHDU(getRMSImage().makeHDU());
		fits.addHDU(getS2NImage().makeHDU());

		editHeader(fits);
		
		if(instrument != null) if(instrument.hasOption("write.scandata")) 
			for(Scan<?,?> scan : scans) fits.addHDU(scan.getSummaryHDU(instrument.options));

		return fits;
	}

	public Fits getFits(String name) throws FitsException, IOException {
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

	public void read(String name) throws HeaderCardException, FitsException, IOException {
		// Get the coordinate system information
		BasicHDU[] HDU = getFits(name).read();
		readHeader(HDU[0].getHeader());
		readCrushData(HDU);
	}

	private void readBasicHeaderData(Header header) throws HeaderCardException {
		int sizeX = header.getIntValue("NAXIS1");
		int sizeY = header.getIntValue("NAXIS2");
		
		setSize(sizeX, sizeY);
		
		grid.getCoordinateInfo(header);
		
		if(instrument == null) {
			if(header.containsKey("INSTRUME")) {
				instrument = Instrument.forName(header.getStringValue("INSTRUME"));
				if(instrument == null) {
					instrument = new GenericInstrument(header.getStringValue("INSTRUME"));
					if(header.containsKey("TELESCOP")) ((GenericInstrument) instrument).telescope = header.getStringValue("TELESCOP");
				}
			}
			else {
				instrument = new GenericInstrument("unknown");
				if(header.containsKey("TELESCOP")) ((GenericInstrument) instrument).telescope = header.getStringValue("TELESCOP");
			}
		}
		
		instrument.parseHeader(header);
		
		creator = header.getStringValue("CREATOR");
		if(creator == null) creator = "unknown";
		creatorVersion = "unknown";
		sourceName = header.getStringValue("OBJECT");
		
		// get the beam and calculate derived quantities
		if(header.containsKey("BEAM")) 
			instrument.resolution = header.getDoubleValue("BEAM", instrument.resolution / Unit.arcsec) * Unit.arcsec;
		else if(header.containsKey("BMAJ"))
			instrument.resolution =  header.getDoubleValue("BMAJ", instrument.resolution / Unit.deg) * Unit.deg;
		else 
			instrument.resolution = 3.0 * Math.sqrt(grid.getPixelArea());

		smoothFWHM = Math.sqrt(grid.getPixelArea()) / fwhm2size;
	}


	public void readHeader(Header header) throws HeaderCardException {	
		this.header = header;
		
		weightFactor = 1.0;
		correctingFWHM = Double.NaN;
		filterBlanking = Double.NaN;
		clippingS2N = Double.NaN;
		integrationTime = Double.NaN;
		extFilterFWHM = Double.NaN;
		
		readBasicHeaderData(header);
		readCrushHeaderData(header);
		
		setUnit(header.getStringValue("BUNIT"));
	}

	private void readCrushHeaderData(Header header) throws HeaderCardException {
		weightFactor =  header.getDoubleValue("XWEIGHT", 1.0);
		integrationTime = header.getDoubleValue("INTEGRTN", Double.NaN) * Unit.s;
		correctingFWHM = header.getDoubleValue("CORRECTN", Double.NaN) * Unit.arcsec;
		filterBlanking = header.getDoubleValue("FLTRBLNK", header.getDoubleValue("MAP_XBLK", Double.NaN));
		clippingS2N = header.getDoubleValue("CLIPS2N", header.getDoubleValue("MAP_CLIP", Double.NaN));
			
		creatorVersion = header.getStringValue("CRUSHVER");
	
		// get the map resolution
		smoothFWHM = header.getDoubleValue("SMOOTH") * Unit.arcsec;
		if(smoothFWHM < Math.sqrt(grid.getPixelArea()) / fwhm2size) smoothFWHM = Math.sqrt(grid.getPixelArea()) / fwhm2size;
		extFilterFWHM = header.getDoubleValue("EXTFLTR", Double.NaN) * Unit.arcsec;		
	}

	private void readCrushData(BasicHDU[] HDU) throws FitsException {
		read(HDU[0]);
		getTimeImage().read(HDU[1]);
		
		AstroImage noise = getWeightImage();
		noise.unit = unit;
		noise.read(HDU[2]);
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) weight[i][j] = 1.0 / (weight[i][j] * weight[i][j]); 
	}


	public void write() throws HeaderCardException, FitsException, IOException { write(getFits()); }

	public void write(Fits fits) throws HeaderCardException, FitsException, IOException {
		if(fileName == null) fileName = CRUSH.workPath + File.separator + sourceName + ".fits";  
		BufferedDataOutputStream file = new BufferedDataOutputStream(new FileOutputStream(fileName));
		fits.write(file);
		System.out.println(" Written " + fileName);
	}
	
	@Override
	public void editHeader(Fits fits) throws HeaderCardException, FitsException, IOException {
		super.editHeader(fits);
		
		nom.tam.util.Cursor cursor = fits.getHDU(0).getHeader().iterator();

		// Go to the end of the header cards...
		while(cursor.hasNext()) cursor.next();
	
		// Add the command-line reduction options
		if(commandLine != null) {
			StringTokenizer args = new StringTokenizer(commandLine);
			cursor.add(new HeaderCard("ARGS", args.countTokens(), "The number of arguments passed from the command line."));
			int i=1;
			while(args.hasMoreTokens()) Util.addLongFitsKey(cursor, "ARG" + (i++), args.nextToken(), "Command-line argument.");
		}
		
		cursor.add(new HeaderCard("V2JY", instrument.janskyPerBeam(), "1 Jy/beam in instrument data units."));	
		cursor.add(new HeaderCard("INTEGRTN", integrationTime / Unit.s, "The total integration time in seconds."));
		
		if(!Double.isNaN(filterBlanking))
			cursor.add(new HeaderCard("FLTRBLNK", filterBlanking, "The S/N blanking of LSS filter."));
		if(!Double.isNaN(clippingS2N))
			cursor.add(new HeaderCard("CLIPS2N", clippingS2N, "The S/N clipping level used in reduction."));
	
		// The number of scans contributing to this image
		cursor.add(new HeaderCard("SCANS", scans.size(), "The number of scans in this composite image."));			
		
	}
	
	
	public void printHeader() {
		header.dumpHeader(System.out);
	}      

	
	public void despike(double significance) {
		double[][] neighbours = {{ 0, 1, 0 }, { 1, 0, 1 }, { 0, 1, 0 }};
		AstroMap diff = (AstroMap) copy();
		diff.convolve(neighbours);
		
		WeightedPoint point = new WeightedPoint();
		WeightedPoint surrounding = new WeightedPoint();
		
		for(int i=0; i<sizeX(); i++) for(int j=0; j<sizeY(); j++) if(flag[i][j] == 0) {
			point.value = data[i][j];
			point.weight = weight[i][j];
			surrounding.value = diff.data[i][j];
			surrounding.weight = diff.weight[i][j];
			point.subtract(surrounding);
			if(point.significance() > significance) flag[i][j] = 1;			
		}	
	}
	
	
	public DataPoint getFlux(Region region) {
		final Bounds bounds = region.getBounds(this);
		double flux = 0.0, var = 0.0;

		double A = 1.0 / getPointsPerSmoothingBeam();

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j))  {
				flux += data[i][j];
				var += 1.0 / weight[i][j];
			}
		
		return new DataPoint(A * flux, A * Math.sqrt(var));
	}
	

	@Override
	public double getLevel(Region region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0, sumw = 0.0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j)) {
				sum += weight[i][j] * data[i][j];
				sumw += weight[i][j];
			}
		return sum / sumw;			
	}

	public double getMeanNoise(Region region) {
		final Bounds bounds = region.getBounds(this);
		double var = 0.0;
		int n = 0;

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0)  if(region.isInside(grid, i, j)) {
				double rms = getRMS(i,j);
				var += rms * rms;
				n++;
			}
		var /= (n-1);
		
		return Math.sqrt(var);
	}
	
	public double getMeanExposure(Region region) {	
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		int n = 0;

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j)) {
				sum += count[i][j];
				n++;
			}
		
		return n > 0 ? sum / n : 0.0;
	}
	
	
	
	
	@Override
	public String toString() {
		String info = fileName == null ? "\n" : " Image File: " + fileName + ". ->" + "\n\n" + 
			"  [" + sourceName + "]" + "\n" +
			super.toString() + 
			"  Instrument Beam FWHM: " + Util.f2.format(instrument.resolution / Unit.arcsec) + " arcsec." + "\n" +
			"  Applied Smoothing: " + Util.f2.format(smoothFWHM / Unit.arcsec) + " arcsec." + " (includes pixelization)\n" +
			"  Image Resolution (FWHM): " + Util.f2.format(getImageFWHM() / Unit.arcsec) + " arcsec. (includes smoothing)" + "\n" +
			"  Noise Estimate from: " + (weightFactor == 1.0 ? "data" : "image (" + Util.f2.format(1.0 / weightFactor) + "x data)") + "\n"; 

		return info;
	}
	
}





