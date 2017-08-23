/*******************************************************************************
 * Copyright (c) 2017 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/

package crush.sourcemodel;


import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

import crush.*;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroProjector;
import jnum.data.*;
import jnum.data.image.Data2D;
import jnum.data.image.Flag2D;
import jnum.data.image.Gaussian2D;
import jnum.data.image.Image2D;
import jnum.data.image.Index2D;
import jnum.data.image.Map2D;
import jnum.data.image.MapProperties;
import jnum.data.image.Observation2D;
import jnum.data.image.Validating2D;
import jnum.data.image.overlay.RangeRestricted2D;
import jnum.data.image.region.EllipticalSource;
import jnum.data.image.region.GaussianSource;
import jnum.data.image.region.SourceCatalog;
import jnum.fits.FitsToolkit;
import jnum.math.Coordinate2D;
import jnum.math.Range;
import jnum.parallel.ParallelPointOp;
import jnum.parallel.ParallelTask;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;



public class AstroMap extends AstroData2D<Observation2D> {
    /**
     * 
     */
    private static final long serialVersionUID = 224617446250418317L;

    public Observation2D map;
    protected Image2D base; 
  
  
    public AstroMap(Instrument<?> instrument) {
        super(instrument);
        createMap();
    }

    @Override
    public void setInstrument(Instrument<?> instrument) {
        super.setInstrument(instrument);
        if(map != null) {
            MapProperties properties = map.getProperties();
            properties.setInstrumentName(instrument.getName());
            properties.setTelescopeName(instrument.getTelescopeName());
        }            
    }
    
  
    public Unit getJanskyUnit() {
        return new Unit("Jy", Double.NaN) {
            private static final long serialVersionUID = -2228932903204574146L;

            @Override
            public double value() { return getInstrument().janskyPerBeam() * map.getProperties().getUnderlyingBeam().getArea(); }
        };
    }


    @Override
    public void addModel(SourceModel model, double weight) {  
        AstroMap astro = (AstroMap) model;
        map.accumulate(astro.map, weight);  
    }

    @Override
    public void mergeAccumulate(AstroModel2D other) {
        map.mergeAccumulate(((AstroMap) other).map);
    }


    @Override
    public AstroMap getWorkingCopy(boolean withContents) {
        AstroMap copy = (AstroMap) super.getWorkingCopy(withContents);
        
        try { copy.map = map.copy(withContents); }
        catch(OutOfMemoryError e) { 
            runtimeMemoryError("Ran out of memory while making a copy of the source map.");
        }

        return copy;
    }

    public void standalone() { 
        base = Image2D.createType(Double.class, map.sizeX(), map.sizeY());
    }
  
    private void createMap() {
        map = new Observation2D(Double.class, Double.class, Flag2D.TYPE_INT);

        map.setParallel(CRUSH.maxThreads);
        map.setGrid(getGrid());
        map.setCriticalFlags(~FLAG_MASK);  
        
        map.addLocalUnit(getNativeUnit());
        map.addLocalUnit(getJanskyUnit(), "Jy, jansky, Jansky");
        map.addLocalUnit(getKelvinUnit(), "K, kelvin, Kelvin");   
           
        MapProperties properties = map.getProperties();
        properties.setInstrumentName(getInstrument().getName());
        properties.setCreatorName(CRUSH.class.getSimpleName());
        properties.setCopyright(CRUSH.getCopyrightString());     
        properties.seDisplayGridUnit(getInstrument().getSizeUnit());
        
        if(hasOption("unit")) map.setUnit(option("unit").getValue());
    }
 
    @Override
    public void createFrom(Collection<? extends Scan<?,?>> collection) throws Exception {
        createMap();
        
        super.createFrom(collection);  
        
        MapProperties properties = map.getProperties();
        properties.setObjectName(getFirstScan().getSourceName());
        properties.setUnderlyingBeam(getAverageResolution());
    
        CRUSH.info(this, "\n" + map.getInfo());

        base = Image2D.createType(Double.class, map.sizeX(), map.sizeY());

        if(hasOption("mask")) {
            try { 
                SourceCatalog catalog = new SourceCatalog(getReference().getClass());
                catalog.read(option("mask").getPath()); 
                maskSources(catalog); 
            }
            catch(IOException e) { 
                warning("Cannot read map mask. Check the file name and path."); 
                if(CRUSH.debug) CRUSH.trace(e);
            }
        }


        if(hasOption("sources")) {
            try { 
                SourceCatalog catalog = new SourceCatalog(getReference().getClass());
                catalog.read(option("sources").getPath()); 
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

    public void maskSources(SourceCatalog catalog) {
        // Since the synching step is removal, the sources should be inserted with a negative sign to add into the
        // timestream.
        double resolution = getAverageResolution();

        for(GaussianSource source : catalog) if(source.getRadius().value() < resolution) {
            info("! Source '" + source.getID() + "' FWHM increased to match map resolution.");
            source.setRadius(resolution);
        }

        info("Masking " + catalog.size() + " region(s).");
        catalog.flag(map, FLAG_MASK);

        maskSamples(Frame.SAMPLE_SKIP);   
    }    


    public void insertSources(SourceCatalog catalog) throws Exception {
        // Since the synching step is removal, the sources should be inserted with a negative sign to add into the
        // timestream.
        double resolution = getAverageResolution();

        for(GaussianSource source : catalog) if(source.getRadius().value() < resolution) {
            info("! Source '" + source.getID() + "' FWHM increased to match map resolution.");
            source.setRadius(resolution);
        }

        catalog.remove(map);

        info("Inserting test sources into data.");

        for(Scan<?,?> scan : getScans()) for(Integration<?,?> integration : scan) {
            sync(integration);
            integration.sourceGeneration=0;
        }
        

        map.renew();
    }    

    public void applyModel(String fileName) throws Exception {
        info("Applying source model:");

        Fits fits = new Fits(new File(fileName));
        Map2D model = Map2D.read(fits, 0);
        fits.close();

        /*
		double renorm = map.getImageBeamArea() / model.getImageBeamArea();
		if(renorm != 1.0) {
			CRUSH.detail(this " --> Rescaling model to instrument resolution: " + Util.s3.format(renorm) + "x");
			model.scale(renorm);
		}
         */
        
        map.resampleFrom(model);

        resetProcessing();
        nextGeneration();
        
        map.validate();

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

        Fits fits = new Fits(new File(fileName));
        Map2D model = Map2D.read(fits, 0);
        fits.close();

        /*
		double renorm = map.getImageBeamArea() / model.getImageBeamArea();
		if(renorm != 1.0) {
			CRUSH.detail(this, " --> Rescaling model to instrument resolution: " + Util.s3.format(renorm) + "x");
			model.scale(renorm);
		}
         */

        map.resampleFrom(model);

        double scaling = hasOption("source.inject.scale") ? option("source.inject.scale").getDouble() : 1.0;

        map.validate();
        map.scale(-scaling);


        CRUSH.detail(this, "Injecting source map into timestream data. ");
        try { super.sync(); }
        catch(Exception e) { error(e); }

        map.renew();
        base.fill(0.0);
    }




    // 3 double maps (signal, weight, integrationTime), one int (flag)
    // 3 doubles:   24
    // 1 int:        4
    @Override
    public int getPixelFootprint() { return 32; }

    @Override
    public long baseFootprint(int pixels) { return 8L * pixels; }

    @Override
    public void setSize(int sizeX, int sizeY) {
        map.setSize(sizeX, sizeY);
    }

    @Override
    public int sizeX() { return map.sizeX(); }

    @Override
    public int sizeY() { return map.sizeY(); }

 
   

    @Override
    public void postprocess(Scan<?,?> scan) {
        super.postprocess(scan);
        
        if(isEmpty()) return;

        if(hasOption("pointing.suggest")) {
            double optimal = hasOption("smooth.optimal") ? 
                    option("smooth.optimal").getDouble() * scan.instrument.getSizeUnit().value() : scan.instrument.getPointSize();
             
            map.smoothTo(optimal);
       
            if(hasOption("pointing.exposureclip")) {
                Data2D exposure = map.getExposures();     
                exposure.restrictRange(new Range(option("pointing.exposureclip").getDouble() * exposure.select(0.9), Double.POSITIVE_INFINITY));
            }
            map.reweight(true);
            
            scan.pointing = getPeakSource();
        }
        
    }
  

    public Index2D getPeakIndex() {
        int sign = hasSourceOption("sign") ? sourceOption("sign").getSign() : 0;
        
        Data2D s2n = map.getSignificance();
        
        if(sign > 0) return s2n.indexOfMax();
        else if(sign < 0) return s2n.indexOfMin();
        else return s2n.indexOfMaxDev();
    }
    
    public Coordinate2D getPeakCoords() {
        AstroProjector projector = new AstroProjector(getProjection());
        getGrid().getOffset(getPeakIndex(), projector.offset);
        projector.deproject();
        return projector.getCoordinates();
    }
    
    // TODO for non spherical coordinates...
    public GaussianSource getPeakSource() {
        map.level(true);
        
        MapProperties properties = map.getProperties();
        
        Gaussian2D beam = map.getProperties().getImageBeam();
        EllipticalSource source = new EllipticalSource(getPeakCoords(), beam.getMajorFWHM(), beam.getMinorFWHM(), beam.getPositionAngle());
        
        source.setPeakPositioning();
        if(hasOption("pointing.method")) if(option("pointing.method").is("centroid")) source.setCentroidPositioning();
        
        source.adaptTo(map);
        source.deconvolveWith(properties.getUnderlyingBeam());
      
        double criticalS2N = hasOption("pointing.significance") ? option("pointing.significance").getDouble() : 5.0;
        if(source.getPeak().significance() < criticalS2N) return null;
     
        return source;
    }



    @Override
    public void updateMask(double blankingLevel, int minNeighbors) {
        if(Double.isNaN(blankingLevel)) blankingLevel = Double.POSITIVE_INFINITY;
        
        Range s2nRange = new Range(-blankingLevel, blankingLevel);
        
        if(hasSourceOption("sign")) {
            int sign = sourceOption("sign").getSign();
            if(sign < 0) s2nRange.setMax(Double.POSITIVE_INFINITY);
            else if(sign > 0) s2nRange.setMin(Double.NEGATIVE_INFINITY);
        }
        
        final Validating<Index2D> neighbors = map.getNeighborValidator(minNeighbors);
        final Validating<Index2D> s2n = new RangeRestricted2D(map.getSignificance(), s2nRange);
            
        map.loop(new ParallelPointOp.Simple<Index2D>() {
            @Override
            public void process(Index2D index) {
                if(neighbors.isValid(index) && s2n.isValid(index)) map.unflag(index, FLAG_MASK);
                else map.flag(index, FLAG_MASK);
            }       
        });
       
    }

  
  
    public final boolean isMasked(final int i, final int j) { 
        return map.isFlagged(i, j, FLAG_MASK);
    }

    public final boolean isMasked(Index2D index) {
        return isMasked(index.i(), index.j());
    }

    @Override
    public void setBase() { 
        base.paste(map, false);
    }

    @Override
    public void resetProcessing() {
        super.resetProcessing();
        map.resetProcessing();
    }
    
    @Override
    public void clearContent() {
        map.clear();
    }

  
    @Override
    protected void addPoint(final Index2D index, final Channel channel, final Frame exposure, final double G, final double dt) {	
        map.accumulateAt(index.i(), index.j(), exposure.data[channel.index], G, exposure.relativeWeight / channel.variance, dt);
    }
    
    
    public void maskSamples(byte sampleFlagPattern) {
        for(Scan<?,?> scan : getScans()) for(Integration<?,?> integration : scan) maskSamples(integration, sampleFlagPattern);
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
                for(final Pixel pixel : pixels)  {
                    AstroMap.this.getIndex(exposure, pixel, projector, index); 
                    if(isMasked(index)) for(Channel channel : pixel) exposure.sampleFlag[channel.index] |= sampleFlagPattern;
                }
            }
        }.process();
        
    }


    @Override
    protected int add(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, int signalMode) {
        int goodFrames = super.add(integration, pixels, sourceGain, signalMode);
        addIntegrationTime(goodFrames * integration.instrument.samplingInterval);
        return goodFrames;
    }

    
    @Override
    protected void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, final double[] syncGain) {
        // The use of iterables is a minor performance hit only (~3% overall)
        if(!map.isValid(index)) return;
        
        final float mapValue = map.get(index).floatValue();
        final float baseValue = base.getValid(index, 0.0F).floatValue();
        
        for(final Channel channel : pixel) {
            // Do not check for flags, to get a true difference image...
            exposure.data[channel.index] -= fG * (sourceGain[channel.index] * mapValue - syncGain[channel.index] * baseValue);	

            // Do the blanking here...
            if(isMasked(index)) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SOURCE_BLANK;
            else exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SOURCE_BLANK;
        }
    }


    @Override
    protected void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain) {
        final Range s2nRange = hasSourceOption("coupling.s2n") ?
                Range.parse(sourceOption("coupling.s2n").getValue(), true) :
                new Range(5.0, Double.POSITIVE_INFINITY);
        
                
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
                    AstroMap.this.getIndex(exposure, pixel, projector, index);
                    final int i = index.i();
                    final int j = index.j();

                    double mapValue = map.get(i, j).doubleValue();
                    double s2n = mapValue / map.noiseAt(i, j);
                    
                    if(!s2nRange.contains(Math.abs(s2n))) continue; 
                    
                    double baseValue = base.get(i,j).doubleValue();
                    
                    for(final Channel channel : pixel) {
                        if((exposure.sampleFlag[channel.index] & excludeSamples) != 0) continue;
   
                        final double prior = fG * syncGain[channel.index] * baseValue;
                        final double expected = fG * sourceGain[channel.index] * mapValue; 
                        final double residual = exposure.data[channel.index] + prior - expected;  

                        sum[channel.index].add(exposure.relativeWeight * residual * expected);
                        sum[channel.index].addWeight(exposure.relativeWeight * expected * expected);			
                    }
                }	
            }

            @Override
            public DataPoint[] getLocalResult() { return sum; }

            @Override
            public DataPoint[] getResult() {
                DataPoint[] total = null;
                for(ParallelTask<DataPoint[]> task : getWorkers()) {
                    DataPoint[] local = task.getLocalResult();
                    if(total == null) total = local;
                    else {
                        for(int i=total.length; --i >= 0; ) {
                            total[i].add(local[i].value());
                            total[i].addWeight(local[i].weight());
                        }
                        Instrument.recycle(local);
                    }
                }
                return total;
            }

        };

        calcCoupling.process();

        final DataPoint[] result = calcCoupling.getResult();

        for(final Channel channel : integration.instrument) {
            DataPoint increment = result[channel.index];
            if(increment.weight() <= 0.0) continue;
            channel.coupling += (increment.value() / increment.weight()) * channel.coupling;
        }
        
        // Normalize the couplings to 1.0
        try {     
            CorrelatedMode coupling = (CorrelatedMode) integration.instrument.modalities.get("coupling").get(0);
            coupling.normalizeGains();
        }
        catch(Exception e) { warning(e); }
        
        Instrument.recycle(result);
        
        // If the coupling falls out of range, then revert to the default of 1.0	
        if(hasSourceOption("coupling.range")) {
            Range range = sourceOption("coupling.range").getRange();
            for(final Pixel pixel : pixels) for(final Channel channel : pixel) if(channel.isUnflagged()) {
                if(!range.contains(channel.coupling)) channel.flag(Channel.FLAG_BLIND);
                else channel.unflag(Channel.FLAG_BLIND);
            }
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
    public boolean isEmpty() {
        return map.countPoints() == 0;
    }

   
    @Override
    public Map2D getMap2D() { return map; }

    @Override
    public void processFinal() {
        
        // Re-level and weight map if allowed and 'deep' or not 'extended'.
        if(!hasOption("extended") || hasOption("deep")) {
            if(enableLevel) map.level(true);
            if(enableWeighting) map.reweight(true);
        }


        if(hasOption("regrid")) {
            map.resample(option("regrid").getDouble() * getInstrument().getSizeUnit().value());
        }
    }

    
    @Override
    public void writeFits(String fileName) throws FitsException, IOException {
        Fits fits = map.createFits(Float.class); 
        map.getProperties().setFileName(fileName);
        
        int nHDU = fits.getNumberOfHDUs();
        for(int i=0; i<nHDU; i++) {
            Header header = fits.getHDU(i).getHeader();
            editHeader(header);
        }   
        
        addScanHDUsTo(fits);
        
        FitsToolkit.write(fits, fileName);
        fits.close();
    }
    
 


    @Override
    public String getSourceName() {
        return map.getProperties().getObjectName();
    }

    @Override
    public Unit getUnit() {
        return map.getUnit();
    }
    
    @Override
    public ExecutorService getExecutor() {
        return map.getExecutor();
    }
    
    @Override
    public void setExecutor(ExecutorService e) {
        map.setExecutor(e);
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
    public Object getTableEntry(String name) {	
        if(name.equals("system")) return astroSystem().getID();
        if(name.startsWith("map.")) return map.getTableEntry(name.substring(4));
        else return super.getTableEntry(name);
    }


    @Override
    public Observation2D getData() {
        return map;
    }
    
    @Override
    public Data<?, ?, ?> getExposures() {
        return map.getExposures();
    }

    @Override
    public Data<?, ?, ?> getWeights() {
        return map.getWeights();
    }

    @Override
    public Data<?, ?, ?> getNoise() {
        return map.getNoise();
    }

    @Override
    public Data<?, ?, ?> getSignificance() {
        return map.getSignificance();
    }

    @Override
    public void endAccumulation() {
        map.endAccumulation();
    }

    @Override
    public void addBase() {
        map.add(base);
    }

    @Override
    public void smoothTo(double FWHM) {
        map.smoothTo(FWHM);
    }
    
    
    @Override
    public void filter(double filterScale, double filterBlanking, boolean useFFT) {
        Validating2D filterBlank = new RangeRestricted2D(map.getSignificance(), new Range(-filterBlanking, filterBlanking));
        
        if(useFFT) map.fftFilterAbove(filterScale, filterBlank);
        else map.filterAbove(filterScale, filterBlank);
        
        map.getProperties().setFilterBlanking(filterBlanking);
    }
    
    @Override
    public void resetFiltering() {
        map.getProperties().resetFiltering();
    }


    @Override
    public void filterBeamCorrect() {
        map.filterBeamCorrect();
    }

    @Override
    public void memCorrect(double lambda) {
        map.memCorrect(null, lambda);
    }




  
}
