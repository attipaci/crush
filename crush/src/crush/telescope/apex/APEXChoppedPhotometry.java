/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.telescope.apex;

import java.util.ArrayList;

import crush.*;
import crush.sourcemodel.Photometry;
import jnum.data.DataPoint;
import jnum.data.WeightedPoint;
import jnum.math.Range;

public class APEXChoppedPhotometry extends Photometry {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3704113753270498352L;

	public APEXChoppedPhotometry(APEXCamera<?> instrument) {
		super(instrument);
	}
	
	public APEXCamera<?> getAPEXArray() { return (APEXCamera<?>) getInstrument(); }


	@Override
	public void process(Scan<?, ?> scan) {		
		final DataPoint[] left = DataPoint.createArray(flux.length);
		final DataPoint[] right = DataPoint.createArray(flux.length);

		for(Integration<?,?> integration : scan) process((APEXSubscan<?,?>) integration, left, right);
		
		sourceFlux.noData();	
		
		for(int c=flux.length; --c >=0; ) {
			flux[c] = left[c].copy();
			flux[c].subtract(right[c]);
			flux[c].scale(0.5);
			if(flux[c].weight() > 0.0) sourceFlux.average(flux[c]);
		}
	
		DataPoint F = new DataPoint(sourceFlux);
		F.scale(1.0 / getInstrument().janskyPerBeam());
		
		scanFluxes.put(scan, F.copy());
		
		scan.getLastIntegration().comments.append(" " + (F.weight() > 0.0 ? F.toString() : "<<invalid>>") + " ");
	}
	
	protected void process(final APEXSubscan<?,?> subscan, final WeightedPoint[] left, final WeightedPoint[] right) {		
		// Proceed only if there are enough pixels to do the job...
		if(!checkPixelCount(subscan)) return;		


		final double transmission = 0.5 * (subscan.getFirstFrame().getTransmission() + subscan.getLastFrame().getTransmission());
		final double[] sourceGain = subscan.instrument.getSourceGains(false);
		final PhaseSet phases = subscan.getPhases();

		final double radius = hasOption("neighbours.radius") ? option("neighbours.radius").getDouble() : 0.0;	

		subscan.instrument.getObservingChannels().new Fork<Void>() {
			@Override
			protected void process(APEXContinuumPixel pixel) {	
				WeightedPoint point = null;

				ArrayList<APEXContinuumPixel> neighbours = subscan.instrument.getNeighbours(pixel, radius * pixel.getResolution());

				if((pixel.sourcePhase & Frame.CHOP_LEFT) != 0) point = left[pixel.getFixedIndex()];
				else if((pixel.sourcePhase & Frame.CHOP_RIGHT) != 0) point = right[pixel.getFixedIndex()];
				else return;

				/*
					try { pixel.writeLROffset(phases, 
							CRUSH.workPath + File.separator + "phases-" + integration.getFullID("-") + "-P" + pixel.getFixedIndex() + ".dat",
							neighbours, sourceGain); }
					catch(IOException e) { error(e); }

					try { pixel.writeLRSpectrum(phases, 
							CRUSH.workPath + File.separator + "phases-" + integration.getFullID("-") + "-P" + pixel.getFixedIndex() + ".spec"); }
					catch(IOException e) { error(e); }
				 */

				WeightedPoint df = pixel.getBGCorrectedLROffset(phases, neighbours, sourceGain);	
				double chi2 = pixel.getBGCorrectedLRChi2(phases, neighbours, df.value(), sourceGain);

			    if(hasOption("chirange")) {
			        Range r = option("chirange").getRange(true);
                    if(!r.contains(Math.sqrt(chi2))) {
                        subscan.comments.append(" <<skip>>"); 
                        df.noData();
                    }
                }
				
				if(!Double.isNaN(chi2)) {
					df.scaleWeight(Math.min(1.0, 1.0 / chi2));            
					df.scale(1.0 / (transmission * subscan.gain * sourceGain[pixel.index]));
					point.average(df);
				}
			}
			
		}.process();
	}
	

	@Override
	public void write() throws Exception {	
		info("Note, that the results of the APEX chopped photometry reduction below include "
		        + "an estimate of the systematic errors, based on the true scatter of the chopped "
		        + "photometry measurements in each nod cycle. As such, these errors are higher than "
		        + "expected from the nominal NEFD values alone, and reflect the photometric uncertainty "
		        + "more accurately.");
		
		
		if(numberOfScans() > 1) {
			info("\nScan-to-scan scatter is measured by the reduced chi value. When |rChi| > 1, "
			        + "you can multiply the quoted uncertainty by it to arrive at a more robust estimate "
			        + "of the total measurement uncertainty.");
		}
		
		super.write();
	}

	
	
}
