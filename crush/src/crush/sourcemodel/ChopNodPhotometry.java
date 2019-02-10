/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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



import java.util.Hashtable;

import crush.*;
import crush.motion.ChopperPhases;
import crush.telescope.TelescopeFrame;
import crush.telescope.TelescopeScan;
import crush.telescope.apex.APEXInstrument;
import jnum.data.DataPoint;
import jnum.data.WeightedPoint;
import jnum.math.Range;



public class ChopNodPhotometry extends PointPhotometry {

    /**
     * 
     */
    private static final long serialVersionUID = -3704113753270498352L;


    public ChopNodPhotometry(APEXInstrument<?> instrument) {
        super(instrument);
    }
    

    @Override
    public void process(Scan<?> scan) {		
        Hashtable<String, Datum> data = new Hashtable<>();

        for(Integration<?> integration : scan) extractData(integration, data);

        sourceFlux.noData();

        DataPoint F = new DataPoint();

        for(Datum entry : data.values()) {    
            F.copy(entry.L);
            F.subtract(entry.R);
            F.scale(0.5);
            if(F.weight() > 0.0) sourceFlux.average(F);
        }

        F.copy(sourceFlux);
        F.scale(1.0 / getInstrument().janskyPerBeam());

        scanFluxes.put(scan, F.copy());

        scan.getLastIntegration().comments.append(" " + (F.weight() > 0.0 ? F.toString() : "<<invalid>>") + " ");
    }

    protected void extractData(final Integration<?> integration, final Hashtable<String, Datum> data) {		
        // Proceed only if there are enough pixels to do the job...
        if(!checkPixelCount(integration)) return;		
        if(!(integration instanceof PhaseModulated)) 
            throw new IllegalArgumentException("Integration " + integration.getDisplayID() + " is not phase-monulated.");

        Instrument<?> instrument = integration.getInstrument();
        PhaseModulated modulated = (PhaseModulated) integration;

        if(!(modulated.getPhases() instanceof ChopperPhases))
            throw new IllegalArgumentException("Integration " + integration.getDisplayID() + " is not a chop-nod observation.");

        double T = 1.0;

        if(integration.getScan() instanceof TelescopeScan)
            T = 0.5 * (((TelescopeFrame) integration.getFirstFrame()).getTransmission() + ((TelescopeFrame) integration.getLastFrame()).getTransmission());

        final double[] sourceGain = instrument.getSourceGains(false);
        final ChopperPhases phases = (ChopperPhases) modulated.getPhases();
        final double transmission = T;
        
        
        instrument.getObservingChannels().new Fork<Void>() {

            @Override
            protected void process(Channel channel) {	

                Datum datum = data.get(channel.getID());
                if(datum == null) {
                    datum = new Datum();
                    data.put(channel.getID(), datum);
                }

                DataPoint point = null;

                if((channel.sourcePhase & TelescopeFrame.CHOP_LEFT) != 0) point = datum.L;
                else if((channel.sourcePhase & TelescopeFrame.CHOP_RIGHT) != 0) point = datum.R;
                else return;

                WeightedPoint df = phases.getLROffset(channel);
                double chi2 = phases.getLRChi2(channel, df.value());
         
                if(hasOption("chirange")) {
                    Range r = option("chirange").getRange(true);
                    if(!r.contains(Math.sqrt(chi2))) {
                        integration.comments.append(" <<skip>>"); 
                        df.noData();
                    }
                }

                if(!Double.isNaN(chi2)) {
                    df.scaleWeight(Math.min(1.0, 1.0 / chi2));            
                    df.scale(1.0 / (transmission * integration.gain * sourceGain[channel.getIndex()]));
                    point.average(df);
                }

            }

        }.process();
    }


    @Override
    public void write() throws Exception {	
        info("Note, that the results of the chopped photometry reduction below include an "
                + "estimate of the systematic errors, based on the true scatter of the chopped "
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



    private class Datum {
        DataPoint L = new DataPoint();
        DataPoint R = new DataPoint();   
    }



}
