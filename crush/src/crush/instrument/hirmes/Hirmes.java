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
import crush.telescope.sofia.SofiaHeader;
import jnum.Constant;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import nom.tam.fits.*;


public class Hirmes extends SofiaCamera<HirmesPixel> {
    /**
     * 
     */
    private static final long serialVersionUID = 6205260168688969947L;

    Vector2D pixelScale;
    Vector2D pixelSize;

    ArrayList<ChannelGroup<HirmesPixel>> subarrayGroups;

    Vector2D[] subarrayOffset;
    double[] subarrayOrientation;

    boolean darkSquidCorrection = false;
    int[] darkSquidLookup;               // col

    int[] mceSubarray;                   // subarray assignment for MCE 0-3
    int[][] detectorBias;                // sub [2], line [rows]

    int detArray = LORES_ARRAY;
    int mode = IMAGING_MODE;

    double baseFrequency;
    double frequencyStep = 0.0;          // frequency step... 
    double frequencyResolution = 0.0;

    public Hirmes() {
        super("hirmes", new SingleColorArrangement<HirmesPixel>(), pixels);
    }

    @Override
    public String getFileID() { return "HIR"; }

    @Override
    public Hirmes copy() {
        Hirmes copy = (Hirmes) super.copy();

        if(pixelScale != null) copy.pixelScale = pixelScale.copy();  
        if(pixelSize != null) copy.pixelSize = pixelSize.copy();  
        if(subarrayOffset != null) copy.subarrayOffset = Vector2D.copyOf(subarrayOffset);
        if(subarrayOrientation != null) copy.subarrayOrientation = Arrays.copyOf(subarrayOrientation, subarrayOrientation.length);
        if(darkSquidLookup != null) copy.darkSquidLookup = Arrays.copyOf(darkSquidLookup, darkSquidLookup.length);
        if(mceSubarray != null) copy.mceSubarray = Arrays.copyOf(mceSubarray, mceSubarray.length);
        if(detectorBias != null) {
            copy.detectorBias = new int[detectorBias.length][];
            for(int i=detectorBias.length; --i >= 0; ) if(detectorBias[i] != null) 
                copy.detectorBias[i] = Arrays.copyOf(detectorBias[i], detectorBias[i].length);
        }

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

        // TODO should not be necessary if the header is proper...
        if(Double.isNaN(integrationTime) || integrationTime < 0.0) {
            warning("Missing SMPLFREQ. Will assume 203.25 Hz.");
            integrationTime = samplingInterval = Unit.s / 203.25;
        }

        if(array.detectorName.equalsIgnoreCase("HIRMES_LOW")) detArray = LORES_ARRAY;
        else if(array.detectorName.equalsIgnoreCase("HIRMES_HIGH")) detArray = HIRES_ARRAY;

        String config = instrumentData.instrumentMode;

        if(config.equalsIgnoreCase("SPECTRAL_IMAGING")) mode = IMAGING_MODE;
        else if(config.equalsIgnoreCase("LOW-RES")) mode = LORES_MODE;
        else if(config.equalsIgnoreCase("MED-RES")) mode = MIDRES_MODE;
        else if(config.equalsIgnoreCase("HI-RES")) mode = HIRES_MODE;

        baseFrequency = header.getDouble("SPECREF", Constant.c / instrumentData.wavelength);
        frequencyStep = header.getDouble("SPECSTEP", 0.0);

        // TODO Add actual spectral resolution....
        frequencyResolution = instrumentData.spectralResolution > 0.0 ? 
                baseFrequency / instrumentData.spectralResolution : 2.0 * frequencyStep;    

        // Set the spectral grid to a default value...
        if(mode != IMAGING_MODE) if(!hasOption("spectral.grid") && !hasOption("spectral.resolution")) {
            double R = baseFrequency / frequencyResolution;
            try { 
                getOptions().process("spectral.resolution", R + ""); 
                info("Set spectral resolution to R ~ " + Util.f1.format(R));
            }
            catch(LockedException e) {}
        }

        mceSubarray = new int[subarrays];
        Arrays.fill(mceSubarray, -1);

        /* TODO
        String mceMap = header.getString("MCEMAP");
        if(mceMap != null) {
            StringTokenizer tokens = new StringTokenizer(mceMap, " \t,;:");
            for(int sub=0; sub<subarrays; sub++) if(tokens.hasMoreTokens()) {
                String assignment = tokens.nextToken();
                try { 
                    int mce = Integer.parseInt(assignment);
                    if(mce >= 0) mceSubarray[mce] = sub;
                }
                catch(NumberFormatException e) { warning("Invalid MCE assignment: " + assignment);}
            }       
        } 
         */  
    }

    @Override
    protected void loadChannelData() {
        clear();

        ensureCapacity(pixels);
        for(int c=0; c<pixels; c++) add(new HirmesPixel(this, c));

        // The subarrays orientations
        subarrayOrientation = new double[subarrays];
        subarrayOrientation[LORES_SUBARRAY_1] = hasOption("rotation.lores1") ? option("rotation.lores1").getDouble() * Unit.deg : 0.0;
        subarrayOrientation[LORES_SUBARRAY_2] = hasOption("rotation.lores2") ? option("rotation.lores2").getDouble() * Unit.deg : 0.0;
        subarrayOrientation[HIRES_SUBARRAY] = hasOption("rotation.hires") ? option("rotation.hires").getDouble() * Unit.deg : 0.0;

        // The subarray offsets (after rotation, in pixels)
        subarrayOffset = new Vector2D[subarrays];
        subarrayOffset[LORES_SUBARRAY_1] = hasOption("offset.lores1") ? option("offset.lores1").getVector2D() : new Vector2D();
        subarrayOffset[LORES_SUBARRAY_2] = hasOption("offset.lores2") ? option("offset.lores2").getVector2D() : new Vector2D();
        subarrayOffset[HIRES_SUBARRAY] = hasOption("offset.hires") ? option("offset.hires").getVector2D() : new Vector2D();

        pixelScale = new Vector2D(array.pixelScale, array.pixelScale);
        
        // Set the pixel size...
        if(hasOption("pixelscale")) {
            StringTokenizer tokens = new StringTokenizer(option("pixelscale").getValue(), " \t,:xX");
            pixelScale.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
            pixelScale.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
        }
        
        // Set the default pixel size, based on the default physical size and the recorded pixel scale (arcsec/mm)
        Vector2D pixelSize = new Vector2D(HirmesPixel.physicalSize.x() * pixelScale.x(), HirmesPixel.physicalSize.y() * pixelScale.y());        
        setNominalPixelPositions(pixelSize);

        // TODO load bias gains? ...

        super.loadChannelData();

        final int blindFlag = hasOption("blinds") ? Channel.FLAG_BLIND : Channel.FLAG_DEAD;

        final double imagingPos = hasOption("offset.imaging") ? option("offset.imaging").getDouble() : 0.0;
        final int fromCol = (int) Math.floor(imagingPos);
        final int toCol = (int) Math.ceil(imagingPos + 16.0);

        for(HirmesPixel pixel : this) {
            if(pixel.detArray != detArray) pixel.flag(Channel.FLAG_DEAD);
            else if(mode == IMAGING_MODE) if(pixel.col < fromCol || pixel.col >= toCol) pixel.flag(blindFlag);
            else if(pixel.pin == DARK_SQUID_PIN) pixel.flag(blindFlag);
        }
    }


    public final String getSubarrayID(int sub) {
        return subID[sub];
    }

    public final int getSubarrayIndex(String id) {
        for(int sub=0; sub<subarrays; sub++) if(subID[sub].equalsIgnoreCase(id)) return sub;
        return -1;        
    }


    private void setNominalPixelPositions(Vector2D size) { 
        pixelSize = size;
        
        // Adjust the plate scale to link apparent size to physical size...
        pixelScale.setX(size.x() / HirmesPixel.physicalSize.x());
        pixelScale.setY(size.y() / HirmesPixel.physicalSize.y());
        
        // Set the scalar plate-scale to the geometric mean in the x/y directions...
        array.pixelScale = Math.sqrt(pixelScale.x() * pixelScale.y());
        
        info("Boresight pixel from FITS is " + array.boresightIndex);

        if(hasOption("pcenter")) {
            array.boresightIndex = option("pcenter").getVector2D(); 
            info("Boresight override --> " + array.boresightIndex);
        } 
        else if(Double.isNaN(array.boresightIndex.x())) {
            array.boresightIndex = mode == IMAGING_MODE ? defaultImagingBoresightIndex : defaultSpectralBoresightIndex;
            if(mode == IMAGING_MODE) if(hasOption("offset.imaging")) array.boresightIndex.addX(option("offset.imaging").getDouble());
            warning("Missing FITS boresight --> " + array.boresightIndex);
        }
        Vector2D center = getSIBSPosition(getFocalPlanePosition(0, (rows - 1) - array.boresightIndex.y(), array.boresightIndex.x()));

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

    /*
    @Override
    public void editImageHeader(List<Scan<?,?>> scans, Header header) throws HeaderCardException {
        super.editImageHeader(scans, header);
        // Add SOFIA specific keywords
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", "<------ " + getName().toUpperCase() + " Header Keys ------>", false));
    }
    */


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
        darkSquidLookup = new int[muxes];

        Arrays.fill(darkSquidLookup, -1);
        for(HirmesPixel pixel : this) if(pixel.pin == DARK_SQUID_PIN) darkSquidLookup[pixel.mux] = pixel.index;
    }


    @Override
    public final Vector2D getPixelSize() { return pixelSize; }


    public Vector2D getFocalPlanePosition(int sub, double row, double col) {      
        Vector2D v = new Vector2D(col * HirmesPixel.physicalSize.x(), -row * HirmesPixel.physicalSize.y()); // X, Y
        v.rotate(subarrayOrientation[sub]);
        v.add(subarrayOffset[sub]);
        return v;
    }

    public double getFrequency(Vector2D focalPlanePosition) {
        if(mode == IMAGING_MODE) return baseFrequency;
        // Simple linear frequency dispersion along x...
        return baseFrequency + focalPlanePosition.x() / HirmesPixel.physicalSize.x() * frequencyStep;  
    }


    public Vector2D getImagingPosition(Vector2D focalPlanePosition) {
        Vector2D v = focalPlanePosition.copy();
        // Simple spectral imaging with linear position along y...
        if(mode != IMAGING_MODE) v.setX(0.0); 
        return v;
    }

    public Vector2D getSIBSPosition(Vector2D focalPlanePosition) {
        Vector2D v = getImagingPosition(focalPlanePosition);
        v.scale(array.pixelScale);       
        v.scaleY(-1.0);
        return v;
    }


    @Override
    public SourceModel getSourceModelInstance(List<Scan<?,?>> scans) {
        if(hasOption("source.type")) {
            String type = option("source.type").getValue();
            if(type.equals("spectralmap")) return new SpectralCube(this); 
            return super.getSourceModelInstance(scans);
        }

        double wavelength = Double.NaN;
        for(Scan<?,?> scan : scans) {    
            double lambda = ((Hirmes) scan.instrument).instrumentData.wavelength;
            if(Double.isNaN(wavelength)) wavelength = lambda;
            else if(lambda != wavelength) {
                CRUSH.info(this, "Multiwavelength scan set detected! Will produce spectral cube...");
                return new SpectralCube(this);
            }
        }

        return mode == IMAGING_MODE ? new AstroIntensityMap(this) : new SpectralCube(this);
    }  


    @Override
    public Object getTableEntry(String name) {
        if(name.equals("mode")) return modeName[mode];
        return super.getTableEntry(name);
    }


    final static int rows = 16;

    final static int subarrays = 3;    
    final static int subCols = 32;

    final static int lowresCols = 64;
    final static int hiresCols = 8;

    final static int muxes = 36;
    final static int muxPixels = 33;      // 32 + 1 dark squid.


    final static int lowresPixels = (lowresCols>>>1) * muxPixels;
    final static int hiresPixels = (hiresCols>>>1) * muxPixels;

    final static int pixels = lowresPixels + hiresPixels;

    final static int DARK_SQUID_PIN = muxPixels - 1;
    //final static int MCE_BIAS_LINES = rows;   // TODO

    final static int LORES_ARRAY = 0;
    final static int HIRES_ARRAY = 1;

    final static int LORES_SUBARRAY_1 = 0;
    final static int LORES_SUBARRAY_2 = 1;
    final static int HIRES_SUBARRAY = 2;

    final static int IMAGING_MODE = 0;
    final static int LORES_MODE = 1;
    final static int MIDRES_MODE = 2;
    final static int HIRES_MODE = 3;

    final static String[] subID = { "lo1", "lo2", "hi" };
    final static String[] modeName = { "imaging", "lo-res", "mid-res", "hi-res" };

    public static final Vector2D defaultImagingBoresightIndex = new Vector2D(7.5, 7.5);
    public static final Vector2D defaultSpectralBoresightIndex = new Vector2D(0.0, 7.5);

}

