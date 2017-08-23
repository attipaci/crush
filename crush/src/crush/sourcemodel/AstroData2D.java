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

import crush.CRUSH;
import crush.Instrument;
import crush.Scan;
import jnum.Configurator;
import jnum.LockedException;
import jnum.Util;
import jnum.data.Data;
import jnum.math.Range;

public abstract class AstroData2D<DataType extends Data<?,?,?>> extends AstroModel2D {

    /**
     * 
     */
    private static final long serialVersionUID = -6317603664492428554L;

    public AstroData2D(Instrument<?> instrument) {
        super(instrument);
    }

    public abstract DataType getData();
    
    public abstract Data<?,?,?> getWeights();
    
    public abstract Data<?,?,?> getNoise();
    
    public abstract Data<?,?,?> getSignificance();
    
    public abstract Data<?,?,?> getExposures();
    
    

    public abstract void endAccumulation();

    public abstract void addBase();

    public abstract void smoothTo(double FWHM);

    public abstract void filter(double filterFWHM, double filterBlanking, boolean useFFT);

    public abstract void resetFiltering();

    public abstract void filterBeamCorrect();

    public abstract void memCorrect(double lambda);

    public abstract void updateMask(double blankingLevel, int minNeighbors);

    
   
    
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

        try { filter.mapValueTo("fwhm"); }
        catch(LockedException e) {} // TODO...

        String mode = filter.isConfigured("type") ? filter.get("type").getValue() : "convolution";
        String directive = "auto";

        if(filter.isConfigured("fwhm")) directive = filter.get("fwhm").getValue().toLowerCase();

        double filterScale = directive.equals("auto") ? 5.0 * getSourceSize() : Double.parseDouble(directive) * getInstrument().getSizeUnit().value();
        double filterBlanking = (allowBlanking && filter.isConfigured("blank")) ? filter.get("blank").getDouble() : Double.POSITIVE_INFINITY;

        filter(filterScale, filterBlanking, mode.equalsIgnoreCase("fft"));  
    }




    @Override
    public void process(Scan<?, ?> scan) {
        DataType data = getData();

        endAccumulation();          
        addBase();

        if(enableLevel) data.level(true);

        if(hasSourceOption("despike")) {
            Configurator despike = sourceOption("despike");
            double level = 10.0;

            try { despike.mapValueTo("level"); }
            catch(LockedException e) {} // TODO...

            if(despike.isConfigured("level")) level = despike.get("level").getDouble();
            data.despike(level);
        }

        filter(NO_BLANKING);      

        //validate();

        scan.weight = 1.0;              
        if(hasOption("weighting.scans")) {
            Configurator weighting = option("weighting.scans");
            String method = "rms";
            if(weighting.isConfigured("method")) method = weighting.get("method").getValue().toLowerCase();
            else if(weighting.getValue().length() > 0) method = weighting.getValue().toLowerCase();
            scan.weight = 1.0 / getChi2(method.equals("robust"));
            if(Double.isNaN(scan.weight)) scan.weight = 0.0;
        }

        if(hasOption("scanmaps")) {
            try { writeFits(CRUSH.workPath + File.separator + "scan-" + (int)scan.getMJD() + "-" + scan.getID() + ".fits"); }
            catch(Exception e) { error(e); }
        }


    }
    
    

    @Override
    public void process() throws Exception {
        // TODO --> 'spectral.smooth'     
        
        endAccumulation();

        nextGeneration(); // Increment the map generation...

        getInstrument().setResolution(getAverageResolution());

        if(enableLevel) addProcessBrief("{level} ");

        if(hasSourceOption("despike")) addProcessBrief("{despike} ");

        if(hasSourceOption("filter") && getSourceSize() > 0.0) addProcessBrief("{filter} ");

        if(enableWeighting) if(hasOption("weighting.scans"))
            for(Scan<?,?> scan : getScans()) addProcessBrief("{" + Util.f2.format(scan.weight) + "} ");

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
            filter(ENABLE_BLANKING);
            filterBeamCorrect();
        }


        // Noise and exposure clip after smoothing for evened-out coverage...
        // Eposure clip
        if(hasOption("exposureclip")) {
            addProcessBrief("(exposureclip) ");
            Data<?,?,?> exposure = getExposures();
            exposure.restrictRange(new Range(option("exposureclip").getDouble() * exposure.select(0.95), Double.POSITIVE_INFINITY));
        }

        // Noise clip
        if(hasOption("noiseclip")) {
            addProcessBrief("(noiseclip) ");
            Data<?,?,?> rms = getNoise();
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
        //map.validate();

        if(hasSourceOption("mem")) {
            addProcessBrief("(MEM) ");
            double lambda = hasSourceOption("mem.lambda") ? sourceOption("mem.lambda").getDouble() : 0.1;
            memCorrect(lambda);
        }


        if(hasSourceOption("intermediates")) {
            try { writeFits(CRUSH.workPath + File.separator + "intermediate.fits"); }
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

  
    
    

    
    public static long FLAG_MASK = 1L<<16;

}
