/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.devel;

import crush.*;
import jnum.Util;
import jnum.data.image.Data2D;
import jnum.math.SphericalCoordinates;
import jnum.util.*;

import java.io.*;
import java.util.*;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;

// TODO reinstate imagetool functionality...
// TODO redo using Configurator engine

public class HistogramTool {
	static String version = "0.1-1";
	
	double binres = 1.0;
	Data2D image;
	String type = "s2n";
	String fileName;
	
	public static void main(String[] args) {
		versionInfo();
		if(args.length == 0) usage();

		HistogramTool histogramTool = new HistogramTool(); 
		
		try { 
			Fits fits = new Fits(new File(args[args.length-1])); 
		
			int N = fits.getNumberOfHDUs();
			
			for(int k=0; k < args.length-1; k++) histogramTool.option(args[k]);
			
			if(N > 4) {
				try { histogramTool.readMap(fits); }
				catch(Exception e) {
					try { histogramTool.readImage(fits); }
					catch(Exception e2) {// Perhaps it's not a CRUSH map after all...
						e.printStackTrace();
						System.exit(1);
					}
				}
			}
			else { 
				try { histogramTool.readImage(fits); }
				catch(Exception e) {
					// Perhaps it's not a CRUSH map after all...
					e.printStackTrace();
					System.exit(1);
				}
			}
			
		}
		catch(Exception e) {
			System.err.println("ERROR: " + e.getMessage());
			System.exit(1);
		}
		
		
		//ImageTool.image = map;			
		
		
		try { histogramTool.writeHistogram(); }
		catch(IOException e) {
			System.err.println("ERROR! " + e.getMessage());
		}
	}
	
	public void readImage(Fits fits) throws Exception {
		image = new Data2D();
		
		int N = fits.getNumberOfHDUs();
		if(N == 1) {
			image.read(fits);
			return;
		}
		
		try {
			int n = Integer.parseInt(type);
			image.read(fits, n);
		}
		catch(NumberFormatException e) {
			for(BasicHDU hdu : fits.read()) {
				String extName = hdu.getHeader().getStringValue("EXTNAME");
				if(extName != null) if(extName.equalsIgnoreCase(type)) {
					image.read(hdu);
					return;
				}
			}
		}
	}
	
	public void readMap(Fits fits) throws Exception {
		GridMap2D<?> map = new GridMap2D<SphericalCoordinates>(); 
		map.read(fits);
		selectImage(map, type);
		binres *= image.getUnit().value();		
	}
	
	public void selectImage(GridMap2D<?> map, String type) {
		if(type.equalsIgnoreCase("flux")) {}
		else if(type.equalsIgnoreCase("s2n")) { image = map.getS2NImage(); }
		else if(type.equalsIgnoreCase("rms")) { image = map.getRMSImage(); }
		else if(type.equalsIgnoreCase("weight")) { image = map.getWeightImage(); }
		else if(type.equalsIgnoreCase("time")) { image = map.getTimeImage(); }
		else throw new IllegalArgumentException("Image plane '" + type + "' is not recognised.");
		
		this.type = type;
	}
	
	public void writeHistogram() throws IOException {		
		int zeroBin = (int) Math.ceil(-image.getMin() / binres);
		int bins = 1 + zeroBin + (int) Math.ceil(image.getMax() / binres);

		int[] count = new int[bins];

		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j)) {
			int bin = zeroBin + (int) Math.round(image.get(i, j) / binres);		
			count[bin]++;
		}

		if(fileName == null) fileName = CRUSH.workPath + "histogram-" + type + "." + image.getName() + ".dat"; 

		PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(fileName)));

		out.println("# histogram for " + image.fileName);
		out.println("# bin resolution is " + (binres / image.getUnit().value()) + " " + image.getUnit().name());
		out.println("# bin\t" + type + "\tcount");
		out.println("# ----\t-----\t------");

		for(int i=0; i<bins; i++) out.println((i+1) + "\t" + Util.e5.format((i-zeroBin) * (binres / image.getUnit().value())) + "\t" + count[i]);

		out.close();

		System.out.println("Written histogram to " + fileName);
	}

	public boolean option(String optionString) {
		StringTokenizer tokens = new StringTokenizer(optionString, "=");

		String key = tokens.nextToken();

		if(key.equalsIgnoreCase("-bin")) binres = Double.parseDouble(tokens.nextToken());
		else if(key.equalsIgnoreCase("-image")) type = tokens.nextToken(); 
		else if(key.equalsIgnoreCase("-out")) fileName = Util.getSystemPath(tokens.nextToken());
		else if(key.equalsIgnoreCase("-help")) usage();
		//else return ImageTool.option(optionString);
	
		return true;    
	}

	public static void versionInfo() {
		System.out.println("histogram -- Histogram Generation Utility for CRUSH maps and more.");
		System.out.println("             Version: " + version);
		System.out.println("             part of crush " + CRUSH.getFullVersion());
		System.out.println("             http://www.submm.caltech.edu/~sharc/crush");
		System.out.println("             Copyright (C)2011 Attila Kovacs <attila[AT]sigmyne.com>");
		System.out.println();
	}

	public static void usage() {
		System.out.println("Usage: histogram [options] <filename>");
		System.out.println();
		System.out.println("Options:");
		System.out.println();
		System.out.println("  All options of 'imagetool' are available. Additionally, 'histogram' defines");
		System.out.println("  the following options:");
		System.out.println();
		System.out.println("    -bin=      Set the bin size in the units of the image plane.");
		System.out.println("    -image=    Select which image to use for the histogram");
		System.out.println("               (image name is one of: flux,s2n,rms,weight,time)");
		System.out.println("    -out=      Set the output file's name.");	
		System.out.println("    -help      Provides this help screen.");
		System.out.println();

		System.exit(0);
	}
}
