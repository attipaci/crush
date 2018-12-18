/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.laboca;

import crush.*;
import crush.array.*;
import crush.telescope.apex.*;
import jnum.Unit;
import jnum.Util;
import jnum.io.LineParser;
import jnum.math.Range;
import jnum.text.SmartTokenizer;
import nom.tam.fits.*;

import java.io.*;



public class Laboca extends APEXCamera<LabocaPixel> implements NonOverlapping {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5113244732586496137L;
	
	public Laboca() {
		super("laboca", 320);	
		setResolution(19.5 * Unit.arcsec);
	}
	
	
	@Override
	public int getNonDetectorFlags() {
		return super.getNonDetectorFlags() | LabocaPixel.FLAG_RESISTOR;
	}
	
	@Override
	public LabocaPixel getChannelInstance(int backendIndex) { return new LabocaPixel(this, backendIndex); }
	
	@Override
	public void readPar(BinaryTableHDU hdu) throws IOException, FitsException, HeaderCardException {
		Header header = hdu.getHeader();

		gain = 270.0 * (1<<(int)header.getDoubleValue("FEGAIN"));
		//CRUSH.values(this, "Frontend Gain is " + gain);
	
		// LabocaPixel.offset: get BOLDCOFF
		
		// Needed only for pre Feb2007 Files with integer format.
		//gain *= 10.0/ ((float[]) row[hdu.findColumn("BEGAIN"])[0] / (1<<15-1);	
		
		if(hasOption("range.auto")) {
			Range range = new Range(-9.9, 9.9);
			Object[] row = hdu.getRow(0);
			float G = ((float[]) row[hdu.findColumn("BEGAIN")])[0];
			range.scale(1.0 / G);
			info("Setting ADC range to " + range.toString() + "(V)");
			setOption("range=" + range.toString());
		}
		
		super.readPar(hdu);
		
	}
	
	@Override
    protected void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("boxes", LabocaPixel.class.getField("box"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("cables", LabocaPixel.class.getField("cable"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("amps", LabocaPixel.class.getField("amp"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }

		try { addDivision(getDivision("pins", LabocaPixel.class.getField("pin"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
	}
	
	@Override
    protected void initModalities() {
		super.initModalities();
		
		try { addModality(new CorrelatedModality("boxes", "B", divisions.get("boxes"), LabocaPixel.class.getField("boxGain"))); }
		catch(NoSuchFieldException e) { error(e); }
		
		try {
			CorrelatedModality cables = new CorrelatedModality("cables", "c", divisions.get("cables"), LabocaPixel.class.getField("cableGain"));
			addModality(cables);
			addModality(cables.new CoupledModality("twisting", "t", new LabocaCableTwist()));
		}			
		catch(NoSuchFieldException e) { error(e); }
		
		try { addModality(new CorrelatedModality("amps", "a", divisions.get("amps"), LabocaPixel.class.getField("ampGain"))); }
		catch(NoSuchFieldException e) { error(e); }
	
		try { addModality(new Modality<LabocaHe3Response>("temperature", "T", divisions.get("detectors"), LabocaPixel.class.getField("temperatureGain"), LabocaHe3Response.class));	}
		catch(NoSuchFieldException e) { error(e); }
		
		modalities.get("boxes").setGainFlag(LabocaPixel.FLAG_BOX);
		modalities.get("cables").setGainFlag(LabocaPixel.FLAG_CABLE);
		modalities.get("amps").setGainFlag(LabocaPixel.FLAG_AMP);
		
		((CorrelatedModality) modalities.get("boxes")).setSkipFlags(~(LabocaPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
		((CorrelatedModality) modalities.get("cables")).setSkipFlags(~(LabocaPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
		((CorrelatedModality) modalities.get("amps")).setSkipFlags(~(LabocaPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
	}
	
	
	@Override
	public void readWiring(String fileName) throws IOException {	
		info("Loading wiring data from " + fileName);
		
		final ChannelLookup<LabocaPixel> lookup = new ChannelLookup<LabocaPixel>(this);
	
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                LabocaPixel pixel = lookup.get(tokens.nextToken());     
                if(pixel == null) return false;
                
                pixel.box = tokens.nextInt();
                pixel.cable = tokens.nextInt();
                tokens.nextToken(); // amp line
                pixel.amp = 16 * pixel.box + tokens.nextToken().charAt(0) - 'A';
                tokens.nextToken(); // cable name
                pixel.pin = tokens.nextInt(); // cable pin
                
                int bol = tokens.nextInt();
                char state = tokens.nextToken().toUpperCase().charAt(0);
                //boolean hasComment = tokens.hasMoreTokens();
                
                if(bol < 0 || state != 'B') {
                    if(state == 'R') {
                        pixel.flag(LabocaPixel.FLAG_RESISTOR);
                        pixel.gain = 0.0;
                        pixel.coupling = 0.0;
                    }
                    else pixel.flag(Channel.FLAG_DEAD);
                }
                return true;
            }
		}.read(fileName);
		
		
	}
	
	public void readTemperatureGains(String fileName) throws IOException {
		info("Loading He3 gains from " + fileName);
		
		
        final ChannelLookup<LabocaPixel> lookup = new ChannelLookup<LabocaPixel>(this);
        
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                LabocaPixel pixel = lookup.get(tokens.nextToken());
                if(pixel == null) return false;
                pixel.temperatureGain = tokens.nextDouble();
                return true;
            }
		    
		}.read(fileName);	
	}
	
	
	public void writeTemperatureGains(String fileName, String header) throws IOException {		
		PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(fileName)));
		out.println("# He3 Temperature Gains Data.");
		out.println("# ");
		if(header != null) {
			out.println(header);
			out.println("# ");
		}
		out.println("# BEch\tGain");
		out.println("#     \t(V/K)");
		out.println("# ----\t-----");
		for(LabocaPixel pixel : this) out.println(pixel.getID() + "\t" + Util.e6.format(pixel.temperatureGain));
		
		out.flush();
		out.close();
		notify("Written He3 gain data to " + fileName + ".");
		
	}
	
	// Wiring is read when divisions are created...
	@Override
    protected void loadChannelData() {
		super.loadChannelData();
		
		if(hasOption("he3")) if(!option("he3").is("calc")) {
			String fileName = hasOption("he3.gains") ? option("he3.gains").getPath() : getConfigPath() + "he3-gains.dat";
			
			try { readTemperatureGains(fileName); }
			catch(IOException e) {
				warning("File not found. Skipping temperature correction.");
				getOptions().purge("he3");
			}
		}
			
		if(hasOption("noresistors")) {
			for(LabocaPixel pixel : this) if(pixel.isFlagged(LabocaPixel.FLAG_RESISTOR)) pixel.flag(Channel.FLAG_DEAD);
		}
		else {
			// Unflag 1MOhm resistors as blinds, since these will be flagged as dead by markBlindChannels() 
			// [and removed by slim()] when blind channels are explicitly defined via the 'blind' option.
			for(LabocaPixel pixel : this) if(pixel.isFlagged(LabocaPixel.FLAG_RESISTOR)) pixel.unflag(Channel.FLAG_BLIND);
		}
		
	}
	

	@Override
	public Scan<?, ?> getScanInstance() {
		return new LabocaScan(this);
	}
	

	@Override
	public void flagInvalidPositions() {
		for(SingleColorPixel pixel : this) if(pixel.position.length() > 10.0 * Unit.arcmin) pixel.flag(Channel.FLAG_BLIND);
	}

	@Override
	public String getChannelDataHeader() {
		return super.getChannelDataHeader() + "\tGbox\tGcable\tbox\tcable\tamp";
	}
	
	
	
}



