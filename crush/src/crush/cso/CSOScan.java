package crush.cso;

import java.io.IOException;
import java.text.NumberFormat;

import nom.tam.fits.FitsException;
import nom.tam.fits.Header;

import crush.ElevationCouplingCurve;
import crush.GroundBased;
import crush.HorizontalFrame;
import crush.InstantFocus;
import crush.Scan;
import kovacs.util.DataTable;
import kovacs.util.SphericalCoordinates;
import kovacs.util.Unit;
import kovacs.util.Util;
import kovacs.util.Vector2D;
import kovacs.util.astro.AstroSystem;
import kovacs.util.astro.GeodeticCoordinates;
import kovacs.util.astro.HorizontalCoordinates;
import kovacs.util.astro.Weather;
import kovacs.util.data.DataPoint;
import kovacs.util.text.TableFormatter;

public abstract class CSOScan<InstrumentType extends CSOArray<?>, IntegrationType extends CSOIntegration<InstrumentType,?>> 
extends Scan<InstrumentType, IntegrationType> implements GroundBased, Weather {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4194847199755728474L;

	protected double tau225GHz;
	protected double ambientT, pressure, humidity;
	
	public Class<? extends SphericalCoordinates> scanSystem;
	
	public Vector2D horizontalOffset, fixedOffset;
	public double elevationResponse = 1.0;

	public boolean addOffsets = true;
	public int iMJD;	

	
	public CSOScan(InstrumentType instrument) {
		super(instrument);
		
		site = new GeodeticCoordinates(
				-(155.0 * Unit.deg + 28.0 * Unit.arcmin + 33.0 * Unit.arcsec), 
				19.0 * Unit.deg  + 49.0 * Unit.arcmin + 21.0 * Unit.arcsec);
		
		isTracking = true;
	}


	@Override
	public void calcHorizontal() {
		HorizontalFrame firstFrame = getFirstIntegration().getFirstFrame();
		HorizontalFrame lastFrame = getLastIntegration().getLastFrame();
		
		horizontal = new HorizontalCoordinates(
				0.5 * (firstFrame.horizontal.getX() + lastFrame.horizontal.getX()),  
				0.5 * (firstFrame.horizontal.getY() + lastFrame.horizontal.getY())
		);
			
	}
	
	@Override
	public void validate() {
		super.validate();	
		
		if(hasOption("elevation-response")) {
			try { 
				String fileName = option("elevation-response").getPath();
				elevationResponse = new ElevationCouplingCurve(fileName).getValue(horizontal.elevation()); 
				System.err.println("   Relative beam efficiency is " + Util.f3.format(elevationResponse));
				for(CSOIntegration<?,?> integration : this) integration.gain *= elevationResponse;
			}
			catch(IOException e) { 
				System.err.println("WARNING! Cannot read elevation response table..."); 
				e.printStackTrace();
			}
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
		
		data.new Entry("FAZO", (pointingOffset.getX() + fixedOffset.getX()) / sizeUnit, sizeName);
		data.new Entry("FZAO", -(pointingOffset.getY() + fixedOffset.getY()) / sizeUnit, sizeName);
		
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
		else if(name.equals("dir")) return AstroSystem.getID(scanSystem);
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
	
	public static final int SCAN_UNDEFINED = -1;
	public static final int SCAN_ALTAZ = 0;
	public static final int SCAN_EQ2000 = 1;
	public static final int SCAN_GAL = 2;
	public static final int SCAN_APPARENT_EQ = 3;
	public static final int SCAN_EQ1950 = 4;
	
}
