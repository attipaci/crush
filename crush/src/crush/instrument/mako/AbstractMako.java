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
package crush.instrument.mako;

import crush.*;
import crush.array.DistortionModel;
import crush.telescope.cso.CSOCamera;
import jnum.Unit;
import jnum.math.Vector2D;
import nom.tam.fits.*;

import java.util.*;


public abstract class AbstractMako<MakoPixelType extends AbstractMakoPixel> extends CSOCamera<MakoPixelType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8482957165297427388L;
	
	protected Vector2D arrayPointingCenter;
	public Vector2D pixelSize = getDefaultPixelSize();
	int pixels;
	
	double nativeSamplingInterval;	

		
	// Information about the IQ -> shift calibration...
	int calPositions;
	int calParms;
	String calModelName;
	String calVersion;
	
	
	protected AbstractMako(String name, int npix) {
		super(name, npix);
		setResolution(8.5 * Unit.arcsec);
	}
	
	protected abstract Vector2D getDefaultPixelSize();
	
	protected abstract Vector2D getDefaultArrayPointingCenter();

	protected abstract Vector2D getDefaultArrayRotationCenter();
	
	public double getAreaFactor() {
		return pixelSize.x() * pixelSize.y() / (getDefaultPixelSize().x() * getDefaultPixelSize().y());
	}
	
	//protected abstract int pixelCount();
	
	@Override
	public AbstractMako<MakoPixelType> copy() {
		AbstractMako<MakoPixelType> copy = (AbstractMako<MakoPixelType>) super.copy();
		
		if(calModelName != null) copy.calModelName = new String(calModelName);
		if(calVersion != null) copy.calVersion = new String(calVersion);
		if(arrayPointingCenter != null) copy.arrayPointingCenter = arrayPointingCenter.copy();
		if(pixelSize != null) copy.pixelSize = pixelSize.copy();
		
		return copy;
	}
	

	public Hashtable<String, MakoPixelType> idLookup() {
		Hashtable<String, MakoPixelType> lookup = new Hashtable<String, MakoPixelType>(pixels);
		for(MakoPixelType pixel : this) if(pixel.id != null) lookup.put(pixel.getID(), pixel);
		return lookup;
	}
	
	@Override
    protected void loadChannelData() {
		arrayPointingCenter = getDefaultArrayPointingCenter();
		super.loadChannelData();
	}

	protected void calcPositions(Vector2D size) {
		pixelSize = size;
		// Make all pixels the same size. Also calculate their distortionless positions...
		for(AbstractMakoPixel pixel : this) pixel.calcNominalPosition();
		
		Vector2D center = new Vector2D(arrayPointingCenter.x() * pixelSize.x(), arrayPointingCenter.y() * pixelSize.y());
		
	
		if(hasOption("distortion")) {
			info("Correcting for focal-plane distortion.");
			DistortionModel model = new DistortionModel();
			model.setOptions(option("distortion"));	
			
			for(AbstractMakoPixel pixel : this) model.distort(pixel.getPosition());
			model.distort(new Vector2D(0.0, 0.0));
		}
		
		setReferencePosition(center);
	}
	
	protected abstract void parseDataHeader(Header header) throws HeaderCardException, FitsException;
	
	@Override
	public void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
		
		samplingInterval = integrationTime = 1.0 / header.getDoubleValue("SAMPLING");
		
		super.parseScanPrimaryHDU(hdu);
	}
		
	protected void parseReadoutHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		if(hasOption("chirp")) parseChirpReadoutHDU(hdu);
		else parseCalibrationHDU(hdu);
	}
	
	protected void parseCalibrationHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
	
		calPositions = header.getIntValue("CALPTS", 53);
		calParms = header.getIntValue("PARAMS", 0);
		calVersion = header.getStringValue("SOFTVER");
		calModelName = header.getStringValue("CALMODEL");
		double binWidth = header.getDoubleValue("BININHZ") * Unit.Hz;
		
		// read in the pixel data...
		pixels = hdu.getNRows();
		if(!isEmpty()) clear();
		ensureCapacity(pixels);
		
		int iBin = hdu.findColumn("Tone Bin");
		int iFlag = hdu.findColumn("Tone Flags");
		int iPts = hdu.findColumn("Points");
		int iErr = hdu.findColumn("Fit Error");
		
		info("MAKO stream has " + pixels + " tones. ");
		
		int blinds = 0;
		
		for(int c=0; c<pixels; c++) {
			MakoPixelType pixel = getChannelInstance(c);
			Object[] row = hdu.getRow(c);
			
			pixel.toneBin = ((int[]) row[iBin])[0];
			pixel.toneFrequency = binWidth * pixel.toneBin;	
			pixel.validCalPositions = ((int[]) row[iPts])[0];
			pixel.calError = ((float[]) row[iErr])[0];
			
			if(iFlag >= 0) if(((int[]) row[iFlag])[0] != 0) {
				pixel.flag(Channel.FLAG_BLIND);
				blinds++;
			}
			else add(pixel);
		}	
		
		if(iFlag < 0) warning("Data has no information on blind tones.");
		else if(blinds > 0) warning("Ignoring " + blinds + " blind tones.");
		else warning("Stream contains no blind tones :-).");
		
	}
	
	protected void parseChirpReadoutHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {			
		//int iFreq = hdu.findColumn("Resonance Frequency");
		
		float[] freqs = (float[]) hdu.getRow(0)[0];
			
		// read in the pixel data...
		pixels = freqs.length;
		if(!isEmpty()) clear();
		ensureCapacity(pixels);
			
		info("MAKO chirp stream has " + pixels + " resonances. ");
		
		for(int c=0; c<pixels; c++) {
			MakoPixelType pixel = getChannelInstance(c);
			
			pixel.toneFrequency = freqs[c];
			add(pixel);
		}	
			
	}
	
	@Override
	public abstract int maxPixels();
	
	
	
	
	@Override
	public String getCommonHelp() {
		return super.getCommonHelp() + 
				"     -fazo=        Correct the pointing with this FAZO value.\n" +
				"     -fzao=        Correct the pointing with this FZAO value.\n";
	}
	
	@Override
	public String getRCPHeader() {
		return "KIDfreq[Hz]\tdX[\"]\tdY[\"]\tsrcgain";
		//return super.getRCPHeader() + "\tKIDfreq"; 
	}

	public static int DEFAULT_ARRAY = 0;
	
}

