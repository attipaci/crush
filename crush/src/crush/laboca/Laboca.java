/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.laboca;

import crush.*;
import crush.apex.*;
import crush.array.*;
import nom.tam.fits.*;

import java.io.*;
import java.util.*;

import kovacs.math.Range;
import kovacs.util.Unit;
import kovacs.util.Util;



public class Laboca extends APEXCamera<LabocaPixel> implements NonOverlappingChannels {
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
		//System.err.println(" Frontend Gain is " + gain);
	
		// LabocaPixel.offset: get BOLDCOFF
		
		// Needed only for pre Feb2007 Files with integer format.
		//gain *= 10.0/ ((float[]) row[hdu.findColumn("BEGAIN"])[0] / (1<<15-1);	
		
		if(hasOption("range.auto")) {
			Range range = new Range(-9.9, 9.9);
			Object[] row = hdu.getRow(0);
			float G = ((float[]) row[hdu.findColumn("BEGAIN")])[0];
			range.scale(1.0 / G);
			System.err.println(" Setting ADC range to " + range.toString() + "(V)");
			setOption("range=" + range.toString());
		}
		
		super.readPar(hdu);
	}
	
	@Override
	public void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("boxes", LabocaPixel.class.getField("box"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("cables", LabocaPixel.class.getField("cable"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("amps", LabocaPixel.class.getField("amp"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
	}
	
	@Override
	public void initModalities() {
		super.initModalities();
		
		try { addModality(new CorrelatedModality("boxes", "B", divisions.get("boxes"), LabocaPixel.class.getField("boxGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try {
			CorrelatedModality cables = new CorrelatedModality("cables", "c", divisions.get("cables"), LabocaPixel.class.getField("cableGain"));
			addModality(cables);
			addModality(cables.new CoupledModality("twisting", "t", new CableTwist()));
		}			
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("amps", "a", divisions.get("amps"), LabocaPixel.class.getField("ampGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
	
		try { addModality(new Modality<LabocaHe3Response>("temperature", "T", divisions.get("detectors"), LabocaPixel.class.getField("temperatureGain"), LabocaHe3Response.class));	}
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		modalities.get("boxes").setGainFlag(LabocaPixel.FLAG_BOX);
		modalities.get("cables").setGainFlag(LabocaPixel.FLAG_CABLE);
		modalities.get("amps").setGainFlag(LabocaPixel.FLAG_AMP);
		
		((CorrelatedModality) modalities.get("boxes")).setSkipFlags(~(LabocaPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
		((CorrelatedModality) modalities.get("cables")).setSkipFlags(~(LabocaPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
		((CorrelatedModality) modalities.get("amps")).setSkipFlags(~(LabocaPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
	}
	
	
	@Override
	public void readWiring(String fileName) throws IOException {	
		System.err.println(" Loading wiring data from " + fileName);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		Hashtable<Integer, LabocaPixel> lookup = getFixedIndexLookup();
		
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
		 	LabocaPixel pixel = lookup.get(Integer.parseInt(tokens.nextToken())); 	
		 	if(pixel == null) continue;
		 	
			pixel.box = Integer.parseInt(tokens.nextToken());
			pixel.cable = Integer.parseInt(tokens.nextToken());
			tokens.nextToken(); // amp line
			pixel.amp = 16 * pixel.box + tokens.nextToken().charAt(0) - 'A';
			tokens.nextToken(); // cable name
			pixel.pin = Integer.parseInt(tokens.nextToken()); // cable pin
			
			int bol = Integer.parseInt(tokens.nextToken());
			char state = tokens.nextToken().charAt(0);
			//boolean hasComment = tokens.hasMoreTokens();
			
			if(bol < 0 || state != 'B') pixel.flag(Channel.FLAG_DEAD);
			if(state == 'R') {
				pixel.unflag(Channel.FLAG_DEAD);
				pixel.flag(LabocaPixel.FLAG_RESISTOR);
				pixel.gain = 0.0;
				pixel.coupling = 0.0;
				pixel.boxGain = 0.0;
			}
		}	
		in.close();
	}
	
	public void readTemperatureGains(String fileName) throws IOException {
		System.err.println(" Loading He3 gains from " + fileName);
		
		// Read gains into LabocaPixel -> temperatureGain:
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
		
		Hashtable<Integer, LabocaPixel> lookup = getFixedIndexLookup();
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			LabocaPixel pixel = lookup.get(Integer.parseInt(tokens.nextToken()));
			if(pixel != null) pixel.temperatureGain = Double.parseDouble(tokens.nextToken());
		}
		in.close();
		
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
		for(LabocaPixel pixel : this) out.println(pixel.getFixedIndex() + "\t" + Util.e6.format(pixel.temperatureGain));
		
		out.flush();
		out.close();
		System.err.println(" Written He3 gain data to " + fileName + ".");
		
	}
	
	// Wiring is read when divisions are created...
	@Override
	public void loadChannelData() {
		super.loadChannelData();
		
		if(hasOption("he3")) if(!option("he3").equals("calc")) {
			String fileName = hasOption("he3.gains") ? option("he3.gains").getPath() : getConfigPath() + "he3-gains.dat";
			
			try { readTemperatureGains(fileName); }
			catch(IOException e) {
				System.err.println(" WARNING! File not found. Skipping temperature correction.");
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



