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

import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

import crush.*;
import crush.astro.AstroMap;
import jnum.Configurator;
import jnum.LockedException;
import jnum.Parallel;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroProjector;
import jnum.astro.SourceCatalog;
import jnum.data.*;
import jnum.math.Range;
import jnum.math.SphericalCoordinates;
import jnum.plot.BufferedImageLayer;
import jnum.plot.ColorScheme;
import jnum.plot.GridImageLayer;
import jnum.plot.ImageArea;
import jnum.plot.colorscheme.Colorful;
import jnum.projection.Projection2D;



public class ScalarMap extends SourceMap {
    /**
     * 
     */
    private static final long serialVersionUID = 224617446250418317L;

    public AstroMap map;

    protected Data2D base; 
    private boolean[][] mask;

    public boolean enableWeighting = true;
    public boolean enableLevel = true;
    public boolean enableBias = true;	

    protected boolean isNormalized = false;

    public ScalarMap(Instrument<?> instrument) {
        super(instrument);
    }

    @Override
    public void setInstrument(Instrument<?> instrument) {
        super.setInstrument(instrument);
        if(map != null) map.setInstrument(instrument);
    }

    /*
	@Override
	public void setExecutor(ExecutorService executor) {
		map.setExecutor(executor);
	}

	@Override
	public ExecutorService getExecutor() {
		return map.getExecutor();
	}
     */

    @Override
    public void add(SourceModel model, double weight) {
        ScalarMap other = (ScalarMap) model;
        isReady = false;

        map.addWeightedDirect(other.map, weight);
        isNormalized = false;

        enableLevel &= other.enableLevel;
        enableWeighting &= other.enableWeighting;
        enableBias &= other.enableBias;

        generation = Math.max(generation, other.generation);
    }

    @Override
    public void addNonZero(SourceMap other) {
        map.mergeNonZero(((ScalarMap) other).map);
    }


    @Override
    public SourceModel getWorkingCopy(boolean withContents) {
        ScalarMap copy = (ScalarMap) super.getWorkingCopy(withContents);

        try { copy.map = (AstroMap) map.copy(withContents); }
        catch(OutOfMemoryError e) { 
            runtimeMemoryError("Ran out of memory while making a copy of the source map.");
        }

        return copy;
    }

    @Override
    public SphericalCoordinates getReference() {
        return map.getReference();
    }

    public void standalone() {
        base = new Data2D(map.sizeX(), map.sizeY());
        createMask();
    }

    @Override
    public Grid2D<?> getGrid() { return map.getGrid(); }

    @Override
    public void createFrom(Collection<? extends Scan<?,?>> collection) {
        map = new AstroMap();
        map.setGrid(new SphericalGrid());

        super.createFrom(collection);

        Scan<?,?> firstScan = scans.get(0);	
        for(Scan<?,?> scan : scans) map.scans.add(scan);	

        if(hasOption("unit")) map.setUnit(option("unit").getValue());

        map.setInstrument(getInstrument());
        map.setParallel(CRUSH.maxThreads);
        map.creator = CRUSH.class.getSimpleName();
        map.setName(firstScan.getSourceName());
        map.setUnderlyingBeam(getAverageResolution());
        map.printShortInfo();

        base = new Data2D(map.sizeX(), map.sizeY());
        createMask();


        if(hasOption("sources")) {
            try { 
                SourceCatalog<SphericalCoordinates> catalog = new SourceCatalog<SphericalCoordinates>();
                catalog.read(option("sources").getPath(), map); 
                try { insertSources(catalog); }
                catch(Exception e) {
                    warning("Source insertion error: " + e.getMessage());
                    if(CRUSH.debug) CRUSH.trace(e);
                }
            }
            catch(IOException e) {
                warning("Cannot read sources: " + e.getMessage());
                if(CRUSH.debug) CRUSH.trace(e);
            }	
        }

        // TODO Apply mask to data either via flag.inside or flag.outside + mask file.

        if(hasSourceOption("inject")) {
            try { injectSource(sourceOption("inject").getPath()); }
            catch(Exception e) { 
                warning("Cannot read injection map. Check the file name and path."); 
                if(CRUSH.debug) CRUSH.trace(e);
            }
        }

        if(hasSourceOption("model")) {
            try { applyModel(sourceOption("model").getPath()); }
            catch(Exception e) { 
                warning("Cannot read source model. Check the file name and path."); 
                if(CRUSH.debug) CRUSH.trace(e);
            }
        }



    }

    public void insertSources(SourceCatalog<SphericalCoordinates> catalog) throws Exception {
        // Since the synching step is removal, the sources should be inserted with a negative sign to add into the
        // timestream.
        double resolution = getAverageResolution();

        for(GaussianSource<?> source : catalog) if(source.getRadius().value() < resolution) {
            info("! Source '" + source.getID() + "' FWHM increased to match map resolution.");
            source.setRadius(resolution);
        }

        catalog.remove(map);

        info("Inserting test sources into data.");

        for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) {
            sync(integration);
            integration.sourceGeneration=0;
        }

        map.reset(true);
    }

    public void applyModel(String fileName) throws Exception {
        info("Applying source model:");

        AstroMap model = new AstroMap(fileName, getInstrument());

        /*
		double renorm = map.getImageBeamArea() / model.getImageBeamArea();
		if(renorm != 1.0) {
			CRUSH.detail(this " --> Rescaling model to instrument resolution: " + Util.s3.format(renorm) + "x");
			model.scale(renorm);
		}
         */

        model.regridTo(map);

        map.generation = 1;
        map.sanitize();

        isReady = true;

        double blankingLevel = getBlankingLevel();
        if(!Double.isNaN(blankingLevel)) CRUSH.detail(this, "Blanking positions above " + Util.f2.format(blankingLevel) + " sigma in source model.");

        CRUSH.detail(this, "Removing model from the data.");
        try { super.sync(); }
        catch(Exception e) { error(e); }

        // For testing the removal of the model...
        //for(int i=0; i<map.sizeX(); i++) Arrays.fill(base[i], 0.0);
    }

    public void injectSource(String fileName) throws Exception {
        info("Injecting source structure.");

        AstroMap model = new AstroMap(fileName, getInstrument());

        /*
		double renorm = map.getImageBeamArea() / model.getImageBeamArea();
		if(renorm != 1.0) {
			CRUSH.detail(this, " --> Rescaling model to instrument resolution: " + Util.s3.format(renorm) + "x");
			model.scale(renorm);
		}
         */

        model.regridTo(map);

        double scaling = hasOption("source.inject.scale") ? option("source.inject.scale").getDouble() : 1.0;

        map.sanitize();
        map.scale(-scaling);

        isReady = true;

        CRUSH.detail(this, "Injecting source map into timestream data. ");
        try { super.sync(); }
        catch(Exception e) { error(e); }

        map.reset(true);
        base.clear();
    }




    // 3 double maps (signal, weight, integrationTime), one int (flag) and one boolean (maks)
    @Override
    public double getPixelFootprint() { return 8*3 + 4 + 1.0/8.0; }

    @Override
    public long baseFootprint(long pixels) { return 8L * pixels; }

    @Override
    public void setSize(int sizeX, int sizeY) {
        map.setSize(sizeX, sizeY);
    }

    @Override
    public int sizeX() { return map.sizeX(); }

    @Override
    public int sizeY() { return map.sizeY(); }

    @Override
    public Projection2D<SphericalCoordinates> getProjection() { return map.getProjection(); }

    @Override
    public void setProjection(Projection2D<SphericalCoordinates> projection) {
        if(map != null) map.setProjection(projection);
    }


    @Override
    public void process(Scan<?,?> scan) {			
        if(!isNormalized) {
            map.normalize();
            isNormalized = true;
        }

        if(base != null) map.addImage(base.getData());

        if(enableLevel && scan.getSourceGeneration() == 0) map.level(true);
        else enableLevel = false;	

        if(hasSourceOption("despike")) {
            Configurator despike = sourceOption("despike");
            double level = 10.0;

            try { despike.mapValueTo("level"); }
            catch(LockedException e) {} // TODO...

            if(despike.isConfigured("level")) level = despike.get("level").getDouble();
            map.despike(level);
        }

        filter(false);
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
            catch(Exception e) { error(e); }
        }

        isReady = true;
    }	

    @Override
    public void postprocess(Scan<?,?> scan) {
        super.postprocess(scan);

        if(countPoints() == 0) return;

        if(hasOption("pointing")) if(option("pointing").equals("auto") || option("pointing").equals("suggest")) {
            double optimal = hasOption("smooth.optimal") ? 
                    option("smooth.optimal").getDouble() * scan.instrument.getSizeUnitValue() :
                        scan.instrument.getPointSize();

                    map.smoothTo(optimal);
                    if(hasOption("pointing.exposureclip")) map.clipBelowRelativeExposure(option("pointing.exposureclip").getDouble(), 0.1);
                    map.reweight(true);
                    scan.pointing = getPeakSource();
        }
    }

    public void filter(boolean allowBlanking) {
        if(!hasSourceOption("filter") || getSourceSize() <= 0.0) {
            map.noExtFilter();
            map.filterBlanking = Double.NaN;
            return;
        }

        Configurator filter = sourceOption("filter");

        try { filter.mapValueTo("fwhm"); }
        catch(LockedException e) {} // TODO...

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
                5.0 * getSourceSize() : Double.parseDouble(directive) * getInstrument().getSizeUnitValue();

                double filterBlanking = (allowBlanking && filter.isConfigured("blank")) ? filter.get("blank").getDouble() : Double.NaN;

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
    public void process() {	

        if(!isNormalized) {
            map.normalize();
            isNormalized = true;
        }
        map.generation++; // Increment the map generation...

        map.getInstrument().setResolution(getAverageResolution());

        double blankingLevel = getBlankingLevel();

        if(enableLevel) addProcessBrief("{level} ");

        if(hasSourceOption("despike")) addProcessBrief("{despike} ");

        if(hasSourceOption("filter") && getSourceSize() > 0.0) addProcessBrief("{filter} ");

        if(enableWeighting) if(hasOption("weighting.scans"))
            for(Scan<?,?> scan : scans) addProcessBrief("{" + Util.f2.format(scan.weight) + "} ");

        if(hasSourceOption("redundancy"))  {
            addProcessBrief("(check) ");
            double minIntTime = getInstrument().integrationTime * sourceOption("redundancy").getInt();
            if(minIntTime > 0.0) map.clipBelowExposure(minIntTime);
        }

        if(hasOption("smooth") && !hasOption("smooth.external")) {
            addProcessBrief("(smooth) ");
            map.smoothTo(getSmoothing());
        }

        if(hasOption("smooth.weights")) {
            AstroMap copy = (AstroMap) map.copy(true);
            copy.smooth(getSmoothing(option("smooth.weights").getValue()));
            map.setWeight(copy.getWeight());
        }

        // Apply the filtering to the final map, to reflect the correct blanking
        // level...
        if(hasSourceOption("filter")) addProcessBrief("(filter) ");

        filter(true);
        map.filterCorrect();

        // Noise and exposure clip after smoothing for evened-out coverage...
        // Eposure clip
        if(hasOption("exposureclip")) {
            addProcessBrief("(exposureclip) ");
            map.clipBelowRelativeExposure(option("exposureclip").getDouble(), 0.95);		
        }

        // Noise clip
        if(hasOption("noiseclip")) {
            addProcessBrief("(noiseclip) ");
            map.clipAboveRelativeRMS(option("noiseclip").getDouble(), 0.05);		
        }


        if(hasOption("clip") && enableBias) {	
            double clipLevel = option("clip").getDouble();
            addProcessBrief("(clip:" + clipLevel + ") ");
            final int sign = hasSourceOption("sign") ? sourceOption("sign").getSign() : 0;
            map.s2nClipBelow(clipLevel, sign);
        }

        // Fill the map with zeroes at the flagged locations, s.t. it can be directly
        // subtracted from the data...
        map.sanitize();

        if(hasSourceOption("mem")) {
            addProcessBrief("(MEM) ");
            double lambda = hasSourceOption("mem.lambda") ? sourceOption("mem.lambda").getDouble() : 0.1;
            map.MEM(lambda, true);
        }

        if(hasSourceOption("intermediates")) {
            map.fileName = "intermediate.fits";
            try { map.write(); }
            catch(Exception e) { error(e); }
        }

        // Coupled with blanking...
        if(!hasSourceOption("nosync")) {
            if(hasOption("blank") && enableBias) {
                addProcessBrief("(blank:" + blankingLevel + ") ");
                final int sign = hasSourceOption("sign") ? sourceOption("sign").getSign() : 0;
                setMask(map.getMask(getBlankingLevel(), 3, sign));
            }
            else map.getMask(Double.NaN, 3, 0);
        }

        isReady = true;

        // Run the garbage collector
        //System.gc();
    }


    public void clearMask() {
        if(mask == null) return;
        map.new Task<Void>() {
            @Override
            protected void processX(int i) { Arrays.fill(mask[i], false); }
            @Override
            protected void process(int i, int j) {}
        }.process();
    }

    public void setMask(boolean[][] mask) {
        this.mask = mask;
    }

    public void addMask(final boolean[][] m) {
        if(mask == null) mask = m;
        map.new Task<Void>() {
            @Override
            protected void process(int i, int j) { mask[i][j] |= m[i][j]; }
        }.process();
    }

    public void copyMask(final boolean[][] m) {
        if(mask == null) createMask();
        map.new Task<Void>() {
            @Override
            protected void processX(int i) { System.arraycopy(m[i], 0, mask[i], 0, m[i].length); }
            @Override
            protected void process(int i, int j) {}
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
    public void setBase() { map.copyTo(base.getData()); }

    @Override
    public void reset(boolean clearContent) {
        super.reset(clearContent);
        map.reset(clearContent);
    }

    @Override
    protected void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain) {
        // The use of iterables is a minor performance hit only (~3% overall)
        for(final Channel channel : pixel) if((exposure.sampleFlag[channel.index] & excludeSamples) == 0)	
            addPoint(index, channel, exposure, fGC * sourceGain[channel.index]);
    }

    protected final void addPoint(final Index2D index, final Channel channel, final Frame exposure, final double G) {	
        addPoint(index, channel, exposure, G, channel.instrument.samplingInterval);
    }

    protected void addPoint(final Index2D index, final Channel channel, final Frame exposure, final double G, final double dt) {	
        map.addPointAt(index.i(), index.j(), exposure.data[channel.index], G, exposure.relativeWeight / channel.variance, dt);
    }




    @Override
    protected int add(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, double filtering, int signalMode) {
        int goodFrames = super.add(integration, pixels, sourceGain, filtering, signalMode);
        map.integrationTime += goodFrames * integration.instrument.samplingInterval;
        isNormalized = false;
        return goodFrames;
    }



    @Override
    protected void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, final double[] syncGain, final boolean isMasked) {
        // The use of iterables is a minor performance hit only (~3% overall)
        final float mapValue = (float) map.getValue(index);
        final float baseValue = (float) base.getValue(index);

        for(final Channel channel : pixel) {
            // Do not check for flags, to get a true difference image...
            exposure.data[channel.index] -= fG * (sourceGain[channel.index] * mapValue - syncGain[channel.index] * baseValue);	

            // Do the blanking here...
            if(isMasked) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SOURCE_BLANK;
            else exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SOURCE_BLANK;
        }
    }


    @Override
    protected void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain) {

        CRUSH.Fork<DataPoint[]> calcCoupling = integration.new Fork<DataPoint[]>() {
            private AstroProjector projector;
            private Index2D index;
            private DataPoint[] sum;

            @Override
            protected void init() {
                super.init();
                projector = new AstroProjector(getProjection());
                index = new Index2D();
                sum = integration.instrument.getDataPoints();
                for(int i=sum.length; --i >= 0; ) sum[i].noData();
            }

            @Override 
            protected void process(final Frame exposure) {
                final double fG = integration.gain * exposure.getSourceGain(signalMode); 

                // Remove source from all but the blind channels...
                for(final Pixel pixel : pixels)  {
                    ScalarMap.this.getIndex(exposure, pixel, projector, index);
                    final int i = index.i();
                    final int j = index.j();

                    // The use of iterables is a minor performance hit only (~3% overall)
                    if(isMasked(index)) for(final Channel channel : pixel) {
                        final int c = channel.index;
                        if((exposure.sampleFlag[c] & Frame.SAMPLE_SKIP) == 0) {
                            final double mapValue = fG * sourceGain[c] * map.getValue(i, j);
                            final double value = exposure.data[c] + fG * syncGain[c] * base.getValue(i, j);
                            final DataPoint point = sum[c];
                            point.add(exposure.relativeWeight * value * mapValue);
                            point.addWeight(exposure.relativeWeight * mapValue * mapValue);			
                        }
                    }
                }	
            }

            @Override
            public DataPoint[] getLocalResult() { return sum; }

            @Override
            public DataPoint[] getResult() {
                DataPoint[] total = null;
                for(Parallel<DataPoint[]> task : getWorkers()) {
                    DataPoint[] local = task.getLocalResult();
                    if(sum == null) sum = local;
                    else {
                        for(int i=sum.length; --i >= 0; ) {
                            sum[i].add(local[i].value());
                            sum[i].addWeight(local[i].weight());
                        }
                        Instrument.recycle(local);
                    }
                }
                return total;
            }

        };

        calcCoupling.process();

        final DataPoint[] sum = calcCoupling.getResult();

        // Apply a globally neutral coupling correction...
        final float[] data = integration.instrument.getFloats();
        int n=0;
        for(final Channel channel : integration.instrument) if(sum[channel.index].weight() > 0.0) {
            final double dG =  sum[channel.index].significance();
            channel.coupling *= dG;
            data[n++] = (float) dG;
        }

        if(n > 0) {
            double norm = 1.0 / Statistics.median(data, 0, n);
            for(final Channel channel : integration.instrument) if(sum[channel.index].weight() > 0.0)
                channel.coupling *= norm;
        }

        Instrument.recycle(sum);
        Instrument.recycle(data);

        // If the coupling falls out of range, then revert to the default of 1.0	
        if(hasSourceOption("coupling.range")) {
            Range range = sourceOption("coupling.range").getRange();
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
            warning("Source" + idExt + " is empty. Skipping.");
            File file = new File(map.fileName);
            if(file.exists()) file.delete();
            return;
        }

        // Re-level and weight map if allowed and 'deep' or not 'extended'.
        if(!hasOption("extended") || hasOption("deep")) {
            if(enableLevel) map.level(true);
            if(enableWeighting) map.reweight(true);
        }


        if(hasOption("regrid")) map.regrid(option("regrid").getDouble() * getInstrument().getSizeUnitValue());

        if(info) map.toString();
        map.write(); 

        if(hasOption("write.png")) writePNG(option("write.png"), path);
    }

    
    public void writePNG(Configurator option, String path) throws InstantiationException, IllegalAccessException, IOException {
        int width = DEFAULT_PNG_SIZE;
        int height = DEFAULT_PNG_SIZE;

        if(option.isConfigured("size")) {
            StringTokenizer tokens = new StringTokenizer(option.get("size").getValue(), "xX*:, ");
            width = Integer.parseInt(tokens.nextToken());
            height = tokens.hasMoreTokens() ? Integer.parseInt(tokens.nextToken()) : width;
        }
        AstroMap thumbnail = (AstroMap) map.copy(true);

        if(option.isConfigured("crop")) {
            List<Double> offsets = option.get("crop").getDoubles();
            if(offsets.isEmpty()) thumbnail.autoCrop();
            else {
                double sizeUnit = getInstrument().getSizeUnitValue();
                double dXmin = offsets.get(0) * sizeUnit;
                double dYmin = offsets.size() > 0 ? offsets.get(1) * sizeUnit : dXmin;
                double dXmax = offsets.size() > 1 ? offsets.get(2) * sizeUnit : -dXmin;
                double dYmax = offsets.size() > 2 ? offsets.get(3) * sizeUnit : -dYmin;
                thumbnail.crop(dXmin, dYmin, dXmax, dYmax);
            }
        }
        else thumbnail.autoCrop(); 

        // Smooth thumbnail by half a beam for nicer appearance
        if(option.isConfigured("smooth")) {
            String arg = option.get("smooth").getValue();
            double fwhm = arg.length() > 0 ? getSmoothing(arg) : 0.5 * getInstrument().getPointSize();
            thumbnail.smoothTo(fwhm);
        }

        GridImage2D<?> plane = thumbnail;

        if(option.isConfigured("plane")) {
            String spec = option.get("plane").getValue().toLowerCase();
            if(spec.equals("s2n")) plane = thumbnail.getS2NImage();
            else if(spec.equals("s/n")) plane = thumbnail.getS2NImage();
            else if(spec.equals("time")) plane = thumbnail.getTimeImage();
            else if(spec.equals("noise")) plane = thumbnail.getRMSImage();
            else if(spec.equals("rms")) plane = thumbnail.getRMSImage();
            else if(spec.equals("weight")) plane = thumbnail.getWeightImage();
        }

        
        final ImageArea<GridImageLayer> imager = new ImageArea<GridImageLayer>();
        final GridImageLayer image = new GridImageLayer(plane);

        if(option.isConfigured("scaling")) {
            String spec = option.get("scaling").getValue().toLowerCase();
            if(spec.equals("log")) image.setScaling(BufferedImageLayer.SCALE_LOG);
            if(spec.equals("sqrt")) image.setScaling(BufferedImageLayer.SCALE_SQRT);
        }

        if(option.isConfigured("spline")) image.setSpline();

        imager.setContentLayer(image);
        imager.setBackground(Color.LIGHT_GRAY);
        imager.setOpaque(true);

        ColorScheme scheme = new Colorful();

        if(option.isConfigured("bg")) {
            String spec = option.get("bg").getValue().toLowerCase();
            if(spec.equals("transparent")) imager.setOpaque(false);
            else {
                try { imager.setBackground(new Color(Integer.decode(spec))); }
                catch(NumberFormatException e) { imager.setBackground(Color.getColor(spec)); }
            }
        }

        if(option.isConfigured("color")) {
            String schemeName = option.get("color").getValue();
            if(ColorScheme.schemes.containsKey(schemeName)) 
                scheme = ColorScheme.getInstanceFor(schemeName);
        }

        image.setColorScheme(scheme);
        imager.setSize(width, height);
        imager.saveAs(map.fileName + ".png", width, height);			
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
    public void setParallel(int threads) {
        map.setParallel(threads);
    }

    @Override
    public int getParallel() {
        return map.getParallel();
    }

    @Override
    public String getFormattedEntry(String name, String formatSpec) {	
        if(name.startsWith("map.")) return map.getFormattedEntry(name.substring(4), formatSpec);
        else return super.getFormattedEntry(name, formatSpec);
    }

    @Override
    public void setExecutor(ExecutorService executor) {
        map.setExecutor(executor);
    }

    @Override
    public ExecutorService getExecutor() {
        return map.getExecutor();
    }


    public static final int DEFAULT_PNG_SIZE = 300; 
}
