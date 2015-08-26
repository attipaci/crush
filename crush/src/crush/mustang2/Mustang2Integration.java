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

package crush.mustang2;

import kovacs.astro.AstroTime;
import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.HorizontalCoordinates;
import kovacs.util.Unit;
import kovacs.util.Util;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.FitsException;
import crush.GroundBased;
import crush.Integration;
import crush.Scan;
import crush.fits.HDUReader;


public class Mustang2Integration extends Integration<Mustang2, Mustang2Frame> implements GroundBased {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1478289598893263095L;
	
	
	public Mustang2Integration(Scan<Mustang2, ?> parent) {
		super(parent);
	}


	@Override
	public Mustang2Frame getFrameInstance() {
		return new Mustang2Frame((Mustang2Scan) scan);
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
		
		new Mustang2Reader(hdu).read();
	}


	
	class Mustang2Reader extends HDUReader {	
		private float[] data;
		private double[] MJD, RA, DEC, AZ, EL, LST;
		private int channels;
		
		private final Mustang2Scan mustang2Scan = (Mustang2Scan) scan;
		
		public Mustang2Reader(BinaryTableHDU hdu) throws FitsException {
			super(hdu);
		
			int iData = hdu.findColumn("DATA");
			channels = table.getSizes()[iData];
			data = (float[]) table.getColumn(iData);
			
			// The IRAM coordinate data...
			MJD = (double[]) table.getColumn(hdu.findColumn("DMJD"));
			LST = (double[]) table.getColumn(hdu.findColumn("LST"));
			
			RA = (double[]) table.getColumn(hdu.findColumn("GBTRA"));
			DEC = (double[]) table.getColumn(hdu.findColumn("GBTDEC"));
			
			AZ = (double[]) table.getColumn(hdu.findColumn("GBTAZ"));
			EL = (double[]) table.getColumn(hdu.findColumn("GBTEL"));
			
		}
		
		@Override
		public Reader getReader() {
			return new Reader() {
				private AstroTime timestamp;
				
				@Override
				public void init() {
					timestamp = new AstroTime();
				}
				
				@Override
				public void processRow(int i) {
					// Create the frame object only if it cleared the above hurdles...
					final Mustang2Frame frame = new Mustang2Frame(mustang2Scan);
					frame.index = i;
						
					// Read the pixel data
					frame.parseData(data, i*channels, channels);	
					
					// Add in the astrometry...
					frame.MJD = MJD[i];
					timestamp.setMJD(frame.MJD);
					frame.LST = LST[i] * Unit.hourAngle;
					
					frame.equatorial = new EquatorialCoordinates(RA[i] * Unit.deg, DEC[i] * Unit.deg, scan.equatorial.epoch);
					frame.horizontal = new HorizontalCoordinates(AZ[i] * Unit.deg, EL[i] * Unit.deg);
						
					// Calculate the parallactic angle
					frame.calcParallacticAngle();	
		
					frame.horizontalOffset = frame.equatorial.getNativeOffsetFrom(scan.equatorial);
					frame.equatorialNativeToHorizontal(frame.horizontalOffset);
					
					set(i, frame);
				}
			};
		}
	}	
	
	@Override
	public void setTau() throws Exception {
		Mustang2Scan mustang2Scan = (Mustang2Scan) scan;
		if(!Double.isNaN(mustang2Scan.zenithTau)) setTau(mustang2Scan.zenithTau);
	}

	
}
