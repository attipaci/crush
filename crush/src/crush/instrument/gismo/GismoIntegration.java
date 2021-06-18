/*******************************************************************************
 * Copyright (c) 2021 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
// Copyright (c) 2009 Attila Kovacs 

package crush.instrument.gismo;


import crush.*;
import crush.fits.HDUReader;
import crush.telescope.GroundBasedIntegration;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.math.Vector2D;
import nom.tam.fits.*;

class GismoIntegration extends GroundBasedIntegration<GismoFrame> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6513008414302600380L;
	
	boolean ignoreIRIG = false;
	
	public GismoIntegration(GismoScan parent) {
		super(parent);
	}	
	
	@Override
    public GismoScan getScan() { return (GismoScan) super.getScan(); }
	
	@Override
    public Gismo getInstrument() { return (Gismo) super.getInstrument(); }
	
	@Override
	public void setTau() throws Exception {
		super.setTau();

	    CRUSH.values(this, "--->"
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz"))
				+ ", tau(LOS):" + Util.f3.format(zenithTau / getScan().horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv")) + "mm"
		);		
	}
	
	@Override
	public GismoFrame getFrameInstance() {
		return new GismoFrame(this);
	}
	
	void read(BinaryTableHDU hdu, boolean oldFormat) throws Exception {
		int records = hdu.getAxes()[0];

		getScan().info("Processing scan data:");		
		info("Reading " + records + " frames.");
	
		Header header = hdu.getHeader();
		
		getInstrument().integrationTime = getInstrument().samplingInterval = header.getDoubleValue("CDELT1") * Unit.ms;
		
		info("Sampling at " + Util.f1.format(getInstrument().integrationTime / Unit.ms) + " ms ---> " 
				+ Util.f1.format(getInstrument().samplingInterval * records / Unit.min) + " minutes.");
			
		clear();
		ensureCapacity(records);
		for(int t=records; --t>=0; ) add(null);
		
		if(oldFormat) new OldGismoReader(hdu).read();
		else new GismoReader(hdu).read();
	}
		
	private class GismoReader extends HDUReader {	
		private float[] DAC;
		private double[] MJD, LST, dX, dY, AZE, ELE;
		private double[] X0, Y0, cEL; //AZ, EL, cAZ, cEL, tAZ, tEL, posA;
		private int[] NS, SN, CAL;
		private byte[] SDI;
		private int channels;
		
		private final GismoScan gismoScan = getScan();
		
		GismoReader(BinaryTableHDU hdu) throws FitsException {
			super(hdu);
		
			int iDAC = hdu.findColumn("DAC");
			
			channels = table.getSizes()[iDAC];
			
			// The IRAM coordinate data...
			MJD = (double[]) table.getColumn(hdu.findColumn("MJD"));
			LST = (double[]) table.getColumn(hdu.findColumn("LST"));
			
			// This is a position angle, not parallactic angle!
			//posA = (double[]) table.getColumn(hdu.findColumn("PARANGLE"));
			
			// The tracking center in the basis coordinates of the scan (usually RA/DEC)
			X0 = (double[]) table.getColumn(hdu.findColumn("BASLONG"));
			Y0 = (double[]) table.getColumn(hdu.findColumn("BASLAT"));
			
			// The scanning offsets in the offset system (usually AZ/EL)
			dX = (double[]) table.getColumn(hdu.findColumn("LONGOFF"));
			dY = (double[]) table.getColumn(hdu.findColumn("LATOFF"));
			
			// The encoder AZ/EL. These are not astronomical AZ/EL!!!
			//AZ = (double[]) table.getColumn(hdu.findColumn("AZIMUTH"));
			//EL = (double[]) table.getColumn(hdu.findColumn("ELEVATION"));
			
			// The commanded encoder AZ/EL. These are not astronomical AZ/EL!!!!
			//cAZ = (double[]) table.getColumn(hdu.findColumn("CAZIMUTH"));
			cEL = (double[]) table.getColumn(hdu.findColumn("CELEVATIO"));
			AZE = (double[]) table.getColumn(hdu.findColumn("TRACKING_AZ"));
			ELE = (double[]) table.getColumn(hdu.findColumn("TRACKING_EL"));
			
			// The GISMO data
			DAC = (float[]) table.getColumn(iDAC);
			SN = (int[]) table.getColumn(hdu.findColumn("FRAME_COUNTER"));
			NS = (int[]) table.getColumn(hdu.findColumn("NUMBER_OF_SAMPLES"));
			SDI = (byte[]) table.getColumn(hdu.findColumn("SUMMARIZED_DIGITAL_INPUT"));
					
			int iCAL = hdu.findColumn("CalFlag"); // 0=none, 1=shutter, 2=ivcurve
			if(iCAL > 0) CAL = (int[]) table.getColumn(iCAL);
			
			// These columns below have been retired in March 2012
			// But even before that, these did not carry actual data, as thermometry
			// was disconnected during observations...
			
			//iLT = hdu.findColumn("LABVIEWTIME");
			//iDT = hdu.findColumn("DIODE_TEMPERATURES");
			//iRT = hdu.findColumn("RESISTOR_TEMPERATURES");
			//iDV = hdu.findColumn("DIODE_VOLTS");
			//iRO = hdu.findColumn("RESISTOR_OHMS");
			//iMRT = hdu.findColumn("MAIN_RESISTOR_TEMPERATURE");
		}
		
		@Override
		public Reader getReader() {
			return new Reader() {
				private Vector2D offset;
				
				@Override
				public void init() {
					offset = new Vector2D();
					if(hasOption("ignoreirig")) ignoreIRIG = true;
				}
				
				@Override
				public void processRow(int i) {
					set(i, null);
					
					// Do not process frames with no coordinate information...
					if(cEL[i] <= MIN_ELEVATION) return;
					if(cEL[i] >= MAX_ELEVATION) return;					
					
					// Zero scanning offsets are typically outside of the scan.
					// (The normal scanning motion should never go through 0.0 exactly).
					if(dX[i] == 0.0 && dY[i] == 0.0) return;
						
					int calFlag = 0, digitalFlag = 0;
					
					// Skip processing frames with non-zero cal flag...
					if(CAL != null) {
						calFlag = CAL[i];
						if(CAL[i] != 0) if(gismoScan.skipReconstructed || calFlag != CALFLAG_RECONSTRUCTED) return;
					}	
						
					// Skip data with invalid flags 
					for(int bit=0, from=6*i; bit<6; bit++) if(SDI[from+bit] > 0) digitalFlag |= 1 << bit;
					if(!ignoreIRIG) if((digitalFlag & GismoFrame.DIGITAL_IRIG) == 0) return;
								
					// Create the frame object only if it cleared the above hurdles...
					final GismoFrame frame = getFrameInstance();
					frame.index = i;
					
					// Set the frame flags...
					frame.calFlag = calFlag;
					frame.digitalFlag = digitalFlag;
					
					// Read the pixel data
					frame.parseData(DAC, i*channels, channels);	
					
					// Add in the astrometry...
					frame.MJD = MJD[i];
					frame.LST = LST[i] * Unit.sec; 
					
					// Use ALWAYS the scanning offsets around the object coordinate...
					// First make sure the horizontal coordinates of the tracking center
					// are correct even if tracking (e.g. equatorial). 
					if(gismoScan.basisSystem == HorizontalCoordinates.class) {
						frame.horizontal = new HorizontalCoordinates(X0[i], Y0[i]);
						if(gismoScan.basisOffset != null) 
							frame.horizontal.addOffset(gismoScan.basisOffset);
					}
					else if(gismoScan.basisSystem == EquatorialCoordinates.class) {
						frame.equatorial = new EquatorialCoordinates(X0[i], Y0[i], getScan().equatorial.getSystem());
						if(gismoScan.basisOffset != null) 
							frame.equatorial.addOffset(gismoScan.basisOffset);
						frame.calcHorizontal();	
					}
					else {
						try {
							CelestialCoordinates celestial = (CelestialCoordinates) gismoScan.basisSystem.getConstructor().newInstance();
							celestial.set(X0[i], Y0[i]);
							if(gismoScan.basisOffset != null) 
								celestial.addOffset(gismoScan.basisOffset);
							
							if(celestial instanceof PrecessingCoordinates) ((PrecessingCoordinates) celestial).setSystem(gismoScan.system);
					
							frame.equatorial = celestial.toEquatorial();
							frame.calcHorizontal();
						}
						catch(Exception e) { error(e); }
					}
						
					// Calculate the parallactic angle
					frame.calcParallacticAngle();	
		
					// Load the scanning offsets...
					frame.horizontalOffset = new Vector2D(dX[i], dY[i]);
					if(!gismoScan.projectedOffsets) frame.horizontalOffset.scaleY(frame.horizontal.cosLat());
					
					// Convert scanning offsets to horizontal if necessary...
					if(gismoScan.offsetSystem == EquatorialCoordinates.class)
						frame.equatorialToHorizontal(frame.horizontalOffset);
					
					// Add the static horizontal offsets
					if(gismoScan.horizontalOffset != null) frame.horizontalOffset.add(gismoScan.horizontalOffset);
										
					// Add the static equatorial offset
					if(gismoScan.equatorialOffset != null) {
						offset.copy(gismoScan.equatorialOffset);
						frame.equatorialToHorizontal(offset);
						frame.horizontalOffset.add(offset);
					}
					
					// Add the tracking errors (confirmed raw AZ differences).
					// Errors are commanded - actual;
					frame.horizontalOffset.subtractX(AZE[i]);
					frame.horizontalOffset.subtractY(ELE[i]);
					
					// Verified that offsets are correctly projected...
					frame.horizontal.addOffset(frame.horizontalOffset);	
					
					// Force recalculation of the equatorial coordinates...
					frame.equatorial = null;
					
					// The GISMO-specific data
					frame.samples = NS[i];
					frame.frameNumber = SN[i];
				
					//frame.diodeT = (float[]) row[iDT];
					//frame.resistorT = (float[]) row[iRT];
					//frame.diodeV = (float[]) row[iDV];
					//frame.parseData((float[][]) row[iDAC]);
					//frame.labviewTime = ((double[])row[iLT])[0] * Unit.sec;
					
					set(i, frame);
				}
			};
		}
	}	
	
	
	private class OldGismoReader extends HDUReader {	
		private float[] DAC, RA, DEC, AZ, EL, AZE, ELE, LST;
		private double[] MJD;
		private int[] NS, SN, CAL;
		private byte[] SDI;
		private int channels;
			
		OldGismoReader(BinaryTableHDU hdu) throws FitsException {
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
		public Reader getReader() {
			return new Reader() {
				private HorizontalCoordinates trackingCenter;
				private EquatorialCoordinates apparent;
				private EquatorialTransform catalogToApparent;

				@Override
				public void init() {
					super.init();
					trackingCenter = new HorizontalCoordinates();
					apparent = new EquatorialCoordinates();
				}
				
				@Override
				public void processRow(int i) {			
					set(i, null);
					
					int calFlag = 0, digitalFlag = 0;
					
					// Do not process frames with no coordinate information...
					if(EL[i] <= MIN_ELEVATION / Unit.deg) return;
					if(EL[i] >= MAX_ELEVATION / Unit.deg) return;
					
					// Skip processing frames with non-zero cal flag...
					if(CAL != null) {
						calFlag = CAL[i];	
						if(CAL[i] != 0) return;
					}
						
					// Skip data with invalid flags 
					for(int bit=0, from=6*i; bit<6; bit++) if(SDI[from+bit] > 0) digitalFlag |= 1 << bit;
					if((digitalFlag & GismoFrame.DIGITAL_IRIG) == 0) return;
								
					// Create the frame object only if it cleared the above hurdles...
					final GismoFrame frame = getFrameInstance();
					
					// Set the frame flags...
					frame.calFlag = calFlag;
					frame.digitalFlag = digitalFlag;
					
					// Read in the detector data...
					//frame.parseData((float[][]) row[iDAC]);
					frame.parseData(DAC, i*channels, channels);
					
					//final double UT = (((double[]) row[iUT])[0] * Unit.sec) % Unit.day;
					frame.MJD = MJD[i];
					frame.LST = LST[i] * Unit.sec;
					
					// Get the coordinate info...
					// This is the tracking center only...
					// It's in the same epoch as the scan (checked!)
					apparent.setLongitude(RA[i] * Unit.hourAngle);
					apparent.setLatitude(DEC[i] * Unit.deg);

					if(catalogToApparent == null) {
						EquatorialSystem local = new EquatorialSystem.Topocentric("IRAM-30m", getScan().site, getMJD());
						catalogToApparent = new EquatorialTransform(getScan().equatorial.getSystem(), local);
					}
					catalogToApparent.transform(apparent);

					// Read the chopped position data...
					//frame.chopperPosition.x = chop[i] * Unit.arcsec;

					// Calculate the horizontal offset	
					apparent.toHorizontal(trackingCenter, getScan().site, frame.LST);

					frame.horizontal = new HorizontalCoordinates(
							AZ[i] * Unit.deg,
							EL[i] * Unit.deg);
					
					frame.horizontalOffset = frame.horizontal.getOffsetFrom(trackingCenter);
					frame.horizontalOffset.subtractX(AZE[i] * Unit.arcsec);
					frame.horizontalOffset.subtractY(ELE[i] * Unit.arcsec);
					
					// Add the chopper offet to the actual coordinates as well...
					//final double chopOffset = frame.chopperPosition.x / frame.horizontal.cosLat;
					//frame.horizontal.x += chopOffset;
					//frame.horizontalOffset.x += chopOffset;

					// Force recalculation of the equatorial coordinates...
					frame.equatorial = null;	
					frame.calcParallacticAngle();
	
					// The GISMO specific columns...
					frame.frameNumber = SN[i];
					frame.samples = NS[i];
					
					//frame.diodeT = (float[]) row[iDT];
					//frame.resistorT = (float[]) row[iRT];
					//frame.diodeV = (float[]) row[iDV];
					//frame.labviewTime = ((double[])row[iLT])[0] * Unit.sec;	

					set(i, frame);
				}
			};
		}
	}	

	
	@Override
	public String getFullID(String separator) {
		return getScan().getID();
	}
	
	private static final int CALFLAG_RECONSTRUCTED = 10;
	
	private static final double MIN_ELEVATION = 0.001 * Unit.deg;
	private static final double MAX_ELEVATION = 90.0 * Unit.deg;
}
