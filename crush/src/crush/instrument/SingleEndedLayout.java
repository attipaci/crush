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

import java.util.ArrayList;

import crush.Channel;
import crush.Instrument;
import crush.Pixel;
import crush.PixelLayout;

public abstract class SingleEndedLayout extends PixelLayout {

    /**
     * 
     */
    private static final long serialVersionUID = -783638243575903810L;

    public SingleEndedLayout(Instrument<? extends Channel> instrument) {
        super(instrument);
    }
   
    
    @Override
    public void validate() {
        final Instrument<?> instrument = getInstrument();
        final int nc = instrument.size();
        
        ArrayList<Pixel> pixels = getPixels();
        pixels.clear();
        pixels.ensureCapacity(nc);
        
        for(int i=0; i<nc; i++) {
            Channel channel = instrument.get(i);
            Pixel pixel = getPixelInstance(channel.getFixedIndex(), channel.getID());
            pixel.add(channel);
            pixels.add(pixel);
        } 
        
        super.validate();
    }
    
}
