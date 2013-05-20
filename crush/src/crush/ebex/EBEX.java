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
// Copyright (c) 2010 Attila Kovacs 

package crush.ebex;

import java.io.*;
import java.util.*;

import util.Unit;

import crush.*;
import crush.array.Array;

public class EBEX extends Array<EBEXPixel, EBEXPixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1922503981182905922L;

	
	public EBEX(String name) {
		super(name);
		mount = Mount.CASSEGRAIN;
		nonDetectorFlags = Channel.FLAG_DEAD | EBEXPixel.FLAG_RESISTOR;
	}
	
	@Override
	public void addGroups() {
		super.addGroups();
		
		addGroup("eccosorbs", getChannels().discard(nonDetectorFlags).discard(EBEXPixel.FLAG_ECCOSORB, ChannelGroup.KEEP_MATCH_FLAGS));
		addGroup("resistors", getChannels().discard(EBEXPixel.FLAG_RESISTOR, ChannelGroup.KEEP_MATCH_FLAGS));
	}
	
	@Override
	public void addDivisions() {
		super.addDivisions();
		
		addDivision(new ChannelDivision<EBEXPixel>("eccorsorbs", groups.get("eccosorbs")));
		addDivision(new ChannelDivision<EBEXPixel>("resistors", groups.get("resistors")));
		
		try { addDivision(getDivision("boards", EBEXPixel.class.getField("board"), nonDetectorFlags)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("cables", EBEXPixel.class.getField("wire"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("squidboards", EBEXPixel.class.getField("squidBoard"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("squids", EBEXPixel.class.getField("squid"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		try { addDivision(getDivision("rows", EBEXPixel.class.getField("row"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
	
		((CorrelatedModality) modalities.get("boards")).setSkipFlags(~(EBEXPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
		((CorrelatedModality) modalities.get("cables")).setSkipFlags(~(EBEXPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
	
		((CorrelatedModality) modalities.get("squidBoards")).setSkipFlags(~(EBEXPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
		((CorrelatedModality) modalities.get("squids")).setSkipFlags(~(EBEXPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));

		
	}

	@Override
	public void addModalities() {
		super.addModalities();
		
		try { addModality(new CorrelatedModality("boards", "B", divisions.get("boards"), EBEXPixel.class.getField("boardGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("cables", "c", divisions.get("cables"), EBEXPixel.class.getField("cableGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("squidBoards", "q", divisions.get("squidsBoards"), EBEXPixel.class.getField("squidGroupGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("squids", "q", divisions.get("squids"),EBEXPixel.class.getField("squidGain") )); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
			
		modalities.get("boards").gainFlag = EBEXPixel.FLAG_BOARD;
		modalities.get("cables").gainFlag = EBEXPixel.FLAG_CABLE;
		modalities.get("squidBoards").gainFlag = EBEXPixel.FLAG_SQUIDBOARD;
		modalities.get("squids").gainFlag = EBEXPixel.FLAG_SQUID;
		
		((CorrelatedModality) modalities.get("boxes")).setSkipFlags(~(EBEXPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
		((CorrelatedModality) modalities.get("cables")).setSkipFlags(~(EBEXPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
		((CorrelatedModality) modalities.get("amps")).setSkipFlags(~(EBEXPixel.FLAG_RESISTOR | Channel.FLAG_SENSITIVITY));
	}
	
	@Override
	public String getTelescopeName() {
		return "EBEX";
	}
	
	@Override
	public EBEXPixel getChannelInstance(int backendIndex) {
		return new EBEXPixel(backendIndex);
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new EBEXScan(this);
	}

	
	// Wiring is read when divisions are created...
	@Override
	public void loadChannelData() {
		super.loadChannelData();
		
		// Read the wiring data here...
		if(hasOption("wiring.150")) {
			try { readWiring(option("wiring.150").getValue(), 150.0 * Unit.GHz); }
			catch(IOException e) { System.err.println("ERROR! Cannot parse " + option("wiring.150").getValue()); }
		}
		if(hasOption("wiring.250")) {
			try { readWiring(option("wiring.250").getValue(), 250.0 * Unit.GHz); }
			catch(IOException e) { System.err.println("ERROR! Cannot parse " + option("wiring.250").getValue()); }
		}
		if(hasOption("wiring.410")) {
			try { readWiring(option("wiring.410").getValue(), 410.0 * Unit.GHz); }
			catch(IOException e) { System.err.println("ERROR! Cannot parse " + option("wiring.410").getValue()); }
		}
	}
		
	@Override
	public Collection<? extends Pixel> getMappingPixels() {
		return getSkyChannels().discard(~0);
	}

	@Override
	public int getPixelCount() {
		return size();
	}

	@Override
	public Collection<? extends Pixel> getPixels() {
		return this;
	}
	
	public void readWiring(String fileName, double frequency) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		
		Hashtable<Integer, EBEXPixel> lookup = getChannelLookup();
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			
			boolean hasComment = tokens.countTokens() > 15;
			
			int padNo = Integer.parseInt(tokens.nextToken());
			int ucbNo = Integer.parseInt(tokens.nextToken());
			String tesName = tokens.nextToken();
			String type = tokens.nextToken();
			String lcPadName = tokens.nextToken();
			double R = Double.parseDouble(tokens.nextToken());
			double C = Double.parseDouble(tokens.nextToken()) * Unit.nF;
			double fExp = Double.parseDouble(tokens.nextToken()) * Unit.Hz;
			double fObs = Double.parseDouble(tokens.nextToken()) * Unit.Hz;
			boolean operational = tokens.nextToken().equalsIgnoreCase("y");
			String comment = hasComment ? tokens.nextToken() : "";
			int squidBoard = Integer.parseInt(tokens.nextToken(), 1);
			int squid = Integer.parseInt(tokens.nextToken(), 1);
			int board = Integer.parseInt(tokens.nextToken());		
			int wire = Integer.parseInt(tokens.nextToken(), 1);
			int pin = Integer.parseInt(tokens.nextToken(), 1);
			
			EBEXPixel pixel = lookup.get(EBEXPixel.getBackendIndex(board, wire, pin));
			
			pixel.frequency = frequency;
			pixel.umnPad = padNo;
			pixel.ucbPad = ucbNo;
			pixel.lcPad = lcPadName;
			StringTokenizer tes = new StringTokenizer(tesName);
			pixel.row = Integer.parseInt(tes.nextToken());
			pixel.col = Integer.parseInt(tes.nextToken());
			pixel.warmR = R;
			pixel.C = C;
			pixel.fMUX = fExp;
			pixel.fMUXexp = fObs;
			pixel.squidBoard = squidBoard;
			pixel.squid = squid;
			pixel.comment = comment;
			
			if(type.equals("resistor")) pixel.flag(EBEXPixel.FLAG_RESISTOR);
			else if(type.equals("eccosorb")) pixel.flag(EBEXPixel.FLAG_ECCOSORB);
			else if(type.equals("dark")) pixel.flag(Channel.FLAG_BLIND);
			
			if(!operational) pixel.flag(Channel.FLAG_DEAD);
		}
	
	
	}

	@Override
	public void readWiring(String fileName) throws IOException {
		// TODO Auto-generated method stub
		System.err.println("WARNING! Option 'wiring' not used. Use 'wiring.150', 'wiring.250' or 'wiring.410'.");
	}
	
}
