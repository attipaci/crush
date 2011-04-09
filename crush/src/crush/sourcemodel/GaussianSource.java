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

import util.*;
import util.astro.CoordinateEpoch;
import util.astro.EquatorialCoordinates;
import util.astro.Precessing;
import util.data.DataPoint;
import util.data.WeightedPoint;


public class GaussianSource extends CircularRegion {
	public DataPoint peak;
	public boolean isCorrected = false;
	
	public GaussianSource() { }

	public GaussianSource(String line, AstroImage forImage) throws ParseException {
		super(line, forImage);
	}
	
	public GaussianSource(AstroMap map, Vector2D offset, double r) {
		super(map, offset, r);
	}
	
	public GaussianSource(SphericalCoordinates coords, double r) {
		super(coords, r);
	}
	
	public Bounds getBounds(AstroImage image, double beams) {
		double origRadius = radius.value;
		radius.value = beams * image.getImageFWHM();
		Bounds bounds = getBounds(image);
		radius.value = origRadius;
		return bounds;
	}
	
	
	public void setPeakPixel(AstroMap map) {
		MapIndex index = map.getS2NImage().indexOfMax();
		SphericalCoordinates coords = (SphericalCoordinates) map.getReference().clone();
		map.grid.getCoords(new Vector2D(index.i, index.j), coords);
		id = map.sourceName;
		setCenter(coords);
		radius = new DataPoint(map.getImageFWHM(), Math.sqrt(map.getPixelArea()));	
	}
	
	public void setPeak(AstroMap map) {
		setPeakPixel(map);
		finetunePeak(map);
	}
	
	public void setPeakCentroid(AstroMap map) {
		setPeak(map);
		centroid(map);
	}
	
	@Override
	public DataPoint finetunePeak(AstroMap map) {
		AstroImage rms = map.getRMSImage();
		peak = super.finetunePeak(map);
		Vector2D centerIndex = getIndex(map.grid);
		if(peak == null) {
			peak.value = map.valueAtIndex(centerIndex);
			peak.setRMS(rms.valueAtIndex(centerIndex));
		}
		return peak;
	}
	
	public void scale(double factor) {
		peak.scale(factor);		
	}
	
	public void getChi2(AstroMap map, WeightedPoint chi2, double level) {
		chi2.noData();
		Bounds bounds = getBounds(map);
		Vector2D centerIndex = getIndex(map.grid);
		final Vector2D resolution = map.getResolution();
		final double sigmaX = radius.value / Util.sigmasInFWHM / resolution.x;
		final double sigmaY = radius.value / Util.sigmasInFWHM / resolution.y;
		final double Ax = -0.5 / (sigmaX*sigmaX);
		final double Ay = -0.5 / (sigmaY*sigmaY);

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) if(map.flag[i][j] == 0) {
			final double di = i-centerIndex.x;
			final double dj = j-centerIndex.y;
			final double dev = (map.data[i][j] - level - peak.value * Math.exp(Ax*di*di + Ay*dj*dj)) / map.getRMS(i, j);
			chi2.value += dev * dev;
			chi2.weight += 1.0;
		}
	}
	
	public void addGaussian(AstroImage image, double FWHM, double scaling) {
		Bounds bounds = getBounds(image, 3.0 * FWHM / image.getImageFWHM());
		Vector2D centerIndex = getIndex(image.grid);
		final Vector2D resolution = image.getResolution();
		final double sigmaX = radius.value / Util.sigmasInFWHM / resolution.x;
		final double sigmaY = radius.value / Util.sigmasInFWHM / resolution.y;
		final double Ax = -0.5 / (sigmaX*sigmaX);
		final double Ay = -0.5 / (sigmaY*sigmaY);
			
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) {
			final double di = i-centerIndex.x;
			final double dj = j-centerIndex.y;
			image.data[i][j] += scaling * peak.value * Math.exp(Ax*di*di + Ay*dj*dj);
		}
		
	}
	
	public void add(AstroImage image) { add(image, null); }
	
	public void add(AstroImage image, Collection<Region> others) { add(image, radius.value, 1.0, others); }
	
	public void addPoint(AstroImage image) { addPoint(image, null); }
	
	public void addPoint(AstroImage image, Collection<Region> others) { add(image, image.getImageFWHM(), 1.0, others); }	
	
	public void subtract(AstroImage image) { subtract(image, null); }
	
	public void subtract(AstroImage image, Collection<Region> others) { add(image, radius.value, -1.0, others); }
	
	public void subtractPoint(AstroImage image) { subtractPoint(image, null); }
	
	public void subtractPoint(AstroImage image, Collection<Region> others) { add(image, image.getImageFWHM(), -1.0, others); }
	
	
	public void add(AstroImage image, final double FWHM, final double scaling) {
		add(image, FWHM, scaling, null);
	}
	
	public void add(AstroImage image, final double FWHM, final double scaling, final Collection<Region> others) {			
		double filterFWHM = Double.NaN;
		double imageFWHM = image.getImageFWHM();
	
		if(!Double.isNaN(image.extFilterFWHM)) 
			filterFWHM = Math.sqrt(
					image.extFilterFWHM * image.extFilterFWHM 
					+ FWHM * FWHM 
					- imageFWHM * imageFWHM
			);		
		
		// Remove source only after the filtering corrections are applied to its flux...
		addGaussian(image, FWHM, scaling);

		// Correct for filtering.
		// Consider that only the tip of the source might escape the filter...
		
		double filterBeamScale = Math.pow(FWHM / filterFWHM, 2.0);
		
		if(!Double.isNaN(image.extFilterFWHM)) if(image instanceof AstroMap) {
			AstroMap map = (AstroMap) image;
	
			filterBeamScale *= Double.isNaN(map.filterBlanking) ? 1.0 : Math.min(1.0, map.filterBlanking / peak.significance());
			addGaussian(image, filterFWHM, -scaling * filterBeamScale);
		}

		final Vector2D resolution = image.getResolution();
		final double sigmaX = radius.value / Util.sigmasInFWHM / resolution.x;
		final double sigmaY = radius.value / Util.sigmasInFWHM / resolution.y;
		final double Ax = -0.5 / (sigmaX*sigmaX);
		final double Ay = -0.5 / (sigmaY*sigmaY);	

		Vector2D centerIndex = getIndex(image.grid);
		// Adjust prior detections.for the filtering around this one.
		if(others != null) for(Region region : others) if(region instanceof GaussianSource) if(region != this) {
			GaussianSource source = (GaussianSource) region;
			Vector2D sourceIndex = source.getIndex(image.grid);
			final double di = sourceIndex.x - centerIndex.x;
			final double dj = sourceIndex.y - centerIndex.y;
			
			if(!Double.isNaN(image.extFilterFWHM))
				source.peak.value -= scaling * filterBeamScale * peak.value * Math.exp(Ax*di*di + Ay * dj*dj);
		}
		
	}
	
	public double getCorrectionFactor(AstroMap map, double FWHM) {	
		double correction = 1.0;	
		
		// Correct for filtering.
		// Consider that only the tip of the source might escape the filter...
		if(!Double.isNaN(map.extFilterFWHM)) {
			double filterFraction = Double.isNaN(map.filterBlanking) ? 1.0 : Math.min(1.0, map.filterBlanking / peak.significance());
			double filtering = 1.0 - 1.0 / map.getExtFilterCorrectionFactor(FWHM);;
			correction *= 1.0 / (1.0 - filtering * filterFraction);
		}
		
		return correction;
	}
	
	
	public void correct(AstroMap map, double FWHM) {	
		if(isCorrected) throw new IllegalStateException("Source is already corrected.");
		double correction = getCorrectionFactor(map, FWHM);
		peak.scale(correction);
		isCorrected = true;
	}
	

	public void uncorrect(AstroMap map, double FWHM) {
		if(!isCorrected) throw new IllegalStateException("Source is already uncorrected.");
		double correction = getCorrectionFactor(map, FWHM);
		peak.scale(1.0 / correction);
		isCorrected = false;
	}
	
	
	// Formula from Kovacs et al. (2006)
	public void setSearchRadius(AstroImage image, double pointingRMS) {
		double beamSigma = image.instrument.resolution / Util.sigmasInFWHM;
		radius.value = Math.sqrt(4.0 * pointingRMS * pointingRMS - 2.0 * beamSigma * beamSigma * Math.log(1.0 - 2.0 / peak.significance()));
	}

	
	@Override
	public void parse(String line, AstroImage forImage) throws ParseException {
		if(line == null) return;
		if(line.length() == 0) return;
		if(line.charAt(0) == '#' || line.charAt(0) == '!') return;

		StringTokenizer tokens = new StringTokenizer(line);

		if(tokens.countTokens() < 5) return;

		id = tokens.nextToken();

		coords = new EquatorialCoordinates();
		coords.parse(tokens.nextToken() + " " + tokens.nextToken() + " " + tokens.nextToken());
	
		if(forImage.getReference() instanceof Precessing) {
			CoordinateEpoch epoch = ((Precessing) forImage.getReference()).getEpoch();
			((Precessing) coords).precess(epoch);
		}
		
		radius = new DataPoint(Double.parseDouble(tokens.nextToken()) * Unit.arcsec, 0.0);
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
			
		Unit unit = nextArg == null ? new Unit("Jy/beam", forImage.getUnit("Jy/beam")) : new Unit(nextArg, forImage.getUnit(nextArg));
		while(tokens.hasMoreTokens()) comment += tokens.nextToken() + " ";
		comment.trim();
		
		peak.scale(unit.value);
	}

	@Override
	public String toCrushString(AstroImage image) {
		return id + "\t" + super.toCrushString(image) + "  " + DataPoint.toString(peak, image.unit);
	}
	
	@Override
	public String getComment() {
		return "s/n=" + Util.f2.format(peak.significance()) + " " + super.getComment();
	}
	
	@Override
	public StringTokenizer parseCrush(String line, AstroImage forImage) {
		StringTokenizer tokens = super.parseCrush(line, forImage);
		
		peak.value = Double.parseDouble(tokens.nextToken());
		String next = tokens.nextToken();
		if(next.equals("+-")) {
			peak.setRMS(Double.parseDouble(tokens.nextToken()));
			next = tokens.nextToken();
		}
		
		peak.value *= forImage.getUnit(next);
		
		return tokens;
	}
	
	
	public void centroid(AstroMap map) {
		Bounds bounds = getBounds(map, 2.0);
		Vector2D index = new Vector2D();
		double sumw = 0.0;
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) if(map.flag[i][j] == 0) {
			double w = Math.abs(map.getS2N(i,j));
			index.x += w * i;
			index.y += w * j;
			sumw += w;
		}
		index.scale(1.0/sumw);
		map.grid.getCoords(index, coords);
	}
	
	// Increase the aperture until it captures >98% of the flux
	// Then estimate the spread by comparing the peak flux to the integrated flux
	// with an equivalent Gaussian source profile...
	public WeightedPoint getAdaptiveIntegral(AstroMap map) {
		double origRadius = radius.value;		
		WeightedPoint I = getIntegral(map);

		// 20 iterations on 20% increases covers ~40-fold increase in radius
		// Should be plenty even for a very large pointing source...
		for(int i=0; i<20; i++) {
			// A 20% increase in radius is ~40% increase in area.
			// Look for less than 5% change in amplitude --> 0.05*0.4 == 0.02 --> 2% change in integral
			radius.value *= 1.2;
			WeightedPoint I1 = getIntegral(map);
			if(I1.value > 1.01 * I.value) I = I1;
			else break;
		}
		
		radius.value = origRadius;
		return I;
	}
	
	public double spread(AstroMap map) {	
		WeightedPoint I = getAdaptiveIntegral(map);	
		return Math.sqrt(I.value / (2.0 * Math.PI * peak.value) * map.getPixelArea());
	}
	
	public void measureShape(AstroMap map) {	
		radius.value = spread(map) * Util.sigmasInFWHM;
		radius.setRMS(radius.value / peak.significance());
		
		// the FWHM scales inversely with sqrt(peak), so sigma(FWHM)^2 ~ 0.5 sigma(peak)^2
		// but FWHM0 = sqrt(FWHM^2 - smooth^2)
		// so sigma(FWHM0)^2 ~ (0.5 / FWHM0 * 2 FWHM)^2  sigma(FWHM)^2
		//                   ~ F2 / (F^2 - S^2) * sigmaF^2
		//                   ~ 1 + (S^2 / F0^2) * sigmaF^2
		double SF0 = Math.min(1.0, map.instrument.resolution / radius.value);
		radius.weight *= 2.0 / (1.0 + SF0 * SF0);
	}
	
	public DataTable getData(AstroMap map) {
		DataTable data = new DataTable();
		
		double sizeUnit = map.instrument.getDefaultSizeUnit();
		String sizeName = map.instrument.getDefaultSizeName();
		double beamScaling = map.getInstrumentBeamArea() / map.getImageBeamArea();
			
		data.add(new Datum("peak", peak.value * beamScaling / map.unit.value, map.unit.name));
		data.add(new Datum("dpeak", peak.rms() * beamScaling / map.unit.value, map.unit.name));
		data.add(new Datum("peakS2N", peak.significance(), ""));
		
		DataPoint F = new DataPoint(getAdaptiveIntegral(map));
		F.scale(map.getPixelArea() / map.getImageBeamArea());
		
		data.add(new Datum("int", F.value * beamScaling / map.unit.value, map.unit.name));
		data.add(new Datum("dint", F.rms() * beamScaling / map.unit.value, map.unit.name));
		data.add(new Datum("intS2N", F.significance(), ""));
		
		data.add(new Datum("FWHM", radius.value / sizeUnit, sizeName));
		data.add(new Datum("dFWHM", radius.rms() / sizeUnit, sizeName));
		
		return data;
	}
	
	public String pointingInfo(AstroMap map) {
		double sizeUnit = map.instrument.getDefaultSizeUnit();
		peak.scale(map.getInstrumentBeamArea() / map.getImageBeamArea());
		
		String info = "  Peak: " + DataPoint.toString(peak, map.unit) 
			+ " (S/N ~ " + Util.f1.format(peak.significance()) + ")\n";
	
		peak.scale(map.getImageBeamArea() / map.getInstrumentBeamArea());
		
		DataPoint F = new DataPoint(getAdaptiveIntegral(map));
		F.scale(map.getPixelArea() / map.getImageBeamArea());
		F.scale(1.0 / map.unit.value);
		
		info += "  Int.: " + F.toString() + "\n";
		
		info += "  FWHM: " + Util.f1.format(radius.value / sizeUnit) 
				+ (radius.weight > 0.0 ? " +- " + Util.f1.format(radius.rms() / sizeUnit) : "")
				+ " " + map.instrument.getDefaultSizeName();
	
		
		return info;
	}
	
	
}
