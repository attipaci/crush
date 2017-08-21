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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import crush.CRUSH;
import crush.Channel;
import crush.Frame;
import crush.Instrument;
import crush.Integration;
import crush.Pixel;
import crush.Scan;
import crush.SourceModel;
import jnum.Constant;
import jnum.Unit;
import jnum.data.cube2.Data2D1;
import jnum.data.cube2.Observation2D1;
import jnum.data.image.Data2D;
import jnum.data.image.Image2D;
import jnum.data.image.Index2D;
import jnum.data.image.Map2D;
import jnum.data.image.MapProperties;
import jnum.data.image.Observation2D;
import jnum.data.image.region.SourceCatalog;
import jnum.data.samples.Grid1D;
import jnum.math.Coordinate2D;
import jnum.math.Range;
import jnum.math.Vector2D;
import nom.tam.fits.FitsException;

public class SpectralMap extends AstroModel2D {
   
    /**
     * 
     */
    private static final long serialVersionUID = -4295840283454069570L;
   
    Observation2D1 cube;
    Data2D1<Image2D> base;
    
    Unit spectralUnit;
    boolean useWavelength = true;
    
    public SpectralMap(Instrument<?> instrument) {
        super(instrument);
        
        spectralUnit = Unit.get("GHz");
        if(hasOption("spectral.unit")) setSpectralUnit(option("spectral.unit").getValue());
    }
    
    public void setSpectralUnit(String spec) {
        spectralUnit = Unit.get("spec");
        String name = spectralUnit.name();

        if(name.endsWith("Hz")) useWavelength = false;
        else useWavelength = true;
        
    }
    
    public Range getFrequencyRange(Collection<? extends Scan<?,?>> scans) {
        Range r = new Range();
        for(Scan<?, ?> scan : scans) for(Integration<?, ?> integration : scan) {
            r.include(integration.getFrequencyRange(integration.instrument.getObservingChannels()));
        }
        return r;
    }
    
    public int getSizeZ() {  
         
        Range range = getFrequencyRange(getScans());
            
        useWavelength = hasOption("spectral.wavelength");
        if(useWavelength) range.setRange(Constant.c / range.max(), Constant.c / range.min());
        
        // TODO based on instrument.frequency & frequencyResolution 
        double delta = range.midPoint() / 1000.0;
        if(hasOption("spectral.grid")) delta = option("spectral.grid").getDouble() * spectralUnit.value();  
        
        Grid1D grid = cube.getGrid1D();
        grid.getAxis().setUnit(spectralUnit);
        grid.setResolution(delta);
        grid.setReference(getFirstScan().instrument.getFrequency());
        grid.setReferenceIndex(0.5 - Math.rint(range.min() / delta));

        int sizeZ = 1 + (int) Math.ceil(grid.getReferenceIndex().value() + range.max() / delta);
        
        if(CRUSH.debug) debug("spectral bins: " + sizeZ);

        return sizeZ;
    }
    
    
    @Override
    public void createFrom(Collection<? extends Scan<?,?>> collection) throws Exception {
           
        createCube();
        
        super.createFrom(collection);  
        
        MapProperties properties = map.getProperties();
        properties.setObjectName(getFirstScan().getSourceName());
        properties.setUnderlyingBeam(getAverageResolution());
    
        CRUSH.info(this, "\n" + cube.getPlane().getInfo());

        base = Image2D.createType(Double.class, sizeX(), sizeY());

        /*
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
        */
    } 
    

    @Override
    public int getPixelFootprint() {
        return 32 * sizeZ();
    }

    @Override
    public long baseFootprint(int pixels) {
        return 8L * sizeZ() * pixels;
    }

    @Override
    public void setSize(int sizeX, int sizeY) {
        cube.setSize(sizeX, sizeY, getSizeZ());
    }

    @Override
    public final int sizeX() {
        return cube.sizeX();
    }

    @Override
    public final int sizeY() {
        return cube.sizeY();
    }
    
    public final int sizeZ() {
        return cube.sizeZ();
    }

    public int getFrequencyBin(double frequency) {
        if(useWavelength) frequency = Constant.c / frequency;
        return (int) Math.round(cube.getGrid1D().indexOf(frequency));
    }
    
  
    @Override
    protected void addPoint(final Index2D index, final Channel channel, final Frame exposure, final double G, final double dt) {    
        Observation2D plane = cube.getPlane(getFrequencyBin(exposure.getChannelFrequency(channel)));
        plane.accumulateAt(index.i(), index.j(), exposure.data[channel.index], G, exposure.relativeWeight / channel.variance, dt);
    }

    @Override
    public boolean isMasked(Index2D index) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void mergeAccumulate(AstroModel2D other) {
        cube.mergeAccumulate(((SpectralMap) other).cube);      
    }

    @Override
    protected void sync(Frame exposure, Pixel pixel, Index2D index, double fG, double[] sourceGain, double[] syncGain,
            boolean isMasked) {
        for(final Channel channel : pixel) sync(exposure, channel, index, fG, sourceGain, syncGain, isMasked);
    }
 
    protected void sync(Frame exposure, Channel channel, Index2D index, double fG, double[] sourceGain, double[] syncGain,
            boolean isMasked) {

        final int k = getFrequencyBin(exposure.getChannelFrequency(channel));
        Observation2D plane = cube.getPlane(k);
        Data2D basePlane = base.getPlane(k);

        // The use of iterables is a minor performance hit only (~3% overall)
        if(!plane.isValid(index)) return;

        final float mapValue = plane.get(index).floatValue();
        final float baseValue = basePlane.getValid(index, 0.0F).floatValue();

        // Do not check for flags, to get a true difference image...
        exposure.data[channel.index] -= fG * (sourceGain[channel.index] * mapValue - syncGain[channel.index] * baseValue);  

        // Do the blanking here...
        if(isMasked) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SOURCE_BLANK;
        else exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SOURCE_BLANK;

    }

    @Override
    public double covariantPoints() {
        return cube.getPlane().getPointsPerSmoothingBeam();      
    }

    @Override
    protected void calcCoupling(Integration<?, ?> integration, Collection<? extends Pixel> pixels, double[] sourceGain,
            double[] syncGain) {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void clearContent() {
        cube.clear();
    }

    @Override
    public void addModel(SourceModel model, double weight) {
        cube.accumulate(((SpectralMap) model).cube, weight);
    }

  

    @Override
    public void setBase() {
        base.paste(cube, false);
    }

   
    @Override
    public boolean isEmpty() {
        return cube.countPoints() == 0;
    }

    @Override
    public Coordinate2D getReference() {
        return cube.getGrid2D().getReference();
    }

  

    @Override
    public int countPoints() {
        return cube.countPoints();
    }

   
    @Override
    public String getSourceName() {
        return cube.getPlane().getProperties().getObjectName();
    }

    @Override
    public Unit getUnit() {
        return cube.getUnit();
    }
    
    @Override
    public ExecutorService getExecutor() {
        return cube.getExecutor();
    }
    
    @Override
    public void setExecutor(ExecutorService e) {
        cube.setExecutor(e);
    }

    @Override
    public void noParallel() {
        cube.noParallel();
    }

    @Override
    public void setParallel(int threads) {
        cube.setParallel(threads);
    }

    @Override
    public int getParallel() {
        return cube.getParallel();
    }

    @Override
    public Object getTableEntry(String name) {  
        if(name.startsWith("map.")) return cube.getPlane().getTableEntry(name.substring(4));
        else return super.getTableEntry(name);
    }


    
    @Override
    public void process(Scan<?, ?> scan) {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void process() throws Exception {
        // TODO --> 'spectral.smooth'
       
        
    }
    

    @Override
    public void processFinal() {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void writeFits(String fileName) throws FitsException, IOException {
        // TODO Auto-generated method stub
        
    }


    @Override
    public Map2D getMap2D() {
        // TODO
        return null;
    }
    

}
