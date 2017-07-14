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
import crush.telescope.HorizontalFrame;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.astro.Weather;
import jnum.data.WeightedPoint;
import jnum.math.Range;
import jnum.math.SphericalCoordinates;

import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

// Bin the correlated signal by elevation...
public class SkyDip extends SourceModel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -4817222317574280875L;
	
	WeightedPoint[] data;
	double resolution;
	WeightedPoint Tamb = new WeightedPoint(); // Assume the sky is at 0C.
	String signalName = "obs-channels";
	int signalIndex = 0;
	
	public SkyDip(Instrument<?> instrument) {
		super(instrument);
	}
	
	@Override
	public SourceModel getWorkingCopy(boolean withContents) {
		SkyDip copy = (SkyDip) super.getWorkingCopy(withContents);
		copy.data = new WeightedPoint[data.length];
		if(Tamb != null) copy.Tamb = (WeightedPoint) Tamb.clone();
		
		if(withContents) {
			for(int i=data.length; --i >= 0; ) copy.data[i] = (WeightedPoint) data[i].clone();
		}
		else {
			for(int i=data.length; --i >= 0; ) copy.data[i] = new WeightedPoint();
		}
		return copy;
	}
	
	@Override
	public void clearContent() {   
	    for(int i=data.length; --i >= 0; ) if(data[i] != null) data[i].noData();
		if(Tamb != null) Tamb.noData();
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) throws Exception {
		super.createFrom(collection);
		
		resolution = hasOption("skydip.grid") ? Math.abs(option("skydip.grid").getDouble()) * Unit.arcsec : 0.25 * Unit.deg;
		
		if(hasOption("skydip.signal")) signalName = option("skydip.signal").getValue();
		if(hasOption("skydip.mode")) signalIndex = option("skydip.mode").getInt();
		
		int bins = (int) Math.ceil(Constant.rightAngle / resolution);
		data = new WeightedPoint[bins];
		for(int i=0; i<bins; i++) data[i] = new WeightedPoint();
	}
	
	public int getBin(double EL) {
	    if(Double.isNaN(EL)) return -1;
		return (int) Math.round(EL / resolution);		
	}

	public double getEL(int bin) {
		return (bin + 0.5) * resolution;
	}
	
	@Override
	public void addModel(SourceModel model, double weight) {
		SkyDip other = (SkyDip) model;
		Tamb.average(other.Tamb);
		for(int i=data.length; --i >= 0; ) data[i].average(other.data[i]);
	}

	@Override
	public void add(Integration<?, ?> integration) {
		integration.comments += "[Dip] ";
		
		CorrelatedMode mode = (CorrelatedMode) integration.instrument.modalities.get(signalName).get(signalIndex);
		CorrelatedSignal C = (CorrelatedSignal) integration.getSignal(mode);
	
		if(C == null) {
			C = new CorrelatedSignal(mode, integration);
			try { C.update(false); }
			catch(Exception e) { error("Cannot decorrelate sky channels: " + e.getMessage()); }
			C = (CorrelatedSignal) integration.getSignal(mode);
		}
		
		for(final Frame frame : integration) if(frame != null) {
		    if(frame.isFlagged(Frame.SOURCE_FLAGS)) continue;
		    
		    final HorizontalFrame exposure = (HorizontalFrame) frame;
		
			int bin = getBin(exposure.horizontal.EL());
			if(bin < 0 || bin >= data.length) continue;
			
			final WeightedPoint point = data[bin];
			double w = exposure.relativeWeight * C.weightAt(frame);
			point.add(w * C.valueAt(frame));
			point.addWeight(w);
		}

	}

	@Override
	public void setBase() {
		// Unused...
	}

	@Override
	public String getSourceName() {
		return "Skydip";
	}

	@Override
	public void process(Scan<?, ?> scan) {
		for(int i=0; i<data.length; i++) if(data[i].weight() > 0.0) data[i].scaleValue(1.0 / data[i].weight());
		if(scan instanceof Weather) {
			double ambientT = ((Weather) scan).getAmbientKelvins();
			if(!Double.isNaN(ambientT)) Tamb.average(new WeightedPoint(ambientT, scan.getObservingTime()));
		}	
	}
	
	@Override
	public int countPoints() {
		int n=0;
		for(WeightedPoint point : data) if(point != null) if(point.weight() > 0.0) n++;
		return n;
		
	}

	@Override
	public void sync(Integration<?, ?> integration) {
		// Unused -- synching is performed by the correlated signal...
	}

	@Override
	public void write(String path) throws Exception {
		SkyDipModel model = new SkyDipModel();
	
		model.kelvin.setValue(getInstrument().kelvin());	
		model.dataUnit = getInstrument().getDataUnit().name();
		
		fit(model);
		
		if(model.hasConverged) {
		    CRUSH.result(this,
		            "Skydip result:\n" +
		            "=================================================\n" +
		            model.toString() +
		            "=================================================\n");
		}
		else warning("Skydip fit did not converge...");
			
		String fileName = hasOption("name") ? option("name").getValue() : getDefaultCoreName();
		String coreName = CRUSH.workPath + File.separator + fileName;
		fileName = coreName + ".dat";
		
		
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		StringTokenizer header = new StringTokenizer(model.toString(), "\n");
		
		while(header.hasMoreTokens()) out.println("# " + header.nextToken());
		out.println("#");
		out.println("# EL\tobs\tmodel");
		
		for(int i=0; i<data.length; i++) {
			out.print(Util.f3.format(getEL(i) / Unit.deg) + "\t");
			//out.print(data[i].weight > 0.0 ? Util.e3.format(data[i].value / model.Kelvin.value) : "...");
			out.print(data[i].weight() > 0.0 ? Util.e3.format(data[i].value()) : "...");
			out.print("\t");
			//out.print(Util.e3.format(model.valueAt(getEL(i)) / model.Kelvin.value));
			out.print(Util.e3.format(model.valueAt(getEL(i))));
			out.println();
		}
		
		out.flush();
		out.close();
		
		CRUSH.notify(this, "Written " + fileName);
		
		gnuplot(coreName, fileName, model);
	}
	
	public Range getSignalRange() {
		Range range = new Range();
		for(WeightedPoint point : data) if(point.weight() > 0.0) range.include(point.value());
		return range;
	}
	
	public Range getElevationRange() {
		Range range = new Range();
		for(int i=data.length; --i >= 0; ) if(data[i].weight() > 0.0) range.include(getEL(i));
		return range;
	}
	
	public Range getAirmassRange() {
        Range elRange = getElevationRange();
        return new Range(1.0 / Math.sin(elRange.max()), 1.0 / Math.sin(elRange.min()));
    }
	
	public void gnuplot(String coreName, String dataName, SkyDipModel model) throws IOException {
		String plotName = coreName + ".plt";
		PrintWriter plot = new PrintWriter(new FileOutputStream(plotName));
		
		plot.println("set xla 'Elevation (deg)");
		plot.println("set yla 'Mean Pixel Response (" + getInstrument().getDataUnit().name() + ")");
		
		Range dataRange = getSignalRange();
		dataRange.grow(1.05);
		
		Range elRange = getElevationRange();
		elRange.grow(1.05);
		elRange.scale(1.0 / Unit.deg);
		
		plot.println("set xra [" + elRange.min() + ":" + elRange.max() + "]");
		plot.println("set yra [" + dataRange.min() + ":" + dataRange.max() + "]");
		
		if(model.elRange != null) {		
			plot.println("set arrow 1 from " + (model.elRange.min() / Unit.deg) + ", " + dataRange.min()
					+ " to " + (model.elRange.min() / Unit.deg) + ", " + dataRange.max() + " nohead lt 0 lw 3");
			plot.println("set arrow 2 from " + (model.elRange.max() / Unit.deg) + ", " + dataRange.min()
					+ " to " + (model.elRange.max() / Unit.deg) + ", " + dataRange.max() + " nohead lt 0 lw 3");
		}
		
		plot.println("set term push");
		plot.println("set term unknown");
	
		plot.println("set label 1 'Produced by CRUSH " + CRUSH.getFullVersion() + "' at graph 0.01,0.04 left font ',12'");
		
		
		plot.println("plot \\");
		String id = getFirstScan().getID().replace("_", " ");
		
		plot.println("  '" + dataName + "' using 1:2 title 'Skydip " + id + "'with linesp lt 1 pt 5 lw 1, \\");
		plot.println("  '" + dataName + "' using 1:3 title 'tau = " + Util.f3.format(model.tau.value()) + " +- " 
				+ Util.f3.format(model.tau.rms()) + "' with lines lt -1 lw 3");
		
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
		plot.println("set term post eps enh col sol 18");
		plot.println("set out '" + coreName + ".eps'");
		plot.println("replot");
		
		plot.println("print 'Written " + coreName + ".eps'");
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
			
		plot.println("set term pngcairo enhanced color " + (isTransparent ? "" : "no") + "transparent" +
				" background '#" + Integer.toHexString(bgColor).substring(2) + "' fontscale " + getGnuplotPNGFontScale(sizeX) + 
				" butt size " + sizeX + "," + sizeY);
		plot.println("set out '" + coreName + ".png'");
		plot.println("replot");
		plot.println("print 'Written " + coreName + ".png'");	
		CRUSH.notify(this, "Written " + coreName + ".png");
	}
	
	public void fit(SkyDipModel model) {
		model.setOptions(option("skydip"));
		model.fit(this);
	}

	@Override
	public Unit getUnit() {
		return Unit.get("K");
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
	public void process() throws Exception {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isValid() {
		return countPoints() > 0;
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
		return 1;
	}

	@Override
	public SphericalCoordinates getReference() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
