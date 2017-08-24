/*******************************************************************************
 * Copyright (c) 2017 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.polarization;


import crush.*;
import crush.array.*;
import crush.sourcemodel.AstroMap;
import jnum.Unit;
import jnum.data.image.Observation2D;
import jnum.math.SphericalCoordinates;

import java.util.*;
import java.util.concurrent.ExecutorService;


public class PolarMap extends SourceModel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -533094900293482665L;
	
	AstroMap N, Q, U;
	
	public boolean usePolarization = false;
	public boolean hasPolarization = false;
	
	public PolarMap(Camera<?> instrument) {
		super(instrument);	
	}
	
	public Camera<?> getArray() { return (Camera<?>) getInstrument(); }

	
	public AstroMap getMapInstance() {
		return new AstroMap(getInstrument());
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

	@Override
    public void clearProcessBrief() {
	    super.clearProcessBrief();
	    if(N != null) N.clearProcessBrief();
	    if(Q != null) Q.clearProcessBrief();
	    if(U != null) U.clearProcessBrief();
	}
	
	public boolean usePolarization() {
		return usePolarization | hasOption("source.polar");
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?, ?>> scans) throws Exception {
		super.createFrom(scans);
		
		N = getMapInstance();
		N.createFrom(scans);
		N.signalMode = PolarModulation.N;
		N.enableLevel = true;
		N.enableBias = true; // Includes blanking from Q and U
		N.enableWeighting = true;
		N.setID("N");
		
		Q = (AstroMap) N.getWorkingCopy(false);
		Q.signalMode = PolarModulation.Q;
		Q.standalone();
		Q.enableLevel = false;
		Q.enableBias = false; // Prevents re-blanking on just Q or U
		Q.enableWeighting = true;
		Q.setID("Q");
					
		U = (AstroMap) Q.getWorkingCopy(false);
		U.signalMode = PolarModulation.U;
		U.standalone();		
		U.setID("U");
	
	}
	
	@Override
	public SourceModel getWorkingCopy(boolean withContents) {
		PolarMap copy = (PolarMap) super.getWorkingCopy(withContents);
		copy.N = (AstroMap) N.getWorkingCopy(withContents);
		copy.Q = (AstroMap) Q.getWorkingCopy(withContents);
		copy.U = (AstroMap) U.getWorkingCopy(withContents);
		return copy;
	}
	
	
	@Override
	public void addModel(SourceModel model, double weight) {
		PolarMap other = (PolarMap) model;
		N.add(other.N, weight);
		
		if(usePolarization()) {
			Q.add(other.Q, weight);
			U.add(other.U, weight);
			hasPolarization = true;
		}
	}
	
	@Override
	public void add(Integration<?, ?> subscan) {
		((Purifiable) subscan).purify();
		
		N.add(subscan);	
		
		if(usePolarization()) {
			//((Purifiable) subscan).purify();
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
	public void process() throws Exception {		
		addProcessBrief("[N] ");
		N.process();
		
		if(usePolarization()) {
			addProcessBrief("[Q] ");
			Q.process();
			N.mergeMask(Q.map); // Add the flagging data from Q
			
			addProcessBrief("[U] ");
			U.process();
			N.mergeMask(U.map); // Add the flagging data from U
		}
	}

	@Override
	public void resetProcessing() {
	    super.resetProcessing();
		N.resetProcessing();
		if(usePolarization()) {
			Q.resetProcessing();
			U.resetProcessing();
		}
	}
	
	@Override
    public void clearContent() {
        N.clearContent();
        if(usePolarization()) {
            Q.clearContent();
            U.clearContent();
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
	
	
	// Angles are measured East of North... 
	public AstroMap getAngles(AstroMap P, AstroMap F) {
		final AstroMap A = (AstroMap) N.getWorkingCopy(false);
		
		final Observation2D q = Q.map;
		final Observation2D u = U.map;
		final Observation2D p = P.map;
		final Observation2D f = F.map;
		final Observation2D a = A.map;
		
		a.new Fork<Void>() {
			@Override
			public void process(int i, int j) {
			  
				if(!f.isValid(i,j)) {
				    a.flag(i, j);
				    return;
				}
				else a.unflag(i, j);
				
				final double p0 = p.get(i, j).doubleValue();
				
				if(p0 == 0.0) {
					a.flag(i, j);
					return;
				}
				
				final double q0 = q.get(i, j).doubleValue();
				final double u0 = u.get(i, j).doubleValue();
				
				final double sigma2Q = 1.0 / q.weightAt(i,j);
				final double sigma2U = 1.0 / u.weightAt(i,j);
				
				a.set(i, j, 0.5 * Math.atan2(u0, q0) / Unit.deg);
				a.setWeightAt(i, j, 4.0 * Unit.deg2 * p0 * p0 * p0 * p0 / (sigma2U * q0 * q0 + sigma2Q * u0 * u0));
			}
		}.process();
		
		A.setID("A");
		a.validate();
		
		A.enableLevel = false;
		A.enableWeighting = false;
		A.enableBias = false;
		
		a.setUnit(Unit.unity);
		
		return A;
	}	
	
	
	public AstroMap getP() {
		final AstroMap P = (AstroMap) N.getWorkingCopy(false);
		
		final Observation2D q = Q.map;
		final Observation2D u = U.map;
		final Observation2D p = P.map;
		
		p.new Fork<Void>() {
			@Override
			public void process(int i, int j) {
				if(!q.isValid(i, j) || !u.isValid(i, j)) {
					p.flag(i, j);
					return;
				}
				
				double q2 = q.get(i, j).doubleValue();
				double u2 = u.get(i, j).doubleValue();
				q2 *= q2; u2 *= u2;
				
				double sigma2Q = 1.0 / q.weightAt(i,j);
				double sigma2U = 1.0 / u.weightAt(i,j);
				
				// De-bias the Rice distribution
				// The following approximation is approximately correct
				// for S/N > 4 according to Simmons & Stewart 1984
				// Better approximations are numerically nasty.
				double psigma2 = (q2*sigma2Q + u2*sigma2U) / (q2 + u2);
				double pol2 = q2 + u2 - psigma2;
				if(pol2 < 0.0) pol2 = 0.0;
				
				// Propagate errors properly...
				p.set(i, j, Math.sqrt(pol2));
				p.setWeightAt(i, j, 1.0 / psigma2);
				p.unflag(i, j);
			}
		}.process();
		
		P.setID("P");
		p.validate();
		return P;
	}	
	
	public AstroMap getI() {
		return getI(getP());
	}	
	
	public AstroMap getI(AstroMap P) {	
		final AstroMap I = (AstroMap) N.getWorkingCopy(false);
		final Observation2D n = N.map;
		final Observation2D p = P.map;
		final Observation2D t = I.map;
		
		t.new Fork<Void>() {
			@Override
			public void process(int i, int j) {
				if(n.isValid(i, j) && p.isValid(i, j)) {
					t.set(i, j, n.get(i, j).doubleValue() + p.get(i, j).doubleValue());
					t.setWeightAt(i, j, n.weightAt(i, j));
					t.unflag(i, j);
					// TODO check on what's the proper uncertainty on I (same as N or N+P from independent N,P)...
					//t.setWeight(i, j, 1.0 / (1.0/n.getWeight(i, j) + 1.0/p.getWeight(i, j)));
				}
				else t.flag(i, j);				
			}
		}.process();

		I.setID("I");
		t.validate();
		return I;
	}
	
	

	public AstroMap getPolarFraction(AstroMap P, AstroMap I, double accuracy) {	
		final AstroMap F = (AstroMap) P.getWorkingCopy(false);
		final Observation2D p = P.map;
		final Observation2D t = I.map;
		final Observation2D f = F.map;
		
		final double minw = 1.0 / (accuracy * accuracy);
		
		f.new Fork<Void>() {
			@Override
			public void process(int i, int j) {
			     
				if(!t.isValid(i, j) || !p.isValid(i, j)) {
					f.flag(i, j);	
					return;
				}
				
				f.set(i, j, p.get(i, j).doubleValue() / t.get(i, j).doubleValue());

				// f = a/b --> df/db = -a/b^2 * db
				// df2 = (da / b)^2 + (a db / b2)^2 = a/b * ((da/a)^2 + (db/b)^2)
				// 1 / wf = 1/(wa * b2) + a2/(wb*b4) = a2/b2 * (1/(wa*a2) + 1/(wb*b2))
				// wf = b2/a2 / (1/(wa*a2) + 1/(wb*b2))
				double p2 = p.get(i, j).doubleValue();
				
				if(p2 == 0.0) {
					f.flag(i, j);	
					return;
				}
				
				
				double t2 = t.get(i, j).doubleValue();
				p2 *= p2; t2 *= t2;
				
				f.setWeightAt(i, j, t2 / p2 / (1.0 / (p2 * p.weightAt(i, j)) + 1.0 / (t2 * t.weightAt(i, j))));

				// if sigma_f > accuracy than flag the datum
				if(f.weightAt(i, j) < minw) f.flag(i, j);	
				else f.unflag(i, j);
			}
		}.process();
		
		F.setID("F");
		F.enableLevel = false;
		F.enableWeighting = false;
		F.enableBias = false;
		
		f.setUnit(Unit.unity);
		f.validate();
		return F;
	}
	
	@Override
	public void write(String path) throws Exception {
		N.write(path);
		
		if(!hasPolarization) {
		    warning("No polarization products available.");
		    CRUSH.suggest(this,
		            "         Consider setting the 'source.polarization' option\n" +
		            "         to create Q, U, P and I images (and optionally F).\n");
			return;
		}
		
		Q.write(path);
		U.write(path);
		
		// Write P (polarized power)
		AstroMap P = getP();
		P.write(path);	
			
		// Write I (total power)
		AstroMap I = getI(P);
		I.write(path);	
		
		// Write F (polarized fraction)
        double accuracy = hasOption("source.polar.fraction.rmsclip") ?
                option("source.polar.fraction.rmsclip").getDouble() : 0.03;
        
        AstroMap F = getPolarFraction(P, I, accuracy);
		
		if(hasOption("source.polar.fraction")) {
			F.write(path);
		}

        if(hasOption("source.polar.angles")) {
            AstroMap A = getAngles(P, F);
            A.write(path);
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
	public void setParallel(int threads) {
		if(N != null) N.setParallel(threads);
		if(Q != null) Q.setParallel(threads);
		if(U != null) U.setParallel(threads);
	}
	
	@Override
	public int getParallel() {
		if(N != null) return N.getParallel();
		if(Q != null) return Q.getParallel();
		if(U != null) return U.getParallel();
		return 1;
	}
	
	@Override
	public int countPoints() {
		return N.countPoints() + Q.countPoints() + U.countPoints();
	}

	@Override
	public Object getTableEntry(String name) {
		if(name.startsWith("N.")) return N.getTableEntry(name.substring(2));
		else if(name.startsWith("Q.")) return Q.getTableEntry(name.substring(2));
		else if(name.startsWith("U.")) return U.getTableEntry(name.substring(2));
		else return super.getTableEntry(name);
	}

	@Override
	public void setExecutor(ExecutorService executor) {
		if(N != null) N.setExecutor(executor);
		if(Q != null) Q.setExecutor(executor);
		if(U != null) U.setExecutor(executor);
		
	}

	@Override
	public ExecutorService getExecutor() {
		if(N != null) return N.getExecutor();
		if(Q != null) return Q.getExecutor();
		if(U != null) return U.getExecutor();
		return null;
	}

	@Override
	public SphericalCoordinates getReference() {
		return N.getReference();
	}

	

	
}
