/* *****************************************************************************
 * Copyright (c) 2021 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.sourcemodel;



import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.*;

import crush.*;
import jnum.Configurator;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.astro.PrecessingCoordinates;
import jnum.colorscheme.ColorScheme;
import jnum.colorscheme.Colorful;
import jnum.data.Statistics;
import jnum.data.image.Data2D;
import jnum.data.image.Gaussian2D;
import jnum.data.image.Grid2D;
import jnum.data.image.Image2D;
import jnum.data.image.Map2D;
import jnum.data.image.Observation2D;
import jnum.data.image.SphericalGrid;
import jnum.data.index.Index2D;
import jnum.math.Coordinate2D;
import jnum.math.Metric;
import jnum.math.Range;
import jnum.math.Range2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.parallel.ParallelTask;
import jnum.plot.BufferedImageLayer;
import jnum.plot.ImageArea;
import jnum.projection.Projection2D;
import jnum.projection.Projector2D;

import nom.tam.fits.FitsException;


public abstract class AbstractSource2D extends SourceModel {	
    /**
     * 
     */
    private static final long serialVersionUID = -8110425445687949465L;

    private Grid2D<?> grid;
    
    public double smoothing = 0.0;  // TODO eliminate?
   
    public boolean allowIndexing = true;

    private int indexShiftX, indexMaskY;
    
    private Projector2D<?> projectorTemplate;

    
    public AbstractSource2D(Instrument<?> instrument) {
        super(instrument);
        grid = instrument.getGridInstance();
        grid.setResolution(hasOption("grid") ? option("grid").getDouble() * instrument.getSizeUnit().value() : 0.2 * instrument.getResolution());  
    }

    @Override
    public AbstractSource2D copy(boolean withContents) {
        AbstractSource2D copy = (AbstractSource2D) super.copy(withContents);
        if(grid != null) copy.grid = grid.copy();
        return copy;
    }

    public abstract boolean isEmpty();
    
    public abstract int getPixelFootprint();

    public abstract long baseFootprint(int pixels);
    
    public abstract void processFinal();
    
    public abstract void writeFits(String fileName) throws FitsException, IOException;
    
    public abstract Map2D getMap2D();
    
    public abstract void mergeAccumulate(AbstractSource2D other);

    public abstract void setSize(int sizeX, int sizeY);

    public abstract int sizeX();

    public abstract int sizeY();

    public abstract double covariantPoints();

      
    protected abstract void addPoint(final Index2D index, final Channel channel, final Frame exposure, final double G, final double dt);    
    
    protected abstract void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, double[] syncGain);

    protected abstract void calcCoupling(final Integration<?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain);



    public long getMemoryFootprint(int pixels) {
        return pixels * getPixelFootprint() + baseFootprint(pixels);
    }

    public long getReductionFootprint(int pixels) {
        // The composite map + one copy for each thread, plus base image (double)
        return (CRUSH.maxThreads + 1) * getMemoryFootprint(pixels) + baseFootprint(pixels);
    }



    public final int pixels() { return sizeX() * sizeY(); }

    public final Grid2D<?> getGrid() { return grid; }
    
 
    protected boolean isAddingToMaster() { return false; }


    public final Projection2D<?> getProjection() { return grid.getProjection(); }
   
    
    public final void setProjector(Projector2D<?> projector) {
        this.projectorTemplate = projector;
        getGrid().setProjection((Projection2D) projector.getProjection());
    }
   
    public Projector2D<?> getProjectorInstance() { return projectorTemplate.clone(); }

    @Override
    public final Coordinate2D getReference() {
        return getGrid().getReference();
    }


    

    @Override
    public void resetProcessing() {
        super.resetProcessing();
        updateSmoothing();
    }
    
    @Override
    public boolean isValid() {
        return !isEmpty();
    }



    protected String getDefaultFileName() {
        return getSourceName() + ".fits";
    }

    public String getCoreName() {
        if(hasOption("name")) {
            String fileName = option("name").getPath();
            if(fileName.toLowerCase().endsWith(".fits")) return fileName.substring(0, fileName.length()-5);
            return fileName;
        }
        return getDefaultCoreName();
    }


    

    @Override
    public void createFrom(Collection<? extends Scan<?>> collection) throws Exception {
        super.createFrom(collection);
        
        info("Initializing Source Map.");	

        Coordinate2D ref = getFirstScan().getPositionReference(hasOption("system") ? option("system").getValue() : "equatorial");

        setProjector(getInstrument().getProjectorInstance(ref));
        
        setSize();

        if(allowIndexing) if(hasOption("indexing")) {
            try { index(); }
            catch(Exception e) { 
                warning("Indexing error: " + e.getMessage());
                if(CRUSH.debug) CRUSH.trace(e);
            }
        }

    }
    

    public void updateSmoothing() {
        if(!hasOption("smooth")) return;
        setSmoothing(getSmoothing(option("smooth").getValue()));
    }

    public double getRequestedSmoothing(Configurator option) {
        if(option == null) return smoothing;
        if(!option.isEnabled()) return smoothing;
        String spec = option.getValue();	
        if(spec.length() == 0) return smoothing;
        return getSmoothing(spec);
    }

    public double getSmoothing(String spec) {
        double sizeUnit = getInstrument().getSizeUnit().value();
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
  
    public double getPixelizationSmoothing() {
        return Math.sqrt(getGrid().getPixelArea() / Gaussian2D.areaFactor);
    }

    @Override
    public double getPointSize() { return ExtraMath.hypot(getInstrument().getPointSize(), getRequestedSmoothing(option("smooth"))); }

    @Override
    public double getSourceSize() { return ExtraMath.hypot(super.getSourceSize(), getRequestedSmoothing(option("smooth"))); }


    private void flagOutside(final Integration<?> integration, final Vector2D fixedSize) {
        final Instrument<?> instrument = integration.getInstrument();
        final Collection<? extends Pixel> pixels = instrument.getMappingPixels(~instrument.getSourcelessChannelFlags());

        new CRUSH.Fork<Void>(integration.size(), integration.getThreadCount()) {
            private Projector2D<?> projector;

            @Override
            public void init() {
                super.init();
                projector = getProjectorInstance();
            }

            @Override
            protected void processIndex(int t) {
                Frame exposure = integration.get(t);
                if(exposure == null) return;
                boolean valid = false;

                Vector2D offset = projector.getOffset();
                
                for(Pixel pixel : pixels) {
                    exposure.project(pixel.getPosition(), projector);
                    for(Channel channel : pixel) {
                        if(Math.abs(offset.x()) > fixedSize.x()) exposure.sampleFlag[channel.getIndex()] |= Frame.SAMPLE_SKIP;
                        else if(Math.abs(offset.y()) > fixedSize.y()) exposure.sampleFlag[channel.getIndex()] |= Frame.SAMPLE_SKIP;
                        else valid = true;
                    }
                }

                if(!valid) exposure = null;

            }
        }.process();
    }



    public Range2D searchCorners() throws Exception {
        final Vector2D fixedSize = new Vector2D(Double.NaN, Double.NaN);
        final boolean fixSize = hasOption("map.size");
       
        Range2D range = new Range2D();
        
        if(fixSize) {
            StringTokenizer sizes = new StringTokenizer(option("map.size").getValue(), " \t,:xX");

            fixedSize.setX(0.5 * Double.parseDouble(sizes.nextToken()) * Unit.arcsec);
            fixedSize.setY(sizes.hasMoreTokens() ? 0.5 * Double.parseDouble(sizes.nextToken()) * Unit.arcsec : fixedSize.x());

            range.setRange(-fixedSize.x(), -fixedSize.y(), fixedSize.x(), fixedSize.y());	

            for(Scan<?> scan : getScans()) for(Integration<?> integration : scan) flagOutside(integration, fixedSize);
        }

        else {
            range.empty();

            for(Scan<?> scan : getScans()) {
                scan.range = new Range2D();
                   
                for(Integration<?> integration : scan) {
                    Range2D r = integration.searchCorners(integration.getInstrument().getLayout().getPerimeterPixels(), projectorTemplate);
                    if(r != null) scan.range.include(r);
                }
                range.include(scan.range);
            }	       
        }
        
        return range;
    }

    public void index() throws Exception {
        final double maxUsage = hasOption("indexing.saturation") ? option("indexing.saturation").getDouble() : 0.5;
        info("Indexing maps (up to " + Util.d1.format(100.0*maxUsage) + "% of RAM saturation).");

        final Runtime runtime = Runtime.getRuntime();
        long maxAvailable = runtime.maxMemory() - getReductionFootprint(pixels());
        final long maxUsed = (long) (maxUsage * maxAvailable);

        for(Scan<?> scan : getScans()) for(Integration<?> integration : scan) {
            if(runtime.totalMemory() - runtime.freeMemory() >= maxUsed) return;
            createLookup(integration);  
        }
    }


    public void createLookup(Integration<?> integration) {    
        final Instrument<?> instrument = integration.getInstrument();
        final List<? extends Pixel> pixels = instrument.getMappingPixels(~instrument.getSourcelessChannelFlags());
        final int n = integration.getInstrument().getPixelCount();

        if(CRUSH.debug) debug("lookup.pixels " + pixels.size() + " : " + integration.getInstrument().size());

        indexShiftX = ExtraMath.log2ceil(sizeY());
        indexMaskY = (1<<indexShiftX) - 1;

        integration.new Fork<Void>() {
            private Projector2D<?> projector;
            private Index2D index;

            @Override
            protected void init() {
                super.init();
                projector = getProjectorInstance();
                index = new Index2D();
            }

            @Override 
            protected void process(Frame exposure) {  
                exposure.sourceIndex = new int[n];

                if(CRUSH.debug) Arrays.fill(exposure.sourceIndex, -1);

                for(final Pixel pixel : pixels) {
                    exposure.project(pixel.getPosition(), projector);
                    getGrid().getIndex(projector.getOffset(), index);

                    if(CRUSH.debug) {
                        if(index.i() < 0 || index.i() >= sizeX() || index.j() < 0 || index.j() >= sizeY()) {
                            warning("!!! invalid map index pixel " + pixel.getID() + " frame " + exposure.index + ": " +
                                    (exposure.sourceIndex == null ? index : exposure.sourceIndex[pixel.getIndex()])
                            );
                            index.set(0, 0);
                        }
                    }

                    exposure.sourceIndex[pixel.getIndex()] = (index.i() << indexShiftX) | index.j();
                }
            }
        }.process();
    }

    public final void getIndex(final Frame exposure, final Pixel pixel, final Projector2D<?> projector, final Index2D index) {
        if(exposure.sourceIndex == null) {
            exposure.project(pixel.getPosition(), projector);
            getGrid().getIndex(projector.getOffset(), index);    
        }
        else {
            final int linearIndex = exposure.sourceIndex[pixel.getIndex()];
            index.set(linearIndex >>> indexShiftX, linearIndex & indexMaskY);
        }

        if(CRUSH.debug) {
            if(index.i() < 0 || index.i() >= sizeX() || index.j() < 0 || index.j() >= sizeY()) {
                warning("!!! invalid map index pixel " + pixel.getID() + " frame " + exposure.index + ": " +
                        (exposure.sourceIndex == null ? index : exposure.sourceIndex[pixel.getIndex()]));
                
                index.set(0, 0);
            }
        }
    }


 
    public void setSize() throws Exception, OutOfMemoryError {
      
        // Figure out what offsets the corners of the map will have...
        Range2D range = searchCorners(); 
        
        if(CRUSH.debug) debug("map range: " + Util.f1.format(range.getXRange().span() / Unit.arcsec) + " x " 
                +  Util.f1.format(range.getYRange().span() / Unit.arcsec) + " arcsec");

        
        double defaultResolution = getInstrument().getPointSize() / 5.0;
        Vector2D delta = new Vector2D(defaultResolution, defaultResolution);

        if(hasOption("grid")) {
            List<Double> values = option("grid").getDoubles();
            if(values.size() == 1) delta.set(values.get(0), values.get(0));
            else delta.set(values.get(0), values.get(1));
            delta.scale(getInstrument().getSizeUnit().value());
        }

        // Make the reference fall on pixel boundaries.
        grid.setResolution(delta);

        Range xRange = range.getXRange();
        Range yRange = range.getYRange();
        
        grid.getReferenceIndex().set(0.5 - Math.rint(xRange.min() / delta.x()), 0.5 - Math.rint(yRange.min() / delta.y()));

        if(CRUSH.debug) {
            Vector2D corner = new Vector2D(xRange.min(), yRange.min());
            grid.toIndex(corner);
            debug("near corner: " + corner);
            
            corner = new Vector2D(xRange.max(), yRange.max());
            grid.toIndex(corner);
            debug("far corner: " + corner);
        }

        int sizeX = 1 + (int) Math.ceil(grid.getReferenceIndex().x() + xRange.max() / delta.x());
        int sizeY = 1 + (int) Math.ceil(grid.getReferenceIndex().y() + yRange.max() / delta.y());
        
        if(CRUSH.debug) debug("map pixels: " + sizeX + " x " + sizeY);
         
        if(sizeX < 0 || sizeY < 0) throw new IllegalStateException("Negative image size: " + sizeX + " x " + sizeY);

        checkForStorage(sizeX, sizeY);	
        
        setSize(sizeX, sizeY);
    }

  
    public void runtimeMemoryError(String message) {
        error(message);
        CRUSH.suggest(this,
                "   * Check that the map size is reasonable for the area mapped and that\n" +
                "     all scans reduced together belong to the same source or region.\n\n" +
                "   * Increase the amount of memory available to crush, by editing the '-Xmx'\n" +
                "     option to Java in 'wrapper.sh' (or 'wrapper.bat' for Windows).\n\n" +
                "   * If using 64-bit Unix OS and Java, you can also add the '-d64' option to\n" +
                "     allow Java to access over 2GB.\n\n" +
                "   * Reduce the number of parallel threads in the reduction by increasing\n" +
                "     the idle CPU cores with the 'reservecpus' option.\n");
    }
    
    public void createMemoryError(int sizeX, int sizeY) throws OutOfMemoryError {

        Vector2D resolution = getGrid().getResolution();
        double diagonal = ExtraMath.hypot(sizeX * resolution.x(), sizeY * resolution.y());

        error("Map is too large to fit into memory (" + sizeX + "x" + sizeY + " pixels).");
        
        StringBuffer buf = new StringBuffer();
        
        buf.append(" Map requires " + (getMemoryFootprint(sizeX * sizeY) >> 20) + " MB free memory.\n\n"); 
        
        boolean foundSuspects = false;

        if(numberOfScans() > 1) {
            // Check if there is a scan at least half long edge away from the median center...
            Collection<Scan<?>> suspects = findOutliers(diagonal / 2.0);
            if(!suspects.isEmpty()) {
                foundSuspects = true;
                buf.append(
                        "   * Check that all scans observe the same area on sky, \n" +
                        "     and remove those that are far from your source.\n");	
                buf.append("     Suspect scan(s) are:\n");
                for(Scan<?> scan : suspects) buf.append("     --> " + scan.getID() + "\n");
            }
        }

        // Check if there is a scan that spans at least a half long edge... 
        Collection<Scan<?>> suspects = findSlewing(diagonal / 2.0);
        if(!suspects.isEmpty()) {
            foundSuspects = true;
            buf.append("   * Was data acquired during telescope slew?\n");	
            buf.append("     Suspect scan(s) are:\n");
            for(Scan<?> scan : suspects) buf.append("     --> " + scan.getID() + "\n");
        }

        if(!foundSuspects) {	
            buf.append(
                    "   * Could there be an unflagged pixel with an invalid position?\n" +
                    "     Check your instrument configuration and pixel data files.\n");
        }

        buf.append(
                "   * Increase the amount of memory available to crush, by editing the '-Xmx'\n" +
                "     option to Java in 'wrapper.sh'.");
       
        CRUSH.suggest(this, new String(buf));
        
        throw new OutOfMemoryError("Will not fit source model (" + sizeX + "x" + sizeY + ") in RAM available to JVM.");
    }

    // Check for minimum required storage (without reduction overheads)
    protected void checkForStorage(int sizeX, int sizeY) throws OutOfMemoryError {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long required = getPixelFootprint() * sizeX * sizeY;
        if(used + required > max) createMemoryError(sizeX, sizeY); 
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection<Scan<?>> findOutliers(double maxDistance) {
        ArrayList<Scan<?>> outliers = new ArrayList<>();

        int scans = numberOfScans();
        
        double[] x = new double[scans];
        double[] y = new double[scans];

        for(int i=scans; --i >= 0; ) {
            Coordinate2D coords = getScan(i).getReferenceCoordinates().copy();
            if(coords instanceof PrecessingCoordinates) ((PrecessingCoordinates) coords).toICRS();
            
            x[i] = coords.x();
            y[i] = coords.y();
        }
        
        Coordinate2D median = getScan(0).getReferenceCoordinates().copy();
        median.set(Statistics.Inplace.median(x), Statistics.Inplace.median(y));

        for(Scan<?> scan : getScans()) {
            Coordinate2D coords = scan.getReferenceCoordinates().copy();
            if(!(coords instanceof Metric)) continue;
            if(!median.getClass().equals(coords.getClass())) continue;
            
            if(((Metric) coords).distanceTo(median) > maxDistance) outliers.add(scan);
        }
        
        return outliers;
    }

    public Collection<Scan<?>> findSlewing(double maxSpan) {
        ArrayList<Scan<?>> slews = new ArrayList<>();
        
        Coordinate2D ref = getProjection().getReference();
        double cosLat = (ref instanceof SphericalCoordinates) ? ((SphericalCoordinates) ref).cosLat() : 1.0;

        for(Scan<?> scan : getScans()) {
            double span = ExtraMath.hypot(scan.range.getXRange().span() * cosLat, scan.range.getYRange().span());
            if(span > maxSpan) slews.add(scan);
        }
        
        return slews;
    }

  
    protected void add(final Frame exposure, final Pixel pixel, final Index2D index, final double frameGain, final double[] sourceGain) {
        // The use of iterables is a minor performance hit only (~3% overall)
        for(final Channel channel : pixel) if((exposure.sampleFlag[channel.getIndex()] & excludeSamples) == 0)   
            addPoint(index, channel, exposure, frameGain * sourceGain[channel.getIndex()], channel.getInstrument().samplingInterval);
    }
    
 
    protected int add(final Integration<?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {	
        if(CRUSH.debug) debug("add.pixels " + pixels.size() + " : " + integration.getInstrument().size());  
        return addForkFrames(integration, pixels, sourceGain, signalMode);
    }

    
    protected int addForkFrames(final Integration<?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {	
        
        class Mapper extends CRUSH.Fork<Integer> {
            private AbstractSource2D localSource;
            private Projector2D<?> projector;
            private Index2D index;
            private int mappingFrames = 0;

            Mapper() { super(integration.size(), integration.getThreadCount()); }

            @Override
            protected void init() {         
                super.init();
    
                if(isAddingToMaster()) localSource = AbstractSource2D.this;
                else localSource = (AbstractSource2D) getRecycledCleanLocalCopy();
                
                projector = localSource.getProjectorInstance();   
                index = new Index2D();
            }

            @Override
            protected void processIndex(int index) {
                Frame exposure = integration.get(index);
                if(exposure != null) process(exposure);
            }

            private void process(Frame exposure) {
                if(exposure.isFlagged(Frame.SOURCE_FLAGS)) return;
     
                final double frameGain = integration.gain * exposure.getSourceGain(signalMode);	
                if(frameGain == 0.0) return;

                mappingFrames++;

                for(final Pixel pixel : pixels) {
                    localSource.getIndex(exposure, pixel, projector, index);
                    localSource.add(exposure, pixel, index, frameGain, sourceGain);
                }
            }

            @Override
            public Integer getLocalResult() { 
                return mappingFrames;         
            }

            @Override
            public Integer getResult() {				
                mappingFrames = 0;
                for(ParallelTask<Integer> task : getWorkers()) {
                    mappingFrames += task.getLocalResult();

                    if(!isAddingToMaster()) {
                        AbstractSource2D localMap = ((Mapper) task).localSource;
                        mergeAccumulate(localMap);
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

    protected int addForkPixels(final Integration<?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {	
        int mappingFrames = 0;

        for(Frame exposure : integration) if(exposure != null) {
            exposure.tempC = exposure.isFlagged(Frame.SOURCE_FLAGS) ? 0.0F : integration.gain * exposure.getSourceGain(signalMode);
            if(exposure.tempC != 0.0F) mappingFrames++;
        }

        class Mapper extends CRUSH.Fork<Void> {
            private AbstractSource2D localSource;
            private Projector2D<?> projector;
            private Index2D index;

            Mapper() { super(pixels.size(), integration.getThreadCount()); }

            @Override
            protected void init() {
                super.init();

                if(isAddingToMaster()) localSource = AbstractSource2D.this;
                else localSource = (AbstractSource2D) getRecycledCleanLocalCopy();

                projector = localSource.getProjectorInstance();
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
                }
            }

            @Override
            public Void getResult() {				
                for(ParallelTask<Void> task : getWorkers()) if(!isAddingToMaster()) {
                    AbstractSource2D localMap = ((Mapper) task).localSource;
                    mergeAccumulate(localMap);
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
    public void add(Integration<?> integration) {
        add(integration, signalMode);
    }

    public void add(Integration<?> integration, int signalMode) {
        final Instrument<?> instrument = integration.getInstrument(); 

        integration.comments.append("Map");
        if(getID() != null) integration.comments.append("." + getID());
        // For jackknived maps indicate sign...

        // Proceed only if there are enough pixels to do the job...
        if(!checkPixelCount(integration)) return;

        // Calculate the effective source NEFD based on the latest weights and the current filtering
        integration.calcSourceNEFD();

        final double averageFiltering = instrument.getAverageFiltering();			

        // For the first source generation, apply the point source correction directly to the signals.
        final boolean signalCorrection = integration.sourceGeneration == 0;
      
        final int mappingFrames = add(
                integration, 
                integration.getInstrument().getMappingPixels(0), 
                instrument.getSourceGains(getPointSize(), signalCorrection), 
                signalMode
        );

        if(CRUSH.debug) {
            debug("mapping frames: " + mappingFrames + " --> map points: " + countPoints());    
        }

        if(signalCorrection)
            integration.comments.append("[C~" + Util.f2.format(1.0/averageFiltering) + "] ");
        
        integration.comments.append(" ");
    }

    public void setSyncGains(final Integration<?> integration, final Pixel pixel, final double[] sourceGain) {
        if(integration.sourceSyncGain == null) integration.sourceSyncGain = new double[sourceGain.length];
        for(Channel channel : pixel) integration.sourceSyncGain[channel.getIndex()] = sourceGain[channel.getIndex()];
    }

    protected void sync(final Integration<?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {			
        if(CRUSH.debug) debug("sync.pixels " + pixels.size() + " : " + integration.getInstrument().size());

        integration.new Fork<Void>() {
            private Projector2D<?> projector;
            private Index2D index;

            @Override
            public void init() {
                super.init();
                projector = getProjectorInstance();
                index = new Index2D();
            }

            @Override 
            protected void process(final Frame exposure) {
                final double fG = integration.gain * exposure.getSourceGain(signalMode); 

                // Remove source from all but the blind channels...
                for(final Pixel pixel : pixels)  {
                    AbstractSource2D.this.getIndex(exposure, pixel, projector, index);	
                    sync(exposure, pixel, index, fG, sourceGain, integration.sourceSyncGain);
                }
            }
        }.process();
    }

    @Override
    public void sync(Integration<?> integration) {
        sync(integration, signalMode);
    }


    public void sync(final Integration<?> integration, final int signalMode) {
        Instrument<?> instrument = integration.getInstrument(); 

        double[] sourceGain = instrument.getSourceGains(getPointSize(), false);	
        if(integration.sourceSyncGain == null) integration.sourceSyncGain = new double[sourceGain.length];

        final List<? extends Pixel> pixels = instrument.getMappingPixels(~instrument.getSourcelessChannelFlags());

        if(hasSourceOption("coupling")) {
            calcCoupling(integration, pixels, sourceGain, integration.sourceSyncGain);
            sourceGain = instrument.getSourceGains(getPointSize(), false);
        }
        sync(integration, pixels, sourceGain, signalMode);

        // Do an approximate accounting of the source dependence...
        double sumpw = 0.0;
        for(Pixel pixel : pixels) for(Channel channel : pixel) if(channel.isUnflagged()) 
            sumpw += sourceGain[channel.getIndex()] * sourceGain[channel.getIndex()] / channel.variance;

        double sumfw = 0.0;
        for(Frame exposure : integration) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS))
            sumfw += exposure.relativeWeight * exposure.getSourceGain(signalMode);		

        double N = Math.min(integration.getScan().sourcePoints, countPoints()) / covariantPoints();
        final double np = sumpw > 0.0 ? N / sumpw : 0.0;
        final double nf = sumfw > 0 ? N / sumfw : 0.0;

        // TODO revise for composite sources...
        final Dependents parms = integration.getDependents("source");

        for(int k=pixels.size(); --k >= 0; ) {
            Pixel pixel = pixels.get(k);
            parms.clear(pixel, 0, integration.size());

            for(Channel channel : pixel) if(channel.isUnflagged()) 
                parms.addAsync(channel, np * sourceGain[channel.getIndex()] * sourceGain[channel.getIndex()] / channel.variance);
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
   

    @Override
    public String suggestMakeValid() {
        StringBuffer buf = new StringBuffer();
        buf.append(super.suggestMakeValid());
     
        buf.append("            * Increase 'grid' for a coarser map pixellization.\n");
        if(hasSourceOption("redundancy")) 
            buf.append("            * Disable redundancy checking ('forget=source.redundancy').\n");
        
        return new String(buf);
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("smooth")) return smoothing / getInstrument().getSizeUnit().value();
        if(name.equals("system")) {
            Grid2D<?> grid = getGrid();
            if(grid instanceof SphericalGrid) return (((SphericalGrid) grid).getReference()).getTwoLetterID();
            return "cartesian";
        }
        return super.getTableEntry(name);
    }
    
    
    @Override
    public void write() throws Exception {    
        // Remove the intermediate image file...
        File intermediate = new File(getOutputPath() + File.separator + "intermediate." + getID() + ".fits");
        if(intermediate.exists()) intermediate.delete();

        String idExt = "";
        if(getID() != null) if(getID().length() > 0) idExt = "." + getID();

        String fileName = getOutputPath() + File.separator + getCoreName() + idExt + ".fits";
           
        if(isEmpty()) {
            // No file is created, any existing file with same name is erased.
            warning("Source" + idExt + " is empty. Skipping.");
            File file = new File(fileName);
            if(file.exists()) file.delete();
            return;
        }
   
        processFinal();
        
        writeFits(fileName);
        
        if(hasOption("write.png")) writePNG(getMap2D(), option("write.png"), fileName);
    }
    

    public void writePNG(Map2D map, Configurator config, String fileName) throws Exception { 
        map = map.copy(true);
        
        Data2D values = map;
        
        
        // Smooth thumbnail (by half a beam, default) for nicer appearance
        if(config.hasOption("smooth")) {
            String arg = config.option("smooth").getValue();
            double fwhm = arg.length() > 0 ? getSmoothing(arg) : 0.5 * getInstrument().getPointSize();
            map.smoothTo(fwhm);
        }
        
        
        if(config.hasOption("crop")) {
            List<Double> offsets = config.option("crop").getDoubles();
                  
            if(offsets.isEmpty()) map.autoCrop();
            else {
                double sizeUnit = getInstrument().getSizeUnit().value();
                double dXmin = offsets.get(0) * sizeUnit;
                double dYmin = offsets.size() > 0 ? offsets.get(1) * sizeUnit : dXmin;
                double dXmax = offsets.size() > 1 ? offsets.get(2) * sizeUnit : -dXmin;
                double dYmax = offsets.size() > 2 ? offsets.get(3) * sizeUnit : -dYmin;
                map.crop(new Vector2D(dXmin, dYmin), new Vector2D(dXmax, dYmax));
            }
        }
               
        if(map instanceof Observation2D) if(config.hasOption("plane")) {
            Observation2D obs = (Observation2D) map;
            
            String spec = config.option("plane").getValue().toLowerCase();
            if(spec.equals("s2n")) values = obs.getSignificance();
            else if(spec.equals("s/n")) values = obs.getSignificance();
            else if(spec.equals("time")) values = obs.getExposures();
            else if(spec.equals("noise")) values = obs.getNoise();
            else if(spec.equals("rms")) values = obs.getNoise();
            else if(spec.equals("weight")) values = obs.getWeights();
        }      
               
        
        writePNG(values.getImage(), config, fileName);  
    }

    public void writePNG(Image2D map, Configurator option, String fileName) throws Exception {
        int width = DEFAULT_PNG_SIZE;
        int height = DEFAULT_PNG_SIZE;

        if(option.hasOption("size")) {
            StringTokenizer tokens = new StringTokenizer(option.option("size").getValue(), "xX*:, ");
            width = Integer.parseInt(tokens.nextToken());
            height = tokens.hasMoreTokens() ? Integer.parseInt(tokens.nextToken()) : width;
        }     
      
        if(!option.hasOption("crop")) {
            map = map.copy(true);
            map.autoCrop(); 
        }

    
        final ImageArea<BufferedImageLayer> imager = new ImageArea<>();
        final BufferedImageLayer image = new BufferedImageLayer(map);

        if(option.hasOption("scaling")) {
            String spec = option.option("scaling").getValue().toLowerCase();
            if(spec.equals("log")) image.setScaling(BufferedImageLayer.SCALE_LOG);
            if(spec.equals("sqrt")) image.setScaling(BufferedImageLayer.SCALE_SQRT);
        }

        if(option.hasOption("spline")) image.setSpline();

        imager.setContentLayer(image);
        imager.setBackground(Color.LIGHT_GRAY);
        imager.setOpaque(true);

        ColorScheme scheme = new Colorful();

        if(option.hasOption("bg")) {
            String spec = option.option("bg").getValue().toLowerCase();
            if(spec.equals("transparent")) imager.setOpaque(false);
            else {
                try { imager.setBackground(new Color(Integer.decode(spec))); }
                catch(NumberFormatException e) { imager.setBackground(Color.getColor(spec)); }
            }
        }

        if(option.hasOption("color")) {
            String schemeName = option.option("color").getValue();
            if(ColorScheme.schemes.containsKey(schemeName)) 
                scheme = ColorScheme.getInstanceFor(schemeName);
        }

        image.setColorScheme(scheme);
        imager.setSize(width, height);
        imager.saveAs(fileName + ".png", width, height);           
    }

  
    
    public static final int DEFAULT_PNG_SIZE = 300;


}

