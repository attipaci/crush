/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.telescope.sofia;

import crush.Channel;
import crush.Instrument;

public class SofiaChannel extends Channel {
    /**
     * 
     */
    private static final long serialVersionUID = 5106268489594345916L;

    public double losGain = 1.0, rollGain = 1.0;
    
    protected SofiaChannel(Instrument<?> instrument, int fixedIndex) {
        super(instrument, fixedIndex);
    }
    
    public static final int FLAG_LOS_RESPONSE = softwareFlags.next('L', "LOS response").value();
    public static final int FLAG_ROLL_RESPONSE = softwareFlags.next('\\', "Roll response").value();
}
