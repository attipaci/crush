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
package crush.sourcemodel;

import java.io.*;
import java.util.Collection;

import util.*;
import util.astro.CelestialProjector;
import util.data.Index2D;
import util.data.Statistics;
import crush.*;
import crush.array.*;
import crush.astro.AstroMap;

public class BeamMap<InstrumentType extends Array<?,?>, ScanType extends Scan<? extends InstrumentType, ?>>
		extends SourceMap<InstrumentType, ScanType> {
	
	ScalarMap<InstrumentType, ScanType>[] pixelMap;
	ScalarMap<InstrumentType, ScanType> template;
	
	public BeamMap(InstrumentType instrument) {
		super(instrument);
	}

	@SuppressWarnings("unchecked")
	@Override
	public SourceModel<InstrumentType, ScanType> copy() {
		BeamMap<InstrumentType, ScanType> copy = (BeamMap<InstrumentType, ScanType>) super.copy();
		copy.pixelMap = new ScalarMap[pixelMap.length];
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) copy.pixelMap[p] = (ScalarMap<InstrumentType, ScanType>) pixelMap[p].copy();
		return copy;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		// Set all pixel positions to zero...
		for(Scan<?,?> scan : collection) for(Integration<?,?> integration : scan) 
			for(Pixel pixel : integration.instrument.getMappingPixels()) {
				pixel.getPosition().zero();
				pixel.setIndependent(true);
			}
		
		super.createFrom(collection);
		
		template = new ScalarMap<InstrumentType, ScanType>(instrument);
		template.setOptions(getOptions());
		template.createFrom(collection);
		
		pixelMap = new ScalarMap[instrument.maxPixels() + 1];
	}
	
	@Override
	public void reset() {
		super.reset();
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) pixelMap[p].reset();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void add(SourceModel<?, ?> model, double weight) {
		if(!(model instanceof BeamMap<?, ?>)) throw new IllegalArgumentException("ERROR! Cannot add " + model.getClass().getSimpleName() + " to " + getClass().getSimpleName());
		BeamMap<InstrumentType, ScanType> other = (BeamMap<InstrumentType, ScanType>) model;
		
		for(int p=0; p<pixelMap.length; p++) if(other.pixelMap[p] != null) {
			if(pixelMap[p] == null) {
				pixelMap[p] = (ScalarMap<InstrumentType, ScanType>) other.pixelMap[p].copy();
				pixelMap[p].map.scaleWeight(weight);
			}
			else pixelMap[p].add(other.pixelMap[p], weight);
		}
	}


	@Override
	public long baseFootprint() {
		return pixelMap.length * template.baseFootprint();
	}
	
	
	@Override
	protected void add(Frame exposure, Pixel pixel, Index2D index, double fGC, double[] sourceGain, double dt, int excludeSamples) {
		int i = pixel.getDataIndex();
		ScalarMap<InstrumentType, ScanType> map = pixelMap[i];
		
		if(map == null) {
			map = (ScalarMap<InstrumentType, ScanType>) template.copy();
			map.id = Integer.toString(i);
			map.standalone();
			pixelMap[i] = map;
		}
			
		pixelMap[i].add(exposure, pixel, index, fGC, sourceGain, dt, excludeSamples);
	}


	@Override
	protected void calcCoupling(Integration<?, ?> integration, Collection<? extends Pixel> pixels, double[] sourceGain, double[] syncGain) {
		// TODO
	}


	@Override
	public int countPoints() {
		int points = 0;
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) points += pixelMap[p].countPoints();
		return points;
	}

	

	@Override
	public double covariantPoints() {
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) return pixelMap[p].covariantPoints();
		return 1.0;
	}


	@Override
	public void getIndex(Frame exposure, Pixel pixel, CelestialProjector projector, Index2D index) {
		template.getIndex(exposure, pixel, projector, index);
	}


	@Override
	public double getPixelFootprint() {
		return pixelMap.length * template.getPixelFootprint();
	}


	@Override
	public Projection2D<SphericalCoordinates> getProjection() {
		return template.getProjection();
	}


	@Override
	public boolean isMasked(Index2D index) {
		return false;
	}


	@Override
	public long pixels() {
		int pixels = 0;
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) pixels += pixelMap[p].pixels();
		return pixels;
	}


	@Override
	public double resolution() {
		return template.resolution();
	}


	@Override
	public void setSize(int sizeX, int sizeY) {
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) pixelMap[p].setSize(sizeX, sizeY);
	}


	@Override
	protected void sync(Frame exposure, Pixel pixel, Index2D index, double fG, double[] sourceGain, double[] syncGain, boolean isMasked) {
		ScalarMap<InstrumentType, ScanType> map = pixelMap[pixel.getDataIndex()];
		if(map != null) map.sync(exposure, pixel, index, fG, sourceGain, syncGain, isMasked);	
	}


	@Override
	public void setBase() {
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) pixelMap[p].setBase();
	}


	@Override
	public void process(Scan<?, ?> scan) {
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) pixelMap[p].process(scan);
	}


	@Override
	public void write(String path) throws Exception {
		if(hasOption("beammap.writemaps")) {
			for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) pixelMap[p].write(path, false);
		}
		calcPixelData(false);
		writePixelData();	
	}

	@Override
	public synchronized void sync() throws InterruptedException {	
		boolean verbose = true;
		for(int p=0; p<pixelMap.length; p++) if(pixelMap[p] != null) {
			if(hasOption("beammap.process")) pixelMap[p].process(verbose);
			verbose = false;
		}
		super.sync();		
	}
	
	// TODO for non AstroMap and non spherical coordinates...
	public void calcPixelData(boolean smooth) {
		float[] peaks = new float[instrument.storeChannels];
		float[] pixelPeak = new float[pixelMap.length];
		
		// Calculate mean rotation angle from sinA and cosA
		// First check if angles are close to +-180 to see if need to branch at zero
		double A0 = scans.get(0).getFirstIntegration().getFirstFrame().getRotation();
		boolean zeroBranch = Math.abs(Math.PI - Math.abs(A0)) < Math.PI/6.0;
		
		double sumA = 0.0;
		int n = 0;
		for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) for(Frame exposure : integration) if(exposure != null) {
			double A = exposure.getRotation();
			if(zeroBranch) {
				A += Math.PI;
				A = Math.IEEEremainder(A, 2.0 * Math.PI);
			}
			sumA += A;
			n++;
		}
		double rotation = n > 0 ? sumA /= n : 0.0;
		if(zeroBranch) rotation -= Math.PI;
		
		int k = 0;
		
		for(Pixel pixel : scans.get(0).instrument.getMappingPixels()) {
			int i = pixel.getDataIndex();
			ScalarMap<InstrumentType, ScanType> beamMap = pixelMap[i];
			if(beamMap != null) {
				AstroMap map = beamMap.map;
				if(smooth) map.smoothTo(instrument.resolution);
				GaussianSource<SphericalCoordinates> source = beamMap.getPeakSource();
				
				// Get the source peak in the pixel.
				pixelPeak[i] = (float) source.peak.value;
				peaks[k++] = pixelPeak[i];
				
				// Get the offset position if it makes sense, or set as NaN otherwise...
				pixel.getPosition().set(Double.NaN, Double.NaN);  
				map.getProjection().project(source.coords, pixel.getPosition());				
				
				// Derotate to array coordinates...
				pixel.getPosition().rotate(-rotation);
				pixel.getPosition().invert();
			}
		}
		if(k == 0) return;
		
		double mean = Statistics.median(peaks, 0, k);
		
		for(Pixel pixel : scans.get(0).instrument.getMappingPixels()) {
			int i = pixel.getDataIndex();
			ScalarMap<InstrumentType, ScanType> map = pixelMap[i];
			if(map != null) {
				final double rel = pixelPeak[i] / mean;
				for(Channel channel : pixel) channel.coupling *= rel;
			}
		}
	}
	
	
	public void writePixelData() throws IOException {
		Scan<?,?> scan = scans.get(0);
		double[] sourceGain = scan.instrument.getSourceGains(false);
		
		String fileName = CRUSH.workPath + File.separator + getDefaultCoreName() + ".rcp";
		PrintStream out = new PrintStream(new FileOutputStream(fileName));
		
		out.println("# CRUSH Receiver Channel Parameter (RCP) Data File");
		out.println("#");
		out.println(scan.getFirstIntegration().getASCIIHeader());
		out.println("#");
		out.println("# ch\tGpnt\tGsky\tdX(\")\tdY(\")");
		
		for(Pixel pixel : scans.get(0).instrument.getMappingPixels()) {
			Vector2D position = pixel.getPosition();
			String positionString = Util.f1.format(position.x / Unit.arcsec) + "\t" + Util.f1.format(position.y / Unit.arcsec);
			
			if(pixelMap[pixel.getDataIndex()] != null) for(Channel channel : pixel) {			
				out.println(channel.storeIndex + "\t" + 
						Util.f3.format(sourceGain[channel.index]) + "\t" +
						Util.f3.format(channel.gain) + "\t" +
						positionString
				);
			}
		}
		out.flush();
		out.close();
		System.err.println("Written " + fileName);
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
		for(ScalarMap<?,?> map : pixelMap) map.noParallel();
	}	
}
