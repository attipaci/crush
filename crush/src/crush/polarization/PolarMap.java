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
import crush.sourcemodel.AstroMap;
import crush.sourcemodel.ScalarMap;

import java.util.*;

import util.Purifiable;
import util.Unit;

public class PolarMap<InstrumentType extends Array<?,?>, ScanType extends Scan<? extends InstrumentType, ?>>
		extends SourceModel<InstrumentType, ScanType> {

	ScalarMap<InstrumentType, ScanType> N,Q,U;
	public boolean usePolarization = false;
	public boolean hasPolarization = false;
	
	public PolarMap(InstrumentType instrument) {
		super(instrument);	
	}

	public ScalarMap<InstrumentType, ScanType> getMapInstance() {
		
		return new ScalarMap<InstrumentType, ScanType>(instrument);
	}
	
	public boolean usePolarization() {
		return usePolarization | hasOption("source.polarization");
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?, ?>> scans) {
		super.createFrom(scans);
		
		N = getMapInstance();
		N.setOptions(getOptions());
		N.createFrom(scans);
		N.signalMode = PolarModulation.N;
		N.enableLevel = true;
		N.enableWeighting = true;
		N.id = "N";
		
		Q = (ScalarMap<InstrumentType, ScanType>) N.copy();
		Q.standalone();
		Q.signalMode = PolarModulation.Q;
		Q.enableLevel = false;
		Q.enableWeighting = true;
		Q.enableBias = false;
		Q.id = "Q";
		
		U = (ScalarMap<InstrumentType, ScanType>) N.copy();
		U.standalone();
		U.signalMode = PolarModulation.U;
		U.enableLevel = false;
		U.enableWeighting = true;
		U.enableBias = false;
		U.id = "U";
	}
	
	@Override
	public SourceModel<InstrumentType, ScanType> copy() {
		PolarMap<InstrumentType, ScanType> copy = (PolarMap<InstrumentType, ScanType>) super.copy();
		copy.N = (ScalarMap<InstrumentType, ScanType>) N.copy();
		copy.Q = (ScalarMap<InstrumentType, ScanType>) Q.copy();
		copy.U = (ScalarMap<InstrumentType, ScanType>) U.copy();
		return copy;
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public void add(SourceModel<?, ?> model, double weight) {
		PolarMap<InstrumentType, ScanType> other = (PolarMap<InstrumentType, ScanType>) model;
		N.add(other.N, weight);
		if(usePolarization()) {
			Q.add(other.Q, weight);
			U.add(other.U, weight);
			hasPolarization = true;
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

		//((Purifiable) subscan).purify();
		
		N.add(subscan);	
		
		if(usePolarization()) {
			((Purifiable) subscan).purify();
			Q.add(subscan);
			U.add(subscan);
		}
	}

	@Override
	public void setBase() {
		N.setBase();
		if(usePolarization()) {
			Q.setBase();
			U.setBase();
		}
	}

	@Override
	public void process(Scan<?, ?> scan) {
		N.process(scan);
		if(usePolarization()) {
			Q.process(scan);
			U.process(scan);
		}
	}
	
	@Override
	public synchronized void sync() throws InterruptedException {
		System.err.print("\n   [N] ");
		N.sync();
		if(usePolarization()) {
			System.err.print("\n   [Q] ");
			Q.sync();
			System.err.print("\n   [U] ");
			U.sync();
		}
	}

	@Override
	public void reset() {
		N.reset();
		if(usePolarization()) {
			Q.reset();
			U.reset();
		}
	}
	
	
	@Override
	public void sync(Integration<?, ?> subscan) {
		N.sync(subscan);	
		if(usePolarization()) {
			Q.sync(subscan);
			U.sync(subscan);	
		}
	}

	@Override
	public void postprocess(Scan<?,?> scan) {
		super.postprocess(scan);
		
		// Treat N as a regular total-power map, so do the post-processing accordingly...
		N.postprocess(scan);
	}
	
	public ScalarMap<InstrumentType, ScanType> getP() {
		final ScalarMap<InstrumentType, ScanType> P = (ScalarMap<InstrumentType, ScanType>) N.copy();
		
		final AstroMap q = Q.map;
		final AstroMap u = U.map;
		final AstroMap p = P.map;
		
		for(int i=p.sizeX(); --i >= 0; ) for(int j=p.sizeY(); --j >= 0; ) {	
			if(q.flag[i][j] == 0 && u.flag[i][j] == 0) {
				p.data[i][j] = Math.hypot(q.data[i][j], u.data[i][j]);
				
				// Propagate errors properly...
				if(p.data[i][j] > 0.0) {
					final double qf = q.data[i][j] / p.data[i][j];
					final double uf = u.data[i][j] / p.data[i][j];
					p.weight[i][j] = 1.0 / (qf*qf/q.weight[i][j] + uf*uf/u.weight[i][j]);
				}
				else p.weight[i][j] = 2.0 / (1.0/q.weight[i][j] + 1.0/u.weight[i][j]);
 				
				p.flag[i][j] = 0;
			}
			else p.flag[i][j] = 1;
		}
		P.id = "P";
		p.sanitize();
		return P;
	}
	
	public ScalarMap<InstrumentType, ScanType> getI() {
		return getI(getP());
	}	
	
	public ScalarMap<InstrumentType, ScanType> getI(ScalarMap<InstrumentType, ScanType> P) {	
		final ScalarMap<InstrumentType, ScanType> I = (ScalarMap<InstrumentType, ScanType>) N.copy();
		final AstroMap n = N.map;
		final AstroMap p = P.map;
		final AstroMap t = I.map;
		
		for(int i=n.sizeX(); --i >= 0; ) for(int j=n.sizeY(); --j >= 0; ) {
			if(n.flag[i][j] == 0 && p.flag[i][j] == 0) {
				t.data[i][j] = n.data[i][j] + p.data[i][j];
				t.weight[i][j] = 1.0 / (1.0/n.weight[i][j] + 1.0/p.weight[i][j]);
			}
			else t.flag[i][j] = 1;
		}
		I.id = "I";
		t.sanitize();
		return I;
	}
	
	public ScalarMap<InstrumentType, ScanType> getPolarFraction(ScalarMap<InstrumentType, ScanType> P, ScalarMap<InstrumentType, ScanType> I, double accuracy) {	
		final ScalarMap<InstrumentType, ScanType> F = (ScalarMap<InstrumentType, ScanType>) P.copy();
		final AstroMap p = P.map;
		final AstroMap t = I.map;
		final AstroMap f = F.map;
		
		final double minw = 1.0 / (accuracy * accuracy);
		
		for(int i=t.sizeX(); --i >= 0; ) for(int j=t.sizeY(); --j >= 0; ) {
			if(t.flag[i][j] == 0 && p.flag[i][j] == 0) {
				f.data[i][j] = p.data[i][j] / t.data[i][j];
			
				// f = a/b --> df/db = -a/b^2 * db
				// df2 = (da / b)^2 + (a db / b2)^2 = a/b * ((da/a)^2 + (db/b)^2)
				// 1 / wf = 1/(wa * b2) + a2/(wb*b4) = a2/b2 * (1/(wa*a2) + 1/(wb*b2))
				// wf = b2/a2 / (1/(wa*a2) + 1/(wb*b2))
				final double p2 = p.data[i][j] * p.data[i][j];
				final double t2 = t.data[i][j] * t.data[i][j];
				f.weight[i][j] = t2 / p2 / (1.0 / (p2 * p.weight[i][j]) + 1.0 / (t2 * t.weight[i][j]));
				
				// if sigma_f > accuracy than flag the datum
				if(f.weight[i][j] < minw) f.flag[i][j] = 1;
			}
			else f.flag[i][j] = 1;
		}
		
		F.id = "F";
		F.enableLevel = false;
		F.enableWeighting = false;
		F.enableBias = false;
		
		f.unit = Unit.unity;
		f.sanitize();
		return F;
	}
	
	@Override
	public void write(String path) throws Exception {
		N.write(path, true);
		
		if(!hasPolarization) {
			System.err.println();
			System.err.println("WARNING! No polarization products available.");
			System.err.println("         Consider setting the 'source.polarization' option");
			System.err.println("         to create Q, U, P and I images (and optionally F).");
			return;
		}
		
		Q.write(path, false);
		U.write(path, false);
		
		// Write P (polarized power)
		ScalarMap<InstrumentType, ScanType> P = getP();
		P.write(path, false);	
			
		// Write I (total power)
		ScalarMap<InstrumentType, ScanType> I = getI(P);
		I.write(path, false);	
		
		if(hasOption("source.polarization.fraction")) {
			// Write F (polarized fraction)
			double accuracy = hasOption("source.polarization.fraction.rmsclip") ?
					option("source.polarization.fraction.rmsclip").getDouble() : 0.03;
			
			ScalarMap<InstrumentType, ScanType> F = getPolarFraction(P, I, accuracy);
			F.write(path, false);
		}
		
		
	}

	@Override
	public String getSourceName() {
		return N.getSourceName();
	}

	@Override
	public Unit getUnit() {
		return N.getUnit();
	}
	

}
