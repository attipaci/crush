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

package crush.sourcemodel;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.StringTokenizer;

import crush.CRUSH;
import crush.Instrument;
import crush.Scan;
import crush.SourceModel;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;

public abstract class PointPhotometry extends Photometry {  
    /**
     * 
     */
    private static final long serialVersionUID = -9148109376660967099L;

    public DataPoint sourceFlux;

    protected Hashtable<Scan<?>, DataPoint> scanFluxes;

    
    public PointPhotometry(Instrument<?> instrument) {
        super(instrument);
        sourceFlux = new DataPoint();
        scanFluxes = new Hashtable<Scan<?>, DataPoint>();
    }
    
    @Override
    public PointPhotometry copy(boolean withContents) {
        PointPhotometry copy = (PointPhotometry) super.copy(withContents);
        if(sourceFlux != null) copy.sourceFlux = sourceFlux.copy();
        copy.scanFluxes = new Hashtable<Scan<?>, DataPoint>(scanFluxes.size());
        copy.scanFluxes.putAll(scanFluxes);
        return copy;
    }
    
    @Override
    public void addModelData(SourceModel model, double weight) {
        super.addModelData(model, weight);
      
        if(!(model instanceof PointPhotometry)) return;
        
        PointPhotometry other = (PointPhotometry) model;
        
        sourceFlux.average(other.sourceFlux);
        scanFluxes.putAll(other.scanFluxes);
    }
    
    @Override
    public void process() throws Exception {
        super.process();

        DataPoint F = new DataPoint(sourceFlux);
        F.scale(1.0 / getInstrument().janskyPerBeam());

        if(F.weight() > 0.0) CRUSH.values(this, "Flux: " + F.toString(Util.e3) + " Jy/beam. "); 
        else CRUSH.values(this, "<<invalid>> ");
    }
    
    @Override
    public void clearContent() {
        super.clearContent();
        sourceFlux.noData();
    }
    
    public DataPoint getFinalizedSourceFlux() {
        DataPoint F = new DataPoint(sourceFlux);
        //double chi2 = getReducedChi2();
        //if(!Double.isNaN(chi2)) F.scaleWeight(1.0 / Math.max(chi2, 1.0));
        return F;
    }

    
    public double getReducedChi2() {
        if(numberOfScans() < 2) return Double.NaN;

        double mean = sourceFlux.value() / getInstrument().janskyPerBeam();
        double chi2 = 0.0;

        for(Scan<?> scan : getScans()) {
            DataPoint F = scanFluxes.get(scan);     
            if(F == null) continue;
            double dev = (F.value() - mean) / F.rms();
            chi2 += dev * dev;
        }

        chi2 /= numberOfScans() - 1;
        return chi2;
    }

    
    @Override
    public boolean isValid() {
        if(sourceFlux == null) return false;
        if(sourceFlux.isNaN()) return false;
        return true;
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
    
    
    @Override
    public void write() throws Exception {

        report();
    
        String coreName = getOutputPath() + File.separator + this.getDefaultCoreName();
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
                Scan<?> scan = getScan(i);
                DataPoint flux = scanFluxes.get(scan);  
                if(flux == null) continue;
                out.println(scan.getID() + "\t" + (flux.weight() > 0.0 ? flux + " Jy/beam" : "---"));
            }

        }



        out.close();

        CRUSH.notify(this, "Written " + fileName + "");

        if(numberOfScans() > 1) gnuplot(coreName, fileName);

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
    public Object getTableEntry(String name) {
        if(name.equals("flux")) return sourceFlux.value();
        if(name.equals("dflux")) return sourceFlux.rms();
        return super.getTableEntry(name);
    }

    

}
