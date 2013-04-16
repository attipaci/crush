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
import nom.tam.fits.*;

import java.io.*;
import java.util.*;
import java.text.*;

import util.*;
import util.astro.AstroTime;
import util.astro.EquatorialCoordinates;
import util.astro.GeodeticCoordinates;
import util.astro.HorizontalCoordinates;
import util.astro.Weather;
import util.data.DataPoint;
import util.text.TableFormatter;

public class Sharc2Scan extends Scan<Sharc2, Sharc2Integration> implements GroundBased, Weather {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2008390740743369604L;

	double tau225GHz;
	double elevationResponse = 1.0;
	double ambientT, pressure, humidity;
	
	boolean addOffsets = true;
	
	Vector2D horizontalOffset, fixedOffset;
	
	public int iMJD;	
	public long fileSize = -1;

	private double raUnit = Unit.hourAngle;
	private double decUnit = Unit.deg;
	
	public Sharc2Scan(Sharc2 instrument) {
		super(instrument);
		site = new GeodeticCoordinates(
				-(155.0 * Unit.deg + 28.0 * Unit.arcmin + 33.0 * Unit.arcsec), 
				19.0 * Unit.deg  + 49.0 * Unit.arcmin + 21.0 * Unit.arcsec);
		isTracking = true;
	}

	@Override
	public Sharc2Integration getIntegrationInstance() {
		return new Sharc2Integration(this);
	}
	
	@Override
	public void calcHorizontal() {
		Sharc2Frame firstFrame = getFirstIntegration().getFirstFrame();
		Sharc2Frame lastFrame = getLastIntegration().getLastFrame();
		
		horizontal = new HorizontalCoordinates(
				0.5 * (firstFrame.horizontal.getX() + lastFrame.horizontal.getX()),  
				0.5 * (firstFrame.horizontal.getY() + lastFrame.horizontal.getY())
		);
			
		// When the telescope is not tracking, the equatorial coordinates may be bogus...
		// Use the horizontal coordinates to make sure the equatorial ones make sense...
		EquatorialCoordinates eq2 = horizontal.toEquatorial(site, LST);
		eq2.epoch = apparent.epoch;		
		eq2.precess(equatorial.epoch);
		
		if(eq2.distanceTo(equatorial) > 5.0 * Unit.deg) {
			System.err.println("   >>> Fix: invalid (stale) equatorial coordinates.");
			equatorial = eq2;
			apparent = horizontal.toEquatorial(site, LST);
		}
		
	}
	
	@Override
	public void validate() {
		super.validate();
	
		if(hasOption("elevation-response")) {
			try { 
				String fileName = option("elevation-response").getPath();
				elevationResponse = new ElevationCouplingCurve(fileName).getValue(horizontal.elevation()); 
				System.err.println("   Relative beam efficiency is " + Util.f3.format(elevationResponse));
				for(Sharc2Integration integration : this) integration.gain *= elevationResponse;
			}
			catch(IOException e) { 
				System.err.println("WARNING! Cannot read elevation response table..."); 
				e.printStackTrace();
			}
		}
	}
	
	protected void read(Fits fits, boolean readFully) throws Exception {
		// Read in entire FITS file		
		BasicHDU[] HDU = fits.read();
		
		int i = 4; 
		BasicHDU firstDataHDU = null;
		while( !(firstDataHDU = HDU[i]).getHeader().getStringValue("EXTNAME").equalsIgnoreCase("SHARC2 Data") ) i++;
		
		checkPrematureFits(HDU[0], (BinaryTableHDU) firstDataHDU);
		
		parseScanPrimaryHDU(HDU[0]);
		clear();
		
		// Load the instrument settings...
		instrument.parseScanPrimaryHDU(HDU[0]);
		instrument.parseHardwareHDU((BinaryTableHDU) HDU[1]);
		instrument.parseDSPHDU((BinaryTableHDU) HDU[2]);
		instrument.parsePixelHDU((BinaryTableHDU) HDU[3]);
		instrument.parseDataHeader(firstDataHDU.getHeader());
		instrument.validate(this);
		
		Sharc2Integration integration = new Sharc2Integration(this);
		integration.read(HDU, i);
		add(integration);
		
		try { fits.getStream().close(); }
		catch(IOException e) {}
		
		horizontal = null;
		
		validate();
	}
	
	
	public File getFile(String scanDescriptor) throws FileNotFoundException {
		File scanFile;

		String path = getDataPath();
		descriptor = scanDescriptor;
		
		try { 
			int scanNo = Integer.parseInt(scanDescriptor);
			String fileName = path + "sharc2-" + Util.d6.format(scanNo) + ".fits";
			scanFile = new File(fileName);
			if(!scanFile.exists()) {
				fileName += ".gz";
				scanFile = new File(fileName);
				if(!scanFile.exists()) throw new FileNotFoundException("Could Not find scan " + scanDescriptor); 
			}
		} 
		catch(NumberFormatException e) {
			scanFile = new File(scanDescriptor) ;	
			if(!scanFile.exists()) {
				scanFile = new File(path + scanDescriptor);
				if(!scanFile.exists()) throw new FileNotFoundException("Could Not find scan " + scanDescriptor); 
			} 
		}	

		fileSize = scanFile.length();

		return scanFile;
	}

	public void readScanInfo(String scanDescriptor) throws IOException, HeaderCardException, FitsException {
		readScanInfo(new Fits(getFile(scanDescriptor)));
	}

	protected void readScanInfo(Fits fits) throws IOException, HeaderCardException, FitsException {
		parseScanPrimaryHDU(fits.readHDU());		
		fits.skipHDU(3);

		BasicHDU nextHDU = fits.readHDU();
		while(!nextHDU.getHeader().getStringValue("EXTNAME").equalsIgnoreCase("SHARC2 Data") ) nextHDU = fits.readHDU();
		instrument.parseDataHeader(nextHDU.getHeader());
	}
	
	
	@Override
	public void read(String scanDescriptor, boolean readFully) throws Exception {
		read(getFits(scanDescriptor), readFully);
	}

	
	public Fits getFits(String scanDescriptor) throws FileNotFoundException, FitsException {
		File file = getFile(scanDescriptor);
		boolean isCompressed = file.getName().endsWith(".gz");
		System.out.println(" Reading " + file.getPath() + "...");
		return new Fits(getFile(scanDescriptor), isCompressed);
	}
	
	protected void checkPrematureFits(BasicHDU main, BinaryTableHDU data) throws FitsException {
		equatorial = null;
		Header header = main.getHeader();
		if(header.containsKey("RAEP") && header.containsKey("DECEP")) return;
		
		System.err.println(" >>> Fix: Assuming early JSharc data format.");
		System.err.println(" >>> Fix: Source coordinates obtained from antenna stream.");
		
		instrument.prematureFITS = true;
		double epoch = header.getDoubleValue("EQUINOX", 2000.0);
		header = data.getHeader();
		float RA = ((float[]) data.getElement(0, data.findColumn("RA")))[0];
		float DEC = ((float[]) data.getElement(0, data.findColumn("DEC")))[0];
		
		equatorial = new EquatorialCoordinates(
				RA * raUnit,
				DEC * decUnit,
				(epoch < 1984.0 ? "B" : "J") + epoch);
	}
	
	@Override
	public void setSerial(int number) {
		// Fix for early Nov 2002 scans, where RA/DEC are in incorrect units.
		if(number < 7320) {
			System.err.println(" >>> Fix: RA/DEC column in unexpected units.");
			raUnit = 1.0;
			decUnit = 1.0;
		}
		else {
			raUnit = Unit.hourAngle;
			decUnit = Unit.deg;
		}
	
		super.setSerial(number);
	}

	@Override
	public void setSourceName(String name) {
		// For early SHARC-2 data, the source name was sometimes lagging, and therefore not always correct. 
		// (JSharc got the source name from the UIP at the beginning of the scan, rather than at the end)
		// This happened only before scan 7320. For these early scans, we can recover the correct source name
		// based on a correction list definition...
		if(getSerial() < 7320) name = fixSourceName(name);
		super.setSourceName(name);		
	}
	
	private String fixSourceName(String name) {
		int serial = getSerial();
		String fileName = instrument.getConfigPath() + "sourcename-2002.fix";
		try { 
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
			String line = null;
			while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
				StringTokenizer tokens = new StringTokenizer(line);
				if(Integer.parseInt(tokens.nextToken()) == serial) {
					name = tokens.nextToken();
					System.err.println(" Fix: source name changed to " + name);
					in.close();
					return name;
				}
			}
			in.close();
		}
		catch(IOException e) { System.err.println(" >>> Fix: WARNING! Cannot find name fix list specification file. No fix."); }
		return name;
	}
	
	protected void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();

		// Scan Info
		setSerial(header.getIntValue("SCANNO"));
		
		//site = new GeodesicCoordinates(header.getDoubleValue("TELLONGI") * Unit.deg, header.getDoubleValue("TELLATID") * Unit.deg);
		//System.err.println("   Location: " + site);
		
		creator = header.getStringValue("CREATOR");
		observer = header.getStringValue("OBSERVER");
		project = header.getStringValue("PROJECT");

		if(creator == null) creator = "Unknown";
		if(observer == null) observer = "Unknown";
		if(project == null) project = "Unknown";

		// Weather
		if(hasOption("tau.225ghz")) tau225GHz = option("tau.225ghz").getDouble();
		else {
			tau225GHz = header.getDoubleValue("TAU225GH");
			instrument.options.process("tau.225ghz", tau225GHz + "");
		}

		ambientT = header.getDoubleValue("TEMPERAT") * Unit.K + 273.16 * Unit.K;
		pressure = header.getDoubleValue("PRESSURE") * Unit.mbar;
		humidity = header.getDoubleValue("HUMIDITY");

		// Source Information
		String sourceName = header.getStringValue("OBJECT");
		if(sourceName == null) sourceName = "Unknown";
		setSourceName(sourceName);
		sourceName = getSourceName(); // Confirm the source name setting...
		
		timeStamp = header.getStringValue("DATE-OBS");
		
		// increment month by 1 to correct JSharc FITS bug
		if(creator.equalsIgnoreCase("JSharc")) {
			System.err.println(" >>> Fix: FITS libraries BUG in JSharc timestamp.");
			String modDate = timeStamp.substring(0, 5);
			modDate += Util.d2.format(Integer.parseInt(timeStamp.substring(5,7)) + 1 );
			modDate += timeStamp.substring(7);
			timeStamp = modDate;
			//System.err.println(modDate);
		}
		double epoch = header.getDoubleValue("EQUINOX");
		
		if(equatorial == null) equatorial = new EquatorialCoordinates(
				header.getDoubleValue("RAEP") * raUnit,
				header.getDoubleValue("DECEP") * decUnit,
				(epoch < 1984.0 ? "B" : "J") + epoch);
	
		// Print out some of the information...
		StringTokenizer tokens = new StringTokenizer(timeStamp, ":T");
		String dateString = tokens.nextToken();
		String timeString = tokens.nextToken() + ":" + tokens.nextToken() + " UT";
		
		System.err.println(" [" + sourceName + "] observed on " + dateString + " at " + timeString + " by " + observer);
		if(equatorial != null) System.err.println(" Equatorial: " + equatorial.toString());	
		
		// iMJD does not exist in earlier scans
		// convert DATE-OBS into MJD...
		if(header.containsKey("JUL_DAY")) iMJD = header.getIntValue("JUL_DAY");
		else {
			try { iMJD = (int)(AstroTime.forFitsTimeStamp(timeStamp).getMJD());	}
			catch(ParseException e) { throw new HeaderCardException(e.getMessage()); }
		}
			
		addOffsets = hasOption("offsets.add");
		
		// Add on the various additional offsets
		horizontalOffset = new Vector2D(
				header.getDoubleValue("AZO") * Unit.arcsec,
				-header.getDoubleValue("ZAO") * Unit.arcsec);
		horizontalOffset.addX(header.getDoubleValue("AZO_MAP") * Unit.arcsec);
		horizontalOffset.subtractY(header.getDoubleValue("ZAO_MAP") * Unit.arcsec);
		horizontalOffset.addX(header.getDoubleValue("AZO_CHOP") * Unit.arcsec);		
		horizontalOffset.subtractY(header.getDoubleValue("ZAO_CHOP") * Unit.arcsec);
		horizontalOffset.addX(header.getDoubleValue("CHPOFFST") * Unit.arcsec);
		
		Vector2D eqOffset = new Vector2D( 
				header.getDoubleValue("RAO") * Unit.arcsec,
				header.getDoubleValue("DECO") * Unit.arcsec);		
		eqOffset.addX(header.getDoubleValue("RAO_MAP") * Unit.arcsec);
		eqOffset.addY(header.getDoubleValue("DECO_MAP") * Unit.arcsec);
		eqOffset.addX(header.getDoubleValue("RAO_FLD") * Unit.arcsec);
		eqOffset.addY(header.getDoubleValue("DECO_FLD") * Unit.arcsec);

		fixedOffset = new Vector2D(header.getDoubleValue("FAZO") * Unit.arcsec, -header.getDoubleValue("FZAO") * Unit.arcsec);	
		
		DecimalFormat f3_1 = new DecimalFormat(" 0.0;-0.0");

		System.err.println("   AZO =" + f3_1.format(horizontalOffset.getX()/Unit.arcsec)
				+ "\tELO =" + f3_1.format(horizontalOffset.getY()/Unit.arcsec)
				+ "\tRAO =" + f3_1.format(eqOffset.getX()/Unit.arcsec)
				+ "\tDECO=" + f3_1.format(eqOffset.getY()/Unit.arcsec)
				
		);
		
		System.err.println("   FAZO=" + f3_1.format(fixedOffset.getX()/Unit.arcsec)
				+ "\tFZAO=" + f3_1.format(-fixedOffset.getY()/Unit.arcsec)
		);
		
		equatorial.addOffset(eqOffset);
	
		// Add pointing corrections...
		if(hasOption("fazo")) {
			double fazo = option("fazo").getDouble() * Unit.arcsec;
			horizontalOffset.addX(fixedOffset.getX() - fazo);
			fixedOffset.setX(fazo);
		}
		if(hasOption("fzao")) {
			double felo = -option("fzao").getDouble() * Unit.arcsec;
			horizontalOffset.addY(fixedOffset.getY() - felo);
			fixedOffset.setY(felo);
		}
		
		
	}

	
	@Override
	public void editScanHeader(Header header) throws FitsException {	
		super.editScanHeader(header);
		header.addValue("MJD", iMJD, "Modified Julian Day.");
		header.addValue("FAZO", fixedOffset.getX() / Unit.arcsec, "Fixed AZ pointing offset.");
		header.addValue("FZAO", -fixedOffset.getY() / Unit.arcsec, "Fixed ZA pointing offset.");
		header.addValue("ELGAIN", elevationResponse, "Relative response at elevation.");
		header.addValue("TEMPERAT", ambientT / Unit.K, "Ambient temperature (K).");
		header.addValue("PRESSURE", pressure / Unit.mbar, "Atmospheric pressure (mbar).");
		header.addValue("HUMIDITY", humidity, "Humidity (%).");
	}
	
	@Override
	public DataTable getPointingData() {
		DataTable data = super.getPointingData();
		Vector2D pointingOffset = getNativePointingIncrement(pointing);
		
		double sizeUnit = instrument.getSizeUnit();
		String sizeName = instrument.getSizeName();
		
		data.add(new Datum("FAZO", (pointingOffset.getX() + fixedOffset.getX()) / sizeUnit, sizeName));
		data.add(new Datum("FZAO", -(pointingOffset.getY() + fixedOffset.getY()) / sizeUnit, sizeName));
		
		return data;
	}
	
	@Override
	public String getPointingString(Vector2D pointing) {	
		return super.getPointingString(pointing) + "\n\n" +
			"  FAZO --> " + Util.f1.format((pointing.getX() + fixedOffset.getX()) / Unit.arcsec) +
			", FZAO --> " + Util.f1.format(-(pointing.getY() + fixedOffset.getY()) / Unit.arcsec);		
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("FAZO")) return Util.defaultFormat(fixedOffset.getX() / Unit.arcsec, f);
		else if(name.equals("FZAO")) return Util.defaultFormat(-fixedOffset.getY() / Unit.arcsec, f);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	public double getAmbientHumidity() {
		return humidity;
	}

	public double getAmbientPressure() {
		return pressure;
	}

	public double getAmbientTemperature() {
		return ambientT;
	}

	public double getWindDirection() {
		return Double.NaN;
	}

	public double getWindPeak() {
		return Double.NaN;
	}

	public double getWindSpeed() {
		return Double.NaN;
	}
	
	@Override
	protected String getFocusString(InstantFocus focus) {
		String info = "";
		
		/*
		info += "\n";
		info += "  Note: The instant focus feature of CRUSH is still very experimental.\n" +
				"        The feature may be used to guesstimate focus corrections on truly\n" +
				"        point-like sources (D < 4\"). However, the essential focusing\n" +
				"        coefficients need to be better determined in the future.\n" +
				"        Use only with extreme caution, and check suggestions for sanity!\n\n";
		*/
		
		focus = new InstantFocus(focus);
		
		if(focus.getX() != null) {
			DataPoint x = focus.getX();
			x.add(instrument.focusX);
			info += "\n  UIP> x_position " + Util.f2.format(x.value() / Unit.mm) 
					+ "       \t[+-" + Util.f2.format(x.rms() / Unit.mm) + "]";			
		}
		if(focus.getY() != null) {
			DataPoint dy = focus.getY();
			dy.add(instrument.focusYOffset);
			info += "\n  UIP> y_position /offset " + Util.f2.format(dy.value() / Unit.mm)
					+ "\t[+-" + Util.f2.format(dy.rms() / Unit.mm) + "]";	
		}
		if(focus.getZ() != null) {
			DataPoint dz = focus.getZ();
			dz.add(instrument.focusZOffset);
			info += "\n  UIP> focus /offset " + Util.f2.format(dz.value() / Unit.mm)
					+ "    \t[+-" + Util.f2.format(dz.rms() / Unit.mm) + "]";
		}
			
		return info;
	}
	
}
