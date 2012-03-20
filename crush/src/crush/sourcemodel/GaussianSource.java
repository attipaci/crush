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

import java.text.*;
import java.util.*;

import crush.Instrument;

import util.*;
import util.astro.CoordinateEpoch;
import util.astro.Precessing;
import util.data.*;


public class GaussianSource<CoordinateType extends CoordinatePair> extends CircularRegion<CoordinateType> {
	private DataPoint peak;
	private boolean isCorrected = false;
	
	public GaussianSource() { }

	public GaussianSource(String line, GridImage<CoordinateType> forImage) throws ParseException {
		super(line, forImage);
	}
	
	public GaussianSource(GridImage<CoordinateType> map, Vector2D offset, double r) {
		super(map, offset, r);
	}
	
	public GaussianSource(CoordinateType coords, double r) {
		super(coords, r);
	}
	
	public Bounds getBounds(GridImage<CoordinateType> image, double r) {
		DataPoint origRadius = getRadius();
		setRadius(new DataPoint(r, 0.0));
		Bounds bounds = getBounds(image);
		setRadius(origRadius);
		return bounds;
	}
	
	public DataPoint getPeak() { return peak; }
	
	public void setPeak(DataPoint value) { peak = value; }
	
	public void setPeak(double value) { 
		if(peak == null) peak = new DataPoint();
		else peak.weight = 0.0;
		peak.value = value;
	}
	
	public boolean isCorrected() { return isCorrected; }
	
	public void setCorrected(boolean value) { isCorrected = value; }
	
	public DataPoint getFWHM() { return getRadius(); }
	
	public void setFWHM(double value) { setRadius(value); }
	
	public void setFWHM(DataPoint value) { setRadius(value); }
	
	public void setPeakPixel(GridImage<CoordinateType> map) {
		Index2D index = map instanceof GridMap ? ((GridMap<?>) map).getS2NImage().indexOfMax() : map.indexOfMax();
		@SuppressWarnings("unchecked")
		CoordinateType coords = (CoordinateType) map.getReference().clone();
		map.getGrid().getCoords(new Vector2D(index.i(), index.j()), coords);
		setID(map instanceof GridSource ? ((GridSource<?>) map).name : "[1]");
		setCenter(coords);
		setRadius(new DataPoint(map.getImageFWHM(), Math.sqrt(map.getPixelArea())));	
	}
	
	public void setPeak(GridImage<CoordinateType> map) {
		setPeakPixel(map);
		finetunePeak(map);
	}
	
	public void setPeakCentroid(GridImage<CoordinateType> map) {
		setPeak(map);
		centroid(map);
	}
	
	@Override
	public DataPoint finetunePeak(GridImage<CoordinateType> image) {
		
		Data2D.InterpolatorData ipolData = new Data2D.InterpolatorData();
		peak = super.finetunePeak(image);
		Vector2D centerIndex = getIndex(image.getGrid());
		if(peak == null) {
			peak.value = image.valueAtIndex(centerIndex, ipolData);
			if(image instanceof GridMap)
				peak.setRMS(((GridMap<?>) image).getRMSImage().valueAtIndex(centerIndex, ipolData));
			else peak.weight = 0.0;
		}
		return peak;
	}
	
	public void scale(double factor) {
		peak.scale(factor);		
	}
	
	public void getChi2(GridImage<CoordinateType> map, WeightedPoint chi2, double level) {
		chi2.noData();
		Bounds bounds = getBounds(map);
		Vector2D centerIndex = getIndex(map.getGrid());
		final Vector2D resolution = map.getResolution();
		final double sigmaX = getRadius().value / Util.sigmasInFWHM / resolution.getX();
		final double sigmaY = getRadius().value / Util.sigmasInFWHM / resolution.getY();
		final double Ax = -0.5 / (sigmaX*sigmaX);
		final double Ay = -0.5 / (sigmaY*sigmaY);

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) if(map.isUnflagged(i, j)) {
			final double di = i-centerIndex.getX();
			final double dj = j-centerIndex.getY();
			final double dev = (map.getValue(i, j) - level - peak.value * Math.exp(Ax*di*di + Ay*dj*dj)) / map.getRMS(i, j);
			chi2.value += dev * dev;
			chi2.weight += 1.0;
		}
	}
	
	public void addGaussian(GridImage<CoordinateType> image, double FWHM, double scaling) {
		Bounds bounds = getBounds(image, 3.0 * FWHM);
			
		Vector2D centerIndex = getIndex(image.getGrid());
		final Vector2D resolution = image.getResolution();
		final double sigmaX = FWHM / Util.sigmasInFWHM / resolution.getX();
		final double sigmaY = FWHM / Util.sigmasInFWHM / resolution.getY();
		final double Ax = -0.5 / (sigmaX*sigmaX);
		final double Ay = -0.5 / (sigmaY*sigmaY);
	
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) {
			final double di = i-centerIndex.getX();
			final double dj = j-centerIndex.getY();
			image.increment(i, j, scaling * peak.value * Math.exp(Ax*di*di + Ay*dj*dj));
		}
		
	}
	
	public void add(GridImage<CoordinateType> image) { add(image, null); }
	
	public void add(GridImage<CoordinateType> image, Collection<Region<CoordinateType>> others) { add(image, getRadius().value, 1.0, others); }
	
	public void addPoint(GridImage<CoordinateType> image) { addPoint(image, null); }
	
	public void addPoint(GridImage<CoordinateType> image, Collection<Region<CoordinateType>> others) { add(image, image.getImageFWHM(), 1.0, others); }	
	
	public void subtract(GridImage<CoordinateType> image) { subtract(image, null); }
	
	public void subtract(GridImage<CoordinateType> image, Collection<Region<CoordinateType>> others) { add(image, getRadius().value, -1.0, others); }
	
	public void subtractPoint(GridImage<CoordinateType> image) { subtractPoint(image, null); }
	
	public void subtractPoint(GridImage<CoordinateType> image, Collection<Region<CoordinateType>> others) { add(image, image.getImageFWHM(), -1.0, others); }
	
	
	public void add(GridImage<CoordinateType> image, final double FWHM, final double scaling) {
		add(image, FWHM, scaling, null);
	}
	
	public void add(GridImage<CoordinateType> image, final double FWHM, final double scaling, final Collection<Region<CoordinateType>> others) {
		// Remove the Gaussian main beam...
		addGaussian(image, Math.hypot(FWHM, image.smoothFWHM), scaling);
		
		// If an LSS filter was used, also correct for the Gaussian bowl around the source...
		if(Double.isNaN(image.extFilterFWHM)) return;
		
		final double filterFWHM = Math.hypot(FWHM, image.extFilterFWHM);			

		// Correct for filtering.
		double filterFraction = 1.0 - 1.0 / image.getFilterCorrectionFactor(FWHM);
		
		// Consider that only the tip of the source might escape the filter...	
		if(image instanceof GridMap) {
			GridMap<?> map = (GridMap<?>) image;
			filterFraction *= Double.isNaN(map.filterBlanking) ? 1.0 : Math.min(1.0, map.filterBlanking / peak.significance());	
		}

		// Add the filter bowl to the image
		addGaussian(image, filterFWHM, -scaling * filterFraction);

		// Now adjust prior detections for the bias caused by this source's filtering...
		final Vector2D resolution = image.getResolution();
		final double sigmai = filterFWHM / Util.sigmasInFWHM / resolution.getX();
		final double sigmaj = filterFWHM / Util.sigmasInFWHM / resolution.getY();
		final double Ai = -0.5 / (sigmai*sigmai);
		final double Aj = -0.5 / (sigmaj*sigmaj);	

		double filterPeak = -filterFraction * scaling * peak.value;
		
		Vector2D centerIndex = getIndex(image.getGrid());
		// Adjust prior detections.for the filtering around this one.
		if(others != null) for(Region<CoordinateType> region : others) if(region instanceof GaussianSource) if(region != this) {
			GaussianSource<CoordinateType> source = (GaussianSource<CoordinateType>) region;
			Vector2D sourceIndex = source.getIndex(image.getGrid());
			final double di = sourceIndex.getX() - centerIndex.getX();
			final double dj = sourceIndex.getY() - centerIndex.getY();
			
			if(!Double.isNaN(image.extFilterFWHM))
				source.peak.value -= filterPeak * Math.exp(Ai*di*di + Aj*dj*dj);
		}
		
	}
	
	public double getCorrectionFactor(GridMap<CoordinateType> map, double FWHM) {	
		double correction = 1.0;	
		
		// Correct for filtering.
		// Consider that only the tip of the source might escape the filter...
		if(!Double.isNaN(map.extFilterFWHM)) {
			double filterFraction = Double.isNaN(map.filterBlanking) ? 1.0 : Math.min(1.0, map.filterBlanking / peak.significance());
			double filtering = 1.0 - 1.0 / map.getFilterCorrectionFactor(FWHM);;
			correction *= 1.0 / (1.0 - filtering * filterFraction);
		}
		
		return correction;
	}
	
	
	public void correct(GridMap<CoordinateType> map, double FWHM) {	
		if(isCorrected) throw new IllegalStateException("Source is already corrected.");
		double correction = getCorrectionFactor(map, FWHM);
		peak.scale(correction);
		isCorrected = true;
	}
	

	public void uncorrect(GridMap<CoordinateType> map, double FWHM) {
		if(!isCorrected) throw new IllegalStateException("Source is already uncorrected.");
		double correction = getCorrectionFactor(map, FWHM);
		peak.scale(1.0 / correction);
		isCorrected = false;
	}
	
	
	// Formula from Kovacs et al. (2006)
	public void setSearchRadius(GridSource<CoordinateType> image, double pointingRMS) {
		double beamSigma = image.instrument.resolution / Util.sigmasInFWHM;
		getRadius().value = Math.sqrt(4.0 * pointingRMS * pointingRMS - 2.0 * beamSigma * beamSigma * Math.log(1.0 - 2.0 / peak.significance()));
	}

	
	@SuppressWarnings("unchecked")
	@Override
	public void parse(String line, GridImage<CoordinateType> forImage) throws ParseException {
		if(line == null) return;
		if(line.length() == 0) return;
		if(line.charAt(0) == '#' || line.charAt(0) == '!') return;

		StringTokenizer tokens = new StringTokenizer(line);

		if(tokens.countTokens() < 5) return;

		setID(tokens.nextToken());

		CoordinateType coords = (CoordinateType) forImage.getReference().clone();
		setCoordinates(coords);
		if(coords instanceof Precessing) {
			coords.parse(tokens.nextToken() + " " + tokens.nextToken() + " " + tokens.nextToken());
			CoordinateEpoch epoch = ((Precessing) forImage.getReference()).getEpoch();
			((Precessing) coords).precess(epoch);
		}
		else coords.parse(tokens.nextToken() + " " + tokens.nextToken());
		
		setRadius(new DataPoint(Double.parseDouble(tokens.nextToken()) * Unit.arcsec, 0.0));
		if(tokens.hasMoreTokens()) peak = new DataPoint(Double.parseDouble(tokens.nextToken()), 0.0);
		
		String nextArg = tokens.hasMoreTokens() ? tokens.nextToken() : null;
		if(nextArg != null) {
			if(nextArg.equals("+-")) nextArg = tokens.nextToken();
			try { 
				peak.setRMS(Double.parseDouble(nextArg));
				nextArg = tokens.hasMoreTokens() ? tokens.nextToken() : null;
			}
			catch(NumberFormatException e) {}
		}

		if(forImage instanceof GridSource) {
			GridSource<CoordinateType> sourceMap = (GridSource<CoordinateType>) forImage;
			Unit unit = nextArg == null ? 
					new Unit("Jy/beam", sourceMap.getUnit("Jy/beam")) :
					new Unit(nextArg, sourceMap.getUnit(nextArg));
			peak.scale(unit.value);
		}
			
		while(tokens.hasMoreTokens()) addComment(tokens.nextToken() + " ");
		setComment(getComment().trim());
		
	}

	@Override
	public String toCrushString(GridImage<CoordinateType> image) {
		return getID() + "\t" + super.toCrushString(image) + "  " + DataPoint.toString(peak, image.unit);
	}
	
	@Override
	public String getComment() {
		return "s/n=" + Util.f2.format(peak.significance()) + " " + super.getComment();
	}
	
	@Override
	public StringTokenizer parseCrush(String line, GridImage<CoordinateType> forImage) {
		StringTokenizer tokens = super.parseCrush(line, forImage);
		
		peak.value = Double.parseDouble(tokens.nextToken());
		String next = tokens.nextToken();
		if(next.equals("+-")) {
			peak.setRMS(Double.parseDouble(tokens.nextToken()));
			next = tokens.nextToken();
		}
		
		if(forImage instanceof GridSource)
			peak.value *= ((GridSource<CoordinateType>) forImage).getUnit(next);
		
		return tokens;
	}
	
	
	public void centroid(GridImage<CoordinateType> map) {
		Bounds bounds = getBounds(map, 2.0 * map.getImageFWHM());
		Vector2D index = new Vector2D();
		double sumw = 0.0;
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) if(map.isUnflagged(i, j)) {
			double w = Math.abs(map.getS2N(i,j));
			index.incrementX(w * i);
			index.incrementY(w * j);
			sumw += w;
		}
		index.scale(1.0/sumw);
		map.getGrid().getCoords(index, getCoordinates());
	}
	
	// Increase the aperture until it captures >98% of the flux
	// Then estimate the spread by comparing the peak flux to the integrated flux
	// with an equivalent Gaussian source profile...
	public WeightedPoint getAdaptiveIntegral(GridImage<CoordinateType> map) {
		double origRadius = getRadius().value;
		WeightedPoint I = getIntegral(map);

		// 20 iterations on 20% increases covers ~40-fold increase in radius
		// Should be plenty even for a very large pointing source...
		for(int i=0; i<20; i++) {
			// A 20% increase in radius is ~40% increase in area.
			// Look for less than 5% change in amplitude --> 0.05*0.4 == 0.02 --> 2% change in integral
			getRadius().value *= 1.2;
			WeightedPoint I1 = getIntegral(map);
			if(I1.value > 1.01 * I.value) I = I1;
			else break;
		}
		
		getRadius().value = origRadius;
		return I;
	}
	
	public double spread(GridImage<CoordinateType> map) {	
		WeightedPoint I = getAdaptiveIntegral(map);	
		return Math.sqrt(I.value / (2.0 * Math.PI * peak.value) * map.getPixelArea());
	}
	
	public void measureShape(GridImage<CoordinateType> map) {	
		getRadius().value = spread(map) * Util.sigmasInFWHM;
		getRadius().setRMS(getRadius().value / peak.significance());
		
		// the FWHM scales inversely with sqrt(peak), so sigma(FWHM)^2 ~ 0.5 sigma(peak)^2
		// but FWHM0 = sqrt(FWHM^2 - smooth^2)
		// so sigma(FWHM0)^2 ~ (0.5 / FWHM0 * 2 FWHM)^2  sigma(FWHM)^2
		//                   ~ F2 / (F^2 - S^2) * sigmaF^2
		//                   ~ 1 + (S^2 / F0^2) * sigmaF^2
		if(map instanceof GridSource) {
			double SF0 = Math.min(1.0, ((GridSource<?>) map).instrument.resolution / getRadius().value);
			getRadius().weight *= 2.0 / (1.0 + SF0 * SF0);
		}
	}
	
	public DataTable getData(GridImage<CoordinateType> map) {
		DataTable data = new DataTable();
		
		double sizeUnit = 1.0; 
		String sizeName = "pixels"; 
		double beamScaling = 1.0;
		
		if(map instanceof GridSource) {
			GridSource<?> sourceMap = ((GridSource<?>) map);
			Instrument<?> instrument = sourceMap.instrument;
			sizeUnit = instrument.getDefaultSizeUnit();
			sizeName = instrument.getDefaultSizeName();
			beamScaling = sourceMap.getInstrumentBeamArea() / sourceMap.getImageBeamArea();
		}
			
		data.add(new Datum("peak", peak.value * beamScaling / map.unit.value, map.unit.name));
		data.add(new Datum("dpeak", peak.rms() * beamScaling / map.unit.value, map.unit.name));
		data.add(new Datum("peakS2N", peak.significance(), ""));
		
		DataPoint F = new DataPoint(getAdaptiveIntegral(map));
		F.scale(map.getPixelArea() / map.getImageBeamArea());
		
		data.add(new Datum("int", F.value * beamScaling / map.unit.value, map.unit.name));
		data.add(new Datum("dint", F.rms() * beamScaling / map.unit.value, map.unit.name));
		data.add(new Datum("intS2N", F.significance(), ""));
		
		data.add(new Datum("FWHM", getRadius().value / sizeUnit, sizeName));
		data.add(new Datum("dFWHM", getRadius().rms() / sizeUnit, sizeName));
		
		return data;
	}
	
	public String pointingInfo(GridImage<CoordinateType> map) {
		double sizeUnit = 1.0;
		String sizeName = "pixels"; 
		double beamScaling = 1.0;
		
		if(map instanceof GridSource) {
			GridSource<?> sourceMap = ((GridSource<?>) map);
			Instrument<?> instrument = sourceMap.instrument;
			sizeUnit = instrument.getDefaultSizeUnit();
			sizeName = instrument.getDefaultSizeName();
			beamScaling = sourceMap.getInstrumentBeamArea() / sourceMap.getImageBeamArea();
		}
		
		peak.scale(beamScaling);
		
		String info = "  Peak: " + DataPoint.toString(peak, map.unit) 
			+ " (S/N ~ " + Util.f1.format(peak.significance()) + ")\n";
	
		peak.scale(1.0 / beamScaling);
		
		DataPoint F = new DataPoint(getAdaptiveIntegral(map));
		F.scale(map.getPixelArea() / map.getImageBeamArea());
		F.scale(1.0 / map.unit.value);
		
		info += "  Int.: " + F.toString() + "\n";
		
		info += "  FWHM: " + Util.f1.format(getRadius().value / sizeUnit) 
				+ (getRadius().weight > 0.0 ? " +- " + Util.f1.format(getRadius().rms() / sizeUnit) : "")
				+ " " + sizeName;
	
		
		return info;
	}
	
	
}
