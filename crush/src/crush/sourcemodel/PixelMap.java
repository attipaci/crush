/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;

import crush.*;
import jnum.Unit;
import jnum.data.*;
import jnum.data.image.Index2D;
import jnum.data.image.Map2D;
import jnum.data.image.Observation2D;
import jnum.data.image.region.GaussianSource;
import jnum.projection.Projection2D;
import nom.tam.fits.FitsException;

public class PixelMap extends AbstractSource2D {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8380688477538977451L;
	
	IntensityMap[] pixelMap;
	private transient IntensityMap template;
	
	public PixelMap(Instrument<?> instrument) {
		super(instrument);
	}

	@Override
	protected boolean isAddingToMaster() { return true; }
	
	@Override
	public PixelMap copy(boolean withContents) {
		PixelMap copy = (PixelMap) super.copy(withContents);
		copy.pixelMap = new IntensityMap[pixelMap.length];
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null)
			copy.pixelMap[p] = pixelMap[p].copy(withContents);	
		return copy;
	}
	
	@Override
    public void clearProcessBrief() {
	    super.clearProcessBrief();
	    for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) pixelMap[p].clearProcessBrief();
	}

	@Override
	public void createFrom(Collection<? extends Scan<?>> collection) throws Exception {
		// Set all pixel positions to zero...
		for(Scan<?> scan : collection) for(Integration<?> integration : scan) {
		    final Instrument<?> instrument = integration.getInstrument();
			for(Pixel pixel : instrument.getMappingPixels(~instrument.getSourcelessChannelFlags())) {
				pixel.getPosition().zero();
				pixel.setIndependent(true);
			}
		}
			
		  
        template = new IntensityMap(getInstrument());
        template.createFrom(collection);
        
        pixelMap = new IntensityMap[getInstrument().maxPixels() + 1];
		
		super.createFrom(collection);
	
	}

    @Override
    public void resetProcessing() {
        super.resetProcessing();
        for(IntensityMap map : pixelMap) if(map != null) map.resetProcessing();
    }

    
	
	@Override
	public void clearContent() {
		for(IntensityMap map : pixelMap) if(map != null) map.clearContent();
	}

		
	@Override
	public void addModelData(SourceModel model, double weight) {
		if(!(model instanceof PixelMap)) throw new IllegalArgumentException("Cannot add " + model.getClass().getSimpleName() + " to " + getClass().getSimpleName());
		PixelMap other = (PixelMap) model;
		
		for(int p=0; p<pixelMap.length; p++) if(other.pixelMap[p] != null) {
			if(pixelMap[p] == null) pixelMap[p] = other.pixelMap[p].copy(false);
			pixelMap[p].add(other.pixelMap[p], weight);
		}
	}

	@Override
	protected int add(final Integration<?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {	
		return addForkPixels(integration, pixels, sourceGain, signalMode);
	}
	

	@Override
	protected void add(final Frame exposure, final Pixel pixel, final Index2D index, final double fGC, final double[] sourceGain) {		
		final int i = pixel.getFixedIndex();
		IntensityMap map = pixelMap[i];
		
		if(map == null) {	
			map = template.copy(false);
			map.setID(pixel.getID());
			pixelMap[i] = map;
		}
			
		map.add(exposure, pixel, index, fGC, sourceGain);
	}


	@Override
	protected void calcCoupling(Integration<?> integration, Collection<? extends Pixel> pixels, double[] sourceGain, double[] syncGain) {
	}

	@Override
	public int countPoints() {
		int points = 0;
		for(IntensityMap map : pixelMap) if(map != null) points += map.countPoints();
		return points;
	}

	@Override
	public double covariantPoints() {
		for(IntensityMap map : pixelMap) if(map != null) return map.covariantPoints();
		return 1.0;
	}

	@Override
	public int getPixelFootprint() {
	    return pixelMap.length * template.getPixelFootprint();
	}

	@Override
	public long baseFootprint(int pixels) {
		return pixelMap.length * template.baseFootprint(pixels);
	}

	@Override
	public void setSize(int sizeX, int sizeY) {
		for(IntensityMap map : pixelMap) if(map != null) map.setSize(sizeX, sizeY);
	}

	@Override
	protected void sync(final Frame exposure, final Pixel pixel, final Index2D index, final double fG, final double[] sourceGain, final double[] syncGain) {
		final IntensityMap map = pixelMap[pixel.getFixedIndex()];
		if(map != null) map.sync(exposure, pixel, index, fG, sourceGain, syncGain);	
	}


	@Override
	public void setBase() {
		for(IntensityMap map : pixelMap) if(map != null) map.setBase();
	}

	@Override
	public void process(Scan<?> scan) {
		for(IntensityMap map : pixelMap) if(map != null) map.process(scan);
	}


	@Override
	public void write() throws Exception {
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
			
			for(int p=from; p<to; p++) if(pixelMap[p] != null) if(pixelMap[p].isValid()) pixelMap[p].write();
		}
		calcPixelData(false);
		writePixelData(getOutputPath());	
	}


    @Override
    public void writeFits(String fileName) throws FitsException, IOException {}

	
	@Override
	public void process() throws Exception {	
		boolean process = hasSourceOption("process");	
		
		for(IntensityMap map : pixelMap) if(map != null) {	
			if(process) map.process();
			else {
				map.map.endAccumulation();
				nextGeneration(); // Increment the map generation...
			}
		}
	}
	

	public void calcPixelData(boolean smooth) {
		float[] peaks = new float[getInstrument().storeChannels];
		float[] pixelPeak = new float[pixelMap.length];
		
		int k = 0;
		
		for(Pixel pixel : getFirstScan().getInstrument().getMappingPixels(0)) {
			int i = pixel.getFixedIndex();
			IntensityMap beamMap = pixelMap[i];
			
			pixel.getPosition().set(Double.NaN, Double.NaN);
			
			if(beamMap != null) if(beamMap.isValid()) {
				Observation2D map = beamMap.map;
				if(smooth) map.smoothTo(getInstrument().getPointSize());
				GaussianSource source = beamMap.getPeakSource();
				
				if(source != null) {
					// Get the source peak in the pixel.
					pixelPeak[i] = (float) source.getPeak().value();
					peaks[k++] = pixelPeak[i];

					// Get the offset position if it makes sense, or set as NaN otherwise... 
					((Projection2D) getProjection()).project(source.getCoordinates(), pixel.getPosition());				

					// The pixel position is the opposite of its apparent offset.
					pixel.getPosition().invert();
				}
			}
		}
		if(k == 0) return;
		
		final double mean = Statistics.Inplace.median(peaks, 0, k);
		
		for(final Pixel pixel : getFirstScan().getInstrument().getMappingPixels(0)) {
			int i = pixel.getFixedIndex();
			IntensityMap map = pixelMap[i];
			if(map != null) {
				final double rel = pixelPeak[i] / mean;
				for(final Channel channel : pixel) channel.coupling *= rel;
			}
		}
	}
	
	
	public void writePixelData(String path) throws IOException {
		
		double[] sourceGain = getFirstScan().getInstrument().getSourceGains(getPointSize(), false);
		
		String fileName = path + File.separator + getDefaultCoreName() + ".rcp";
		try(PrintStream out = new PrintStream(new FileOutputStream(fileName))) {
		    Instrument<?> instrument = getFirstScan().getInstrument();
		    for(Channel channel : instrument) channel.coupling = sourceGain[channel.getIndex()] / channel.gain;

		    instrument.getLayout().printPixelRCP(out, getFirstScan().getFirstIntegration().getASCIIHeader());

		    out.flush();
		    out.close();
		}
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
		if(pixelMap != null) for(IntensityMap map : pixelMap) if(map != null) map.noParallel();
	}
	
	@Override
	public void setParallel(int threads) {
		if(pixelMap != null) for(IntensityMap map : pixelMap) if(map != null) map.setParallel(threads);
	}
	
	@Override
	public int getParallel() {
		if(pixelMap != null) for(IntensityMap map : pixelMap) if(map != null) return map.getParallel();
		return 1;
	}

	@Override
	public void mergeAccumulate(AbstractSource2D other) {
		if(!(other instanceof PixelMap)) 
			throw new IllegalStateException("Cannot add " + other.getClass().getSimpleName() + " to " + getClass().getSimpleName() + ".");
		
		PixelMap pixelmap = (PixelMap) other;
		
		for(int k=pixelMap.length; --k >= 0; ) {
			IntensityMap map = pixelMap[k];
			if(map == null) continue;
			IntensityMap otherMap = pixelmap.pixelMap[k];
			if(otherMap == null) continue;
			map.mergeAccumulate(otherMap);
		}
	}

	@Override
	public void setExecutor(ExecutorService executor) {
		if(pixelMap != null) for(IntensityMap map : pixelMap) if(map != null) map.setExecutor(executor);
	}

	@Override
	public ExecutorService getExecutor() {
		if(pixelMap != null) for(IntensityMap map : pixelMap) if(map != null) return map.getExecutor();
		return null;
	}

    @Override
    public int sizeX() {
        return template.sizeX();
    }

    @Override
    public int sizeY() {
        return template.sizeY();
    }

    @Override
    public boolean isEmpty() {
        if(pixelMap == null) return true;
        for(IntensityMap map : pixelMap) if(map != null) if(!map.isEmpty()) return false;
        return true;
    }

    @Override
    protected void addPoint(Index2D index, Channel channel, Frame exposure, double G, double dt) {
        // TODO not needed...
    }

    @Override
    public void processFinal() {
        if(pixelMap == null) return;
        for(IntensityMap map : pixelMap) if(map != null) map.processFinal();
    }
    
    @Override
    public Map2D getMap2D() { return null; }


}
