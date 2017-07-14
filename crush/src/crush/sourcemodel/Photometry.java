/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

import crush.*;
import jnum.Unit;
import jnum.Util;
import jnum.astro.EquatorialCoordinates;
import jnum.data.*;
import jnum.math.SphericalCoordinates;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;

public abstract class Photometry extends SourceModel {
    /**
     * 
     */
    private static final long serialVersionUID = -8495366629075732092L;

    public String sourceName;
    public double integrationTime;
    public WeightedPoint[] flux;
    public WeightedPoint sourceFlux = new WeightedPoint();
    public EquatorialCoordinates equatorial;

    protected Hashtable<Scan<?,?>, DataPoint> scanFluxes = new Hashtable<Scan<?,?>, DataPoint>();

    public Photometry(Instrument<?> instrument) {
        super(instrument);
        flux = new WeightedPoint[instrument.storeChannels+1];
        for(int i=flux.length; --i >= 0; ) flux[i] = new WeightedPoint();
    }


    @Override
    public SourceModel getWorkingCopy(boolean withContents) {
        Photometry copy = (Photometry) super.getWorkingCopy(withContents);
        copy.sourceFlux = (WeightedPoint) sourceFlux.clone();
        copy.flux = new WeightedPoint[flux.length];
        if(withContents) for(int i=flux.length; --i >= 0; ) if(flux[i] != null) copy.flux[i] = (WeightedPoint) flux[i].clone();
        return copy;
    }


    @Override
    public void createFrom(Collection<? extends Scan<?,?>> collection) throws Exception {
        super.createFrom(collection);
        Scan<?,?> firstScan = getFirstScan();
        sourceName = firstScan.getSourceName();
        equatorial = firstScan.equatorial;
    }

    @Override
    public SphericalCoordinates getReference() {
        return equatorial;
    }

    @Override
    public void addModel(SourceModel model, double weight) {
        Photometry other = (Photometry) model;
        double renorm = getInstrument().janskyPerBeam() / other.getInstrument().janskyPerBeam();
        for(int c=flux.length; --c >= 0; ) {
            WeightedPoint F = other.flux[c];
            F.scale(renorm);
            flux[c].average(F);
        }
        sourceFlux.average(other.sourceFlux);
    }

    @Override
    public void add(Integration<?, ?> integration) {
        if(!integration.isPhaseModulated()) return;

        integration.comments += "[Phot]";
        Instrument<?> instrument = integration.instrument;
        final PhaseSet phases = ((PhaseModulated) integration).getPhases();

        int frames = 0;
        for(PhaseData phase : phases) frames += phase.end.index - phase.start.index;

        integrationTime += frames * instrument.integrationTime;
    }

    @Override
    public void process() throws Exception {
        super.sync();

        DataPoint F = new DataPoint(sourceFlux);
        F.scale(1.0 / getInstrument().janskyPerBeam());

        if(F.weight() > 0.0) CRUSH.values(this, "Flux: " + F.toString(Util.e3) + " Jy/beam. ");	
        else CRUSH.values(this, "<<invalid>> ");
    }

    @Override
    public void sync(Integration<?, ?> integration) {	
        // Nothing to do here...
    }


    @Override
    public void setBase() {
    }

    @Override
    public void resetProcessing() {
        super.resetProcessing();
        integrationTime = 0.0;
    }
    
    @Override
    public void clearContent() {
        if(flux != null) for(int i=flux.length; --i >= 0; ) if(flux[i] != null) flux[i].noData();
        sourceFlux.noData();
    }

    public double getReducedChi2() {
        if(numberOfScans() < 2) return Double.NaN;

        double mean = sourceFlux.value() / getInstrument().janskyPerBeam();
        double chi2 = 0.0;


        for(Scan<?,?> scan : getScans()) {
            DataPoint F = scanFluxes.get(scan);			
            double dev = (F.value() - mean) / F.rms();
            chi2 += dev * dev;
        }

        chi2 /= numberOfScans() - 1;
        return chi2;
    }

    public DataPoint getFinalizedSourceFlux() {
        DataPoint F = new DataPoint(sourceFlux);
        //double chi2 = getReducedChi2();
        //if(!Double.isNaN(chi2)) F.scaleWeight(1.0 / Math.max(chi2, 1.0));
        return F;
    }

    public void report() throws Exception {
        double jansky = getInstrument().janskyPerBeam();

        DataPoint F = getFinalizedSourceFlux();

        Unit Jy = new Unit("Jy/beam", jansky);
        Unit mJy = new Unit("mJy/beam", 1e-3 * jansky);
        Unit uJy = new Unit("uJy/beam", 1e-6 * jansky);
             
        StringBuffer buf = new StringBuffer();
        buf.append(
                "  [" + sourceName + "]\n" +
                "  =====================================\n" + 
                "  Flux  : ");

        double mag = Math.max(Math.abs(F.value()), F.rms()) ;

        if(mag > 1.0 * Jy.value()) buf.append(F.toString(Jy));
        else if(mag > 1.0 * mJy.value()) buf.append(F.toString(mJy));
        else buf.append(F.toString(uJy));
        
        buf.append("\n");

        buf.append("  Time  : " + Util.f1.format(integrationTime/Unit.min) + " min.\n");

        double chi2 = getReducedChi2();
        if(!Double.isNaN(chi2)) buf.append("  |rChi|: " + getCommentedChi2(chi2) + "\n");

        //buf.append("  NEFD  : " + Util.f1.format(500.0 * F.rms() * Math.sqrt(integrationTime/Unit.s)) + " mJy sqrt(s).\n");
        buf.append("  =====================================\n");

        CRUSH.result(this, new String(buf));
    }

    public String getCommentedChi2(double chi2) {
        double chi = Math.sqrt(chi2);

        if(chi <= 1.0) return "<= 1      [excellent!]   ;-)";

        String value = Util.s3.format(Math.sqrt(chi2));

        if(chi < 1.2) return value + "      [good!]   :-)";
        if(chi < 1.5) return value + "      [OK]   :-|";
        if(chi < 2.0) return value + "      [highish...]   :-o";
        if(chi < 3.0) return value + "      [high!]   :-(";
        return value + "      [ouch!!!]   :-/";
    }

    @Override
    public void write(String path) throws Exception {

        report();
    
        String coreName = path + File.separator + this.getDefaultCoreName();
        String fileName = coreName + ".dat";

        PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
        out.println("# CRUSH Photometry Data File");
        out.println("# =============================================================================");
        out.println(getASCIIHeader());
        out.println();

        Unit Jy = new Unit("Jy/beam", getInstrument().janskyPerBeam());
        out.println("# Final Combined Photometry:");
        out.println("# =============================================================================");
        out.println("# [" + sourceName + "]");
        out.println("Flux    " + getFinalizedSourceFlux().toString(Jy));	
        out.println("Time    " + Util.f1.format(integrationTime/Unit.min) + " min.");

        double chi2 = getReducedChi2();
        if(!Double.isNaN(chi2)) out.println("|rChi|  " + Util.s3.format(Math.sqrt(chi2)));

        out.println();
        out.println();

        if(scanFluxes != null && numberOfScans() > 1) {
            out.println("# Photometry breakdown by scan:");
            out.println("# =============================================================================");
            for(int i=0; i<numberOfScans(); i++) {
                Scan<?,?> scan = getScan(i);
                DataPoint flux = scanFluxes.get(scan);	
                out.println(scan.getID() + "\t" + (flux.weight() > 0.0 ? flux + " Jy/beam" : "---"));
            }

        }



        out.close();

        CRUSH.notify(this, "Written " + fileName + "");

        if(numberOfScans() > 1) gnuplot(coreName, fileName);

    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public Unit getUnit() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void noParallel() {
        // TODO Auto-generated method stub

    }

    @Override
    public void setParallel(int threads) {
        // TODO Auto-generated method stub
    }

    @Override
    public int countPoints() {
        return 1;
    }

    @Override
    public boolean isValid() {
        if(sourceFlux == null) return false;
        if(sourceFlux.isNaN()) return false;
        return true;
    }


    public void gnuplot(String coreName, String dataName) throws IOException {
        String plotName = coreName + ".plt";
        PrintWriter plot = new PrintWriter(new FileOutputStream(plotName));

        DataPoint F = new DataPoint(sourceFlux);

        double jansky = getInstrument().janskyPerBeam();
        Unit Jy = new Unit("Jy/beam", jansky);
        Unit mJy = new Unit("mJy/beam", 1e-3 * jansky);
        Unit uJy = new Unit("uJy/beam", 1e-6 * jansky);

        String printValue = null;
        double mag = Math.max(Math.abs(F.value()), F.rms()) ;		
        if(mag > 1.0 * Jy.value()) printValue = F.toString(Jy);
        else if(mag > 1.0 * mJy.value()) printValue = F.toString(mJy);
        else printValue = F.toString(uJy);

        F.scale(1.0 / jansky);	

        plot.println("set title '" + sourceName.replace("_",  " ") + " / " + getInstrument().getName().toUpperCase() + "    " + printValue + "'");
        plot.println("set xla 'Scans");
        plot.println("set yla 'Photometry (Jy/beam)'");

        plot.println("set term push");
        plot.println("set term unknown");

        plot.print("set xtics rotate by 45" + " right (");
        for(int i=0; i<numberOfScans(); i++) {
            if(i > 0) plot.print(", ");
            plot.print("'" + getScan(i).getID() + "' " + i);
        }
        plot.println(")");

        plot.println("set xra [-0.5:" + (numberOfScans() - 0.5) + "]");

        plot.println("set label 1 'Produced by CRUSH " + CRUSH.getFullVersion() + "' at graph 0.99,0.04 right font ',12'");

        plot.println("plot \\");
        plot.println("  " + F.value() + " notitle lt -1, \\");
        plot.println("  " + (F.value() - F.rms()) + " notitle lt 0, \\");
        plot.println("  " + (F.value() + F.rms()) + " notitle lt 0, \\");
        plot.println("  '" + dataName + "' index 1 using :2:4 notitle with yerr lt 1 pt 5 lw 1");

        if(hasOption("write.eps")) gnuplotEPS(plot, coreName);

        if(hasOption("write.png")) gnuplotPNG(plot, coreName);

        plot.println("set out");
        plot.println("set term pop");

        plot.println("unset label 1");		
        plot.println((hasOption("show") ? "" : "#")  + "replot");

        plot.close();

        CRUSH.notify(this, "Written " + plotName);

        if(hasOption("gnuplot")) {
            String command = option("gnuplot").getValue();
            if(command.length() == 0) command = "gnuplot";
            else command = Util.getSystemPath(command);

            Runtime runtime = Runtime.getRuntime();
            runtime.exec(command + " -p " + plotName);
        }
    }

    public void gnuplotEPS(PrintWriter plot, String coreName) {
        int fontSize = 18;

        // Adjust the font size to fit the scans (as much as possible)...
        // Max ~70 scans will fit on the plot with the smallest font...
        int N = numberOfScans();
        
        if(N > 54) fontSize = 8;
        else if(N > 45) fontSize = 10;
        else if(N > 39) fontSize = 12; 
        else if(N > 34) fontSize = 14;
        else if(N > 30) fontSize = 16;

        plot.println("set term post eps enh col sol " + fontSize);
        plot.println("set out '" + coreName + ".eps'");
        plot.println("replot");

        plot.println("print '  Written " + coreName + ".eps'");
        CRUSH.notify(this, "Written " + coreName + ".eps");	
    }

    public void gnuplotPNG(PrintWriter plot, String coreName) {
        boolean isTransparent = false;

        int bgColor = Color.WHITE.getRGB();
        if(hasOption("write.png.bg")) {
            String spec = option("write.png.bg").getValue().toLowerCase();

            if(spec.equals("transparent")) isTransparent = true;
            else bgColor = Color.getColor(spec).getRGB(); 
        }

        int sizeX = 640;
        int sizeY = 480;
        if(hasOption("write.png.size")) {
            String spec = option("write.png.size").getValue();
            StringTokenizer tokens = new StringTokenizer(spec, "xX*:, ");
            sizeX = sizeY = Integer.parseInt(tokens.nextToken());
            if(tokens.hasMoreTokens()) sizeY = Integer.parseInt(tokens.nextToken());				
        }

        plot.println("set term pngcairo enhanced color " + (isTransparent ? "" : "no") + "transparent crop" +
                " background '#" + Integer.toHexString(bgColor).substring(2) + "' fontscale " + getGnuplotPNGFontScale(sizeX) + 
                "butt size " + sizeX + "," + sizeY);
        plot.println("set out '" + coreName + ".png'");
        plot.println("replot");
        plot.println("print '  Written " + coreName + ".png'");	
        CRUSH.notify(this, "Written " + coreName + ".png");
    }


    @Override
    public void setExecutor(ExecutorService executor) {
        // TODO Auto-generated method stub

    }

    @Override
    public ExecutorService getExecutor() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getParallel() {
        // TODO Auto-generated method stub
        return 0;
    }

}
