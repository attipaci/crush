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
// Copyright (c) 2010 Attila Kovacs 

package crush.polarization;


import crush.*;
import crush.array.*;
import crush.sourcemodel.ScalarMap;

import java.util.*;

import util.Unit;

public class PolarMap<InstrumentType extends Array<?,?>, ScanType extends Scan<? extends InstrumentType, ?>>
		extends SourceModel<InstrumentType, ScanType> {

	ScalarMap<InstrumentType, ScanType> I,Q,U;
	
	public PolarMap(InstrumentType instrument) {
		super(instrument);
	}
	
	public ScalarMap<InstrumentType, ScanType> createMap() {
		return new ScalarMap<InstrumentType, ScanType>(instrument);
	}
	
	@Override
	public void create(Collection<? extends Scan<?, ?>> scans) {
		super.create(scans);
		
		I = createMap();
		I.create(scans);
		I.signalMode = PolarizationMode.I;
		I.isLevelled = true;
		I.id = "I";
		
		Q = (ScalarMap<InstrumentType, ScanType>) I.copy();
		Q.standalone();
		Q.signalMode = PolarizationMode.Q;
		Q.isLevelled = false;
		Q.allowBias = false;
		Q.id = "Q";
		
		U = (ScalarMap<InstrumentType, ScanType>) I.copy();
		U.standalone();
		U.signalMode = PolarizationMode.U;
		U.isLevelled = false;
		U.allowBias = false;
		U.id = "U";
	}
	
	@Override
	public SourceModel<InstrumentType, ScanType> copy() {
		PolarMap<InstrumentType, ScanType> copy = (PolarMap<InstrumentType, ScanType>) super.copy();
		copy.I = (ScalarMap<InstrumentType, ScanType>) I.copy();
		copy.Q = (ScalarMap<InstrumentType, ScanType>) Q.copy();
		copy.U = (ScalarMap<InstrumentType, ScanType>) U.copy();
		return copy;
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public void add(SourceModel<?, ?> model, double weight) {
		PolarMap<InstrumentType, ScanType> other = (PolarMap<InstrumentType, ScanType>) model;
		I.add(other.I, weight);
		if(hasOption("source.polarization")) {
			Q.add(other.Q, weight);
			U.add(other.U, weight);
		}
	}

	
	public void removeBias(Integration<?, ?> subscan) {
		
		if(subscan instanceof Biased) {
			double[] dG = subscan.instrument.getSourceGains(false);	
			if(subscan.sourceSyncGain != null) 
				for(int c=dG.length; --c >= 0; ) dG[c] -= subscan.sourceSyncGain[c];
				
			((Biased) subscan).removeBias(dG);
			subscan.comments += " ";
		}
		
	}
	
	@Override
	public void add(Integration<?, ?> subscan) {
		removeBias(subscan);
		
		I.add(subscan);
		if(hasOption("source.polarization")) {			
			Q.add(subscan);
			U.add(subscan);
		}
	}

	@Override
	public void setBase() {
		I.setBase();
		if(hasOption("source.polarization")) {
			Q.setBase();
			U.setBase();
		}
	}

	@Override
	public void process(Scan<?, ?> scan) {
		I.process(scan);
		if(hasOption("source.polarization")) {
			Q.process(scan);
			U.process(scan);
		}
	}
	
	@Override
	public synchronized void sync() throws InterruptedException {
		System.err.print("\n   [I] ");
		I.sync();
		if(hasOption("source.polarization")) {
			System.err.print("\n   [Q] ");
			Q.sync();
			System.err.print("\n   [U] ");
			U.sync();
		}
	}

	@Override
	public void reset() {
		I.reset();
		Q.reset();
		U.reset();
	}
	
	@Override
	public void sync(Integration<?, ?> subscan) {
		I.sync(subscan);	
		if(hasOption("source.polarization")) {
			Q.sync(subscan);
			U.sync(subscan);	
		}
	}

	@Override
	public void write(String path) throws Exception {
		I.write(path);
		Q.write(path);
		U.write(path);
	
		for(int i=0; i<Q.map.sizeX(); i++) for(int j=0; j<Q.map.sizeY(); j++) {
			if(Q.map.flag[i][j] == 0 && U.map.flag[i][j] == 0) {
				double q = Q.map.data[i][j];
				double u = U.map.data[i][j];
				
				Q.map.data[i][j] = Math.hypot(q,u);
				Q.map.weight[i][j] = 0.5 * (Q.map.weight[i][j] + U.map.weight[i][j]);
			}
			else Q.map.flag[i][j] = 1;
		}
		
		Q.map.sanitize();
		Q.id = "P";
		Q.write(path);
				
	}

	@Override
	public String getSourceName() {
		return I.getSourceName();
	}

	@Override
	public Unit getUnit() {
		return I.getUnit();
	}
	

}
