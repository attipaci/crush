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
package crush.mako;

import crush.*;
import nom.tam.fits.*;

import java.io.*;
import java.net.*;

import kovacs.util.*;
import kovacs.util.astro.*;
import kovacs.util.data.DataPoint;

import crush.cso.CSOTauTable;
import crush.fits.HDUReader;

public class MakoIntegration extends Integration<Mako, MakoFrame> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2439173655341594018L;
	public boolean hasExtraTimingInfo = false;
	private DataPoint chopZero = new DataPoint();
	
	public MakoIntegration(MakoScan parent) {
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
			double eps = (measuredLoad - instrument.excessLoad) / ((MakoScan) scan).ambientT;
			double tauLOS = -Math.log(1.0-eps);
			
			// TODO
			//System.err.println("   Tau from bolometers (not used):");
			//printEquivalentTaus(tauLOS * scan.horizontal.sinLat());
			
			
			if(!hasOption("excessload")) instrument.excessLoad = measuredLoad - getSkyLoadTemperature();
			//System.err.println("   Excess optical load on bolometers is " + Util.f1.format(instrument.excessLoad) + " K. (not used)");		
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
			
			CSOTauTable table = CSOTauTable.get(((MakoScan) scan).iMJD, file.getPath());
			table.setOptions(option("tau"));
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
		
		double tauLOS = zenithTau / scan.horizontal.sinLat();
		System.err.println("   Optical load is " + Util.f1.format(((MakoScan) scan).ambientT * (1.0 - Math.exp(-tauLOS))) + " K.");
	
	}

	@Override
	public String getASCIIHeader() {
		double eps = 1.0 - Math.exp(-zenithTau / scan.horizontal.sinLat());
		double Tload = ((MakoScan) scan).getAmbientTemperature();
		
		return super.getASCIIHeader() + "\n" 
				+ "# tau(225GHz) = " + Util.f3.format(this.getTau("225ghz")) + "\n"
				+ "# T_amb = " + Util.f1.format(((MakoScan) scan).getAmbientTemperature()/Unit.H - 273.16) + " C\n" 
				+ "# T_load = " + Util.f1.format(Tload * eps / Unit.K) + " K";
	}
	 
	@Override
	public MakoFrame getFrameInstance() {
		return new MakoFrame((MakoScan) scan);
	}
	
	
	
	protected void read(BasicHDU[] HDU, int firstDataHDU) throws Exception {
		
		int nDataHDUs = HDU.length - firstDataHDU, records = 0;
		
		if(hasOption("skiplast")) {
			System.err.println("   WARNING! Skipping last stream HDU...");
			nDataHDUs--; 
		}
	
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
			new MakoReader(hdu, startIndex).read();
			startIndex += hdu.getNRows();
		}
			
		//if(!isEmpty()) if(chopZero.weight() > 0.0) chopZero.scaleValue(1.0 / chopZero.weight());
		
		MakoScan sharcscan = (MakoScan) scan;
		
		if(sharcscan.addOffsets) for(MakoFrame frame : this) {
			// Remove the small zero offset from the chopper signal.
			frame.chopperPosition.subtractX(chopZero.value());	
			// Add chopper offset to the aggregated horizontal offset...
			frame.horizontalOffset.add(frame.chopperPosition);
			// Add the chopper offset to the absolute coordinates also...
			frame.horizontal.addOffset(frame.chopperPosition);
		}
	
		
	}
		
	class MakoReader extends HDUReader {	
		private int offset;

		private byte[] ch;
		private float[] data; //intTime, chop;
		private int[] SN, AZ, EL, dX, dY, AZE, ELE, LST, PA, MJD, ticks; // UTseconds, UTnanosec;
		private int channels;
		
		private final MakoScan sharcscan = (MakoScan) scan;
		
		public MakoReader(TableHDU hdu, int offset) throws FitsException {
			super(hdu);
			this.offset = offset;			

			int cData = hdu.findColumn("Shift");
			channels = table.getSizes()[cData];
			
			data = (float[]) table.getColumn(cData);
			
			SN = (int[]) table.getColumn(hdu.findColumn("Sequence Number"));
			//UTseconds = (int[]) table.getColumn(hdu.findColumn("Detector UTC seconds (2000/1/1)"));
			//UTnanosec = (int[]) table.getColumn(hdu.findColumn("Detector UTC nanoseconds"));
			//intTime = (float[]) table.getColumn(hdu.findColumn("Integration Time"));
			
			ch = (byte[]) table.getColumn(hdu.findColumn("Channel"));
			AZ = (int[]) table.getColumn(hdu.findColumn("Requested AZ"));
			EL = (int[]) table.getColumn(hdu.findColumn("Requested EL"));
			AZE = (int[]) table.getColumn(hdu.findColumn("Error In AZ"));
			ELE = (int[]) table.getColumn(hdu.findColumn("Error In EL"));
			PA = (int[]) table.getColumn(hdu.findColumn("Parallactic Angle"));
			LST = (int[]) table.getColumn(hdu.findColumn("LST"));
			dX = (int[]) table.getColumn(hdu.findColumn("X Offset"));
			dY = (int[]) table.getColumn(hdu.findColumn("Y Offset"));
			MJD = (int[]) table.getColumn(hdu.findColumn("Antenna MJD"));
			ticks = (int[]) table.getColumn(hdu.findColumn("N Ticks From Midnight"));
			//chop = (float[]) table.getColumn(hdu.findColumn("CHOP_OFFSET"));
			
		}
	
		@Override
		public Reader getReader() {
			return new Reader() {
				private Vector2D equatorialOffset;
				private boolean isEquatorial = EquatorialCoordinates.class.isAssignableFrom(((MakoScan) scan).scanSystem);
				//AstroTime time = new AstroTime();
				
				@Override
				public void init() { 
					super.init();
					equatorialOffset = new Vector2D();
				}
				@Override
				public void readRow(int i) throws FitsException {	
					if(ch[i] == 255) return;
					
					final MakoFrame frame = new MakoFrame(sharcscan);
					frame.index = i;
					
					frame.parseData(data, i*channels, instrument);

					//time.setMillis(AstroTime.millisJ2000 + 1000L * UTseconds[i] + (UTnanosec[i] / 1000000L));
					//frame.MJD = time.getMJD();	
					
					frame.MJD = MJD[i] + ticks[i] * antennaTick / Unit.day;
					
					// Enforce the calculation of the equatorial coordinates
					frame.equatorial = null;

					frame.horizontal = new HorizontalCoordinates(
							(AZ[i] + AZE[i]) * tenthArcsec,
							(EL[i] + ELE[i]) * tenthArcsec);
					
					final double pa = PA[i] * tenthArcsec;
					frame.sinPA = Math.sin(pa);
					frame.cosPA = Math.cos(pa);

					frame.LST = LST[i] * antennaTick;
			
					frame.frameNumber = SN[i];
					
					if(isEquatorial) {
						frame.horizontalOffset = new Vector2D(
							AZE[i] * frame.horizontal.cosLat() * tenthArcsec,
							ELE[i] * tenthArcsec);
						equatorialOffset.set(dX[i] * tenthArcsec, dY[i] * tenthArcsec);	
					}
					else {
						frame.horizontalOffset = new Vector2D(
							(dX[i] + AZE[i] * frame.horizontal.cosLat()) * tenthArcsec,
							(dY[i] + ELE[i]) * tenthArcsec);
						equatorialOffset.zero();
					}
						
					//frame.chopperPosition.setX(chop[i] * Unit.arcsec);
					//chopZero.add(frame.chopperPosition.getX());
					//chopZero.addWeight(1.0);

					// Add in the scanning offsets...
					if(sharcscan.addOffsets) frame.horizontalOffset.add(sharcscan.horizontalOffset);		
					
					frame.equatorialToHorizontal(equatorialOffset);
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
		return (1.0 - transmission) * ((MakoScan) scan).ambientT;
	}
	
	public double getDirectTau() { 
		double eps = (instrument.getLoadTemperature() - instrument.excessLoad) / ((MakoScan) scan).getAmbientTemperature(); 	
		return -Math.log(1.0-eps) * scan.horizontal.sinLat();
	}

	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}
	
	public final float antennaTick = (float) (0.01 * Unit.s);
	public final float tenthArcsec = (float) (0.1 * Unit.arcsec);
	public final float rightAngle = (float) (Util.rightAngle);
}
