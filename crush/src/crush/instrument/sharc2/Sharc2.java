/* *****************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.sharc2;

import crush.*;
import crush.telescope.cso.CSOInstrument;
import jnum.Unit;
import jnum.Util;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;
import jnum.io.LineParser;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;
import nom.tam.fits.*;

import java.io.*;
import java.util.*;


public class Sharc2 extends CSOInstrument<Sharc2Pixel> {
    /**
     * 
     */
    private static final long serialVersionUID = -6054582144119360355L;
    String filterName;

    private Vector2D arrayPointingCenter = new Vector2D(6.5, 16.5);

    double nativeSamplingInterval;
    double[] rowGain;
    boolean[] isHiGain;
    double bias0;

    boolean earlyFITS = true;

    public Sharc2() {
        super("sharc2", pixels);
        setResolution(8.5 * Unit.arcsec);
    }


    @Override
    public Sharc2 copy() {
        Sharc2 copy = (Sharc2) super.copy();

        if(arrayPointingCenter != null) copy.arrayPointingCenter = arrayPointingCenter.copy();
        
        if(rowGain != null) copy.rowGain = Util.copyOf(rowGain);	
        if(isHiGain != null) copy.isHiGain = Util.copyOf(isHiGain);

        return copy;
    }

    public Vector2D getArrayPointingCenter() { return arrayPointingCenter; }

    @Override
    public Sharc2Layout getLayoutInstance() { return new Sharc2Layout(this); }

    @Override
    public Sharc2Layout getLayout() { return (Sharc2Layout) super.getLayout(); }

    @Override
    public void configure() {
        String filter = null;

        if(hasOption("350um")) filter = "350um";
        else if(hasOption("450um")) filter = "450um";
        else if(hasOption("850um")) filter = "850um";

        info("SHARC-2 Filter set to " + filter);

        if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();

        super.configure();
    }


    @Override
    public Sharc2Pixel getChannelInstance(int backendIndex) {
        return new Sharc2Pixel(this, backendIndex);
    }	

    @Override
    public String getChannelDataHeader() {
        return super.getChannelDataHeader() + "\teff\tgRow";
    }

    @Override
    public Scan<?> getScanInstance() {
        return new Sharc2Scan(this);
    }



    @Override
    protected void loadChannelData() {
        double gainCompress = 1.0;

        // Load the Gain Non-linearity coefficients
        if(hasOption("response")) {
            try {
                loadGainCoefficients(option("response").getPath());
                gainCompress = calcPixelGains();
            }
            catch(IOException e) { warning("Problem parsing nonlinearity file."); }		
        }

        super.loadChannelData();

        gain *= gainCompress;
    }




    @Override
    protected void createDivisions() {
        super.createDivisions();

        try { addDivision(getDivision("rows", Sharc2Pixel.class.getField("row"), Channel.FLAG_DEAD)); }
        catch(Exception e) { error(e); }

        if(hasOption("block")) {
            StringTokenizer tokens = new StringTokenizer(option("block").getValue(), " \t:x");
            int sizeX = Integer.parseInt(tokens.nextToken());
            int sizeY = tokens.hasMoreTokens() ? Integer.parseInt(tokens.nextToken()) : sizeX;
            int nx = (int)Math.ceil(32.0 / sizeX);

            for(Sharc2Pixel pixel : this) pixel.block = (pixel.row / sizeY) * nx + (pixel.col / sizeX); 
        }

        try { addDivision(getDivision("blocks", Sharc2Pixel.class.getField("block"), Channel.FLAG_DEAD)); }
        catch(Exception e) { error(e); }

        /*
		try { addDivision(getDivision("amps", Sharc2Pixel.class.getField("amp"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
         */
    }

    @Override
    protected void createModalities() {
        super.createModalities();

        try { addModality(new CorrelatedModality("rows", "r", divisions.get("rows"), Sharc2Pixel.class.getField("rowGain"))); }
        catch(NoSuchFieldException e) { error(e); }

        try { addModality(new CorrelatedModality("mux", "m", divisions.get("rows"), Sharc2Pixel.class.getField("muxGain"))); }
        catch(NoSuchFieldException e) { error(e); }

        try { addModality(new CorrelatedModality("blocks", "b", divisions.get("blocks"), Sharc2Pixel.class.getField("gain"))); }
        catch(NoSuchFieldException e) { error(e); }

        /* TODO
		try { addModality(new CorrelatedModality("amps", "a", divisions.get("amps"), Sharc2Pixel.class.getField("ampGain"))); }
		catch(NoSuchFieldException e) { error(e); }
         */

        addModality(modalities.get("rows").new CoupledModality("smileys", "s", new Sharc2SmileyRows()));

        modalities.get("rows").setGainFlag(Sharc2Pixel.FLAG_ROW);
        modalities.get("mux").solveGains = false;
        modalities.get("blocks").solveGains = false;
        // TODO modalities.get("amps").gainFlag = Sharc2Pixel.FLAG_AMP;

    }


    protected void parseHardwareHDU(BinaryTableHDU hdu) throws FitsException {
        // Get Vital information from Descriptive HDU's
        double hiGain = hdu.getHeader().getDoubleValue("GAIN_HI", 10521.0);
        double loGain = hdu.getHeader().getDoubleValue("GAIN_LO", 1412.0);

        // Filter
        filterName = hdu.getHeader().getStringValue("FILTER");
        if(hasOption("filter")) filterName = option("filter").getValue();

        // Get the electronic gains for the rows
        rowGain = new double[12];
        isHiGain = new boolean[12];

        boolean[][] gainMode = (boolean[][]) hdu.getColumn("Gain Mode");

        for(int row=0; row<12; row++) {
            isHiGain[row] = gainMode[row][0];
            rowGain[row] = isHiGain[row] ? hiGain : loGain;
        }

        // Read in the DAC values...
        short[][] DAC = new short[12][];
        for(int row=0; row<12; row++) DAC[row] = (short[]) hdu.getRow(row)[0];

        // Read the Bias Voltages
        float[][] rowBias = (float[][])hdu.getColumn("Bias Voltage");

        if(rowBias == null) {
            rowBias = new float[12][2];
            for(int row=0; row<12; row++) rowBias[row][0] = 1000.0F;
        }

        bias0 = rowBias[0][0] * Unit.mV;

        // Add the pixels here...
        populate(pixels);

        for(Sharc2Pixel pixel : this) {
            pixel.DAC = DAC[pixel.row][pixel.col];
            pixel.biasV = rowBias[pixel.row][0] * Unit.mV;
            pixel.offset = -(isHiGain[pixel.row] ? 48.83 : 4.439) * Unit.mV * pixel.DAC;
        }

    }    


    protected void parseDSPHDU(BinaryTableHDU hdu) throws HeaderCardException {
        nativeSamplingInterval = earlyFITS ?
                3.0 * hdu.getHeader().getDoubleValue("AVERAGE") * Unit.ms :
                    hdu.getHeader().getDoubleValue("FRAMESPC") * Unit.ms;
    }

    protected void parsePixelHDU(BinaryTableHDU hdu) throws FitsException {

        int iGain = hdu.findColumn("Relative Pixel Gains");
        int iFlag = hdu.findColumn("Pixel Flags");
        int iWeight = hdu.findColumn("Pixel Weights");
        int iOffset = hdu.findColumn("Pixel Offsets");

        if(iGain < 0) warning("FITS pixel gains not found.");
        if(iFlag < 0) warning("FITS pixel flags not found.");
        if(iWeight < 0) warning("FITS pixel weights not found.");
        if(iOffset < 0) warning("FITS pixel offsets not found.");

        for(int row=0; row<12; row++) {
            Object[] data = hdu.getRow(row);
            int rowStart = 32 * row;

            if(iGain >= 0) {
                float[] gain = (float[]) data[iGain];
                for(int col=0; col<32; col++) get(rowStart + col).gain = gain[col];
            }
            // Flags in data file are different than here...
            // Are they really necessary?
            /*
			if(iFlag >= 0) {
				int[] flag = (int[]) data[iFlag];
				for(int col=0; col<32; col++) lookup.get(bol0+col).flag = flag[col];
			}	
             */
            if(iWeight >= 0) {
                float[] weight = (float[]) data[iWeight];
                for(int col=0; col<32; col++) get(rowStart + col).weight = weight[col];
            }		   
            // Do not parse offsets. One should not rely on the uncertain levelling
            // by user before scans. It's safer and better to get these calculated
            // from the data themselves...
        }
    } 


    protected void parseDataHeader(Header header) throws HeaderCardException {
        if(earlyFITS) {
            samplingInterval = integrationTime = nativeSamplingInterval;
            arrayPointingCenter = new Vector2D(6.5, 16.5);
            return;
        }

        samplingInterval = integrationTime = header.getDoubleValue("CDELT1") * Unit.ms;

        // Pointing Center
        arrayPointingCenter = new Vector2D();
        arrayPointingCenter.setX(header.getDoubleValue("CRPIX3", 6.5));
        arrayPointingCenter.setY(header.getDoubleValue("CRPIX2", 16.5));
    }

    public void loadGainCoefficients(String fileName) throws IOException {
        info("Loading nonlinearities from " + fileName + ".");

        new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);

                int row = tokens.nextInt() - 1;
                int col = tokens.nextInt() - 1;

                Sharc2Pixel pixel = get(32*row + col + 1);

                pixel.G0 = tokens.nextDouble();
                pixel.V0 = -tokens.nextDouble() * pixel.biasV;
                pixel.T0 = tokens.nextDouble() * Unit.K;

                return true;
            }
        }.read(fileName);

    }

    public double calcPixelGains() {
        double sumwG2 = 0.0, sumwG = 0.0;
        double sourceGain = Double.NaN;

        for(Sharc2Pixel pixel : this) {
            pixel.gain = pixel.G0 * pixel.offset / (pixel.getReadoutGain() * pixel.V0);
            if(pixel.isUnflagged()) {
                sumwG2 += pixel.weight * pixel.gain * pixel.gain;
                sumwG += pixel.weight * Math.abs(pixel.gain);
            }
        }

        if(sumwG > 0.0) {
            sourceGain = sumwG2 / sumwG;
            for(Sharc2Pixel pixel : this) pixel.gain /= sourceGain;
        }

        info("Gain compression is " + Util.f3.format(sourceGain));
        return sourceGain;
    }

    // TODO convert to robust estimate?...
    public double getMeandVdT() {
        double sumdIdT = 0.0, sumw=0.0;

        for(Sharc2Pixel pixel : this) if(pixel.isUnflagged()) {
            sumdIdT += pixel.weight * pixel.V0 / pixel.T0;
            sumw += pixel.weight;
        }

        return sumw > 0.0 ? (float) (getLayout().getAreaFactor() * sumdIdT / sumw) : 0.0;
    }


    @Override
    public double getLoadTemperature() {
        WeightedPoint[] data = new WeightedPoint[size()];
        int n = 0;

        double areaFactor = getLayout().getAreaFactor();

        for(Sharc2Pixel pixel : this) if(pixel.isUnflagged()) {
            double dVdT = areaFactor * pixel.V0 / pixel.T0;
            WeightedPoint T = new WeightedPoint();
            T.setValue((pixel.V0 - pixel.offset / pixel.getReadoutGain()) / dVdT);
            T.setWeight(pixel.weight * dVdT * dVdT);
            data[n++] = T;
        }

        return Statistics.Inplace.smartMedian(data, 0, n, 0.25).value() - excessLoad;
    }

    public void calcGainCoefficients(double loadT) {
        info("Calculating nonlinearity coefficients.");

        double sumG02 = 0.0, sumG0 = 0.0;

        double areaFactor = getLayout().getAreaFactor();

        for(Sharc2Pixel pixel : this) {
            double eps = 1.0 - areaFactor * loadT / pixel.T0;

            pixel.G0 = pixel.gain / eps;
            pixel.V0 = pixel.offset / pixel.getReadoutGain() / eps;

            if(!pixel.isFlagged()) {
                sumG02 += pixel.weight * pixel.G0 * pixel.G0;
                sumG0 += pixel.weight * Math.abs(pixel.G0);
            }
        }

        if(sumG0 > 0.0) {
            double aveG0 = sumG02 / sumG0;
            for(Sharc2Pixel pixel : this) pixel.G0 /= aveG0;
        }

    }

    public void writeGainCoefficients(String fileName, String header) throws IOException {
        try(PrintWriter out = new PrintWriter(new FileOutputStream(fileName))) {
            out.println("# SHARC2 Non-linearity Coefficients ");
            out.println("#");
            if(header != null) {
                out.println(header);
                out.println("#");
            }
            out.println("# row\tcol\tG(eps=0)\tI0(Vb=1V)\tT0 (K))");
            out.println("# ----- ------- --------------  --------------- ----------");

            for(Sharc2Pixel pixel : this) {
                out.print((pixel.row+1) + "\t" + (pixel.col+1) + "\t");
                out.print(Util.f6.format(pixel.G0) + "\t");
                out.print(Util.e4.format(-pixel.V0 / pixel.biasV) + "\t");
                out.println(Util.f6.format(pixel.T0 / Unit.K));
            }

            out.close();
        }

        notify("Written nonlinearity coefficients to " + fileName);
    }

    @Override
    public int maxPixels() {
        return storeChannels;
    }


    @Override
    public Object getTableEntry(String name) {	
        if(name.equals("bias")) return bias0 / Unit.mV;
        if(name.equals("filter")) return filterName;
        return super.getTableEntry(name);
    }

    @Override
    public String getScanOptionsHelp() {
        return super.getScanOptionsHelp() + 
                "     -fazo=         Correct the pointing with this FAZO value.\n" +
                "     -fzao=         Correct the pointing with this FZAO value.\n" +
                "     -350um         Select 450um imaging mode (default).\n" +
                "     -450um         Select 450um imaging mode.\n" +
                "     -850um         Select 850um imaging mode.\n";
    }




    public static final int rows = 12;
    public static final int cols = 32;
    public static final int pixels = rows * cols;


}

