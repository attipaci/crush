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
import crush.fits.HDUManager;
import crush.fits.HDUReader;
import nom.tam.fits.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;

import util.*;
import util.astro.*;
import util.data.FFT;

// TODO Split nod-phases into integrations...
public class Sharc2Integration extends Integration<Sharc2, Sharc2Frame> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2387643745464766162L;
	
	public boolean hasExtraTimingInfo = false;
	private double chopCenter = 0.0;
	
	public Sharc2Integration(Sharc2Scan parent) {
		super(parent);
	}

	@Override
	public void validate() {	
		reindex();
		
		// Incorporate the relative instrument gain (under loading) in the scan gain...
		gain *= instrument.averagePixelGain;
		
		// Add the residual offsets to the DAC values...
		// Must do this before tau estimates...
		removeOffsets(true, true);
			
		// Tau is set here...
		super.validate();	
		
		boolean directTau = false;
		if(hasOption("tau")) directTau = option("tau").equals("direct"); 
		
		if(!directTau) {		
			double measuredLoad = instrument.getLoadTemperature(); 
			double eps = (measuredLoad - instrument.excessLoad) / ((Sharc2Scan) scan).ambientT;
			double tauLOS = -Math.log(1.0-eps);
			System.err.println("   Tau from bolometers (not used):");
			printEquivalentTaus(tauLOS * scan.horizontal.sinLat);
			
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
				+ ", tau(LOS):" + Util.f3.format(value / scan.horizontal.sinLat)
				+ ", PWV:" + Util.f2.format(getTau("pwv", value)) + "mm"
		);		
	}
	
	// TODO ???
	@Override
	public void removeDrifts(ChannelGroup<?> channels, int targetFrameResolution, boolean robust, boolean quick) {
		super.removeDrifts(channels, targetFrameResolution, robust, quick);
		
		// Recalculate the scan gains...
		/*
		if(hasOption("nonlinearity")) {
			comments += "G0";
			gain /= instrument.averagePixelGain;
			instrument.calcAveragePixelGain();
			gain *= instrument.averagePixelGain;
		}
		*/
		
	}
	
	public void filter13Hz(ChannelGroup<Sharc2Pixel> channels) {
		System.err.println("   Filtering 13.4 Hz resonances.");
		
		double f = 13.38 * Unit.Hz;
		double nyquist = 0.5 / instrument.samplingInterval;
		
		// Make it s.t. notch filter has ~0.3 Hz width...
		if(nyquist < f) throw new IllegalStateException("   WARNING! Data no longer contains unaliased 13.38 Hz signals.");
		
		int n = FFT.getTruncatedSize(2 * (int)Math.ceil(nyquist / (0.3 * Unit.Hz)));
		
		double[] response = new double[n];
		Arrays.fill(response, 1.0);
		
		int F = (int)Math.floor(f / nyquist * response.length);
		response[F] = 0.0;
		response[F+1] = 0.0;
		
		filter(channels, response);
		
		comments += "n";
	}
	
	@Override
	public void setTau() throws Exception {
		String source = option("tau").getValue().toLowerCase();
		
		if(source.equals("direct")) setZenithTau(getDirectTau());
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
		double tauLOS = zenithTau / scan.horizontal.sinLat;
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
	
	
	
	protected void read(BasicHDU[] HDU, int firstDataHDU) throws IllegalStateException, HeaderCardException, FitsException {
		
		int nDataHDUs = HDU.length - firstDataHDU, records = 0;
		for(int datahdu=0; datahdu<nDataHDUs; datahdu++) records += HDU[firstDataHDU + datahdu].getAxes()[0];

		System.err.println(" Processing scan data:");
		
		System.err.println("   " + nDataHDUs + " HDUs,  " + records + " x " +
				(int)(instrument.integrationTime/Unit.ms) + "ms frames" + " -> " + 
				Util.f1.format(records*instrument.integrationTime/Unit.min) + " minutes total."); 
	
			
		clear();
		ensureCapacity(records);
		for(int t=records; --t>=0; ) add(null);
		
		HDUManager manager = new HDUManager(this);
				
		for(int n=0, startIndex = 0; n<nDataHDUs; n++) {
			BinaryTableHDU hdu = (BinaryTableHDU) HDU[firstDataHDU+n]; 
			manager.read(new Sharc2Reader(hdu, startIndex));
			startIndex += hdu.getNRows();
		}
		
		if(!isEmpty()) chopCenter /= size();

		// Add to the offsets the centered chopper signal.
		for(Sharc2Frame frame : this) {
			frame.chopperPosition.x -= chopCenter;
			
			// The chopper position is uncentered so better leave it out unless needed...
			frame.horizontalOffset.x += frame.chopperPosition.x;

			// Add the chopper offet to the actual coordinates as well...
			frame.horizontal.x += frame.chopperPosition.x / frame.horizontal.cosLat;
		}
		
	}
	
	class Sharc2Reader extends HDUReader {	
		private boolean hasExtraTimingInfo, isDoubleUT;
		private int offset;

		private float[] data, fUT, AZ, EL, AZO, ELO, AZE, ELE, RAO, DECO, LST, PA, chop;
		private double[] dUT, DT;
		private int[] SN;
		private int channels;
		
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
			RAO = (float[]) table.getColumn(hdu.findColumn("RAO"));
			DECO = (float[]) table.getColumn(hdu.findColumn("DECO"));
			AZO = (float[]) table.getColumn(hdu.findColumn("AZO"));
			ELO = (float[]) table.getColumn(hdu.findColumn("ELO"));
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
		public void read() throws FitsException {	
			final Sharc2Scan sharcscan = (Sharc2Scan) scan;
			final Vector2D equatorialOffset = new Vector2D();
		
			for(int i=from; i<to; i++) {
				if(isInterrupted()) break;
				
				final Sharc2Frame frame = new Sharc2Frame(sharcscan);
				frame.parseData(data, i*channels, channels);

				final double UT = isDoubleUT ? dUT[i] * Unit.hour : fUT[i] * Unit.hour;
				frame.MJD = sharcscan.iMJD + UT / Unit.day;

				// Enforce the calculcation of the equatorial coordinates
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
						(AZO[i] + AZE[i] * frame.horizontal.cosLat) * Unit.arcsec,
						(ELO[i] + ELE[i]) * Unit.arcsec);

				frame.chopperPosition.x = chop[i] * Unit.arcsec;
				chopCenter += frame.chopperPosition.x;	

				// Add in the scanning offsets...
				frame.horizontalOffset.add(sharcscan.horizontalOffset);

				// Add in the equatorial sweeping offsets
				// Watch out for the sign of the RA offset, which is counter to the native coordinate direction
				equatorialOffset.setNative(RAO[i] * Unit.arcsec, DECO[i] * Unit.arcsec);			
				frame.toHorizontal(equatorialOffset);
				frame.horizontalOffset.add(equatorialOffset);
				
				set(offset + i, frame);
			}
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
		
		if(Double.isNaN(value)) throw new NumberFormatException("No " + id + " value for date in MaiTau database.");
		
		return value;
	}

	
	// Reminder: eps_obs = eps * areaFactor
	public double getSkyLoadTemperature() {
		double transmission = 0.5 * (getFirstFrame().transmission + getLastFrame().transmission);
		return (1.0 - transmission) * ((Sharc2Scan) scan).ambientT;
	}


	public double getDirectTau() { 
		double eps = (instrument.getLoadTemperature() - instrument.excessLoad) / ((Sharc2Scan) scan).ambientT; 	
		return -Math.log(1.0-eps) * scan.horizontal.sinLat;
	}

	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}
}
