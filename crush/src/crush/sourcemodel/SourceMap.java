/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
	
	protected int excludeSamples = ~Frame.SAMPLE_SOURCE_BLANK;
	
	public SourceMap(Instrument<?> instrument) {
		super(instrument);
	}

	public void setExcludeSamples(int pattern) {
		excludeSamples = pattern;
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
	
	
	private synchronized void flagOutside(final Integration<?,?> integration, final Vector2D fixedSize) {
		final Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels();
	
		new CRUSH.IndexedFork<Void>(integration.size()) {
			private AstroProjector projector;
			
			@Override
			public void init() {
				super.init();
				projector = new AstroProjector(getProjection());
			}
			
			@Override
			protected void processIndex(int t) {
				Frame exposure = integration.get(t);
				if(exposure == null) return;
				boolean valid = false;

				for(Pixel pixel : pixels) {
					exposure.project(pixel.getPosition(), projector);
					for(Channel channel : pixel) {
						if(Math.abs(projector.offset.x()) > fixedSize.x()) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
						else if(Math.abs(projector.offset.y()) > fixedSize.y()) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
						else valid = true;
					}
				}

				if(!valid) exposure = null;
				
			}
		}.process();
	}
	
	private synchronized void searchCorners(final Integration<?,?> integration) {
		//final Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels();
		final Collection<? extends Pixel> pixels = integration.instrument.getPerimeterPixels();
		if(pixels.size() == 0) return;
		
		class ProjectorData {	
			public Range longitudeRange = new Range();
			public Range latitudeRange = new Range();
		}
		
		CRUSH.IndexedFork<ProjectorData> findCorners = new CRUSH.IndexedFork<ProjectorData>(integration.size()) {
			private ProjectorData data;
			private AstroProjector projector;
			
			@Override
			public void init() {
				super.init();
				data = new ProjectorData();
				projector = new AstroProjector(getProjection());
			}
			
			@Override
			protected void processIndex(int t) {
				
				Frame exposure = integration.get(t);
				if(exposure == null) return;
				if(exposure.isFlagged(Frame.SKIP_SOURCE)) return;
				
				for(Pixel pixel : pixels) {
					exposure.project(pixel.getPosition(), projector);	
					data.longitudeRange.include(projector.offset.x());
					data.latitudeRange.include(projector.offset.y());
				}
			}
			
			@Override
			public ProjectorData getPartialResult() { return data; }
			
			@Override
			public ProjectorData getResult() {
				data = null;
				for(Parallel<ProjectorData> task : getWorkers()) {
					ProjectorData local = task.getPartialResult();
					if(data == null) data = local;
					else {
						data.longitudeRange.include(local.longitudeRange);
						data.latitudeRange.include(local.latitudeRange);
					}
				}
				return data;
			}

			
		};
		
		findCorners.process();
		ProjectorData data = findCorners.getResult();
		
		if(CRUSH.debug) System.err.println("### map range " + integration.getDisplayID() + "> "
				+ Util.f1.format(data.longitudeRange.span() / Unit.arcsec) + " x " 
				+ Util.f1.format(data.latitudeRange.span() / Unit.arcsec));
		
		Scan<?,?> scan = integration.scan;
		scan.longitudeRange.include(data.longitudeRange);
		scan.latitudeRange.include(data.latitudeRange);
		
		xRange.include(data.longitudeRange);
		yRange.include(data.latitudeRange);
		
	}
	
	public synchronized void searchCorners() throws Exception {
		final Vector2D fixedSize = new Vector2D(Double.NaN, Double.NaN);
		final boolean fixSize = hasOption("map.size");
		
		if(fixSize) {
			StringTokenizer sizes = new StringTokenizer(option("map.size").getValue(), " \t,:xX");

			fixedSize.setX(0.5* Double.parseDouble(sizes.nextToken()) * Unit.arcsec);
			fixedSize.setY(sizes.hasMoreTokens() ? 0.5 * Double.parseDouble(sizes.nextToken()) * Unit.arcsec : fixedSize.x());

			xRange.setRange(-fixedSize.x(), fixedSize.x());
			yRange.setRange(-fixedSize.y(), fixedSize.y());	
			
			for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) flagOutside(integration, fixedSize);
		}
			
		else {
			xRange.empty();
			yRange.empty();
			
			for(Scan<?,?> scan : scans) {
				scan.longitudeRange = new Range();
				scan.latitudeRange = new Range();
				for(Integration<?,?> integration : scan) searchCorners(integration);
			}
		}
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
		
		if(CRUSH.debug) System.err.println("\n### map range: " + Util.f1.format(xRange.span() / Unit.arcsec) + " x " 
				+  Util.f1.format(yRange.span() / Unit.arcsec) + " arcsec");
		
		xRange.setMax(xRange.max() + margin.x());
		xRange.setMin(xRange.min() - margin.x());
		yRange.setMax(yRange.max() + margin.y());
		yRange.setMin(yRange.min() - margin.y());
		
		Vector2D resolution = resolution();

		if(CRUSH.debug) System.err.println("### map + margin: " + Util.f1.format(xRange.span() / Unit.arcsec) + " x " 
					+  Util.f1.format(yRange.span() / Unit.arcsec) + " arcsec");
		
		int sizeX = 2 + (int)Math.ceil(xRange.span() / resolution.x());
		int sizeY = 2 + (int)Math.ceil(yRange.span() / resolution.y());
		
		if(CRUSH.debug) System.err.println("### map pixels: " + sizeX + " x " + sizeY);
	
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
	
	protected abstract void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain);
	
	public abstract boolean isMasked(Index2D index); 
	
	public abstract void addNonZero(SourceMap other);
	
	protected boolean isAddingToMaster() { return false; }
	
	protected int add(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final double filtering, final int signalMode) {	
		return addForkFrames(integration, pixels, sourceGain, filtering, signalMode);
	}
	
	protected synchronized int addForkFrames(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final double filtering, final int signalMode) {	
				
		class Mapper extends CRUSH.IndexedFork<Integer> {
			private SourceMap localSource;
			private AstroProjector projector;
			private Index2D index;
			private int mappingFrames = 0;

			Mapper() { super(integration.size()); }

			@Override
			protected void init() {
				super.init();
	
				if(isAddingToMaster()) localSource = SourceMap.this;
				else {
					localSource = (SourceMap) getRecyclerCopy(false);
					localSource.reset(true);
				}
				
				projector = new AstroProjector(localSource.getProjection());
				index = new Index2D();
			}
			
			@Override
			protected void processIndex(int index) {
				Frame exposure = integration.get(index);
				if(exposure != null) process(exposure);
			}
			
			private void process(Frame exposure) {
				if(exposure.isFlagged(Frame.SOURCE_FLAGS)) return;
				
				final double fG = integration.gain * exposure.getSourceGain(signalMode);	
				if(fG == 0.0) return;
				
				mappingFrames++;

				for(final Pixel pixel : pixels) {
					localSource.getIndex(exposure, pixel, projector, index);
					localSource.add(exposure, pixel, index, fG, sourceGain);
					//localSource.add(exposure, pixel, index, (isMasked(index) ? fG : filtering * fG), sourceGain);
				}
			}
			
			@Override
			public Integer getPartialResult() { return mappingFrames; }
			
			@Override
			public Integer getResult() {				
				mappingFrames = 0;
				for(Parallel<Integer> task : getWorkers()) {
					mappingFrames += task.getPartialResult();
					
					if(!isAddingToMaster()) {
						SourceMap localMap = ((Mapper) task).localSource;
						addNonZero(localMap);
						localMap.recycle();
					}
				}
				
				return mappingFrames;
			}	
		}
		
		Mapper mapping = new Mapper();
		mapping.process();
		
		return mapping.getResult();		
	}
	
	protected synchronized int addForkPixels(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final double filtering, final int signalMode) {	
		int mappingFrames = 0;
		
		for(Frame exposure : integration) if(exposure != null) {
			exposure.tempC = exposure.isFlagged(Frame.SOURCE_FLAGS) ? 0.0F : integration.gain * exposure.getSourceGain(signalMode);
			if(exposure.tempC != 0.0F) mappingFrames++;
		}
		
		class Mapper extends CRUSH.IndexedFork<Void> {
			private SourceMap localSource;
			private AstroProjector projector;
			private Index2D index;
		
			Mapper() { super(pixels.size()); }

			@Override
			protected void init() {
				super.init();
						
				if(isAddingToMaster()) localSource = SourceMap.this;
				else {
					localSource = (SourceMap) getRecyclerCopy(false);
					localSource.reset(true);
				}
				
				projector = new AstroProjector(localSource.getProjection());
				index = new Index2D();
		
			}
			
			@Override
			protected void processIndex(int index) {
				process(pixels.get(index));
			}
			
			private void process(final Pixel pixel) {
				for(Frame exposure : integration) if(exposure != null) if(exposure.tempC != 0.0F) {
					localSource.getIndex(exposure, pixel, projector, index);
					localSource.add(exposure, pixel, index, exposure.tempC, sourceGain);
					//localSource.add(exposure, pixel, index, (isMasked(index) ? exposure.tempC : filtering * exposure.tempC), sourceGain);
				}
			}
			
			@Override
			public Void getResult() {				
				for(Parallel<Void> task : getWorkers()) if(!isAddingToMaster()) {
					SourceMap localMap = ((Mapper) task).localSource;
					addNonZero(localMap);
					localMap.recycle();
				}
				return null;
			}	
			
		}
		
		Mapper mapping = new Mapper();
		mapping.process();
		mapping.getResult();	

		return mappingFrames;
	}
	
	
	
	@Override
	public void add(Integration<?,?> integration) {
		add(integration, signalMode);
	}
	
	public void add(Integration<?,?> integration, int signalMode) {
		final Instrument<?> instrument = integration.instrument; 

		integration.comments += "Map";
		if(id != null) integration.comments += "." + id;
		// For jackknived maps indicate sign...
		
		// Proceed only if there are enough pixels to do the job...
		if(!checkPixelCount(integration)) return;

		// Calculate the effective source NEFD based on the latest weights and the current filtering
		integration.calcSourceNEFD();

		final double averageFiltering = instrument.getAverageFiltering();			
	
		// For the first source generation, apply the point source correction directly to the signals.
		final boolean signalCorrection = integration.sourceGeneration == 0;
		boolean mapCorrection = hasSourceOption("correct") && !signalCorrection;
	
		
		final int mappingFrames = add(
				integration, 
				integration.instrument.getMappingPixels(), 
				instrument.getSourceGains(signalCorrection), 
				mapCorrection ? averageFiltering : 1.0, 
				signalMode
		);
		
		if(CRUSH.debug) System.err.println("### mapping frames:" + mappingFrames);
		
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
	
	protected void sync(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {			
		integration.new Fork<Void>() {
			private AstroProjector projector;
			private Index2D index;

			@Override
			public void init() {
				super.init();
				projector = new AstroProjector(getProjection());
				index = new Index2D();
			}
			
			@Override 
			protected void process(final Frame exposure) {
				//if(exposure.isFlagged(Frame.SKIP_SOURCE)) return;
				
				final double fG = integration.gain * exposure.getSourceGain(signalMode); 
				
				// Remove source from all but the blind channels...
				for(final Pixel pixel : pixels)  {
					SourceMap.this.getIndex(exposure, pixel, projector, index);	
					sync(exposure, pixel, index, fG, sourceGain, integration.sourceSyncGain, isMasked(index));
				}
			}
		}.process();
	}

	@Override
	public void sync(Integration<?,?> integration) {
		sync(integration, signalMode);
	}

	
	public void sync(final Integration<?,?> integration, final int signalMode) {
		Instrument<?> instrument = integration.instrument; 
		
		final double[] sourceGain = instrument.getSourceGains(false);	
		if(integration.sourceSyncGain == null) integration.sourceSyncGain = new double[sourceGain.length];
		
		final List<? extends Pixel> pixels = instrument.getMappingPixels();
		
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
		final double np = sumpw > 0.0 ? N / sumpw : 0.0;
		final double nf = sumfw > 0 ? N / sumfw : 0.0;

		// TODO revise for composite sources...
		final Dependents parms = integration.dependents.containsKey("source") ? integration.dependents.get("source") : new Dependents(integration, "source");
	
		for(int k=pixels.size(); --k >= 0; ) {
			Pixel pixel = pixels.get(k);
			parms.clear(pixel, 0, integration.size());
			
			for(Channel channel : pixel) if(channel.flag == 0) 
				parms.addAsync(channel, np * sourceGain[channel.index] * sourceGain[channel.index] / channel.variance);
		}
		
		for(Frame exposure : integration) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS))
			parms.addAsync(exposure, nf * exposure.relativeWeight * Math.abs(exposure.getSourceGain(signalMode)));
	
		for(int k=pixels.size(); --k >= 0; ) {
			Pixel pixel = pixels.get(k);
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

