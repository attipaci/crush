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

package crush.array;

import java.io.*;
import java.util.*;

import crush.*;
import crush.sourcemodel.*;
import util.*;


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
		addModality(new GradientModality("gradients", "G", divisions.get("obs-channels"))); 
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
		for(Pixel pixel : getPixels()) table.put(pixel.getDataIndex(), pixel);
		return table;
	}
		
	@Override
	public String getDefaultSizeName() {
		return "arcsec";
	}

	@Override
	public double getDefaultSizeUnit() {
		return Unit.arcsec;
	}
	
	public abstract int maxPixels();
	
	@Override
	public SourceModel<?, ?> getSourceModelInstance() {
		if(hasOption("source.type")) {
			String type = option("source.type").getValue();
			if(type.equals("beammap")) return new BeamMap<Array<?, ?>, Scan<? extends Array<?,?>, ?>>(this);
			else return super.getSourceModelInstance();
		}
		else return super.getSourceModelInstance();
	}
	
	@Override
	public void loadChannelData() {
		if(hasOption("rcp")) {
			try { readRCP(Util.getSystemPath(option("rcp").getValue())); }
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
			pixel.gain = 0.0;
			pixel.coupling = 1.0;
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
						double gain = (columns == 3 || columns > 4) ? Double.parseDouble(tokens.nextToken()) : sourceGain;
						
						if(useGains) channel.gain = gain;
						if(sourceGain != 0.0) channel.unflag(Channel.FLAG_BLIND);
					}

					Vector2D position = pixel.getPosition();
					position.x = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
					position.y = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
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
	

	public void generateRCPFrom(String rcpFileName, String pixelFileName) throws IOException {
		readRCP(rcpFileName);
		loadPixelData(pixelFileName);
		printPixelRCP(System.out, null);
	}
	
	public void printPixelRCP(PrintStream out, String header)  throws IOException {
		out.println("# CRUSH Receiver Channel Parameter (RCP) Data File.");
		out.println("#");
		if(header != null) out.println(header);
		out.println("#");
		out.println("# ch\t[Gpnt]\t[Gsky]\tdX(\")\tdY(\")");
		
		for(Pixel pixel : getMappingPixels()) {
			out.print(pixel.getDataIndex() + "\t");
			if(pixel instanceof Channel) {
				Channel channel = (Channel) pixel;
				out.print(Util.f3.format(channel.gain * channel.coupling) + "\t");
				out.print(Util.f3.format(channel.gain) + "\t");
			}
			Vector2D position = pixel.getPosition();
			System.out.print(Util.f2.format(position.x / Unit.arcsec) + "  ");
			System.out.print(Util.f2.format(position.y / Unit.arcsec));
			out.println();
		}
	}
	
	
	public void flagInvalidPositions() {
		for(Pixel pixel : getPixels()) if(pixel.getPosition().length() > 1 * Unit.deg) 
			for(Channel channel : pixel) channel.flag(Channel.FLAG_BLIND);
	}
	
	
}
