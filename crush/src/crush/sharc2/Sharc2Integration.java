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

package crush.sharc2;

import crush.*;
import nom.tam.fits.*;

import java.io.*;
import java.net.*;

import util.*;
import util.astro.*;
import util.data.DataPoint;
import crush.cso.CSOTauTable;
import crush.fits.HDUReader;

// TODO Split nod-phases into integrations...
public class Sharc2Integration extends Integration<Sharc2, Sharc2Frame> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2387643745464766162L;
	
	public boolean hasExtraTimingInfo = false;
	private DataPoint chopZero = new DataPoint();
	
	public Sharc2Integration(Sharc2Scan parent) {
		super(parent);
	}

	@Override
	public void validate() {	
		// Tau is set here...
		super.validate();	
			
		boolean directTau = false;
		if(hasOption("tau")) directTau = option("tau").equals("direct"); 
		
		if(!directTau) {		
			double measuredLoad = instrument.getLoadTemperature(); 
			double eps = (measuredLoad - instrument.excessLoad) / ((Sharc2Scan) scan).ambientT;
			double tauLOS = -Math.log(1.0-eps);
			System.err.println("   Tau from bolometers (not used):");
			printEquivalentTaus(tauLOS * scan.horizontal.sinLat());
			
			if(!hasOption("excessload")) instrument.excessLoad = measuredLoad - getSkyLoadTemperature();
			System.err.println("   Excess optical load on bolometers is " + Util.f1.format(instrument.excessLoad) + " K. (not used)");		
		}
		
	}
	
	@Override
	public void fillGaps() {
		// trim gaps...
		if(hasOption("nogaps")) {
			String argument = option("nogaps").getValue();
			if(argument.length() == 0) trimToGap();
			else if(argument.equalsIgnoreCase("JSharc")) if(scan.creator.equals("JSharc")) trimToGap();
		}
		else super.fillGaps();		
	}
	
	@Override
	public void writeProducts() {
		super.writeProducts();
		
		String scanID = getID();
		// Needs 'excessload'
		if(hasOption("response.calc")) {
			instrument.calcGainCoefficients(getSkyLoadTemperature());
			try { instrument.writeGainCoefficients(CRUSH.workPath + File.separator + "response-" + scanID + ".dat", getASCIIHeader()); }
			catch(IOException e) { System.err.println("   WARNING! Error writing nonlinearity coefficients: " + e.getMessage()); }
		}
	}
	
	public void printEquivalentTaus(double value) {	
		System.err.println("   --->"
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz", value))
				+ ", tau(350um):" + Util.f3.format(getTau("350um", value))
				+ ", tau(LOS):" + Util.f3.format(value / scan.horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv", value)) + "mm"
		);		
	}
	
	
	
	@Override
	public void setTau() throws Exception {
		String source = option("tau").getValue().toLowerCase();
			
		if(source.equals("tables")) {
			source = hasOption("tau.tables") ? option("tau.tables").getValue() : ".";
			String date = scan.getID().substring(0, scan.getID().indexOf('.'));
			String spec = date.substring(2, 4) + date.substring(5, 7) + date.substring(8, 10);
			
			File file = new File(Util.getSystemPath(source) + File.separator + spec + ".dat");
			if(!file.exists()) {
				System.err.print("   WARNING! No tau table found for " + date + "...");
				System.err.print("            Using default tau.");
				instrument.options.remove("tau");
				setTau();
				return;
			}
			
			CSOTauTable table = CSOTauTable.get(((Sharc2Scan) scan).iMJD, file.getPath());
			setTau("225GHz", table.getTau(getMJD()));	
		
		}
		else if(source.equals("direct")) setZenithTau(getDirectTau());
		else {
			if(source.equals("maitau")) {
				try {
					try { setTau("350um", getMaiTau("350um")); }
					catch(NumberFormatException no350) { setTau("225GHz", getMaiTau("225GHz")); }
				}	
				catch(Exception e) {
					if(hasOption("maitau.fallback")) {
						System.err.print("   WARNING! MaiTau lookup failed. ");
						source = option("maitau.fallback").getValue().toLowerCase();
						if(source.equals("maitau")) {
							System.err.println("Deadlocked fallback option.");
							throw e;
						}	
						System.err.println("Falling back to '" + source + "'.");
						instrument.options.process("tau", source);
						setTau();
						return;
					}
					else throw e;				
				}
			}
			else super.setTau();
		}
		
		printEquivalentTaus(zenithTau);
		
		// TODO move to obslog...
		double tauLOS = zenithTau / scan.horizontal.sinLat();
		System.err.println("   Optical load is " + Util.f1.format(((Sharc2Scan) scan).ambientT * (1.0 - Math.exp(-tauLOS))) + " K.");
	
	}

	
	
	public void trimToGap() {
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
			System.err.println("   WARNING! Gap detected. Discarding data after gap.");
			this.trim(0, t);
		}
	}
	
	
	@Override
	public Sharc2Frame getFrameInstance() {
		return new Sharc2Frame((Sharc2Scan) scan);
	}
	
	
	
	protected void read(BasicHDU[] HDU, int firstDataHDU) throws Exception {
		
		int nDataHDUs = HDU.length - firstDataHDU, records = 0;
		for(int datahdu=0; datahdu<nDataHDUs; datahdu++) records += HDU[firstDataHDU + datahdu].getAxes()[0];

		System.err.println(" Processing scan data:");
		
		System.err.println("   " + nDataHDUs + " HDUs,  " + records + " x " +
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
		
		//if(!isEmpty()) if(chopZero.weight() > 0.0) chopZero.scaleValue(1.0 / chopZero.weight());
		
		Sharc2Scan sharcscan = (Sharc2Scan) scan;
		
		if(sharcscan.addOffsets) for(Sharc2Frame frame : this) {
			// Remove the small zero offset from the chopper signal.
			frame.chopperPosition.subtractX(chopZero.value());	
			// Add chopper offset to the aggregated horizontal offset...
			frame.horizontalOffset.add(frame.chopperPosition);
			// Add the chopper offset to the absolute coordinates also...
			frame.horizontal.addOffset(frame.chopperPosition);
		}
	
		
	}
		
	class Sharc2Reader extends HDUReader {	
		private boolean hasExtraTimingInfo, isDoubleUT;
		private int offset;

		private float[] data, fUT, AZ, EL, AZO, ELO, AZE, ELE, RAO, DECO, LST, PA, chop;
		private double[] dUT, DT;
		private int[] SN;
		private int channels;
		
		private final Sharc2Scan sharcscan = (Sharc2Scan) scan;
		
		public Sharc2Reader(TableHDU hdu, int offset) throws FitsException {
			super(hdu);
			this.offset = offset;			

			channels = table.getSizes()[0];
			
			data = (float[]) table.getColumn(0);
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
			
			int iDT = hdu.findColumn("Detector Time");
			int iSN = hdu.findColumn("Sequence Number");			
			hasExtraTimingInfo = iDT >= 0 && iSN >= 0; 
			
			if(iDT > 0) DT = (double[]) table.getColumn(iDT);
			if(iSN > 0) SN = (int[]) table.getColumn(iSN);
			
			int iUT = hdu.findColumn("UT");
			isDoubleUT = hdu.getHeader().getStringValue("TFORM" + (iUT+1)).equalsIgnoreCase("1D");
			
			if(isDoubleUT) dUT = (double[]) table.getColumn(iUT);
			else fUT = (float[]) table.getColumn(iUT);
			
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
				public void readRow(int i) throws FitsException {	

					final Sharc2Frame frame = new Sharc2Frame(sharcscan);
					frame.index = i;
					
					frame.parseData(data, i*channels, channels);

					final double UT = isDoubleUT ? dUT[i] * Unit.hour : fUT[i] * Unit.hour;
					frame.MJD = sharcscan.iMJD + UT / Unit.day;
	
					// Enforce the calculation of the equatorial coordinates
					frame.equatorial = null;

					frame.horizontal = new HorizontalCoordinates(
							AZ[i] * Unit.deg + AZE[i] * Unit.arcsec,
							EL[i] * Unit.deg + ELE[i] * Unit.arcsec);
					
					final double pa = PA[i] * Unit.deg;
					frame.sinPA = Math.sin(pa);
					frame.cosPA = Math.cos(pa);

					frame.LST = LST[i] * Unit.hour;

					if(hasExtraTimingInfo) {
						frame.dspTime = DT[i] * Unit.sec;
						frame.frameNumber = SN[i];
					}		

					frame.horizontalOffset = new Vector2D(
							(AZO[i] + AZE[i] * frame.horizontal.cosLat()) * Unit.arcsec,
							(ELO[i] + ELE[i]) * Unit.arcsec);
				
					frame.chopperPosition.setX(chop[i] * Unit.arcsec);
					
					//chopZero.add(frame.chopperPosition.getX());
					//chopZero.addWeight(1.0);

					// Add in the scanning offsets...
					if(sharcscan.addOffsets) frame.horizontalOffset.add(sharcscan.horizontalOffset);	

					// Add in the equatorial sweeping offsets
					// Watch out for the sign of the RA offset, which is counter to the native coordinate direction
					equatorialOffset.set(RAO[i] * Unit.arcsec, DECO[i] * Unit.arcsec);	
					
					
					frame.toHorizontal(equatorialOffset);
					frame.horizontalOffset.add(equatorialOffset);
		
					set(offset + i, frame);
				}
			};
		}
	}

	public double getMaiTau(String id) throws IOException {
		// Return immediately if ID does not match 225GHz or 350um, which are the only values in
		// the Mai-Tau lookup at present
		if(!id.equalsIgnoreCase("225GHz") && !id.equalsIgnoreCase("350um")) 
			throw new IllegalArgumentException("No MaiTau lookup for '" + id + "'.");
		
		if(!hasOption("maitau.server")) 
			throw new IllegalArgumentException(" WARNING! MaiTau server not set. Use 'maitau.server' configuration key.");
		
		Socket tauServer = new Socket();
		tauServer.setSoTimeout(3000);
		tauServer.setTcpNoDelay(true); 
		//tauServer.setPerformancePreferences(0, 1, 2); // connection time, latency, throughput
		tauServer.setTrafficClass(0x10); // low latency
		tauServer.connect(new InetSocketAddress(option("maitau.server").getValue(), 63225));
		
		PrintWriter out = new PrintWriter(tauServer.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(tauServer.getInputStream()));

		while(in.read() != '>'); // Seek for prompt

		out.println("set noexit"); // Enter into interactive mode (do not disconnect after first command).

		while(in.read() != '>'); // Seek for prompt

		out.println("set " + id); // Select which tau value to query...

		while(in.read() != '>'); // Seek for prompt

		out.println("get tau " + scan.timeStamp); // Request tau for the specified date	
		
		double value = Double.NaN;
		
		try { 
			value = Double.parseDouble(in.readLine().trim());
			System.err.println("   Got MaiTau! tau(" + id + ") = " + Util.f3.format(value));
		}
		catch(NumberFormatException e) {}
		
		out.println("exit"); // Disconnect from Mai-Tau server 
		in.close();
		out.close();
		
		tauServer.close();
		
		if(Double.isNaN(value)) throw new NumberFormatException("No " + id + " value for date in MaiTau database.");
		
		return value;
	}

	
	public double getSkyLoadTemperature() {
		double transmission = 0.5 * (getFirstFrame().transmission + getLastFrame().transmission);
		return (1.0 - transmission) * ((Sharc2Scan) scan).ambientT;
	}


	public double getDirectTau() { 
		double eps = (instrument.getLoadTemperature() - instrument.excessLoad) / ((Sharc2Scan) scan).ambientT; 	
		return -Math.log(1.0-eps) * scan.horizontal.sinLat();
	}

	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}
}
