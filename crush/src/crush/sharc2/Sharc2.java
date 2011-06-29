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

package crush.sharc2;

import crush.*;
import crush.array.*;
import nom.tam.fits.*;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import util.*;
import util.data.Statistics;
import util.data.WeightedPoint;
import util.text.TableFormatter;

public class Sharc2 extends RotatingArray<Sharc2Pixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6054582144119360355L;
	String filterName;
	
	public Vector2D pixelSize;
	private Vector2D arrayPointingCenter = new Vector2D(6.5, 16.5);
	
	double nativeSamplingInterval;
	double[] rowGain;
	boolean[] isHiGain;
	
	double rotatorAngle, rotatorZeroAngle, rotatorOffset;
	String rotatorMode;
	
	double focusX, focusY, focusZ;
	double focusYOffset, focusZOffset;
	String focusMode;
	
	boolean dsosUsed;
	String dsosVersion;
	
	double excessLoad = 0.0;
	double averagePixelGain = 1.0;
	
	
	public Sharc2() {
		super("sharc2", pixels);
		resolution = 8.5 * Unit.arcsec;
	}
	
	@Override
	public String getTelescopeName() {
		return "CSO";
	}
	
	@Override
	public Instrument<Sharc2Pixel> copy() {
		Sharc2 copy = (Sharc2) super.copy();
		
		if(rowGain != null) {
			copy.rowGain = new double[rowGain.length];
			System.arraycopy(rowGain, 0, copy.rowGain, 0, rowGain.length);
		}
		
		if(isHiGain != null) {
			copy.isHiGain = new boolean[isHiGain.length];
			System.arraycopy(isHiGain, 0, copy.isHiGain, 0, isHiGain.length);
		}
		
		copy.arrayPointingCenter = (Vector2D) arrayPointingCenter.clone();
		
		return copy;
	}
	 
	@Override
	public void initialize() {
		super.initialize();
		
		String filter = null;
		
		if(hasOption("350um")) filter = "350um";
		else if(hasOption("450um")) filter = "450um";
		else if(hasOption("850um")) filter = "850um";
		
		System.err.println("SHARC-2 Filter set to " + filter);
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
				System.err.println(" WARNING! SHARC-2 is in non-standard orientation. Will assume that pointing");
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
	public Sharc2Pixel getChannelInstance(int backendIndex) {
		return new Sharc2Pixel(this, backendIndex);
	}	

	@Override
	public String getPixelDataHeader() {
		return super.getPixelDataHeader() + "\teff\tgRow";
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new Sharc2Scan(this);
	}

	@Override
	public void loadChannelData() {
		
		// Load the Gain Non-linearity coefficients
		if(hasOption("response")) {
			try {
				loadGainCoefficients(Util.getSystemPath(option("response").getValue()));
				calcPixelGains();
			}
			catch(IOException e) { System.err.println(" WARNING! Problem parsing nonlinearity file."); }		
		}
	
		// Update the pointing center...
		if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
		
		Vector2D pixelSize = Sharc2Pixel.defaultSize;
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.x = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
			pixelSize.y = tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x;
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
	
		return Sharc2Pixel.getPosition(pixelSize, arrayPointingCenter.x - arrayRotationCenter.x, arrayPointingCenter.y - arrayRotationCenter.y);
	}
	
	private void calcPositions(Vector2D size) {
		pixelSize = size;
		// Make all pixels the same size. Also calculate their positions...
		for(Sharc2Pixel pixel : this) {
			pixel.size = size;
			pixel.calcPosition();
		}
		Vector2D center = Sharc2Pixel.getPosition(size, arrayPointingCenter.x - 1.0, arrayPointingCenter.y - 1.0);
		setReferencePosition(center);
	}
	
	@Override
	public double getRotation() {
		return (mount == Mount.CASSEGRAIN ? rotatorAngle : 0.0) - rotatorZeroAngle;
	}
	
	@Override
	public void addDivisions() {
		super.addDivisions();
		
		try { addDivision(getDivision("rows", Sharc2Pixel.class.getField("row"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		
		if(hasOption("block")) {
			StringTokenizer tokens = new StringTokenizer(option("block").getValue(), " \t:x");
			int sizeX = Integer.parseInt(tokens.nextToken());
			int sizeY = tokens.hasMoreTokens() ? Integer.parseInt(tokens.nextToken()) : sizeX;
			int nx = (int)Math.ceil(32.0 / sizeX);
			
			for(Sharc2Pixel pixel : this) pixel.block = (pixel.row / sizeY) * nx + (pixel.col / sizeX); 
		}
			
		try { addDivision(getDivision("blocks", Sharc2Pixel.class.getField("block"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
	
		/*
		try { addDivision(getDivision("amps", Sharc2Pixel.class.getField("amp"), Channel.FLAG_DEAD)); }
		catch(Exception e) { e.printStackTrace(); }
		*/
		
		
	}
	
	@Override
	public void addModalities() {
		super.addModalities();
		
		try { addModality(new CorrelatedModality("rows", "r", divisions.get("rows"), Sharc2Pixel.class.getField("rowGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("mux", "m", divisions.get("rows"), Sharc2Pixel.class.getField("muxGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("blocks", "b", divisions.get("blocks"), Sharc2Pixel.class.getField("gain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		/* TODO
		try { addModality(new CorrelatedModality("amps", "a", divisions.get("amps"), Sharc2Pixel.class.getField("ampGain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		*/
		
		modalities.get("rows").setGainFlag(Sharc2Pixel.FLAG_ROW);
		modalities.get("mux").solveGains = false;
		modalities.get("blocks").solveGains = false;
		// TODO modalities.get("amps").gainFlag = Sharc2Pixel.FLAG_AMP;
		
	}
	

	protected void parseHardwareHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		// Get Vital information from Descriptive HDU's
		double hiGain = hdu.getHeader().getDoubleValue("GAIN_HI", 10521.0);
		double loGain = hdu.getHeader().getDoubleValue("GAIN_LO", 1412.0);

		// Filter
		filterName = hdu.getHeader().getStringValue("FILTER");
		if(hasOption("filter")) filterName = option("filter").getValue();
		
		// Get the electronic gains for the rows
		rowGain = new double[12];
		isHiGain = new boolean[12];
		
		boolean[][] gainMode = (boolean[][]) hdu.getColumn("Gain Mode");
		
		for(int row=0; row<12; row++) {
			isHiGain[row] = gainMode[row][0];
			rowGain[row] = isHiGain[row] ? hiGain : loGain;
		}
	
		// Read in the DAC values...
		short[][] DAC = new short[12][];
		for(int row=0; row<12; row++) DAC[row] = (short[]) hdu.getRow(row)[0];

		// Read the Bias Voltages
		float[][] rowBias = (float[][])hdu.getColumn("Bias Voltage");
		if(rowBias == null) {
			rowBias = new float[12][2];
			for(int row=0; row<12; row++) rowBias[row][0] = 1000.0F;
		}
		
		// Add the pixels here...
		if(!isEmpty()) clear();
		ensureCapacity(pixels);
		for(int c=0; c<pixels; c++) add(new Sharc2Pixel(this, c));
		
		for(Sharc2Pixel pixel : this) {
			pixel.DAC = DAC[pixel.row][pixel.col];
			pixel.biasV = rowBias[pixel.row][0] * Unit.mV;
			pixel.offset = -(isHiGain[pixel.row] ? 48.83 : 4.439) * Unit.mV * pixel.DAC;
		}

	}    
	
	
	protected void parseDSPHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		nativeSamplingInterval = hdu.getHeader().getDoubleValue("FRAMESPC", 36.0) * Unit.ms;
	}

	protected void parsePixelHDU(BinaryTableHDU hdu) throws HeaderCardException, FitsException {
		Hashtable<Integer, Sharc2Pixel> lookup = getChannelLookup();
		
		int iGain = hdu.findColumn("Relative Pixel Gains");
		int iFlag = hdu.findColumn("Pixel Flags");
		int iWeight = hdu.findColumn("Pixel Weights");
		int iOffset = hdu.findColumn("Pixel Offsets");
		
		if(iGain < 0) System.err.println(" WARNING! FITS pixel gains not found.");
		if(iFlag < 0) System.err.println(" WARNING! FITS pixel flags not found.");
		if(iWeight < 0) System.err.println(" WARNING! FITS pixel weights not found.");
		if(iOffset < 0) System.err.println(" WARNING! FITS pixel offsets not found.");
		
		for(int row=0; row<12; row++) {
			Object[] data = hdu.getRow(row);
			int rowStart = 32 * row + 1;
			
			if(iGain >= 0) {
				float[] gain = (float[]) data[iGain];
				for(int col=0; col<32; col++) lookup.get(rowStart + col).gain = gain[col];
			}
			// Flags in data file are different than here...
			// Are they really necessary?
			/*
			if(iFlag >= 0) {
				int[] flag = (int[]) data[iFlag];
				for(int col=0; col<32; col++) lookup.get(bol0+col).flag = flag[col];
			}	
			*/
			if(iWeight >= 0) {
				float[] weight = (float[]) data[iWeight];
				for(int col=0; col<32; col++) lookup.get(rowStart + col).weight = weight[col];
			}		   
			// Do not parse offsets. One should not rely on the uncertain levelling
			// by user before scans. It's safer and better to get these calculated
			// from the data themselves...
		}
	} 
	
	protected void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
		
		// Platform
		String platform = header.getStringValue("PLATFORM");
		if(platform == null) platform = "Cassegrain";
		
		mount =  platform.equalsIgnoreCase("NASMYTH") ? Mount.RIGHT_NASMYTH : Mount.CASSEGRAIN;
		
		System.err.println(" " + mount.name + " mount assumed.");
		
		rotatorAngle = header.getDoubleValue("ROTATOR", 0.0) * Unit.deg;
		rotatorOffset = header.getDoubleValue("ROTOFFST") * Unit.deg;
		rotatorMode = header.getStringValue("ROTMODE");
		
		rotatorZeroAngle = header.getDoubleValue("ROTZERO") * Unit.deg;
		if(rotatorMode == null) rotatorMode = "Unknown";
		
		// Focus
		focusX =  header.getDoubleValue("FOCUS_X");
		focusY =  header.getDoubleValue("FOCUS_Y");
		focusZ =  header.getDoubleValue("FOCUS_Z");

		focusYOffset =  header.getDoubleValue("FOCUS_YO");
		focusZOffset =  header.getDoubleValue("FOCUS_ZO");

		focusMode = header.getStringValue("FOCMODE");
		if(focusMode == null) focusMode = "Unknown";
		
		System.err.println(" Focus [" + focusMode + "]"
				+ " X=" + Util.f2.format(focusX)
				+ " Y=" + Util.f2.format(focusY)
				+ " Z=" + Util.f2.format(focusZ)
				+ " Yoff=" + Util.f2.format(focusYOffset) 
				+ " Zoff=" + Util.f2.format(focusZOffset)
		);

		// DSOS
		dsosUsed = header.getBooleanValue("DSOS");
		dsosVersion = header.getStringValue("DSOSVER");
		
		if(dsosUsed) System.err.println(" DSOS version " + dsosVersion);
		
	}
	
	protected void parseDataHeader(Header header) throws HeaderCardException, FitsException {
		samplingInterval = integrationTime = header.getDoubleValue("CDELT1") * Unit.ms;
		
		// Pointing Center
		arrayPointingCenter = new Vector2D();
		arrayPointingCenter.x = header.getDoubleValue("CRPIX3", 6.5) - 1.0;
		arrayPointingCenter.y = header.getDoubleValue("CRPIX2", 16.5) - 1.0;
	}
	
	public void loadGainCoefficients(String fileName) throws IOException {
		System.out.print(" Loading nonlinearities from " + fileName + ".");

		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		Hashtable<Integer, Sharc2Pixel> lookup = getChannelLookup();
		
		String line;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);

			int row = Integer.parseInt(tokens.nextToken()) - 1;
			int col = Integer.parseInt(tokens.nextToken()) - 1;
			
			Sharc2Pixel pixel = lookup.get(32*row + col + 1);

			pixel.G0 = Double.parseDouble(tokens.nextToken());
			pixel.V0 = -Double.parseDouble(tokens.nextToken()) * pixel.biasV;
			pixel.T0 = Double.parseDouble(tokens.nextToken()) * Unit.K;
		}
		
		in.close();
		System.out.println();
	}

	public void calcPixelGains() {
		double sumwG2 = 0.0, sumwG = 0.0;
		
		for(Sharc2Pixel pixel : this) {
			pixel.gain = pixel.G0 * pixel.offset / (pixel.getHardwareGain() * pixel.V0);
			if(pixel.isUnflagged()) {
				sumwG2 += pixel.weight * pixel.gain * pixel.gain;
				sumwG += pixel.weight * Math.abs(pixel.gain);
			}
		}
		
		if(sumwG > 0.0) {
			averagePixelGain = sumwG2 / sumwG;
			for(Sharc2Pixel pixel : this) pixel.gain /= averagePixelGain;
		}
		
		System.err.println(" Gain compression is " + Util.f3.format(averagePixelGain));
	}
	
	// TODO convert to robust estimate?...
	public double getMeandVdT() {
		double sumdIdT = 0.0, sumw=0.0;
		for(Sharc2Pixel pixel : this) if(pixel.isUnflagged()) {
			sumdIdT += pixel.weight * pixel.getAreaFactor() * pixel.V0 / pixel.T0;
			sumw += pixel.weight;
		}

		return sumw > 0.0 ? (float) (sumdIdT / sumw) : 0.0;
	}


	public double getLoadTemperature() {
		WeightedPoint[] data = new WeightedPoint[size()];
		int n = 0;
		
		for(Sharc2Pixel pixel : this) if(pixel.isUnflagged()) {
			double dVdT = pixel.getAreaFactor() * pixel.V0 / pixel.T0;
			WeightedPoint T = new WeightedPoint();
			T.value = (pixel.V0 - pixel.offset / pixel.getHardwareGain()) / dVdT;
			T.weight = pixel.weight * dVdT * dVdT;
			data[n++] = T;
		}
		
		return Statistics.smartMedian(data, 0, n, 0.25).value - excessLoad;
	}
	
	public void calcGainCoefficients(double loadT) {
		System.err.println(" Calculating nonlinearity coefficients.");
		
		double sumG02 = 0.0, sumG0 = 0.0;
		
		for(Sharc2Pixel pixel : this) {
			double areaFactor = pixel.getAreaFactor();
			double eps = 1.0 - areaFactor * loadT / pixel.T0;
			
			pixel.G0 = pixel.gain / eps;
			pixel.V0 = pixel.offset / pixel.getHardwareGain() / eps;
			
			if(!pixel.isFlagged()) {
				sumG02 += pixel.weight * pixel.G0 * pixel.G0;
				sumG0 += pixel.weight * Math.abs(pixel.G0);
			}
		}
		
		if(sumG0 > 0.0) {
			double aveG0 = sumG02 / sumG0;
			for(Sharc2Pixel pixel : this) pixel.G0 /= aveG0;
		}
		
	}

	public void writeGainCoefficients(String fileName, String header) throws IOException {
		System.err.println(" Writing nonlinearity coefficients to " + fileName);
		
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
	
		out.println("# SHARC2 Non-linearity Coefficients ");
		out.println("#");
		if(header != null) {
			out.println(header);
			out.println("#");
		}
		out.println("# row\tcol\tG(eps=0)\tI0(Vb=1V)\tT0 (K))");
		out.println("# ----- ------- --------------  --------------- ----------");
	
		for(Sharc2Pixel pixel : this) {
			out.print((pixel.row+1) + "\t" + (pixel.col+1) + "\t");
			out.print(Util.f6.format(pixel.G0) + "\t");
			out.print(Util.e4.format(-pixel.V0 / pixel.biasV) + "\t");
			out.println(Util.f6.format(pixel.T0 / Unit.K));
		}
	
		out.close();
	}

	
	
	public final static int pixels = 384;


	@Override
	public void readWiring(String fileName) throws IOException {
		// TODO the amplifier wiring...
	}

	@Override
	public int maxPixels() {
		return storeChannels;
	}

	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		
		final Sharc2Scan firstScan = (Sharc2Scan) scans.get(0);
		
		if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);
		
		super.validate(scans);
	}
	
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("dsos?")) return Boolean.toString(dsosUsed);
		else if(name.equals("focY")) return Util.defaultFormat(focusX / Unit.mm, f);
		else if(name.equals("focY")) return Util.defaultFormat(focusY / Unit.mm, f);
		else if(name.equals("focZ")) return Util.defaultFormat(focusZ / Unit.mm, f);
		else if(name.equals("focDY")) return Util.defaultFormat(focusYOffset / Unit.mm, f);
		else if(name.equals("focDZ")) return Util.defaultFormat(focusZOffset / Unit.mm, f);
		else if(name.equals("focmode")) return focusMode;
		else if(name.equals("rot")) return Util.defaultFormat(rotatorAngle / Unit.deg, f);
		else if(name.equals("rot0")) return Util.defaultFormat(rotatorZeroAngle / Unit.deg, f);
		else if(name.equals("rotoff")) return Util.defaultFormat(rotatorOffset / Unit.deg, f);
		else if(name.equals("rotMode")) return rotatorMode;
		else if(name.equals("load")) return Util.defaultFormat(excessLoad / Unit.K, f);
		else if(name.equals("filter")) return filterName;
		else return super.getFormattedEntry(name, formatSpec);
	}

	
}

