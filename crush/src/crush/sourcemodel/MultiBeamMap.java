/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import java.util.Collection;

import kovacs.data.CartesianGrid2D;
import kovacs.data.Data2D;
import kovacs.data.Data2D.Task;
import kovacs.data.GridMap;
import kovacs.fft.MultiFFT;
import kovacs.math.Coordinate2D;
import kovacs.math.SphericalCoordinates;
import kovacs.math.Vector2D;
import kovacs.util.Constant;
import kovacs.util.ExtraMath;
import kovacs.util.Parallel;
import crush.DualBeam;
import crush.Instrument;
import crush.Integration;
import crush.Pixel;
import crush.Scan;
import crush.SourceModel;

public class MultiBeamMap extends ScalarMap {
	GridMap<Coordinate2D> transformer;
		
	Class<SphericalCoordinates> scanningSystem;
	MultiFFT fft = new MultiFFT();

	
	double trackSpacing;
	boolean isSpectrum = false;

	double sumScanWeight = 0.0;
	
	public MultiBeamMap(Instrument<?> instrument, double trackSpacing) {
		super(instrument);
		this.trackSpacing = trackSpacing;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public SourceModel copy(boolean withContents) {
		MultiBeamMap copy = (MultiBeamMap) super.copy(withContents);
		copy.fft = new MultiFFT();
		if(transformer != null) copy.transformer = (GridMap<Coordinate2D>) transformer.copy();
		return copy;
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
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
		
		transformer = new GridMap<Coordinate2D>(nx, ny + 2);		
		transformer.setGrid(new CartesianGrid2D());
		transformer.setReference(new Coordinate2D());
		
		double dFx = 1.0 / (nx * delta.x());
		double dFy = 1.0 / (ny * delta.y());
		
		transformer.getGrid().setResolution(dFx, dFy);
		
				
	}
	
	
	@Override
	public void add(SourceModel model, double weight) {
		MultiBeamMap multibeam = (MultiBeamMap) model;
		super.add(multibeam, weight);
		
		sumScanWeight += weight;
		
		if(!multibeam.isSpectrum) throw new IllegalStateException("Expecting spectrum to accumulate.");
		
		transformer.addWeightedDirect(multibeam.transformer, weight);		
		
		isSpectrum = true;
	}

	
	public double getPositionAngle(DualBeam dual) {
		return dual.getChopAngle(map.getReference());
	}
	
	@Override
	public void process(Scan<?, ?> scan) {
		DualBeam dual = (DualBeam) scan;
		
		if(!isNormalized) {
			map.normalize();
			isNormalized = true;
		}
		
		map.unflag();	
		
		double sigma = trackSpacing / Constant.sigmasInFWHM;
		double angle = getPositionAngle(dual);
		Vector2D delta = map.getResolution();
		
		double[][] beam = Data2D.getGaussian(sigma / delta.x(), sigma / delta.y(), angle, Constant.sigmasInFWHM / Constant.sqrt2);
			
		map.smooth(beam, trackSpacing);
		trim();
			
		map.sanitize();
		
		/*
		try { map.write("increment.fits"); }
		catch(Exception e) { e.printStackTrace(); }
		*/
		
		forwardTransform();
		
		/*
		try { 
			transformer.write("fft.fits");
			System.err.println(" Written fft.fits");
		}
		catch(Exception e) { e.printStackTrace(); }
		*/
		
		deconvolve(dual);
		
		isReady = true;
	}
	
	public void forwardTransform() {
		if(isSpectrum) throw new IllegalStateException("Expecting map to transform forward.");
		
		transformer.clear();
		
		// TODO convert weights into a normalized window function s.t. transform is essentially a PSD.
		// TODO also keep track of total weight s.t. add(sourceModel) properly takes it into account.

	
		Task<Double> loader = map.new Task<Double>() {	
			double sumw;
			
			@Override
			public void init() { sumw = 0.0; }
			
			@Override
			public void process(int i, int j) {	
				final double w = map.getWeight(i, j);
				sumw += w;
				transformer.setValue(i, j, w * map.getValue(i,  j));
			}
			
			@Override 
			public Double getPartialResult() { return sumw; }
			
			@Override
			public Double getResult() {
				double globalSumW = 0.0;
				for(Parallel<Double> task : getWorkers()) globalSumW += task.getPartialResult();
				return globalSumW;
			}
			
		};
		
		loader.process();
		
		double w = loader.getResult();
		transformer.unflag();
		transformer.setWeight(w); // All frequencies have equal weight before deconvolution
		
		/*
		try { 
			transformer.write("transformer-dual.fits");
			System.err.println(" Written transformer-dual.fits");
		}
		catch(Exception e) { e.printStackTrace(); }
		*/
	
			
		fft.real2Amplitude(transformer.getData());		
		isSpectrum = true;
	}
	
	
	public void backTransform() {
		if(!isSpectrum) throw new IllegalStateException("Expecting spectrum to transform back.");
	
		fft.amplitude2Real(transformer.getData());
		
		// undo weight multiplication...
		map.new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(map.getWeight(i, j) > 0.0) map.setValue(i, j, transformer.getValue(i,  j) * sumScanWeight / map.getWeight(i, j));
				else map.setValue(i,  j, 0.0);
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

		transformer.new Task<Void>() {		
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
					double temp = transformer.getValue(i,  j);
					transformer.setValue(i,  j, -transformer.getValue(i, j+1));
					transformer.setValue(i,  j+1, temp);
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
			final double w = transformer.getWeight(i, j);
			if(w == 0.0) continue;
			transformer.scaleValue(i,  j, 1.0 / w);
			//sumiw += 1.0 / w;
			//n++;			
		}
		
		//transformer.scale(sumiw / n);
	}
	

	private MultiBeamMap getDualBeam(DualBeam scan) {
	
		base.unflag();
		
		final MultiBeamMap dual = (MultiBeamMap) copy(true);
		dual.standalone();
		
		final double l = 0.5 * scan.getChopSeparation();
		final double a = getPositionAngle(scan);
		final Vector2D delta = map.getResolution();
		
		final int di = (int) Math.round(l * Math.cos(a) / delta.x());
		final int dj = (int) Math.round(l * Math.sin(a) / delta.y());	
		
		// calculate the dual-beam image;
		dual.map.new Task<Void>() {		
			@Override
			public void process(int i, int j) {
				
				double l = map.containsIndex(i + di,  j + dj) ? map.getValue(i + di, j + dj) : 0.0;
				double r = map.containsIndex(i - di,  j - dj) ? map.getValue(i - di, j - dj) : 0.0;

				//if(Double.isNaN(l)) l = 0.0;
				//if(Double.isNaN(r)) r = 0.0;

				dual.map.setValue(i, j, l - r);

				l = base.containsIndex(i + di,  j + dj) ? base.getValue(i + di, j + dj) : 0.0;
				r = base.containsIndex(i - di,  j - dj) ? base.getValue(i - di, j - dj) : 0.0;
				
				//if(Double.isNaN(l)) l = 0.0;
				//if(Double.isNaN(r)) r = 0.0;

				dual.base.setValue(i, j, l - r);
				dual.base.unflag(i, j);
			}
		}.process();

		//dual.map.sanitize();
		
		return dual;
	}
	
	@Override
	public void sync(Integration<?, ?> integration) {		
		MultiBeamMap dual = getDualBeam((DualBeam) integration.scan);
		
		/*
		try { 
			dual.map.write("reconstructed-dual.fits");
			System.err.println(" Written reconstructed-dual.fits");
		}
		catch(Exception e) { e.printStackTrace(); }
		*/

		/*
		try { 
			dual.base.write("base-dual.fits");
			System.err.println(" Written base-dual.fits");
		}
		catch(Exception e) { e.printStackTrace(); }
		*/
		
		dual.syncSuper(integration);
	}
	
	private void syncSuper(Integration<?, ?> integration) {
		super.sync(integration);
	}
		
	@Override
	public void reset(boolean clearContent) {
		sumScanWeight = 0.0;
		super.reset(clearContent);
		if(clearContent) transformer.clear();
	}

	public void trim() {
		// Trim the outer pixels...
		map.new Task<Void>() {	
			@Override
			public void process(int i, int j) {
				if(map.getWeight(i, j) == 0.0) return;
				int neighbors = -1;
				
				for(int di = -1; di <= 1; di++) for(int dj = -1; dj <= 1; dj++) if(map.containsIndex(i + di, j + dj))
					if(map.getWeight(i + di, j + dj) > 0.0) neighbors++;
				
				if(neighbors < 8) map.flag(i, j);		
			}
					
		}.process();
		
		//map.sanitize();	
	}
	
	
	@Override
	public void process(boolean verbose) {		
		normalizeTransformer();
		isNormalized = true;
		
		backTransform();
		
		
		if(base != null) map.addImage(base.getData());
		
		/*
		try { 
			map.write("deconvolved-radec.fits");
			System.err.println(" Written deconvolved-radec.fits");
		}
		catch(Exception e) { e.printStackTrace(); }
		*/
		
		//enableLevel = false;
		
		super.process(verbose);
		
		/*
		try { 
			map.write("processed.fits");
			System.err.println(" Written processed.fits");
		}
		catch(Exception e) { e.printStackTrace(); }
		*/
	}
	
	
	
	@Override
	public void postprocess(Scan<?,?> scan) {
		backTransform();		
		if(base != null) map.addImage(base.getData());
		super.postprocess(scan);
	}

	@Override
	public void noParallel() {
		super.noParallel();
		transformer.noParallel();
	}

	@Override
	protected void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain) {
		throw new UnsupportedOperationException("Cannot calculate coupling for multi-beam maps.");
	}
	
	// TODO what to do about calcCoupling...
	
}
