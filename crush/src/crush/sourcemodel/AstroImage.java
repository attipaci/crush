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
// Copyright (c) 2009 Attila Kovacs 

package crush.sourcemodel;


import java.util.*;

import nom.tam.fits.*;
import nom.tam.util.*;

import util.*;
import util.astro.EclipticCoordinates;
import util.astro.EquatorialCoordinates;
import util.astro.GalacticCoordinates;
import util.astro.HorizontalCoordinates;
import util.astro.SuperGalacticCoordinates;
import util.data.ArrayUtil;
import util.data.WeightedPoint;

import java.io.*;

import crush.*;


public class AstroImage implements Cloneable {
	public SphericalGrid grid = new SphericalGrid();
	public double[][] data;
	public int[][] flag;
	protected double[][] beam;
	
	public Unit unit = Unit.unity;
	public Instrument<?> instrument;
	
	public double smoothFWHM;  
	public double extFilterFWHM = Double.NaN;
	public double correctingFWHM = Double.NaN;	
		
	public Header header;
	public String fileName, sourceName, contentType = "";
	public String creator = "CRUSH", creatorVersion = CRUSH.version; 
	
	public boolean verbose = false;
	
	// 2 pi sigma^2 = a^2
	// a = sqrt(2 pi) sigma
	//   = sqrt(2 pi) fwhm / 2.35
	public static double fwhm2size = Math.sqrt(2.0 * Math.PI) / Util.sigmasInFWHM;
	
	public AstroImage() {
		Locale.setDefault(Locale.US);
	}
	
	public AstroImage(int sizeX, int sizeY) {
		this();
		setSize(sizeX, sizeY);
	}
	
	public AstroImage(double[][] data) {
		this();
		this.data = data;
		noFlag();
	}
	
	public AstroImage(double[][] data, int[][] flag) {
		this();
		this.data = data;
		this.flag = flag;
	}
	
	@Override
	public Object clone() {
		try {return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public AstroImage copy() {
		AstroImage copy = (AstroImage) clone();
		copy.copy(this);
		return copy;
	}
	
	public final void copy(AstroImage image) {
		copyObjectFields(image);
		copyImage(image);		
	}
	
	public void copyObjectFields(AstroImage image) {
		grid = image.grid == null ? null : image.grid.copy();		
	}
	
	public void copyImage(AstroImage image) {
		// Make a copy of the fundamental data
		data = (double[][]) copyOf(image.data);
		beam = (double[][]) copyOf(image.beam);
		flag = (int[][]) copyOf(image.flag);		
	}
	
	public final int sizeX() { return data.length; }
	
	public final int sizeY() { return data[0].length; }
	
	public void setSize(int x, int y) {
		beam = new double[][] {{1.0}};
		data = new double[x][y];
		flag = new int[x][y];
		for(int i=0; i<x; i++) Arrays.fill(flag[i], 1); 
	}
	
	public void noFlag() {
		flag = new int[sizeX()][sizeY()];		
	}
	
	public SphericalProjection getProjection() {
		return grid.getProjection();
	}
	
	public SphericalCoordinates getReference() {
		return grid.getReference();
	}
	
	public boolean isHorizontal() {
		return getReference() instanceof HorizontalCoordinates;
	}
	
	public boolean isEquatorial() {
		return getReference() instanceof EquatorialCoordinates;
	}
	
	public boolean isEcliptic() {
		return getReference() instanceof EclipticCoordinates;
	}
	
	public boolean isGalactic() {
		return getReference() instanceof GalacticCoordinates;
	}
	
	public boolean isSuperGalactic() {
		return getReference() instanceof SuperGalacticCoordinates;
	}
	
	public double getPixelArea() {
		return grid.getPixelArea();
	}
	
	public double getImageFWHM() {
		return Math.hypot(instrument.resolution, smoothFWHM);
	}
	
	public double getInstrumentBeamArea() {
		double size = instrument.resolution * fwhm2size;
		return size*size;
	}
	
	public double getImageBeamArea() {
		final double A = fwhm2size * getImageFWHM();
		return A*A;		
	}
	
	public double getPointsPerSmoothingBeam() {
		return Math.max(1.0, fwhm2size * smoothFWHM / grid.delta.x) * Math.max(1.0, fwhm2size * smoothFWHM / grid.delta.y);
	}
	
	public void setResolution(double value) { 
		grid.delta.x = grid.delta.y = value; 
		smoothFWHM = Math.max(smoothFWHM, value / fwhm2size);
	}
	
	public Vector2D getResolution() {
		return grid.delta;
	}
	
	public double getExtFilterCorrectionFactor(double FWHM) {
		double effectiveFilterFWHM2 = FWHM*FWHM + extFilterFWHM*extFilterFWHM;
		double effectiveFWHM2 = FWHM*FWHM + smoothFWHM*smoothFWHM;
		if(Double.isNaN(extFilterFWHM)) return 1.0;
		return 1.0 / (1.0 - effectiveFWHM2/effectiveFilterFWHM2);
	}
	
	public void copyImage(double[][] image) { 
		for(int i=sizeX(); --i>=0; ) System.arraycopy(image[i], 0, data[i], 0, sizeY());
	}

	public void addImage(double[][] image, double scale) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) data[i][j] += scale * image[i][j];
	}

	public void addImage(double[][] image) { addImage(image, 1.0); }
	
	public void clear() {
		for(int i=sizeX(); --i >= 0; ) {
			Arrays.fill(data[i], 0.0);
			Arrays.fill(flag[i], 1);
		}
	}

	public void reset() {
		clear();
		smoothFWHM = Math.sqrt(grid.getPixelArea()) / fwhm2size;
		extFilterFWHM = Double.NaN;
		correctingFWHM = Double.NaN;
		beam = new double[][] {{ 1.0 }};
	}
	
	
	public final double valueAt(final MapIndex index) {
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
	
	public void setProjection(SphericalProjection projection) {
		grid.projection = projection;
	}
	
	public double getMin() { 
		double min=Double.POSITIVE_INFINITY;
		for(int i=sizeX(); --i >=0; ) for(int j=sizeY(); --j >= 0; ) 
			if(flag[i][j]==0) if(data[i][j] < min) min = data[i][j];
		return min;
	}

	
	public double getMax() {
		double max=Double.NEGATIVE_INFINITY;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >=0; ) if(flag[i][j]==0) 
				if(data[i][j] > max) max = data[i][j];
		return max;
	}
	
	public Range getRange() {
		final Range range = new Range();
		range.empty();
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j]==0) 
			range.include(data[i][j]);
		return range;
	}
	
	
	public MapIndex indexOfMax() {
		MapIndex index = new MapIndex();

		double peak = 0.0;

		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(data[i][j] > peak) {
				peak = data[i][j];
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
		return Math.sqrt(ArrayUtil.median(chi2) / 0.454937);	
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
	
	public void crop(double dXmin, double dYmin, double dXmax, double dYmax) {		
		if(dXmin > dXmax) { double temp = dXmin; dXmin = dXmax; dXmax=temp; }
		if(dYmin > dYmax) { double temp = dYmin; dYmin = dYmax; dYmax=temp; }
		
		MapIndex c1 = getIndex(new Vector2D(dXmin, dYmin));
		MapIndex c2 = getIndex(new Vector2D(dXmax, dYmax));
	
		crop(c1.i, c1.j, c2.i, c2.j);
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

		grid.refIndex.x -= imin;
		grid.refIndex.y -= jmin;
	}
	
	public int[] getHorizontalIndexRange() {
		int min = sizeX()-1, max = 0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			if(i < min) min = i;
			if(i > max) max = i;
			break;
		}
		return max > min ? new int[] { min, max } : null;
	}
	
	public int[] getVerticalIndexRange() {
		int min = sizeY()-1, max = 0;
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
	
	
	public double countBeams() { return getArea() / getImageBeamArea(); }

	public double getArea() { return countPoints() * getPixelArea(); }

	
	// Convolves image to the specified beam resolution
	// by a properly chosen convolving beam...
	public void convolveTo(double FWHM) {
		if(smoothFWHM >= FWHM) return;
		convolve(Math.sqrt(FWHM * FWHM - smoothFWHM * smoothFWHM));
	}	

	public double[][] getConvolvedTo(double[][] image, double FWHM) {
		if(smoothFWHM >= FWHM) return image; 
		return getConvolved(image, Math.sqrt((FWHM * FWHM - smoothFWHM * smoothFWHM)));
	}

	public void convolve(double FWHM) {
		int stepX = (int)Math.ceil(FWHM/(5.0 * grid.delta.y));
		int stepY = (int)Math.ceil(FWHM/(5.0 * grid.delta.y));
		
		fastConvolve(getGaussian(FWHM, 2.0), stepX, stepY);
		smoothFWHM = Math.hypot(smoothFWHM, FWHM);
		
		// The correcting FWHM is underlying FWHM...
		//if(!Double.isNaN(correctingFWHM)) correctingFWHM = Math.hypot(correctingFWHM, FWHM);
		setUnit(unit.name);
	}

	public double[][] getConvolved(double[][] image, double FWHM) {
		int stepX = (int)Math.ceil(FWHM/(5.0 * grid.delta.y));
		int stepY = (int)Math.ceil(FWHM/(5.0 * grid.delta.y));
		return getFastConvolved(image, getGaussian(FWHM, 2.0), null, stepX, stepY);
	}   

	public void convolve(double[][] beam) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getConvolved(data, beam, beamw);
		this.beam = beam;
	}
	
	public void fastConvolve(double[][] beam, int stepX, int stepY) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getFastConvolved(data, beam, beamw, stepX, stepY);
		this.beam = beam;
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
	
	
	
	public void filterAbove(double FWHM) { filterAbove(FWHM, flag); }

	public void filterAbove(double FWHM, int[][] skip) {
		AstroImage extended = copy();
		extended.flag = skip;
		extended.convolveTo(FWHM);
	
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) data[i][j] -= extended.data[i][j];		

		if(Double.isNaN(extFilterFWHM)) extFilterFWHM = FWHM;
		else extFilterFWHM = 1.0/Math.sqrt(1.0/(extFilterFWHM * extFilterFWHM) + 1.0/(FWHM*FWHM));

		filterCorrect(instrument.resolution, skip);
	}
	
	public void filterCorrect(double FWHM, int[][] skip) {
		if(!Double.isNaN(correctingFWHM)) return;
		
		final double filterC = getExtFilterCorrectionFactor(FWHM);
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(skip[i][j] != 0) scale(i, j, filterC);
		
		correctingFWHM = FWHM;
	}
	
	public void scale(int i, int j, double factor) {
		data[i][j] *= factor;
	}

	public double[][] getBeam(double FWHM) {
		return getGaussian(FWHM, 3.0);
	}
	
	public double[][] getGaussian(double FWHM, double nBeams) {
		int sizeX = 2 * (int)Math.ceil(nBeams * FWHM/grid.delta.x) + 1;
		int sizeY = 2 * (int)Math.ceil(nBeams * FWHM/grid.delta.x) + 1;
		
		final double[][] beam = new double[sizeX][sizeY];
		final double sigma = FWHM / Util.sigmasInFWHM;
		final double A = -0.5 * getPixelArea() / (sigma * sigma);
		final double centerX = (sizeX-1) / 2.0;
		final double centerY = (sizeY-1) / 2.0;
		
		for(int i=sizeX; --i >= 0; ) for(int j=sizeY; --j >= 0; ) {
			double dx = i - centerX;
			double dy = j - centerY;

			beam[i][j] = Math.exp(A*(dx*dx+dy*dy));
		}
		return beam;
	}	
	
	private void addGaussianAt(AstroImage from, int fromi, int fromj, double FWHM, double beamRadius, float[][] renorm) {	
		double xFWHM = FWHM / grid.delta.x;
		double yFWHM = FWHM / grid.delta.y;
		
		final double sigmaX = xFWHM / Util.sigmasInFWHM;
		final double Ax = -0.5 / (sigmaX*sigmaX);
		
		final double sigmaY = yFWHM / Util.sigmasInFWHM;
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
				
				final double a = Math.exp(dx2 + dy2);
				
				data[i][j] += a * from.data[fromi][fromj];
				renorm[i][j] += (float) a;
			}
		}
	}
	
	protected AstroImage getRawRegrid(SphericalGrid toGrid, int nx, int ny, double FWHM, double beams) {	
		AstroImage regrid = (AstroImage) clone();
		regrid.copyObjectFields(this);
		regrid.setSize(nx, ny);
		float renorm[][] = new float[nx][ny];
		
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) if(flag[i][j]==0) 
			regrid.addGaussianAt(this, i, j, FWHM, beams, renorm);
		
		// renormalize with the taper...
		for(int i=0; i<nx; i++) for(int j=0; j<ny; j++) if(renorm[i][j] > 0.0) regrid.data[i][j] /= renorm[i][j];
		
		return regrid;
	}

	public void regridTo(final AstroImage map) throws IllegalStateException {
		AstroImage regrid = getRegrid(map.grid);
		
		Vector2D o1 = new Vector2D();
		Vector2D o2 = new Vector2D(map.sizeX() - 1.0, map.sizeY() - 1.0);
		toOffset(o1);
		toOffset(o2);
		
		regrid.crop(o1.x, o1.y, o2.x, o2.y); 
		
		map.copyImage(regrid);
	}
	

		
	public AstroImage getRegrid(final SphericalGrid toGrid) throws IllegalStateException {		
		// Check if it is an identical grid...
		// Add directly if it is...
		if(toGrid.equals(grid, 1e-10)) {
			if(verbose) System.err.println(" Matching grids.");
			return this;
		}
			
		// replace pixels with Gaussians whose FWHM is the diagonal of the pixel (i.e. enclosing)...
		double FWHM = Math.sqrt(2.0 * getPixelArea());
		final int nx = (int) Math.ceil(sizeX() * grid.delta.x / toGrid.delta.x);
		final int ny = (int) Math.ceil(sizeY() * grid.delta.y / toGrid.delta.y);
		if(verbose) System.err.println(" Regrid size: " + nx + "x" + ny);
		
		AstroImage regrid = getRawRegrid(toGrid, nx, ny, FWHM, 1.0);
		
		regrid.smoothFWHM = Math.hypot(smoothFWHM, FWHM);
		
		for(int i=regrid.sizeX(); --i >= 0; ) Arrays.fill(regrid.flag[i], 1);
		
		final Vector2D c1 = new Vector2D();
		final Vector2D c2 = new Vector2D();
		final MapIndex idx1 = new MapIndex();
		final MapIndex idx2 = new MapIndex();
		
		final int rxm1 = regrid.sizeX() - 1;
		final int rym1 = regrid.sizeY() - 1;
		
		// recalculate the flags...	
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			c1.x = i - 0.5;
			c2.x = i + 0.5;
			c1.y = j - 0.5;
			c1.y = j + 0.5;

			toOffset(c1);
			regrid.getIndex(c1, idx1);
			toOffset(c2);
			regrid.getIndex(c2, idx2);

			final int mini = Math.max(0, idx1.i);
			final int maxi = Math.min(idx2.i, rxm1);
			final int minj = Math.max(0, idx2.j);
			final int maxj = Math.min(idx2.j, rym1);

			for(int i1=mini; i1<=maxi; i1++) for(int j1=minj; j1<=maxj; j1++) regrid.flag[i1][j1] = 0;
		}

		regrid.setUnit(unit.name);
		
		regrid.sanitize();
		return regrid;
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
	
	
	public void read(BasicHDU HDU) throws FitsException {		
		Object image = HDU.getData().getData();
		for(int i=sizeX(); --i >= 0; ) Arrays.fill(flag[i], 0);
				
		try {
			final float[][] fdata = (float[][]) image;
			for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
				if(Float.isNaN(fdata[j][i])) flag[i][j] |= 1;
				else data[i][j] = fdata[j][i] * unit.value;	    
			}
		}
		catch(ClassCastException e) {
			final double[][] ddata = (double[][]) image;
			for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
				if(Double.isNaN(ddata[j][i])) flag[i][j] |= 1;
				else data[i][j] = ddata[j][i] * unit.value;	    
			}				
		}
	}
	
	public ImageHDU makeHDU() throws HeaderCardException, FitsException {
		float[][] fitsImage = new float[sizeY()][sizeX()];
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
			if(flag[i][j] == 0.0) fitsImage[j][i] = (float) (data[i][j] / unit.value);
			else fitsImage[j][i] = Float.NaN;
		}
		ImageHDU hdu = (ImageHDU)Fits.makeHDU(fitsImage);

		hdu.addValue("EXTNAME", contentType, "The type of data contained in this HDU");
		grid.addCoordinateInfo(hdu);

		Range range = getRange();
		
		hdu.addValue("DATAMIN", range.min / unit.value, "");
		hdu.addValue("DATAMAX", range.max / unit.value, "");

		hdu.addValue("BZERO", 0.0, "Zeroing level of the image data");
		hdu.addValue("BSCALE", 1.0, "Scaling of the image data");
		hdu.addValue("BUNIT", unit.name, "The image data unit.");

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
	
	
	
	@Override
	public String toString() {		
		String info =
			"  Map Size: " + sizeX() + " x " + sizeY() + " pixels. (" 
			+ Util.f1.format(sizeX() * grid.delta.x / Unit.arcmin) + " x " + Util.f1.format(sizeY() * grid.delta.y / Unit.arcmin) + " arcmin)." + "\n"
			+ grid.toString();
		return info;
	}
	
	public void info() {
		System.err.println("\n" + toString() + "\n");
	}
	
	
	public void write(String name, Unit unit) throws HeaderCardException, FitsException, IOException {
		Fits fits = new Fits();	

		fits.addHDU(makeHDU());
		editHeader(fits);

		BufferedDataOutputStream file = new BufferedDataOutputStream(new FileOutputStream(name));

		fits.write(file);	
		System.out.println(" Written Image to " + name);
	}
	
	public void editHeader(Fits fits) throws HeaderCardException, FitsException, IOException {
		
		nom.tam.util.Cursor cursor = fits.getHDU(0).getHeader().iterator();

		// Go to the end of the header cards...
		while(cursor.hasNext()) cursor.next();

		double imageFWHM = getImageFWHM();
		
		cursor.add(new HeaderCard("OBJECT", sourceName, "Source name as it appear in the raw data."));	
		cursor.add(new HeaderCard("DATE", FitsDate.getFitsDateString(), "Time-stamp of creation."));
		cursor.add(new HeaderCard("CREATOR", creator, "The software that created the image."));
		cursor.add(new HeaderCard("CRUSHVER", creatorVersion, "CRUSH version information."));
		//cursor.add(new HeaderCard("ORIGIN", "Caltech", "California Institute of Technology"));
		
		if(instrument != null) instrument.editImageHeader(cursor);
		
		cursor.add(new HeaderCard("BMAJ", imageFWHM, "The beam major axis (radians)"));
		cursor.add(new HeaderCard("BMIN", imageFWHM, "The beam minor axis (radians)."));
		cursor.add(new HeaderCard("BPA", 0.0, "The beam position angle (radians)."));
		cursor.add(new HeaderCard("SMOOTH", smoothFWHM / Unit.arcsec, "The FWHM (arcsec) of the smoothing applied."));	
		cursor.add(new HeaderCard("SMTHRMS", true, "Is the Noise (RMS) image smoothed?"));
		cursor.add(new HeaderCard("RESOLUTN", imageFWHM / Unit.arcsec, "The effective image FWHM (arcsec)."));	
		
		if(!Double.isNaN(extFilterFWHM)) 
			cursor.add(new HeaderCard("EXTFLTR", extFilterFWHM / Unit.arcsec, "Large-scale structure filtering FWHM."));
		
		if(!Double.isNaN(correctingFWHM))
			cursor.add(new HeaderCard("CORRECTN", correctingFWHM / Unit.arcsec, "The FWHM (arcsec) for which fluxes are corrected."));
		
		if(instrument != null) if(instrument.options != null) instrument.options.editHeader(cursor);
	}

    public boolean containsIndex(double i, double j) {
    	return i >= 0 && i < sizeX() && i >= 0 && j < sizeY();
    }
    
	protected double getUnit(String name) {
		if(name.contains("/")) {
			int index = name.lastIndexOf('/');
			String baseUnit = name.substring(0, index);
			String area = name.substring(index + 1).toLowerCase();
			return getUnit(baseUnit) / getAreaUnit(area);
		}
		else {
			Unit dataUnit = instrument.getDataUnit();
			if(name.equalsIgnoreCase(dataUnit.name)) return dataUnit.value * getInstrumentBeamArea();
			else if(name.equalsIgnoreCase("Jy") || name.equalsIgnoreCase("jansky")) return instrument.janskyPerBeam() * getInstrumentBeamArea();
			else {
				Unit u = Unit.get(name);
				if(u != null) return u.value;
				// Else assume there is a standard multiplier in front, such as k, M, m, u...
				else return  Unit.getMultiplier(name.charAt(0)) * getUnit(name.substring(1));
			}
		}
	}
	
	public double getAreaUnit(String area) {	
		if(area.equals("beam") || area.equals("bm"))
			return getImageBeamArea();
		if(area.equals("arcsec**2") || area.equals("arcsec2") || area.equals("arcsec^2") || area.equals("sqarcsec"))
			return Unit.arcsec2;
		else if(area.equals("arcmin**2") || area.equals("arcmin2") || area.equals("arcmin^2") || area.equals("sqarcmin"))
			return Unit.arcmin2;
		else if(area.equals("deg**2") || area.equals("deg2") || area.equals("deg^2") || area.equals("sqdeg"))
			return Unit.deg2;
		else if(area.equals("rad**2") || area.equals("rad2") || area.equals("rad^2") || area.equals("sr"))
			return Unit.sr;
		else if(area.equals("mas") || area.equals("mas2") || area.equals("mas^2") || area.equals("sqmas"))
			return Unit.mas * Unit.mas;
		else if(area.equals("pixel") || area.equals("pix"))
			return getPixelArea();
		else return Double.NaN;
	}
	
	public void setUnit(String name) {
		unit = new Unit(name, getUnit(name));
	}
    
	public void toIndex(Vector2D offset) { grid.toIndex(offset); }
	
	public void toOffset(Vector2D index) { grid.toOffset(index); }

	public void getIndex(Vector2D offset, MapIndex index) {
		double x = offset.x;
		double y = offset.y;
		toIndex(offset);
		index.i = (int) Math.round(offset.x);
		index.j = (int) Math.round(offset.y);
		offset.x = x;
		offset.y = y;		
	}
	
	public void getOffset(MapIndex index, Vector2D offset) {
		offset.x = index.i;
		offset.y = index.j;
		toOffset(offset);		
	}

	public MapIndex getIndex(Vector2D offset) {
		MapIndex index = new MapIndex();
		getIndex(offset, index);
		return index;
	}
	
	public Vector2D getOffset(MapIndex index) {
		Vector2D offset = new Vector2D();
		getOffset(index, offset);
		return offset;
	}
    
    public void flag(Region region) { flag(region, 1); }
	
	public void flag(Region region, int pattern) {
		final Bounds bounds = region.getBounds(this);
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(region.isInside(grid, i, j)) flag[i][j] |= pattern;
	}
	
	public void unflag(Region region, int pattern) {
		final Bounds bounds = region.getBounds(this);
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(region.isInside(grid, i, j)) flag[i][j] &= ~pattern;
	}
    	
	public double getIntegral(Region region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j)) sum += data[i][j];	
		return sum;			
	}
	
	public double getLevel(Region region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		int n = 0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j)) {
				sum += data[i][j];
				n++;
			}
		return sum / n;			
	}
	
	public double getRMS(Region region) {
		final Bounds bounds = region.getBounds(this);
		double var = 0.0;
		int n = 0;
		double level = getLevel(region);

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j))  {
				double value = data[i][j] - level;
				var += value * value;
				n++;
			}
		var /= (n-1);
		
		return Math.sqrt(var);
	}

	// In 1-D at least 3 points per beam are needed to separate a positioned point
	// source from an extended source...
	// Similarly 9 points per beam are necessary for 2-D...
	public double countIndependentPoints(double area) {
		double smoothArea = fwhm2size * fwhm2size * smoothFWHM * smoothFWHM;
		double filterArea = fwhm2size * fwhm2size * extFilterFWHM * extFilterFWHM;
		double beamArea = this.getImageBeamArea();
		
		// Account for the filtering correction.
		double eta = 1.0;
		if(Double.isNaN(extFilterFWHM) && extFilterFWHM > 0.0) eta -= smoothArea / filterArea;
		double iPointsPerBeam = eta * Math.min(9.0, smoothArea / getPixelArea());
		
		return Math.ceil((1.0 + area/beamArea) * iPointsPerBeam);
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

