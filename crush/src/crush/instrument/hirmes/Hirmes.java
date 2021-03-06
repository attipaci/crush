/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.hirmes;

import java.io.IOException;
import java.util.*;

import crush.*;
import crush.sourcemodel.IntensityMap;
import crush.sourcemodel.SpectralCube;
import crush.telescope.sofia.SofiaInstrument;
import crush.telescope.sofia.SofiaData;
import crush.telescope.sofia.SofiaHeader;
import crush.telescope.sofia.SofiaSpectroscopyData;
import jnum.Constant;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;
import jnum.data.SimpleInterpolator;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;
import nom.tam.fits.*;
import nom.tam.util.Cursor;


public class Hirmes extends SofiaInstrument<HirmesPixel> {
    /**
     * 
     */
    private static final long serialVersionUID = 6205260168688969947L;

    int detArray = LORES_ARRAY;
    int mode = IMAGING_MODE;

    int hiresColUsed = -1;                  // [0-7] Hires strip index used.   

    double gratingAngle;                    // -10 to -20 degrees
    double fpiConstant = Double.NaN;        // FPI dispersion contant
    int gratingIndex;                       // [0-2]

    boolean darkSquidCorrection = false;
    int[] darkSquidLookup;                  // col

    boolean applyBandwidthCorrection = true;     // Whether to account for pixel bandwidths when converting
    // between flux density and incident power.
    // TODO only diagnostic. Remove later...

    //int[][] detectorBias;                 // array [2], line [rows]

    private double z = 0.0;                 // Doppler shift.

    SimpleInterpolator transmissionTable;

    public Hirmes() {
        super("hirmes", pixels);
    }

    @Override
    public String getFileID() { return "HIR"; }

    @Override
    public HirmesLayout getLayoutInstance() { return new HirmesLayout(this); }

    @Override
    public HirmesLayout createLayout() { return (HirmesLayout) super.createLayout(); }

    @Override
    public HirmesLayout getLayout() { return (HirmesLayout) super.getLayout(); }

    @Override
    public Hirmes copy() {
        Hirmes copy = (Hirmes) super.copy();

        if(darkSquidLookup != null) copy.darkSquidLookup = Arrays.copyOf(darkSquidLookup, darkSquidLookup.length);      

        /*
        if(detectorBias != null) {
            copy.detectorBias = new int[detectorBias.length][];
            for(int i=detectorBias.length; --i >= 0; ) if(detectorBias[i] != null) 
                copy.detectorBias[i] = Arrays.copyOf(detectorBias[i], detectorBias[i].length);
        }
         */

        return copy;
    }


    @Override
    public HirmesPixel getChannelInstance(int backendIndex) {
        return new HirmesPixel(this, backendIndex);
    }

    @Override
    public Scan<?> getScanInstance() {
        return new HirmesScan(this);
    }

    @Override
    protected void createDivisions() {
        super.createDivisions();

        try { addDivision(getDivision("subarrays", HirmesPixel.class.getField("sub"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
        catch(Exception e) { error(e); }    

        try { addDivision(getDivision("bias", HirmesPixel.class.getField("biasLine"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
        catch(Exception e) { error(e); } 

        try { addDivision(getDivision("series", HirmesPixel.class.getField("seriesArray"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
        catch(Exception e) { error(e); } 

        // If correction was applied at validation, then only decorrelate detectors
        // Otherwise, decorrelate including the dark SQUIDs...
        int muxSkipFlag = hasOption("darkcorrect") ? Channel.FLAG_DEAD | Channel.FLAG_BLIND : Channel.FLAG_DEAD;

        try { 
            ChannelDivision<HirmesPixel> muxDivision = getDivision("mux", HirmesPixel.class.getField("mux"), muxSkipFlag); 
            addDivision(muxDivision);

            Comparator<HirmesPixel> pinSorter = new Comparator<HirmesPixel>() {
                @Override
                public int compare(HirmesPixel p1, HirmesPixel p2) {
                    if(p1.pin == p2.pin) return 0;
                    return p1.pin > p2.pin ? 1 : -1;
                }   
            };

            // Order mux channels in pin order...
            for(ChannelGroup<HirmesPixel> mux : muxDivision) Collections.sort(mux, pinSorter); 
        }
        catch(Exception e) { error(e); }

        try { addDivision(getDivision("elevensies", HirmesPixel.class.getField("elevensy"), muxSkipFlag)); }
        catch(Exception e) { error(e); }

        try { addDivision(getDivision("rows", HirmesPixel.class.getField("row"), muxSkipFlag)); }
        catch(Exception e) { error(e); }  

        try { addDivision(getDivision("cols", HirmesPixel.class.getField("col"), muxSkipFlag)); }
        catch(Exception e) { error(e); }  

        try { addDivision(getDivision("pins", HirmesPixel.class.getField("pin"), muxSkipFlag)); }
        catch(Exception e) { error(e); }  
    }


    @Override
    protected void createModalities() {
        super.createModalities();

        try { 
            CorrelatedModality subMode = new CorrelatedModality("subarrays", "S", divisions.get("subarrays"), HirmesPixel.class.getField("subGain")); 
            //subMode.solveGains = false;
            subMode.setGainFlag(HirmesPixel.FLAG_SUB);
            addModality(subMode);
        }
        catch(NoSuchFieldException e) { error(e); }

        try {
            CorrelatedModality biasMode = new CorrelatedModality("bias", "b", divisions.get("bias"), HirmesPixel.class.getField("biasGain"));     
            //biasMode.solveGains = false;
            biasMode.setGainFlag(HirmesPixel.FLAG_BIAS);
            addModality(biasMode);
        }
        catch(NoSuchFieldException e) { error(e); }  

        try {
            CorrelatedModality seriesMode = new CorrelatedModality("series", "s", divisions.get("series"), HirmesPixel.class.getField("seriesGain"));     
            //seriesMode.solveGains = false;
            seriesMode.setGainFlag(HirmesPixel.FLAG_SERIES_ARRAY);
            addModality(seriesMode);
        }
        catch(NoSuchFieldException e) { error(e); } 

        try {
            CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), HirmesPixel.class.getField("muxGain"));     
            //muxMode.solveGains = false;
            muxMode.setGainFlag(HirmesPixel.FLAG_MUX);
            addModality(muxMode);
        }
        catch(NoSuchFieldException e) { error(e); } 

        try {
            CorrelatedModality pinMode = new CorrelatedModality("pins", "p", divisions.get("pins"), HirmesPixel.class.getField("pinGain"));     
            //pinMode.solveGains = false;
            pinMode.setGainFlag(HirmesPixel.FLAG_PIN);
            addModality(pinMode);
        }
        catch(NoSuchFieldException e) { error(e); } 

        try {
            CorrelatedModality elevensiesMode = new CorrelatedModality("elevensies", "e", divisions.get("elevensies"), HirmesPixel.class.getField("elevensyGain"));     
            //elevensiesMode.solveGains = false;
            elevensiesMode.setGainFlag(HirmesPixel.FLAG_ELEVENSY);
            addModality(elevensiesMode);
        }
        catch(NoSuchFieldException e) { error(e); } 

        try { 
            Modality<?> rowMode = new CorrelatedModality("rows", "r", divisions.get("rows"), HirmesPixel.class.getField("pinGain")); 
            rowMode.setGainFlag(HirmesPixel.FLAG_ROW);
            addModality(rowMode);
        }
        catch(NoSuchFieldException e) { error(e); }

        try { 
            Modality<?> colMode = new CorrelatedModality("cols", "c", divisions.get("cols"), HirmesPixel.class.getField("colGain")); 
            colMode.setGainFlag(HirmesPixel.FLAG_COL);
            addModality(colMode);
        }
        catch(NoSuchFieldException e) { error(e); }
    }

    @Override
    public void parseHeader(SofiaHeader header) {
        super.parseHeader(header);

        populate(pixels);
        createLayout();

        spectral = new SofiaSpectroscopyData(header);

        samplingInterval = integrationTime = 1.0 / (header.getDouble("SMPLFREQ", Double.NaN) * Unit.Hz);
        if(samplingInterval < 0.0) samplingInterval = integrationTime = Double.NaN;

        gratingAngle = header.getDouble("GRATANGL", Double.NaN) * Unit.deg;
        fpiConstant = header.getDouble("FPIK", Double.NaN);

        if(array.detectorName.equalsIgnoreCase("HIRMES-LOW")) detArray = LORES_ARRAY;
        else if(array.detectorName.equalsIgnoreCase("HIRMES-HI")) detArray = HIRES_ARRAY;
        String instrumentMode = instrumentData.instrumentMode;

        if(instrumentMode.equalsIgnoreCase("SPECTRAL_IMAGING")) mode = IMAGING_MODE;
        else if(instrumentMode.equalsIgnoreCase("LOW-RES")) mode = LORES_MODE;
        else if(instrumentMode.equalsIgnoreCase("MED-RES")) mode = MIDRES_MODE;
        else if(instrumentMode.equalsIgnoreCase("HI-RES")) mode = HIRES_MODE;

        hiresColUsed = header.getInt("HIRESSUB", -1);

        // Doppler correction to rest frame...
        z = hasOption("spectral.obs") ? spectral.getRedshift() : 0.0;

        // Set the spectral grid to a default value...
        if(!hasOption("spectral.grid") && !hasOption("spectral.r")) {
            try { 
                double R = getSpectralResolution();
                getOptions().process("spectral.r", R + ""); 
                info("Set spectral resolution to R ~ " + Util.f1.format(R));
            }
            catch(LockedException e) {}
        }

        if(mode == LORES_MODE || mode == MIDRES_MODE) gratingIndex = getGratingIndex(Constant.c / spectral.observingFrequency);  

        applyBandwidthCorrection = !hasOption("flatbw");

        getLayout().parseHeader(header);        
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {             
        // Add HAWC+ specific keywords
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ HIRMES Header Keys ------>", false));
        c.add(SofiaData.makeCard("SMPLFREQ", 1.0 / samplingInterval, "(Hz) Detector readout rate."));
        c.add(SofiaData.makeCard("GRATANGL", gratingAngle / Unit.deg, "Grating angle"));
        c.add(SofiaData.makeCard("FPIK", fpiConstant, "FPI dispersison constant"));
        c.add(new HeaderCard("HIRESSUB", hiresColUsed, "[0-7] Hires col used."));
    }

    
    boolean incompleteTransmission = false;

    double getRelativeTransmission(double fobs) {
        if(transmissionTable == null) return 1.0;
        
        
        try { return transmissionTable.getValue(Constant.c / fobs / Unit.um); }
        catch(ArrayIndexOutOfBoundsException e) {
            if(!incompleteTransmission) {
                incompleteTransmission = true;
                warning("Incomplete atmospheric transmission coverage.");
            }
        }
        return Double.NaN;
    }

    final String getSubarrayID(int sub) {
        return subID[sub];
    }

    final int getSubarrayIndex(String id) {
        for(int sub=0; sub < subarrays; sub++) if(subID[sub].equalsIgnoreCase(id)) return sub;
        return -1;        
    }



    @Override
    public int maxPixels() {
        return storeChannels;
    }    

    @Override
    public String getChannelDataHeader() {
        return super.getChannelDataHeader() + "\teff\tGmux\tGpin\tidx\tsub\tmux\tpin\tBW";
    }

    @Override
    public void readData(BasicHDU<?>[] hdu) throws Exception {   
        /* TODO
        for(int i=1; i<hdu.length; i++) {
            String extName = hdu[i].getHeader().getStringValue("EXTNAME").toLowerCase(); 
            if(extName.equals("configuration")) parseConfigurationHDU(hdu[i]);
        }
         */
    }

    /*
    private void parseConfigurationHDU(BasicHDU<?> hdu) {      
        Header header = hdu.getHeader();

        detectorBias = new int[subarrays][MCE_BIAS_LINES];

        int found = 0;
        for(int mce=0; mce < subarrays; mce++) {
            String key = "HIERARCH.MCE" + mce + "_TES_BIAS";
            String values = header.getStringValue(key);
            if(values != null) {
                parseTESBias(mceSubarray[mce], values);
                found++;
            }
        }

        info("Parsing HAWC+ TES bias. Found for " + found + " MCEs.");
    }

    private void parseTESBias(int sub, String values) {     
        StringTokenizer tokens = new StringTokenizer(values, ", \t;");
        for(int i=0; i<MCE_BIAS_LINES; i++) {
            if(!tokens.hasMoreTokens()) {
                warning("Missing TES bias values for subarray " + sub);
                break;
            }
            detectorBias[sub][i] = Integer.parseInt(tokens.nextToken());
        }
    }
     */

    @Override
    public void validate() {
        super.validate();
        darkSquidCorrection = hasOption("darkcorrect");
        createDarkSquidLookup();
    }


    @Override
    public boolean slim(int discardFlags, boolean reindex) {
        boolean slimmed = super.slim(discardFlags, reindex);
        if(slimmed) createDarkSquidLookup();
        return slimmed;
    }

    private void createDarkSquidLookup() {
        darkSquidLookup = new int[readoutRows];
        Arrays.fill(darkSquidLookup, -1);
        for(HirmesPixel pixel : this) if(pixel.isDarkSQUID()) darkSquidLookup[pixel.mux] = pixel.getIndex();
    }



    @Override
    public final Vector2D getSIPixelSize() { 
        if(detArray == LORES_ARRAY) return getLayout().loresPixelSize;
        return getLayout().hiresPixelSize[hiresColUsed];    
    }


    private int getGratingIndex(double wavelength) {
        if(wavelength > 71.8 * Unit.um) return 2;
        if(wavelength > 42.4 * Unit.um) return 1;
        return 0;
    }

    private double getM6Angle(double fpx) {
        fpx /= Unit.mm; 
        return Math.atan(fpx / 546.48) + (3.17e-9 * fpx - 2.30e-7) * fpx*fpx;
    }

    double getSpectralResolution() {
        return spectral.observingFrequency / spectral.frequencyResolution;
    }

    double getRestFrequency(Vector2D focalPlanePosition) {
        return (1.0 + z) * getObservingFrequency(focalPlanePosition);
    }

    double getRestFrequency() {
        return (1.0 + z) * getFrequency();
    }

    @Override
    public double getFrequency() {
        return getObservingFrequency(getLayout().focalPlaneReference);
    }

    double getObservingFrequency(Vector2D focalPlanePosition) {
        return getObservingFrequency(focalPlanePosition, gratingIndex, gratingAngle, fpiConstant);
    }

    private double getObservingFrequency(Vector2D focalPlanePosition, int gIdx, double gAngle, double fpiK) {
        final double[] n = { 0.0220311,0.0129907,0.0076615 };

        if(mode == LORES_MODE || mode == MIDRES_MODE) {
            final double beta = 12.0 * Unit.deg - getM6Angle(focalPlanePosition.x()) - gAngle;
            final double lambdaMicrons = -(Math.sin(gAngle) - Math.sin(beta)) / n[gIdx];  
            return Constant.c / (lambdaMicrons * Unit.um);
        }
        else if(!Double.isNaN(fpiK)) {
            HirmesLayout layout = getLayout(); 
            return spectral.observingFrequency / Math.cos(fpiK * layout.plateScale * focalPlanePosition.distanceTo(layout.focalPlaneReference));
        }
        return spectral.observingFrequency;
    }

    public double getSlitEfficiency(double frequency) throws IOException {
        if(mode == IMAGING_MODE) return 1.0;
        if(!hasOption("slitcorrect")) return 1.0;

        SimpleInterpolator interpolator = new SimpleInterpolator(option("slitcorrect").getPath());

        return interpolator.getValue(Constant.c / frequency / Unit.um);
        // * Math.pow(frequency / (10.0 * Unit.THz), 0.045);    // Empirical residual correction for low-res...
    }

    @Override
    protected boolean isWavelengthConsistent(double wavelength) {
        if(instrumentData == null) return false;      
        // TODO introduce sanity checks later if needed
        //if Math.abs(wavelength - instrumentData.wavelength) < 0.1 * instrumentData.wavelength;
        return true;
    }

    @Override
    public SourceModel getSourceModelInstance(List<Scan<?>> scans) {
        if(hasOption("source.type")) {
            String type = option("source.type").getValue();
            if(type.equals("spectralmap")) return new SpectralCube(this); 
            // TODO if(type.equals("wavecal")) return new PointPhotometry(this); 
            return super.getSourceModelInstance(scans);
        }
        if(hasOption("pointing.suggest")) return new IntensityMap(this);
        return new SpectralCube(this);
    }  

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("mode")) return modeName[mode];
        if(name.equals("gratingAngle")) return gratingAngle / Unit.deg;
        if(name.equals("gratingIdx")) return gratingIndex;
        if(name.equals("strip")) return hiresColUsed > 0 ? hiresColUsed : null;
        if(name.equals("FPIk")) return fpiConstant;
        return super.getTableEntry(name);
    }

    @Override
    public String getMapConfigHelp() {
        return super.getMapConfigHelp() +
                "     -spectral.obs  Produce observing frame spectra (w/o Dopper correction).\n" +
                "     -spectral.R=   Override the spectral resolution for which data is binned.\n" +
                "     -spectral.unit=  Specify the spectral unit (wavelength or frequency).\n";
    }


    @Override
    public String getReductionModesHelp() {
        return super.getReductionModesHelp() +
                "     -spectral      Produce spectral cubes (default).\n";
    }



    final static int readoutRows = 36;
    final static int readoutCols = 33;

    final static int rows = 16;

    final static int subarrays = 3;    
    final static int subCols = 32;

    final static int lowresCols = 64;
    final static int hiresCols = 8;

    final static int lowresPixels = 32 * Hirmes.readoutCols; // Including blinds
    final static int hiresPixels = 4 * Hirmes.readoutCols;   // Including blinds

    final static int pixels = lowresPixels + hiresPixels;

    final static int LORES_ARRAY = 0;
    final static int HIRES_ARRAY = 1;

    final static int LORES_BLUE_SUBARRAY = 0;
    final static int LORES_RED_SUBARRAY = 1;
    final static int HIRES_SUBARRAY = 2; 

    final static int IMAGING_MODE = 0;
    final static int LORES_MODE = 1;
    final static int MIDRES_MODE = 2;
    final static int HIRES_MODE = 3;


    final static String[] subID = { "blue", "red", "hi" };
    final static String[] modeName = { "imaging", "lo-res", "mid-res", "hi-res" };
}

