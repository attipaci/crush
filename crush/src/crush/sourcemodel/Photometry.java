/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package crush.sourcemodel;

import crush.*;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import kovacs.data.*;
import kovacs.util.*;

public abstract class Photometry extends SourceModel {
	public String sourceName;
	public double integrationTime;
	public WeightedPoint[] flux;
	public WeightedPoint sourceFlux = new WeightedPoint();
	
	protected Hashtable<Scan<?,?>, DataPoint> scanFluxes = new Hashtable<Scan<?,?>, DataPoint>();

	@Override
	public SourceModel copy(boolean withContents) {
		Photometry copy = (Photometry) super.copy(withContents);
		copy.sourceFlux = (WeightedPoint) sourceFlux.clone();
		copy.flux = new WeightedPoint[flux.length];
		if(withContents) for(int i=flux.length; --i >= 0; ) if(flux[i] != null) copy.flux[i] = (WeightedPoint) flux[i].clone();
		return copy;
	}

	public Photometry(Instrument<?> instrument) {
		super(instrument);
		flux = new WeightedPoint[instrument.storeChannels+1];
		for(int i=flux.length; --i >= 0; ) flux[i] = new WeightedPoint();
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		super.createFrom(collection);
		Scan<?,?> firstScan = scans.get(0);
		sourceName = firstScan.getSourceName();
	}
	
	@Override
	public synchronized void add(SourceModel model, double weight) {
		Photometry other = (Photometry) model;
		for(int c=flux.length; --c >= 0; ) {
			WeightedPoint F = other.flux[c];
			F.scale(getInstrument().janskyPerBeam() / other.getInstrument().janskyPerBeam());
			flux[c].average(F);
		}
		sourceFlux.average(other.sourceFlux);
		integrationTime += other.integrationTime;
	}

	@Override
	public synchronized void add(Integration<?, ?> integration) {
		integration.comments += "[Phot]";
		Instrument<?> instrument = integration.instrument;
		final PhaseSet phases = integration.getPhases();
	
		int frames = 0;
		for(PhaseOffsets offset : phases) frames += offset.end.index - offset.start.index;
	
		integrationTime += frames * instrument.integrationTime;
	}

	@Override
	public void process(boolean verbose) throws Exception {
		super.sync();
		
		double jansky = getInstrument().janskyPerBeam();	
		
		DataPoint F = new DataPoint(sourceFlux);
		F.scale(1.0/jansky);
		
		System.err.print("Flux: " + F.toString(Util.e3) + " Jy/beam.");	
	}
	
	@Override
	public void sync(Integration<?, ?> integration) {	
		// Nothing to do here...
	}


	@Override
	public void setBase() {
	}
	
	@Override
	public synchronized void reset(boolean clearContent) {
		super.reset(clearContent);
		if(clearContent) {
			if(flux != null) for(int i=flux.length; --i >= 0; ) if(flux[i] != null) flux[i].noData();
			sourceFlux.noData();
		}
		integrationTime = 0.0;
	}
	
	public double getReducedChi2() {
		if(scans.size() < 2) return Double.NaN;
		
		double mean = sourceFlux.value() / getInstrument().janskyPerBeam();
		double chi2 = 0.0;
		
		
		for(Scan<?,?> scan : scans) {
			DataPoint F = scanFluxes.get(scan);			
			double dev = (F.value() - mean) / F.rms();
			chi2 += dev * dev;
		}
		
		chi2 /= scans.size() - 1;
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
	
		System.out.println("  [" + sourceName + "]");
		System.out.println("  =====================================");
		System.out.print("  Flux  : ");
		
		double mag = Math.max(Math.abs(F.value()), F.rms()) ;
		
		if(mag > 1.0 * Jy.value()) System.out.println(F.toString(Jy));
		else if(mag > 1.0 * mJy.value()) System.out.println(F.toString(mJy));
		else System.out.println(F.toString(uJy));
		
		System.out.println("  Time  : " + Util.f1.format(integrationTime/Unit.min) + " min.");
		
		double chi2 = getReducedChi2();
		if(!Double.isNaN(chi2)) System.out.println("  |rChi| : " + Util.s3.format(Math.sqrt(chi2)));
		
		//System.out.println("  NEFD  : " + Util.f1.format(500.0 * F.rms() * Math.sqrt(integrationTime/Unit.s)) + " mJy sqrt(s).");
		System.out.println("  =====================================");
		
	}
	
	@Override
	public void write(String path) throws Exception {
		
		report();
		System.err.println();
		
		String coreName = path + File.separator + this.getDefaultCoreName();
		String fileName = coreName + ".dat";
		
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		out.println("# CRUSH Photometry Data File");
		out.println("# =============================================================================");
		out.println("#");
		out.println(getASCIIHeader());
		out.println("# =============================================================================");
		out.println();
		out.println();
		
		double jansky = getInstrument().janskyPerBeam();
		
		if(scanFluxes != null && scans.size() > 1) {
			out.println("# Photometry breakdown by scan:");
			out.println("# =============================================================================");
			for(int i=0; i<scans.size(); i++) {
				Scan<?,?> scan = scans.get(i);
				out.println(scan.getID() + "\t" + scanFluxes.get(scan) + " Jy/beam");
			}
			out.println();
			out.println();
		}
			
		Unit Jy = new Unit("Jy/beam", jansky);
		out.println("# Final Combined Photometry:");
		out.println("# =============================================================================");
		out.println("# [" + sourceName + "]");
		out.println("Flux  " + getFinalizedSourceFlux().toString(Jy));	
		out.println("Time  " + Util.f1.format(integrationTime/Unit.min) + " min.");
		
		double chi2 = getReducedChi2();
		if(!Double.isNaN(chi2)) out.println("  |rChi| : " + Util.s3.format(Math.sqrt(chi2)));
	
		
		out.close();

		System.err.println("  Written " + fileName + "");
		
		if(scans.size() > 1) gnuplot(coreName, fileName);
		
		System.err.println();
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
		
		plot.println("set xla 'Scans");
		plot.println("set yla 'Photometry (Jy/beam)'");
		
		plot.println("set term push");
		plot.println("set term unknown");
		
		DataPoint F = new DataPoint(sourceFlux);
		F.scale(1.0 / getInstrument().janskyPerBeam());	
		
		plot.print("set xtics rotate by 45" + " right (");
		for(int i=0; i<scans.size(); i++) {
			Scan<?,?> scan = scans.get(i);
			if(i > 0) plot.print(", ");
			plot.print("'" + scan.getID() + "' " + i);
		}
		plot.println(")");
		
		plot.println("set xra [-0.5:" + (scans.size() - 0.5) + "]");
		
		plot.println("plot \\");
		plot.println("  " + F.value() + " notitle lt -1, \\");
		plot.println("  " + (F.value() - F.rms()) + " notitle lt 0, \\");
		plot.println("  " + (F.value() + F.rms()) + " notitle lt 0, \\");
		plot.println("  '" + dataName + "' index 0 using :2:4 notitle with yerr lt 1 pt 5 lw 1");
		
	
		if(hasOption("write.eps")) gnuplotEPS(plot, coreName);
				
		if(hasOption("write.png")) gnuplotPNG(plot, coreName);
		
		plot.println("set out");
		plot.println("set term pop");
		plot.println((hasOption("show") ? "" : "#")  + "replot");
		
		plot.close();
		
		System.err.println("  Written " + plotName);
		
		if(hasOption("gnuplot")) {
			String command = option("gnuplot").getValue();
			if(command.length() == 0) command = "gnuplot";
			else command = Util.getSystemPath(command);
			
			Runtime runtime = Runtime.getRuntime();
			runtime.exec(command + " -p " + plotName);
		}
	}

	public void gnuplotEPS(PrintWriter plot, String coreName) {
		plot.println("set term post eps enh col sol 18");
		plot.println("set out '" + coreName + ".eps'");
		plot.println("replot");
		
		plot.println("print '  Written " + coreName + ".eps'");
		System.err.println("  Written " + coreName + ".eps");	
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
			StringTokenizer tokens = new StringTokenizer(spec, "xX:,");
			sizeX = sizeY = Integer.parseInt(tokens.nextToken());
			if(tokens.hasMoreTokens()) sizeY = Integer.parseInt(tokens.nextToken());				
		}
		
		plot.println("set term png enh " + (isTransparent ? "" : "no") + "transparent truecolor interlace" +
				" background '#" + Integer.toHexString(bgColor).substring(2) + "' size " + sizeX + "," + sizeY);
		plot.println("set out '" + coreName + ".png'");
		plot.println("replot");
		plot.println("print '  Written " + coreName + ".png'");	
		System.err.println("  Written " + coreName + ".png");
	}
	
}
