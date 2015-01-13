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
package crush.cso;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import kovacs.util.Unit;
import kovacs.util.Util;
import crush.GroundBased;
import crush.HorizontalFrame;
import crush.Integration;
import crush.Scan;


public abstract class CSOIntegration<InstrumentType extends CSOArray<?>, FrameType extends HorizontalFrame> 
extends Integration<InstrumentType, FrameType> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8762250193431287809L;

	public CSOIntegration(Scan<InstrumentType, ?> parent) {
		super(parent);

	}
	
	@Override
	public void validate() {	
		if(!hasOption("nochopper")) {
			removeChopperDCOffset();

			for(FrameType frame : this) if(frame != null) {
				// Add chopper offset to the aggregated horizontal offset...
				frame.horizontalOffset.add(frame.chopperPosition);
				// Add the chopper offset to the absolute coordinates also...
				frame.horizontal.addOffset(frame.chopperPosition);
			}
		}
			
		super.validate();
	}
	
	private void removeChopperDCOffset() {
		double threshold = instrument.getMinBeamFWHM() / 2.5;
		double sumP = 0.0, sumM = 0.0;
		int nP = 0, nM = 0;
		
		System.err.println("   Removing chopper signal DC offset.");
		
		for(FrameType frame : this) if(frame != null) {
			sumP += frame.chopperPosition.x();
			nP++;			
		}
		if(nP == 0) return;
		
		final double mean = sumP / nP;
		sumP = 0.0;
		nP = 0;
		
		System.err.print("   --> mean: " + Util.f1.format(mean / Unit.arcsec) + "\", ");
		
		for(FrameType frame : this) if(frame != null) {
			frame.chopperPosition.subtractX(mean);
			final double dx = frame.chopperPosition.x();
			
			if(dx > threshold) {
				sumP += dx;
				nP++;	
			}
			else if(dx < -threshold) {
				sumM += dx;
				nM++;				
			}
		}
		
		if(nP == 0 || nM == 0) {
			System.err.println("not chopped.");
			instrument.forget("detect.chopped");
			return;
		}
		
		final double level = 0.5 * (sumP / nP + sumM / nM);
		
		System.err.println(" res: " + Util.f1.format(level / Unit.arcsec) + "\".");
		

		for(FrameType frame : this) if(frame != null) frame.chopperPosition.subtractX(level);
		
		return;
	}
	
	
	public void printEquivalentTaus(double value) {	
		System.err.println("   --->"
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz", value))
				+ ", tau(350um):" + Util.f3.format(getTau("350um", value))
				+ ", tau(LOS):" + Util.f3.format(value / scan.horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv", value)) + "mm"
		);		
	}
	
	
	public double getSkyLoadTemperature() {
		double transmission = 0.5 * (getFirstFrame().getTransmission() + getLastFrame().getTransmission());
		return (1.0 - transmission) * ((CSOScan<?,?>) scan).getAmbientTemperature();
	}
	
	
	@Override
	public void setTau() throws Exception {
		String source = option("tau").getValue().toLowerCase();
		
		if(source.equals("tables")) setTableTau();
		else if(source.equals("direct")) setZenithTau(getDirectTau());
		else if(source.equals("maitau") && hasOption("maitau.server")) setMaiTau();	
		else if(source.equals("jctables") && hasOption("tau.jctables")) setJCMTTableTau();
		else super.setTau();
		
		printEquivalentTaus(zenithTau);
		
		double tauLOS = zenithTau / scan.horizontal.sinLat();
		System.err.println("   Optical load is " + Util.f1.format(((CSOScan<?,?>) scan).ambientT * (1.0 - Math.exp(-tauLOS))) + " K.");
	
	}
	
	public void setMaiTau() throws Exception {
		System.err.println("   Requesing MaiTau via " + option("maitau.server") + "...");
		
		try {
			try { setTau("350um", getMaiTau("350um")); }
			catch(NumberFormatException no350) { setTau("225GHz", getMaiTau("225GHz")); }
		}	
		catch(Exception e) { fallbackTau("maitau", e); }	
	}
	
	public void setTableTau() throws Exception {
		String source = hasOption("tau.tables") ? option("tau.tables").getValue() : ".";
		String date = scan.getID().substring(0, scan.getID().indexOf('.'));
		String spec = date.substring(2, 4) + date.substring(5, 7) + date.substring(8, 10);
		
		File file = new File(Util.getSystemPath(source) + File.separator + spec + ".dat");
		if(!file.exists()) {
			System.err.print("   WARNING! No tau table found for " + date + "...");
			System.err.print("            Using default tau.");
			instrument.getOptions().remove("tau");
			setTau();
			return;
		}
		
		CSOTauTable table = CSOTauTable.get(((CSOScan<?,?>) scan).iMJD, file.getPath());
		table.setOptions(option("tau"));
		setTau("225GHz", table.getTau(getMJD()));	
	}
	
	public void setJCMTTableTau() throws Exception {
		String source = hasOption("tau.jctables") ? option("tau.jctables").getValue() : ".";
		String date = scan.getID().substring(0, scan.getID().indexOf('.'));
		String spec = date.substring(0, 10);
		String fileName = Util.getSystemPath(source) + File.separator + spec + ".jcmt-183-ghz.dat";
		
		try {
			JCMTTauTable table = JCMTTauTable.get(((CSOScan<?,?>) scan).iMJD, fileName);
			table.setOptions(option("tau"));
			setTau("225GHz", table.getTau(getMJD()));	
		}
		catch(IOException e) { fallbackTau("jctables", e); }
	}
	
	private void fallbackTau(String from, Exception e) throws Exception {
		if(hasOption("maitau.fallback")) {
			System.err.println("   WARNING! Tau lookup failed: " + e.getMessage());
			String source = option("maitau.fallback").getValue().toLowerCase();
			if(source.equals(from)) {
				System.err.println("   WARNING! Deadlocked fallback option!");
				throw e;
			}	
			System.err.println("   ... Falling back to '" + source + "'.");
			instrument.setOption("tau=" + source);
			setTau();
			return;
		}
		else throw e;	
		
	}
	
	public double getMaiTau(String id) throws IOException {
		// Return immediately if ID does not match 225GHz or 350um, which are the only values in
		// the Mai-Tau lookup at present
		final int timeout = 3000;
		
		
		if(!id.equalsIgnoreCase("225GHz") && !id.equalsIgnoreCase("350um")) 
			throw new IllegalArgumentException("No MaiTau lookup for '" + id + "'.");
		
		if(!hasOption("maitau.server")) 
			throw new IllegalArgumentException(" WARNING! MaiTau server not set. Use 'maitau.server' configuration key.");
		
		Socket tauServer = new Socket();
		tauServer.setSoTimeout(timeout);
		tauServer.setTcpNoDelay(true);
		tauServer.setReuseAddress(true);
		//tauServer.setPerformancePreferences(0, 1, 2); // connection time, latency, throughput
		tauServer.setTrafficClass(0x10); // low latency
		tauServer.connect(new InetSocketAddress(option("maitau.server").getValue(), 63225), timeout);
		
		
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
			if(!Double.isNaN(value)) System.err.println("   ---> MaiTau(" + id + ") = " + Util.f3.format(value));
		}
		catch(NumberFormatException e) {}
		
		out.println("exit"); // Disconnect from Mai-Tau server 
		in.close();
		out.close();
		
		tauServer.close();
		
		if(Double.isNaN(value)) throw new NumberFormatException("No " + id + " value for date in MaiTau database.");
		
		return value;
	}

	@Override
	public String getASCIIHeader() {
		double eps = 1.0 - Math.exp(-zenithTau / scan.horizontal.sinLat());
		double Tload = ((CSOScan<?, ?>) scan).getAmbientTemperature();
		
		return super.getASCIIHeader() + "\n" 
				+ "# tau(225GHz) = " + Util.f3.format(this.getTau("225ghz")) + "\n"
				+ "# T_amb = " + Util.f1.format(((CSOScan<?,?>) scan).getAmbientTemperature()/Unit.H - 273.16) + " C\n" 
				+ "# T_load = " + Util.f1.format(Tload * eps / Unit.K) + " K";
	}
	 

	public double getDirectTau() { 
		double eps = (instrument.getLoadTemperature() - instrument.excessLoad) / ((CSOScan<?,?>) scan).ambientT; 	
		return -Math.log(1.0-eps) * scan.horizontal.sinLat();
	}
	
	public final float antennaTick = (float) (0.01 * Unit.s);
	public final float tenthArcsec = (float) (0.1 * Unit.arcsec);

}
