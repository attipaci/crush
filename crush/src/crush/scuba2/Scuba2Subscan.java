/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.scuba2;

import crush.*;
import nom.tam.fits.*;
import nom.tam.util.*;

import java.util.ArrayList;
import java.util.Arrays;

import kovacs.astro.AstroTime;
import kovacs.astro.HorizontalCoordinates;
import kovacs.math.Vector2D;
import kovacs.util.*;

public class Scuba2Subscan extends Integration<Scuba2, Scuba2Frame> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6513008414302600380L;
	
	ArrayList<Scuba2Fits> files = new ArrayList<Scuba2Fits>(4);
	
	public double totalIntegrationTime;
	public int rawFrames;

	int[] readoutLevel;
	
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
				+ " tau(183GHz):" + Util.f3.format(getTau("183ghz"))
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz"))
				+ ", PWV:" + Util.f2.format(getTau("pwv")) + "mm"
		);	
	
	}
	
	@Override
	public Scuba2Frame getFrameInstance() {
		return new Scuba2Frame((Scuba2Scan) scan);
	}
	
	
	public void read() throws Exception {
		clear();
		
		Scuba2Scan scuba2Scan = (Scuba2Scan) scan;
			
		readoutLevel = new int[scuba2Scan.subarrays * Scuba2Subarray.PIXELS];
		Arrays.fill(readoutLevel, scuba2Scan.blankingValue);
		
		// Read the subsequent subarray data (if any).
		for(int i=0; i<files.size(); i++) {
			Scuba2Fits file = files.get(i);
			Fits fits = new Fits(file.getFile());
			ImageHDU dataHDU = null;
						
			if(i==0) {
				BasicHDU[] HDU = fits.read();
				
				parsePrimaryHeader(HDU[0].getHeader());
				
				// TODO WCS-TAB HDU has data timestamps...
				// TODO could match when reading coordinates, or check if same size...
				// Read the coordinate info etc. from the first subscan file.
				readCoordinateData(getJcmtHDU(HDU));
				
				dataHDU = (ImageHDU) HDU[0];
			}
			else dataHDU = (ImageHDU) fits.getHDU(0);
			
			int subarrayIndex = file.getSubarrayIndex();
			int channelOffset = 0;
			for(int k=0; k<subarrayIndex; k++) if(scuba2Scan.hasSubarray[k]) channelOffset += Scuba2Subarray.PIXELS;
			
			Scuba2Subarray subarray = instrument.subarray[subarrayIndex];
			float scaling = hasOption(subarray.id + ".scale") ? option(subarray.id + ".scale").getFloat() : 1.0F;
			subarray.scaling *= scaling;
			
			readArrayData(dataHDU, channelOffset, scaling);
		}
		
	}

	private void setReadoutLevels(final int[][] DAC, final int channelOffset) {
		for(int bol=Scuba2Subarray.PIXELS; --bol >= 0; ) readoutLevel[channelOffset + bol] = DAC[bol%Scuba2.COLS][bol/Scuba2.COLS];
	}

	
	private void readArrayData(ImageHDU dataHDU, final int channelOffset, final float scaling) throws FitsException {
		final int[][][] data = (int[][][]) dataHDU.getData().getData();
			
		setReadoutLevels(data[0], channelOffset);
		
		new CRUSH.IndexedFork<Void>(size()) {
			@Override
			protected void processIndex(int i) {
				Scuba2Frame frame = get(i);
				if(frame != null) frame.parseData(data[i], channelOffset, scaling, readoutLevel);
			}
		}.process();
	}
	
	public BinaryTableHDU getJcmtHDU(BasicHDU[] HDU) {
		for(int i=1; i<HDU.length; i++) {
			String extName = HDU[i].getHeader().getStringValue("EXTNAME");
			if(extName != null) if(extName.endsWith("JCMTSTATE")) return (BinaryTableHDU) HDU[i];
		}
		return null;		
	}

	@Override
	public String getID() { return Integer.toString(integrationNo+1); }
	
	public void parsePrimaryHeader(Header header) throws HeaderCardException, DarkSubscanException {
		integrationNo = header.getIntValue("NSUBSCAN") - 1;
		
		boolean isDark = header.getDoubleValue("SHUTTER", 1.0) == 0.0;
		if(isDark) throw new DarkSubscanException();
		
		totalIntegrationTime = header.getDoubleValue("INT_TIME") * Unit.s;
		rawFrames = header.getIntValue("NAXIS3"); 
		
		System.err.println("   " + Util.f2.format(totalIntegrationTime / Unit.s) + " seconds with " + rawFrames + " frames --> @ "
				+ Util.f2.format(rawFrames / totalIntegrationTime) + " Hz.");
		
		if(hasOption("subscan.minlength")) if(totalIntegrationTime < option("subscan.minlength").getDouble() * Unit.s)
			throw new IllegalStateException(" Subscan " + getID() + " is less than " + option("subscan.minlength").getDouble() + "s long. Skipping.");

		
		instrument.integrationTime = instrument.samplingInterval = totalIntegrationTime / rawFrames;
	}
	
	public void readCoordinateData(BinaryTableHDU hdu) throws FitsException {
		
		// TODO chop phase and beam (L/R/M?)...	
		final Scuba2Scan scuba2Scan = (Scuba2Scan) scan;
			
		Object[] table = (Object[]) ((ColumnTable) hdu.getData().getData()).getRow(0);
			
		//final boolean isEquatorial = scuba2Scan.trackingClass == EquatorialCoordinates.class;
			
		final double[] MJD = (double[]) table[hdu.findColumn("TCS_TAI")];
		final double TAI2TT = AstroTime.TAI2TT / Unit.day;
		final int samples = MJD.length;
			
		if(samples < 2) {
			System.err.println(" Subscan " + getID() + " has no coordinate data. Dropping from set.");
			return;
		}
				
	
		final double[] AZ = (double[]) table[hdu.findColumn("TCS_AZ_AC1")];
		final double[] EL = (double[]) table[hdu.findColumn("TCS_AZ_AC2")];		
		final double[] tAZ = (double[]) table[hdu.findColumn("TCS_AZ_BC1")];
		final double[] tEL = (double[]) table[hdu.findColumn("TCS_AZ_BC2")];

		
		/*
		final double[] RA = isEquatorial ? (double[]) table[hdu.findColumn("TCS_TR_AC1")] : null;
		final double[] DEC = isEquatorial ? (double[]) table[hdu.findColumn("TCS_TR_AC2")] : null;
		final double[] tRA = isEquatorial ? (double[]) table[hdu.findColumn("TCS_TR_BC1")] : null;
		final double[] tDEC = isEquatorial ? (double[]) table[hdu.findColumn("TCS_TR_BC2")] : null;
		*/

	
		final int[] SN = (int[]) table[hdu.findColumn("RTS_NUM")];
		int iDT = hdu.findColumn("SC2_MIXTEMP");
		final float[] DT = iDT < 0 ? null : (float[]) table[iDT];

		
		final double[] CX = (double[]) table[hdu.findColumn("SMU_AZ_CHOP_X")];
		final double[] CY = (double[]) table[hdu.findColumn("SMU_AZ_CHOP_Y")];
		final boolean isChopped = (CX.length == AZ.length);
		
		//	CR = (double[]) table[hdu.findColumn("SMU_TR_CHOP_X")];
		//	CD = (double[]) table[hdu.findColumn("SMU_TR_CHOP_Y")];

		final double[] JX = (double[]) table[hdu.findColumn("SMU_AZ_JIG_X")];
		final double[] JY = (double[]) table[hdu.findColumn("SMU_AZ_JIG_Y")];
		final boolean isJiggled = (CX.length == AZ.length);
		
		//	JR = (double[]) table[hdu.findColumn("SMU_TR_JIG_X")];
		//	JD = (double[]) table[hdu.findColumn("SMU_TR_JIG_Y")];

		clear();
		ensureCapacity(samples);
		for(int i=samples; --i >=0; ) add(null);
		
		new CRUSH.IndexedFork<Void>(samples) {
			private AstroTime time;
			
			@Override
			public void init() {
				time = new AstroTime();
			}
			
			@Override
			protected void processIndex(int i) {
				// Check to see if the frame has valid astrometry...
				if(Double.isNaN(AZ[i])) return;
				//if(Double.isNaN(RA[i])) return;
				
				final Scuba2Frame frame = new Scuba2Frame(scuba2Scan);

				//final double UT = (((double[]) row[iUT])[0] * Unit.sec) % Unit.day;
				frame.MJD = MJD[i] + TAI2TT;
				time.setMJD(frame.MJD);
				
				frame.LST = time.getLMST(scan.site.longitude(), scuba2Scan.dUT1) * Unit.timeAngle;
				
				frame.horizontal = new HorizontalCoordinates(AZ[i], EL[i]);
				frame.horizontalOffset = new Vector2D((AZ[i] - tAZ[i]) * frame.horizontal.cosLat(), EL[i] - tEL[i]);		
				
				if(isChopped || isJiggled) {
					frame.chopperPosition = new Vector2D();
					
					if(isChopped) { frame.chopperPosition.addX(CX[i]); frame.chopperPosition.addY(CY[i]); }
					if(isJiggled) { frame.chopperPosition.addX(JX[i]); frame.chopperPosition.addY(JY[i]); }
		
					frame.horizontalOffset.add(frame.chopperPosition);
					frame.horizontal.addOffset(frame.chopperPosition);
				}
				
				frame.calcParallacticAngle();

				frame.frameNumber = SN[i];
				if(DT != null) frame.detectorT = DT[i];
				
				set(i, frame);

			}
			
			
		}.process();
		
	}

	/*
	public void temperatureCorrect() {
		System.err.println("   Correcting for temperature drifts.");
		
		Response mode = (Response) instrument.modalities.get("temperature").get(0);
		Signal signal = getSignal(mode);
		if(signal == null) signal = mode.getSignal(this);
		
		signal.level(false);
		double rmsHe3 = signal.getRMS();
		System.err.println("   RMS He3 temperature drift is " + Util.f3.format(1e3 * rmsHe3) + " mK.");
		
		final Signal temperatureSignal = signal;
		
		new Fork<Void>() {
			@Override
			protected void process(Scuba2Frame exposure) {
				double dT = temperatureSignal.valueAt(exposure);
				if(!Double.isNaN(dT)) for(Scuba2Pixel pixel : instrument) exposure.data[pixel.index] -= pixel.he3Gain * dT;
			}	
		}.process();
	}
	*/
	
	/*
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
	*/
	
	
	@Override
	public String getFullID(String separator) {
		return super.getFullID(separator) + separator + instrument.filter;
	}

	
}
