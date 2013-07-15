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
package crush.mako;

import crush.*;
import crush.array.*;
import crush.cso.CSOArray;
import nom.tam.fits.*;

import java.io.*;
import java.util.*;

import kovacs.math.Vector2D;
import kovacs.text.TableFormatter;
import kovacs.util.*;


public class Mako extends CSOArray<MakoPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8482957165297427388L;
	
	public Vector2D pixelSize;
	private Vector2D arrayPointingCenter = new Vector2D((rows+1) / 2.0, (cols+1) / 2.0);
	
	double nativeSamplingInterval;	

		
	// Information about the IQ -> shift calibration...
	int calPositions;
	int calParms;
	String calModelName;
	String calVersion;
	
	double Tsky = Double.NaN;
	
	ToneIdentifier identifier;
	
	public Mako() {
		super("mako", pixels);
		resolution = 8.5 * Unit.arcsec;
	}
	
	@Override
	public Instrument<MakoPixel> copy() {
		Mako copy = (Mako) super.copy();
		
		copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.clone();
		
		return copy;
	}
	
	@Override
	public MakoPixel getChannelInstance(int backendIndex) {
		return new MakoPixel(this, backendIndex);
	}	

	@Override
	public String getChannelDataHeader() {
		return "toneid\t" + super.getChannelDataHeader() + "\teff";
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new MakoScan(this);
	}
	
	public Hashtable<String, MakoPixel> idLookup() {
		Hashtable<String, MakoPixel> lookup = new Hashtable<String, MakoPixel>(pixels);
		for(MakoPixel pixel : this) if(pixel.id != null) lookup.put(pixel.getID(), pixel);
		return lookup;
	}
	
	@Override
	public void loadChannelData() {
		
		if(hasOption("toneid")) {
			try {
				identifier = new ToneIdentifier(option("toneid"));	
				double guessT = (hasOption("toneid.guesst") ? option("toneid.guesst").getDouble() : 150.0) * Unit.K;
				Tsky = identifier.match(new ResonanceList(this), guessT);
			}
			catch(IOException e) {
				System.err.println(" WARNING! Cannot identify tones from '" + option("toneid").getValue() + "'."); 
				if(CRUSH.debug) e.printStackTrace();
			}
		}
				
		if(identifier != null && hasOption("assign")) {	
			try { assignPixels(option("assign").getValue()); }
			catch(IOException e) { 
				System.err.println(" WARNING! Cannot assign pixels from '" + option("assign").getValue() + "'."); 
				if(CRUSH.debug) e.printStackTrace();
			}
		}
		else System.err.println(" WARNING! Tones are not assigned to pixels. Cannot make regular maps.");
		
		
		// Do not flag unassigned pixels when beam-mapping...
		if(hasOption("source.type")) if(option("source.type").equals("beammap")) 
				for(MakoPixel pixel : this) pixel.unflag(MakoPixel.FLAG_UNASSIGNED);
		
		// Update the pointing center...
		if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
		
		Vector2D pixelSize = MakoPixel.defaultSize;
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.getX());
		}
		if(hasOption("mirror")) { pixelSize.scaleX(-1.0); }
		if(hasOption("zoom")) { pixelSize.scale(option("zoom").getDouble()); }
		if(hasOption("stretch")) { 
			double skew = option("stretch").getDouble();
			pixelSize.scaleX(skew);
			pixelSize.scaleY(1.0/skew);
		}
		
		calcPositions(pixelSize);
		
		checkRotation();
		
		super.loadChannelData();
		
	}
	
	@Override
	public Hashtable<Integer, Pixel> getPixelLookup() {
		Hashtable<Integer, Pixel> table = new Hashtable<Integer, Pixel>();
		for(MakoPixel pixel : this) if(pixel.id != null) table.put(pixel.id.index, pixel);
		return table;
	}
	
	public void assignPixels(String fileSpec) throws IOException {
		if(identifier == null) throw new IllegalStateException(" Assigning pixels requires tone identifications first.");
		
		System.err.println(" Loading pixel assignments from " + fileSpec);

		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(Util.getSystemPath(fileSpec))));
		String line = null;
		
		ResonanceList associations = new ResonanceList(pixels);
		
		double guessT = (hasOption("assign.guesst") ? option("assign.guesst").getDouble() : 300.0) * Unit.K;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line, ", \t");
			MakoPixel pixel = new MakoPixel(this, -1);
			
			pixel.toneFrequency = Double.parseDouble(tokens.nextToken());
			pixel.row = Integer.parseInt(tokens.nextToken()) - 1;
			pixel.col = Integer.parseInt(tokens.nextToken()) - 1;

			associations.add(pixel);
		}

		in.close();

		System.err.println(" Found pixel assignments for " + associations.size() + " resonances.");
		
		identifier.match(associations, guessT);
		associations.assign(this);
	}
	
	
	@Override
	public Vector2D getPointingCenterOffset() {
		// Update the rotation center...
		Vector2D arrayRotationCenter = new Vector2D((rows + 1)/2.0, (cols+1)/2.0);
		if(hasOption("rcenter")) arrayRotationCenter = option("rcenter").getVector2D();
	
		return MakoPixel.getPosition(pixelSize, arrayPointingCenter.getX() - arrayRotationCenter.getX(), arrayPointingCenter.getY() - arrayRotationCenter.getY());
	}
	
	private void calcPositions(Vector2D size) {
		pixelSize = size;
		// Make all pixels the same size. Also calculate their distortionless positions...
		for(MakoPixel pixel : this) {
			pixel.size = size;
			pixel.calcPosition();
		}
		
		Vector2D center = MakoPixel.getPosition(size, arrayPointingCenter.getX() - 1.0, arrayPointingCenter.getY() - 1.0);
		
		if(hasOption("distortion")) {
			System.err.println(" Correcting for focal-plane distortion.");
			DistortionModel model = new DistortionModel();
			model.setOptions(option("distortion"));	
			
			for(MakoPixel pixel : this) model.distort(pixel.getPosition());
			model.distort(center);
		}
		
		setReferencePosition(center);
	}
	
	@Override
	public void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
		samplingInterval = integrationTime = 1.0 / header.getDoubleValue("SAMPLING");
		
		super.parseScanPrimaryHDU(hdu);
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
		
		System.err.print(" MAKO stream has " + pixels + " tones. ");
		
		int blinds = 0;
		
		for(int c=0; c<pixels; c++) {
			MakoPixel pixel = new MakoPixel(this, c);
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
		
		if(iFlag < 0) System.err.println(" WARNING! Data has no information on blind tones.");
		else if(blinds > 0) System.err.println(" Ignoring " + blinds + " blind tones.");
		else System.err.println(" Stream contains no blind tones :-).");
		
	}
	
	protected void parseDataHeader(Header header) throws HeaderCardException, FitsException {
		// Pointing Center
		arrayPointingCenter = new Vector2D();
		arrayPointingCenter.setX(header.getDoubleValue("CRPIX3", (rows + 1) / 2.0));
		arrayPointingCenter.setY(header.getDoubleValue("CRPIX2", (cols + 1) / 2.0));
	}
	
	@Override
	public void readWiring(String fileName) throws IOException {}

	@Override
	public int maxPixels() {
		return rows*cols;
	}
	
	// Assuming tone id's are at 4.2K load and maximum movement is measured at room temperature -- 22 C)
	@Override
	public double getLoadTemperature() {
		double Tcold = 4.2 * Unit.K;
		double Thot = 295.16 * Unit.K;
		return Tcold + Tsky * (Thot - Tcold);
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		if(name.equals("Tres")) return Util.defaultFormat(Tsky / Unit.K, TableFormatter.getNumberFormat(formatSpec));
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	@Override
	public String getCommonHelp() {
		return super.getCommonHelp() + 
				"     -fazo=        Correct the pointing with this FAZO value.\n" +
				"     -fzao=        Correct the pointing with this FZAO value.\n";
	}
	
	@Override
	public String getRCPHeader() { return super.getRCPHeader() + "\tKIDfreq"; }

	
	public static int rows = 16;
	public static int cols = 27;
	public static int pixels = rows * cols;

	
}

