/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import kovacs.astro.AstroProjector;
import kovacs.astro.CoordinateEpoch;
import kovacs.astro.EclipticCoordinates;
import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.FocalPlaneCoordinates;
import kovacs.astro.GalacticCoordinates;
import kovacs.data.Index2D;
import kovacs.data.Statistics;
import kovacs.math.Range;
import kovacs.math.SphericalCoordinates;
import kovacs.math.Vector2D;
import kovacs.projection.Gnomonic;
import kovacs.projection.Projection2D;
import kovacs.projection.SphericalProjection;
import kovacs.text.TableFormatter;
import kovacs.util.*;
import crush.*;


public abstract class SourceMap extends SourceModel {	
	public double integationTime = 0.0;
	public double smoothing = 0.0;
	public int signalMode = Frame.TOTAL_POWER;
	
	public boolean allowIndexing = true;
	public int marginX = 0, marginY = 0;
		
	public SourceMap(Instrument<?> instrument) {
		super(instrument);
	}

	
	@Override
	public void reset(boolean clearContent) {
		super.reset(clearContent);
		setSmoothing();
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		super.createFrom(collection);
		
		System.out.print(" Initializing Source Map. ");	
				
		String system = hasOption("system") ? option("system").getValue().toLowerCase() : "equatorial";

		Projection2D<SphericalCoordinates> projection = null;
		
		try { projection = hasOption("projection") ? SphericalProjection.forName(option("projection").getValue()) : new Gnomonic(); }
		catch(Exception e) { projection = new Gnomonic(); }		
		
		Scan<?,?> firstScan = scans.get(0);
		
		if(system.equals("horizontal")) projection.setReference(firstScan.horizontal);
		else if(system.equals("focalplane")) projection.setReference(new FocalPlaneCoordinates()); 
		else if(firstScan.isMovingObject) {
			System.err.println(" Forcing equatorial for moving object.");
			getOptions().process("system", "equatorial");
			projection.setReference(firstScan.equatorial);
		}
		else if(system.equals("ecliptic")) {
			EclipticCoordinates ecliptic = new EclipticCoordinates();
			ecliptic.fromEquatorial(firstScan.equatorial);
			projection.setReference(ecliptic);
		}
		else if(system.equals("galactic")) {
			GalacticCoordinates galactic = new GalacticCoordinates();
			galactic.fromEquatorial(firstScan.equatorial);
			projection.setReference(galactic);
		}
		else if(system.equals("supergalactic")) {
			EclipticCoordinates sg = new EclipticCoordinates();
			sg.fromEquatorial(firstScan.equatorial);
			projection.setReference(sg);
		}
		else projection.setReference(firstScan.equatorial);
		
		setProjection(projection);
	}
	
	public void setSmoothing() {
		if(!hasOption("smooth")) return;
		setSmoothing(getSmoothing(option("smooth").getValue()));
	}
	
	public double getRequestedSmoothing(Configurator option) {
		if(option == null) return smoothing;
		if(!option.isEnabled) return smoothing;
		String spec = option.getValue();	
		if(spec.length() == 0) return smoothing;
		return getSmoothing(spec);
	}
	
	public double getSmoothing(String spec) {
		double sizeUnit = getInstrument().getSizeUnitValue();
		double beam = getInstrument().getPointSize();
		double pixelSmoothing = getPixelizationSmoothing();
		double fwhm = 0.0;
		
		if(spec.equals("beam")) fwhm = beam;
		else if(spec.equals("halfbeam")) fwhm = 0.5 * beam;
		else if(spec.equals("2/3beam")) fwhm = beam / 1.5;
		else if(spec.equals("minimal")) fwhm = 0.3 * beam;
		else if(spec.equals("optimal")) fwhm = hasOption("smooth.optimal") ? option("smooth.optimal").getDouble() * sizeUnit : beam;
		else fwhm = Math.max(0.0, Double.parseDouble(spec) * sizeUnit);
		
		return fwhm > pixelSmoothing ? fwhm : pixelSmoothing;
	}
	
	public void setSmoothing(double value) { smoothing = value; }
	
	public double getSmoothing() { return smoothing; }

	public abstract double getPixelizationSmoothing();

	@Override
	public double getPointSize() { return ExtraMath.hypot(getInstrument().getPointSize(), getRequestedSmoothing(option("smooth"))); }
	
	@Override
	public double getSourceSize() { return ExtraMath.hypot(super.getSourceSize(), getRequestedSmoothing(option("smooth"))); }
	
	
	public synchronized void searchCorners() throws Exception {
		final Vector2D fixedSize = new Vector2D(Double.NaN, Double.NaN);
		final boolean fixSize = hasOption("map.size");
		
		if(fixSize) {
			StringTokenizer sizes = new StringTokenizer(option("map.size").getValue(), " \t,:xX");

			fixedSize.setX(0.5* Double.parseDouble(sizes.nextToken()) * Unit.arcsec);
			fixedSize.setY(sizes.hasMoreTokens() ? 0.5 * Double.parseDouble(sizes.nextToken()) * Unit.arcsec : fixedSize.x());

			xRange.setRange(-fixedSize.x(), fixedSize.x());
			yRange.setRange(-fixedSize.y(), fixedSize.y());	
		}
			
		new IntegrationFork<Void>() {		
			@Override
			public void process(Integration<?,?> integration) {					
				Scan<?,?> scan = integration.scan;
				
				// Try restrict boxing to the corners only...
				// May not work well for maps that reach far on both sides of the equator...
				// Also may end up with larger than necessary maps as rotated box corners can go
				// farther than any of the actual pixels...
				// Else use 'perimeter' key

				// The safe thing to do is to check all pixels...
				Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels();
				scan.longitudeRange = new Range();
				scan.latitudeRange = new Range();
	
				final AstroProjector projector = new AstroProjector(getProjection());

				for(Frame exposure : integration) if(exposure != null) {
					boolean valid = false;

					for(Pixel pixel : pixels) {
						exposure.project(pixel.getPosition(), projector);

						if(!fixSize) {
							xRange.include(projector.offset.x());
							yRange.include(projector.offset.y());
							scan.longitudeRange.include(projector.offset.x());
							scan.latitudeRange.include(projector.offset.y());
						}
						else for(Channel channel : pixel) {
							if(Math.abs(projector.offset.x()) > fixedSize.x()) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
							else if(Math.abs(projector.offset.y()) > fixedSize.y()) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
							else valid = true;
						}
					}

					if(fixSize && !valid) exposure = null;
				}
			}
		}.process();		
	}
	
	public long getMemoryFootprint(long pixels) {
		return (long) (pixels * getPixelFootprint() + baseFootprint(pixels));
	}
	
	public long getReductionFootprint(long pixels) {
		// The composite map + one copy for each thread, plus base image (double)
		return (CRUSH.maxThreads + 1) * getMemoryFootprint(pixels) + baseFootprint(pixels);
	}
	
	public abstract double getPixelFootprint();
	
	public abstract long baseFootprint(long pixels);
	
	public abstract int pixels();
	
	public abstract Vector2D resolution();
	
	public void setSize() {
		Vector2D margin = new Vector2D();
		
		if(hasSourceOption("margin")) {
			List<Double> values = sourceOption("margin").getDoubles();
			margin.setX(values.get(0));
			margin.setY(values.size() > 1 ? values.get(1) : margin.x());
			margin.scale(getInstrument().getSizeUnitValue());
		}
		
		// Figure out what offsets the corners of the map will have...
		try { searchCorners(); }
		catch(Exception e) { 
			e.printStackTrace(); 
			System.exit(1);
		}
		
		xRange.setMax(xRange.max() + margin.x());
		xRange.setMin(xRange.min() - margin.x());
		yRange.setMax(yRange.max() + margin.y());
		yRange.setMin(yRange.min() - margin.y());
		
		Vector2D resolution = resolution();

		int sizeX = 2 + (int)Math.ceil(xRange.span() / resolution.x());
		int sizeY = 2 + (int)Math.ceil(yRange.span() / resolution.y());
	
		try { 
			checkForStorage(sizeX, sizeY);	
			setSize(sizeX, sizeY);
		}
		catch(OutOfMemoryError e) { memoryError(sizeX, sizeY); }
	}
	
	public abstract void setSize(int sizeX, int sizeY);
	
	public abstract Projection2D<SphericalCoordinates> getProjection(); 
	
	public abstract void setProjection(Projection2D<SphericalCoordinates> projection); 
	
	public void memoryError(int sizeX, int sizeY) {
		
		Vector2D resolution = resolution();
		double diagonal = ExtraMath.hypot(sizeX * resolution.x(), sizeY * resolution.y());
		
		System.err.println("\n");
		System.err.println("ERROR! Map is too large to fit into memory (" + sizeX + "x" + sizeY + " pixels).");
		System.err.println("       Requires " + (getMemoryFootprint((long) sizeX * sizeY) >> 20) + " MB free memory."); 
		System.err.println();
		
		boolean foundSuspects = false;
		
		if(scans.size() > 1) {
			// Check if there is a scan at least half long edge away from the median center...
			Collection<Scan<?,?>> suspects = findOutliers(diagonal / 2.0);
			if(!suspects.isEmpty()) {
				foundSuspects = true;
				System.err.println("   * Check that all scans observe the same area on sky.");
				System.err.println("     Remove scans, which are far from your source.");	
				System.err.println("     Suspect scan(s): ");
				for(Scan<?,?> scan : suspects) System.err.println("\t--> " + scan.getID());
				System.err.println();
			}
		}

		// Check if there is a scan that spans at least a half long edge... 
		Collection<Scan<?,?>> suspects = findSlewing(diagonal / 2.0);
		if(!suspects.isEmpty()) {
			foundSuspects = true;
			System.err.println("   * Was data acquired during telescope slew?");	
			System.err.println("     Suspect scan(s):");
			for(Scan<?,?> scan : suspects) System.err.println("\t--> " + scan.getID());
			System.err.println();
		}
		
		if(!foundSuspects) {	
			System.err.println("   * Could there be an unflagged pixel with an invalid position?");
			System.err.println("     check your instrument configuration and pixel data files.");
			System.err.println();
		}
			
		System.err.println("   * Increase the amount of memory available to crush, by editing the '-Xmx'");
		System.err.println("     option to Java in 'wrapper.sh'.");
		System.err.println();
	
		System.exit(1);
	}
	
	// Check for minimum required storage (without reduction overheads)
	protected void checkForStorage(int sizeX, int sizeY) {
		Runtime runtime = Runtime.getRuntime();
		long max = runtime.maxMemory();
		long used = runtime.totalMemory() - runtime.freeMemory();
		long required = (long) (getPixelFootprint() * sizeX * sizeY);
		if(used + required > max) memoryError(sizeX, sizeY); 
	}
	
	public Collection<Scan<?,?>> findOutliers(double maxDistance) {
		ArrayList<Scan<?,?>> outliers = new ArrayList<Scan<?,?>>();

		float[] ra = new float[scans.size()];
		float[] dec = new float[scans.size()];
		
		for(int i=scans.size(); --i >= 0; ) {
			EquatorialCoordinates equatorial = (EquatorialCoordinates) scans.get(i).equatorial.clone();
			equatorial.precess(CoordinateEpoch.J2000);
			ra[i] = (float) equatorial.RA();
			dec[i] = (float) equatorial.DEC();
		}
		EquatorialCoordinates median = new EquatorialCoordinates(Statistics.median(ra), Statistics.median(dec), CoordinateEpoch.J2000);

		for(Scan<?,?> scan : scans) {
			EquatorialCoordinates equatorial = (EquatorialCoordinates) scan.equatorial.clone();
			equatorial.precess(CoordinateEpoch.J2000);
			double d = equatorial.distanceTo(median);
			if(d > maxDistance) outliers.add(scan);
		}
		return outliers;
	}
	
	public Collection<Scan<?,?>> findSlewing(double maxDistance) {
		ArrayList<Scan<?,?>> slews = new ArrayList<Scan<?,?>>();
		double cosLat = getProjection().getReference().cosLat();
		
		for(Scan<?,?> scan : scans) {
			double span = ExtraMath.hypot(scan.longitudeRange.span() * cosLat, scan.latitudeRange.span());
			if(span > maxDistance) slews.add(scan);
		}
		return slews;
	}
	
	public abstract void getIndex(final Frame exposure, final Pixel pixel, final AstroProjector projector, final Index2D index);
	
	protected abstract void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain, final double dt, final int excludeSamples);
	
	public abstract boolean isMasked(Index2D index); 
	
	protected synchronized int add(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, double filtering, int signalMode) {
		int goodFrames = 0;
		
		final int excludeSamples = ~Frame.SAMPLE_SOURCE_BLANK;
		final double samplingInterval = integration.instrument.samplingInterval;

		final AstroProjector projector = new AstroProjector(getProjection());
		final Index2D index = new Index2D();	
			
		for(final Frame exposure : integration) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS)) {
			final double fG = integration.gain * exposure.getSourceGain(signalMode);
			final double fGC = (isMasked(index) ? 1.0 : filtering) * fG;
				
			if(fGC == 0.0) continue;
			
			goodFrames++;

			for(final Pixel pixel : pixels) {
				getIndex(exposure, pixel, projector, index);		
				add(exposure, pixel, index, fGC, sourceGain, samplingInterval, excludeSamples);
			}
		}
		
			
		if(CRUSH.debug) System.err.println("### mapping frames:" + goodFrames);
		
		return goodFrames;
	}
	
	@Override
	public void add(Integration<?,?> integration) {
		add(integration, signalMode);
	}
	
	public void add(Integration<?,?> integration, int signalMode) {
		Instrument<?> instrument = integration.instrument; 

		// For the first source generation, apply the point source correction directly to the signals.
		final boolean signalCorrection = integration.sourceGeneration == 0;
		boolean mapCorrection = hasSourceOption("correct") && !signalCorrection;
	
		integration.comments += "Map";
		if(id != null) integration.comments += "." + id;
		// For jackknived maps indicate sign...
		
		Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels();
		
		// Proceed only if there are enough pixels to do the job...
		if(!checkPixelCount(integration)) return;

		// Calculate the effective source NEFD based on the latest weights and the current filtering
		integration.calcSourceNEFD();

		final double[] sourceGain = instrument.getSourceGains(signalCorrection);
	
		double averageFiltering = instrument.getAverageFiltering();			
	
		add(integration, pixels, sourceGain, mapCorrection ? averageFiltering : 1.0, signalMode);
			
		if(signalCorrection)
			integration.comments += "[C1~" + Util.f2.format(1.0/averageFiltering) + "] ";
		else if(mapCorrection) {
			integration.comments += "[C2=" + Util.f2.format(1.0/averageFiltering) + "] ";
		}
		
		integration.comments += " ";
	}
		
	protected abstract void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, double[] syncGain, final boolean isMasked);
	
	public void setSyncGains(final Integration<?,?> integration, final Pixel pixel, final double[] sourceGain) {
		if(integration.sourceSyncGain == null) integration.sourceSyncGain = new double[sourceGain.length];
		for(Channel channel : pixel) integration.sourceSyncGain[channel.index] = sourceGain[channel.index];
	}
	
	protected void sync(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, int signalMode) {
		final AstroProjector projector = new AstroProjector(getProjection());
		final Index2D index = new Index2D();
				
		for(final Frame exposure : integration) if(exposure != null) {
			final double fG = integration.gain * exposure.getSourceGain(signalMode); 
			
			// Remove source from all but the blind channels...
			for(final Pixel pixel : pixels)  {
				getIndex(exposure, pixel, projector, index);
				sync(exposure, pixel, index, fG, sourceGain, integration.sourceSyncGain, isMasked(index));
			}
		}
	}

	@Override
	public void sync(Integration<?,?> integration) {
		sync(integration, signalMode);
	}

	
	public void sync(Integration<?,?> integration, int signalMode) {
		Instrument<?> instrument = integration.instrument; 
		
		double[] sourceGain = instrument.getSourceGains(false);	
		if(integration.sourceSyncGain == null) integration.sourceSyncGain = new double[sourceGain.length];
		
		Collection<? extends Pixel> pixels = instrument.getMappingPixels();
		
		if(hasSourceOption("coupling")) calcCoupling(integration, pixels, sourceGain, integration.sourceSyncGain);
		sync(integration, pixels, sourceGain, signalMode);

		// Do an approximate accounting of the source dependence...
		double sumpw = 0.0;
		for(Pixel pixel : pixels) for(Channel channel : pixel) if(channel.flag == 0) 
			sumpw += sourceGain[channel.index] * sourceGain[channel.index] / channel.variance;

		double sumfw = 0.0;
		for(Frame exposure : integration) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS))
			sumfw += exposure.relativeWeight * exposure.getSourceGain(signalMode);		

		double N = Math.min(integration.scan.sourcePoints, countPoints()) / covariantPoints();
		double np = sumpw > 0.0 ? N / sumpw : 0.0;
		double nf = sumfw > 0 ? N / sumfw : 0.0;

		// TODO revise for composite sources...
		Dependents parms = integration.dependents.containsKey("source") ? integration.dependents.get("source") : new Dependents(integration, "source");
		for(Pixel pixel : pixels) {
			parms.clear(pixel, 0, integration.size());
			
			for(Channel channel : pixel) if(channel.flag == 0) 
				parms.add(channel, np * sourceGain[channel.index] * sourceGain[channel.index] / channel.variance);
		}
			
		for(Frame exposure : integration) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS)) 
			parms.add(exposure, nf * exposure.relativeWeight * Math.abs(exposure.getSourceGain(signalMode)));

		for(Pixel pixel : pixels) {
			parms.apply(pixel, 0, integration.size());
			setSyncGains(integration, pixel, sourceGain);
		}

		if(CRUSH.debug) for(Pixel pixel : pixels) integration.checkForNaNs(pixel, 0, integration.size());
	}
	
	public abstract double covariantPoints();
	
	protected abstract void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain);
	
	@Override
	public void suggestMakeValid() {
		super.suggestMakeValid();
		System.err.println("            * Increase 'grid' for a coarser map pixellization.");
		if(hasSourceOption("redundancy")) 
			System.err.println("            * Disable redundancy checking ('forget=source.redundancy').");
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		if(name.equals("smooth")) return TableFormatter.getNumberFormat(formatSpec).format(smoothing / getInstrument().getSizeUnitValue());
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	public Range xRange = new Range();
	public Range yRange = new Range();
	
}

