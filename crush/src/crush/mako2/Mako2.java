/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.mako2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import crush.CRUSH;
import crush.Channel;
import crush.Scan;
import crush.array.DistortionModel;
import crush.mako.Mako;
import crush.mako.MakoPixel;
import crush.mako.MakoScan;
import crush.mako.ResonanceList;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import kovacs.math.Vector2D;
import kovacs.text.TableFormatter;
import kovacs.util.Unit;
import kovacs.util.Util;

public class Mako2 extends Mako<Mako2Pixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3325545547718854921L;
	
	public Vector2D offset350 = new Vector2D(-10.38, 0.0);	// rel. to 350um pixel scale...
	public Vector2D offset850 = new Vector2D(20.46, 0.0); // rel. to 350um pixel scale...
	
	public double scale850 = 1.92;	// 850um pixel separation rel. to 350um separation.
	
	ToneIdentifier2 identifier;
	double meanResonatorShift350;
	double meanResonatorShift850;
	
	public Mako2() {
		super("mako2", rows350 * cols350 + rows850 * cols850);
	}
	
	
	@Override
	public void initialize() {
		super.initialize();
		
	}
	
	@Override
	public Mako2Pixel getChannelInstance(int backendIndex) {
		return new Mako2Pixel(this, backendIndex);
	}	
	
	@Override
	public Scan<?, ?> getScanInstance() {
		return new MakoScan<Mako2>(this);
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
	public void loadChannelData() {
		if(hasOption("pixelsize.scale850")) scale850 = option("pixelsize.scale850").getDouble();
		if(hasOption("offset.350")) offset350 = option("offset.350").getVector2D();
		if(hasOption("offset.850")) offset350 = option("offset.850").getVector2D();
		
		double max850Frequency = hasOption("850um.maxfreq") ? option("850um.maxfreq").getDouble() * Unit.mHz : 100.0 * Unit.mHz;
		for(Mako2Pixel pixel : this) pixel.array = pixel.toneFrequency > max850Frequency ? Mako2.ARRAY_350 : Mako2.ARRAY_850;
		
		
		if(hasOption("toneid")) {
			try {
				
				List<Mako2Pixel> pixels350 = get350Pixels();
				List<Mako2Pixel> pixels850 = get850Pixels();
				
				if(pixels350 != null) {
					identifier = new ToneIdentifier2(option("toneid"));
					identifier.discardBelow(max850Frequency);
					meanResonatorShift350 = identifier.match(new ResonanceList<Mako2Pixel>(pixels350));
				}
				if(pixels850 != null) {
					identifier = new ToneIdentifier2(option("toneid"));
					identifier.discardAbove(max850Frequency);
					meanResonatorShift850 = identifier.match(new ResonanceList<Mako2Pixel>(pixels850));
				}
				
				realign();
			}
			catch(IOException e) {
				System.err.println(" WARNING! Cannot identify tones from '" + option("toneid").getValue() + "'."); 
				if(CRUSH.debug) e.printStackTrace();
			}
		}
		else {
			// TODO why are pixels flagged other than unassigned...
			System.err.println("!!! No tone ids. Maps are bogus...");
			//for(MakoPixel pixel : this) pixel.unflag(MakoPixel.FLAG_UNASSIGNED);
			for(MakoPixel pixel : this) pixel.unflag();
		}
		
		// Do not flag unassigned pixels when beam-mapping...
		if(hasOption("source.type")) if(option("source.type").equals("beammap")) 
				for(MakoPixel pixel : this) pixel.unflag(MakoPixel.FLAG_UNASSIGNED);
		
		// Update the pointing center...
		updateArrayPointingCenter();

		checkRotation();
		
		super.loadChannelData();
		
		boolean map850um = hasOption("850um");
		System.err.println("Map with " + (map850um ? "850um" : "350um") + " subarray only.");
		
		
		for(Mako2Pixel pixel : this) {
			if(map850um) {
				if(pixel.array != ARRAY_850) pixel.flag(Channel.FLAG_DEAD);
			}
			else {
				if(pixel.array != ARRAY_350) pixel.flag(Channel.FLAG_DEAD);
			}
		}
		
		
	}
	
	public void realign() {
		if(hasOption("toneid.center")) {
			Vector2D offset = option("toneid.center").getVector2D();
			offset.scale(Unit.arcsec);
			for(Mako2Pixel pixel : this) if(pixel.position != null) pixel.position.subtract(offset);
		}
		if(hasOption("toneid.zoom")) {
			double zoom = option("toneid.zoom").getDouble();
			for(Mako2Pixel pixel : this) if(pixel.position != null) pixel.position.scale(zoom);
		}
		if(hasOption("toneid.mirror")) {
			for(Mako2Pixel pixel : this) if(pixel.position != null) pixel.position.scaleX(-1.0);
		}
		if(hasOption("toneid.rotate")) {
			double angle = option("toneid.rotate").getDouble() * Unit.deg;
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
	protected void calcPositions(Vector2D size) {
		pixelSize = size;
		// Make all pixels the same size. Also calculate their distortionless positions...
		for(MakoPixel pixel : this) {
			pixel.size = size;
			pixel.calcNominalPosition();
		}
		
		Vector2D center = new Vector2D(arrayPointingCenter.x() * pixelSize.x(), arrayPointingCenter.y() * pixelSize.y());
		
	
		if(hasOption("distortion")) {
			System.err.println(" Correcting for focal-plane distortion.");
			DistortionModel model = new DistortionModel();
			model.setOptions(option("distortion"));	
			
			for(MakoPixel pixel : this) model.distort(pixel.getPosition());
			model.distort(new Vector2D(0.0, 0.0));
		}
		
		setReferencePosition(center);
	}

	
	@Override
	protected Vector2D getDefaultArrayPointingCenter() {
		if(hasOption("pointing.mode")) return getDefaultArrayPointingCenter(option("pointing.mode").getValue());
		else return getDefaultArrayPointingCenter("350");
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
		return "toneid\tarray\t" + super.getChannelDataHeader() + "\teff";
	}


	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		if(name.equals("df350")) return Util.defaultFormat(meanResonatorShift350, TableFormatter.getNumberFormat(formatSpec));
		if(name.equals("df850")) return Util.defaultFormat(meanResonatorShift850, TableFormatter.getNumberFormat(formatSpec));
		else return super.getFormattedEntry(name, formatSpec);
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

