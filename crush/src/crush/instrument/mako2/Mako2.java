/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.mako2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import crush.CRUSH;
import crush.Channel;
import crush.Scan;
import crush.instrument.mako.AbstractMako;
import crush.instrument.mako.AbstractMakoPixel;
import crush.instrument.mako.MakoPixel;
import crush.resonators.ResonatorList;
import jnum.Unit;
import jnum.math.Vector2D;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;

public class Mako2 extends AbstractMako<Mako2Pixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3325545547718854921L;
	
	public Vector2D offset350 = new Vector2D(-10.38, 0.0);	// rel. to 350um pixel scale...
	public Vector2D offset850 = new Vector2D(20.46, 0.0); // rel. to 350um pixel scale...
	
	public double scale850 = 1.92;	// 850um pixel separation rel. to 350um separation.
	
	Mako2PixelMatch identifier;     // TODO How to copy() ?
	double meanResonatorShift350;
	double meanResonatorShift850;
	
	public Mako2() {
		super("mako2", rows350 * cols350 + rows850 * cols850);
	}

	
	@Override
	public Mako2Pixel getChannelInstance(int backendIndex) {
		return new Mako2Pixel(this, backendIndex);
	}	
	
	@Override
    public Mako2 copy() {
	    Mako2 copy = (Mako2) super.copy();
	    copy.offset350 = offset350.copy();
	    copy.offset850 = offset850.copy();
	    return copy;
	}
	
	@Override
	public Scan<?, ?> getScanInstance() {
		return new Mako2Scan(this);
	}
	
	public List<Mako2Pixel> get350Pixels() {
		ArrayList<Mako2Pixel> list = new ArrayList<Mako2Pixel>(pixels350());
		for(Mako2Pixel pixel : this) if(pixel.array == ARRAY_350) list.add(pixel);
		return list;
	}
	
	public List<Mako2Pixel> get850Pixels() {
		ArrayList<Mako2Pixel> list = new ArrayList<Mako2Pixel>(pixels850());
		for(Mako2Pixel pixel : this) if(pixel.array == ARRAY_850) list.add(pixel);
		return list;
	}

	
	@Override
    protected void loadChannelData() {
		if(hasOption("pixelsize.scale850")) scale850 = option("pixelsize.scale850").getDouble();
		if(hasOption("offset.350")) offset350 = option("offset.350").getVector2D();
		if(hasOption("offset.850")) offset350 = option("offset.850").getVector2D();
		
		// Assign the pixels to the 350um or 850um subarrays based on KID frequency...
		double max850Frequency = hasOption("850um.maxfreq") ? option("850um.maxfreq").getDouble() * Unit.MHz : 100.0 * Unit.MHz;
		for(Mako2Pixel pixel : this) pixel.array = pixel.toneFrequency > max850Frequency ? Mako2.ARRAY_350 : Mako2.ARRAY_850;
		
		
		if(hasOption("pixelid")) {
			try {
				List<Mako2Pixel> pixels350 = hasOption("350um") ? get350Pixels() : null;
				List<Mako2Pixel> pixels850 = hasOption("850um") ? get850Pixels() : null;
				
				identifier = new Mako2PixelMatch(option("pixelid"));
				ResonatorList<Mako2Pixel> resonators = null;
				
				if(pixels350 != null) {
					identifier.discardBelow(max850Frequency);
					resonators = new ResonatorList<Mako2Pixel>(pixels350);
					if(!identifier.isEmpty()) meanResonatorShift350 = identifier.match(resonators);
				}
				if(pixels850 != null) {
					identifier.discardAbove(max850Frequency);
					resonators = new ResonatorList<Mako2Pixel>(pixels850);
					if(!identifier.isEmpty()) meanResonatorShift850 = identifier.match(resonators);
				}
				
				realign();
			}
			catch(IOException e) {
				warning("Cannot identify tones from '" + option("pixelid").getValue() + "'."); 
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}
		else {
			// TODO why are pixels flagged other than unassigned...
			boolean pixelmap = false;
			if(hasOption("source.type")) if(option("source.type").getValue().equalsIgnoreCase("pixelmap")) pixelmap = true;
			
			if(!pixelmap) warning("No pixel ids. All pixels mapped to tracking position...");
		
			for(AbstractMakoPixel pixel : this) {
				pixel.position = new Vector2D();
				pixel.unflag(AbstractMakoPixel.FLAG_UNASSIGNED | AbstractMakoPixel.FLAG_NOTONEID);
			}
		}
		
		
		// Update the pointing center...
		updateArrayPointingCenter();

		checkRotation(); 
		
		super.loadChannelData();
		
		boolean map850um = hasOption("850um");
		info("Map with " + (map850um ? "850um" : "350um") + " subarray only.");
		
		
		for(Mako2Pixel pixel : this) {
			if(map850um) {
				if(pixel.array != ARRAY_850) pixel.flag(Channel.FLAG_DISCARD);
			}
			else {
				if(pixel.array != ARRAY_350) pixel.flag(Channel.FLAG_DISCARD);
			}
		}
		
		
		
	}
	
	public void realign() {
		if(hasOption("pixelid.center")) {
			Vector2D offset = option("pixelid.center").getVector2D();
			offset.scale(Unit.arcsec);
			for(Mako2Pixel pixel : this) if(pixel.position != null) pixel.position.subtract(offset);
		}
		if(hasOption("pixelid.zoom")) {
			double zoom = option("pixelid.zoom").getDouble();
			for(Mako2Pixel pixel : this) if(pixel.position != null) pixel.position.scale(zoom);
		}
		if(hasOption("pixelid.mirror")) {
			for(Mako2Pixel pixel : this) if(pixel.position != null) pixel.position.scaleX(-1.0);
		}
		if(hasOption("pixelid.rotate")) {
			double angle = option("pixelid.rotate").getDouble() * Unit.deg;
			for(Mako2Pixel pixel : this) if(pixel.position != null) pixel.position.rotate(angle);
		}	
	
	}
	
	@Override
	public Vector2D getPointingCenterOffset() {
		// Update the rotation center...
		Vector2D arrayRotationCenter = getDefaultArrayRotationCenter();
		if(hasOption("rcenter")) {
			arrayRotationCenter = option("rcenter").getVector2D();
			arrayRotationCenter.scale(Unit.arcsec);
		}
	
		return new Vector2D(arrayPointingCenter.x() * pixelSize.x() - arrayRotationCenter.x(), arrayPointingCenter.y() * pixelSize.y() - arrayRotationCenter.y());
	}
	
	
	@Override
	protected Vector2D getDefaultArrayPointingCenter() {
		if(hasOption("pointing.mode")) return getDefaultArrayPointingCenter(option("pointing.mode").getValue());
		return getDefaultArrayPointingCenter("350");
	}
	
	private Vector2D getDefaultArrayPointingCenter(String mode) {
		mode = mode.toLowerCase();
		if(mode.equals("350")) return offset350;
		else if(mode.equals("850")) return offset850;
		else if(mode.equals("dual")) return new Vector2D();
		else return new Vector2D();		
	}
	
	protected void updateArrayPointingCenter() {
		if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
		if(hasOption("pointing.mode")) {
			String mode = option("pointing.mode").getValue().toLowerCase();
			if(mode.equals("350")) arrayPointingCenter.add(offset350);
			else if(mode.equals("850")) arrayPointingCenter.add(offset850);
		}
	}
	
	@Override
	protected Vector2D getDefaultArrayRotationCenter() {
		return getDefaultArrayPointingCenter();
	}

	@Override
	public int maxPixels() {
		return rows350 * cols350 + rows850 * cols850;
	}
	

	public Vector2D getPixelPosition(Vector2D size, int array, int row, int col) {	
		if(array == ARRAY_350) {
			Vector2D pos = new Vector2D(size.x() * (col - 0.5 * (Mako2.cols350-1)), size.y() * (row - 0.5 * (Mako2.rows350-1)));
			if((col & 1) == 1) pos.addY(0.25 * size.y());
			else pos.subtractY(0.25 * size.y());
			pos.addX(size.x() * offset350.x());
			pos.addY(size.y() * offset350.y());
			return pos;
		}
		
		if(array == ARRAY_850) {
			Vector2D pos = new Vector2D(size.x() * (col - 0.5 * (Mako2.cols850-1)), size.y() * (row - 0.5 * (Mako2.rows850-1)));
			if((col & 1) == 1) pos.addY(0.25 * size.y());
			else pos.subtractY(0.25 * size.y());
			pos.scale(scale850);
			pos.addX(size.x() * offset850.x());
			pos.addY(size.y() * offset850.y());
			return pos;
		}
		return null;
	}
	
	@Override
	public String getChannelDataHeader() {
		return "pixelid\tarray\t" + super.getChannelDataHeader() + "\teff";
	}


	@Override
	public Object getTableEntry(String name) {
		if(name.equals("df350")) return meanResonatorShift350;
		if(name.equals("df850")) return meanResonatorShift850;
		return super.getTableEntry(name);
	}

	
	@Override
	protected void parseDataHeader(Header header) throws HeaderCardException, FitsException {
		// TODO
		
		/*
		// Pointing Center
		arrayPointingCenter = new Vector2D();
		arrayPointingCenter.setX(header.getDoubleValue("CRPIX3", 0.0));
		arrayPointingCenter.setY(header.getDoubleValue("CRPIX2", 0.0));
		*/
	}
	
	@Override
	protected Vector2D getDefaultPixelSize() {
		return MakoPixel.defaultSize;
	}
	
	public static int pixels350() {
		return rows350 * cols350;
	}
	
	public static int pixels850() {
		return rows850 * cols850;
	}
	
	
	public static int rows350 = 20;
	public static int cols350 = 24;

	public static int rows850 = 10;
	public static int cols850 = 12;
	
	public static final int ARRAY_350 = 0;
	public static final int ARRAY_850 = 1;

	@Override
	public double getLoadTemperature() {
		return Double.NaN;
	}
}

