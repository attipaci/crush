/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.mustang2;

import java.io.IOException;

import kovacs.math.Vector2D;
import kovacs.util.Unit;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import crush.Channel;
import crush.CorrelatedModality;
import crush.GroundBased;
import crush.Instrument;
import crush.Mount;
import crush.Scan;
import crush.array.Array;
import crush.array.SingleColorLayout;

public class Mustang2 extends Array<Mustang2Pixel, Mustang2Pixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8027132292040380342L;

	Mustang2Readout[] readout;
	double focus;
	
	// TODO ? Vector2D arrayPointingCenter
	
	public Mustang2() {
		super("mustang2", new SingleColorLayout<Mustang2Pixel>(), maxReadouts * maxReadoutPixels);	
		setResolution(6.2 * Unit.arcsec);
		
		mount = Mount.CASSEGRAIN;
	}
	
	@Override
	public Instrument<Mustang2Pixel> copy() {
		Mustang2 copy = (Mustang2) super.copy();
		if(readout != null) {
			copy.readout = new Mustang2Readout[readout.length];
			for(int i=readout.length; --i >= 0; ) copy.readout[i] = readout[i].copy();
		}
		return copy;
	}
	
	
	@Override
	public void initDivisions() {
		super.initDivisions();
		
		try { addDivision(getDivision("mux", Mustang2Pixel.class.getField("readoutIndex"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
	}
	
	@Override
	public void initModalities() {
		super.initModalities();
			
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), Mustang2Pixel.class.getField("readoutGain"));		
			muxMode.setGainFlag(Mustang2Pixel.FLAG_MUX);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { e.printStackTrace(); }	
		
	}
	

	@Override
	public int maxPixels() {
		return maxReadouts * maxReadoutPixels;
	}

	@Override
	public String getTelescopeName() {
		return "GBT";
	}

	@Override
	public void readWiring(String fileName) throws IOException {
		// TODO Auto-generated method stub
	}

	@Override
	public Mustang2Pixel getChannelInstance(int backendIndex) {
		return new Mustang2Pixel(this, backendIndex);
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new Mustang2Scan(this);
	}
	
	
	public void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException {
		Header header = hdu.getHeader();
		
		//readout = new Mustang2Readout[header.getIntValue("NROACHES", 1)];
		focus = header.getDoubleValue("LFCY", Double.NaN) * Unit.mm;
		
		// TODO read from FITS when available...
		samplingInterval = integrationTime = 1.0 * Unit.ms;
	}
	
	public void parseHardwareHDU(BinaryTableHDU hdu) throws FitsException {
		Object[] data = hdu.getRow(0);
		
		byte[] row = (byte[]) data[hdu.findColumn("ROW")];
		byte[] col = (byte[]) data[hdu.findColumn("COL")];
		float[] f0 = (float[]) data[hdu.findColumn("RESFREQ")];
		float[] atten = (float[]) data[hdu.findColumn("ATTEN")];
		float[] bias = (float[]) data[hdu.findColumn("DETBIAS")];
		float[] heater = (float[]) data[hdu.findColumn("DETHEATERS")];
		float[] x = (float[]) data[hdu.findColumn("DETOFFX")];
		float[] y = (float[]) data[hdu.findColumn("DETOFFY")];
		float[] weight = (float[]) data[hdu.findColumn("DETWT")];
		
		readout = new Mustang2Readout[bias.length];
		
		for(int i=0; i<readout.length; i++) {
			Mustang2Readout r = new Mustang2Readout(i);
			r.bias = bias[i];
			r.heater = heater[i];
			readout[i] = r;
		}
			
		int pixels = f0.length;
		System.err.println(" Camera has " + pixels + " active pixels.");
		clear();
		ensureCapacity(pixels);
		
		for(int i=0; i<pixels; i++) {
			Mustang2Pixel pixel = new Mustang2Pixel(this, (i+1));
			pixel.weight = weight[i];
			pixel.frequency = f0[i] * Unit.mHz;
			pixel.attenuation = atten[i];
			pixel.readoutIndex = col[i]; 
			pixel.muxIndex = row[i];
			pixel.position = new Vector2D(x[i] * Unit.arcsec, y[i] * Unit.arcsec);
			if(pixel.position.length() == 0.0) pixel.flag(Channel.FLAG_DEAD);
			add(pixel);
		}
		
	
		if(hasOption("rotate")) {
			double angle = option("rotate").getDouble() * Unit.deg;
			for(Mustang2Pixel pixel : this) pixel.position.rotate(angle);
		}
		
		if(hasOption("flip")) for(Mustang2Pixel pixel : this) pixel.position.scaleX(-1.0);
			
		if(hasOption("zoom")) {
			double factor = option("zoom").getDouble();
			for(Mustang2Pixel pixel : this) pixel.position.scaleY(factor);
		}
		
		
	}
	
	
	
	
	public static int maxReadouts = 7;
	public static int maxReadoutPixels = 36;
}
