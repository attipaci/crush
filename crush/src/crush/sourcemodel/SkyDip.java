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
import java.io.*;
import java.util.*;

import kovacs.astro.Weather;
import kovacs.data.WeightedPoint;
import kovacs.math.Range;
import kovacs.util.*;

// Bin the correlated signal by elevation...
public class SkyDip extends SourceModel {
	
	WeightedPoint[] data;
	double resolution;
	WeightedPoint Tamb = new WeightedPoint(); // Assume the sky is at 0C.
	
	public SkyDip(Instrument<?> instrument) {
		super(instrument);
		preferredStem = "skydip";
	}
	
	@Override
	public SourceModel copy(boolean withContents) {
		SkyDip copy = (SkyDip) super.copy(withContents);
		copy.data = new WeightedPoint[data.length];
		if(Tamb != null) copy.Tamb = (WeightedPoint) Tamb.clone();
		
		if(withContents) {
			for(int i=0; i<data.length; i++) copy.data[i] = (WeightedPoint) data[i].clone();
		}
		else {
			for(int i=0; i<data.length; i++) copy.data[i] = new WeightedPoint();
		}
		return copy;
	}
	
	@Override
	public synchronized void reset(boolean clearContent) {
		super.reset(clearContent);
		if(clearContent) if(data != null) {
			for(int i=0; i<data.length; i++) if(data[i] != null) data[i].noData();
			Tamb.noData();
		}
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		super.createFrom(collection);
		
		resolution = hasOption("grid") ? Math.abs(option("grid").getDouble()) * Unit.arcsec : 0.25 * Unit.deg;
		int bins = (int) Math.ceil(Constant.rightAngle / resolution);
		data = new WeightedPoint[bins];
		for(int i=0; i<bins; i++) data[i] = new WeightedPoint();
	}
	
	public int getBin(double EL) {
		return (int) Math.round(EL / resolution);		
	}

	public double getEL(int bin) {
		return (bin + 0.5) * resolution;
	}
	
	@Override
	public synchronized void add(SourceModel model, double weight) {
		SkyDip other = (SkyDip) model;
		Tamb.average(other.Tamb);
		for(int i=0; i<data.length; i++) data[i].average(other.data[i]);
	}

	@Override
	public synchronized void add(Integration<?, ?> integration) {
		integration.comments += "[Dip] ";
		
		CorrelatedMode mode = (CorrelatedMode) integration.instrument.modalities.get("obs-channels").get(0);
		CorrelatedSignal C = (CorrelatedSignal) integration.getSignal(mode);
	
		if(C == null) {
			C = new CorrelatedSignal(mode, integration);
			try { C.update(false); }
			catch(Exception e) { 
				System.err.println("ERROR! Cannot decorrelate sky channels: " + e.getMessage());
			}
			C = (CorrelatedSignal) integration.getSignal(mode);
		}
		
		for(final Frame frame : integration) if(frame != null) {
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
	public synchronized void setBase() {
		// Unused...
	}

	@Override
	public String getSourceName() {
		return "Skydip";
	}

	@Override
	public synchronized void process(Scan<?, ?> scan) {
		for(int i=0; i<data.length; i++) if(data[i].weight() > 0.0) data[i].scaleValue(1.0 / data[i].weight());
		if(scan instanceof Weather) {
			double ambientT = ((Weather) scan).getAmbientTemperature();
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
		
		if(model.fitOK) {
			System.out.println();
			System.out.println("Skydip result:");
			System.out.println("=================================================");
			System.out.print(model.toString());
			System.out.println("=================================================");
			System.out.println();
		}
		else {
			System.err.println("WARNING! Skydip fit did not converge...");
		}
			
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
		
		System.err.println("Written " + fileName);
		
		gnuplot(coreName, fileName, model);
	}
	
	public Range getRange() {
		Range range = new Range();
		for(WeightedPoint point : data) if(point.weight() > 0.0) range.include(point.value());
		return range;
	}
	
	public Range getElevationRange() {
		Range range = new Range();
		for(int i=data.length; --i >= 0; ) if(data[i].weight() > 0.0) range.include(getEL(i));
		return range;
	}
	
	public void gnuplot(String coreName, String dataName, SkyDipModel model) throws IOException {
		String plotName = coreName + ".plt";
		PrintWriter plot = new PrintWriter(new FileOutputStream(plotName));
		
		plot.println("set xla 'Elevation (deg)");
		plot.println("set yla 'Mean Pixel Response (" + getInstrument().getDataUnit().name() + ")");
		
		Range dataRange = getRange();
		dataRange.grow(0.05);
		
		Range elRange = getElevationRange();
		elRange.grow(0.05);
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
	
		plot.println("set label 1 'Produced by CRUSH " + CRUSH.getFullVersion() + "' at graph 0.99,0.04 right font ',12'");
		
		
		plot.println("plot \\");
		plot.println("  '" + dataName + "' using 1:2 title 'Skydip " + scans.get(0).getID() + "'with linesp lt 1 pt 5 lw 1, \\");
		plot.println("  '" + dataName + "' using 1:3 title 'tau = " + Util.f3.format(model.tau.value()) + " +- " 
				+ Util.f3.format(model.tau.rms()) + "' with lines lt -1 lw 3");
		
		if(hasOption("write.eps")) gnuplotEPS(plot, coreName);
				
		if(hasOption("write.png")) gnuplotPNG(plot, coreName);
		
		plot.println("set out");
		plot.println("set term pop");
		
		plot.println("unset label 1");
		plot.println((hasOption("show") ? "" : "#")  + "replot");
		
		plot.close();
		
		System.err.println("Written " + plotName);
		
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
		System.err.println("Written " + coreName + ".eps");	
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
		plot.println("print 'Written " + coreName + ".png'");	
		System.err.println("Written " + coreName + ".png");
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
	public void process(boolean verbose) throws Exception {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isValid() {
		return countPoints() > 0;
	}
	
}
