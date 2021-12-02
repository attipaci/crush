/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.sourcemodel;


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import crush.CRUSH;
import crush.Channel;
import crush.Frame;
import crush.Instrument;
import crush.Integration;
import crush.Pixel;
import crush.Scan;
import crush.SourceModel;
import jnum.Configurator;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.data.FlagCompanion;
import jnum.data.SplineSet;
import jnum.data.Validating;
import jnum.data.cube.overlay.RangeRestricted3D;
import jnum.data.cube2.Data2D1;
import jnum.data.cube2.Image2D1;
import jnum.data.cube2.Observation2D1;
import jnum.data.image.Data2D;
import jnum.data.image.Image2D;
import jnum.data.image.Map2D;
import jnum.data.image.Observation2D;
import jnum.data.image.Validating2D;
import jnum.data.image.overlay.RangeRestricted2D;
import jnum.data.image.overlay.Referenced2D;
import jnum.data.index.Index2D;
import jnum.data.index.Index3D;
import jnum.data.samples.Data1D;
import jnum.data.samples.Grid1D;
import jnum.data.samples.Samples1D;
import jnum.data.samples.overlay.Overlay1D;
import jnum.fits.FitsProperties;
import jnum.fits.FitsToolkit;
import jnum.math.CoordinateAxis;
import jnum.math.Range;
import jnum.math.Vector2D;
import jnum.parallel.ParallelPointOp;
import jnum.text.GreekLetter;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;


public class SpectralCube extends SourceData2D<Index3D, Observation2D1> {

    /**
     * 
     */
    private static final long serialVersionUID = -4295840283454069570L;

    Observation2D1 cube;
    Data2D1<Image2D> base;

    Unit spectralUnit;
    boolean useWavelength = true;
    boolean isLogarithmic;

    public SpectralCube(Instrument<?> instrument) {
        super(instrument);
    }

    @Override
    public SpectralCube copy(boolean withContents) throws OutOfMemoryError {
        SpectralCube copy = (SpectralCube) super.copy(withContents);

        try { copy.cube = cube.copy(withContents); }
        catch(OutOfMemoryError e) { 
            runtimeMemoryError("Ran out of memory while making a copy of the spectral cube.");
            throw(e);
        }

        copy.spectralUnit = spectralUnit.copy();

        return copy;
    }

    public void standalone() {
        base = Image2D1.create(Double.class, cube.sizeX(), cube.sizeY(), cube.sizeZ());
    }


    public void setSpectralUnit(String spec) {
        spectralUnit = Unit.get(spec);
        String name = spectralUnit.name().toLowerCase();

        if(name.endsWith("hz")) useWavelength = false;
        else useWavelength = true;  
    }


    public Unit getJanskyUnit() {
        return new Unit("Jy", Double.NaN) {
            private static final long serialVersionUID = -2228932903204574146L;

            @Override
            public double value() { return getInstrument().janskyPerBeam() * cube.getPlane(0).getUnderlyingBeam().getArea(); }
        };
    }


    public Range getFrequencyRange(Collection<? extends Scan<?>> scans) {
        Range r = new Range();
        for(Scan<?> scan : scans) for(Integration<?> integration : scan) {
            r.include(integration.getFrequencyRange(integration.getInstrument().getObservingChannels()));
        }
        return r;
    }

    public int findSizeZ() {  
        Range zRange = getFrequencyRange(getScans());

        spectralUnit = Unit.get("GHz");
        if(hasOption("spectral.unit")) setSpectralUnit(option("spectral.unit").getValue());

        if(useWavelength) zRange.setRange(Constant.c / zRange.max(), Constant.c / zRange.min());

        isLogarithmic = hasOption("spectral.logscale");
        if(isLogarithmic) {
            zRange.scale(1.0 / spectralUnit.value());
            zRange.setRange(Math.log10(zRange.min()), Math.log10(zRange.max()));
        }

        double zReference = zRange.midPoint();

        // TODO based on instrument.frequency & frequencyResolution 
        double delta = isLogarithmic ? 1e-3 * log10Octave : zRange.midPoint() / 1000.0; // Default to R ~ 1000 at the center frequency 

        if(hasOption("spectral.grid")) {
            delta = option("spectral.grid").getDouble() * spectralUnit.value();  
        }
        else if(hasOption("spectral.r")) {
            // In log scale R is the number of bins per octave...
            if(isLogarithmic) delta = 0.5 / option("spectral.r").getDouble() * log10Octave;
            else delta = 0.5 * zRange.midPoint() / option("spectral.r").getDouble();
        }

        Grid1D grid = cube.getGrid1D();
        grid.setResolution(delta);
        grid.setReference(zReference);
        grid.setReferenceIndex(0.5 + Math.rint((zReference - zRange.min()) / delta));

        CoordinateAxis zAxis = grid.getAxis();   

        if(isLogarithmic) zAxis.setUnit(new Unit(spectralUnit.name(), 1.0));
        else zAxis.setUnit(spectralUnit);

        zAxis.setLabel(useWavelength ? "Wavelength" : "Frequency");
        zAxis.setFancyLabel(useWavelength ? GreekLetter.lambda + "" : GreekLetter.nu + "");

        int sizeZ = 1 + (int) Math.ceil(zRange.span() / delta);

        if(CRUSH.debug) debug("spectral bins: " + sizeZ);

        return sizeZ;
    }


    private void createCube() {        
        cube = new Observation2D1(Double.class, Double.class, FlagCompanion.Type.INT);

        cube.setParallel(CRUSH.maxThreads); 
        cube.setGrid2D(getGrid());
        cube.setGrid1D(new Grid1D(3));
        cube.setCriticalFlags(~FLAG_MASK);  

        cube.addLocalUnit(getNativeUnit());
        cube.addLocalUnit(getJanskyUnit(), "Jy, jansky, Jansky, JY, jy, JANSKY");
        cube.addLocalUnit(getKelvinUnit(), "K, kelvin, Kelvin, KELVIN");  

        cube.getPlaneTemplate().setDisplayGridUnit(getInstrument().getSizeUnit());

        FitsProperties properties = cube.getPlaneTemplate().getFitsProperties();
        properties.setInstrumentName(getInstrument().getName());
        properties.setCreatorName(CRUSH.class.getSimpleName());
        properties.setCopyright(CRUSH.getCopyrightString());     

        if(hasOption("unit")) {
            String unitName = option("unit").getValue();
            cube.setUnit(unitName);
        }
    }



    @Override
    public void createFrom(Collection<? extends Scan<?>> collection) throws Exception {

        createCube();

        super.createFrom(collection);  

        for(Observation2D plane : cube.getPlanes()) {
            plane.getFitsProperties().setObjectName(getFirstScan().getSourceName());
            plane.setUnderlyingBeam(getAverageResolution());
        }

        CRUSH.info(this, "\n" + cube.getPlaneTemplate().getInfo());

        Grid1D g = cube.getGrid1D();
        Unit zUnit = g.getAxis().getUnit();

        CRUSH.info(this, "Spectral Reference: " + Util.s4.format(g.getReferenceValue(0) / zUnit.value()) + " " + zUnit.name());
        CRUSH.info(this, "Spectral Bins: " + sizeZ());

        base = Image2D1.create(Double.class, sizeX(), sizeY(), sizeZ());

        // TODO
        /*
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
    public void editHeader(Header header) throws HeaderCardException {
        super.editHeader(header);
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("LOGSPEC", isLogarithmic, "Whether the spectral binning is logarithmic."));
    }

    @Override
    public void parseHeader(Header header) throws Exception {
        super.parseHeader(header);
        isLogarithmic = header.getBooleanValue("LOGSPEC");
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
        cube.setSize(sizeX, sizeY, findSizeZ());

        // Set the underlying beam size for each plane separately...
        Grid1D g = cube.getGrid1D();
        double rf0 = getInstrument().getResolution() * getInstrument().getFrequency();

        for(int i=sizeZ(); --i >= 0; ) {
            double f = g.coordAt(i);
            if(isLogarithmic) f = Math.pow(10.0, f);
            cube.getPlane(i).setUnderlyingBeam(rf0 / f);
        }
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

    public final int getFrequencyBin(Frame exposure, Channel channel) {
        return getFrequencyBin(exposure.getChannelFrequency(channel));
    }

    public int getFrequencyBin(double frequency) {
        if(useWavelength) frequency = Constant.c / frequency;
        if(isLogarithmic) frequency = Math.log10(frequency / spectralUnit.value());
        return (int) Math.round(cube.getGrid1D().indexOf(frequency));
    }


    @Override
    protected void addPoint(final Index2D index, final Channel channel, final Frame exposure, final double G, final double dt) {    
        Observation2D plane = cube.getPlane(getFrequencyBin(exposure, channel));
        plane.accumulateAt(index.i(), index.j(), exposure.data[channel.getIndex()], G, exposure.relativeWeight / channel.variance, dt);
    }

    public boolean isMasked(int i, int j, int k) {
        return cube.getPlane(k).isFlagged(i, j, FLAG_MASK);
    }


    @Override
    public void addModelData(SourceModel model, double weight) {
        cube.accumulate(((SpectralCube) model).cube, 1.0, weight);   
    }


    @Override
    public void mergeAccumulate(AbstractSource2D other) {
        cube.mergeAccumulate(((SpectralCube) other).cube);      
    }

    @Override
    protected void sync(Frame exposure, Pixel pixel, Index2D index, double fG, double[] sourceGain, double[] syncGain) {
        for(final Channel channel : pixel) sync(exposure, channel, index, fG, sourceGain, syncGain);
    }

    protected void sync(Frame exposure, Channel channel, Index2D index, double fG, double[] sourceGain, double[] syncGain) {
        final int c = channel.getIndex();
        final int k = getFrequencyBin(exposure, channel);
        Observation2D plane = cube.getPlane(k);

        // The use of iterables is a minor performance hit only (~3% overall)
        if(!plane.isValid(index)) return;

        final float mapValue = plane.get(index).floatValue();
        final float baseValue = base.getPlane(k).getValid(index, 0.0F).floatValue();

        // Do not check for flags, to get a true difference image...
        exposure.data[c] -= fG * (sourceGain[c] * mapValue - syncGain[c] * baseValue);  

        // Do the blanking here...
        if(isMasked(index.i(), index.j(), k)) exposure.sampleFlag[c] |= Frame.SAMPLE_SOURCE_BLANK;
        else exposure.sampleFlag[c] &= ~Frame.SAMPLE_SOURCE_BLANK;
    }

    @Override
    public double covariantPoints() {
        return cube.getPlane(0).getPointsPerSmoothingBeam();      
    }

    @Override
    protected void calcCoupling(Integration<?> integration, Collection<? extends Pixel> pixels, double[] sourceGain,
            double[] syncGain) {
        // TODO Auto-generated method stub  
    }


    @Override
    public void setBase() {
        base.copyOf(cube, false);
    }

    @Override
    public void resetProcessing() {
        super.resetProcessing();
        cube.resetProcessing();
    }


    @Override
    public String getSourceName() {
        return cube.getPlane(0).getFitsProperties().getObjectName();
    }

    @Override
    public Unit getUnit() {
        return cube.getUnit();
    }


    @Override
    public Object getTableEntry(String name) {  
        if(name.startsWith("map.")) return cube.getPlane(0).getTableEntry(name.substring(4));
        return super.getTableEntry(name);
    }


    @Override
    public void processFinal() {
        // TODO Auto-generated method stub   
    }

    @Override
    public void level(boolean isRobust) {
        for(Data2D plane : cube.getPlanes()) plane.level(isRobust);
    }

    @Override
    public Map2D getMap2D() {     
        return hasOption("write.png.median") ? cube.getMedianZ() : cube.getAverageZ();
    }


    @Override
    public void filter(double filterScale, double filterBlanking) {
        for(Observation2D plane : cube.getPlanes()) {
            Validating2D filterBlank = new RangeRestricted2D(plane.getSignificance(), new Range(-filterBlanking, filterBlanking));

            plane.filterAbove(filterScale, filterBlank);
            plane.setFilterBlanking(filterBlanking);
        }
    }

    @Override
    public void setFiltering(double FWHM) {
        for(Observation2D plane : cube.getPlanes()) plane.updateFiltering(FWHM);
    }


    @Override
    public void resetFiltering() {
        cube.resetFiltering();
    }


    @Override
    public Observation2D1 getData() {
        return cube;
    }


    @Override
    public void addBase() {
        cube.add(base);
    }

    @Override
    public void smoothTo(double FWHM) {
        updateSmoothingPolicy();
        cube.smoothXYTo(FWHM);
    }

    @Override
    public void filterBeamCorrect() {
        cube.filterBeamCorrect();
    }

    @Override
    public void memCorrect(double lambda) {
        cube.memCorrect(null, lambda);
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

        final Validating<Index3D> neighbors = cube.getNeighborValidator(minNeighbors);
        final Validating<Index3D> s2n = new RangeRestricted3D(cube.getSignificance(), s2nRange);

        cube.loop(new ParallelPointOp.Simple<Index3D>() {
            @Override
            public void process(Index3D index) {
                if(neighbors.isValid(index) && s2n.isValid(index)) cube.unflag(index, FLAG_MASK);
                else cube.flag(index, FLAG_MASK);
            }       
        });

    }

    @Override
    public void write() throws Exception {    
        super.write();

        String path = getOutputPath();

        if(hasOption("write.flattened")) {
            writeFlattenedFits(path + File.separator + getCoreName() + ".flattened.fits");
        }

        if(hasOption("write.fieldspec")) {
            plotSpectrum(path + File.separator + getCoreName() + ".fieldspec");
        }
    }

    /**
     * Plots the peak spectrum at the observed source position. 
     * 
     * 
     * @param coreName
     * @throws IOException
     */
    public void plotSpectrum(String coreName) throws IOException {
        String gnuplot = "gnuplot";
        if(hasOption("gnuplot")) {
            String cmd = option("gnuplot").getValue();
            if(cmd.length() > 0) gnuplot = Util.getSystemPath(cmd);
        }

        final Data1D mean = Samples1D.createType(Double.class, cube.sizeZ());
        final Data1D rms = Samples1D.createType(Double.class, cube.sizeZ());

        final Vector2D refIndex = cube.getGrid2D().getReferenceIndex();

        DataPoint smoothedValue = new DataPoint();
        SplineSet<Vector2D> splines = new SplineSet<>(2);

        for(int i=cube.sizeZ(); --i >= 0; ) {
            Observation2D plane = cube.getPlane(i);
            Referenced2D beam = plane.getUnderlyingBeam().getBeam(plane.getGrid());
            plane.getSmoothedValueAtIndex(refIndex, beam, beam.getReferenceIndex(), plane.getWeights(), splines, smoothedValue);
            mean.set(i, smoothedValue.value());
            rms.set(i, smoothedValue.rms());
        }

        System.err.println();

        Data1D spectrum = new Overlay1D(mean) {
            @Override
            protected String getASCIITableHeader(Grid1D grid, String yName) {
                return super.getASCIITableHeader(grid, yName) + "\trms";
            }

            @Override
            protected String getASCIITableEntry(int index, Grid1D grid, String nanValue) {
                double rmsValue = 1.0 / Math.sqrt(rms.get(index).doubleValue()) / getUnit().value();
                String sRMS = Double.isNaN(rmsValue) ? nanValue : Util.s3.format(rmsValue);
                return super.getASCIITableEntry(index, grid, nanValue) + "\t" + sRMS;
            }         

            @Override
            protected void configGnuplot(PrintWriter plot, String coreName, Grid1D grid, String yName, Configurator options) throws IOException {       
                super.configGnuplot(plot, coreName, grid, yName, options);
                plot.println("set yra [0:*]");
                plot.println("set style fill transparent solid 0.3");
                if(isLogarithmic) {
                    plot.println("set log x");
                    plot.println("set xra [" + Math.pow(10.0, grid.coordAt(-0.5)) + ":" + Math.pow(10.0, grid.coordAt(sizeZ()-0.5)) + "]");

                    // Add minor tick labels explicitly
                    int from = (int) Math.floor(grid.coordAt(-0.5));
                    int to = (int) Math.ceil(grid.coordAt(sizeZ()-0.5));

                    if(to - from < 3) {
                        StringBuffer buf = new StringBuffer();
                        double step = Math.pow(10.0, from);
                        double last = Math.pow(10.0, to);
                        

                        for(int k=2; ; k++) {
                            double f = k * step;
                            if(f >= last) break;
                            
                            if(Math.abs(f - 10.0 * step) < 1e-6*step) step *= 10; 
                            else {
                                if(k != 2) buf.append(", ");
                                buf.append(Util.f1.format(f));
                            }
                        }

                        plot.println("set xtics add (" + new String(buf) + ")");
                    }
                }
            }

            @Override
            public String getPlotXValues() {
                return isLogarithmic ? "(10**($1))" : "1";
            }

            @Override
            protected String getPlotCommand(String coreName) {    
                return super.getPlotCommand(coreName) + 
                        ",\\\n '' using " + getPlotXValues() + ":" + getPlotYValues() + ":(0.5 * delta):4 notitle with boxxyerr lt 1" + 
                        ",\\\n0 notitle lt -1";
            }
        };

        String nanValue = hasOption("write.fieldspec.nodata") ? option("write.fieldspec.nodata").getValue() : "---";

        spectrum.writeASCIITable(coreName, cube.getGrid1D(), "Flux", nanValue);
        spectrum.gnuplot(coreName, cube.getGrid1D(), "Mean Flux", gnuplot, option("write.fieldspec"));
    }

    public void writeFlattenedFits(String fileName) throws FitsException, IOException {  
        Map2D image = getMap2D();

        try(Fits fits = image.createFits(Float.class)) { 

            for(int i=fits.getNumberOfHDUs(); --i >= 0; ) {
                Header header = fits.getHDU(i).getHeader();
                image.editHeader(header);
            }   

            addScanHDUsTo(fits);

            if(hasOption("write.flattened.gzip")) FitsToolkit.writeGZIP(fits, fileName);
            else FitsToolkit.write(fits, fileName);

            fits.close();
        }
    }


    private static double log10Octave = Constant.log2 / Constant.log2;

}
