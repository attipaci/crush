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
package crush.apex;

import java.util.ArrayList;

import kovacs.data.DataPoint;
import kovacs.data.WeightedPoint;
import crush.*;
import crush.sourcemodel.Photometry;

public class APEXChoppedPhotometry extends Photometry {

	public APEXChoppedPhotometry(APEXCamera<?> instrument) {
		super(instrument);
	}
	
	public APEXCamera<?> getAPEXArray() { return (APEXCamera<?>) getInstrument(); }


	@Override
	public synchronized void process(Scan<?, ?> scan) {		
		final WeightedPoint[] left = new WeightedPoint[flux.length];
		final WeightedPoint[] right = new WeightedPoint[flux.length];
		
		for(int c=flux.length; --c >= 0; ) {
			left[c] = new WeightedPoint();
			right[c] = new WeightedPoint();			
		}
		
		for(Integration<?,?> integration : scan) process((APEXArraySubscan<?,?>) integration, left, right);
		
		sourceFlux.noData();	
		
		for(int c=flux.length; --c >=0; ) {
			flux[c] = (WeightedPoint) left[c].clone();
			flux[c].subtract(right[c]);
			flux[c].scale(0.5);
			if(flux[c].weight() > 0.0) sourceFlux.average(flux[c]);
		}
	
		DataPoint F = new DataPoint(sourceFlux);
		F.scale(1.0 / getInstrument().janskyPerBeam());
		scanFluxes.put(scan, F);
		
		scan.getLastIntegration().comments += " " + (F.weight() > 0.0 ? F.toString() : "<<invalid>>");
	}
	
	protected void process(final APEXArraySubscan<?,?> subscan, final WeightedPoint[] left, final WeightedPoint[] right) {		
		// Proceed only if there are enough pixels to do the job...
		if(!checkPixelCount(subscan)) return;		


		final double transmission = 0.5 * (subscan.getFirstFrame().getTransmission() + subscan.getLastFrame().getTransmission());
		final double[] sourceGain = subscan.instrument.getSourceGains(false);
		final PhaseSet phases = subscan.getPhases();

		final double radius = hasOption("neighbours.radius") ? option("neighbours.radius").getDouble() : 0.0;	

		subscan.instrument.getObservingChannels().new Fork<Void>() {
			@Override
			protected void process(APEXPixel channel) {
				APEXPixel pixel = (APEXPixel) channel;
				
				WeightedPoint point = null;

				ArrayList<APEXPixel> neighbours = subscan.instrument.getNeighbours(pixel, radius * pixel.getResolution());

				if((pixel.sourcePhase & Frame.CHOP_LEFT) != 0) point = left[pixel.getFixedIndex()];
				else if((pixel.sourcePhase & Frame.CHOP_RIGHT) != 0) point = right[pixel.getFixedIndex()];
				else return;

				/*
					try { pixel.writeLROffset(phases, 
							CRUSH.workPath + File.separator + "phases-" + integration.getFullID("-") + "-P" + pixel.getFixedIndex() + ".dat",
							neighbours, sourceGain); }
					catch(IOException e) { e.printStackTrace(); }

					try { pixel.writeLRSpectrum(phases, 
							CRUSH.workPath + File.separator + "phases-" + integration.getFullID("-") + "-P" + pixel.getFixedIndex() + ".spec"); }
					catch(IOException e) { e.printStackTrace(); }
				 */

				WeightedPoint df = pixel.getCorrectedLROffset(phases, neighbours, sourceGain);	
				double chi2 = pixel.getCorrectedLRChi2(phases, neighbours, df.value(), sourceGain);

				if(!Double.isNaN(chi2)) {
					df.scaleWeight(Math.min(1.0, 1.0 / chi2));
					df.scale(1.0 / (transmission * subscan.gain * sourceGain[pixel.index]));
					point.average(df);
				}
			}
			
		}.process();
	}


	@Override
	public void write(String path) throws Exception {	
		System.out.println("  Note, that the results of the APEX chopped photometry reduction below include");
		System.out.println("  an estimate of the systematic errors, based on the true scatter of the");
		System.out.println("  chopped photometry measurements in each nod cycle. As such, these errors are");
		System.out.println("  higher than expected from the nominal NEFD values alone, and reflect the");
		System.out.println("  photometric uncertainty more accurately.");
		System.out.println();
		
		if(scans.size() > 1) {
			System.out.println("  Scan-to-scan scatter is measured by the reduced chi value. When |rChi| > 1,"); 
			System.out.println("  you can multiply the quoted uncertainty by it to arrive at a more robust");
			System.out.println("  estimate of the total measurement uncertainty.");
			System.out.println();
		}
		
		
		super.write(path);
		
		
		
	}

	
	
}
