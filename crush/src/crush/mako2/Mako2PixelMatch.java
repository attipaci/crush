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

package crush.mako2;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

import crush.resonators.ResonatorList;
import crush.resonators.ToneIdentifier;
import kovacs.math.Range;
import kovacs.math.Vector2D;
import kovacs.util.Configurator;
import kovacs.util.Unit;
import kovacs.util.Util;


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
		System.err.println(" Discarded " + (n-size()) + ", kept " + size() + " tones below " + (freq / Unit.MHz) + "MHz.");
	}
	 
	public void discardBelow(double freq) {
		int n = size();
		for(int i=size(); --i >= 0; ) {
			Mako2PixelID id = get(i);
			if(id.freq < freq) remove(i);
		}
		System.err.println(" Discarded " + (n-size()) + ", kept " + size() + " tones above " + (freq / Unit.MHz) + "MHz.");
	}
	 
	
	public void read(String fileSpec) throws IOException {
		System.err.println(" Loading resonance identifications from " + fileSpec);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(Util.getSystemPath(fileSpec))));
		String line = null;

		clear();
			
		int index = 1;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line, ", \t");
			Mako2PixelID id = new Mako2PixelID(index++);
			
			id.freq = Double.parseDouble(tokens.nextToken());
			id.position = new Vector2D(Double.parseDouble(tokens.nextToken()), Double.parseDouble(tokens.nextToken()));
			id.position.scale(Unit.arcsec);
			//if(tokens.hasMoreTokens()) id.gain = Double.parseDouble(tokens.nextToken());
			
			add(id);
		}
		
		in.close();
		
		//Collections.sort(this);
		
		System.err.println(" Got IDs for " + size() + " resonances.");
	}
	
	
	
	@Override
	protected double fit(final ResonatorList<?> resonators, double initShift) {
		double delta = super.fit(resonators, initShift);
		System.err.println("   --> df/f (id) = " + Util.s4.format(1e6 * delta) + " ppm.");
		return delta;
	}
	


	@Override
	public Range getDefaultShiftRange() {
		return new Range(-1e-3, 1e-3);
	}
	
}