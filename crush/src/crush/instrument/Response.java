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
package crush.instrument;


import crush.Frame;
import crush.Integration;
import crush.Mode;
import crush.Signal;


/**
 * A class for representing the common mode response of a group of detector channels to some known external stimulus,
 * such as an electronic modulation, measured temperature variation, or telescope movememt.
 * 
 * 
 * The removal of common mode responses involves the estimation of coupling gains to the known driving signal. 
 * Following the gain estimation, the scaled imprint of the known driving signal is subtracted from the 
 * timestreams of channels associated with this common mode response.
 * 
 * 
 * 
 * @author Attila Kovacs
 *
 */
public abstract class Response<FrameType extends Frame> extends Mode {    
	/**
	 * 
	 */
	private static final long serialVersionUID = -8619855129077390006L;

    private boolean isFloating;
   
    public Response() { this(false); }
    
    public Response(boolean isFloating) {
        super();
        this.isFloating = isFloating;
    }

    public boolean isFloating() { return isFloating; }
    
    public void setFloating(boolean value) { isFloating = value; }


	public abstract Signal getSignal(Integration<? extends FrameType> integration);    
	
}
