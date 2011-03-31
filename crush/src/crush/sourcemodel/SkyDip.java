/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import util.*;
import util.astro.Weather;
import util.data.WeightedPoint;

import java.io.*;
import java.util.*;

// Bin the correlated signal by elevation...
public class SkyDip<InstrumentType extends Instrument<?>, ScanType extends Scan<? extends InstrumentType, ?>>
		extends SourceModel<InstrumentType, ScanType> {
	
	WeightedPoint[] data;
	double resolution;
	WeightedPoint Tsky = new WeightedPoint(); // Assume the sky is at 0C.
	
	public SkyDip(InstrumentType instrument) {
		super(instrument);
	}
	
	@Override
	public SourceModel<InstrumentType, ScanType> copy() {
		SkyDip<InstrumentType, ScanType> copy = (SkyDip<InstrumentType, ScanType>) super.copy();
		copy.data = new WeightedPoint[data.length];
		if(Tsky != null) copy.Tsky = (WeightedPoint) Tsky.clone();
		for(int i=0; i<data.length; i++) copy.data[i] = (WeightedPoint) data[i].clone();
		return copy;
	}
	
	@Override
	public void reset() {
		super.reset();
		for(int i=0; i<data.length; i++) data[i].noData();
		Tsky.noData();
	}
	
	@Override
	public void create(Collection<? extends Scan<?,?>> collection) {
		super.create(collection);
		
		resolution = hasOption("grid") ? option("grid").getDouble() * Unit.arcsec : 0.25 * Unit.deg;
		int bins = (int) Math.ceil(0.5 * Math.PI / resolution);
		data = new WeightedPoint[bins];
		for(int i=0; i<bins; i++) data[i] = new WeightedPoint();
	}
	
	public int getBin(double EL) {
		return (int) Math.round(EL / resolution);		
	}

	public double getEL(int bin) {
		return (bin + 0.5) * resolution;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void add(SourceModel<?, ?> model, double weight) {
		SkyDip<InstrumentType, ScanType> other = (SkyDip<InstrumentType, ScanType>) model;
		for(int i=0; i<data.length; i++) data[i].average(other.data[i]);
	}

	@Override
	public void add(Integration<?, ?> integration) {
		integration.comments += "[Dip] ";
		
		CorrelatedMode mode = (CorrelatedMode) integration.instrument.modalities.get("obs-channels").get(0);
		CorrelatedSignal C = (CorrelatedSignal) integration.signals.get(mode);
	
		if(C == null) {
			C = new CorrelatedSignal(mode, integration);
			try { C.update(false); }
			catch(IllegalAccessException e) { 
				System.err.println("ERROR! Cannot decorrelate sky channels: " + e.getMessage());
			}
			C = (CorrelatedSignal) integration.signals.get(mode);
		}
		
		for(Frame frame : integration) if(frame != null) {
			HorizontalFrame exposure = (HorizontalFrame) frame;
			WeightedPoint bin = data[getBin(exposure.horizontal.EL())];
			double w = exposure.relativeWeight * C.weightAt(frame);
			bin.value += w * C.valueAt(frame);
			bin.weight += w;
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
		for(int i=0; i<data.length; i++) if(data[i].weight > 0.0) data[i].value /= data[i].weight;
		if(scan instanceof Weather) {
			double ambientT = ((Weather) scan).getAmbientTemperature();
			if(!Double.isNaN(ambientT)) Tsky.average(new WeightedPoint(ambientT, scan.getObservingTime()));
		}
	}

	@Override
	public void sync(Integration<?, ?> integration) {
		// Unused -- synching is performed by the correlated signal...
	}

	@Override
	public void write(String path) throws Exception {
		SkyDipModel model = new SkyDipModel();
		fit(model);
		
		if(model.fitOK) {
			System.out.println();
			System.out.println("Skydip result:");
			System.out.println("=================================================");
			System.out.println(model.toString());
			System.out.println("=================================================");
			System.out.println();
		}
		else {
			System.err.println("WARNING! Skydip fit did not converge...");
		}
			
		String fileName = hasOption("name") ? option("name").getValue() : getDefaultCoreName();
		fileName = CRUSH.workPath + File.separator + fileName + ".dat";
		
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		StringTokenizer header = new StringTokenizer(model.toString(), "\n");
		
		while(header.hasMoreTokens()) out.println("# " + header.nextToken());
		out.println("#");
		out.println("# EL\tobs\tmodel");
		
		for(int i=0; i<data.length; i++) {
			out.print(Util.f3.format(getEL(i) / Unit.deg) + "\t");
			//out.print(data[i].weight > 0.0 ? Util.e3.format(data[i].value / model.Kelvin.value) : "...");
			out.print(data[i].weight > 0.0 ? Util.e3.format(data[i].value) : "...");
			out.print("\t");
			//out.print(Util.e3.format(model.valueAt(getEL(i)) / model.Kelvin.value));
			out.print(Util.e3.format(model.valueAt(getEL(i))));
			out.println();
		}
		
		out.flush();
		out.close();
		
		System.err.println("Written " + fileName);
	}

	public void fit(SkyDipModel model) {
		model.setOptions(option("skydip"));
		model.fit(this);
	}

	@Override
	public Unit getUnit() {
		return Unit.get("K");
	}
	
}
