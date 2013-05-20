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
// Copyright (c)2009,2010 Attila Kovacs

package crush.tools;

import java.io.*;
import java.util.*;

import crush.CRUSH;
import crush.Integration;
import crush.sourcemodel.GaussianSource;
import crush.sourcemodel.SkyMap;

import util.RadioProjection;
import util.SphericalProjection;
import util.Unit;

public class DataSimulator {
	
	static Hashtable<String, String> options;
	
	public static void main(String[] args) {
		String[] initargs = new String[args.length + 2];
		initargs[0] = args[0];
		initargs[1] = "-forget=aclip";
		initargs[2] = "-forget=vclip";
		System.arraycopy(args, 1, initargs, 3, args.length-1);
		
		CRUSH crush = CRUSH.init(initargs);
		options = crush.options;
		SkyMap reduced = new SkyMap();
		try { reduced.projection = SphericalProjection.forName(options.get("projection")); }
		catch(Exception e) { e.printStackTrace(); }
		
		crush.map = reduced;
			
		SphericalProjection projection = new RadioProjection();
		projection.setReference(crush.integration[0][0].equatorial);
		reduced.projection = projection;
		
		for(int i=0; i<crush.scans; i++) {
			for(int k=0; k<crush.integration[i].length; k++) {
				Integration scan = crush.integration[i][k];

				if(options.containsKey("jacknife")) {
					if(Math.random() < 0.5) scan.scale(-1.0);
				}
				
				scan.map = reduced;
				
				if(options.containsKey("sources")) {
					try { addSources(scan, options.get("sources")); }
					catch(IOException e) { System.err.println("ERROR! Reading sources file..."); }
				}
				
				if(!options.containsKey("orig")) {
					System.err.println("Clearing Scan Data.");
					scan.clearData();
					
					if(options.containsKey("uniform")) {
						System.err.println("Using uniform gains.");
						for(int c=0; c<scan.channels; c++) {
							scan.channel[c].gain = 1.0;
							scan.channel[c].coupling = 1.0;
						}
					}

					scan.map = reduced;

					if(!(options.containsKey("noiseless") || options.containsKey("orig"))) {
						System.err.println("Adding White Noise.");
						scan.randomData();
					}

					if(options.containsKey("constant")) {
						System.err.println("Adding Constant value to all channels.");
						double value = Double.parseDouble(options.get("constant"));
						scan.offset(value * scan.instrumentGain);
					}
					if(options.containsKey("decorrelate")) addCorrelated(scan);
					if(options.containsKey("cables")) addCables(scan);
				}
				
				
				scan.readoutStage();
				
				String ext = scan.isCompressed ? ".fits.gz" : ".fits";
				String fromName = scan.scanPath + File.separator + (scan.integrationNo+1) + File.separator + "LABOCA-ABBA-ARRAYDATA-1";
				String toName = fromName + ".fits" + (options.containsKey("overwrite") ? "" :".1");
				fromName += ext;
				
				try {
					scan.writeData(fromName, toName);
					System.err.println("Written simulated data to:");
					System.err.println("  " + toName);
				}
				catch(Exception e) { 
					System.err.println("Problem writing data to " + toName); 
					e.printStackTrace();
				}
			}
		}
		
	}
	
	
	public static void addSources(Integration scan, String fileName) throws IOException {
		Vector<GaussianSource> sources = new Vector<GaussianSource>();
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			try { sources.add(new GaussianSource(line)); }
			catch(IllegalArgumentException e) { e.printStackTrace(); }			
		}
		System.err.println("Adding " + sources.size() + " Gaussian source(s).");
		scan.addSources(sources);
	}
	
	public static void addCorrelated(Integration scan) {
		System.err.println("Adding Correlated Noise to " + scan.allChannels.length + " channels.");
		// Say ~25 Jy in 1 min;
		// ~5.0 uV in 1 min;
		double nyquistLevel = 5e-6 / Math.sqrt(Unit.min / scan.integrationTime);
		float[] gain = new float[scan.allChannels.length];
		for(int c=0; c<gain.length; c++) gain[c] = (float)scan.channel[c].gain;
		scan.addCorrelated(scan.allChannels, gain, nyquistLevel);
	}
	
	public static void addCables(Integration scan) {
		System.err.println("Adding Correlated Cables.");
		// Say 2.5 Jy in 1 min;
		// ~0.5 uV in 1 min;
		double nyquistLevel = 5e-7 / Math.sqrt(Unit.min / scan.integrationTime);
		addCorrelated(scan, scan.cableChannels, nyquistLevel);
	}
	
	public static void addCorrelated(Integration scan, int[][] channelSelection, double nyquistLevel) {
		for(int g=0; g<channelSelection.length; g++) {
			float[] gain = new float[channelSelection[g].length];
			Arrays.fill(gain, 1.0F);
			scan.addCorrelated(channelSelection[g], gain, nyquistLevel);			
		}
	}	
}
