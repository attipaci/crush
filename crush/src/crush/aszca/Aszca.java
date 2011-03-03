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

package crush.aszca;

import crush.*;
import crush.apex.*;
import crush.array.*;

import java.io.*;
import java.util.*;

import util.Unit;
import util.Util;


public class Aszca extends APEXArray<AszcaPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2143671786323097253L;
	
	public Aszca() {
		super("aszca", 320);	
		resolution = 60.0 * Unit.arcsec;
	}
	
	@Override
	public void addDivisions() {
		super.addDivisions();
		
		try { addDivision(getDivision("wafers", AszcaPixel.class.getField("wafer"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("squidgroups", AszcaPixel.class.getField("squidGroup"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("squids", AszcaPixel.class.getField("squid"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("cables", AszcaPixel.class.getField("cable"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
	}
	
	@Override
	public void addModalities() {
		super.addModalities();
		
		try { addModality(new CorrelatedModality("wafers", "V", divisions.get("wafers"), AszcaPixel.class.getField("waferGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("squidgroups", "Q", divisions.get("squidgroups"), AszcaPixel.class.getField("squidGroupGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("squids", "q", divisions.get("squids"), AszcaPixel.class.getField("squidGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("cables", "c", divisions.get("cables"), AszcaPixel.class.getField("cableGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		modalities.get("wafers").setGainFlag(AszcaPixel.FLAG_WAFER);
		modalities.get("squidgroups").setGainFlag(AszcaPixel.FLAG_SQUIDGROUP);
		modalities.get("squids").setGainFlag(AszcaPixel.FLAG_SQUID);
		modalities.get("cables").setGainFlag(AszcaPixel.FLAG_CABLE);
	}

	@Override
	public void flagInvalidPositions() {
		for(SimplePixel pixel : this) if(pixel.position.length() > 60.0 * Unit.arcmin) pixel.flag(Channel.FLAG_BLIND);
	}

	@Override
	public String getPixelDataHeader() {
		return super.getPixelDataHeader() + "\tGwafer\tGsquid";
	}

	@Override
	public AszcaPixel getChannelInstance(int backendIndex) {
		return new AszcaPixel(this, backendIndex);
	}  
	
	@Override
	public void readWiring(String fileName) throws IOException {
		System.err.println(" Loading wiring data from " + fileName);
		fileName = Util.getSystemPath(fileName);
		
		String[] waferNames = { "e1", "e5", "e8", "ed", "f0", "f3" };
		int boxStartAddress = Integer.decode("0xe1");
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		Hashtable<Integer, AszcaPixel> lookup = getChannelLookup();
		
		String line;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			
			tokens.nextToken();
			String readoutAddress = tokens.nextToken();
			String squidAddress = tokens.nextToken();
			AszcaPixel pixel = lookup.get(Integer.parseInt(tokens.nextToken()));
			
			pixel.wafer = Integer.decode("0x" + readoutAddress.substring(0,2)) - boxStartAddress;
			pixel.cable = 2 * pixel.wafer + (readoutAddress.charAt(4) - 'a'); 
			pixel.pin = readoutAddress.charAt(5);
			
			String waferName = squidAddress.substring(0, 2);
			
			for(int g=0; g<waferNames.length; g++) if(waferName.equalsIgnoreCase(waferNames[g])) {
				pixel.wafer = g; 
				break;
			}
			
			pixel.squid = 7 * pixel.wafer + Integer.parseInt(squidAddress.substring(5)) - 2;			
		}
		in.close();
	}

	@Override
	public int maxPixels() {
		return 320;
	}	
}

