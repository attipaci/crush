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

import crush.*;
import jnum.Configurator;
import jnum.ExtraMath;
import jnum.Parallel;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroProjector;
import jnum.astro.CoordinateEpoch;
import jnum.astro.EclipticCoordinates;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.FocalPlaneCoordinates;
import jnum.astro.GalacticCoordinates;
import jnum.data.Grid2D;
import jnum.data.GridImage2D;
import jnum.data.Index2D;
import jnum.data.Statistics;
import jnum.math.Range;
import jnum.math.Range2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.projection.Gnomonic;
import jnum.projection.Projection2D;
import jnum.projection.SphericalProjection;


public abstract class SourceMap extends SourceModel {	
    /**
     * 
     */
    private static final long serialVersionUID = -8110425445687949465L;

    public double integationTime = 0.0;
    public double smoothing = 0.0;
    public int signalMode = Frame.TOTAL_POWER;

    public boolean allowIndexing = true;
    public int marginX = 0, marginY = 0;

    public Range2D range;
    
    protected int excludeSamples = ~Frame.SAMPLE_SOURCE_BLANK;


    private int indexShiftX, indexMaskY;
    

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
    public void createFrom(Collection<? extends Scan<?,?>> collection) throws Exception {
        super.createFrom(collection);
      
        info("Initializing Source Map.");	
 
        Projection2D<SphericalCoordinates> projection = null;

        try { projection = hasOption("projection") ? SphericalProjection.forName(option("projection").getValue()) : new Gnomonic(); }
        catch(Exception e) { projection = new Gnomonic(); }		

        Scan<?,?> firstScan = scans.get(0);
        String system = hasOption("system") ? option("system").getValue().toLowerCase() : "equatorial";

        if(system.equals("horizontal")) projection.setReference(firstScan.horizontal);
        else if(system.equals("native")) projection.setReference(firstScan.getNativeCoordinates()); 
        else if(system.equals("focalplane")) projection.setReference(new FocalPlaneCoordinates()); 
        else if(firstScan.isNonSidereal) {
            info("Forcing equatorial for moving object.");
            getOptions().processSilent("system", "equatorial");
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

        setSize();

        if(allowIndexing) if(hasOption("indexing")) {
            try { index(); }
            catch(Exception e) { 
                warning("Indexing error: " + e.getMessage());
                if(CRUSH.debug) CRUSH.trace(e);
            }
        }

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
  
    public double getPixelizationSmoothing() {
        return Math.sqrt(getGrid().getPixelArea()) / GridImage2D.fwhm2size;
    }

    @Override
    public double getPointSize() { return ExtraMath.hypot(getInstrument().getPointSize(), getRequestedSmoothing(option("smooth"))); }

    @Override
    public double getSourceSize() { return ExtraMath.hypot(super.getSourceSize(), getRequestedSmoothing(option("smooth"))); }


    private void flagOutside(final Integration<?,?> integration, final Vector2D fixedSize) {
        final Instrument<?> instrument = integration.instrument;
        final Collection<? extends Pixel> pixels = instrument.getMappingPixels(~instrument.sourcelessChannelFlags());

        new CRUSH.Fork<Void>(integration.size(), integration.getThreadCount()) {
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

    private void searchCorners(final Integration<?,?> integration) {
        final Collection<? extends Pixel> pixels = integration.instrument.getPerimeterPixels();
        if(pixels.size() == 0) return;

        if(CRUSH.debug) debug("search pixels: " + pixels.size() + " : " + integration.instrument.size());
        
        
        CRUSH.Fork<Range2D> findCorners = integration.new Fork<Range2D>() {
            private Range2D range;
            private AstroProjector projector;

            @Override
            protected void init() {
                super.init();
                projector = new AstroProjector(getProjection());
            }

            @Override
            protected void process(Frame exposure) {	
                for(Pixel pixel : pixels) {
                    exposure.project(pixel.getPosition(), projector);
                    
                    // Check to make sure the sample produces a valid position...
                    // If not, then flag out the corresponding data...
                    if(Double.isNaN(projector.offset.x()) || Double.isNaN(projector.offset.y())) {
                        for(Channel channel : pixel) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
                    }
                    else {
                        if(range == null) range = new Range2D(projector.offset);
                        else range.include(projector.offset);
                    }
                }
            }

            @Override
            public Range2D getLocalResult() { return range; }

            @Override
            public Range2D getResult() {
                range = null;
                for(Parallel<Range2D> task : getWorkers()) {
                    Range2D local = task.getLocalResult();
                    if(range == null) range = local;
                    else if(local != null) range.include(local);
                }
                return range;
            }		
        };

        findCorners.process();

        Range2D range = findCorners.getResult();

        // Check for null range...
        if(range == null) {
            if(CRUSH.debug) debug("map range " + integration.getDisplayID() + "> null");
        }
        else {
            if(CRUSH.debug) debug("map range " + integration.getDisplayID() + "> "
                    + Util.f1.format(range.getXRange().span() / Unit.arcsec) + " x " 
                    + Util.f1.format(range.getYRange().span() / Unit.arcsec));

            integration.scan.range.include(range);
        }

    }

    public void searchCorners() throws Exception {
        final Vector2D fixedSize = new Vector2D(Double.NaN, Double.NaN);
        final boolean fixSize = hasOption("map.size");

        range = new Range2D();
        
        if(fixSize) {
            StringTokenizer sizes = new StringTokenizer(option("map.size").getValue(), " \t,:xX");

            fixedSize.setX(0.5 * Double.parseDouble(sizes.nextToken()) * Unit.arcsec);
            fixedSize.setY(sizes.hasMoreTokens() ? 0.5 * Double.parseDouble(sizes.nextToken()) * Unit.arcsec : fixedSize.x());

            range.setRange(-fixedSize.x(), -fixedSize.y(), fixedSize.x(), fixedSize.y());	

            for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) flagOutside(integration, fixedSize);
        }

        else {
            range.empty();

            for(Scan<?,?> scan : scans) {
                scan.range = new Range2D();
                for(Integration<?,?> integration : scan) searchCorners(integration);
                range.include(scan.range);
            }	       
        }
    }

    public void index() throws Exception {
        final double maxUsage = hasOption("indexing.saturation") ? option("indexing.saturation").getDouble() : 0.5;
        info("Indexing maps (up to " + Util.d1.format(100.0*maxUsage) + "% of RAM saturation).");

        final Runtime runtime = Runtime.getRuntime();
        long maxAvailable = runtime.maxMemory() - getReductionFootprint(pixels());
        final long maxUsed = (long) (maxUsage * maxAvailable);

        for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) {
            if(runtime.totalMemory() - runtime.freeMemory() >= maxUsed) return;
            createLookup(integration);  
        }

    }


    public void createLookup(Integration<?,?> integration) {    
        final Instrument<?> instrument = integration.instrument;
        final List<? extends Pixel> pixels = instrument.getMappingPixels(~instrument.sourcelessChannelFlags());
        final int n = integration.instrument.getPixelCount();

        if(CRUSH.debug) debug("lookup.pixels " + pixels.size() + " : " + integration.instrument.size());

        indexShiftX = ExtraMath.log2ceil(sizeY());
        indexMaskY = (1<<indexShiftX) - 1;

        integration.new Fork<Void>() {
            private AstroProjector projector;
            private Index2D index;

            @Override
            protected void init() {
                super.init();
                projector = new AstroProjector(getProjection());
                index = new Index2D();
            }

            @Override 
            protected void process(Frame exposure) {  
                exposure.sourceIndex = new int[n];

                if(CRUSH.debug) Arrays.fill(exposure.sourceIndex, -1);

                for(final Pixel pixel : pixels) {
                    exposure.project(pixel.getPosition(), projector);
                    getGrid().getIndex(projector.offset, index);

                    if(CRUSH.debug) {
                        if(index.i() < 0 || index.i() >= sizeX() || index.j() < 0 || index.j() >= sizeY()) {
                            warning("!!! invalid map index pixel " + pixel.getID() + " frame " + exposure.index + ": " +
                                    (exposure.sourceIndex == null ? 
                                            index : 
                                                exposure.sourceIndex[pixel.getIndex()]
                                            )
                                    );
                            index.set(0, 0);
                        }
                    }


                    exposure.sourceIndex[pixel.getIndex()] = (index.i() << indexShiftX) | index.j();
                }
            }

        }.process();
    }

    public final void getIndex(final Frame exposure, final Pixel pixel, final AstroProjector projector, final Index2D index) {
        if(exposure.sourceIndex == null) {
            exposure.project(pixel.getPosition(), projector);
            getGrid().getIndex(projector.offset, index);    
        }
        else {
            final int linearIndex = exposure.sourceIndex[pixel.getIndex()];
            index.set(linearIndex >>> indexShiftX, linearIndex & indexMaskY);
        }

        if(CRUSH.debug) {
            if(index.i() < 0 || index.i() >= sizeX() || index.j() < 0 || index.j() >= sizeY()) {
                warning("!!! invalid map index pixel " + pixel.getID() + " frame " + exposure.index + ": " +
                        (exposure.sourceIndex == null ? 
                                index : 
                                    exposure.sourceIndex[pixel.getIndex()]
                                )
                        );
                index.set(0, 0);
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

    public final int pixels() { return sizeX() * sizeY(); }

    public abstract Grid2D<?> getGrid();

    public void setSize() throws Exception {
      
        // Figure out what offsets the corners of the map will have...
        searchCorners(); 
        
        if(CRUSH.debug) debug("map range: " + Util.f1.format(range.getXRange().span() / Unit.arcsec) + " x " 
                +  Util.f1.format(range.getYRange().span() / Unit.arcsec) + " arcsec");


        double defaultResolution = getInstrument().getPointSize() / 5.0;
        Vector2D delta = new Vector2D(defaultResolution, defaultResolution);

        if(hasOption("grid")) {
            List<Double> values = option("grid").getDoubles();
            if(values.size() == 1) delta.set(values.get(0), values.get(0));
            else delta.set(values.get(0), values.get(1));
            delta.scale(getInstrument().getSizeUnitValue());
        }

        // Make the reference fall on pixel boundaries.
        Grid2D<?> grid = getGrid();
        grid.setResolution(delta);

        Range xRange = range.getXRange();
        Range yRange = range.getYRange();
        
        grid.refIndex.setX(0.5 - Math.rint(xRange.min() / delta.x()));
        grid.refIndex.setY(0.5 - Math.rint(yRange.min() / delta.y()));

        if(CRUSH.debug) {
            Vector2D corner = new Vector2D(xRange.min(), yRange.min());
            grid.toIndex(corner);
            debug("near corner: " + corner);
            
            corner = new Vector2D(xRange.max(), yRange.max());
            grid.toIndex(corner);
            debug("far corner: " + corner);
        }

        int sizeX = 1 + (int) Math.ceil(grid.refIndex.x() + xRange.max() / delta.x());
        int sizeY = 1 + (int) Math.ceil(grid.refIndex.y() + yRange.max() / delta.y());
        
        if(CRUSH.debug) debug("map pixels: " + sizeX + " x " + sizeY);
         
        if(sizeX < 0 || sizeY < 0) throw new IllegalStateException("Negative image size: " + sizeX + " x " + sizeY);

        try { 
            checkForStorage(sizeX, sizeY);	
            setSize(sizeX, sizeY);
        }
        catch(OutOfMemoryError e) { createMemoryError(sizeX, sizeY); }
    }

    public abstract void setSize(int sizeX, int sizeY);

    public abstract int sizeX();

    public abstract int sizeY();

    public abstract Projection2D<SphericalCoordinates> getProjection(); 

    public abstract void setProjection(Projection2D<SphericalCoordinates> projection); 

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
        System.exit(1);
    }
    
    public void createMemoryError(int sizeX, int sizeY) {

        Vector2D resolution = getGrid().getResolution();
        double diagonal = ExtraMath.hypot(sizeX * resolution.x(), sizeY * resolution.y());

        error("Map is too large to fit into memory (" + sizeX + "x" + sizeY + " pixels).");
        
        StringBuffer buf = new StringBuffer();
        
        buf.append(" Map requires " + (getMemoryFootprint((long) sizeX * sizeY) >> 20) + " MB free memory.\n\n"); 
        
        boolean foundSuspects = false;

        if(scans.size() > 1) {
            // Check if there is a scan at least half long edge away from the median center...
            Collection<Scan<?,?>> suspects = findOutliers(diagonal / 2.0);
            if(!suspects.isEmpty()) {
                foundSuspects = true;
                buf.append(
                        "   * Check that all scans observe the same area on sky, \n" +
                        "     and remove those that are far from your source.\n");	
                buf.append("     Suspect scan(s) are:\n");
                for(Scan<?,?> scan : suspects) buf.append("     --> " + scan.getID() + "\n");
            }
        }

        // Check if there is a scan that spans at least a half long edge... 
        Collection<Scan<?,?>> suspects = findSlewing(diagonal / 2.0);
        if(!suspects.isEmpty()) {
            foundSuspects = true;
            buf.append("   * Was data acquired during telescope slew?\n");	
            buf.append("     Suspect scan(s) are:\n");
            for(Scan<?,?> scan : suspects) buf.append("     --> " + scan.getID() + "\n");
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

        System.exit(1);
    }

    // Check for minimum required storage (without reduction overheads)
    protected void checkForStorage(int sizeX, int sizeY) {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long required = (long) (getPixelFootprint() * sizeX * sizeY);
        if(used + required > max) createMemoryError(sizeX, sizeY); 
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
            double span = ExtraMath.hypot(scan.range.getXRange().span() * cosLat, scan.range.getYRange().span());
            if(span > maxDistance) slews.add(scan);
        }
        return slews;
    }

    protected abstract void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain);

    public abstract boolean isMasked(Index2D index); 

    public abstract void addNonZero(SourceMap other);

    protected boolean isAddingToMaster() { return false; }

    protected int add(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {	
        if(CRUSH.debug) debug("add.pixels " + pixels.size() + " : " + integration.instrument.size());

        return addForkFrames(integration, pixels, sourceGain, signalMode);
    }

    protected int addForkFrames(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {	

        class Mapper extends CRUSH.Fork<Integer> {
            private SourceMap localSource;
            private AstroProjector projector;
            private Index2D index;
            private int mappingFrames = 0;

            Mapper() { super(integration.size(), integration.getThreadCount()); }

            @Override
            protected void init() {
                super.init();

                if(isAddingToMaster()) localSource = SourceMap.this;
                else localSource = (SourceMap) getRecycledCleanThreadLocalCopy();

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
            public Integer getLocalResult() { return mappingFrames; }

            @Override
            public Integer getResult() {				
                mappingFrames = 0;
                for(Parallel<Integer> task : getWorkers()) {
                    mappingFrames += task.getLocalResult();

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

    protected int addForkPixels(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {	
        int mappingFrames = 0;

        for(Frame exposure : integration) if(exposure != null) {
            exposure.tempC = exposure.isFlagged(Frame.SOURCE_FLAGS) ? 0.0F : integration.gain * exposure.getSourceGain(signalMode);
            if(exposure.tempC != 0.0F) mappingFrames++;
        }

        class Mapper extends CRUSH.Fork<Void> {
            private SourceMap localSource;
            private AstroProjector projector;
            private Index2D index;

            Mapper() { super(pixels.size(), integration.getThreadCount()); }

            @Override
            protected void init() {
                super.init();

                if(isAddingToMaster()) localSource = SourceMap.this;
                else localSource = (SourceMap) getRecycledCleanThreadLocalCopy();

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
      
        final int mappingFrames = add(
                integration, 
                integration.instrument.getMappingPixels(0), 
                instrument.getSourceGains(signalCorrection), 
                        signalMode
                );

        if(CRUSH.debug) debug("mapping frames:" + mappingFrames);

        if(signalCorrection)
            integration.comments += "[C1~" + Util.f2.format(1.0/averageFiltering) + "] ";
        
        integration.comments += " ";
    }

    protected abstract void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, double[] syncGain, final boolean isMasked);

    public void setSyncGains(final Integration<?,?> integration, final Pixel pixel, final double[] sourceGain) {
        if(integration.sourceSyncGain == null) integration.sourceSyncGain = new double[sourceGain.length];
        for(Channel channel : pixel) integration.sourceSyncGain[channel.index] = sourceGain[channel.index];
    }

    protected void sync(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {			
        if(CRUSH.debug) debug("sync.pixels " + pixels.size() + " : " + integration.instrument.size());

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

        final List<? extends Pixel> pixels = instrument.getMappingPixels(~instrument.sourcelessChannelFlags());

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
    
    public void maskSamples(byte sampleFlagPattern) {
        for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) maskSamples(integration, sampleFlagPattern);
    }
    
    public void maskSamples(Integration<?,?> integration, final byte sampleFlagPattern) {
        final Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels(~0);
        
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
                 // Remove source from all but the blind channels...
                for(final Pixel pixel : pixels)  {
                    SourceMap.this.getIndex(exposure, pixel, projector, index); 
                    if(isMasked(index)) for(Channel channel : pixel) exposure.sampleFlag[channel.index] |= sampleFlagPattern;
                }
            }
        }.process();
        
    }

    public abstract double covariantPoints();

    protected abstract void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain);

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
        if(name.equals("smooth")) return smoothing / getInstrument().getSizeUnitValue();
        else return super.getTableEntry(name);
    }

  

}

