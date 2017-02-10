/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.mako2;

import java.io.IOException;

import crush.CRUSH;
import crush.resonators.ResonatorList;
import crush.resonators.ToneIdentifier;
import jnum.Configurator;
import jnum.Unit;
import jnum.Util;
import jnum.io.LineParser;
import jnum.math.Range;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;


public class Mako2PixelMatch extends ToneIdentifier<Mako2PixelID> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3011775640230135691L;
		
	
	
	public Mako2PixelMatch() {}
	
	public Mako2PixelMatch(Configurator options) throws IOException {
		super(options);
		read(options.getValue());
	}
	
	public Mako2PixelMatch(String fileName) throws IOException {
		this();
		read(fileName);
	}
	
	public void discardAbove(double freq) {
		int n = size();
		for(int i=size(); --i >= 0; ) {
			Mako2PixelID id = get(i);
			if(id.freq >= freq) remove(i);
		}
		CRUSH.info(this, "Discarded " + (n-size()) + ", kept " + size() + " tones below " + (freq / Unit.MHz) + "MHz.");
	}
	 
	public void discardBelow(double freq) {
		int n = size();
		for(int i=size(); --i >= 0; ) {
			Mako2PixelID id = get(i);
			if(id.freq < freq) remove(i);
		}
		CRUSH.info(this, "Discarded " + (n-size()) + ", kept " + size() + " tones above " + (freq / Unit.MHz) + "MHz.");
	}
	 
	
	public void read(String fileSpec) throws IOException {
		CRUSH.info(this, "Loading resonance identifications from " + fileSpec);
	
		clear();
				
		new LineParser() {
		    int index = 1;
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line, ", \t");
                Mako2PixelID id = new Mako2PixelID(index++);
                
                id.freq = tokens.nextDouble();
                id.position = new Vector2D(tokens.nextDouble(), tokens.nextDouble());
                id.position.scale(Unit.arcsec);
                //if(tokens.hasMoreTokens()) id.gain = tokens.nextDouble();
                
                add(id);
                return true;
            }
		}.read(fileSpec);
			
		//Collections.sort(this);
		
		CRUSH.info(this, "Got IDs for " + size() + " resonances.");
	}
	
	
	
	@Override
	protected double fit(final ResonatorList<?> resonators, double initShift) {
		double delta = super.fit(resonators, initShift);
		CRUSH.values(this, "--> df/f (id) = " + Util.s4.format(1e6 * delta) + " ppm.");
		return delta;
	}
	


	@Override
	public Range getDefaultShiftRange() {
		return new Range(-1e-3, 1e-3);
	}
	
}