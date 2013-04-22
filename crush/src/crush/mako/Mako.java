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
import nom.tam.fits.*;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import util.*;
import util.text.TableFormatter;

public class Mako extends RotatingArray<MakoPixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8482957165297427388L;
	
	public Vector2D pixelSize;
	private Vector2D arrayPointingCenter = new Vector2D((rows+1) / 2.0, (cols+1) / 2.0);
	
	double nativeSamplingInterval;
	
	double rotatorAngle, rotatorZeroAngle, rotatorOffset;
	String rotatorMode;
	
	double focusX, focusY, focusZ;
	double focusYOffset, focusZOffset;
	String focusMode;
	
	boolean dsosUsed;
	String dsosVersion;
	
	double excessLoad = 0.0;
		
	// Information about the IQ -> shift calibration...
	int calPositions;
	int calParms;
	String calModelName;
	String calVersion;
	
	double alpha = Double.NaN;
	
	
	public Mako() {
		super("mako", pixels);
		resolution = 8.5 * Unit.arcsec;
	}
	
	@Override
	public String getTelescopeName() {
		return "CSO";
	}
	
	@Override
	public Instrument<MakoPixel> copy() {
		Mako copy = (Mako) super.copy();
		
		copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.clone();
		
		return copy;
	}
	 
	@Override
	public void initialize() {
		super.initialize();
	}

	@Override
	public void validate() {
		if(hasOption("excessload")) excessLoad = option("excessload").getDouble() * Unit.K;	
		super.validate();
	}
	
	
	private void checkRotation() {
		// Check the instrument rotation...
		if(hasOption("rot0")) rotatorZeroAngle = option("rot0").getDouble() * Unit.deg;
		if(hasOption("rotation")) rotatorAngle = option("rotation").getDouble() * Unit.deg;	
		
		if(mount == Mount.CASSEGRAIN) {
			System.out.println(" Rotator = " + Util.f1.format(rotatorAngle/Unit.deg) + " RotZero = " 
					+ Util.f1.format(rotatorZeroAngle/Unit.deg));
	
			if(Math.abs(rotatorAngle - rotatorZeroAngle) > 5.0 * Unit.deg) {
				System.err.println(" *****************************************************************************");
				System.err.println(" WARNING! MAKO is in non-standard orientation. Will assume that pointing");
				if(hasOption("rcenter")) {
					System.err.println("          was performed in the horizontal orientation. To override this and to");
					System.err.println("          assume pointing in this rotation, use '-forget=rcenter'.");
				}
				else {
					System.err.println("          was performed in the same orientration. To override this and to");
					System.err.println("          assume pointing in horizontal orientation, set the 'rcenter' option.");
				}
				System.err.println(" *****************************************************************************");
			}
		}
		else System.out.println(" Mounted at " + Util.f1.format(rotatorZeroAngle/Unit.deg) + " deg.");
		
		
	}


	@Override
	public MakoPixel getChannelInstance(int backendIndex) {
		return new MakoPixel(this, backendIndex);
	}	

	@Override
	public String getChannelDataHeader() {
		return super.getChannelDataHeader() + "\teff\ttonefreq";
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new MakoScan(this);
	}

	@Override
	public void loadChannelData() {
		
		if(hasOption("assign")) {
			try { assignTones(option("assign").getValue()); }
			catch(IOException e) { System.err.println(" WARNING! Cannot assign pixels from '" + option("assign").getValue() + "'."); }
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
		if(hasOption("skew")) { 
			double skew = option("skew").getDouble();
			pixelSize.scaleX(skew);
			pixelSize.scaleY(1.0/skew);
		}
		
		calcPositions(pixelSize);
	
		checkRotation();
		
		super.loadChannelData();
		
	}
	
	
	
	@Override
	public Vector2D getPointingCenterOffset() {
		// Update the rotation center...
		Vector2D arrayRotationCenter = new Vector2D(6.5, 16.5);
		if(hasOption("rcenter")) arrayRotationCenter = option("rcenter").getVector2D();
	
		return MakoPixel.getPosition(pixelSize, arrayPointingCenter.getX() - arrayRotationCenter.getX(), arrayPointingCenter.getY() - arrayRotationCenter.getY());
	}
	
	private void calcPositions(Vector2D size) {
		pixelSize = size;
		// Make all pixels the same size. Also calculate their positions...
		for(MakoPixel pixel : this) {
			pixel.size = size;
			pixel.calcPosition();
		}
		Vector2D center = MakoPixel.getPosition(size, arrayPointingCenter.getX() - 1.0, arrayPointingCenter.getY() - 1.0);
		setReferencePosition(center);
	}
	
	@Override
	public double getRotation() {
		return (mount == Mount.CASSEGRAIN ? rotatorAngle : 0.0) - rotatorZeroAngle;
	}
	
	@Override
	public void addDivisions() {
		super.addDivisions();	
	}
	
	@Override
	public void addModalities() {
		super.addModalities();
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
		int iPts = hdu.findColumn("Points");
		int iErr = hdu.findColumn("Fit Error");
		
		System.err.println(" MAKO has " + pixels + " tones");
		
		for(int c=0; c<pixels; c++) {
			MakoPixel pixel = new MakoPixel(this, c);
			Object[] row = hdu.getRow(c);
			
			pixel.toneBin = ((int[]) row[iBin])[0];
			pixel.toneFrequency = binWidth * pixel.toneBin;
			pixel.validCalPositions = ((int[]) row[iPts])[0];
			pixel.calError = ((float[]) row[iErr])[0];
		
			add(pixel);
		}	
	}
	
	
	protected void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
		
		samplingInterval = integrationTime = 1.0 / header.getDoubleValue("SAMPLING");
		
		// Platform
		String platform = header.getStringValue("PLATFORM");
		if(platform == null) platform = "Cassegrain";
		
		mount =  platform.equalsIgnoreCase("NASMYTH") ? Mount.RIGHT_NASMYTH : Mount.CASSEGRAIN;
		
		System.err.println(" " + mount.name + " mount assumed.");
		
		
		//rotatorZeroAngle = header.getDoubleValue("ROTZERO") * Unit.deg;
		//rotatorAngle = header.getDoubleValue("ROTATOR", rotatorZeroAngle / Unit.deg) * Unit.deg;
		//rotatorOffset = header.getDoubleValue("ROTOFFST") * Unit.deg;
		//rotatorMode = header.getStringValue("ROTMODE");
	
		if(rotatorMode == null) rotatorMode = "Unknown";
		
		// Focus
		focusX =  header.getDoubleValue("FOCUS_X") * Unit.mm;
		focusY =  header.getDoubleValue("FOCUS_Y") * Unit.mm;
		focusZ =  header.getDoubleValue("FOCUS_Z") * Unit.mm;

		focusYOffset =  header.getDoubleValue("FOCUS_YO") * Unit.mm;
		focusZOffset =  header.getDoubleValue("FOCUS_ZO") * Unit.mm;

		focusMode = header.getStringValue("FOCMODE");
		if(focusMode == null) focusMode = "Unknown";
		
		System.err.println(" Focus [" + focusMode + "]"
				+ " X=" + Util.f2.format(focusX / Unit.mm)
				+ " Y=" + Util.f2.format(focusY / Unit.mm)
				+ " Z=" + Util.f2.format(focusZ / Unit.mm)
				+ " Yoff=" + Util.f2.format(focusYOffset / Unit.mm) 
				+ " Zoff=" + Util.f2.format(focusZOffset / Unit.mm)
		);

		// DSOS
		dsosUsed = header.getBooleanValue("DSOS");
		dsosVersion = header.getStringValue("DSOSVER");
		
		if(dsosUsed) System.err.println(" DSOS version " + dsosVersion);
		
	}
	
	protected void parseDataHeader(Header header) throws HeaderCardException, FitsException {
		// Pointing Center
		arrayPointingCenter = new Vector2D();
		arrayPointingCenter.setX(header.getDoubleValue("CRPIX3", (rows + 1) / 2.0));
		arrayPointingCenter.setY(header.getDoubleValue("CRPIX2", (cols + 1) / 2.0));
	}
	

	
	@Override
	public void readWiring(String fileName) throws IOException {}
	
	public void assignTones(String fileSpec) throws IOException {
		PixelAssignment assignment = new PixelAssignment(fileSpec);
		if(hasOption("assign.max")) assignment.maxDeviation = option("assign.max").getDouble();
		assignment.match(new ToneList(this));
		alpha = assignment.alpha;
		if(CRUSH.verbose) System.err.println(" Loading constant alpha = " + Util.f3.format(alpha));
	}

	@Override
	public int maxPixels() {
		return pixels;
	}

	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		final MakoScan firstScan = (MakoScan) scans.get(0);
		
		if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);
		
		super.validate(scans);
	}
	
	// Assuming tone id's are at 4.2K load and maximum movement is measured at room temperature -- 22 C)
	public double getLoadTemperature() {
		double Tcold = 4.2 * Unit.K;
		double Thot = 295.16 * Unit.K;
		
		return Tcold + alpha * (Thot - Tcold);
		
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("dsos?")) return Boolean.toString(dsosUsed);
		else if(name.equals("foc.X")) return Util.defaultFormat(focusX / Unit.mm, f);
		else if(name.equals("foc.Y")) return Util.defaultFormat(focusY / Unit.mm, f);
		else if(name.equals("foc.Z")) return Util.defaultFormat(focusZ / Unit.mm, f);
		else if(name.equals("foc.dY")) return Util.defaultFormat(focusYOffset / Unit.mm, f);
		else if(name.equals("foc.dZ")) return Util.defaultFormat(focusZOffset / Unit.mm, f);
		else if(name.equals("foc.mode")) return focusMode;
		else if(name.equals("rot")) return Util.defaultFormat(rotatorAngle / Unit.deg, f);
		else if(name.equals("rot0")) return Util.defaultFormat(rotatorZeroAngle / Unit.deg, f);
		else if(name.equals("rotoff")) return Util.defaultFormat(rotatorOffset / Unit.deg, f);
		else if(name.equals("rotMode")) return rotatorMode;
		else if(name.equals("load")) return Util.defaultFormat(excessLoad / Unit.K, f);
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
	public static int pixels = 2 * rows * cols;

	
}

