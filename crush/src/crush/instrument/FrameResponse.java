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

package crush.instrument;

import crush.Frame;
import crush.Integration;
import crush.Signal;


public abstract class FrameResponse<FrameType extends Frame> extends Response<FrameType> {

    /**
     * 
     */
    private static final long serialVersionUID = 6877873384217735032L;

    private int derivative = 0;
    
    public FrameResponse() {
        this(false);
    }

    public FrameResponse(boolean isFloating) {
        super(isFloating);
    }

    
    public void setDerivative(int n) {
        derivative = n;
    }
    
    protected abstract double getValue(FrameType exposure) throws Exception;

    
    @Override
    public final Signal getSignal(Integration<? extends FrameType> integration) {
        float[] data = new float[integration.size()];   

        try {
            for(int t=data.length; --t >= 0; ) {
                final FrameType exposure = integration.get(t);
                data[t] = exposure == null ? Float.NaN : (float) getValue(exposure);
            }
        }
        catch(Exception e) { integration.warning(e); }

        Signal s = new Signal(this, integration, data, true);
        for(int i=derivative; --i >= 0; ) s.differentiate();
        return s;
    }

    
    
}
