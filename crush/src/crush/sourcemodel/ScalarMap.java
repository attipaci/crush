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

import java.awt.Color;
import java.io.*;
import java.util.*;

import kovacs.astro.CelestialProjector;
import kovacs.astro.EclipticCoordinates;
import kovacs.astro.GalacticCoordinates;
import kovacs.astro.SourceCatalog;
import kovacs.data.*;
import kovacs.math.Range;
import kovacs.math.SphericalCoordinates;
import kovacs.math.Vector2D;
import kovacs.plot.ColorScheme;
import kovacs.plot.Data2DLayer;
import kovacs.plot.ImageArea;
import kovacs.plot.colorscheme.Colorful;
import kovacs.projection.Projection2D;
import kovacs.util.*;

import crush.*;
import crush.astro.AstroMap;



public class ScalarMap extends SourceMap {
	public AstroMap map;
	private double[][] base; 
	private boolean[][] mask;
		
	public boolean enableWeighting = true;
	public boolean enableLevel = true;
	public boolean enableBias = true;	
	
	public ScalarMap(Instrument<?> instrument) {
		super(instrument);
	}
	
	@Override
	public void setInstrument(Instrument<?> instrument) {
		super.setInstrument(instrument);
		if(map != null) map.instrument = instrument;
	}

	@Override
	public synchronized void add(SourceModel model, double weight) {
		ScalarMap other = (ScalarMap) model;
		isReady = false;
	
		map.addDirect(other.map, weight);
		
		enableLevel &= other.enableLevel;
		enableWeighting &= other.enableWeighting;
		enableBias &= other.enableBias;
		
		generation = Math.max(generation, other.generation);
	}


	@Override
	public SourceModel copy(boolean withContents) {
		ScalarMap copy = (ScalarMap) super.copy(withContents);
		try { copy.map = (AstroMap) map.copy(withContents); }
		catch(OutOfMemoryError e) {
			System.err.println("ERROR! Ran of of memory while making a copy of the source map.");
			System.err.println();
			System.err.println("   * Check that the map size is reasonable for the area mapped and that");
			System.err.println("     all scans reduced together belong to the same source or region.");
			System.err.println();
			System.err.println("   * Increase the amount of memory available to crush, by editing the '-Xmx'");
			System.err.println("     option to Java in 'wrapper.sh' (or 'wrapper.bat' for Windows).");
			System.err.println();
			System.err.println("   * If using 64-bit Unix OS and Java, you can also add the '-d64' option to");
			System.err.println("     allow Java to access over 2GB.");
			System.err.println();
			System.err.println("   * Reduce the number of parallel threads in the reduction by increasing");
			System.err.println("     the idle CPU cores with the 'reservecpus' option.");
		    System.err.println();
		    System.exit(1);
		}
		return copy;
	}
	
	public void standalone() {
		base = new double[map.sizeX()][map.sizeY()];
		createMask();
	}

	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		map = new AstroMap();
		
		setInstrument(getInstrument());
		
		super.createFrom(collection);
				
		double gridSize = getInstrument().resolution / 5.0;
		if(hasOption("grid")) gridSize = option("grid").getDouble() * Unit.arcsec;
	
		Scan<?,?> firstScan = scans.get(0);
		
		for(Scan<?,?> scan : scans) map.scans.add(scan);	
		
		if(hasOption("unit")) map.setUnit(option("unit").getValue());
		
		map.setParallel(CRUSH.maxThreads);
		map.creator = CRUSH.class.getSimpleName();
		map.setName(firstScan.getSourceName());
		map.commandLine = commandLine;
		
		String system = hasOption("system") ? option("system").getValue().toLowerCase() : "equatorial";
		
		Projection2D<SphericalCoordinates> projection = getProjection();
		
		if(system.equals("horizontal")) projection.setReference(firstScan.horizontal);
		else if(system.equals("focalplane")) projection.setReference(new FocalPlaneCoordinates()); 
		else if(firstScan.isPlanetary) {
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
	
		map.setGrid(new SphericalGrid());
		map.setProjection(projection);
		map.setResolution(gridSize);
		
		setSize();

		// Make the reference fall on pixel boundaries.
		map.getGrid().refIndex.setX(0.5 - Math.rint(xRange.min()/gridSize));
		map.getGrid().refIndex.setY(0.5 - Math.rint(yRange.min()/gridSize));
			
		map.printShortInfo();		
		
		base = new double[map.sizeX()][map.sizeY()];
		createMask();
		
		if(hasOption("indexing")) {
			try {index(); }
			catch(Exception e) { 
				System.err.println("WARNING! Indexing error:");
				e.printStackTrace();
			}
		}
		
		if(hasOption("sources")) {
			try { 
				SourceCatalog<SphericalCoordinates> catalog = new SourceCatalog<SphericalCoordinates>();
				catalog.read(option("sources").getPath(), map); 
				try { insertSources(catalog); }
				catch(Exception e) {
					System.err.println("WARNING! Source insertion error:");
					e.printStackTrace();
				}
				map.reset(true);
			}
			catch(IOException e) {
				System.err.println("WARNING! Cannot read sources: " + e.getMessage());
			}	
		}
		
		// TODO Apply mask to data either via flag.inside or flag.outside + mask file.
		
		if(hasOption("source.model")) {
			try { applyModel(option("source.model").getValue()); }
			catch(Exception e) { 
				System.err.println("WARNING! Cannot read source model. Check the file name and path."); 
				e.printStackTrace();
			}
		}
		
	}
		
	public synchronized void insertSources(SourceCatalog<SphericalCoordinates> catalog) throws Exception {
		catalog.remove(map);
		for(GaussianSource<?> source : catalog) source.getPeak().scale(-1.0);
		
		System.err.println(" Inserting test sources into data.");
		
		new IntegrationFork<Void>() {
			@Override
			public void process(Integration<?,?> integration) {
				sync(integration);
				integration.sourceGeneration=0; // Reset the source generation...
			}
		}.process();
	}
	
	public synchronized void applyModel(String fileName) throws Exception {
		System.err.println(" Applying source model:");
			
		AstroMap model = new AstroMap(fileName, map.instrument);
			
		model.regridTo(map);	
		map.generation = 1;
		map.sanitize();
		isReady = true;
			
		double blankingLevel = getBlankingLevel();
		if(!Double.isNaN(blankingLevel)) System.err.println("  --> Blanking positions above " + Util.f2.format(blankingLevel) + " sigma in source model.");
		
		try { 
			System.err.print("  --> Removing model from the data. ");
			super.sync(); 
			System.err.println();
		}
		catch(Exception e) { e.printStackTrace(); }
	
		System.err.println();
		// For testing the removal of the model...
		//for(int i=0; i<map.sizeX(); i++) Arrays.fill(base[i], 0.0);
	}
		
	public void index() throws Exception {
		System.err.print(" Indexing maps. ");
	
		final double maxUsage = hasOption("indexing.saturation") ? option("indexing.saturation").getDouble() : 0.5;
		System.err.println(" (Up to " + Util.d1.format(100.0*maxUsage) + "% of RAM saturation.)");
		
		final Runtime runtime = Runtime.getRuntime();
		long maxAvailable = runtime.maxMemory() - getReductionFootprint();
		final long maxUsed = (long) (maxUsage * maxAvailable);
		
		new IntegrationFork<Void>() {
			@Override
			public void process(final Integration<?,?> integration) {	
				if(isInterrupted()) return;
				
				if(runtime.totalMemory() - runtime.freeMemory() >= maxUsed) interruptAll();
				else createLookup(integration);	
			}
		}.process();
	}
	

	// 3 double maps (signal, weight, integrationTime), one int (flag) and one boolean (maks)
	@Override
	public double getPixelFootprint() { return 8*3 + 4 + 1.0/8.0; }
	
	@Override
	public long baseFootprint() { return 8L * pixels(); }

	@Override
	public void setSize(int sizeX, int sizeY) {
		map.setSize(sizeX, sizeY);
	}

	@Override
	public long pixels() { return (long) map.sizeX() * map.sizeY(); }
	
	@Override
	public double resolution() { return Math.sqrt(map.getPixelArea()); }
	
	@Override
	public Projection2D<SphericalCoordinates> getProjection() { return map.getProjection(); }
	
	@Override
	public void setProjection(Projection2D<SphericalCoordinates> projection) {
		if(map != null) map.setProjection(projection);
	}
	
	@Override
	public synchronized void process(Scan<?,?> scan) {	
		map.normalize();
		if(base != null) map.addImage(base);
			
		if(enableLevel && scan.getSourceGeneration() == 0) map.level(true);
		else enableLevel = false;	
		
		if(hasOption("source.despike")) {
			Configurator despike = option("source.despike");
			double level = 10.0;
			despike.mapValueTo("level");
			if(despike.isConfigured("level")) level = despike.get("level").getDouble();
			map.despike(level);
		}
		
		filter();
		map.sanitize();

		scan.weight = 1.0;				
		if(hasOption("weighting.scans")) {
			Configurator weighting = option("weighting.scans");
			String method = "rms";
			if(weighting.isConfigured("method")) method = weighting.get("method").getValue().toLowerCase();
			else if(weighting.getValue().length() > 0) method = weighting.getValue().toLowerCase();
			scan.weight = map.getChi2(method.equals("robust"));
			if(Double.isNaN(scan.weight)) scan.weight = 0.0;
		}

		if(hasOption("scanmaps")) {
			map.fileName = CRUSH.workPath + "/scan-" + (int)scan.getMJD() + "-" + scan.getID() + ".fits";
			try { map.write(); }
			catch(Exception e) { e.printStackTrace(); }
		}
		
		isReady = true;
	}	
	
	@Override
	public synchronized void postprocess(Scan<?,?> scan) {
		super.postprocess(scan);
		
		if(countPoints() == 0) return;
		
		if(hasOption("pointing")) if(option("pointing").equals("auto") || option("pointing").equals("suggest")) {
			double optimal = hasOption("smooth.optimal") ? 
					option("smooth.optimal").getDouble() * scan.instrument.getSizeUnit() :
					scan.instrument.resolution;
	
			map.smoothTo(optimal);
			if(hasOption("pointing.exposureclip")) map.clipBelowRelativeExposure(option("pointing.exposureclip").getDouble(), 0.1);
			map.reweight(true);
			scan.pointing = getPeakSource();
		}
	}
	
	public synchronized void filter() {
		if(!hasOption("source.filter") || getSourceSize() <= 0.0) {
			map.noExtFilter();
			map.filterBlanking = Double.NaN;
			return;
		}
			
		Configurator filter = option("source.filter");
		filter.mapValueTo("fwhm");
			
		String mode = filter.isConfigured("type") ? filter.get("type").getValue() : "convolution";
		String directive = "auto";
			
		if(filter.isConfigured("interpolation")) {
			String spec = filter.get("interpolation").getValue().toLowerCase();
			// The default terminology...
			if(spec.equals("nearest")) map.setInterpolationType(Data2D.NEAREST_NEIGHBOR);
			else if(spec.equals("linear")) map.setInterpolationType(Data2D.BILINEAR);
			else if(spec.equals("quadratic")) map.setInterpolationType(Data2D.PIECEWISE_QUADRATIC);
			else if(spec.equals("cubic")) map.setInterpolationType(Data2D.BICUBIC_SPLINE);
			// And alternative names...
			else if(spec.equals("none")) map.setInterpolationType(Data2D.NEAREST_NEIGHBOR);
			else if(spec.equals("bilinear")) map.setInterpolationType(Data2D.BILINEAR);
			else if(spec.equals("piecewise")) map.setInterpolationType(Data2D.PIECEWISE_QUADRATIC);
			else if(spec.equals("bicubic")) map.setInterpolationType(Data2D.BICUBIC_SPLINE);
			else if(spec.equals("spline")) map.setInterpolationType(Data2D.BICUBIC_SPLINE);
		}
		
		if(filter.isConfigured("fwhm")) directive = filter.get("fwhm").getValue().toLowerCase();
		
		double filterScale = directive.equals("auto") ? 
				5.0 * getSourceSize() : Double.parseDouble(directive) * getInstrument().getSizeUnit();
			
		double filterBlanking = filter.isConfigured("blank") ? filter.get("blank").getDouble() : Double.NaN;

		if(mode.equalsIgnoreCase("fft")) map.fftFilterAbove(filterScale, filterBlanking);
		else map.filterAbove(filterScale, filterBlanking);
			
		map.filterBlanking = filterBlanking;
	}
	
	// TODO for non spherical coordinates...
	public GaussianSource<SphericalCoordinates> getPeakSource() {
		EllipticalSource<SphericalCoordinates> source = new EllipticalSource<SphericalCoordinates>();
		source.setPeak(map);	

		// Rescale the peak to an equivalent unsmoothed value...
		source.getPeak().scale(map.getImageBeamArea() / map.getInstrumentBeamArea());
		
		// Alternative is to use the centroid around that peak...
		if(hasOption("pointing.method")) if(option("pointing.method").equals("centroid")) source.centroid(map);	
		
		double criticalS2N = hasOption("pointing.significance") ? option("pointing.significance").getDouble() : 5.0;
		if(source.getPeak().significance() < criticalS2N) return null;
		
		// Finally, calculate the FWHM from the observed beam spread...
		source.measureShape(map);
			
		return source;
	}
	
	
	@Override
	public synchronized void process(boolean verbose) {	
		
		map.normalize();
		map.generation++; // Increment the map generation...
			
		double blankingLevel = getBlankingLevel();
		
		if(verbose) if(enableLevel) System.err.print("{level} ");
		
		if(verbose) if(hasOption("source.despike")) System.err.print("{despike} ");
		
		if(hasOption("source.filter") && getSourceSize() > 0.0) {
			if(verbose) System.err.print("{filter} ");
		}
		
		if(verbose) if(enableWeighting) if(hasOption("weighting.scans"))
			for(Scan<?,?> scan : scans) System.err.print("{" + Util.f2.format(scan.weight) + "} ");
		
		if(hasOption("source.redundancy"))  {
			if(hasOption("source.redundancy")) System.err.print("(check) ");
			double minIntTime = getInstrument().integrationTime * (hasOption("source.redundancy") ? option("source.redundancy").getInt() : 0);
			if(minIntTime > 0.0) map.clipBelowExposure(minIntTime);
		}

		if(hasOption("smooth") && !hasOption("smooth.external")) {
			if(verbose) System.err.print("(smooth) ");
			map.smoothTo(getSmoothing());
		}
		
		if(hasOption("smooth.weights")) {
			AstroMap copy = (AstroMap) map.copy(true);
			copy.smooth(getSmoothing(option("smooth.weights").getValue()));
			map.setWeight(copy.getWeight());
		}

		// Apply the filtering to the final map, to reflect the correct blanking
		// level...
		if(hasOption("source.filter")) if(verbose) System.err.print("(filter) ");
		
		filter();
		map.filterCorrect();
		
		// Noise and exposure clip after smoothing for evened-out coverage...
		// Eposure clip
		if(hasOption("exposureclip")) {
			if(verbose) System.err.print("(exposureclip) ");
			map.clipBelowRelativeExposure(option("exposureclip").getDouble(), 0.95);		
		}

		// Noise clip
		if(hasOption("noiseclip")) {
			if(verbose) System.err.print("(noiseclip) ");
			map.clipAboveRelativeRMS(option("noiseclip").getDouble(), 0.05);		
		}

		
		if(hasOption("clip") && enableBias) {	
			double clipLevel = option("clip").getDouble();
			if(verbose) System.err.print("(clip:" + clipLevel + ") ");
			map.s2nClipBelow(clipLevel);
		}

		// Fill the map with zeroes at the flagged locations, s.t. it can be directly
		// subtracted from the data...
		map.sanitize();

		Configurator sourceOption = option("source");
		if(sourceOption.isConfigured("mem")) {
			if(verbose) System.err.print("(MEM) ");
			double lambda = sourceOption.isConfigured("mem.lambda") ? sourceOption.get("mem.lambda").getDouble() : 0.1;
			map.MEM(lambda, true);
		}

		if(sourceOption.isConfigured("intermediates")) {
			map.fileName = "intermediate.fits";
			try { map.write(); }
			catch(Exception e) { e.printStackTrace(); }
		}

		// Coupled with blanking...
		if(!sourceOption.isConfigured("nosync")) {
			if(hasOption("blank") && enableBias) {
				if(verbose) System.err.print("(blank:" + blankingLevel + ") ");
				setMask(map.getMask(getBlankingLevel(), 3));
			}
			else map.getMask(Double.NaN, 3);
		}
		
		
		
		isReady = true;
		
		// Run the garbage collector
		System.gc();
	}


	public void clearMask() {
		if(mask == null) return;
		map.new Task<Void>() {
			@Override
			public void processX(int i) { Arrays.fill(mask[i], false); }
			@Override
			public void process(int i, int j) {}
		}.process();
	}
	
	public void setMask(boolean[][] mask) {
		this.mask = mask;
	}
	
	public void addMask(final boolean[][] m) {
		if(mask == null) mask = m;
		map.new Task<Void>() {
			@Override
			public void process(int i, int j) { mask[i][j] |= m[i][j]; }
		}.process();
	}
	
	public void copyMask(final boolean[][] m) {
		if(mask == null) createMask();
		map.new Task<Void>() {
			@Override
			public void processX(int i) { System.arraycopy(m[i], 0, mask[i], 0, m[i].length); }
			@Override
			public void process(int i, int j) {}
		}.process();
	}
	
	public void createMask() {
		mask = new boolean[map.sizeX()][map.sizeY()];
	}
	
	public boolean[][] getMask() { return mask; }

	public final boolean isMasked(final int i, final int j) { return mask[i][j]; }
	
	@Override
	public final boolean isMasked(Index2D index) {
		return mask[index.i()][index.j()];
	}
	
	@Override
	public synchronized void setBase() { map.copyTo(base); }

	@Override
	public synchronized void reset(boolean clearContent) {
		super.reset(clearContent);
		map.reset(clearContent);
	}
	
	@Override
	protected void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain, final double dt, final int excludeSamples) {
		// The use of iterables is a minor performance hit only (~3% overall)
		for(final Channel channel : pixel) if((exposure.sampleFlag[channel.index] & excludeSamples) == 0) 	
			addPoint(index, channel, exposure, fGC * sourceGain[channel.index], dt);
	}
	
	protected void addPoint(final Index2D index, final Channel channel, final Frame exposure, final double G, final double dt) {		
		map.addPointAt(index.i(), index.j(), exposure.data[channel.index], G, exposure.relativeWeight/channel.variance, dt);
	}
	
	@Override
	public final void getIndex(final Frame exposure, final Pixel pixel, final CelestialProjector projector, final Index2D index) {
		if(exposure.sourceIndex == null) {
			exposure.project(pixel.getPosition(), projector);
			map.getIndex(projector.offset, index);
		}
		else {
			int linearIndex = exposure.sourceIndex[pixel.getIndex()];
			index.set(linearIndex % map.sizeX(), linearIndex / map.sizeX());
		}
	}

	
	
	public void createLookup(Integration<?,?> integration) {	
		final CelestialProjector projector = new CelestialProjector(getProjection());
		final Index2D index = new Index2D();
		final Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels();
		final int n = integration.instrument.getPixelCount();
		final Vector2D offset = projector.offset;
		
		for(final Frame exposure : integration) if(exposure != null) {
			exposure.sourceIndex = new int[n];
			
			for(final Pixel pixel : pixels) {
				exposure.project(pixel.getPosition(), projector);
				map.getIndex(offset, index);
				exposure.sourceIndex[pixel.getIndex()] = map.sizeX() * index.j() + index.i();
			}
		}
	}

	
	@Override
	protected synchronized int add(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, double filtering, int signalMode) {
		int goodFrames = super.add(integration, pixels, sourceGain, filtering, signalMode);
		map.integrationTime += goodFrames * integration.instrument.samplingInterval;
		return goodFrames;
	}
	
	protected double getIncrement(final Index2D index, final Channel channel, final double oldG, final double G) {
		return G * map.getValue(index.i(), index.j()) - oldG * base[index.i()][index.j()];	
	}
	
	@Override
	protected void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, final double[] syncGain, final boolean isMasked) {
		// The use of iterables is a minor performance hit only (~3% overall)
		for(final Channel channel : pixel) if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SKIP) == 0) {
			// Do not check for flags, to get a true difference image...
			exposure.data[channel.index] -= getIncrement(index, channel, 
					fG * syncGain[channel.index], fG * sourceGain[channel.index]);
			// Do the blanking here...
			if(isMasked) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SOURCE_BLANK;
			else exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SOURCE_BLANK;
		}
	}

	@Override
	protected void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain) {
		final CelestialProjector projector = new CelestialProjector(getProjection());
		final Index2D index = new Index2D();

		final double[] sumIM = new double[sourceGain.length];
		final double[] sumM2 = new double[sourceGain.length];
				
		for(final Frame exposure : integration) if(exposure != null) {
			final double fG = integration.gain * exposure.getSourceGain(signalMode); 
				
			// Remove source from all but the blind channels...
			for(final Pixel pixel : pixels)  {
				getIndex(exposure, pixel, projector, index);
				final int i = index.i();
				final int j = index.j();
				
				// The use of iterables is a minor performance hit only (~3% overall)
				if(isMasked(index)) for(final Channel channel : pixel) {
					final int c = channel.index;
					if((exposure.sampleFlag[c] & Frame.SAMPLE_SKIP) == 0) {
						final double mapValue = fG * sourceGain[c] * map.getValue(i, j);
						final double value = exposure.data[c] + fG * syncGain[c] * base[i][j];
						sumIM[c] += exposure.relativeWeight * value * mapValue;
						sumM2[c] += exposure.relativeWeight * mapValue * mapValue;			
					}
				}
			}	    
		}
		
		// Apply a globally neutral coupling correction...
		final float[] data = new float[integration.instrument.size()];
		int n=0;
		for(final Pixel pixel : pixels) for(final Channel channel : pixel) if(sumM2[channel.index] > 0.0)
			data[n++] = (float) (sumIM[channel.index] / sumM2[channel.index]);
	
		if(n > 0) {
			double ave = Statistics.median(data, 0, n);
			for(final Pixel pixel : pixels) for(final Channel channel : pixel) if(sumM2[channel.index] > 0.0)
				channel.coupling *= sumIM[channel.index] / sumM2[channel.index] / ave;
		}
	
		// If the coupling falls out of range, then revert to the default of 1.0	
		if(hasOption("source.coupling.range")) {
			Range range = option("source.coupling.range").getRange();
			for(final Pixel pixel : pixels) for(final Channel channel : pixel) if(channel.isUnflagged())
				if(!range.contains(channel.coupling)) channel.coupling = 1.0;
		}
			
	}
	
	@Override
	public double covariantPoints() {
		return map.getPointsPerSmoothingBeam();		
	}

	@Override
	public int countPoints() {
		return map.countPoints();
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public void write(String path) throws Exception {
		write(path, true);
	}

	public String getCoreName() {
		if(hasOption("name")) {
			String fileName = option("name").getPath();
			if(fileName.toLowerCase().endsWith(".fits")) return fileName.substring(0, fileName.length()-5);
			else return fileName;
		}
		else return getDefaultCoreName();
	}
	
	@Override
	public boolean isValid() {
		return !isEmpty();
	}
	
	public void write(String path, boolean info) throws Exception {		
		// Remove the intermediate image file...
		File intermediate = new File(path + File.separator + "intermediate." + id + ".fits");
		if(intermediate.exists()) intermediate.delete();
		
		String idExt = "";
		if(id != null) if(id.length() > 0) idExt = "." + id;

		map.fileName = path + File.separator + getCoreName() + idExt + ".fits";
		
		if(!isReady) {
			System.err.println(" WARNING! Source" + idExt + " is empty. Skipping");
			File file = new File(map.fileName);
			if(file.exists()) file.delete();
			return;
		}
		
		// Re-level and weight map if allowed and not 'extended' or 'deep'.
		if(!hasOption("extended") || hasOption("deep")) {
			if(enableLevel) map.level(true);
			if(enableWeighting) map.reweight(true);
		}

		if(info) map.toString();
		map.write(); 
		
		
		if(hasOption("write.png")) {
			int width = 300;
			int height = 300;
			
			if(hasOption("write.png.size")) {
				StringTokenizer tokens = new StringTokenizer(option("write.png.size").getValue(), ",x ");
				width = Integer.parseInt(tokens.nextToken());
				height = tokens.hasMoreTokens() ? Integer.parseInt(tokens.nextToken()) : width;
			}
			AstroMap thumbnail = (AstroMap) map.copy(true);
			thumbnail.autoCrop();
			
			// Smooth thumbnail by half a beam for nicer appearance
			thumbnail.smoothTo(0.5 * getInstrument().resolution);
			
			GridImage<?> plane = thumbnail;
			
			if(hasOption("write.png.plane")) {
				String spec = option("write.png.plane").getValue().toLowerCase();
				if(spec.equals("s2n")) plane = thumbnail.getS2NImage();
				else if(spec.equals("s/n")) plane = thumbnail.getS2NImage();
				else if(spec.equals("time")) plane = thumbnail.getTimeImage();
				else if(spec.equals("noise")) plane = thumbnail.getRMSImage();
				else if(spec.equals("rms")) plane = thumbnail.getRMSImage();
				else if(spec.equals("weight")) plane = thumbnail.getWeightImage();
			}
			
			final ImageArea<Data2DLayer> imager = new ImageArea<Data2DLayer>();
			final Data2DLayer image = new Data2DLayer(plane);
			
			if(hasOption("write.png.spline")) image.setSpline();
	
			imager.setContentLayer(image);
			imager.setBackground(Color.LIGHT_GRAY);
			imager.setOpaque(true);
			
			ColorScheme scheme = new Colorful();
			
			if(hasOption("write.png.bg")) {
				String spec = option("write.png.bg").getValue().toLowerCase();
				if(spec.equals("transparent")) imager.setOpaque(false);
				else {
					try { imager.setBackground(new Color(Integer.decode(spec))); }
					catch(NumberFormatException e) { imager.setBackground(Color.getColor(spec)); }
				}
			}
			
			if(hasOption("write.png.color")) {
				String schemeName = option("write.png.color").getValue();
				if(ColorScheme.schemes.containsKey(schemeName)) 
					scheme = ColorScheme.getInstanceFor(schemeName);
			}
				
			image.setColorScheme(scheme);
			imager.setSize(width, height);
			imager.saveAs(map.fileName + ".png", width, height);	
		}
		System.err.println();
	}
	
	@Override
	public String getSourceName() {
		return map.getName();
	}

	@Override
	public Unit getUnit() {
		return map.getUnit();
	}

	@Override
	public void noParallel() {
		map.noParallel();
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {	
		if(name.startsWith("map.")) return map.getFormattedEntry(name.substring(4), formatSpec);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
}
