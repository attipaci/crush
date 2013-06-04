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
package crush.array;

import java.io.*;
import java.util.*;

import kovacs.util.*;

import crush.*;
import crush.sourcemodel.*;


public abstract class Array<PixelType extends Pixel, ChannelType extends Channel> extends Instrument<ChannelType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -707752417431510013L;
	
	public Array(String name, int size) {
		super(name, size);
	}
	
	@Override
	public void addModalities() {
		super.addModalities(); 
		
		CorrelatedMode common = (CorrelatedMode) modalities.get("obs-channels").get(0);
			
		CorrelatedMode gx = common.new CoupledMode(new SkyGradient.X());
		gx.name = "gradients:x";
		CorrelatedMode gy = common.new CoupledMode(new SkyGradient.Y());
		gy.name = "gradients:y";
		
		CorrelatedModality gradients = new CorrelatedModality("gradients", "G");
		gradients.add(gx);
		gradients.add(gy);
		
		addModality(gradients);
	}

	public Array(String name) {
		super(name);
	}
	
	public void setReferencePosition(Vector2D position) {
		Vector2D referencePosition = (Vector2D) position.clone();
		for(Pixel pixel : getPixels()) pixel.getPosition().subtract(referencePosition);
	}
	
	public Hashtable<Integer, Pixel> getPixelLookup() {
		Hashtable<Integer, Pixel> table = new Hashtable<Integer, Pixel>();
		for(Pixel pixel : getPixels()) table.put(pixel.getFixedIndex(), pixel);
		return table;
	}
		
	@Override
	public String getSizeName() {
		return "arcsec";
	}

	@Override
	public double getSizeUnit() {
		return Unit.arcsec;
	}
	
	public abstract int maxPixels();
	
	@Override
	public SourceModel getSourceModelInstance() {
		if(hasOption("source.type")) {
			String type = option("source.type").getValue();
			if(type.equals("beammap")) return new BeamMap(this);
			else return super.getSourceModelInstance();
		}
		else return super.getSourceModelInstance();
	}
	
	@Override
	public void loadChannelData() {	
		if(hasOption("rcp")) {
			try { readRCP(option("rcp").getPath()); }
			catch(IOException e) { System.err.println("WARNING! Cannot update pixel RCP data. Using values from FITS."); }
		}
		
		super.loadChannelData();
	}
		
	
	public void readRCP(String fileName)  throws IOException {		
		System.err.println(" Reading RCP from " + fileName);
			
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;

		// Channels not in the RCP file are assumed to be blind...
		for(ChannelType pixel : this) {
			pixel.flag(Channel.FLAG_BLIND);
		}
		
		Hashtable<Integer, Pixel> backends = getPixelLookup(); 
		boolean useGains = hasOption("rcp.gains");
			
		if(useGains) System.err.println(" Initial Source Gains set from RCP file.");
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(!"#!/".contains(line.charAt(0) + "")) {
			StringTokenizer tokens = new StringTokenizer(line);
			int columns = tokens.countTokens();
			Pixel pixel = backends.get(Integer.parseInt(tokens.nextToken()));
			if(pixel != null) {
				try {
					if(pixel instanceof Channel) {
						Channel channel = (Channel) pixel;
						double sourceGain = Double.parseDouble(tokens.nextToken());
						double coupling = (columns == 3 || columns > 4) ? sourceGain / Double.parseDouble(tokens.nextToken()) : sourceGain / channel.gain;
						
						if(useGains) channel.coupling = coupling;
						if(sourceGain != 0.0) channel.unflag(Channel.FLAG_BLIND);
					}

					Vector2D position = pixel.getPosition();
					position.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
					position.setY(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
				}
				catch(NumberFormatException e){}
			}
			flagInvalidPositions();
		}
		in.close();
		
		if(hasOption("rcp.center")) {
			Vector2D offset = option("rcp.center").getVector2D();
			offset.scale(Unit.arcsec);
			for(Pixel pixel : getPixels()) pixel.getPosition().subtract(offset);
		}
		
		if(hasOption("rcp.rotate")) {
			double angle = option("rcp.rotate").getDouble() * Unit.deg;
			for(Pixel pixel : getPixels()) pixel.getPosition().rotate(angle);
		}
		
		if(hasOption("rcp.zoom")) {
			double zoom = option("rcp.zoom").getDouble();
			for(Pixel pixel : getPixels()) pixel.getPosition().scale(zoom);
		}
		
	}
	
	public String getRCPHeader() { return "ch\t[Gpnt]\t[Gsky]ch\t[dX\"]\t[dY\"]"; }
	
	public void printPixelRCP(PrintStream out, String header)  throws IOException {
		out.println("# CRUSH Receiver Channel Parameter (RCP) Data File.");
		out.println("#");
		if(header != null) out.println(header);
		out.println("#");
		out.println("# " + getRCPHeader());
		
		for(Pixel pixel : getMappingPixels()) if(pixel.getPosition() != null)
			if(!pixel.getPosition().isNaN()) out.println(pixel.getRCPString());
	}

	public void generateRCPFrom(String rcpFileName, String pixelFileName) throws IOException {
		readRCP(rcpFileName);
		loadChannelData(pixelFileName);
		printPixelRCP(System.out, null);
	}
	
	public void flagInvalidPositions() {
		for(Pixel pixel : getPixels()) if(pixel.getPosition().length() > 1 * Unit.deg) 
			for(Channel channel : pixel) channel.flag(Channel.FLAG_BLIND);
	}
	
	protected void setPointing(Scan<?,?> scan) {
		if(hasOption("point")) return;
		System.err.println(" Setting 'point' option to obtain pointing/calibration data.");
		options.parse("point");
		scan.instrument.options.parse("point");		
	}
	
}
