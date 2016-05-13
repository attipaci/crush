/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import crush.array.Camera;
import crush.array.SingleColorPixel;
import jnum.Unit;
import jnum.Util;
import jnum.astro.EquatorialCoordinates;
import jnum.math.Vector2D;
import jnum.text.TableFormatter;
import crush.array.SingleColorArrangement;
import nom.tam.fits.*;

import java.io.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Vector;


public abstract class APEXCamera<ChannelType extends APEXContinuumPixel> extends Camera<ChannelType, ChannelType> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4654355516742128832L;

	public ChannelType referencePixel;
	public double rotation = 0.0;
	
	public APEXCamera(String name, int size) {
		super(name, new SingleColorArrangement<APEXContinuumPixel>(), size);
	}
	
	public APEXCamera(String name) {
		super(name, new SingleColorArrangement<APEXContinuumPixel>());
	}
	
	
	@Override
	public String getTelescopeName() {
		return "APEX";
	}

	@Override
	public void readRCP(String fileName)  throws IOException {		
		super.readRCP(fileName);
	
		if(referencePixel != null) setReferencePosition(referencePixel.position);	
		// Take dewar rotation into account...
		for(SingleColorPixel pixel : this) pixel.position.rotate(rotation);
	}
	

	public void readPar(String fileName) throws IOException, FitsException, HeaderCardException {
		Fits fits = new Fits(new File(fileName), fileName.endsWith(".gz"));

		BinaryTableHDU hdu = (BinaryTableHDU) fits.getHDU(1);
		readPar(hdu);
		
		fits.close();
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
			final ChannelType pixel = getChannelInstance(c);
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
		for(SingleColorPixel pixel : this) pixel.position.rotate(rotation);
		
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new APEXScan<APEXCamera<?>, APEXSubscan<APEXCamera<?>,?>>(this);
	}

	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		
		final APEXScan<?,?> firstScan = (APEXScan<?,?>) scans.get(0);
		final APEXSubscan<?,?> firstSubscan = firstScan.get(0);
		final EquatorialCoordinates reference = firstScan.equatorial;
		final String sourceName = firstScan.getSourceName();
		
		final double pointingTolerance = getPointSize() / 5.0;
		
		final boolean isChopped = firstSubscan.getChopper() != null;
		
	
		if(isChopped) {
			System.err.println(" Chopped photometry reduction mode.");
			System.err.println(" Target is [" + sourceName + "] at " + reference.toString());
			setOption("chopped");
		}
		else if(sourceName.equalsIgnoreCase("SKYDIP")) {
			System.err.println(" Skydip reduction mode.");
			setOption("skydip");
			
			if(scans.size() > 1) {
				System.err.println("Ignoring all but first scan in list (for skydip).");
				scans.clear();
				scans.add(firstScan);
			}
		}
		
		if(firstScan.type.equalsIgnoreCase("POINT")) if(scans.size() == 1) {
			setPointing(firstScan);
		}
		
		if(hasOption("nochecks")) return;
		
		// Make sure the rest of the list conform to the first scan...
		for(int i=scans.size(); --i > 0; ) {
			APEXScan<?,?> scan = (APEXScan<?,?>) scans.get(i);
			APEXSubscan<?,?> subscan = scan.get(0);
			
			// Throw out any subsequent skydips...
			if(scan.getSourceName().equalsIgnoreCase("SKYDIP")) {
				warning("Scan " + scan.getID() + " is a skydip. Dropping from dataset.");
				scans.remove(i);
			}
			
			boolean subscanChopped = subscan.getChopper() != null;
			
			if(subscanChopped != isChopped) {	
				if(isChopped) warning("Scan " + scan.getID() + " is not a chopped scan. Dropping from dataset.");
				else warning("Scan " + scan.getID() + " is a chopped scan. Dropping from dataset.");
				scans.remove(i);
				continue;
			}
			
			if(isChopped) {
				if(!scan.isNonSidereal) {
					if(scan.equatorial.distanceTo(reference) > pointingTolerance) {
						warning("Scan " + scan.getID() + " observed at a different position. Dropping from dataset.");
						System.err.println("           (You can use 'moving' to keep and reduce anyway.)");
						scans.remove(i);
					}
				}
				else if(!scan.getSourceName().equalsIgnoreCase(sourceName)) {
					System.err.println("  WARNING! Scan " + scan.getID() + " is on a different object. Dropping from dataset.");
					scans.remove(i);
				}
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
	public SourceModel getSourceModelInstance() {
		if(hasOption("chopped")) return new APEXChoppedPhotometry(this);
		else return super.getSourceModelInstance();
	}
	
	public ArrayList<APEXContinuumPixel> getNeighbours(APEXContinuumPixel pixel, double radius) {
		ArrayList<APEXContinuumPixel> neighbours = new ArrayList<APEXContinuumPixel>();
		for(APEXContinuumPixel p2 : getObservingChannels()) if(p2 != pixel) if(p2.distanceTo(pixel) <= radius) neighbours.add(p2);
		return neighbours;		
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("ref")) return referencePixel.getID();
		else if(name.equals("rot")) return Util.defaultFormat(rotation / Unit.deg, f);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	@Override
	public String getDataLocationHelp() {
		return super.getDataLocationHelp() +
				"                    'datapath' can be either the directory containing the\n" +
				"                    scans (FITS files or scan folders) themselves, or the\n" +
				"                    location in which project sub-directories reside.\n" +
				"     -project=      The project ID (case insensitive). E.g. 'T-79.F-0002-2007'.\n";
	}
	
	
}
