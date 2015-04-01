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

package crush.hawcplus;

import kovacs.astro.*;
import kovacs.math.Vector2D;
import kovacs.util.*;
import crush.fits.HDUReader;
import crush.sofia.SofiaIntegration;
import nom.tam.fits.*;

public class HawcPlusIntegration extends SofiaIntegration<HawcPlus, HawcPlusFrame> {	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3894220792729801094L;


	public HawcPlusIntegration(HawcPlusScan parent) {
		super(parent);
	}	
	
	@Override
	public void setTau() throws Exception {
		super.setTau();
		printEquivalentTaus();
	}
	
	// TODO
	public void printEquivalentTaus() {
		System.err.println("   --->"
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz"))
				+ ", tau(LOS):" + Util.f3.format(zenithTau / scan.horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv")) + "mm"
		);		
	}
	
	@Override
	public HawcPlusFrame getFrameInstance() {
		return new HawcPlusFrame((HawcPlusScan) scan);
	}
	
	
	@Override
	public void readData(BasicHDU[] hdus) throws Exception {
		read((BinaryTableHDU) hdus[1]);
	}
	
	protected void read(BinaryTableHDU hdu) throws Exception {
		int records = hdu.getAxes()[0];

		System.err.println(" Processing scan data:");		
		System.err.println("   Reading " + records + " frames.");		
		System.err.println("   Sampling at " + Util.f1.format(instrument.integrationTime / Unit.ms) + " ms ---> " 
				+ Util.f1.format(instrument.samplingInterval * records / Unit.min) + " minutes.");
			
		clear();
		ensureCapacity(records);
		for(int t=records; --t>=0; ) add(null);
		
		new HawcPlusReader(hdu).read();
	}
		
	class HawcPlusReader extends HDUReader {	
		private long[] R, T, SN;
		private double[] TS; 
		private float[] RA, DEC, AZ, EL, PA, VPA, LON, LAT, LST, chop, PWV, HWP;
		private boolean isSimulated;
		//private int channels;
		
		private final HawcPlusScan hawcPlusScan = (HawcPlusScan) scan;
		
		public HawcPlusReader(BinaryTableHDU hdu) throws FitsException {
			super(hdu);
			
			isSimulated = hasOption("simulated");
		
			// The IRAM coordinate data...
			TS = (double[]) table.getColumn(hdu.findColumn("Timestamp"));	
			SN = (long[]) table.getColumn(hdu.findColumn("FRAME_COUNTER"));
			
			// The GISMO data
			int iR = hdu.findColumn("R array");
			R = (long[]) table.getColumn(iR);
			int iT = hdu.findColumn("T array");
			T = (long[]) table.getColumn(iT);
			
			// The tracking center in the basis coordinates of the scan (usually RA/DEC)
			RA = (float[]) table.getColumn(hdu.findColumn("RA"));
			DEC = (float[]) table.getColumn(hdu.findColumn("DEC"));
						
			// The scanning offsets in the offset system (usually AZ/EL)
			AZ = (float[]) table.getColumn(hdu.findColumn("Azimuth"));
			EL = (float[]) table.getColumn(hdu.findColumn("Elevation"));
			
			// The parallactic angle and the SOFIA Vertical Position Angle
			PA = (float[]) table.getColumn(hdu.findColumn("Parallactic Angle"));
			VPA = (float[]) table.getColumn(hdu.findColumn("VPA"));
			
			LON = (float[]) table.getColumn(hdu.findColumn("GEOLON"));
			LON = (float[]) table.getColumn(hdu.findColumn("GEOLAT"));
			
			LST = (float[]) table.getColumn(hdu.findColumn("LST"));
			
			chop = (float[]) table.getColumn(hdu.findColumn("Chop Offset"));
			// CRUSH does not need the nod offset, so ignore it...
			
			// HWP may be used in the future if support is extended for
			// scan-mode polarimetry (or polarimetry, in general...
			HWP = (float[]) table.getColumn(hdu.findColumn("HPW Angle"));
			
			PWV = (float[]) table.getColumn(hdu.findColumn("PWV"));
			
		}
		
		@Override
		public Reader getReader() {
			return new Reader() {	
				AstroTime timeStamp;
				Vector2D offset;
				
				@Override
				public void init() {
					timeStamp = new AstroTime();
					offset = new Vector2D();
				}
				
				@Override
				public void processRow(int i) {
					set(i, null);
										
					// Create the frame object only if it cleared the above hurdles...
					final HawcPlusFrame frame = new HawcPlusFrame(hawcPlusScan);
					frame.index = i;
						
					// Read the pixel data
					frame.parseData(0, R, i);
					frame.parseData(1, T, i);
					
					frame.mceSerial = SN[i];
					
					// Add in the astrometry...
					timeStamp.setUTCMillis(Math.round(1000.0 * TS[i] * Unit.s));
					frame.MJD = timeStamp.getMJD();
					
					frame.equatorial = new EquatorialCoordinates(RA[i] * Unit.hourAngle, DEC[i] * Unit.deg, scan.equatorial.epoch);
					frame.site = new GeodeticCoordinates(LON[i] * Unit.deg, LAT[i] * Unit.deg);
					
					frame.chopperPosition = new Vector2D(chop[i] * Unit.arcsec, 0.0);
					frame.chopperPosition.rotate(hawcPlusScan.chopper.angle);
					
					frame.HPWangle = HWP[i] * (float) Unit.deg;
					frame.PWV = PWV[i] * (float) Unit.um;

					if(!isSimulated) {
						frame.horizontal = new HorizontalCoordinates(AZ[i] * Unit.deg, EL[i] * Unit.deg);
						frame.LST = LST[i] * (float) Unit.hour;
						frame.setParallacticAngle(PA[i] * Unit.deg);
						frame.VPA = VPA[i] * (float) Unit.deg;	
					}
					else {
						// -------------- CALCULATE MISSING -------------------
						frame.LST = timeStamp.getLMST(frame.site.longitude());
						frame.equatorial.getParallacticAngle(frame.site, frame.LST);
						frame.VPA = (float) (frame.getParallacticAngle() + frame.horizontal.EL());
						frame.calcHorizontal(); // needs LST.
						// ----------------------------------------------------
					}

					// Calculate the scanning offsets...
					frame.horizontalOffset = frame.equatorial.getNativeOffsetFrom(scan.equatorial);
					frame.equatorialNativeToHorizontal(frame.horizontalOffset);

					// Add the chopper offset to the telescope coordinates.
					// TODO check!
					frame.horizontalOffset.add(frame.chopperPosition);
					frame.horizontal.addOffset(frame.chopperPosition);
					
					offset = frame.chopperPosition;
					frame.horizontalToNativeEquatorial(offset);
					frame.equatorial.addNativeOffset(offset);
					
					set(i, frame);
				}
			};
		}
	}	

	
	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}

	

	
}
