/* *****************************************************************************
 * Copyright (c) 2017 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.telescope.sofia;


import java.util.List;

import jnum.Configurator;
import jnum.Unit;

public class AtranModel {
    private List<Double> amCoeffs;
    private List<Double> altCoeffs;
  
    private double referenceTransmission = 1.0;
    
    public AtranModel(Configurator options) {
        init(options);
    }
    
    private void init(Configurator options) throws IllegalStateException {
        if(options.hasOption("atran.amcoeffs")) amCoeffs = options.option("atran.amcoeffs").getDoubles();
        else throw new IllegalStateException("Undefined option 'atran.amcoeffs'");
        
        if(options.hasOption("atran.altcoeffs")) altCoeffs = options.option("atran.altcoeffs").getDoubles();
        else throw new IllegalStateException("Undefined option 'atran.altcoeffs'");
        
        if(options.hasOption("atran.reference")) referenceTransmission = options.option("atran.reference").getDouble();
    }
    
    public double getReferenceTransmission() { return referenceTransmission; }
    
    public double getRelativeTransmission(double altitude, double elevation) {
        return getValueAt(1.0 / Math.sin(elevation) - referenceAirmass, amCoeffs) * 
                getValueAt((altitude - referenceAltitude) / kft, altCoeffs);
    }
    
   
    private double getValueAt(double dx, List<Double> coeffs) {
        double value = 0.0;
        double term = 1.0;
        
        for(int i=0; i<coeffs.size(); i++) {
            value += coeffs.get(i) * term;
            term *= dx;
        }
      
        return value;
    }
    
    private final double kft = 1000.0 * Unit.ft;
    private final double referenceAirmass = Math.sqrt(2.0);
    private final double referenceAltitude = 41.0 * kft;
}
