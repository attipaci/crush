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

import java.io.File;
import java.util.List;

import crush.CRUSH;
import crush.fits.HDUReader;
import crush.sofia.SofiaIntegration;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.math.Vector2D;
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
	
	
	
	protected void read(List<BinaryTableHDU> dataHDUs) throws Exception {	
		int records = 0;
		for(BinaryTableHDU hdu : dataHDUs) records += hdu.getAxes()[0];
		
		System.err.println(" Processing scan data:");
		System.err.println("   Reading " + records + " frames from " + dataHDUs.size() + " HDU(s).");
		System.err.println("   Sampling at " + Util.f1.format(instrument.integrationTime / Unit.ms) + " ms ---> " 
				+ Util.f1.format(instrument.samplingInterval * records / Unit.min) + " minutes.");
			
		clear();
		ensureCapacity(records);
		for(int t=records; --t>=0; ) add(null);
		
		int startIndex = 0;
		for(int i=0; i<dataHDUs.size(); i++) {
			BinaryTableHDU hdu = dataHDUs.get(i);
			new HawcPlusReader(hdu, startIndex).read();
			startIndex += hdu.getAxes()[0];
		}	
		
	}
		
	class HawcPlusReader extends HDUReader {	
		private int startIndex;
		private long[] SN;
		private long[] data;
		private double[] TS; 
		private float[] RA, DEC, AZ, EL, iVPA, LON, LAT, LST, chop, PWV, HWP;
		//private float[] dT;
		private boolean isSimulated;
		//private int channels;
		
		private final HawcPlusScan hawcPlusScan = (HawcPlusScan) scan;
		
		public HawcPlusReader(BinaryTableHDU hdu, int startIndex) throws FitsException {
			super(hdu);
			
			this.startIndex = startIndex;
			
			isSimulated = hasOption("simulated");
			
			HawcPlus hawc = scan.instrument;
			
			Object[] row = (Object[]) hdu.getRow(0);
			
			// The Sofia timestamp (decimal seconds since 0 UTC 1 Jan 1970...
			TS = (double[]) table.getColumn(hdu.findColumn("Timestamp"));	
			
			//dT = (float[]) table.getColumn(hdu.findColumn("Time Adjustment"));	
			SN = (long[]) table.getColumn(hdu.findColumn("Frame Counter"));
			
			// The R/T array data
			int iData = hdu.findColumn("SQ1 Feedback");
			data = (long[]) table.getColumn(iData);
			
			int storeRows = ((long[][]) row[iData]).length;
			int storeCols = ((long[][]) row[iData])[0].length;
			System.err.println("   FITS has " + storeRows + "x" + storeCols + " arrays.");
			
			System.err.println("   Logical layout: " + hawc.polsubarrays + " subarrays of " + hawc.rows + "x" + hawc.subarrayCols + " pixels per polarization.");
			
			// The tracking center in the basis coordinates of the scan (usually RA/DEC)
			RA = (float[]) table.getColumn(hdu.findColumn("Right Ascension"));
			DEC = (float[]) table.getColumn(hdu.findColumn("Declination"));
			
			// The scanning offsets in the offset system (usually AZ/EL)
			AZ = (float[]) table.getColumn(hdu.findColumn("Azimuth"));
			EL = (float[]) table.getColumn(hdu.findColumn("Elevation"));
			
			LST = (float[]) table.getColumn(hdu.findColumn("LST"));
				
			// The parallactic angle and the SOFIA Vertical Position Angle
			//PA = (float[]) table.getColumn(hdu.findColumn("Parallactic Angle"));
			iVPA = (float[]) table.getColumn(hdu.findColumn("Array VPA"));
				
			LON = (float[]) table.getColumn(hdu.findColumn("Longitude"));
			LAT = (float[]) table.getColumn(hdu.findColumn("Latitude"));
			
			chop = (float[]) table.getColumn(hdu.findColumn("Chop Offset"));
			// CRUSH does not need the nod offset, so ignore it...
			
			// HWP may be used in the future if support is extended for
			// scan-mode polarimetry (or polarimetry, in general...
			HWP = (float[]) table.getColumn(hdu.findColumn("HWP Angle"));
			
			PWV = (float[]) table.getColumn(hdu.findColumn("Water Vapor"));
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
					set(startIndex + i, null);
										
					// Create the frame object only if it cleared the above hurdles...
					final HawcPlusFrame frame = new HawcPlusFrame(hawcPlusScan);
					frame.index = i;
						
					// Read the pixel data
					frame.parseData(data, i);
					
					frame.mceSerial = SN[i];
					
					// Add in the astrometry...
					timeStamp.setUTCMillis(Math.round(1000.0 * TS[i] * Unit.s));
					frame.MJD = timeStamp.getMJD();
					
					frame.equatorial = new EquatorialCoordinates(RA[i] * Unit.hourAngle, DEC[i] * Unit.deg, scan.equatorial.epoch);
					frame.site = new GeodeticCoordinates(LON[i] * Unit.deg, LAT[i] * Unit.deg);
					
					if(hawcPlusScan.isChopping) {
						frame.chopperPosition = new Vector2D(chop[i] * Unit.arcsec, 0.0);
						frame.chopperPosition.rotate(hawcPlusScan.chopper.angle);
					}
					else frame.chopperPosition = new Vector2D();
					
					frame.hwpAngle = HWP[i] * (float) Unit.deg;
					frame.PWV = PWV[i] * (float) Unit.um;

					frame.LST = LST[i] * (float) Unit.hour;
					frame.horizontal = new HorizontalCoordinates(AZ[i] * Unit.deg, EL[i] * Unit.deg);
					
					// TODO...
					frame.telescopeCoords = new TelescopeCoordinates(AZ[i] * Unit.deg, EL[i] * Unit.deg);
					
					// LST and horizontal can be calculated (approximately within dUT1), if need be...
					//frame.LST = timeStamp.getLMST(frame.site.longitude());
					//frame.horizontal = frame.equatorial.toHorizontal(frame.site, frame.LST);
					
					
					if(isSimulated) {
						// The simulation writes a position angle as if a parallactic angle...
						frame.instrumentVPA = iVPA[i] * (float) Unit.deg;
						frame.telescopeVPA = iVPA[i] * (float) Unit.deg; // TODO
						frame.setParallacticAngle(-iVPA[i] * Unit.deg);
						frame.setRotation(0.0);
					}
					else {
					    // calc parallactic angle...
					    // rot: sibs VPA - PA
						frame.instrumentVPA = iVPA[i] * (float) Unit.deg;
						frame.telescopeVPA = iVPA[i] * (float) Unit.deg; // TODO
						frame.setParallacticAngle(frame.telescopeVPA);
						frame.setRotation(frame.instrumentVPA - frame.telescopeVPA); 
					}


					// Calculate the scanning offsets...
					frame.horizontalOffset = frame.equatorial.getNativeOffsetFrom(scan.equatorial);
					frame.equatorialNativeToHorizontal(frame.horizontalOffset);
					
					// Add the chopper offset to the telescope coordinates.
					// TODO check!
					if(hawcPlusScan.isChopping) {
						frame.horizontalOffset.add(frame.chopperPosition);
						frame.horizontal.addOffset(frame.chopperPosition);
					
						offset = frame.chopperPosition;
						frame.horizontalToNativeEquatorial(offset);
						frame.equatorial.addNativeOffset(offset);
					}
				
						
					set(startIndex + i, frame);
				}
				
			};
		}
	}	


	@Override
	public void writeProducts() {
		super.writeProducts();
		
		if(hasOption("write.flatfield")) {
			String fileName = option("write.flatfield").getValue();
			if(fileName.isEmpty()) fileName = CRUSH.workPath + File.separator + "flatfield-" + getDisplayID() + ".fits";
			try { instrument.writeFlatfield(fileName); }
			catch(Exception e) { e.printStackTrace(); }
		}
	}
	
	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}

	
	
}
