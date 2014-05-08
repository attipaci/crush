package crush.sourcemodel;

import java.util.Collection;

import kovacs.data.CartesianGrid2D;
import kovacs.data.GridMap;
import kovacs.data.Index2D;
import kovacs.fft.MultiFFT;
import kovacs.math.Coordinate2D;
import kovacs.math.Vector2D;
import kovacs.util.Configurator;
import kovacs.util.Constant;
import kovacs.util.ExtraMath;
import kovacs.util.Unit;
import crush.Channel;
import crush.DualBeam;
import crush.Instrument;
import crush.Integration;
import crush.Pixel;
import crush.Scan;
import crush.SourceModel;


public class DualBeamMap extends ScalarMap {
	ScalarMap deconvolved;
	GridMap<Coordinate2D> transformer;
	
	boolean isSpectrum = false;

	MultiFFT fft = new MultiFFT();

	private Vector2D currentThrow;
		
	public DualBeamMap(Instrument<?> instrument) {
		super(instrument);
		id = "dualbeam";
		deconvolved = new ScalarMap(instrument);
		deconvolved.id = "deconvolved";
	}

	@SuppressWarnings("unchecked")
	@Override
	public SourceModel copy(boolean withContents) {
		DualBeamMap copy = (DualBeamMap) super.copy(withContents);

		if(deconvolved != null) copy.deconvolved = (ScalarMap) deconvolved.copy(); 
		if(transformer != null) copy.transformer = (GridMap<Coordinate2D>) transformer.copy(withContents);

		currentThrow = null;
		
		return copy;
	}
	
	
	@Override
	public void reset(boolean clearContent) {
		super.reset(clearContent);
		if(deconvolved != null) deconvolved.reset(clearContent);
		if(clearContent) if(transformer != null) transformer.clear();
	}

	@Override
	public void standalone() {
		super.standalone();
		deconvolved.standalone();
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		double maxThrow = 0.0;

		for(Scan<?,?> scan : collection) {
			DualBeam dual = (DualBeam) scan;
			maxThrow = Math.max(maxThrow, dual.getChopSeparation());
		}

		super.createFrom(collection);
		deconvolved.createFrom(collection);
		
		Vector2D delta = map.getResolution();

		int nx = ExtraMath.pow2ceil((int) Math.ceil(map.sizeX() + maxThrow / delta.x()));
		int ny = ExtraMath.pow2ceil((int) Math.ceil(map.sizeY() + maxThrow / delta.y()));

		// TODO make spectrum large enough to avoid edge effects
		transformer = new GridMap<Coordinate2D>(nx, ny);
		transformer.setGrid(new CartesianGrid2D());
		transformer.setReference(new Coordinate2D());
		transformer.getGrid().setResolution(1.0 / (nx * delta.x()), 1.0 / (ny * delta.y()));
	}
	
	@Override
	public void setOptions(Configurator options) {
		super.setOptions(options);
		deconvolved.setOptions(options);
	}
	
	@Override
	public void setBase() {
		super.setBase();
		deconvolved.setBase();
	}

	@Override
	public synchronized void add(SourceModel model, double weight) {
		super.add(model, weight);
		
		DualBeamMap dual = (DualBeamMap) model;
		deconvolved.add(dual.deconvolved, weight);
		
		if(!dual.isSpectrum) throw new IllegalStateException("Expecting spectrum to accumulate.");
		
		transformer.addDirect(dual.transformer, weight);
		
		isSpectrum = true;
	}
	
	
	@Override
	public synchronized void process(Scan<?,?> scan) {
		deconvolve((DualBeam) scan);
	
		super.process(scan);
		deconvolved.process(scan);
	}
	
	@Override
	public synchronized void postprocess(Scan<?,?> scan) {
		//super.postprocess(scan);
		if(deconvolved != null) deconvolved.postprocess(scan);
	}

	@Override
	public synchronized void process(boolean verbose) {
		super.process(verbose);
		
		if(!isSpectrum) throw new IllegalStateException("Expecting spectrum to normalize.");

		// The transformer now contains the weighted sum of the deconvolved spectrum.
		
		/*
		if(hasOption("multi")) {
			// normalize the deconvolved image...
			transformer.new Task<Void>() {
				@Override
				public void process(int i, int j) {
					if(transformer.isUnflagged(i, j)) transformer.scaleValue(i, j, 1.0 / transformer.getWeight(i, j));
				}
			}.process();
		}
		*/
	
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

		fft.amplitude2Real(transformer.getData());
		
		// undo weight multiplication...
		deconvolved.map.new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(map.getWeight(i, j) > 0.0) deconvolved.map.setValue(i, j, transformer.getValue(i,  j) / map.getWeight(i , j));
				else deconvolved.map.setValue(i,  j, 0.0);
				deconvolved.map.setFlag(i, j, map.getFlag(i, j));
				deconvolved.map.setWeight(i, j, map.getWeight(i, j));
				deconvolved.map.setTime(i, j, map.getTime(i, j));
			}
		}.process();
		
		deconvolved.process(false);
		
		isSpectrum = false;
	}

	private void deconvolve(DualBeam scan) {
		transformer.clear();

		// calculate the dual-beam image;
		map.new Task<Void>() {
			@Override
			public void process(int i, int j) {	
				transformer.setValue(i, j, map.getValue(i,  j) * map.getWeight(i, j));	
			}
		}.process();

	
		transformer.unflag();
		transformer.setWeight(1.0); // All frequencies have equal weight before deconvolution
		
		fft.real2Amplitude(transformer.getData());
		isSpectrum = true;


		double l = 0.5 * scan.getChopSeparation();
		double a = scan.getChopAngle(map.getReference());
		Vector2D delta = transformer.getResolution();
		
		System.err.println("# " + (l/Unit.arcsec) + ", " + (a/Unit.deg));

		final double wx = Constant.twoPi * delta.x() * l * Math.cos(a);
		final double wy = Constant.twoPi * delta.y() * l * Math.sin(a);

		final int Nx = transformer.sizeX() >> 1;

		System.err.println("# " + wx + ", " + wy);

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

				double T = Math.sin(fi * wx + fj * wy);
				
				if(Math.abs(T) < 0.01) {
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
	
	private ScalarMap getDualBeam(DualBeam scan) {
		final ScalarMap dual = (ScalarMap) deconvolved.copy(true);
		
		double l = 0.5 * scan.getChopSeparation();
		double a = scan.getChopAngle(map.getReference());
		Vector2D delta = map.getResolution();

		final double di = l * Math.cos(a) / delta.x();
		final double dj = l * Math.sin(a) / delta.y();

		// calculate the dual-beam image;
		dual.map.new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(map.isUnflagged(i, j)) {
					double l = deconvolved.map.valueAtIndex(i + di, j + dj);
					double r = deconvolved.map.valueAtIndex(i - di, j - dj);
					if(Double.isNaN(l)) l = 0.0;
					if(Double.isNaN(r)) r = 0.0;
					dual.map.setValue(i,  j, l - r);
				}
			}
		}.process();

		return dual;
	}
	
	private boolean[][] getDualBeamMask(DualBeam scan) {
		boolean[][] dm = new boolean[map.sizeX()][map.sizeY()];
		
		double l = 0.5 * scan.getChopSeparation();
		double a = scan.getChopAngle(map.getReference());
		Vector2D delta = map.getResolution();

		final int di = (int)Math.round(l * Math.cos(a) / delta.x());
		final int dj = (int)Math.round(l * Math.sin(a) / delta.y());

		boolean[][] m = deconvolved.getMask();
		
		for(int i=map.sizeX(); --i >=0; ) for(int j=map.sizeY(); --j >=0; ) {	
			int i1 = i + di;
			if(i1 >= 0 && i1 < map.sizeX()) { 
				int j1 = j + dj;
				if(j1 >= 0 && j1 < map.sizeY()) dm[i][j] |= m[i1][j1]; 
			}
		
			i1 = i - di;
			if(i1 >= 0 && i1 < map.sizeX()) { 
				int j1 = j - dj;
				if(j1 >= 0 && j1 < map.sizeY()) dm[i][j] |= m[i1][j1]; 
			}
		}
			
		return dm;
	}
	

	@Override
	protected double getIncrement(final Index2D index, final Channel channel, final double oldG, final double G) {
		double i = index.i() + currentThrow.x();
		double j = index.j() + currentThrow.y();
		
		double l = G * deconvolved.map.valueAtIndex(i, j) - oldG * deconvolved.base.valueAtIndex(i, j);	
		if(Double.isNaN(l)) l = 0.0;
		
		i = index.i() - currentThrow.x();
		j = index.j() - currentThrow.y();
		
		double r = G * deconvolved.map.valueAtIndex(i, j) - oldG * deconvolved.base.valueAtIndex(i, j);	
		if(Double.isNaN(r)) r = 0.0;
		
		return l - r;
	}
	

	@Override
	public void calcCoupling(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, final double[] syncGain) {
		getDualBeam((DualBeam) integration.scan).calcCoupling(integration, pixels, sourceGain, syncGain);
	}

	private void setCurrentThrow(DualBeam scan) {
		double l = 0.5 * scan.getChopSeparation();
		double a = scan.getChopAngle(map.getReference());
		currentThrow = new Vector2D(l * Math.cos(a), l * Math.sin(a));
	}
	
	@Override
	public void sync(Integration<?,?> integration) {
		setCurrentThrow((DualBeam) integration.scan);	
		setMask(getDualBeamMask((DualBeam) integration.scan));	
		super.sync(integration);
		currentThrow = null;
	}	
	
	@Override
	public void write(String path, boolean info) throws Exception {		
		super.write(path, info);
		deconvolved.write(path, info);
	}
}
