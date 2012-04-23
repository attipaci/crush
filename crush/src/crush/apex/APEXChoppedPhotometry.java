/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import util.data.WeightedPoint;
import crush.*;
import crush.sourcemodel.Photometry;

public class APEXChoppedPhotometry extends Photometry {
	
	public APEXChoppedPhotometry(APEXArray<?> instrument) {
		super(instrument);
	}
	
	public APEXArray<?> getAPEXArray() { return (APEXArray<?>) instrument; }


	@Override
	public synchronized void process(Scan<?, ?> scan) {		
		final WeightedPoint[] left = new WeightedPoint[flux.length];
		final WeightedPoint[] right = new WeightedPoint[flux.length];
		
		for(int c=flux.length; --c >= 0; ) {
			left[c] = new WeightedPoint();
			right[c] = new WeightedPoint();			
		}
	
		for(Integration<?,?> integration : scan) {
			final APEXArraySubscan<?,?> subscan = (APEXArraySubscan<?,?>) integration;
			final double transmission = 0.5 * (subscan.getFirstFrame().transmission + subscan.getLastFrame().transmission);
			final double[] sourceGain = subscan.instrument.getSourceGains(false);
			final PhaseSet phases = integration.getPhases();
		
			// To be sure one could update the phases one last time...
			//integration.updatePhases();
			
			// Proceed only if there are enough pixels to do the job...
			if(!checkPixelCount(integration)) continue;
			
			for(APEXPixel pixel : subscan.instrument.getObservingChannels()) if(pixel.sourcePhase != 0) {
				WeightedPoint point = null;

				//ArrayList<APEXPixel> neighbours = subscan.instrument.getNeighbours(pixel, 5.0 * pixel.getResolution());
				
				if((pixel.sourcePhase & Frame.CHOP_LEFT) != 0) point = left[pixel.storeIndex];
				else if((pixel.sourcePhase & Frame.CHOP_RIGHT) != 0) point = right[pixel.storeIndex];
				else continue;
					
				
				WeightedPoint df = pixel.getLROffset(phases);
					
				df.scaleWeight(Math.max(1.0, pixel.getLRChi2(phases, df.value())));
				df.scale(1.0 / (transmission * subscan.gain * sourceGain[pixel.index]));
				
				point.average(df);
			}
		}
		
		
		Channel refPixel = ((APEXArrayScan<?,?>) scan).get(0).instrument.referencePixel;
		int refIndex = refPixel.storeIndex;
		
		for(int c=flux.length; --c >=0; ) {
			flux[c] = (WeightedPoint) left[c].clone();
			flux[c].subtract(right[c]);
			flux[c].scale(0.5);
		}
	
		// TODO add all pixels chopping over the source to the source flux...
		sourceFlux.copy(flux[refIndex]);
		flux[refIndex].noData();		
	}
	
	
	@Override
	public void write(String path) throws Exception {	
		System.out.println("  Note, that the results of the APEX chopped photometry reduction below include");
		System.out.println("  the best estimate of the systematic errors, based on the true scatter of the");
		System.out.println("  chopped photometry measurements. As such, these errors are higher than what");
		System.out.println("  is expected from the nominal NEFD values alone, and reflect the true");
		System.out.println("  uncertainty of the photometry more accurately.");
		System.out.println();
		
		super.write(path);
		
	}

	
}
