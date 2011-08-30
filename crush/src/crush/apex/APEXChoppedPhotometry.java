/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush.apex;

import crush.*;

import util.*;
import util.data.*;

import java.util.*;

public class APEXChoppedPhotometry<InstrumentType extends APEXArray<?>, ScanType extends APEXArrayScan<InstrumentType,?>> extends SourceModel<InstrumentType, ScanType> {
	String sourceName;
	double integrationTime;
	WeightedPoint[] flux;
	WeightedPoint sourceFlux = new WeightedPoint();
	
	//Hashtable<ScanType, WeightedPoint> scanFluxes = new Hashtable<ScanType, WeightedPoint>();
	
	@Override
	public SourceModel<InstrumentType, ScanType> copy() {
		APEXChoppedPhotometry<InstrumentType, ScanType> copy = (APEXChoppedPhotometry<InstrumentType, ScanType>) super.copy();
		copy.sourceFlux = (WeightedPoint) sourceFlux.clone();
		copy.flux = new WeightedPoint[flux.length];
		for(int i=flux.length; --i >= 0; ) if(flux[i] != null) copy.flux[i] = (WeightedPoint) flux[i].clone();
		return copy;
	}

	public APEXChoppedPhotometry(InstrumentType instrument) {
		super(instrument);
		flux = new WeightedPoint[instrument.storeChannels+1];
		for(int i=flux.length; --i >= 0; ) flux[i] = new WeightedPoint();
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		super.createFrom(collection);
		ScanType firstScan = scans.get(0);
		sourceName = firstScan.sourceName;
	}
	
	@Override
	public void add(SourceModel<?, ?> model, double weight) {
		@SuppressWarnings("unchecked")
		APEXChoppedPhotometry<InstrumentType, ScanType> other = (APEXChoppedPhotometry<InstrumentType, ScanType>) model;
		for(int c=flux.length; --c >= 0; ) flux[c].average(other.flux[c]);
		sourceFlux.average(other.sourceFlux);
		integrationTime += other.integrationTime;
	}

	@Override
	public void add(Integration<?, ?> integration) {
		integration.comments += "[Phot]";
		APEXArraySubscan<?,?> subscan = (APEXArraySubscan<?,?>) integration;		
		APEXArray<?> instrument = subscan.instrument;
		integrationTime += subscan.chopper.efficiency * subscan.size() * instrument.integrationTime;
	}

	@Override
	public void process(Scan<?, ?> scan) {		
		final WeightedPoint[] left = new WeightedPoint[flux.length];
		final WeightedPoint[] right = new WeightedPoint[flux.length];
		
		for(int c=flux.length; --c >= 0; ) {
			left[c] = new WeightedPoint();
			right[c] = new WeightedPoint();			
		}
	
		for(Integration<?,?> integration : scan) {
			final APEXArraySubscan<?,?> subscan = (APEXArraySubscan<?,?>) integration;
			final double transmission = 0.5 * (subscan.getFirstFrame().transmission + subscan.getLastFrame().transmission);
			final double[] sourceGain = subscan.instrument.getSourceGains(false);
			final PhaseSet phases = integration.getPhases();
		
			// To be sure one could update the phases one last time...
			//integration.updatePhases();
			
			// Proceed only if there are enough pixels to do the job...
			if(!checkPixelCount(integration)) continue;
			
			for(APEXPixel pixel : subscan.instrument.getObservingChannels()) if(pixel.sourcePhase != 0) {
				WeightedPoint point = null;

				//ArrayList<APEXPixel> neighbours = subscan.instrument.getNeighbours(pixel, 5.0 * pixel.getResolution());
				
				if((pixel.sourcePhase & Frame.CHOP_LEFT) != 0) point = left[pixel.dataIndex];
				else if((pixel.sourcePhase & Frame.CHOP_RIGHT) != 0) point = right[pixel.dataIndex];
				else continue;
					
				
				WeightedPoint df = pixel.getLROffset(phases);
					
				df.weight /= Math.max(1.0, pixel.getLRChi2(phases, df.value));
				df.scale(1.0 / (transmission * subscan.gain * sourceGain[pixel.index]));
				
				point.average(df);
			}
		}
		
		
		Channel refPixel = ((APEXArrayScan<?,?>) scan).get(0).instrument.referencePixel;
		int refIndex = refPixel.dataIndex;
		
		for(int c=flux.length; --c >=0; ) {
			flux[c] = (WeightedPoint) left[c].clone();
			flux[c].subtract(right[c]);
			flux[c].scale(0.5);
		}
	
		// TODO add all pixels chopping over the source to the source flux...
		sourceFlux.copy(flux[refIndex]);
		flux[refIndex].noData();		
	}
	

	@Override
	public void sync() {
		double jansky = instrument.janskyPerBeam();	
		
		DataPoint F = new DataPoint(sourceFlux);
		F.scale(1.0/jansky);
		
		System.err.print("Flux: " + F.toString(Util.e3) + " Jy/beam.");	
	}


	@Override
	public void sync(Integration<?, ?> integration) {		
	}


	@Override
	public void setBase() {
	}
	
	@Override
	public void reset() {
		super.reset();
		for(int i=flux.length; --i >= 0; ) flux[i].noData();
		sourceFlux.noData();
		integrationTime = 0.0;
	}
		
	@Override
	public void write(String path) throws Exception {
		double jansky = instrument.janskyPerBeam();
		
		DataPoint F = new DataPoint(sourceFlux);
		
		Unit Jy = new Unit("Jy/beam", jansky);
		Unit mJy = new Unit("mJy/beam", 1e-3 * jansky);
		Unit uJy = new Unit("uJy/beam", 1e-6 * jansky);
		
		System.out.println("  Note, that the results of the APEX chopped photometry reduction below include");
		System.out.println("  the best estimate of the systematic errors, based on the true scatter of the");
		System.out.println("  chopped photometry measurements. As such, these errors are higher than what");
		System.out.println("  is expected from the nominal NEFD values alone, and reflect the true");
		System.out.println("  uncertainty of the photometry more accurately.");
		System.out.println();
		
		System.out.println("  [" + sourceName + "]");
		System.out.println("  =====================================");
		System.out.print("  Flux  : ");
		
		if(F.value > 1.0 * Jy.value) System.out.println(F.toString(Jy));
		else if(F.value > 1.0 * mJy.value) System.out.println(F.toString(mJy));
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

	
}
