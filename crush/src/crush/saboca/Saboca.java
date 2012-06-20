/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2009 Attila Kovacs 

package crush.saboca;

import crush.*;
import crush.apex.*;
import crush.array.*;

import java.io.*;
import java.util.*;

import util.Unit;


public class Saboca extends APEXArray<SabocaPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7625410928147194316L;

	public Saboca() {
		super("saboca", 40);	
		resolution = 7.4 * Unit.arcsec;
		gain = -1000.0;
	}
	
	@Override
	public void addDivisions() {
		super.addDivisions();
		
		try { addDivision(getDivision("squids", SabocaPixel.class.getField("squid"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
	}
	
	@Override
	public void addModalities() {
		super.addModalities();
		
		try { addModality(new CorrelatedModality("squids", "q", divisions.get("squids"), SabocaPixel.class.getField("squidGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		modalities.get("squids").setGainFlag(SabocaPixel.FLAG_SQUID);
	}
	

	@Override
	public SabocaPixel getChannelInstance(int backendIndex) {
		return new SabocaPixel(this, backendIndex);
	}

	@Override
	public void flagInvalidPositions() {
		for(SimplePixel pixel : this) if(pixel.position.length() > 3.0 * Unit.arcmin) pixel.flag(Channel.FLAG_BLIND);
	}

	@Override
	public String getPixelDataHeader() {
		return super.getPixelDataHeader() + "\tGsquid";
	}
	
	@Override
	public void readWiring(String fileName) throws IOException {
		System.err.println(" Loading wiring data from " + fileName);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		Hashtable<Integer, SabocaPixel> lookup = getChannelLookup();
		
		String line;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			SabocaPixel pixel = lookup.get(Integer.parseInt(tokens.nextToken()));
			if(pixel != null) {
				pixel.squid = Integer.parseInt(tokens.nextToken());
				pixel.pin = Integer.parseInt(tokens.nextToken());
				// in principle the default positions are also here...
				// TODO maybe should be used for blind flagging...
			}
		}
		in.close();
	}	
	
}
