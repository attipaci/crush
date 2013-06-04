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


import java.util.*;

import kovacs.util.*;
import kovacs.util.data.*;

public abstract class Photometry extends SourceModel {
	public String sourceName;
	public double integrationTime;
	public WeightedPoint[] flux;
	public WeightedPoint sourceFlux = new WeightedPoint();
	
	//Hashtable<ScanType, WeightedPoint> scanFluxes = new Hashtable<ScanType, WeightedPoint>();
	
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
			for(int i=flux.length; --i >= 0; ) flux[i].noData();
			sourceFlux.noData();
		}
		integrationTime = 0.0;
	}
		
	@Override
	public void write(String path) throws Exception {
		double jansky = getInstrument().janskyPerBeam();
		
		DataPoint F = new DataPoint(sourceFlux);
		
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
		//System.out.println("  NEFD  : " + Util.f1.format(500.0 * F.rms() * Math.sqrt(integrationTime/Unit.s)) + " mJy sqrt(s).");
		System.out.println("  =====================================");
		
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
	
}
