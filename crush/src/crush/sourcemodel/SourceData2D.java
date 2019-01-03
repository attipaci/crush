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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import crush.Instrument;
import crush.Scan;
import jnum.Configurator;
import jnum.LockedException;
import jnum.Util;
import jnum.data.Data;
import jnum.data.Index;
import jnum.data.Observations;
import jnum.fits.FitsToolkit;
import jnum.math.Range;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;

public abstract class SourceData2D<IndexType extends Index<IndexType>, DataType extends Data<IndexType> & Observations<? extends Data<IndexType>>> extends SourceModel2D {

    /**
     * 
     */
    private static final long serialVersionUID = -6317603664492428554L;

    public SourceData2D(Instrument<?> instrument) {
        super(instrument);
    }
    
    @Override
    public void createFrom(Collection<? extends Scan<?,?>> collection) throws Exception {
        super.createFrom(collection);  
        if(hasOption("unit")) getData().setUnit(option("unit").getValue());
    }

    public abstract DataType getData();
   
   
    public abstract void addBase();

    public abstract void smoothTo(double FWHM);

    public abstract void filter(double filterFWHM, double filterBlanking, boolean useFFT);

    public abstract void setFiltering(double FWHM);
    
    public abstract void resetFiltering();
    
    public abstract void filterBeamCorrect();

    public abstract void memCorrect(double lambda);

    public abstract void updateMask(double blankingLevel, int minNeighbors);

    
    
    public final Data<?> getWeights() { return getData().getWeights(); }
    
    public final Data<?> getNoise() { return getData().getNoise(); }
    
    public final Data<?> getSignificance() { return getData().getSignificance(); }
    
    public final Data<?> getExposures() { return getData().getExposures(); }

    public final void endAccumulation() { getData().endAccumulation(); }


    @Override
    public final ExecutorService getExecutor() { return getData().getExecutor(); }
    
    @Override
    public final void setExecutor(ExecutorService e) { getData().setExecutor(e); }

    @Override
    public final int getParallel() { return getData().getParallel(); }
    
    @Override
    public void noParallel() { getData().noParallel(); }

    @Override
    public void setParallel(int threads) { getData().setParallel(threads); }
   
   
    @Override
    public void clearContent() { getData().clear(); }

    
    @Override
    public final boolean isEmpty() { return getData().countPoints() == 0; }

    @Override
    public final int countPoints() { return getData().countPoints(); }

   
    
    
    public final double getChi2(boolean isRobust) {
        return isRobust ? getSignificance().getRobustVariance() : getSignificance().getVariance();
    }
    
   

    public void smooth() {
        smoothTo(getSmoothing());
    }
        
    public void filter(boolean allowBlanking) {
        if(!hasSourceOption("filter") || getSourceSize() <= 0.0) {
            resetFiltering();
            return;
        }

        Configurator filter = sourceOption("filter");
        String mode = filter.hasOption("type") ? filter.option("type").getValue() : "convolution";

        double filterBlanking = (allowBlanking && filter.hasOption("blank")) ? filter.option("blank").getDouble() : Double.NaN;

        filter(getFilterScale(filter), filterBlanking, mode.equalsIgnoreCase("fft"));  
    }

    public double getFilterScale(Configurator filter) {
        try { filter.mapValueTo("fwhm"); }
        catch(LockedException e) {} // TODO...

        String directive = "auto";

        if(filter.hasOption("fwhm")) directive = filter.option("fwhm").getValue().toLowerCase();

        return directive.equals("auto") ? 5.0 * getSourceSize() : Double.parseDouble(directive) * getInstrument().getSizeUnit().value();

    }


    @Override
    public void process(Scan<?, ?> scan) {
        DataType data = getData();
        
        endAccumulation();          
        addBase();

        if(enableLevel) level(true);

        if(hasSourceOption("despike")) {
            Configurator despike = sourceOption("despike");
            double level = 10.0;

            try { despike.mapValueTo("level"); }
            catch(LockedException e) {} // TODO...

            if(despike.hasOption("level")) level = despike.option("level").getDouble();
            data.despike(level);
        }
        
        filter(NO_BLANKING);   
        
        //data.validate();
        
        scan.weight = 1.0;              
        if(hasOption("weighting.scans")) {
            Configurator weighting = option("weighting.scans");
            
            try { weighting.mapValueTo("method"); } 
            catch (LockedException e) {}
            
            String method = "rms";
            if(weighting.hasOption("method")) method = weighting.option("method").getValue().toLowerCase();
            scan.weight = 1.0 / getChi2(method.equals("robust"));
            if(Double.isNaN(scan.weight)) scan.weight = 0.0;
        }


        if(hasOption("scanmaps")) {
            try { writeFits(getOutputPath() + File.separator + "scan-" + (int)scan.getMJD() + "-" + scan.getID() + ".fits"); }
            catch(Exception e) { error(e); }
        }


    }
    
    
    public void level(boolean isRobust) {
        getData().level(isRobust);
    }

    @Override
    public void process() throws Exception {
        endAccumulation();
        
        nextGeneration(); // Increment the map generation...

        getInstrument().setResolution(getAverageResolution());

        if(enableLevel) addProcessBrief("{level} ");

        if(hasSourceOption("despike")) addProcessBrief("{despike} ");

        if(hasSourceOption("filter") && getSourceSize() > 0.0) addProcessBrief("{filter} ");

        if(enableWeighting) if(hasOption("weighting.scans"))
            for(Scan<?,?> scan : getScans()) addProcessBrief("{" + Util.f2.format(1.0/Math.sqrt(scan.weight)) + "x} ");

        if(hasSourceOption("redundancy"))  {
            addProcessBrief("(check) ");
            double minIntTime = getInstrument().integrationTime * sourceOption("redundancy").getInt();
            getExposures().restrictRange(new Range(minIntTime, Double.POSITIVE_INFINITY));
        }

        if(hasOption("smooth") && !hasOption("smooth.external")) {
            addProcessBrief("(smooth) ");
            smooth();        
        }


        // Apply the filtering to the final map, to reflect the correct blanking
        // level...
        if(hasSourceOption("filter")) {
            addProcessBrief("(filter) ");
            //setFiltering(getFilterScale(sourceOption("filter")));
            filter(ENABLE_BLANKING);
            filterBeamCorrect();
        }
        

        // Noise and exposure clip after smoothing for evened-out coverage...
        // Eposure clip
        if(hasOption("exposureclip")) {
            addProcessBrief("(exposureclip) ");
            Data<?> exposure = getExposures();
            exposure.restrictRange(new Range(option("exposureclip").getDouble() * exposure.select(0.95), Double.POSITIVE_INFINITY));
        }

        // Noise clip
        if(hasOption("noiseclip")) {
            addProcessBrief("(noiseclip) ");
            Data<?> rms = getNoise();
            rms.restrictRange(new Range(0, rms.select(0.05) * option("noiseclip").getDouble()));
        }


        if(enableBias) if(hasOption("clip")) {  
            double clipLevel = option("clip").getDouble();
            addProcessBrief("(clip:" + clipLevel + ") ");
            final int sign = hasSourceOption("sign") ? sourceOption("sign").getSign() : 0;

            Range s2nReject = new Range(-clipLevel, clipLevel);

            if(sign > 0) s2nReject.setMin(Double.NEGATIVE_INFINITY);
            else if(sign < 0) s2nReject.setMax(Double.POSITIVE_INFINITY);

            getSignificance().discardRange(s2nReject);
        }

        // discard any invalid data...
        //getData().validate();

        if(hasSourceOption("mem")) {
            addProcessBrief("(MEM) ");
            double lambda = hasSourceOption("mem.lambda") ? sourceOption("mem.lambda").getDouble() : 0.1;
            memCorrect(lambda);
        }


        if(hasSourceOption("intermediates")) {
            try { writeFits(getOutputPath() + File.separator + "intermediate.fits"); }
            catch(Exception e) { error(e); }
        }

        // Coupled with blanking...
        if(!hasSourceOption("nosync")) {
            if(enableBias && hasOption("blank")) {
                final double blankingLevel = getBlankingLevel();
                addProcessBrief("(blank:" + blankingLevel + ") ");
                updateMask(blankingLevel, 3);
            }
            else updateMask(Double.NaN, 3);
        } 

        // Run the garbage collector
        //System.gc();
    }

    @Override
    public void processFinal() {
        getData().clearHistory();
    }
   
    @Override
    public void writeFits(String fileName) throws FitsException, IOException {        
        Fits fits = getData().createFits(Float.class); 
        
        int nHDU = fits.getNumberOfHDUs();
        for(int i=0; i<nHDU; i++) {
            Header header = fits.getHDU(i).getHeader();
            editHeader(header);
            File f = new File(fileName);
            header.addValue("FILENAME", f.getName(), "Name at creation");       
        }   
        
        addScanHDUsTo(fits);
        
        if(hasOption("gzip")) FitsToolkit.writeGZIP(fits, fileName);
        else FitsToolkit.write(fits, fileName);
        
        fits.close();
    }
    
    
    public static long FLAG_MASK = 1L<<16;

}
