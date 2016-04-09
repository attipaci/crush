/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import crush.Channel;
import crush.fits.HDUReader;
import crush.sofia.SofiaChopperData;
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

	public boolean flagJumps = true;
	
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
		private short[] jump;
		private long[] data;
		private double[] TS; 
		private double[] RA, DEC, AZ, EL, iVPA, tVPA, cVPA, LON, LAT, LST, PWV;
		private double[] objectRA, objectDEC;
		private float[] chopR, chopS;
		private int[] HWP;
	
		private boolean isLab;
		
		private final HawcPlusScan hawcPlusScan = (HawcPlusScan) scan;
		
		public HawcPlusReader(BinaryTableHDU hdu, int startIndex) throws FitsException {
			super(hdu);
			
			this.startIndex = startIndex;
		
			isLab = hasOption("lab");
			
			Object[] row = (Object[]) hdu.getRow(0);
			
			// The Sofia timestamp (decimal seconds since 0 UTC 1 Jan 1970...
			TS = (double[]) table.getColumn(hdu.findColumn("Timestamp"));	
			SN = (long[]) table.getColumn(hdu.findColumn("FrameCounter"));
			jump = (short[]) table.getColumn(hdu.findColumn("FluxJumps"));
			
			// The R/T array data
			int iData = hdu.findColumn("SQ1Feedback");
			data = (long[]) table.getColumn(iData);
			
			int storeRows = ((long[][]) row[iData]).length;
			int storeCols = ((long[][]) row[iData])[0].length;
			System.err.println("   FITS has " + storeRows + "x" + storeCols + " arrays.");
			
			// HWP may be used in the future if support is extended for
            // scan-mode polarimetry (or polarimetry, in general...
			HWP = (int[]) table.getColumn(hdu.findColumn("hwpCount"));
            
			// Ignore coordinate info for 'lab' data...
			if(isLab) {
			    System.err.println("   Lab mode data reduction. Ignoring telescope data...");
			    return;
			}
			
			// The tracking center in the basis coordinates of the scan (usually RA/DEC)
			RA = (double[]) table.getColumn(hdu.findColumn("RA"));
			DEC = (double[]) table.getColumn(hdu.findColumn("DEC"));

			if(scan.isMovingObject) {
			    objectRA = (double[]) table.getColumn(hdu.findColumn("NonSiderealRA"));
	            objectDEC = (double[]) table.getColumn(hdu.findColumn("NonSiderealDEC"));
			}
			
			// The scanning offsets in the offset system (usually AZ/EL)
			AZ = (double[]) table.getColumn(hdu.findColumn("AZ"));
			EL = (double[]) table.getColumn(hdu.findColumn("EL"));

			LST = (double[]) table.getColumn(hdu.findColumn("LST"));

			iVPA = (double[]) table.getColumn(hdu.findColumn("SIBS_VPA"));
			tVPA = (double[]) table.getColumn(hdu.findColumn("TABS_VPA"));
			cVPA = (double[]) table.getColumn(hdu.findColumn("Chop_VPA"));

			LON = (double[]) table.getColumn(hdu.findColumn("LON"));
			LAT = (double[]) table.getColumn(hdu.findColumn("LAT"));

			chopR = (float[]) table.getColumn(hdu.findColumn("sofiaChopR"));
			chopR = (float[]) table.getColumn(hdu.findColumn("sofiaChopS"));

			PWV = (double[]) table.getColumn(hdu.findColumn("PWV"));
			
		}
		
		@Override
		public Reader getReader() {
			return new Reader() {	
				private AstroTime timeStamp;
				private EquatorialCoordinates objectEq;
				private Vector2D offset;
				
				@Override
				public void init() {
					timeStamp = new AstroTime();
					offset = new Vector2D();
					objectEq = scan.isMovingObject ? new EquatorialCoordinates() : (EquatorialCoordinates) scan.equatorial.copy();		
				}
				
				@Override
				public void processRow(int i) {
					// TODO may not be needed...
				    set(startIndex + i, null);
										
					// Create the frame object only if it cleared the above hurdles...
					final HawcPlusFrame frame = new HawcPlusFrame(hawcPlusScan);
					frame.index = i;
					frame.hasTelescopeInfo = !isLab;
						
					// Read the pixel data (DAC and MCE jump counter)
					frame.parseData(i, data, jump);
					frame.mceSerial = SN[i];
					
                    timeStamp.setUTCMillis(Math.round(1000.0 * TS[i]));
                    frame.MJD = timeStamp.getMJD();
                       
                    frame.hwpAngle = (float) (HWP[i] * HawcPlus.hwpStep);
                    
                    set(startIndex + i, frame);
					
					if(!frame.hasTelescopeInfo) return;
					
					// Below here is telescope data only, which will be ignored for 'lab' mode reductions...
					// Add the astrometry...
					
					frame.equatorial = new EquatorialCoordinates(RA[i] * Unit.hourAngle, DEC[i] * Unit.deg, scan.equatorial.epoch);
					if(scan.isMovingObject) objectEq.set(objectRA[i] * Unit.hourAngle, objectDEC[i] * Unit.deg);
					
					frame.site = new GeodeticCoordinates(LON[i] * Unit.deg, LAT[i] * Unit.deg);
					
					if(hawcPlusScan.isChopping) {
					   // In telescope XEL, EL
					   // TODO check sign convention!!!
					   frame.chopperPosition = new Vector2D(chopS[i] * Unit.V, chopR[i] * Unit.V);
					   frame.chopperPosition.scale(SofiaChopperData.volts2Angle);
					   // TODO check signs...
					   frame.chopperPosition.rotate((cVPA[i] - tVPA[i]) * Unit.deg);
					}
					else frame.chopperPosition = new Vector2D();
					
					frame.PWV = PWV[i] * (float) Unit.um;

					frame.LST = LST[i] * (float) Unit.hour;
					frame.horizontal = new HorizontalCoordinates(AZ[i] * Unit.deg, EL[i] * Unit.deg);
					
					// TODO use actual telescope XEL, EL...
					frame.telescopeCoords = new TelescopeCoordinates(AZ[i] * Unit.deg, EL[i] * Unit.deg);
							
					// calc parallactic angle...
					// rot: sibs VPA - PA
					frame.instrumentVPA = iVPA[i] * (float) Unit.deg;
					frame.telescopeVPA = tVPA[i] * (float) Unit.deg; // TODO
					frame.setParallacticAngle(frame.telescopeVPA);
					
					// TODO check signs!!!
					frame.setRotation(frame.instrumentVPA - frame.telescopeVPA); 
				
					// Calculate the scanning offsets...
					frame.horizontalOffset = frame.equatorial.getNativeOffsetFrom(objectEq);
					frame.equatorialNativeToHorizontal(frame.horizontalOffset);
					
					// Add the chopper offset to the telescope coordinates.
					// TODO check!
					if(hawcPlusScan.isChopping) {
						frame.horizontalOffset.add(frame.chopperPosition);
						frame.horizontal.addOffset(frame.chopperPosition);
					
						// TODO check native vs nominal
                        frame.equatorial.addOffset(frame.chopperPosition);
                        
                        offset = (Vector2D) frame.chopperPosition.copy();
                        frame.horizontalToNativeEquatorial(offset);
						frame.equatorial.addNativeOffset(offset);
					}	
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

	@Override
    public void validate() {
	    super.validate();
	    flagJumps = hasOption("flagjumps");
	}
	
	@Override
    public boolean checkConsistency(final Channel channel, final int from, final int to) {
	    super.checkConsistency(channel, from, to);
	    
	    if(!flagJumps) return true;
	    
	    final byte jumpStart = get(from).jumpCounter[channel.index];
	    final int clearFlag = ~HawcPlusFrame.SAMPLE_PHI0_JUMP;
	    
	    for(int t=to; --t > from; ) if(get(t).jumpCounter[channel.index] != jumpStart) {
	        get(t).sampleFlag[channel.index] &= clearFlag;
	        flagJump(channel, from, to);
	        return false;
	    }
	   
	    return true;
	}
	
	private void flagJump(final Channel channel, final int from, int to) {
	    while(--to >= from) get(to).sampleFlag[channel.index] |= HawcPlusFrame.SAMPLE_PHI0_JUMP;
	}
	
	
	
}
