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


package crush.sourcemodel;

import java.util.Collection;

import crush.Instrument;
import crush.Integration;
import crush.Pixel;
import crush.Scan;
import crush.SourceModel;
import crush.telescope.DualBeam;
import jnum.Constant;
import jnum.ExtraMath;
import jnum.data.image.FlatGrid2D;
import jnum.data.image.Flag2D;
import jnum.data.image.Gaussian2D;
import jnum.data.image.Observation2D;
import jnum.fft.MultiFFT;
import jnum.math.Coordinate2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.parallel.ParallelTask;

public class MultiBeamMap extends AstroIntensityMap {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1489783970843373449L;

	Observation2D transformer;
		
	Class<SphericalCoordinates> scanningSystem;
	MultiFFT fft = new MultiFFT(getExecutor());

	double trackSpacing;
	boolean isSpectrum = false;

	double sumScanWeight = 0.0;
	
	public MultiBeamMap(Instrument<?> instrument, double trackSpacing) {
		super(instrument);
		this.trackSpacing = trackSpacing;
	}

	@Override
	public MultiBeamMap getWorkingCopy(boolean withContents) {
		MultiBeamMap copy = (MultiBeamMap) super.getWorkingCopy(withContents);
		copy.fft = new MultiFFT();
		if(transformer != null) copy.transformer = transformer.copy();
		return copy;
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) throws Exception {
		double maxThrow = 0.0;

		for(Scan<?,?> scan : collection) {
			DualBeam dual = (DualBeam) scan;
			maxThrow = Math.max(maxThrow, dual.getChopSeparation());
		}
		
		super.createFrom(collection);
		initTransformer(maxThrow);
	}
	
	public void initTransformer(double maxThrow) {
		Vector2D delta = map.getResolution();

		// make spectrum large enough to avoid edge effects
		int nx = ExtraMath.pow2ceil((int) Math.ceil(map.sizeX() + maxThrow / delta.x()));
		int ny = ExtraMath.pow2ceil((int) Math.ceil(map.sizeY() + maxThrow / delta.y()));
		
		transformer = new Observation2D(Double.class, Flag2D.TYPE_INT);
		transformer.setSize(nx, ny + 2);		
		transformer.setGrid(new FlatGrid2D());
		transformer.setReference(new Coordinate2D());
		
		double dFx = 1.0 / (nx * delta.x());
		double dFy = 1.0 / (ny * delta.y());
		
		transformer.getGrid().setResolution(dFx, dFy);
		
				
	}
	
	
	@Override
	public void addModelData(SourceModel model, double weight) {
		MultiBeamMap multibeam = (MultiBeamMap) model;
		super.add(multibeam, weight);
		
		sumScanWeight += weight;
		
		if(!multibeam.isSpectrum) throw new IllegalStateException("Expecting spectrum to accumulate.");
		
		transformer.accumulate(multibeam.transformer, weight);		
		
		isSpectrum = true;
	}

	
	public double getPositionAngle(DualBeam dual) {
		return dual.getChopAngle(map.getReference());
	}
	
	@Override
	public void process(Scan<?, ?> scan) {
		DualBeam dual = (DualBeam) scan;
		
		map.endAccumulation();
	
		map.unflag();	
					
		map.smooth(new Gaussian2D(trackSpacing));
		
		trim();
			
		map.validate();
		
		forwardTransform();
		deconvolve(dual);

	}
	
	public void forwardTransform() {
		if(isSpectrum) throw new IllegalStateException("Expecting map to transform forward.");
		
		transformer.noData();
		
		// TODO convert weights into a normalized window function s.t. transform is essentially a PSD.
		// TODO also keep track of total weight s.t. add(sourceModel) properly takes it into account.

	
		Observation2D.Fork<Double> loader = map.new Fork<Double>() {	
			double sumw;
			
			@Override
			public void init() { sumw = 0.0; }
			
			@Override
			public void process(int i, int j) {	
				final double w = map.weightAt(i, j);
				sumw += w;
				transformer.set(i, j, w * map.get(i,  j).doubleValue());
			}
			
			@Override 
			public Double getLocalResult() { return sumw; }
			
			@Override
			public Double getResult() {
				double globalSumW = 0.0;
				for(ParallelTask<Double> task : getWorkers()) globalSumW += task.getLocalResult();
				return globalSumW;
			}
			
		};
		
		loader.process();
		
		double w = loader.getResult();
		transformer.unflag();
		transformer.getWeights().fill(w); // All frequencies have equal weight before deconvolution
			
		fft.real2Amplitude((Object[]) transformer.getImage().getCore());		
		isSpectrum = true;
	}
	
	
	public void backTransform() {
		if(!isSpectrum) throw new IllegalStateException("Expecting spectrum to transform back.");
	
		fft.amplitude2Real((Object[]) transformer.getImage().getCore());
		
		// undo weight multiplication...
		map.new Fork<Void>() {
			@Override
			public void process(int i, int j) {
				if(map.weightAt(i, j) > 0.0) map.set(i, j, transformer.get(i, j).doubleValue() * sumScanWeight / map.weightAt(i, j));
				else map.set(i,  j, 0.0);
			}
		}.process();
				
		isSpectrum = false;
		
	}
	
	public void deconvolve(DualBeam scan) {
		if(!isSpectrum) throw new IllegalStateException("Expecting spectrum to deconvolve.");
		
		final double threshold = hasOption("deconvolve.above") ? option("deconvolve.above").getDouble() : 0.1;
		
		double l = 0.5 * scan.getChopSeparation();
		double a = scan.getChopAngle(map.getReference());
		Vector2D delta = transformer.getResolution();

		final double wx = Constant.twoPi * delta.x() * l * Math.cos(a);
		final double wy = Constant.twoPi * delta.y() * l * Math.sin(a);

		final int Nx = transformer.sizeX() >> 1;

		transformer.new Fork<Void>() {		
			@Override
			public void process(int i, int j) {	
				// If j points to the imaginary part of the packed complex number then ignore...
				if((j & 1) != 0) return;
				
				int fj = j>>1; 
				
				// i has positive and negative frequencies...
				int fi = i;
				if(i > Nx) fi = i - (Nx<<1);

				final double T = 2.0 * Math.sin(fi * wx + fj * wy);
				
				if(Math.abs(T) < threshold) {
					transformer.clear(i, j);
					transformer.clear(i, j+1);
				}
				else {
					final double iT = 1.0 / T;
					transformer.scale(i, j, iT);
					transformer.scale(i, j+1, iT);
							
					// multiply by i...
					double temp = transformer.get(i, j).doubleValue();
					transformer.set(i, j, -transformer.get(i, j+1).doubleValue());
					transformer.set(i, j+1, temp);
				}			
			}
		}.process();

	}
	
	private void normalizeTransformer() {
		//if(!isSpectrum) throw new IllegalStateException("Expecting spectrum to normalize.");
		
		// The transformer now contains the weighted sum of the deconvolved spectrum.	
		// w = 1/T^2 --> sum T^2 = sum 1/w
		//double sumiw = 0.0;
		//int n = 0;
		for(int i=transformer.sizeX(); --i >= 0; ) for(int j=transformer.sizeY(); --j >= 0; ) {
			final double w = transformer.weightAt(i, j);
			if(w == 0.0) continue;
			transformer.scale(i,  j, 1.0 / w);
			//sumiw += 1.0 / w;
			//n++;			
		}
		
		//transformer.scale(sumiw / n);
	}
	

	private MultiBeamMap getDualBeam(DualBeam scan) {
	
		
		final MultiBeamMap dual = getWorkingCopy(true);
		dual.standalone();
		
		final double l = 0.5 * scan.getChopSeparation();
		final double a = getPositionAngle(scan);
		final Vector2D delta = map.getResolution();
		
		final int di = (int) Math.round(l * Math.cos(a) / delta.x());
		final int dj = (int) Math.round(l * Math.sin(a) / delta.y());	
		
		// calculate the dual-beam image;
		dual.map.new Fork<Void>() {		
			@Override
			public void process(int i, int j) {
				
				double l = map.containsIndex(i + di,  j + dj) ? map.get(i + di, j + dj).doubleValue() : 0.0;
				double r = map.containsIndex(i - di,  j - dj) ? map.get(i - di, j - dj).doubleValue() : 0.0;

				//if(Double.isNaN(l)) l = 0.0;
				//if(Double.isNaN(r)) r = 0.0;

				dual.map.set(i, j, l - r);

				l = base.containsIndex(i + di,  j + dj) ? base.get(i + di, j + dj).doubleValue() : 0.0;
				r = base.containsIndex(i - di,  j - dj) ? base.get(i - di, j - dj).doubleValue() : 0.0;
				
				//if(Double.isNaN(l)) l = 0.0;
				//if(Double.isNaN(r)) r = 0.0;

				dual.base.set(i, j, l - r);
			}
		}.process();

		//dual.map.sanitize();
		
		return dual;
	}
	
	@Override
	public void sync(Integration<?, ?> integration) {		
		MultiBeamMap dual = getDualBeam((DualBeam) integration.scan);
		dual.syncSuper(integration);
	}
	
	private void syncSuper(Integration<?, ?> integration) {
		super.sync(integration);
	}
		
	@Override
	public void resetProcessing() {
	    super.resetProcessing();
		sumScanWeight = 0.0;
	}

	@Override
    public void clearContent() {
	    super.clearContent();
        transformer.noData();
    }
	
	public void trim() {
		// Trim the outer pixels...
		map.new Fork<Void>() {	
			@Override
			public void process(int i, int j) {
				if(map.weightAt(i, j) == 0.0) return;
				int neighbors = -1;
				
				for(int di = -1; di <= 1; di++) for(int dj = -1; dj <= 1; dj++) if(map.containsIndex(i + di, j + dj))
					if(map.weightAt(i + di, j + dj) > 0.0) neighbors++;
				
				if(neighbors < 8) map.flag(i, j);		
			}
					
		}.process();
		
		//map.sanitize();	
	}
	
	
	@Override
	public void process() throws Exception {		
		normalizeTransformer();
		
		backTransform();
		
		if(base != null) map.add(base);
		
		super.process();
	}
	
	
	
	@Override
	public void postProcess(Scan<?,?> scan) {
		backTransform();		
		if(base != null) map.add(base);
		super.postProcess(scan);
	}

	@Override
	public void noParallel() {
		super.noParallel();
		transformer.noParallel();
	}
	
	@Override
    public void setParallel(int threads) {
        super.setParallel(threads);
        transformer.setParallel(threads);
    }

	@Override
	protected void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain) {
		throw new UnsupportedOperationException("Cannot calculate coupling for multi-beam maps.");
	}
	
	// TODO what to do about calcCoupling...
	
}
