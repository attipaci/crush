/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
// Copyright (c) 2009 Attila Kovacs 

package crush.aszca;

import crush.*;
import crush.apex.*;
import crush.array.*;
import jnum.Unit;
import jnum.io.LineParser;
import jnum.text.SmartTokenizer;

import java.io.*;



public class Aszca extends APEXCamera<AszcaPixel> implements NonOverlapping {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2143671786323097253L;
	
	public Aszca() {
		super("aszca", 320);	
		setResolution(60.0 * Unit.arcsec);
	}
	
	@Override
    protected void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("wafers", AszcaPixel.class.getField("wafer"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("squidgroups", AszcaPixel.class.getField("squidGroup"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("squids", AszcaPixel.class.getField("squid"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("cables", AszcaPixel.class.getField("cable"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
	}
	
	@Override
    protected void initModalities() {
		super.initModalities();
		
		try { addModality(new CorrelatedModality("wafers", "V", divisions.get("wafers"), AszcaPixel.class.getField("waferGain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		try { addModality(new CorrelatedModality("squidgroups", "Q", divisions.get("squidgroups"), AszcaPixel.class.getField("squidGroupGain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		try { addModality(new CorrelatedModality("squids", "q", divisions.get("squids"), AszcaPixel.class.getField("squidGain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		try { addModality(new CorrelatedModality("cables", "c", divisions.get("cables"), AszcaPixel.class.getField("cableGain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		modalities.get("wafers").setGainFlag(AszcaPixel.FLAG_WAFER);
		modalities.get("squidgroups").setGainFlag(AszcaPixel.FLAG_SQUIDGROUP);
		modalities.get("squids").setGainFlag(AszcaPixel.FLAG_SQUID);
		modalities.get("cables").setGainFlag(AszcaPixel.FLAG_CABLE);
	}

	@Override
	public void flagInvalidPositions() {
		for(SingleColorPixel pixel : this) if(pixel.position.length() > 60.0 * Unit.arcmin) pixel.flag(Channel.FLAG_BLIND);
	}

	@Override
	public String getChannelDataHeader() {
		return super.getChannelDataHeader() + "\tGwafer\tGsquid";
	}

	@Override
	public AszcaPixel getChannelInstance(int backendIndex) {
		return new AszcaPixel(this, backendIndex);
	}  
	
	@Override
	public void readWiring(String fileName) throws IOException {
		info("Loading wiring data from " + fileName);
			
		final String[] waferNames = { "e1", "e5", "e8", "ed", "f0", "f3" };
		final int boxStartAddress = Integer.decode("0xe1");
		final ChannelLookup<AszcaPixel> lookup = new ChannelLookup<AszcaPixel>(this);
		
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                
                tokens.skip();
                String readoutAddress = tokens.nextToken();
                String squidAddress = tokens.nextToken();
                AszcaPixel pixel = lookup.get(tokens.nextToken());
                
                pixel.wafer = Integer.decode("0x" + readoutAddress.substring(0,2)) - boxStartAddress;
                pixel.cable = 2 * pixel.wafer + (readoutAddress.charAt(4) - 'a'); 
                pixel.pin = readoutAddress.charAt(5);
                
                String waferName = squidAddress.substring(0, 2);
                
                for(int g=0; g<waferNames.length; g++) if(waferName.equalsIgnoreCase(waferNames[g])) {
                    pixel.wafer = g; 
                    break;
                }
                
                pixel.squid = 7 * pixel.wafer + Integer.parseInt(squidAddress.substring(5)) - 2;  
                return true;
            }
		}.read(fileName);
		
	}

	@Override
	public int maxPixels() {
		return 320;
	}	
}

