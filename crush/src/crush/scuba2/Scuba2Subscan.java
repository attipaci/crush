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
import util.*;
import util.astro.EquatorialCoordinates;
import util.astro.HorizontalCoordinates;

import nom.tam.fits.*;
import nom.tam.util.*;

import java.io.*;

public class Scuba2Subscan extends Integration<Scuba2, Scuba2Frame> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6513008414302600380L;
	
	HorizontalCoordinates reuseTrackingCenter = new HorizontalCoordinates();
	
	public Scuba2Subscan(Scuba2Scan parent) {
		super(parent);
	}	
	
	
	@Override
	public void setTau() throws Exception {
		super.setTau();
		printEquivalentTaus();	
	}
	
	public void printEquivalentTaus() {
		System.err.println("   --->"
				+ " tau(186GHz):" + Util.f3.format(getTau("186ghz"))
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz"))
				+ ", PWV:" + Util.f2.format(getTau("pwv")) + "mm"
		);	
		
		/*
		System.err.println("   --->"
				+ " tau(186GHz):" + Util.f3.format(getTau("186ghz"))
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz"))
				+ ", tau(LOS):" + Util.f3.format(zenithTau / scan.horizontal.sinLat)
				+ ", PWV:" + Util.f2.format(getTau("pwv")) + "mm"
		);
		*/	
	}
	
	@Override
	public Scuba2Frame getFrameInstance() {
		return new Scuba2Frame((Scuba2Scan) scan);
	}

	
	protected void read(ImageHDU dataHDU, BinaryTableHDU hdu) throws IllegalStateException, HeaderCardException, FitsException {		
		Header header = dataHDU.getHeader();
		integrationNo = header.getIntValue("NSUBSCAN") - 1;
		
		// TODO chop phase and beam (L/R/M?)...	
		final Scuba2Scan scuba2Scan = (Scuba2Scan) scan;
		
		if(hasOption("subscan.minlength"))
			if(header.getIntValue("NAXIS3") * instrument.samplingInterval < option("subscan.minlength").getDouble() * Unit.s)
				throw new IllegalStateException(" Subscan " + getID() + " is less than " + option("subscan.minlength").getDouble() + "s long. Skipping.");
		
		int[][][] data = (int[][][]) dataHDU.getData().getData();
			
		System.err.println("   Subscan " + getID() + ": " + data.length + " frames at " + Util.f1.format(1.0 / instrument.integrationTime / Unit.Hz) + " Hz ---> " 
				+ Util.f1.format(instrument.samplingInterval * data.length / Unit.s) + " seconds.");
	
		
		Object[] table = (Object[]) ((ColumnTable) hdu.getData().getData()).getRow(0);
	
		integrationNo = dataHDU.getHeader().getIntValue("NSUBSCAN") - 1;
		
		boolean isEquatorial = scuba2Scan.trackingClass == EquatorialCoordinates.class;
		
		final double[] RA = isEquatorial ? (double[]) table[hdu.findColumn("TCS_TR_AC1")] : null;
		final double[] DEC = isEquatorial ? (double[]) table[hdu.findColumn("TCS_TR_AC2")] : null;
		final double[] AZ = (double[]) table[hdu.findColumn("TCS_AZ_AC1")];
		final double[] EL = (double[]) table[hdu.findColumn("TCS_AZ_AC2")];
		final double[] tAZ = (double[]) table[hdu.findColumn("TCS_AZ_BC1")];
		final double[] tEL = (double[]) table[hdu.findColumn("TCS_AZ_BC2")];
		final double[] MJD = (double[]) table[hdu.findColumn("TCS_TAI")];
		final int[] SN = (int[]) table[hdu.findColumn("RTS_NUM")];
		final int iDT = hdu.findColumn("SC2_MIXTEMP");
		final float[] DT = iDT < 0 ? null : (float[]) table[iDT];
	
		/*
		final double[] CX = (double[]) table[hdu.findColumn("SMU_AZ_CHOP_X")];
		final double[] CY = (double[]) table[hdu.findColumn("SMU_AZ_CHOP_Y")];
		final double[] CR = (double[]) table[hdu.findColumn("SMU_TR_CHOP_X")];
		final double[] CD = (double[]) table[hdu.findColumn("SMU_TR_CHOP_Y")];
		
		final double[] JX = (double[]) table[hdu.findColumn("SMU_AZ_JIG_X")];
		final double[] JY = (double[]) table[hdu.findColumn("SMU_AZ_JIG_Y")];
		final double[] JR = (double[]) table[hdu.findColumn("SMU_TR_JIG_X")];
		final double[] JD = (double[]) table[hdu.findColumn("SMU_TR_JIG_Y")];
		*/
		
		clear();
		ensureCapacity(data.length);

		
		//final Vector2D equatorialChop = new Vector2D();
		
		for(int i=0; i<data.length; i++) {
			// Check to see if the frame has valid astrometry...
			if(Double.isNaN(AZ[i])) { add(null); return; }
			if(Double.isNaN(RA[i])) { add(null); return; }
			
			final Scuba2Frame frame = new Scuba2Frame(scuba2Scan);

			// parse the data:
			frame.parseData(data[i]);
			
			//final double UT = (((double[]) row[iUT])[0] * Unit.sec) % Unit.day;
			frame.MJD = MJD[i];
			
			frame.horizontal = new HorizontalCoordinates(AZ[i], EL[i]);
			if(isEquatorial) frame.equatorial = new EquatorialCoordinates(RA[i], DEC[i], scan.equatorial.epoch);
			frame.horizontalOffset = new Vector2D((AZ[i] - tAZ[i]) * frame.horizontal.cosLat(), EL[i] - tEL[i]);
			
			/*
			frame.chopperPosition.x = ((double[])row[iCX])[0] + ((double[])row[iJX])[0];
			frame.chopperPosition.x = ((double[])row[iCY])[0] + ((double[])row[iJY])[0];
			frame.horizontal.addOffset(frame.chopperPosition);
			
			equatorialChop.x = ((double[])row[iCR])[0] + ((double[])row[iJR])[0];
			equatorialChop.x = ((double[])row[iCD])[0] + ((double[])row[iJD])[0];
			frame.equatorial.addOffset(equatorialChop);
			*/
			
			frame.calcParallacticAngle();

			// TODO calculate
			// frame.LST = ???

			frame.frameNumber = SN[i];
			if(DT != null) frame.detectorT = DT[i];
			
			add(frame);
		}

		if(isEmpty()) {
			System.err.println("   Subscan has no ancillary coordinate information. Dropping subscan.");
		}
	}

	
	public void temperatureCorrect() {
		System.err.println("   Correcting for temperature drifts.");
		
		Response mode = (Response) instrument.modalities.get("temperature").get(0);
		Signal signal = signals.get(mode);
		if(signal == null) signal = mode.getSignal(this);
		
		signal.level(false);
		double rmsHe3 = signal.getRMS();
		System.err.println("   RMS He3 temperature drift is " + Util.f3.format(1e3 * rmsHe3) + " mK.");
		
		for(Scuba2Frame exposure : this) if(exposure != null) {
			double dT = signal.valueAt(exposure);
			if(!Double.isNaN(dT)) for(Scuba2Pixel pixel : instrument) exposure.data[pixel.index] -= pixel.temperatureGain * dT;
		}
	}
	
	
	public void writeTemperatureGains() throws IOException {
		// Now write to a file
		String fileName = CRUSH.workPath + File.separator + "temperature-gains-" + scan.getID() + ".dat";
		try { instrument.writeTemperatureGains(fileName, getASCIIHeader()); }
		catch(IOException e) { e.printStackTrace(); }
	}
	

	@Override
	public void writeProducts() {
		super.writeProducts();
		if(hasOption("write.tgains")) {
			try { writeTemperatureGains(); }
			catch(IOException e) { System.err.println("WARNING! Problem writing temperature gains."); }
		}
	}
	
	@Override
	public String getFullID(String separator) {
		return super.getFullID(separator) + separator + instrument.filter;
	}
	
	@Override
	public String getDisplayID() {
		return super.getFullID("|");
	}
	
}
