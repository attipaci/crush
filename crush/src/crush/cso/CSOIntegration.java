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
import crush.mako.MakoScan;

public abstract class CSOIntegration<InstrumentType extends CSOArray<?>, FrameType extends HorizontalFrame> 
extends Integration<InstrumentType, FrameType> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8762250193431287809L;

	public CSOIntegration(Scan<InstrumentType, ?> parent) {
		super(parent);

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
			
			CSOTauTable table = CSOTauTable.get(((CSOScan<?,?>) scan).iMJD, file.getPath());
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
		System.err.println("   Optical load is " + Util.f1.format(((CSOScan<?,?>) scan).ambientT * (1.0 - Math.exp(-tauLOS))) + " K.");
	
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
		tauServer.setReuseAddress(true);
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

	@Override
	public String getASCIIHeader() {
		double eps = 1.0 - Math.exp(-zenithTau / scan.horizontal.sinLat());
		double Tload = ((MakoScan) scan).getAmbientTemperature();
		
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
