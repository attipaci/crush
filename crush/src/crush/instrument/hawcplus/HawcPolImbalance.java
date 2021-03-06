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

package crush.instrument.hawcplus;

import crush.Channel;
import crush.instrument.ZeroMeanGains;

public class HawcPolImbalance extends ZeroMeanGains {

    /**
     * 
     */
    private static final long serialVersionUID = 3346494258785117721L;

    @Override
    public double getRelativeGain(Channel c) throws Exception {
        return ((HawcPixel) c).pol == 0 ? 1.0 : -1.0;
    }

    @Override
    public void setRawGain(Channel c, double value) throws Exception {
        throw new UnsupportedOperationException("Cannot set polarization imbalance gains.");
    }
}
