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


import util.*;
import util.astro.CelestialProjector;
import util.astro.CoordinateEpoch;
import util.astro.EquatorialCoordinates;
import util.astro.Gnomonic;
import util.data.Index2D;
import util.data.Statistics;

import java.util.*;

import crush.*;


public abstract class SourceMap extends SourceModel {	
	public double integationTime = 0.0;
	public double smoothing = 0.0;
	public int signalMode = Frame.TOTAL_POWER;
	
	public SourceMap(Instrument<?> instrument) {
		super(instrument);
	}

	
	@Override
	public void reset() {
		super.reset();
		setSmoothing();
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		super.createFrom(collection);
		
		System.out.print(" Initializing Source Map. ");	
		
		try { setProjection(hasOption("projection") ? SphericalProjection.forName(option("projection").getValue()) : new Gnomonic()); }
		catch(Exception e) { setProjection(new Gnomonic()); }		
	}

	public void setSmoothing() {
		if(!hasOption("smooth")) return;
		double sizeUnit = instrument.getDefaultSizeUnit();
		Configurator option = option("smooth");
		if(option.equals("beam")) setSmoothing(instrument.resolution);
		else if(option.equals("halfbeam")) setSmoothing(0.5 * instrument.resolution);
		else if(option.equals("2/3beam")) setSmoothing(instrument.resolution / 1.5);
		else if(option.equals("minimal")) setSmoothing(0.3 * instrument.resolution);
		else if(option.equals("optimal")) {
			setSmoothing(hasOption("smooth.optimal") ? 
					option("smooth.optimal").getDouble() * sizeUnit : instrument.resolution);
		}
		else setSmoothing(Math.max(0.0, option.getDouble()) * sizeUnit);
	}
	
	public void setSmoothing(double value) { smoothing = value; }
	
	public double getSmoothing() { return smoothing; }


	@Override
	public double getPointSize(Instrument<?> instrument) { return Math.hypot(instrument.resolution, smoothing); }
	
	@Override
	public double getSourceSize(Instrument<?> instrument) { return Math.hypot(super.getSourceSize(instrument), smoothing); }
	
	
	public synchronized void searchCorners() throws Exception {
		final Vector2D fixedSize = new Vector2D(Double.NaN, Double.NaN);
		final boolean fixSize = hasOption("map.size");
		
		if(fixSize) {
			StringTokenizer sizes = new StringTokenizer(option("map.size").getValue(), " \t,:xX");

			fixedSize.setX(0.5* Double.parseDouble(sizes.nextToken()) * Unit.arcsec);
			fixedSize.setY(sizes.hasMoreTokens() ? 0.5 * Double.parseDouble(sizes.nextToken()) * Unit.arcsec : fixedSize.getX());

			xRange.setRange(-fixedSize.getX(), fixedSize.getX());
			yRange.setRange(-fixedSize.getY(), fixedSize.getY());	
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

				final CelestialProjector projector = new CelestialProjector(getProjection());

				for(Frame exposure : integration) if(exposure != null) {
					boolean valid = false;

					for(Pixel pixel : pixels) {
						exposure.project(pixel.getPosition(), projector);

						if(!fixSize) {
							xRange.include(projector.offset.getX());
							yRange.include(projector.offset.getY());
							scan.longitudeRange.include(projector.offset.getX());
							scan.latitudeRange.include(projector.offset.getY());
						}
						else for(Channel channel : pixel) {
							if(Math.abs(projector.offset.getX()) > fixedSize.getX()) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
							else if(Math.abs(projector.offset.getY()) > fixedSize.getY()) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
							else valid = true;
						}
					}

					if(fixSize && !valid) exposure = null;
				}
			}
		}.process();		
	}
	
	public long getMemoryFootprint() {
		return (long) (pixels() * getPixelFootprint());
	}
	
	public long getReductionFootprint() {
		// The composite map + one copy for each thread, plus base image (double)
		return (CRUSH.maxThreads + 1) * getMemoryFootprint() + baseFootprint();
	}
	
	public abstract double getPixelFootprint();
	
	public abstract long baseFootprint();
	
	public abstract long pixels();
	
	public abstract double resolution();
	
	public void setSize() {
		//double margin = hasOption("map.margin") ? option("map.margin").getDouble() * instrument.getDefaultSizeUnit() : 0.0;
		
		// Figure out what offsets the corners of the map will have...
		try { searchCorners(); }
		catch(Exception e) { 
			e.printStackTrace(); 
			System.exit(1);
		}
		
		/*
		xRange.max += margin;
		xRange.min -= margin;
		yRange.max += margin;
		yRange.min -= margin;
		*/

		int sizeX = (int)Math.ceil((xRange.max - xRange.min)/resolution()) + 2;
		int sizeY = (int)Math.ceil((yRange.max - yRange.min)/resolution()) + 2;
	
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
		int diagonal = (int) Math.hypot(sizeX, sizeY);
		
		System.err.println("\n");
		System.err.println("ERROR! Map is too large to fit into memory (" + sizeX + "x" + sizeY + " pixels).");
		System.err.println("       Requires " + ((long) (pixels() * getPixelFootprint()) >> 20) + " MB free memory."); 
		System.err.println();
		
		if(scans.size() > 1) {
			// Check if there is a scan at least half long edge away from the median center...
			Collection<Scan<?,?>> suspects = findOutliers(diagonal >> 2);
			if(!suspects.isEmpty()) {
				System.err.println("   * Check that all scans observe the same area on sky.");
				System.err.println("     Remove scans, which are far from your source.");	
				System.err.println("     Suspect scan(s): ");
				for(Scan<?,?> scan : suspects) System.err.println("\t--> " + scan.descriptor);
				System.err.println();
			}
		}
		else {
			// Check if there is a scan that spans at least a half long edge... 
			Collection<Scan<?,?>> suspects = findSlewing(diagonal >> 1);
			if(!suspects.isEmpty()) {
				System.err.println("   * Was data acquired during telescope slew?");	
				System.err.println("     Suspect scan(s):");
				for(Scan<?,?> scan : suspects) System.err.println("\t--> " + scan.descriptor);
				System.err.println();
			}
			else {	
				System.err.println("   * Could there be an unflagged pixel with an invalid position?");
				System.err.println("     check your instrument configuration and pixel data files.");
				System.err.println();
			}
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
	
	public Collection<Scan<?,?>> findOutliers(int pixels) {
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
			if(d > pixels * resolution()) outliers.add(scan);
		}
		return outliers;
	}
	
	public Collection<Scan<?,?>> findSlewing(int pixels) {
		ArrayList<Scan<?,?>> slews = new ArrayList<Scan<?,?>>();
		double cosLat = getProjection().getReference().cosLat();
		
		for(Scan<?,?> scan : scans) {
			double span = Math.hypot(scan.longitudeRange.span() * cosLat, scan.latitudeRange.span());
			if(span > pixels * resolution()) slews.add(scan);
		}
		return slews;
	}
	
	public abstract void getIndex(final Frame exposure, final Pixel pixel, final CelestialProjector projector, final Index2D index);
	
	protected abstract void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain, final double dt, final int excludeSamples);
	
	public abstract boolean isMasked(Index2D index); 
	
	protected int add(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, double filtering, int signalMode) {
		int goodFrames = 0;
		final int excludeSamples = ~Frame.SAMPLE_SOURCE_BLANK;
		final double samplingInterval = integration.instrument.samplingInterval;

		final CelestialProjector projector = new CelestialProjector(getProjection());
		final Index2D index = new Index2D();
		
		for(final Frame exposure : integration) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS)) {
			final double fG = integration.gain * exposure.getSourceGain(signalMode);
			final double fGC = (isMasked(index) ? 1.0 : filtering) * fG;
			
			goodFrames++;

			for(final Pixel pixel : pixels) {
				getIndex(exposure, pixel, projector, index);
				add(exposure, pixel, index, fGC, sourceGain, samplingInterval, excludeSamples);
			}
		}
		return goodFrames;
	}
	
	@Override
	public void add(Integration<?,?> integration) {
		add(integration, signalMode);
	}
	
	public void add(Integration<?,?> integration, int signalMode) {
		Configurator option = option("source");
		Instrument<?> instrument = integration.instrument; 

		boolean filterCorrection = option.isConfigured("correct");
	
		integration.comments += "Map";
		if(id != null) integration.comments += "." + id;
		// For jackknived maps indicate sign...
		
		Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels();
		
		// Proceed only if there are enough pixels to do the job...
		if(!checkPixelCount(integration)) return;

		// Calculate the effective source NEFD based on the latest weights and the current filtering
		integration.calcSourceNEFD();

		// For the first source generation, apply the point source correction directly to the signals...
		final boolean signalCorrection = integration.sourceGeneration == 0;
		final double[] sourceGain = instrument.getSourceGains(signalCorrection);
	
		double averageFiltering = instrument.getAverageFiltering();
		double C = filterCorrection && !signalCorrection ? averageFiltering : 1.0;
	
		add(integration, pixels, sourceGain, C, signalMode);

		if(signalCorrection)
			integration.comments += "[C1~" + Util.f2.format(1.0/averageFiltering) + "] ";
		else if(filterCorrection) {
			integration.comments += "[C2=" + Util.f2.format(1.0/C) + "] ";
		}
		
		integration.comments += " ";
	}
	
	protected abstract void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, double[] syncGain, final boolean isMasked);
	
	protected void sync(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, int signalMode) {
		final CelestialProjector projector = new CelestialProjector(getProjection());
		final Index2D index = new Index2D();
				
		for(final Frame exposure : integration) if(exposure != null) {
			final double fG = integration.gain * exposure.getSourceGain(signalMode); 
			
			// Remove source from all but the blind channels...
			for(final Pixel pixel : pixels)  {
				getIndex(exposure, pixel, projector, index);
				sync(exposure, pixel, index, fG, sourceGain, integration.sourceSyncGain, isMasked(index));
			}
		}
		
		System.arraycopy(sourceGain, 0, integration.sourceSyncGain, 0, sourceGain.length);
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
		
		if(hasOption("source.coupling")) calcCoupling(integration, pixels, sourceGain, integration.sourceSyncGain);
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

		Dependents parms = integration.dependents.containsKey("source") ? integration.dependents.get("source") : new Dependents(integration, "source");
		for(Pixel pixel : pixels) parms.clear(pixel, 0, integration.size());

		for(Pixel pixel : pixels) for(Channel channel : pixel) if(channel.flag == 0) 
			parms.add(channel, np * sourceGain[channel.index] * sourceGain[channel.index] / channel.variance);

		for(Frame exposure : integration) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS)) 
			parms.add(exposure, nf * exposure.relativeWeight * exposure.transmission);

		for(Pixel pixel : pixels) parms.apply(pixel, 0, integration.size());

		if(CRUSH.debug) for(Pixel pixel : pixels) integration.checkForNaNs(pixel, 0, integration.size());
	}
	
	public abstract double covariantPoints();
	
	protected abstract void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain);

	public Range xRange = new Range();
	public Range yRange = new Range();
	
}

