/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of the proprietary SCUBA-2 modules of crush.
 * 
 * You may not modify or redistribute this file in any way. 
 * 
 * Together with this file you should have received a copy of the license, 
 * which outlines the details of the licensing agreement, and the restrictions
 * it imposes for distributing, modifying or using the SCUBA-2 modules
 * of CRUSH-2. 
 * 
 * These modules are provided with absolutely no warranty.
 ******************************************************************************/
package crush.scuba2;

import crush.*;
import nom.tam.fits.*;
import util.*;
import util.astro.AstroCoordinateID;
import util.astro.CoordinateEpoch;
import util.astro.EquatorialCoordinates;
import util.astro.GeodeticCoordinates;
import util.astro.HorizontalCoordinates;
import util.astro.Weather;

import java.io.*;
import java.util.*;


public class Scuba2Scan extends Scan<Scuba2, Scuba2Subscan> implements GroundBased, Weather {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1608680718251250629L;

	String date, endTime;
	String obsMode, scanPattern;
	int iDate;
	
	double tau225GHz, tau186GHz;
	double ambientT, pressure, humidity, windAve, windPeak, windDirection;
	String scanID;
	File fitsTemp;
	int blankingValue;
	
	public Class<? extends SphericalCoordinates> trackingClass;
	
	public Scuba2Scan(Scuba2 instrument) {
		super(instrument);
	}
	
	@Override
	public Scuba2Subscan getIntegrationInstance() {
		return new Scuba2Subscan(this);
	}

	@Override
	public void read(String scanDescriptor, boolean readFully) throws IOException, HeaderCardException, FitsException, FileNotFoundException {
		ArrayList<File> files = getFiles(scanDescriptor);
		
		for(int i=0; i<files.size(); i++) {
			File file = files.get(i);

			// If it's an SDF, check if an equivalent FITS exists also...
			// If so, then we can skip the SDF, and wait for the FITS to be read...
			if(file.getName().endsWith(".sdf"))
				if(new File(getFitsName(file.getName())).exists()) continue;
			
			Fits fits = getFits(file);
			if(fits == null) continue;
			
			// If converting only, than close the FITS file and move on...
			if(hasOption("convert")) if(fitsTemp != null) {
				try { fits.getStream().close(); }
				catch(IOException e) {}
				fitsTemp = null;
				continue;				
			}
			
			// Get the header information from the first file in this scan...
			if(i==0) {
				BasicHDU mainHDU = fits.getHDU(0);
				parseScanPrimaryHDU(mainHDU);
				instrument.parseScanPrimaryHDU(mainHDU);
				instrument.validate(this);	
				clear();
			}
					
			// Read the data contained in the FITS...
			try { readSubscan(fits); }
			catch(IllegalStateException e) { 
				System.err.println("   WARNING! " + e.getMessage() + " Skipping.");
			}
			
			// Try close the FITS stream so that it can be garbage collected.
			try { fits.getStream().close(); }
			catch(IOException e) {}
			
			// Remove the temporary FITS file, as it is no longer needed...
			if(fitsTemp != null) if(fitsTemp.exists()) {
				fitsTemp.delete();
				fitsTemp = null;
			}
		}
		Collections.sort(this);
		if(hasOption("subscans.merge")) mergeSubscans();
		if(!isEmpty()) validate();
	}
	
	
	
	@Override
	public void validate() {
		Scuba2Frame firstFrame = getFirstIntegration().getFirstFrame();
		Scuba2Frame lastFrame = getLastIntegration().getLastFrame();

		if(horizontal == null) {
			horizontal = new HorizontalCoordinates();
			horizontal.setLongitude(0.5*(firstFrame.horizontal.getX() + lastFrame.horizontal.getX()));
			horizontal.setLatitude(0.5*(firstFrame.horizontal.getY() + lastFrame.horizontal.getY()));
		}
			
		double PA = 0.5 * (firstFrame.getParallacticAngle() + lastFrame.getParallacticAngle());
		System.err.println("   Mean parallactic angle is " + Util.f1.format(PA / Unit.deg) + " deg.");
		
		super.validate();
	}

	public ArrayList<File> getFiles(String scanDescriptor) throws FileNotFoundException {
		ArrayList<File> scanFiles = new ArrayList<File>();

		String path = getDataPath();
		descriptor = scanDescriptor;

		String subarray = hasOption("450um") ? "s4a" : "s8d";
		
		// Try to read scan number with the help of 'object' and 'date' keys...
		try {
			String scanNo = Util.d5.format(Integer.parseInt(scanDescriptor));
			if(hasOption("date")) {
				File directory = new File(path);
				String date = option("date").getValue();
				String convention = subarray + date + "_" + scanNo + "_";
				
				if(!directory.exists()) {
					String message = "Cannot find scan directory " + path +
						"\n    * Check that 'datapath' is correct:" + 
						"\n      --> datapath = '" + option("datapath").getValue() + "'";
					throw new FileNotFoundException(message);
				}
				else if(!directory.isDirectory()) {
					throw new FileNotFoundException(path + " is not a directory.");
				}
				else {
					String[] files = directory.list();
					for(int i=0; i<files.length; i++) if(files[i].startsWith(convention))
						scanFiles.add(new File(path + File.separator + files[i]));
		
					if(scanFiles.isEmpty()) 
						throw new FileNotFoundException("Cannot find matching files in " + path);
				}
			}
			else {
				String message = "Cannot find scan " + scanDescriptor;

				if(!hasOption("date")) 
					message += "\n    * Specify 'date' for unique JCMT scan ID.";
			
				throw new FileNotFoundException(message);
			}
		}
		// Otherwise, just read as file names...
		catch(NumberFormatException e) {
			File scanFile = new File(scanDescriptor) ;	
			if(!scanFile.exists()) {
				scanFile = new File(path + scanDescriptor);
				if(!scanFile.exists()) throw new FileNotFoundException("Could not find scan " + scanDescriptor);
			} 	
			
			scanFiles.add(scanFile);
		}
		
		Collections.sort(scanFiles);
		
		return scanFiles;
	}	

	public boolean isSDF(File file) {
		return file.getName().endsWith(".sdf");		
	}
	
	public String getFitsName(String sdfName) {
		return sdfName.substring(0, sdfName.length() - 4) + ".fits";		
	}
	
	private Fits getFits(File file) throws IOException, FileNotFoundException, FitsException {
		String fileName = file.getName();
		if(fileName.endsWith(".sdf.gz")) 
			throw new IOException("Uncompress SDF '" + fileName + "' before use.");
	
		if(!isSDF(file)) {
			boolean isCompressed = fileName.endsWith(".gz");
			System.out.println(" Reading " + file.getPath() + "...");
			return new Fits(file, isCompressed);
		}
		
		
		if(hasOption("ndf2fits")) {
			String inName = file.getAbsolutePath();
			String command = option("ndf2fits").getValue();
			String outName = getFitsName(inName);
				
			// Check if the SDF has a FITS equivalent in the same directory...
			File outFile = new File(outName);
			if(outFile.exists()) return new Fits(outFile);
			// We could also return null to just let go of the SDF, and wait for the FITS...
			//if(outFile.exists()) return null;
		
			// Try the same in the default working directory of CRUSH...
			String path = hasOption("outpath") ? option("outpath").getPath() : getDataPath();
			
			outName = path + File.separator + getFitsName(file.getName());
			outFile = new File(outName);
			// check if a FITS exists in the temporaty directory...
			if(outFile.exists()) return new Fits(outFile);
			
			Runtime runtime = Runtime.getRuntime();
				
			String commandLine = command + " " + inName + " " + outName + " proexts";
			String[] commandArray = { command, inName, outName, "proexts" };
			
			System.err.println(" Converting SDF to FITS...");
			System.err.println(" > " + commandLine);
				
			Process convert = runtime.exec(commandArray); 
			//BufferedReader err = new BufferedReader(new InputStreamReader(convert.getErrorStream()));
				
			//String line = null;
			//while((line = err.readLine()) != null) System.err.println("> " + line);
				
			try { 
				int retval = convert.waitFor(); 
				if(retval != 0) {
					System.err.println("WARNING! Conversion error. Check that 'ndf2fits' is correct, and that");
					System.err.println("         the 'datapath' directory is writeable.");
					if(outFile.exists()) outFile.delete();
					throw new IOException("SDF to FITS conversion error.");
				}
				fitsTemp = new File(outName);
				return new Fits(fitsTemp);
			}
			catch(InterruptedException e) {
				System.err.println("Interrupted!");
				System.exit(1);
			}
		}
		return null;
	}
	

	protected void readSubscan(Fits fits) throws IllegalStateException, HeaderCardException, FitsException {
		// Read in entire FITS file
		BasicHDU[] HDU = fits.read();
		if(HDU == null) throw new IllegalStateException("FITS has no content.");
		
		Scuba2Subscan integration = new Scuba2Subscan(this);	
		integration.read((ImageHDU) HDU[0], getJcmtHDU(HDU));
		
		if(!integration.isEmpty()) {
			integration.validate();
			add(integration);
		}
	}
	
	public BinaryTableHDU getJcmtHDU(BasicHDU[] HDU) {
		for(int i=1; i<HDU.length; i++) {
			String extName = HDU[i].getHeader().getStringValue("EXTNAME");
			if(extName != null) if(extName.endsWith("JCMTSTATE")) return (BinaryTableHDU) HDU[i];
		}
		return null;		
	}

	
	protected void parseScanPrimaryHDU(BasicHDU hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();

		// Scan Info
		setSerial(header.getIntValue("OBSNUM"));
		if(instrument.options.containsKey("serial")) instrument.setSerialOptions(getSerial());
	
		site = new GeodeticCoordinates(header.getDoubleValue("LONG-OBS") * Unit.deg, header.getDoubleValue("LAT-OBS") * Unit.deg);
		creator = header.getStringValue("ORIGIN");
		//observer = header.getStringValue("OBSERVER");
		project = header.getStringValue("PROJECT");
		//descriptor = header.getStringValue("DESCRIPT");
		//scanID = header.getStringValue("SCANID");
		
		if(creator == null) creator = "Unknown";
		if(observer == null) observer = "Unknown";
		if(project == null) project = "Unknown";
		
		setSourceName(header.getStringValue("OBJECT"));
		date = header.getStringValue("DATE-OBS");
		endTime = header.getStringValue("DATE-END");
		iDate = header.getIntValue("UTDATE");
		scanID = header.getStringValue("OBSID");
		
		blankingValue = header.getIntValue("BLANK");
		
		// INSTAP_X, Y instrument aperture offsets. Kinda like FAZO, FZAO?
		
		System.err.println(" [" + getSourceName() + "] observed on " + date);
		String trackingSystem = header.getStringValue("TRACKSYS");
		
		if(trackingSystem == null) {
			trackingClass = null;
			horizontal = new HorizontalCoordinates(0.0, 0.0);
		}
		else if(trackingSystem.equals("AZEL")) {
			trackingClass = HorizontalCoordinates.class;
			horizontal = new HorizontalCoordinates(
					header.getDoubleValue("BASEC1") * Unit.deg,
					header.getDoubleValue("BASEC2") * Unit.deg
			);
			System.err.println(" Horizontal: " + horizontal.toString());	
		}
		else {
			trackingClass = EquatorialCoordinates.class;
			double mjdDay = header.getDoubleValue("TMORG_A");
			double year = (mjdDay - CoordinateEpoch.J2000.getMJD()) / 365.25;
			
			String epoch = trackingSystem.equals("APP") ? "J" + Util.f1.format(year) : "J2000";
			
			equatorial = new EquatorialCoordinates(
				header.getDoubleValue("BASEC1") * Unit.deg,
				header.getDoubleValue("BASEC2") * Unit.deg,
				epoch
			);
			System.err.println(" Equatorial: " + equatorial.toString());	
		}

		// Weather
		
		if(hasOption("tau.186ghz")) tau186GHz = option("tau.186ghz").getDouble();
		else {
			tau186GHz = 0.5 * (header.getDoubleValue("WVMTAUST") + header.getDoubleValue("WVMTAUEN"));
			instrument.options.process("tau.186ghz", tau186GHz + "");
		}
		
		if(hasOption("tau.225ghz")) tau225GHz = option("tau.225ghz").getDouble();
		else {
			tau225GHz = 0.5 * (header.getDoubleValue("TAU225ST") + header.getDoubleValue("TAU225EN"));
			instrument.options.process("tau.225ghz", tau225GHz + "");
		}
		
		System.err.println(" tau(225GHz)=" + Util.f3.format(tau225GHz) + ", tau(186GHz)=" + Util.f3.format(tau186GHz));
		
		ambientT = 0.5 * (header.getDoubleValue("ATSTART") + header.getDoubleValue("ATEND")) * Unit.K + 273.16 * Unit.K;
		pressure = 0.5 * (header.getDoubleValue("BPSTART") + header.getDoubleValue("BPEND")) * Unit.mbar;
		humidity = 0.5 * (header.getDoubleValue("HUMSTART") + header.getDoubleValue("HUMEND"));
		windAve = 0.5 * (header.getDoubleValue("WINDSPDST") + header.getDoubleValue("WINDSPDEN")) * Unit.km / Unit.hour;
		windDirection = 0.5 * (header.getDoubleValue("WINDDIRST") + header.getDoubleValue("WINDDIREN")) * Unit.deg;
		
		obsMode = header.getStringValue("SAM_MODE");
		// + Switching mode
		// + Chopper, jiggler parameters
		// + Scan details
		scanPattern = header.getStringValue("SCAN_PAT");

		// + pointing offsets in the SMU section...
		
		isTracking = true;
		
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
		return windDirection;
	}

	public double getWindPeak() {
		return windPeak;
	}

	public double getWindSpeed() {
		return windAve;
	}

	
	@Override 
	public int compareTo(Scan<?,?> scan) {
		Scuba2Scan scubascan = (Scuba2Scan) scan;
		if(iDate != scubascan.iDate) return iDate < scubascan.iDate ? -1 : 1;
		if(getSerial() == scan.getSerial()) return 0;
		return getSerial() < scan.getSerial() ? -1 : 1;
		
	}
	
	@Override
	public String getID() {
		return iDate + "." + getSerial();
	}
	
	@Override
	public void setSourceModel(SourceModel model) {
		super.setSourceModel(model);
		sourceModel.id = instrument.filter;
	}	
	
	@Override
	public void editScanHeader(Header header) throws FitsException {	
		super.editScanHeader(header);
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		if(name.equals("obsmode")) return obsMode;
		else if(name.equals("obspattern")) return scanPattern;
		else if(name.equals("dir")) return AstroCoordinateID.getSimpleID(trackingClass);
		else return super.getFormattedEntry(name, formatSpec);
	}

	
}
