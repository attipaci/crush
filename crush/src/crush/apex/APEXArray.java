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
// Copyright Copyright (c)  2010 Attila Kovacs

package crush.apex;

import crush.*;
import crush.array.MonoArray;
import crush.array.SimplePixel;
import nom.tam.fits.*;

import java.io.*;
import java.util.Vector;

import util.Unit;
import util.Util;
import util.Vector2D;
import util.astro.EquatorialCoordinates;

public abstract class APEXArray<ChannelType extends APEXPixel> extends MonoArray<ChannelType> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4654355516742128832L;

	public ChannelType referencePixel;
	public double rotation = 0.0;
	
	public APEXArray(String name, int size) {
		super(name, size);
	}

	public APEXArray(String name) {
		super(name);
	}
	
	@Override
	public String getTelescopeName() {
		return "APEX";
	}

	@Override
	public void readRCP(String fileName)  throws IOException {		
		super.readRCP(fileName);
	
		setReferencePosition(referencePixel.position);	
		// Take dewar rotation into account...
		for(SimplePixel pixel : this) pixel.position.rotate(rotation);
	}
	

	public void readPar(String fileName) throws IOException, FitsException, HeaderCardException {
		Fits fits = new Fits(new File(fileName), fileName.endsWith(".gz"));

		BinaryTableHDU hdu = (BinaryTableHDU) fits.getHDU(1);
		readPar(hdu);
		
		fits.getStream().close();
	}
	
	public void readPar(BinaryTableHDU hdu) throws IOException, FitsException, HeaderCardException {
		Header header = hdu.getHeader();

		Object[] row = hdu.getRow(0);

		double[] xOffset = (double[]) row[hdu.findColumn("FEEDOFFX")];
		double[] yOffset = (double[]) row[hdu.findColumn("FEEDOFFY")];
	
		// Read in the instrument location...
		String cabin = header.getStringValue("DEWCABIN").toLowerCase();
		if(cabin.startsWith("c")) mount = Mount.CASSEGRAIN;
		else if(cabin.startsWith("a") || cabin.equals("nasmyth_a")) mount = Mount.LEFT_NASMYTH;
		else if(cabin.startsWith("b") || cabin.equals("nasmyth_b")) mount = Mount.RIGHT_NASMYTH;
		else throw new IllegalStateException("WARNING! Instrument Cabin Undefined.");
			
		System.err.println(" Instrument mounted in " + mount.name + " cabin.");
		
		rotation = header.getDoubleValue("DEWUSER", 0.0) - header.getDoubleValue("DEWZERO", 0.0);
		if(rotation != 0.0) {
			System.err.println(" Dewar rotated at " + rotation + " deg.");
			rotation *= Unit.deg;
		}
		
		storeChannels = xOffset.length;
		clear();
		ensureCapacity(storeChannels);

	
		for(int c=0; c<storeChannels; c++) {
			final ChannelType pixel = getChannelInstance(c+1);
			pixel.index = c;
			pixel.fitsPosition = new Vector2D(xOffset[c] * Unit.deg, yOffset[c] * Unit.deg);
			pixel.position = (Vector2D) pixel.fitsPosition.clone();
			add(pixel);
		}
		
		flagInvalidPositions();
		
		int referenceBENumber = ((int[]) row[hdu.findColumn("REFFEED")])[0];
		referencePixel = get(referenceBENumber-1);
		setReferencePosition(referencePixel.fitsPosition);
		System.err.println(" " + storeChannels + " channels found. Reference pixel is " + referenceBENumber);

		// Take instrument rotation into account
		for(SimplePixel pixel : this) pixel.position.rotate(rotation);
		
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new APEXArrayScan<APEXArray<?>, APEXArraySubscan<APEXArray<?>,?>>(this);
	}

	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		
		APEXArrayScan<?,?> scan = (APEXArrayScan<?,?>) scans.get(0);
		APEXArraySubscan<?,?> subscan = scan.get(0);
		EquatorialCoordinates reference = scan.equatorial;
		String sourceName = scan.sourceName;
		
		double pointingTolerance = resolution / 5.0;
		
		boolean isChopped = subscan.chopper != null;
		
		if(isChopped) {
			System.err.println("Setting chopped reduction mode.");
			System.err.println("Target is [" + scan.sourceName + "] at " + reference.toString());
		}
		options.parse("chopped");
		
		
		for(int i=scans.size(); --i >=0; ) {
			scan = (APEXArrayScan<?,?>) scans.get(i);
			subscan = scan.get(0);
			
			if((subscan.chopper != null) != isChopped) {		
				System.err.println("  WARNING! Scan " + scan.serialNo + " is not a chopped scan. Dropping from dataset.");
				scans.remove(i);
			}
			else if(!scan.isPlanetary) {
				if(isChopped && scan.equatorial.distanceTo(reference) > pointingTolerance) {
					System.err.println("  WARNING! Scan " + scan.serialNo + " observed at a different position. Dropping from dataset.");
					scans.remove(i);
				}
			}
			else if(!scan.sourceName.equalsIgnoreCase(sourceName)) {
				System.err.println("  WARNING! Scan " + scan.serialNo + " is on a different object. Dropping from dataset.");
				scans.remove(i);
			}
		}
		
		super.validate(scans);		
	}
		
	@Override
	public void readWiring(String fileName) throws IOException {		
	}

	@Override
	public int maxPixels() {
		return storeChannels;
	}
	
	@Override
	public String getPointingString(Scan<?,?> scan, Vector2D pointing) {
		return super.getPointingString(scan, pointing) + "\n\n" +
			"  >>> pcorr " + Util.f1.format(pointing.x / Unit.arcsec) + "," + Util.f1.format(pointing.y / Unit.arcsec);
		
	}
	
	@Override
	public SourceModel<?, ?> getSourceModelInstance() {
		if(hasOption("chopped")) return new APEXChoppedPhotometry<APEXArray<?>, APEXArrayScan<APEXArray<?>,?>>(this);
		else return super.getSourceModelInstance();
	}
	
}
