/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;

import crush.*;
import crush.array.*;
import crush.astro.AstroMap;
import jnum.Unit;
import jnum.data.*;
import jnum.math.SphericalCoordinates;
import jnum.projection.Projection2D;

public class BeamMap extends SourceMap {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8380688477538977451L;
	
	ScalarMap[] pixelMap;
	private ScalarMap template;
	
	public BeamMap(Camera<?, ?> instrument) {
		super(instrument);
	}

	@Override
	protected boolean isAddingToMaster() { return true; }
	
	@Override
	public SourceModel getWorkingCopy(boolean withContents) {
		BeamMap copy = (BeamMap) super.getWorkingCopy(withContents);
		copy.pixelMap = new ScalarMap[pixelMap.length];
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null)
			copy.pixelMap[p] = (ScalarMap) pixelMap[p].getWorkingCopy(withContents);	
		return copy;
	}

	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		// Set all pixel positions to zero...
		for(Scan<?,?> scan : collection) for(Integration<?,?> integration : scan) {
		    final Instrument<?> instrument = integration.instrument;
			for(Pixel pixel : instrument.getMappingPixels(~instrument.sourcelessChannelFlags())) {
				pixel.getPosition().zero();
				pixel.setIndependent(true);
			}
		}
			
		super.createFrom(collection);
		
		template = new ScalarMap(getInstrument());
		template.createFrom(collection);
		
		pixelMap = new ScalarMap[getArray().maxPixels() + 1];
	}
	
	public Camera<?, ?> getArray() { return (Camera<?, ?>) getInstrument(); }
	
	@Override
	public void reset(boolean clearContent) {
		super.reset(clearContent);
		for(ScalarMap map : pixelMap) if(map != null) map.reset(clearContent);
	}

	
		
	@Override
	public void add(SourceModel model, double weight) {
		if(!(model instanceof BeamMap)) throw new IllegalArgumentException("Cannot add " + model.getClass().getSimpleName() + " to " + getClass().getSimpleName());
		BeamMap other = (BeamMap) model;
		
		for(int p=0; p<pixelMap.length; p++) if(other.pixelMap[p] != null) {
			if(pixelMap[p] == null) pixelMap[p] = (ScalarMap) other.pixelMap[p].getWorkingCopy(false);
			pixelMap[p].add(other.pixelMap[p], weight);
		}
		
		generation = Math.max(generation, other.generation);
	}

	@Override
	protected int add(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final double filtering, final int signalMode) {	
		return addForkPixels(integration, pixels, sourceGain, filtering, signalMode);
	}
	

	@Override
	protected void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain) {		
		final int i = pixel.getFixedIndex();
		ScalarMap map = pixelMap[i];
		
		if(map == null) {	
			map = (ScalarMap) template.getWorkingCopy(false);
			map.id = Integer.toString(i);
			map.standalone();
			pixelMap[i] = map;
		}
			
		map.add(exposure, pixel, index, fGC, sourceGain);
	}


	@Override
	protected void calcCoupling(Integration<?, ?> integration, Collection<? extends Pixel> pixels, double[] sourceGain, double[] syncGain) {
	}

	@Override
	public int countPoints() {
		int points = 0;
		for(ScalarMap map : pixelMap) if(map != null) points += map.countPoints();
		return points;
	}

	@Override
	public double covariantPoints() {
		for(ScalarMap map : pixelMap) if(map != null) return map.covariantPoints();
		return 1.0;
	}

	@Override
	public double getPixelFootprint() {
		return pixelMap.length * template.getPixelFootprint();
	}

	@Override
	public long baseFootprint(long pixels) {
		return pixelMap.length * template.baseFootprint(pixels);
	}
	

	@Override
	public Projection2D<SphericalCoordinates> getProjection() {
		return template.getProjection();
	}

	@Override
	public void setProjection(Projection2D<SphericalCoordinates> projection) {
		if(template != null) template.setProjection(projection);
	}

	@Override
	public boolean isMasked(Index2D index) {
		return false;
	}

	@Override
	public void setSize(int sizeX, int sizeY) {
		for(ScalarMap map : pixelMap) if(map != null) map.setSize(sizeX, sizeY);
	}

	@Override
	protected void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, final double[] syncGain, final boolean isMasked) {
		final ScalarMap map = pixelMap[pixel.getFixedIndex()];
		if(map != null) map.sync(exposure, pixel, index, fG, sourceGain, syncGain, isMasked);	
	}


	@Override
	public void setBase() {
		for(ScalarMap map : pixelMap) if(map != null) map.setBase();
	}

	@Override
	public void process(Scan<?, ?> scan) {
		for(ScalarMap map : pixelMap) if(map != null) map.process(scan);
	}


	@Override
	public void write(String path) throws Exception {
		if(hasSourceOption("writemaps")) {
			int from = 0;
			int to = pixelMap.length;
			
			String spec = sourceOption("writemaps").getValue();
			
			if(spec.length() > 0) {
				StringTokenizer tokens = new StringTokenizer(spec, ":");
				from = Integer.parseInt(tokens.nextToken());
				if(tokens.hasMoreTokens()) to = Math.min(pixelMap.length, 1 + Integer.parseInt(tokens.nextToken()));
				else to = from+1;
			}
			
			for(int p=from; p<to; p++) if(pixelMap[p] != null) if(pixelMap[p].isValid()) pixelMap[p].write(path, false);
		}
		calcPixelData(false);
		writePixelData();	
	}

	@Override
	public void process(boolean verbose) throws Exception {	
		boolean process = hasSourceOption("process");	
		
		for(ScalarMap map : pixelMap) if(map != null) {	
			if(process) map.process(verbose);
			else {
				map.map.normalize();
				map.map.generation++; // Increment the map generation...
			}
			verbose = false;
		}
	}
	
	// TODO for non AstroMap and non spherical coordinates...
	public void calcPixelData(boolean smooth) {
		float[] peaks = new float[getInstrument().storeChannels];
		float[] pixelPeak = new float[pixelMap.length];
		
		int k = 0;
		
		for(Pixel pixel : scans.get(0).instrument.getMappingPixels(0)) {
			int i = pixel.getFixedIndex();
			ScalarMap beamMap = pixelMap[i];
			
			pixel.getPosition().set(Double.NaN, Double.NaN);
			
			if(beamMap != null) if(beamMap.isValid()) {
				AstroMap map = beamMap.map;
				if(smooth) map.smoothTo(getInstrument().getPointSize());
				GaussianSource<SphericalCoordinates> source = beamMap.getPeakSource();
				
				if(source != null) {
					// Get the source peak in the pixel.
					pixelPeak[i] = (float) source.getPeak().value();
					peaks[k++] = pixelPeak[i];

					// Get the offset position if it makes sense, or set as NaN otherwise... 
					map.getProjection().project(source.getCoordinates(), pixel.getPosition());				

					// The pixel position is the opposite of its apparent offset.
					pixel.getPosition().invert();
				}
			}
		}
		if(k == 0) return;
		
		final double mean = Statistics.median(peaks, 0, k);
		
		for(final Pixel pixel : scans.get(0).instrument.getMappingPixels(0)) {
			int i = pixel.getFixedIndex();
			ScalarMap map = pixelMap[i];
			if(map != null) {
				final double rel = pixelPeak[i] / mean;
				for(final Channel channel : pixel) channel.coupling *= rel;
			}
		}
	}
	
	
	public void writePixelData() throws IOException {
		Scan<?,?> scan = scans.get(0);
		double[] sourceGain = scan.instrument.getSourceGains(false);
		
		String fileName = CRUSH.workPath + File.separator + getDefaultCoreName() + ".rcp";
		PrintStream out = new PrintStream(new FileOutputStream(fileName));
		
		Camera<?,?> array = (Camera<?,?>) scans.get(0).instrument;
		for(Channel channel : array) channel.coupling = sourceGain[channel.index] / channel.gain;
	
		array.printPixelRCP(out, scan.getFirstIntegration().getASCIIHeader());
		
		out.flush();
		out.close();
		CRUSH.notify(this, "Written " + fileName);
	}


	@Override
	public String getSourceName() {
		return template.getSourceName();
	}

	@Override
	public Unit getUnit() {
		return template.getUnit();
	}

	@Override
	public void noParallel() {
		if(pixelMap != null) for(ScalarMap map : pixelMap) if(map != null) map.noParallel();
	}
	
	@Override
	public void setParallel(int threads) {
		if(pixelMap != null) for(ScalarMap map : pixelMap) if(map != null) map.setParallel(threads);
	}
	
	@Override
	public int getParallel() {
		if(pixelMap != null) for(ScalarMap map : pixelMap) if(map != null) return map.getParallel();
		return 1;
	}

	@Override
	public boolean isValid() {
		if(pixelMap == null) return false;
		// Require at least one valid pixel map.
		for(ScalarMap map : pixelMap) if(map != null) if(map.isValid()) return true;
		return false;
	}

	@Override
	public void addNonZero(SourceMap other) {
		if(!(other instanceof BeamMap)) 
			throw new IllegalStateException("Cannot add " + other.getClass().getSimpleName() + " to " + getClass().getSimpleName() + ".");
		
		BeamMap beammap = (BeamMap) other;
		
		for(int k=pixelMap.length; --k >= 0; ) {
			ScalarMap map = pixelMap[k];
			if(map == null) continue;
			ScalarMap otherMap = beammap.pixelMap[k];
			if(otherMap == null) continue;
			map.addNonZero(otherMap);
		}
	}

	@Override
	public void setExecutor(ExecutorService executor) {
		if(pixelMap != null) for(ScalarMap map : pixelMap) if(map != null) map.setExecutor(executor);
	}

	@Override
	public ExecutorService getExecutor() {
		if(pixelMap != null) for(ScalarMap map : pixelMap) if(map != null) return map.getExecutor();
		return null;
	}

	@Override
	public SphericalCoordinates getReference() {
		return template.getReference();
	}

    @Override
    public Grid2D<?> getGrid() {
        return template.getGrid();
    }

    @Override
    public int sizeX() {
        return template.sizeX();
    }

    @Override
    public int sizeY() {
        return template.sizeY();
    }

	

}
