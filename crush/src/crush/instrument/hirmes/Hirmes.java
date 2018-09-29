/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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

import java.util.*;

import crush.*;
import crush.array.SingleColorArrangement;
import crush.sourcemodel.AstroIntensityMap;
import crush.sourcemodel.SpectralCube;
import crush.telescope.sofia.SofiaCamera;
import crush.telescope.sofia.SofiaData;
import crush.telescope.sofia.SofiaHeader;
import crush.telescope.sofia.SofiaSpectroscopyData;
import jnum.Constant;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;
import nom.tam.fits.*;
import nom.tam.util.Cursor;


public class Hirmes extends SofiaCamera<HirmesPixel> {
    /**
     * 
     */
    private static final long serialVersionUID = 6205260168688969947L;

    // TODO Clarify mux readout wiring (Elmer)
    // TODO S/N flattened spectra

    double plateScale = defaultPlateScale;
    Vector2D loresPixelSize;                // (arcsec)
    Vector2D[] hiresPixelSize = new Vector2D[hiresCols];      // (arcsec)
    
    int detArray = LORES_ARRAY;
    int mode = IMAGING_MODE;
    int hiresColUsed = -1;                  // [0-7] Hires strip index used.
     
    
    Vector2D focalPlaneReference;           // (mm) on the focal-plane coordinate system
    double gratingAngle;
    double fpiConstant = Double.NaN;        // FPI dispersion contant
    int gratingIndex;                       // [0-2]
        
    ArrayList<ChannelGroup<HirmesPixel>> subarrayGroups;

    Vector2D[] subarrayPixelOffset;         // (lowres pixels)
    double[] subarrayOrientation;   
    Vector2D hiresFocalPlaneOffset;         // (mm) Hires-array offset, calculated from subarrayPixelOffset & loresPixelSpacing

  
    boolean darkSquidCorrection = false;
    int[] darkSquidLookup;                // col

    //int[][] detectorBias;                 // array [2], line [rows]

    double z = 0.0;                         // Doppler shift. 

    public Hirmes() {
        super("hirmes", new SingleColorArrangement<HirmesPixel>(), pixels);
    }

    @Override
    public String getFileID() { return "HIR"; }

    @Override
    public Hirmes copy() {
        Hirmes copy = (Hirmes) super.copy();

        if(loresPixelSize != null) copy.loresPixelSize = loresPixelSize.copy();
        if(hiresPixelSize != null) copy.hiresPixelSize = Vector2D.copyOf(hiresPixelSize);
        if(focalPlaneReference != null) copy.focalPlaneReference = focalPlaneReference.copy();
        if(subarrayPixelOffset != null) copy.subarrayPixelOffset = Vector2D.copyOf(subarrayPixelOffset);
        if(subarrayOrientation != null) copy.subarrayOrientation = Arrays.copyOf(subarrayOrientation, subarrayOrientation.length);
        

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
    public Scan<?, ?> getScanInstance() {
        return new HirmesScan(this);
    }

    @Override
    protected void initDivisions() {
        super.initDivisions();

        try { addDivision(getDivision("subarrays", HirmesPixel.class.getField("sub"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
        catch(Exception e) { error(e); }    

        try { addDivision(getDivision("bias", HirmesPixel.class.getField("biasLine"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
        catch(Exception e) { error(e); } 

        try { addDivision(getDivision("series", HirmesPixel.class.getField("seriesArray"), Channel.FLAG_DEAD | Channel.FLAG_BLIND)); }
        catch(Exception e) { error(e); } 

        // If correction was applied at validation, then only decorrelate detectors
        // Otherwise, decorrelate including the dark SQUIDs...
        int muxSkipFlag = hasOption("darkcorrect") ? Channel.FLAG_DEAD | Channel.FLAG_BLIND : Channel.FLAG_DEAD;

        try { addDivision(getDivision("mux", HirmesPixel.class.getField("mux"), muxSkipFlag)); 
        ChannelDivision<HirmesPixel> muxDivision = divisions.get("mux");

        // TODO
        // Order mux channels in pin order...
        for(ChannelGroup<HirmesPixel> mux : muxDivision) {
            Collections.sort(mux, new Comparator<HirmesPixel>() {
                @Override
                public int compare(HirmesPixel o1, HirmesPixel o2) {
                    if(o1.row == o2.row) return 0;
                    return o1.row > o2.row ? 1 : -1;
                }   
            });
        }
        }
        catch(Exception e) { error(e); }

        try { addDivision(getDivision("rows", HirmesPixel.class.getField("row"), muxSkipFlag)); }
        catch(Exception e) { error(e); }  

        try { addDivision(getDivision("cols", HirmesPixel.class.getField("col"), muxSkipFlag)); }
        catch(Exception e) { error(e); }  

        try { addDivision(getDivision("pins", HirmesPixel.class.getField("pin"), muxSkipFlag)); }
        catch(Exception e) { error(e); }  
    }

    @Override
    protected void initGroups() {
        super.initGroups();

        subarrayGroups = new ArrayList<ChannelGroup<HirmesPixel>>(subarrays);
        for(int sub=0; sub<subarrays; sub++) {
            ChannelGroup<HirmesPixel> g = new ChannelGroup<HirmesPixel>(subID[sub]);
            subarrayGroups.add(g);
            addGroup(g);
        }

        for(HirmesPixel pixel : this) subarrayGroups.get(pixel.sub).add(pixel);
    }

    @Override
    protected void initModalities() {
        super.initModalities();

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
            CorrelatedModality pinMode = new CorrelatedModality("pin", "p", divisions.get("pins"), HirmesPixel.class.getField("pinGain"));     
            //muxMode.solveGains = false;
            pinMode.setGainFlag(HirmesPixel.FLAG_PIN);
            addModality(pinMode);
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
        
        spectral = new SofiaSpectroscopyData(header);
        
        samplingInterval = integrationTime = 1.0 / (header.getDouble("SMPLFREQ", Double.NaN) * Unit.Hz);
        if(samplingInterval < 0.0) samplingInterval = integrationTime = Double.NaN;

        focalPlaneReference = new Vector2D(header.getDouble("CRX") * Unit.mm, header.getDouble("CRY") * Unit.mm);
        gratingAngle = header.getDouble("GRATANGL", Double.NaN) * Unit.deg;
        fpiConstant = header.getDouble("FPIK", Double.NaN);
 
        if(array.detectorName.equalsIgnoreCase("HIRMES-LOW")) detArray = LORES_ARRAY;
        else if(array.detectorName.equalsIgnoreCase("HIRMES-HI")) detArray = HIRES_ARRAY;
        String config = instrumentData.instrumentMode;

        if(config.equalsIgnoreCase("SPECTRAL_IMAGING")) mode = IMAGING_MODE;
        else if(config.equalsIgnoreCase("LOW-RES")) mode = LORES_MODE;
        else if(config.equalsIgnoreCase("MED-RES")) mode = MIDRES_MODE;
        else if(config.equalsIgnoreCase("HI-RES")) mode = HIRES_MODE;
       
        hiresColUsed = header.getInt("HIRESSUB", -1);

           
        // Doppler correction to rest frame...
        z = hasOption("spectral.obs") ? spectral.getRedshift() : 0.0;

        
        // Set the spectral grid to a default value...
        if(mode != IMAGING_MODE) if(!hasOption("spectral.grid") && !hasOption("spectral.r")) {
            try { 
                double R = spectral.observingFrequency / spectral.frequencyResolution;
                getOptions().process("spectral.r", R + ""); 
                info("Set spectral resolution to R ~ " + Util.f1.format(R));
            }
            catch(LockedException e) {}
        }

        if(mode == LORES_MODE || mode == HIRES_MODE) gratingIndex = getGratingIndex(Constant.c / spectral.observingFrequency);
        
    }
    
    @Override
    public void editHeader(Header header) throws HeaderCardException {             
        // Add HAWC+ specific keywords
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ HIRMES Header Keys ------>", false));
        c.add(SofiaData.makeCard("SMPLFREQ", 1.0 / samplingInterval, "(Hz) Detector readout rate."));
        c.add(SofiaData.makeCard("CRX", focalPlaneReference.x() / Unit.mm, "(mm) Focal plane center x."));
        c.add(SofiaData.makeCard("CRY", focalPlaneReference.y() / Unit.mm, "(mm) Focal plane center y"));
        c.add(SofiaData.makeCard("GRATANGL", gratingAngle / Unit.deg, "Grating angle"));
        c.add(SofiaData.makeCard("FPIK", fpiConstant, "FPI dispecrison constant"));
        c.add(new HeaderCard("HIRESSUB", hiresColUsed, "[0-7] Hires col used."));
    }
    

    @Override
    protected void loadChannelData() {
        clear();

        ensureCapacity(pixels);
        for(int c=0; c<pixels; c++) add(new HirmesPixel(this, c));

        
        
        // The subarrays orientations
        subarrayOrientation = new double[subarrays];
        subarrayOrientation[LORES_SUBARRAY_1] = hasOption("rotation.blue") ? option("rotation.blue").getDouble() * Unit.deg : 0.0;
        subarrayOrientation[LORES_SUBARRAY_2] = hasOption("rotation.red") ? option("rotation.red").getDouble() * Unit.deg : 0.0;
        subarrayOrientation[HIRES_SUBARRAY] = hasOption("rotation.hires") ? option("rotation.hires").getDouble() * Unit.deg : 0.0;

        // The subarray offsets (after rotation, in pixels)
        subarrayPixelOffset = new Vector2D[subarrays];
        subarrayPixelOffset[LORES_SUBARRAY_1] = hasOption("offset.blue") ? option("offset.blue").getVector2D() : new Vector2D(-7.816, 0.0);
        subarrayPixelOffset[LORES_SUBARRAY_2] = hasOption("offset.red") ? option("offset.red").getVector2D() : new Vector2D(-40.175, 0.0);
        subarrayPixelOffset[HIRES_SUBARRAY] = hasOption("offset.hires") ? option("offset.hires").getVector2D() : new Vector2D();

        
        hiresFocalPlaneOffset = subarrayPixelOffset[HIRES_SUBARRAY].copy();
        hiresFocalPlaneOffset.multiplyByComponents(loresPixelSpacing);
 
        setNominalPixelPositions();

        // TODO load bias gains? ...
        super.loadChannelData();

        final int blindFlag = hasOption("blinds") ? Channel.FLAG_BLIND : Channel.FLAG_DEAD;
        
        Vector2D imageAperture = hasOption("imaging.aperture") ? option("imaging.aperture").getDimension2D(Unit.arcsec) : defaultImagingAperture; 
        imageAperture.add(new Vector2D(loresPixelSize.x(), loresPixelSize.y())); // Include partially illuminated pixels.
        imageAperture.scale(0.5);
                 
        for(HirmesPixel pixel : this) {
            if(pixel.detArray != detArray) pixel.flag(Channel.FLAG_DEAD);
            else if(pixel.isDark()) pixel.flag(blindFlag);
            else if(pixel.sub == HIRES_SUBARRAY) {
                if(pixel.subcol != hiresColUsed) pixel.flag(blindFlag);
            }
            else if(mode == IMAGING_MODE) {
                if(Math.abs(pixel.getPosition().x()) > imageAperture.x()) pixel.flag(blindFlag);
                if(Math.abs(pixel.getPosition().y()) > imageAperture.y()) pixel.flag(blindFlag);  
            }
        }
    }
    

    @Override
    public double[] getSourceGains(final boolean filterCorrected) {
        double[] G = super.getSourceGains(filterCorrected);

        final double rest2Obs = 1.0 / (1.0 + z);
        for(HirmesPixel pixel : this) G[pixel.index] *= getRelativeTransmission(pixel.getFrequency() * rest2Obs);
        
        return G;        
    }

    public double getRelativeTransmission(double fobs) {
        // TODO spectral telluric corrections go here...
        return 1.0;
    }

    public final String getSubarrayID(int sub) {
        return subID[sub];
    }

    public final int getSubarrayIndex(String id) {
        for(int sub=0; sub<subarrays; sub++) if(subID[sub].equalsIgnoreCase(id)) return sub;
        return -1;        
    }


    private void setNominalPixelPositions() { 
        
        // Set the pixel sizes...
        if(hasOption("pixelsize.lores")) {
            loresPixelSize = option("pixelsize.lores").getDimension2D(Unit.arcsec);
            plateScale = Math.sqrt(loresPixelSize.x() / loresPixelSpacing.x() * loresPixelSize.y() / loresPixelSpacing.y()); 
        }
        else {
            if(hasOption("platescale")) plateScale = option("platescale").getDouble() * Unit.arcsec / Unit.mm; 
            loresPixelSize = loresPixelSpacing.copy();
            loresPixelSize.scale(plateScale);
        }
            
        for(int i=0; i<hiresCols; i++) {
            if(hasOption("pixelsize.hires" + (i+1))) hiresPixelSize[i] = option("pixelsize.hires" + (i+1)).getVector2D(Unit.arcsec);
            else {
                hiresPixelSize[i] = new Vector2D(hiresWidthMicrons[i], hiresHeightMicrons[i]);
                hiresPixelSize[i].scale(Unit.um * plateScale);
            }
        }
        
        // Update the SOFIA standard pixel size...
        if(detArray == LORES_ARRAY) array.pixelSize = Math.sqrt(loresPixelSize.x() * loresPixelSize.y());
        else array.pixelSize = Math.sqrt(hiresPixelSize[hiresColUsed].x() * hiresPixelSize[hiresColUsed].y());
               
        Vector2D center = getSIBSPosition(focalPlaneReference);  
        for(HirmesPixel pixel : this) pixel.calcSIBSPosition3D();
        
        // Set the pointing center...
        setReferencePosition(center);
      }


    @Override
    public int maxPixels() {
        return storeChannels;
    }    

    @Override
    public String getChannelDataHeader() {
        return super.getChannelDataHeader() + "\teff\tGmux\tidx\tsub\trow\tcol";
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
    public void validate(Scan<?,?> scan) {
        darkSquidCorrection = hasOption("darkcorrect");
        createDarkSquidLookup();

        super.validate(scan);
    }

    @Override
    public void validate(Vector<Scan<?,?>> scans) throws Exception {
        final HirmesScan firstScan = (HirmesScan) scans.get(0);

        double wavelength = firstScan.instrument.instrumentData.wavelength;

        for(int i=scans.size(); --i >= 1; ) {
            HirmesScan scan = (HirmesScan) scans.get(i);

            double dlambda = scan.instrument.instrumentData.wavelength - wavelength;
            if(Math.abs(dlambda) > 0.1 * wavelength) {
                warning("Scan " + scans.get(i).getID() + " is at too different of a wavelength. Removing from set.");
                scans.remove(i);
            }

            if(!scan.instrument.instrumentData.instrumentConfig.equals(firstScan.instrument.instrumentData.instrumentConfig)) {
                warning("Scan " + scans.get(i).getID() + " is in different instrument configuration. Removing from set.");
                scans.remove(i);                
            }  

            if(scan.hasOption("gyrocorrect")) if(scan.hasOption("gyrocorrect.max")) {
                double limit = scan.option("gyrocorrect.max").getDouble() * Unit.arcsec;
                if(scan.gyroDrifts.getMax() > limit) {
                    warning("Scan " + scans.get(i).getID() + " has too large gyro drifts. Removing from set.");
                    scans.remove(i);
                }
            }
        }

        super.validate(scans);
    }

    @Override
    public boolean slim(boolean reindex) {
        boolean slimmed = super.slim(reindex);
        if(slimmed) createDarkSquidLookup();
        return slimmed;
    }

    public void createDarkSquidLookup() {
        darkSquidLookup = new int[readoutRows];
        Arrays.fill(darkSquidLookup, -1);
        for(HirmesPixel pixel : this) if(pixel.isDark()) darkSquidLookup[pixel.mux] = pixel.index;
    }

    public Vector2D getPixelSize(int sub, int col) {
        if(sub == HIRES_SUBARRAY) return hiresPixelSize[col];
        return loresPixelSize;
    }


    @Override
    public final Vector2D getSIPixelSize() { 
        if(detArray == LORES_ARRAY) return loresPixelSize;
        return hiresPixelSize[hiresColUsed];    
    }

    public Vector2D getFocalPlanePosition(int sub, double row, double col) {      
        Vector2D v = (sub == HIRES_SUBARRAY) ? new Vector2D(0.0, row-7.5) : new Vector2D(-col, row-7.5); // xp, yp
        v.rotate(subarrayOrientation[sub]);
        v.add(subarrayPixelOffset[sub]);
        v.multiplyByComponents(loresPixelSpacing); // Offsets are in lores pixels...
        return v;
    }
    
    public Vector2D getHiresFocalPlanePosition(int strip, int row) {
        Vector2D v = new Vector2D(0.0, (row-7.5) * hiresHeightMicrons[strip] * Unit.um);
        v.rotate(subarrayOrientation[HIRES_SUBARRAY]);
        v.addX(hiresColOffsetMillis[strip] * Unit.mm);
        v.add(hiresFocalPlaneOffset);  
        
        return v;
    }

    public double getRestFrequency(Vector2D focalPlanePosition) {
        return (1.0 + z) * getObservingFrequency(focalPlanePosition);
    }
    
    public double getObservingFrequency(Vector2D focalPlanePosition) {
        final double[] n = { 0.0220311,0.0129907,0.0076615 };
        
        if(mode == LORES_MODE || mode == MIDRES_MODE) {
            final double beta = 12.0 * Unit.deg - getM6Angle(focalPlanePosition.x()) - gratingAngle;
            final double lambdaMicrons = -(Math.sin(gratingAngle) - Math.sin(beta)) / n[gratingIndex];            
            return Constant.c / (lambdaMicrons * Unit.um);
        }
        else if(!Double.isNaN(fpiConstant)) {
            return spectral.observingFrequency / Math.cos(fpiConstant * plateScale * focalPlanePosition.distanceTo(focalPlaneReference));
        }
        return spectral.observingFrequency;
    }

   
    public int getGratingIndex(double wavelength) {
        if(wavelength > 71.8 * Unit.um) return 2;
        if(wavelength > 42.4 * Unit.um) return 1;
        return 0;
    }
    
    public double getM6Angle(double fpx) {
        fpx /= Unit.mm; 
        return Math.atan(fpx / 546.48) + (3.17e-9 * fpx - 2.30e-7) * fpx*fpx;
    }

    public Vector2D getImagingPosition(Vector2D focalPlanePosition) {
        Vector2D v = focalPlanePosition.copy();
        // Simple spectral imaging with linear position along y...
        if(mode != IMAGING_MODE) v.setX(0.0); 
        return v;
    }

    public Vector2D getSIBSPosition(Vector2D focalPlanePosition) {
        Vector2D v = getImagingPosition(focalPlanePosition);
        v.scale(plateScale);       
        //v.scaleX(-1.0);
        return v;
    }


    @Override
    public SourceModel getSourceModelInstance(List<Scan<?,?>> scans) {
        if(hasOption("source.type")) {
            String type = option("source.type").getValue();
            if(type.equals("spectralmap")) return new SpectralCube(this); 
            return super.getSourceModelInstance(scans);
        }
        if(hasOption("pointing.suggest")) return new AstroIntensityMap(this);
        return new SpectralCube(this);
    }  


    @Override
    public Object getTableEntry(String name) {
        if(name.equals("mode")) return modeName[mode];
        if(name.equals("gratingAngle")) return gratingAngle / Unit.deg;
        if(name.equals("gratingIdx")) return gratingIndex;
        if(name.equals("ref.x")) return focalPlaneReference.x() / Unit.mm;
        if(name.equals("ref.y")) return focalPlaneReference.y() / Unit.mm;
        if(name.equals("strip")) return hiresColUsed > 0 ? hiresColUsed : null;
        if(name.equals("FPIk")) return fpiConstant;
        return super.getTableEntry(name);
    }
    
    @Override
    public String getMapConfigHelp() {
        return super.getMapConfigHelp() +
                "     -spectral.obs  Produce observing frame spectra (w/o Dopper correction).\n" +
                "     -spectral.R=   Specify the spectral resolution for which data is binned.\n" +
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

    final static int lowresPixels = 32 * readoutCols; // Including blinds
    final static int hiresPixels = 4 * readoutCols;   // Including blinds

    final static int pixels = lowresPixels + hiresPixels;

    final static int LORES_ARRAY = 0;
    final static int HIRES_ARRAY = 1;

    final static int LORES_SUBARRAY_1 = 0;
    final static int LORES_SUBARRAY_2 = 1;
    final static int HIRES_SUBARRAY = 2; 

    final static int IMAGING_MODE = 0;
    final static int LORES_MODE = 1;
    final static int MIDRES_MODE = 2;
    final static int HIRES_MODE = 3;
    
    final static Vector2D loresPixelSpacing = new Vector2D(1.180 * Unit.mm, 1.180 * Unit.mm);

    final static double defaultPlateScale = 6.203 * Unit.arcsec / Unit.mm;
    
    final static double hiresWidthMicrons[] = { 480, 574, 686, 821, 982, 1175, 1405, 1680 };
    final static double hiresHeightMicrons[] = { 410, 488, 582, 694, 828, 989, 1181, 1410 };
    
    final static double[] hiresColOffsetMillis = { 0.0, 0.9922, 2.1161, 3.5049, 5.1582, 7.2088, 9.5228, 12.4433 }; 
    final static Vector2D defaultImagingAperture = new Vector2D(118.0 * Unit.arcsec, 118.0 * Unit.arcsec);  
    
    final static String[] subID = { "blue", "red", "hi" };
    final static String[] modeName = { "imaging", "lo-res", "mid-res", "hi-res" };
}

