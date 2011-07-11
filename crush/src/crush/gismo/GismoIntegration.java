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

package crush.gismo;

import crush.*;
import util.*;
import util.astro.CoordinateEpoch;
import util.astro.EquatorialCoordinates;
import util.astro.HorizontalCoordinates;
import util.astro.JulianEpoch;
import util.astro.Precession;
import crush.fits.HDUManager;
import crush.fits.HDUReader;

import nom.tam.fits.*;

public class GismoIntegration extends Integration<Gismo, GismoFrame> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6513008414302600380L;
	
	HorizontalCoordinates reuseTrackingCenter = new HorizontalCoordinates();
	
	public GismoIntegration(GismoScan parent) {
		super(parent);
	}	
	
	@Override
	public void setTau() throws Exception {
		super.setTau();
		printEquivalentTaus();
	}
	
	public void printEquivalentTaus() {
		System.err.println("   --->"
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz"))
				+ ", tau(LOS):" + Util.f3.format(zenithTau / scan.horizontal.sinLat)
				+ ", PWV:" + Util.f2.format(getTau("pwv")) + "mm"
		);		
	}
	
	@Override
	public GismoFrame getFrameInstance() {
		return new GismoFrame((GismoScan) scan);
	}
	
	protected void read(BinaryTableHDU hdu) throws IllegalStateException, HeaderCardException, FitsException {
		int records = hdu.getAxes()[0];

		System.err.println(" Processing scan data:");		
		System.err.println("   Reading " + records + " frames.");
	
		Header header = hdu.getHeader();
		
		instrument.integrationTime = instrument.samplingInterval = header.getDoubleValue("CDELT1") * Unit.ms;
		System.err.println("   Sampling at " + Util.f1.format(instrument.integrationTime / Unit.ms) + " ms ---> " 
				+ Util.f1.format(instrument.samplingInterval * records / Unit.min) + " minutes.");
		
		clear();
		ensureCapacity(records);
		
		new HDUManager(this).read(new GismoReader(hdu));
	}
		
	class GismoReader extends HDUReader {	
		float[] DAC, RA, DEC, AZ, EL, AZE, ELE, LST;
		double[] MJD;
		int[] NS, SN, CAL;
		byte[] SDI;
		int channels;
		
		public GismoReader(BinaryTableHDU hdu) throws FitsException {
			super(hdu);
		
			int iDAC = hdu.findColumn("DAC");
			channels = table.getSizes()[iDAC];
			
			DAC = (float[]) table.getColumn(iDAC);
			RA = (float[]) table.getColumn(hdu.findColumn("RA"));
			DEC = (float[]) table.getColumn(hdu.findColumn("DEC"));	
			MJD = (double[]) table.getColumn(hdu.findColumn("MJD"));
			LST = (float[]) table.getColumn(hdu.findColumn("LST"));
			SN = (int[]) table.getColumn(hdu.findColumn("FRAME_COUNTER"));
			NS = (int[]) table.getColumn(hdu.findColumn("NUMBER_OF_SAMPLES"));
			SDI = (byte[]) table.getColumn(hdu.findColumn("SUMMARIZED_DIGITAL_INPUT"));
			
			// chop = (float[]) table.getColumn(hdu.findColumn("CHOP_OFFSET"));
			// AZO = (float[]) table.getColumn(hdu.findColumn("AZO"));
			// ELO = (float[]) table.getColumn(hdu.findColumn("ELO"));
			
			AZ = (float[]) table.getColumn(hdu.findColumn("AZ"));
			EL = (float[]) table.getColumn(hdu.findColumn("EL"));
			AZE = (float[]) table.getColumn(hdu.findColumn("AZ_ERROR"));
			ELE = (float[]) table.getColumn(hdu.findColumn("EL_ERROR"));		

			int iCAL = hdu.findColumn("CalFlag"); // 0=none, 1=shutter, 2=ivcurve
			if(iCAL > 0) CAL = (int[]) table.getColumn(iCAL);
			
			//iUT = hdu.findColumn("UT");
			//iPA = hdu.findColumn("PARALLACTIC_ANGLE");
			//iLT = hdu.findColumn("LABVIEWTIME");
			//iRAO = hdu.findColumn("RAO");
			//iDECO = hdu.findColumn("DECO");
			//iAZO = hdu.findColumn("AZO");
			//iELO = hdu.findColumn("ELO");
			//iFlag = hdu.findColumn("Celestial");
			//iTRK = hdu.findColumn("Tracking");
			//iDT = hdu.findColumn("DIODE_TEMPERATURES");
			//iRT = hdu.findColumn("RESISTOR_TEMPERATURES");
			//iDV = hdu.findColumn("DIODE_VOLTS");
			//iRO = hdu.findColumn("RESISTOR_OHMS");
			//iMRT = hdu.findColumn("MAIN_RESISTOR_TEMPERATURE");
			//iSAE = hdu.findColumn("SAE");
		}
		
		@Override
		public void read() throws FitsException {
			final GismoScan gismoScan = (GismoScan) scan;
			
			final HorizontalCoordinates trackingCenter = new HorizontalCoordinates();
			final EquatorialCoordinates apparent = new EquatorialCoordinates();
			Precession catalogToApparent = null;

			for(int i=from; i<to; i++) {
				if(isInterrupted()) break;
				
				final GismoFrame frame = new GismoFrame(gismoScan);

				//final double UT = (((double[]) row[iUT])[0] * Unit.sec) % Unit.day;
				frame.MJD = MJD[i];
				frame.samples = NS[i];
				
				for(int bit=0, from=6*i; bit<6; bit++) if(SDI[from+bit] > 0) frame.digitalFlag |= 1 << bit;

				//frame.diodeT = (float[]) row[iDT];
				//frame.resistorT = (float[]) row[iRT];
				//frame.diodeV = (float[]) row[iDV];

				//frame.parseData((float[][]) row[iDAC]);
				frame.parseData(DAC, i*channels, channels);

				// This is the tracking center only...
				// It's in the same epoch as the scan (checked!)
				apparent.setLongitude(RA[i] * Unit.hourAngle);
				apparent.setLatitude(DEC[i] * Unit.deg);
				
				if(catalogToApparent == null) {
					CoordinateEpoch apparentEpoch = new JulianEpoch();
					apparentEpoch.setMJD(frame.MJD);
					catalogToApparent = new Precession(scan.equatorial.epoch, apparentEpoch);
				}
				catalogToApparent.precess(apparent);
				
				frame.LST = LST[i] * Unit.sec;

				//frame.labviewTime = ((double[])row[iLT])[0] * Unit.sec;
				frame.frameNumber = SN[i];

				// Read the chopped position data...
				//frame.chopperPosition.x = chop[i] * Unit.arcsec;
				
				// Calculate the horizontal offset	
				apparent.toHorizontal(trackingCenter, scan.site, frame.LST);
				
				//	frame.horizontalOffset = new Vector2D(AZO[i] * Unit.arcsec, ELO[i] * Unit.arcsec);
				//	frame.horizontal = (HorizontalCoordinates) trackingCenter.clone();
				//	frame.horizontal.addOffset(frame.horizontalOffset);
				
				frame.horizontal = new HorizontalCoordinates(
						AZ[i] * Unit.deg + AZE[i] * Unit.arcsec,
						EL[i] * Unit.deg + ELE[i] * Unit.arcsec);
				frame.horizontalOffset = frame.horizontal.getOffsetFrom(trackingCenter);
				
				// Add the chopper offet to the actual coordinates as well...
				//final double chopOffset = frame.chopperPosition.x / frame.horizontal.cosLat;
				//frame.horizontal.x += chopOffset;
				//frame.horizontalOffset.x += chopOffset;
					
			    frame.calcParallacticAngle();

				// Force recalculation of the equatorial coordinates...
				frame.equatorial = null;

				if(CAL != null) frame.calFlag = CAL[i];

				if(frame.isValid())	set(i, frame);
				else set(i, null);	
			}

		}
	}	

	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}
	
}
