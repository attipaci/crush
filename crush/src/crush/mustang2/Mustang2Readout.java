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

package crush.mustang2;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import crush.CRUSH;
import jnum.Copiable;
import jnum.Unit;

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
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public Mustang2Readout copy() {
		return (Mustang2Readout) clone();
	}
	
	public void parseFrequencies(String fileName) throws IOException {
		tones.clear();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
				
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			Mustang2PixelID id = new Mustang2PixelID(index, tones.size());
			id.freq = Double.parseDouble(line) * Unit.GHz;
			tones.add(id);
		}
		
		in.close();
		
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
