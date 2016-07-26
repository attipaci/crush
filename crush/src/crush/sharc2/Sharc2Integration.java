/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sharc2;

import crush.*;
import nom.tam.fits.*;

import java.io.*;

import crush.cso.CSOIntegration;
import crush.fits.HDUReader;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.math.Vector2D;

// TODO Split nod-phases into integrations...
public class Sharc2Integration extends CSOIntegration<Sharc2, Sharc2Frame> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2387643745464766162L;
	
	public boolean hasExtraTimingInfo = false;
	
	public Sharc2Integration(Sharc2Scan parent) {
		super(parent);
	}

	@Override
	public void validate() {	
	  
		// Tau is set here...
		super.validate();	
	
		if(hasOption("tau")) if(!option("tau").equals("direct")) {		
			double measuredLoad = instrument.getLoadTemperature(); 
			double eps = (measuredLoad - instrument.excessLoad) / ((Sharc2Scan) scan).getAmbientTemperature();
			double tauLOS = -Math.log(1.0-eps);
			info("Tau from bolometers (not used):");
			printEquivalentTaus(tauLOS * scan.horizontal.sinLat());
			
			if(!hasOption("excessload")) instrument.excessLoad = measuredLoad - getSkyLoadTemperature();
			info("Excess optical load on bolometers is " + Util.f1.format(instrument.excessLoad) + " K. (not used)");		
		}
		
	}
	
	@Override
	public void fillGaps() {
		// trim gaps...
		if(hasOption("nogaps")) trimToGap();
		else super.fillGaps();		
	}
	
	@Override
	public void writeProducts() {
		super.writeProducts();
		
		// Needs 'excessload'
		if(hasOption("response.calc")) {
			instrument.calcGainCoefficients(getSkyLoadTemperature());
			try { instrument.writeGainCoefficients(CRUSH.workPath + File.separator + "response-" + getFileID() + ".dat", getASCIIHeader()); }
			catch(IOException e) { warning("Could not write nonlinearity coefficients: " + e.getMessage()); }
		}
	}
	
	public void trimToGap() {
		info("Trimming timestream to first gap.");
		
		Sharc2Frame first = getFirstFrame();
		Sharc2Frame last = getLastFrame();
		
		int t = first.index + 1;
		
		for(; t<=last.index; t++) {
			Sharc2Frame exposure = get(t);
			if(exposure != null) {
				int dN = (t - first.index);
				if(exposure.MJD > first.MJD + (dN + 1) * instrument.samplingInterval) break;
				if(hasExtraTimingInfo) {
					if(exposure.frameNumber > first.frameNumber + (dN + 1)) break;
					if(exposure.dspTime > first.dspTime + (dN + 1) * instrument.samplingInterval) break;
				}
			}
		}
		
		if(t < last.index) {
			warning("Gap detected. Discarding data after gap.");
			for(int i=size(); --i >= t; ) remove(i);
		}
	}
	
	
	@Override
	public Sharc2Frame getFrameInstance() {
		return new Sharc2Frame((Sharc2Scan) scan);
	}
	
	
	
	protected void read(BasicHDU<?>[] HDU, int firstDataHDU) throws Exception {
		
		int nDataHDUs = HDU.length - firstDataHDU, records = 0;
		for(int datahdu=0; datahdu<nDataHDUs; datahdu++) records += HDU[firstDataHDU + datahdu].getAxes()[0];

		scan.info("Processing scan data:");
		
		info(nDataHDUs + " HDUs,  " + records + " x " +
				(int)(instrument.integrationTime/Unit.ms) + "ms frames" + " -> " + 
				Util.f1.format(records*instrument.integrationTime/Unit.min) + " minutes total."); 
	
			
		clear();
		ensureCapacity(records);
		for(int t=records; --t>=0; ) add(null);
					
		
		for(int n=0, startIndex = 0; n<nDataHDUs; n++) {
			BinaryTableHDU hdu = (BinaryTableHDU) HDU[firstDataHDU+n]; 
			new Sharc2Reader(hdu, startIndex).read();	
			startIndex += hdu.getNRows();
		}	
		
	}
		
	class Sharc2Reader extends HDUReader {	
		private boolean hasExtraTimingInfo, isDoubleUT;
		private int offset;

		private float[] data, fUT, AZ, EL, AZO, ELO, AZE, ELE, RAO, DECO, LST, PA, chop;
		private double[] dUT, DT;
		private int[] SN;
		private int channels;
		private boolean isLab;
		
		private final Sharc2Scan sharcscan = (Sharc2Scan) scan;
		
		public Sharc2Reader(TableHDU<?> hdu, int offset) throws FitsException {
			super(hdu);
			this.offset = offset;			

			channels = table.getSizes()[0];
			
			isLab = hasOption("lab");
			
			data = (float[]) table.getColumn(0);
			
			int iDT = hdu.findColumn("Detector Time");
            int iSN = hdu.findColumn("Sequence Number");            
            hasExtraTimingInfo = iDT >= 0 && iSN >= 0; 
            
            if(iDT > 0) DT = (double[]) table.getColumn(iDT);
            if(iSN > 0) SN = (int[]) table.getColumn(iSN);
            
            int iUT = hdu.findColumn("UT");
            isDoubleUT = hdu.getHeader().getStringValue("TFORM" + (iUT+1)).equalsIgnoreCase("1D");
            
            if(isDoubleUT) dUT = (double[]) table.getColumn(iUT);
            else fUT = (float[]) table.getColumn(iUT);
            
            if(isLab) {
                info("Laboratory data reduction. Ignoring telescope information...");
                return;
            }
			
			AZ = (float[]) table.getColumn(hdu.findColumn("AZ"));
			EL = (float[]) table.getColumn(hdu.findColumn("EL"));
			AZE = (float[]) table.getColumn(hdu.findColumn("AZ_ERROR"));
			ELE = (float[]) table.getColumn(hdu.findColumn("EL_ERROR"));
			PA = (float[]) table.getColumn(hdu.findColumn("Parallactic Angle"));
			LST = (float[]) table.getColumn(hdu.findColumn("LST"));
			RAO = (float[]) table.getColumn(Math.max(hdu.findColumn("RAO"), hdu.findColumn("RA_OFFSET")));
			DECO = (float[]) table.getColumn(Math.max(hdu.findColumn("DECO"), hdu.findColumn("DEC_OFFSET")));
			AZO = (float[]) table.getColumn(Math.max(hdu.findColumn("AZO"), hdu.findColumn("AZ_OFFSET")));
			ELO = (float[]) table.getColumn(Math.max(hdu.findColumn("ELO"), hdu.findColumn("EL_OFFSET")));
			chop = (float[]) table.getColumn(hdu.findColumn("CHOP_OFFSET"));
			
			//iFlag = hdu.findColumn("Celestial");
			//iTRK = hdu.findColumn("Tracking");
			
			
			
		}
	
		@Override
		public Reader getReader() {
			return new Reader() {
				private Vector2D equatorialOffset;

				@Override
				public void init() { 
					super.init();
					equatorialOffset = new Vector2D();
				}
				@Override
				public void processRow(int i) throws FitsException {	

					final Sharc2Frame frame = new Sharc2Frame(sharcscan);
					frame.hasTelescopeInfo = !isLab;
					frame.index = i;
					
					frame.parseData(data, i*channels, channels);

					final double UT = isDoubleUT ? dUT[i] * Unit.hour : fUT[i] * Unit.hour;
					frame.MJD = sharcscan.iMJD + UT / Unit.day;
	
					if(hasExtraTimingInfo) {
                        frame.dspTime = DT[i] * Unit.sec;
                        frame.frameNumber = SN[i];
                    }       
					
					// Enforce the calculation of the equatorial coordinates
					frame.equatorial = null;
					
					set(offset + i, frame);
						
					// Do not add coordinate information for lab reductions...
					if(!frame.hasTelescopeInfo) return;
					
					
					frame.horizontal = new HorizontalCoordinates(
					        AZ[i] * Unit.deg + AZE[i] * Unit.arcsec,
					        EL[i] * Unit.deg + ELE[i] * Unit.arcsec);

					final double pa = PA[i] * Unit.deg;
					frame.sinPA = Math.sin(pa);
					frame.cosPA = Math.cos(pa);

					frame.LST = LST[i] * Unit.hour;


					frame.horizontalOffset = new Vector2D(
					        (AZO[i] + AZE[i] * frame.horizontal.cosLat()) * Unit.arcsec,
					        (ELO[i] + ELE[i]) * Unit.arcsec);

					frame.chopperPosition.setX(chop[i] * Unit.arcsec);

					//chopZero.add(frame.chopperPosition.getX());
					//chopZero.addWeight(1.0);

					// Add in the scanning offsets...
					if(sharcscan.addStaticOffsets) frame.horizontalOffset.add(sharcscan.horizontalOffset);	

					// Add in the equatorial sweeping offsets
					// Watch out for the sign of the RA offset, which is counter to the native coordinate direction
					equatorialOffset.set(RAO[i] * Unit.arcsec, DECO[i] * Unit.arcsec);	

					frame.equatorialToHorizontal(equatorialOffset);
					frame.horizontalOffset.add(equatorialOffset);
					
					
				}
			};
		}
	}

	/*
	class Sharc2RowReader extends HDURowReader {	
		private boolean hasExtraTimingInfo, isDoubleUT;

		
		private int iData, iUT, iAZ, iEL, iAZO, iELO, iAZE, iELE, iRAO, iDECO, iLST, iPA, iChop;
		private int iDT, iSN;
		private int channels;
		
		private final Sharc2Scan sharcscan = (Sharc2Scan) scan;
		
		public Sharc2RowReader(BinaryTableHDU hdu, ArrayDataInput in) throws FitsException {
			super(hdu, in);		

			iData = 0;
			channels = table.getSizes()[iData];
			
			iAZ = hdu.findColumn("AZ");
			iEL = hdu.findColumn("EL");
			iAZE = hdu.findColumn("AZ_ERROR");
			iELE = hdu.findColumn("EL_ERROR");
			iPA = hdu.findColumn("Parallactic Angle");
			iLST = hdu.findColumn("LST");
			iRAO = Math.max(hdu.findColumn("RAO"), hdu.findColumn("RA_OFFSET"));
			iDECO = Math.max(hdu.findColumn("DECO"), hdu.findColumn("DEC_OFFSET"));
			iAZO = Math.max(hdu.findColumn("AZO"), hdu.findColumn("AZ_OFFSET"));
			iELO = Math.max(hdu.findColumn("ELO"), hdu.findColumn("EL_OFFSET"));
			iChop = hdu.findColumn("CHOP_OFFSET");
			
			//iFlag = hdu.findColumn("Celestial");
			//iTRK = hdu.findColumn("Tracking");
			
			iDT = hdu.findColumn("Detector Time");
			iSN = hdu.findColumn("Sequence Number");			
			hasExtraTimingInfo = iDT >= 0 && iSN >= 0; 
			
			iUT = hdu.findColumn("UT");
			isDoubleUT = hdu.getHeader().getStringValue("TFORM" + (iUT+1)).equalsIgnoreCase("1D");
		}
	
		@Override
		public Reader getReader() throws FitsException {
			return new Reader() {
				private Vector2D offset;

				@Override
				public void init() { 
					super.init();
					offset = new Vector2D();
				}
				
				@Override
				public void processRow(int index, Object[] row) throws FitsException {	

					final Sharc2Frame frame = new Sharc2Frame(sharcscan);
					frame.index = index;
					
					frame.parseData((float[][]) row[iData]);

					final double UT = (isDoubleUT ? ((double[]) row[iUT])[0] : ((float[]) row[iUT])[0]) * Unit.hour;
					frame.MJD = sharcscan.iMJD + UT / Unit.day;
	
					// Enforce the calculation of the equatorial coordinates
					frame.equatorial = null;

					frame.horizontal = new HorizontalCoordinates(
							((float[]) row[iAZ])[0] * Unit.deg + ((float[]) row[iAZE])[0] * Unit.arcsec,
							((float[]) row[iEL])[0] * Unit.deg + ((float[]) row[iELE])[0] * Unit.arcsec);
					
					final double pa = ((float[]) row[iPA])[0] * Unit.deg;
					frame.sinPA = Math.sin(pa);
					frame.cosPA = Math.cos(pa);

					frame.LST = ((float[]) row[iLST])[0] * Unit.hour;

					if(hasExtraTimingInfo) {
						frame.dspTime = ((double[]) row[iDT])[0] * Unit.sec;
						frame.frameNumber = ((int[]) row[iSN])[0];
					}		

					frame.horizontalOffset = new Vector2D(
							(((float[]) row[iAZO])[0] + ((float[]) row[iAZE])[0] * frame.horizontal.cosLat()) * Unit.arcsec,
							(((float[]) row[iELO])[0] + ((float[]) row[iELE])[0]) * Unit.arcsec);
				
					frame.chopperPosition.setX(((float[]) row[iChop])[0] * Unit.arcsec);
					
					//chopZero.add(frame.chopperPosition.getX());
					//chopZero.addWeight(1.0);

					// Add in the scanning offsets...
					if(sharcscan.addStaticOffsets) frame.horizontalOffset.add(sharcscan.horizontalOffset);	

					// Add in the equatorial sweeping offsets
					// Watch out for the sign of the RA offset, which is counter to the native coordinate direction
					offset.set(((float[]) row[iRAO])[0] * Unit.arcsec, ((float[]) row[iDECO])[0] * Unit.arcsec);	
					
					
					frame.equatorialToHorizontal(offset);
					frame.horizontalOffset.add(offset);
		
					set(index, frame);
				}
			};
		}
	}
	*/
	
	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}
}
