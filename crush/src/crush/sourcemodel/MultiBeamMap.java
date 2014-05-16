package crush.sourcemodel;

import java.util.Collection;

import kovacs.data.ArrayUtil;
import kovacs.data.CartesianGrid2D;
import kovacs.data.Data2D;
import kovacs.data.GridMap;
import kovacs.data.Data2D.Task;
import kovacs.fft.MultiFFT;
import kovacs.math.Coordinate2D;
import kovacs.math.SphericalCoordinates;
import kovacs.math.Vector2D;
import kovacs.util.Configurator;
import kovacs.util.Constant;
import kovacs.util.ExtraMath;
import kovacs.util.Unit;
import kovacs.util.Util;
import crush.DualBeam;
import crush.Instrument;
import crush.Integration;
import crush.Pixel;
import crush.Scan;
import crush.SourceModel;
import crush.astro.AstroMap;

public class MultiBeamMap extends ScalarMap {
	GridMap<Coordinate2D> transformer;
	Class<SphericalCoordinates> scanningSystem;
	MultiFFT fft = new MultiFFT();
	
	
	double trackSpacing;
	boolean isSpectrum = false;
	
	public MultiBeamMap(Instrument<?> instrument, double trackSpacing) {
		super(instrument);
		this.trackSpacing = trackSpacing;
		
		//preferredStem = "multibeam";
	}
	
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
		
		transformer = new GridMap<Coordinate2D>(nx, ny);
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
		
		if(!multibeam.isSpectrum) throw new IllegalStateException("Expecting spectrum to accumulate.");
		
		transformer.addDirect(multibeam.transformer, weight);		
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

		// calculate the dual-beam image;
		map.new Task<Void>() {
			@Override
			public void process(int i, int j) {	
				transformer.setValue(i, j, map.getValue(i,  j) * map.getWeight(i, j));
			}
		}.process();

		transformer.unflag();
		
		/*
		try { 
			transformer.write("transformer-dual.fits");
			System.err.println(" Written transformer-dual.fits");
		}
		catch(Exception e) { e.printStackTrace(); }
		*/
	
		transformer.setWeight(1.0); // All frequencies have equal weight before deconvolution
		
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
				if(map.getWeight(i, j) > 0.0) map.setValue(i, j, transformer.getValue(i,  j) / map.getWeight(i, j));
				else map.setValue(i,  j, 0.0);
			}
		}.process();
				
		isSpectrum = false;
		
	}
	
	public void deconvolve(DualBeam scan) {
		if(!isSpectrum) throw new IllegalStateException("Expecting spectrum to deconvolve.");
		
		
		double l = 0.5 * scan.getChopSeparation();
		double a = scan.getChopAngle(map.getReference());
		Vector2D delta = transformer.getResolution();

		final double wx = Constant.twoPi * delta.x() * l * Math.cos(a);
		final double wy = Constant.twoPi * delta.y() * l * Math.sin(a);

		final int Nx = transformer.sizeX() >> 1;

		transformer.new Task<Void>() {				
			@Override
			public void process(int i, int j) {				
				if((j & 1) != 0) return;
				
				// in j, index 1 is the nyquist frequency...
				int fj = j>>1; 
				if(j == 1) fj = transformer.sizeY() >> 1;

				// i has positive and negative frequencies...
				int fi = i;
				if(i > Nx) fi = i - (Nx<<1);

				double T = 0.5 * Math.sin(fi * wx + fj * wy);
				
				if(Math.abs(T) < 0.1) {
					transformer.clear(i, j);
					transformer.clear(i, j+1);
				}
				else {
					transformer.scale(i, j, T);
					transformer.scale(i, j+1, T);

					// multiply by i...
					double temp = transformer.getValue(i,  j);
					transformer.setValue(i,  j, -transformer.getValue(i, j+1));
					transformer.setValue(i,  j+1, temp);
				}			
			}
		}.process();
	}
	
	public void normalizeTransformer() {
		//if(!isSpectrum) throw new IllegalStateException("Expecting spectrum to normalize.");

		// The transformer now contains the weighted sum of the deconvolved spectrum.	
		// w = 1/T^2 --> sum T^2 = sum 1/w
		double sumiw = 0.0;
		int n = 0;
		for(int i=transformer.sizeX(); --i >= 0; ) for(int j=transformer.sizeY(); --j >= 0; ) {
			double w = transformer.getWeight(i, j);
			if(w == 0.0) continue;
			sumiw += 1.0 / w;
			n++;			
		}

		transformer.scale(sumiw / n);
	}
	

	private MultiBeamMap getDualBeam(DualBeam scan) {
		
		final MultiBeamMap dual = (MultiBeamMap) copy(true);	
		
		final double l = 0.5 * scan.getChopSeparation();
		final double a = getPositionAngle(scan);
		final Vector2D delta = map.getResolution();
		
		final double di = l * Math.cos(a) / delta.x();
		final double dj = l * Math.sin(a) / delta.y();
		
		// calculate the dual-beam image;
		dual.map.new Task<Void>() {		
			@Override
			public void process(int i, int j) {
				if(dual.map.isUnflagged(i, j)) {
					
					double l = map.valueAtIndex(i + di, j + dj);
					double r = map.valueAtIndex(i - di, j - dj);
					
					if(Double.isNaN(l)) l = 0.0;
					if(Double.isNaN(r)) r = 0.0;
					
					dual.map.setValue(i,  j, l - r);
				}
			}
		}.process();

		dual.map.sanitize();
		
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
		
		dual.syncSuper(integration);
	}
	
	private void syncSuper(Integration<?, ?> integration) {
		super.sync(integration);
	}
		
	@Override
	public void reset(boolean clearContent) {
		super.reset(clearContent);
		if(clearContent) transformer.clear();
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
