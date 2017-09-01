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

package crush.instrument.mustang2;

import java.io.IOException;
import java.util.ArrayList;

import crush.CRUSH;
import jnum.Copiable;
import jnum.Unit;
import jnum.io.LineParser;

public class Mustang2Readout implements Cloneable, Copiable<Mustang2Readout> {
	private int index;
	double bias;
	double heater;
	ArrayList<Mustang2PixelID> tones = new ArrayList<Mustang2PixelID>(Mustang2.maxReadoutChannels);
	
	public Mustang2Readout(int index) { 
		this.index = index;
	}	
	
	public int getIndex() { return index; }

	@Override
	public Mustang2Readout clone() {
		try { return (Mustang2Readout) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public Mustang2Readout copy() {
		return clone();
	}
	
	public void parseFrequencies(String fileName) throws IOException {
		tones.clear();
		
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                Mustang2PixelID id = new Mustang2PixelID(index, tones.size());
                id.freq = Double.parseDouble(line) * Unit.GHz;
                tones.add(id);
                return true;
            }  
		}.read(fileName);
		
		
		CRUSH.info(this, "ROACH " + (index+1) + ": " + tones.size() + " frequencies from " + fileName);	
	}
	
	
	public Mustang2PixelID getNearestID(double f) {
		double mind = Double.POSITIVE_INFINITY;
		Mustang2PixelID nearest = null;
		for(Mustang2PixelID tone : tones) {
			double d = Math.abs(tone.freq - f);
			if(d < mind) {
				mind = d;
				nearest = tone;
			}
		}
		return nearest;
	}
	
	
	
}
