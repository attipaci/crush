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

import java.awt.Color;
import java.io.*;
import java.util.*;

import crush.*;
import crush.gui.AstroImageLayer;
import util.*;
import util.astro.CelestialProjector;
import util.astro.EclipticCoordinates;
import util.astro.GalacticCoordinates;
import util.astro.SourceCatalog;
import util.data.Data2D;
import util.data.Index2D;
import util.data.Statistics;
import util.plot.ColorScheme;
import util.plot.ImageArea;
import util.plot.colorscheme.Colorful;


public class ScalarMap<InstrumentType extends Instrument<?>, ScanType extends Scan<? extends InstrumentType,?>> extends SourceMap<InstrumentType, ScanType> {
	public AstroMap map;
	public double[][] base; 
	public boolean[][] mask;
	public boolean isLevelled = true;
	public boolean allowBias = true;
	public int signalMode = Frame.TOTAL_POWER;
	
	public ScalarMap(InstrumentType instrument) {
		super(instrument);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void add(SourceModel<?, ?> model, double weight) {
		ScalarMap<InstrumentType, ScanType> other = (ScalarMap<InstrumentType, ScanType>) model;
		isValid = false;
		map.addDirect(other.map, weight);
		if(!other.isLevelled) isLevelled = false;
		generation = Math.max(generation, other.generation);
	}


	@Override
	public SourceModel<InstrumentType, ScanType> copy() {
		ScalarMap<InstrumentType, ScanType> copy = (ScalarMap<InstrumentType, ScanType>) super.copy();
		try { copy.map = (AstroMap) map.copy(); }
		catch(OutOfMemoryError e) {
			System.err.println("ERROR! Ran of of memory while making a copy of the source map.");
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
		mask = new boolean[map.sizeX()][map.sizeY()];
	}

	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		super.createFrom(collection);
		
		map = new AstroMap();
		
		double gridSize = instrument.resolution / 5.0;
		if(hasOption("grid")) gridSize = option("grid").getDouble() * Unit.arcsec;

		ScanType firstScan = scans.get(0);
		
		for(Scan<?,?> scan : scans) map.scans.add(scan);	
		
		map.creator = CRUSH.class.getSimpleName();
		map.creatorVersion = CRUSH.getFullVersion();
		map.sourceName = firstScan.sourceName;
		map.commandLine = commandLine;
		map.instrument = (Instrument<?>) instrument.copy();
		map.correctingFWHM = map.getImageFWHM();	
		
		if(hasOption("unit")) map.setUnit(option("unit").getValue());
		
		String system = hasOption("system") ? option("system").getValue().toLowerCase() : "equatorial";
		
		if(system.equals("horizontal")) projection.setReference(firstScan.horizontal);
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
	
		map.grid.projection = projection;
		map.setResolution(gridSize);
		
		setSize();

		// Make the reference fall on pixel boundaries.
		map.grid.refIndex.x = 0.5 - Math.rint(xRange.min/gridSize);
		map.grid.refIndex.y = 0.5 - Math.rint(yRange.min/gridSize);
			
		map.printShortInfo();		
		
		base = new double[map.sizeX()][map.sizeY()];
		mask = new boolean[map.sizeX()][map.sizeY()];
		
		if(hasOption("indexing")) index();
		
		if(hasOption("sources")) {
			try { 
				SourceCatalog catalog = new SourceCatalog();
				catalog.read(Util.getSystemPath(option("sources").getValue()), map); 
				insertSources(catalog);
				map.reset();
			}
			catch(IOException e) {
				System.err.println("WARNING! Cannot read sources: " + e.getMessage());
			}	
		}
		
		if(hasOption("source.model")) {
			try { applyModel(option("source.model").getValue()); }
			catch(Exception e) { 
				System.err.println("WARNING! Cannot read source model. Check the file name and path."); 
				e.printStackTrace();
			}
		}
		
	}
	
	public void insertSources(SourceCatalog catalog) {
		catalog.remove(map);
		for(GaussianSource source : catalog) source.peak.scale(-1.0);
		
		System.err.println(" Inserting test sources into data.");
			
		Parallel<Integration<?,?>> sync = new Parallel<Integration<?,?>>(CRUSH.maxThreads) {
			@Override
			public void process(Integration<?,?> integration, ProcessingThread thread) {
				sync(integration);
				integration.sourceGeneration=0; // Reset the source generation...
			}
		};
		
		try { sync.process(getIntegrations()); }
		catch(InterruptedException e) { System.err.println("WARNING! Source insertion was interrupted."); }
	}
	
	public void applyModel(String fileName) throws Exception {
		System.err.println(" Applying source model:");
			
		AstroMap model = new AstroMap(fileName, map.instrument);
			
		model.regridTo(map);	
		map.generation = 1;
		map.sanitize();
		isValid = true;
			
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
		
	public void index() {
		System.err.print(" Indexing maps. ");
	
		Parallel<Integration<?,?>> indexing = new Parallel<Integration<?,?>>(CRUSH.maxThreads) {
			Runtime runtime;
			long maxUsed;
			boolean saturated = false;
			
			@Override
			public void init() {
				double maxUsage = hasOption("indexing.saturation") ? option("indexing.saturation").getDouble() : 0.5;
				System.err.println(" (Up to " + Util.d1.format(100.0*maxUsage) + "% of RAM saturation.)");
				
				runtime = Runtime.getRuntime();
				long maxAvailable = runtime.maxMemory() - getReductionFootprint();
				maxUsed = (long) (maxUsage * maxAvailable);
			}
			
			@Override
			public void process(final Integration<?,?> integration, ProcessingThread thread) {	
				if(saturated) return;
				
				if(runtime.totalMemory() - runtime.freeMemory() >= maxUsed) saturated = true;
				else createLookup(integration);	
			}
		};
	
		try { indexing.process(getIntegrations()); }
		catch(InterruptedException e) { System.err.println(" WARNING! Indexing was interrupted..."); }
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
	public SphericalProjection getProjection() { return map.getProjection(); }
	
	@Override
	public void process(Scan<?,?> scan) {
		map.normalize();
		if(base != null) map.addImage(base);
		
		double minIntTime = instrument.integrationTime * (hasOption("source.redundancy") ? option("source.redundancy").getInt() : 0);
		if(minIntTime > 0.0) map.clipBelowExposure(minIntTime);
		
		if(isLevelled && scan.getSourceGeneration() == 0) map.level(true);
		else isLevelled = false;
		
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
			map.fileName = CRUSH.workPath + "/scan-" + (int)scan.MJD + "-" + scan.getID() + ".fits";
			if(hasOption("unit")) map.setUnit(option("unit").getValue());
			try { map.write(); }
			catch(Exception e) { e.printStackTrace(); }
		}
		
		isValid = true;
	}	
	
	@Override
	public void postprocess(Scan<?,?> scan) {
		super.postprocess(scan);
		
		if(hasOption("pointing")) if(option("pointing").equals("auto") || option("pointing").equals("suggest")) {
			map.smoothTo(scan.instrument.resolution);
			if(hasOption("pointing.exposureclip")) map.clipBelowRelativeExposure(option("pointing.exposureclip").getDouble(), 0.1);
			map.weight(true);
			scan.pointing = getPeakSource();
		}
	}
	
	public void filter() {
		if(!hasOption("source.filter") || getSourceSize(instrument) <= 0.0) {
			map.extFilterFWHM = Double.NaN;
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
			if(spec.equals("nearest")) map.interpolationType = Data2D.NEAREST_NEIGHBOR;
			else if(spec.equals("linear")) map.interpolationType = Data2D.BILINEAR;
			else if(spec.equals("quadratic")) map.interpolationType = Data2D.PIECEWISE_QUADRATIC;
			else if(spec.equals("cubic")) map.interpolationType = Data2D.BICUBIC_SPLINE;
			// And alternative names...
			else if(spec.equals("none")) map.interpolationType = Data2D.NEAREST_NEIGHBOR;
			else if(spec.equals("bilinear")) map.interpolationType = Data2D.BILINEAR;
			else if(spec.equals("piecewise")) map.interpolationType = Data2D.PIECEWISE_QUADRATIC;
			else if(spec.equals("bicubic")) map.interpolationType = Data2D.BICUBIC_SPLINE;
			else if(spec.equals("spline")) map.interpolationType = Data2D.BICUBIC_SPLINE;
		}
		
		if(filter.isConfigured("fwhm")) directive = filter.get("fwhm").getValue().toLowerCase();
		
		double filterScale = directive.equals("auto") ? 
				5.0 * getSourceSize(instrument) : Double.parseDouble(directive) * instrument.getDefaultSizeUnit();
			
		double filterBlanking = filter.isConfigured("blank") ? filter.get("blank").getDouble() : Double.NaN;

		if(mode.equalsIgnoreCase("fft")) map.fftFilterAbove(filterScale, filterBlanking);
		else map.filterAbove(filterScale, filterBlanking);
			
		map.extFilterFWHM = filterScale;
		map.filterBlanking = filterBlanking;
	}
	
	public GaussianSource getPeakSource() {
		EllipticalSource source = new EllipticalSource();
		source.setPeak(map);	

		// Rescale the peak to an equivalent unsmoothed value...
		source.peak.scale(map.getImageBeamArea() / map.getInstrumentBeamArea());
		
		// Alternative is to use the centroid around that peak...
		if(hasOption("pointing.method")) if(option("pointing.method").equals("centroid")) source.centroid(map);	
		
		double criticalS2N = hasOption("pointing.significance") ? option("pointing.significance").getDouble() : 5.0;
		if(source.peak.significance() < criticalS2N) return null;
		
		// Finally, calculate the FWHM from the observed beam spread...
		source.measureShape(map);
		
		return source;
	}
	
	
	@Override
	public synchronized void sync() throws InterruptedException {	
		process(true);
		super.sync();	
	}
	
	public synchronized void process(boolean verbose) {
		map.normalize();
		map.generation++; // Increment the map generation...
		
		double blankingLevel = getBlankingLevel();
		
		if(hasOption("source.redundancy")) System.err.print("{check} ");
		
		if(verbose) if(isLevelled) System.err.print("{level} ");

		if(verbose) if(hasOption("source.despike")) System.err.print("{despike} ");

		if(hasOption("source.filter") && getSourceSize(instrument) > 0.0) {
			if(verbose) System.err.print("{filter} ");
		}
		
		if(verbose) if(hasOption("weighting.scans"))
			for(Scan<?,?> scan : scans) System.err.print("{" + Util.f2.format(scan.weight) + "} ");


		if(hasOption("smooth") && !hasOption("smooth.external")) {
			if(verbose) System.err.print("(smooth) ");
			map.smoothTo(getSmoothing());
		}

		// Apply the filtering to the final map, to reflect the correct blanking
		// level...
		if(hasOption("source.filter")) if(verbose) System.err.print("(filter) ");
		filter();
		
		
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

		
		if(hasOption("clip") && allowBias) {	
			double clipLevel = option("clip").getDouble();
			if(verbose) System.err.print("(clip:" + clipLevel + ") ");
			map.s2nClipBelow(clipLevel);
			map.clippingS2N = clipLevel;
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
			if(hasOption("blank") && allowBias) {
				if(verbose) System.err.print("(blank:" + blankingLevel + ") ");
				mask = map.getMask(getBlankingLevel(), 3);
			}
		}
		
		isValid = true;
		
		// Run the garbage collector
		System.gc();
	}


	@Override
	public void setBase() {
		for(int i=0; i<map.sizeX(); i++) System.arraycopy(map.data[i], 0, base[i], 0, map.sizeY());
	}

	@Override
	public void reset() {
		super.reset();
		map.reset();
		map.correctingFWHM = map.instrument.resolution;
	}
	
	@Override
	protected void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain, final double dt, final int excludeSamples) {
		// The use of iterables is a minor performance hit only (~3% overall)
		for(final Channel channel : pixel) if((exposure.sampleFlag[channel.index] & excludeSamples) == 0) 	
			addPoint(index, channel, exposure, fGC * sourceGain[channel.index], dt);
	}
	
	protected void addPoint(final Index2D index, final Channel channel, final Frame exposure, final double G, final double dt) {
		map.addPointAt(index.i, index.j, exposure.data[channel.index], G, exposure.relativeWeight/channel.variance, dt);
	}
	
	@Override
	public final void getIndex(final Frame exposure, final Pixel pixel, final CelestialProjector projector, final Index2D index) {
		if(exposure.sourceIndex == null) {
			exposure.project(pixel.getPosition(), projector);
			map.getIndex(projector.offset, index);
		}
		else {
			int linearIndex = exposure.sourceIndex[pixel.getIndex()];
			index.j = linearIndex / map.sizeX();
			index.i = linearIndex % map.sizeX();
		}
	}

	@Override
	public final boolean isMasked(Index2D index) {
		return mask[index.i][index.j];
	}
	
	public void createLookup(Integration<?,?> integration) {	
		final CelestialProjector projector = new CelestialProjector(projection);
		final Index2D index = new Index2D();
		final Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels();
		final int n = integration.instrument.getPixelCount();
		final Vector2D offset = projector.offset;
		
		for(final Frame exposure : integration) if(exposure != null) {
			exposure.sourceIndex = new int[n];
			
			for(final Pixel pixel : pixels) {
				exposure.project(pixel.getPosition(), projector);
				map.getIndex(offset, index);
				exposure.sourceIndex[pixel.getIndex()] = map.sizeX() * index.j + index.i;
			}
		}
	}

	@Override
	public void add(Integration<?,?> integration) {
		add(integration, signalMode);
	}
	
	@Override
	protected int add(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, double filtering, int signalMode) {
		int goodFrames = super.add(integration, pixels, sourceGain, filtering, signalMode);
		map.integrationTime += goodFrames * integration.instrument.samplingInterval;
		return goodFrames;
	}
	
	protected double getIncrement(final Index2D index, final Channel channel, final double oldG, final double G) {
		return G * map.data[index.i][index.j] - oldG * base[index.i][index.j];	
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
		final CelestialProjector projector = new CelestialProjector(projection);
		final Index2D index = new Index2D();

		final double[] sumIM = new double[sourceGain.length];
		final double[] sumM2 = new double[sourceGain.length];
				
		for(final Frame exposure : integration) if(exposure != null) {
			final double fG = integration.gain * exposure.getSourceGain(signalMode); 
				
			// Remove source from all but the blind channels...
			for(final Pixel pixel : pixels)  {
				getIndex(exposure, pixel, projector, index);
				final int i = index.i;
				final int j = index.j;
				
				// The use of iterables is a minor performance hit only (~3% overall)
				if(isMasked(index)) for(final Channel channel : pixel) {
					final int c = channel.index;
					if((exposure.sampleFlag[c] & Frame.SAMPLE_SKIP) == 0) {
						final double mapValue = fG * sourceGain[c] * map.data[i][j];
						final double value = exposure.data[c] + fG * syncGain[c] * base[i][j];;
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

	@Override
	public void write(String path) throws Exception {
		write(path, true);
	}

	public String getCoreName() {
		if(hasOption("name")) {
			String fileName = Util.getSystemPath(option("name").getValue());
			if(fileName.toLowerCase().endsWith(".fits")) return fileName.substring(0, fileName.length()-5);
			else return fileName;
		}
		else return getDefaultCoreName();
	}
	
	public void write(String path, boolean info) throws Exception {		
		// Remove the intermediate image file...
		File intermediate = new File(path + File.separator + "intermediate." + id + ".fits");
		if(intermediate.exists()) intermediate.delete();
		
		String idExt = "";
		if(id != null) if(id.length() > 0) idExt = "." + id;

		map.fileName = path + File.separator + getCoreName() + idExt + ".fits";
		
		if(!isValid) {
			System.err.println(" WARNING! Source" + idExt + " is empty. Skipping");
			File file = new File(map.fileName);
			if(file.exists()) file.delete();
			return;
		}
		
		// Re-level and weight map, unless 'extended' in other than 'deep'.
		if(!hasOption("extended") || hasOption("deep")) {
			map.level(true);
			map.weight(true);
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
			AstroMap cropped = (AstroMap) map.copy();
			cropped.autoCrop();
			
			final ImageArea<AstroImageLayer> imager = new ImageArea<AstroImageLayer>();
			final AstroImageLayer image = new AstroImageLayer(cropped);
			imager.setContentLayer(image);
			
			ColorScheme scheme = new Colorful();
			
			if(hasOption("write.png.bg")) {
				String spec = option("write.png.bg").getValue();
				try { imager.setBackground(new Color(Integer.decode(spec))); }
				catch(NumberFormatException e) { imager.setBackground(Color.getColor(spec)); }
			}
			
			if(hasOption("write.png.color")) {
				String schemeName = option("write.png.color").getValue();
				if(ColorScheme.schemes.containsKey(schemeName)) 
					scheme = ColorScheme.getInstanceFor(schemeName);
			}
				
			imager.setBackground(Color.LIGHT_GRAY);
			image.colorScheme = scheme;
			imager.saveAs(map.fileName + ".png", width, height);	
		}
		
	}

	@Override
	public String getSourceName() {
		return map.sourceName;
	}

	@Override
	public Unit getUnit() {
		return map.unit;
	}
	
}
