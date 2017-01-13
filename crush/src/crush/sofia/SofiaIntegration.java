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

package crush.sofia;

import crush.GroundBased;
import crush.Integration;
import crush.Scan;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;

public abstract class SofiaIntegration<InstrumentType extends SofiaCamera<?, ?>, FrameType extends SofiaFrame> 
extends Integration<InstrumentType, FrameType> implements GroundBased {

    /**
     * 
     */
    private static final long serialVersionUID = -4771883165716694480L;


    public SofiaIntegration(Scan<InstrumentType, ?> parent) {
        super(parent);
    }

    @Override
    public double getModulationFrequency(int signalMode) {
        SofiaScan<?,?> sofiaScan = (SofiaScan<?,?>) scan;
        if(sofiaScan.isChopping) return sofiaScan.chopper.frequency;
        return super.getModulationFrequency(signalMode);
    }

    public double getMeanPWV() { return ((SofiaScan<?,?>) scan).environment.pwv.midPoint(); }
    
    @Override
    public void validate() {  
        
        double pwv = getMeanPWV();
        info("PWV: " + Util.f1.format(pwv / Unit.um) + " um");
     

        if(!hasOption("tau.pwv")) {
            if(Double.isNaN(pwv)) {
                pwv = 0.0;
                info("--> FIX: Assuming PWV = 0 um for opacity correction...");
            }
           
            try { instrument.getOptions().process("tau.pwv", Double.toString(pwv / Unit.um)); }
            catch(LockedException e) {}
        }
        if(!hasOption("tau")) {
            try { instrument.getOptions().process("tau", "pwv"); }
            catch(LockedException e) {}
        }
        
       
        super.validate();
    }

}

