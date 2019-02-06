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

package crush.instrument.saboca;

import crush.*;
import crush.instrument.NonOverlapping;
import crush.telescope.apex.*;
import jnum.Unit;
import jnum.io.LineParser;
import jnum.text.SmartTokenizer;

import java.io.*;



public class Saboca extends APEXInstrument<SabocaPixel> implements NonOverlapping {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7625410928147194316L;

	public Saboca() {
		super("saboca", 40);	
		setResolution(7.4 * Unit.arcsec);
		gain = -1000.0;
	}
	
	@Override
    protected void createDivisions() {
		super.createDivisions();
		
		try { addDivision(getDivision("squids", SabocaPixel.class.getField("squid"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
	}
	
	@Override
    protected void createModalities() {
		super.createModalities();
		
		try { addModality(new CorrelatedModality("squids", "q", divisions.get("squids"), SabocaPixel.class.getField("squidGain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		modalities.get("squids").setGainFlag(SabocaPixel.FLAG_SQUID);
	}
	

	@Override
	public SabocaPixel getChannelInstance(int backendIndex) {
		return new SabocaPixel(this, backendIndex);
	}

	@Override
	public void flagInvalidPositions() {
		for(Pixel pixel : getPixels()) if(pixel.getPosition().length() > 3.0 * Unit.arcmin)
		    for(Channel channel : pixel) channel.flag(Channel.FLAG_BLIND);
	}

	@Override
	public String getChannelDataHeader() {
		return super.getChannelDataHeader() + "\tGsquid";
	}
	
	
	@Override
    protected void readWiring(String fileName) throws IOException {
		info("Loading wiring data from " + fileName);
		
		final ChannelLookup<SabocaPixel> lookup = new ChannelLookup<SabocaPixel>(this);
	
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                SabocaPixel pixel = lookup.get(tokens.nextToken());
                if(pixel == null) return false;
               
                pixel.squid = tokens.nextInt();
                pixel.pin = tokens.nextInt();
                // in principle the default positions are also here...
                // TODO maybe should be used for blind flagging...
                
                return true;
            }  
		}.read(fileName);
	}	
	
}
