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
import crush.astro.AstroMap;
import crush.sourcemodel.ScalarMap;

import java.util.*;

import util.Purifiable;
import util.Unit;

public class PolarMap extends SourceModel {

	ScalarMap N,Q,U;
	public boolean usePolarization = false;
	public boolean hasPolarization = false;
	
	public PolarMap(Array<?,?> instrument) {
		super(instrument);	
	}
	
	public Array<?,?> getArray() { return (Array<?,?>) getInstrument(); }

	public ScalarMap getMapInstance() {
		return new ScalarMap(getInstrument());
	}
	
	@Override
	public boolean isValid() {
		if(!N.isValid()) return false;
		if(hasPolarization) {
			if(!Q.isValid()) return false;
			if(!U.isValid()) return false;
		}
		return true;
	}

	
	public boolean usePolarization() {
		return usePolarization | hasOption("source.polar");
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?, ?>> scans) {
		super.createFrom(scans);
		
		N = getMapInstance();
		N.createFrom(scans);
		N.signalMode = PolarModulation.N;
		N.enableLevel = true;
		N.enableBias = true; // Includes blanking from Q and U
		N.enableWeighting = true;
		N.id = "N";
		
		Q = (ScalarMap) N.copy(false);
		Q.standalone();
		Q.signalMode = PolarModulation.Q;
		Q.enableLevel = false;
		Q.enableBias = false; // Prevents re-blanking on just Q
		Q.enableWeighting = true;
		Q.id = "Q";
		
		U = (ScalarMap) N.copy(false);
		U.standalone();
		U.signalMode = PolarModulation.U;
		U.enableLevel = false;
		U.enableBias = false; // Prevents re-blanking on just U
		U.enableWeighting = true;
		U.id = "U";
	}
	
	@Override
	public SourceModel copy(boolean withContents) {
		PolarMap copy = (PolarMap) super.copy(withContents);
		copy.N = (ScalarMap) N.copy(withContents);
		copy.Q = (ScalarMap) Q.copy(withContents);
		copy.U = (ScalarMap) U.copy(withContents);
		return copy;
	}
	
	
	@Override
	public synchronized void add(SourceModel model, double weight) {
		PolarMap other = (PolarMap) model;
		N.add(other.N, weight);
		if(usePolarization()) {
			Q.add(other.Q, weight);
			U.add(other.U, weight);
			hasPolarization = true;
		}
	}
	
	@Override
	public synchronized void add(Integration<?, ?> subscan) {
		((Purifiable) subscan).purify();
		
		N.add(subscan);	
		
		if(usePolarization()) {
			//((Purifiable) subscan).purify();
			Q.add(subscan);
			U.add(subscan);
		}
	}

	@Override
	public synchronized void setBase() {
		N.setBase();
		if(usePolarization()) {
			Q.setBase();
			U.setBase();
		}
	}

	@Override
	public synchronized void process(Scan<?, ?> scan) {
		N.process(scan);
		if(usePolarization()) {
			Q.process(scan);
			U.process(scan);
		}
	}
	
	@Override
	public synchronized void process(boolean verbose) throws Exception {		
		if(verbose) System.err.print("\n   [N] ");
		N.process(verbose);
		if(usePolarization()) {
			if(verbose) System.err.print("\n   [Q] ");
			Q.process(verbose);
			N.addMask(Q.getMask()); // Add the flagging data from Q
			
			if(verbose) System.err.print("\n   [U] ");
			U.process(verbose);
			N.addMask(U.getMask()); // Add the flagging data from U
		
			if(verbose) System.err.print("\n   ");	
		}
	}

	@Override
	public synchronized void reset(boolean clearContent) {
		N.reset(clearContent);
		if(usePolarization()) {
			Q.reset(clearContent);
			U.reset(clearContent);
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
	public synchronized void postprocess(Scan<?,?> scan) {
		super.postprocess(scan);
		
		// Treat N as a regular total-power map, so do the post-processing accordingly...
		N.postprocess(scan);
	}
	
	
	// Angles are measured East of North... 
	public ScalarMap getAngles(ScalarMap P) {
		final ScalarMap A = (ScalarMap) N.copy(false);
		
		final AstroMap q = Q.map;
		final AstroMap u = U.map;
		final AstroMap p = P.map;
		final AstroMap a = A.map;
		
		a.new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(p.isFlagged(i,j)) {
					a.flag(i, j);
					return;
				}
				
				final double p0 = p.getValue(i, j);
				
				if(p0 == 0.0) {
					a.flag(i, j);
					return;
				}
				
				final double q0 = q.getValue(i, j);
				final double u0 = u.getValue(i, j);
				
				final double sigma2Q = 1.0 / q.getWeight(i,j);
				final double sigma2U = 1.0 / u.getWeight(i,j);
				
				a.setValue(i, j, 0.5 * Math.atan2(u0, q0) / Unit.deg);
				a.setWeight(i, j, 4.0 * Unit.deg2 * p0 * p0 * p0 * p0 / (sigma2U * q0 * q0 + sigma2Q * u0 * u0));
			}
		}.process();
		
		A.id = "A";
		a.sanitize();
		
		A.enableLevel = false;
		A.enableWeighting = false;
		A.enableBias = false;
		
		a.setUnit(Unit.unity);
		
		return A;
	}	
	
	
	public ScalarMap getP() {
		final ScalarMap P = (ScalarMap) N.copy(false);
		
		final AstroMap q = Q.map;
		final AstroMap u = U.map;
		final AstroMap p = P.map;
		
		p.new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(q.isFlagged(i, j) || u.isFlagged(i, j)) {
					p.flag(i, j);
					return;
				}
				
				double q2 = q.getValue(i, j);
				double u2 = u.getValue(i, j);
				q2 *= q2; u2 *= u2;
				
				double sigma2Q = 1.0 / q.getWeight(i,j);
				double sigma2U = 1.0 / u.getWeight(i,j);
				
				// De-bias the Rice distribution
				// The following approximation is approximately correct
				// for S/N > 4 according to Simmons & Stewart 1984
				// Better approximations are numerically nasty.
				double psigma2 = (q2*sigma2Q + u2*sigma2U) / (q2 + u2);
				double pol2 = q2 + u2 - psigma2;
				if(pol2 < 0.0) pol2 = 0.0;
				
				// Propagate errors properly...
				p.setValue(i, j, Math.sqrt(pol2));
				p.setWeight(i, j, 1.0 / psigma2);
				p.unflag(i, j);
			}
		}.process();
		
		P.id = "P";
		p.sanitize();
		return P;
	}	
	
	public ScalarMap getI() {
		return getI(getP());
	}	
	
	public ScalarMap getI(ScalarMap P) {	
		final ScalarMap I = (ScalarMap) N.copy(false);
		final AstroMap n = N.map;
		final AstroMap p = P.map;
		final AstroMap t = I.map;
		
		t.new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(n.isUnflagged(i, j) && p.isUnflagged(i, j)) {
					t.setValue(i, j, n.getValue(i, j) + p.getValue(i, j));
					t.setWeight(i, j, 1.0 / (1.0/n.getWeight(i, j) + 1.0/p.getWeight(i, j)));
				}
				else t.flag(i, j);				
			}
		}.process();

		I.id = "I";
		t.sanitize();
		return I;
	}
	
	

	public ScalarMap getPolarFraction(ScalarMap P, ScalarMap I, double accuracy) {	
		final ScalarMap F = (ScalarMap) P.copy(false);
		final AstroMap p = P.map;
		final AstroMap t = I.map;
		final AstroMap f = F.map;
		
		final double minw = 1.0 / (accuracy * accuracy);
		
		f.new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(t.isFlagged(i, j) || p.isFlagged(i, j)) {
					f.flag(i, j);	
					return;
				}
				
				f.setValue(i, j, p.getValue(i, j) / t.getValue(i, j));

				// f = a/b --> df/db = -a/b^2 * db
				// df2 = (da / b)^2 + (a db / b2)^2 = a/b * ((da/a)^2 + (db/b)^2)
				// 1 / wf = 1/(wa * b2) + a2/(wb*b4) = a2/b2 * (1/(wa*a2) + 1/(wb*b2))
				// wf = b2/a2 / (1/(wa*a2) + 1/(wb*b2))
				double p2 = p.getValue(i, j);
				
				if(p2 == 0.0) {
					f.flag(i, j);	
					return;
				}
				
				double t2 = t.getValue(i, j);
				p2 *= p2; t2 *= t2;
				
				f.setWeight(i, j, t2 / p2 / (1.0 / (p2 * p.getWeight(i, j)) + 1.0 / (t2 * t.getWeight(i, j))));

				// if sigma_f > accuracy than flag the datum
				if(f.getWeight(i, j) < minw) f.flag(i, j);			
			}
		}.process();
		
		F.id = "F";
		F.enableLevel = false;
		F.enableWeighting = false;
		F.enableBias = false;
		
		f.setUnit(Unit.unity);
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
		ScalarMap P = getP();
		P.write(path, false);	
			
		// Write I (total power)
		ScalarMap I = getI(P);
		I.write(path, false);	
		
		if(hasOption("source.polar.angles")) {
			ScalarMap A = getAngles(P);
			A.write(path, false);
		}
		
		if(hasOption("source.polar.fraction")) {
			// Write F (polarized fraction)
			double accuracy = hasOption("source.polar.fraction.rmsclip") ?
					option("source.polar.fraction.rmsclip").getDouble() : 0.03;
			
			ScalarMap F = getPolarFraction(P, I, accuracy);
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

	@Override
	public void noParallel() {
		if(N != null) N.noParallel();
		if(Q != null) Q.noParallel();
		if(U != null) U.noParallel();
	}

	@Override
	public int countPoints() {
		return N.countPoints() + Q.countPoints() + U.countPoints();
	}

	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		if(name.startsWith("N.")) return N.getFormattedEntry(name.substring(2), formatSpec);
		else if(name.startsWith("Q.")) return Q.getFormattedEntry(name.substring(2), formatSpec);
		else if(name.startsWith("U.")) return U.getFormattedEntry(name.substring(2), formatSpec);
		else return super.getFormattedEntry(name, formatSpec);
	}
	

	
}
