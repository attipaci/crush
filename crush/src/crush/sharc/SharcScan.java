/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sharc;

import java.io.DataInput;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.StringTokenizer;
import java.io.File;

import crush.DualBeam;
import crush.cso.CSOScan;
import jnum.Configurator;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroTime;
import jnum.astro.BesselianEpoch;
import jnum.astro.CelestialCoordinates;
import jnum.astro.CoordinateEpoch;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.FocalPlaneCoordinates;
import jnum.astro.HorizontalCoordinates;
import jnum.astro.JulianEpoch;
import jnum.math.Coordinate2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.text.FixedLengthFormat;
import jnum.text.TimeFormat;

public class SharcScan extends CSOScan<Sharc, SharcIntegration> implements DualBeam {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1872788122815053272L;

	SharcFile file;
	String qual;
	EquatorialCoordinates trackingEquatorial;
	HorizontalCoordinates horizontalStart, horizontalEnd;
	Vector2D equatorialOffset;
	int index;

	short header_records;
	int year, ut_day;
	short otype, npixels, reference_pixel, badpixels[] = new short[2 * Sharc.pixels];
	short first_scan, quadrature, sumrs, ncycles;
	int nsamples, chops_per_integer, number;
	float filter;
	double chop_frequency, ut_time;
	double glo, gbo;
	float focus, focus_offset;
	double chopper_throw;
	float scale_factor, otf_longitude_rate;
	float otf_latitude_rate, otf_longitude_step, otf_latitude_step;
	
	double[][] offsets; // records x pixels
	
		
	public SharcScan(Sharc instrument, SharcFile file) {
		super(instrument);
		this.file = file;
	}
	
	@Override
	public String getID() {
		return getFitsDateString() + "." + super.getID();
	}
	
	public void readScanRow(DataInput in) throws IOException {
		ensureCapacity(ncycles);
		SharcIntegration integration = new SharcIntegration(this);
		integration.readFrom(in);
		add(integration);
	}
	
	
	@Override
	public void validate() {
		System.out.println();
		
		if(hasOption("chopper.throw")) chopper_throw = option("chopper.throw").getDouble() * instrument.getSizeUnitValue();
		
		printInfo(System.out);
		
		String filterName = Integer.toString((int)filter) + "um";
		info("Setting options for " + filterName + " filter.");
		if(!hasOption(filterName)) instrument.getOptions().parseSilent(filterName);
		
		super.validate();		
	}
	
	public void readHeader(DataInput in, int index) throws IOException {		
		this.index = index;
		setSerial(index);
		
		// Turn off configuration verbosity...
		Configurator.verbose = false;
		
		// Populate instrument with pixels...
		for(int c=0; c<Sharc.pixels; c++) instrument.add(new SharcPixel(instrument, c));
		
		byte[] nameBytes = new byte[20];
		byte[] qualBytes = new byte[10];
		
		in.readFully(nameBytes);
		in.readFully(qualBytes);
		
		setSourceName(new String(nameBytes).trim());
		
		qual = new String(qualBytes).trim();
		
		header_records = (short) (in.readShort() - 2);
		year = in.readInt();
		ut_day = in.readInt();
		
		otype = in.readShort();
		npixels = in.readShort();
		reference_pixel = in.readShort();
		for(int i=0; i<badpixels.length; i++) badpixels[i] = in.readShort();
		for(int c=0; c<Sharc.pixels; c++) instrument.get(c).isBad = (badpixels[c] != 0) ;
		
		scanSystem = getScanSystem(in.readShort());
		first_scan = in.readShort();
		quadrature = in.readShort();
		sumrs = in.readShort();
		ncycles = in.readShort();
		
		nsamples = in.readInt();
		chops_per_integer = in.readInt();
		number = in.readInt();
		
		filter = in.readFloat();
		
		chop_frequency = in.readDouble();
		
		instrument.samplingInterval = chops_per_integer / chop_frequency;
		if(quadrature != 0) instrument.samplingInterval *= 0.5;
		instrument.integrationTime = instrument.samplingInterval;
			
		ut_time = 12.0 * in.readDouble() / Math.PI;
		LST = 12.0 * Unit.hour * in.readDouble() / Math.PI;
		
		
		int dy = year - 2000;
		int leapDays = dy > 0 ?  (dy+3)/4 : dy/4 - 1;
		int days2000 = dy * 365 + leapDays + (ut_day-1);
		
		long millis = Math.round(AstroTime.millis0UTC1Jan2000 + (days2000 * Unit.day + ut_time * Unit.hour) / Unit.ms);
		AstroTime time = new AstroTime();
		time.setUTCMillis(millis);
		timeStamp = time.getFitsTimeStamp();
		
		setMJD(time.getMJD());
		
		// Really iMJD is the MJD for the calendar date here...
		iMJD = (int) AstroTime.mjdJ2000 + days2000;	
		
		equatorial = new EquatorialCoordinates((hasOption("moving") ? -1.0 : 1.0) * in.readDouble(), in.readDouble());
		double epoch = in.readDouble();
		if(hasOption("moving")) epoch = year + ut_day / 365.25;
		equatorial.epoch = epoch < 1984.0 ? new BesselianEpoch(epoch) : new JulianEpoch(epoch);
		equatorial.precess(CoordinateEpoch.J2000);
		trackingEquatorial = (EquatorialCoordinates) equatorial.clone();
		
		horizontalStart = new HorizontalCoordinates(in.readDouble(), in.readDouble());
		horizontalEnd = new HorizontalCoordinates(in.readDouble(), in.readDouble());
			
		double rao = in.readDouble();
		double deco = in.readDouble();
		double maprao = in.readDouble();
		double mapdeco = in.readDouble();
		double fieldrao = in.readDouble();
		double fielddeco = in.readDouble();
		
		equatorialOffset = new Vector2D(rao + maprao + fieldrao, deco + mapdeco + fielddeco);	
		equatorial.addOffset(equatorialOffset);
		
		glo = in.readDouble();
		gbo = in.readDouble();
		
		horizontalOffset = new Vector2D(in.readDouble(), -in.readDouble());
		fixedOffset = new Vector2D(in.readFloat(), -in.readFloat());
		
		if(hasOption("fazo")) {
			double fazo = option("fazo").getDouble() * Unit.arcsec;
			horizontalOffset.addX(fixedOffset.x() - fazo);
			fixedOffset.setX(fazo);
		}
		if(hasOption("fzao")) {
			double felo = -option("fzao").getDouble() * Unit.arcsec;
			horizontalOffset.addY(fixedOffset.y() - felo);
			fixedOffset.setY(felo);
		}
		
		focus = in.readFloat();
		focus_offset = in.readFloat();
		
		chopper_throw = in.readDouble();
		instrument.rotatorAngle = in.readDouble();
		instrument.rotatorMode = "n/a";
		
		scale_factor = in.readFloat();	
		
		// Weather
		tau225GHz = in.readFloat();
		
		if(hasOption("tau.225ghz")) tau225GHz = option("tau.225ghz").getDouble();
		else instrument.setOption("tau.225ghz=" + tau225GHz);
		
		otf_longitude_rate = in.readFloat();
		otf_latitude_rate = in.readFloat();
		otf_longitude_step = in.readFloat();
		otf_latitude_step = in.readFloat();
		
		if(quadrature != 0) otf_longitude_step *= 0.5;
		
		//System.err.println("# " + getSourceName() + " " + ncycles + " " + nsamples);
		
		if(header_records < 0) throw new IOException("corrupted data?");

		if(header_records > 0) {
			offsets = new double[header_records][Sharc.pixels];
			for(int i=0; i<header_records; i++) for(int c=0; c<Sharc.pixels; c++) offsets[i][c] = in.readDouble();
		}
		
		// Turn back on the configuration verbosity...
		Configurator.verbose = true;
	}
	
	public void printInfo(PrintStream out) {
		String fileName = file == null ? "<unknown>" : new File(file.fileName).getName();
		
		out.println(" Scan #" + getSerial() + " in " + fileName); 

		// Print out some of the information...
		StringTokenizer tokens = new StringTokenizer(timeStamp, ":T");
		String dateString = tokens.nextToken();
		String timeString = tokens.nextToken() + ":" + tokens.nextToken() + " UT";
				
		out.println("   [" + getSourceName() + "] observed on " + dateString + " at " + timeString);

		out.println("   Equatorial: " + trackingEquatorial);
		
		horizontal = new HorizontalCoordinates(
			0.5 * (horizontalStart.x() + horizontalEnd.x()),
			0.5 * (horizontalStart.y() + horizontalEnd.y())
		);
		
		out.println("   Horizontal: " + horizontal);
		
		DecimalFormat f3_1 = new DecimalFormat(" 0.0;-0.0");
		
		out.println("     AZO =" + f3_1.format(horizontalOffset.x()/Unit.arcsec)
				+ "\tELO =" + f3_1.format(horizontalOffset.y()/Unit.arcsec)
				+ "\tRAO =" + f3_1.format(equatorialOffset.x()/Unit.arcsec)
				+ "\tDECO=" + f3_1.format(equatorialOffset.y()/Unit.arcsec)
				
		);
		
		out.println("     FAZO=" + f3_1.format(fixedOffset.x()/Unit.arcsec)
				+ "\tFZAO=" + f3_1.format(-fixedOffset.y()/Unit.arcsec)
				+ "\tFocZ= " + focus + " mm\tdZ  = " + focus_offset + " mm."
		);
		
		
		out.println("   Filter: " + (int)filter + "um, Rotation: " + Util.f1.format(instrument.rotatorAngle / Unit.deg) + " deg, Reference pixel: " + reference_pixel);
		
		double delta = otf_longitude_step * nsamples;
		if(quadrature != 0) delta *= 2.0;
		
		SphericalCoordinates basisCoords = new HorizontalCoordinates();
		try { basisCoords = scanSystem.newInstance(); }
		catch(Exception e) {}
		String longitudeName = basisCoords.getCoordinateSystem().get(0).getShortLabel();
		
		out.println("   Chop: " + Util.f1.format(chopper_throw / Unit.arcsec) + "\" at " + Util.f3.format(chop_frequency) + " Hz" +
				", Scan: " + (int)Math.round(delta / Unit.arcsec) + "\" in " + longitudeName);
		
	
	}
	

	@Override
	public void read(String descriptor, boolean readFully) throws Exception {
		throw new UnsupportedOperationException("Use instrument.readScan() instead.");
	}

	@Override
	public SharcIntegration getIntegrationInstance() {
		return new SharcIntegration(this);
	}

	
	
	// TODO a 1-line summary of the scan...
	@Override
	public String toString() {
		NumberFormat serialFormat = new FixedLengthFormat(new DecimalFormat("#"), 4);
		NumberFormat sizeFormat = new FixedLengthFormat(new DecimalFormat("#"), 3);
		TimeFormat tf = new TimeFormat(0);
		tf.colons();
		
		
		String name = getSourceName() + "            ";
		name = name.substring(0, 12);
			
		return serialFormat.format(index)
				+ " " + getFitsDateString()
				+ " " + tf.format(ut_time * 3600.0)
				+ " " + name
				+ " " + Util.f3.format(tau225GHz)
				+ " " + sizeFormat.format(ncycles) + " x " + sizeFormat.format(nsamples)
				+ " " + Util.f1.format(chopper_throw / Unit.arcsec) + "\" " + Util.f3.format(chop_frequency) + " Hz";
	}

	@Override
	public double getChopSeparation() {
		return chopper_throw;
	}

	@Override
	public double getChopAngle(Coordinate2D coordinates) {
		if(coordinates instanceof HorizontalCoordinates) return 0.0;
		if(coordinates instanceof FocalPlaneCoordinates) return -instrument.rotatorAngle;
		if(coordinates instanceof CelestialCoordinates) return getPA() - ((CelestialCoordinates) coordinates).getEquatorialPositionAngle();
		return Double.NaN;
	}
	
	
	/**
	 * Gets the simple date.
	 *
	 * @return the simple date
	 */
	public String getFitsDateString() {
		return AstroTime.fitsDateFormatter.format(AstroTime.getTTMillis(iMJD + 0.5));
	}
	
	
}
