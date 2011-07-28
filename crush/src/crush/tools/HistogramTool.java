/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.tools;

import crush.*;
import crush.sourcemodel.*;
import util.*;

import java.io.*;
import java.util.*;

// TODO reinstate imagetool functionality...
// TODO redo using Configurator engine

public class HistogramTool {
	static String version = "0.1-1";
	
	double binres = 1.0;
	AstroImage image;
	String type = "s2n";
	String fileName;
	
	public static void main(String[] args) {
		versionInfo();
		if(args.length == 0) usage();

		AstroMap map = new AstroMap();
		HistogramTool histogramTool = new HistogramTool(); 
		
		try { 
			map.read(args[args.length-1]); 
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		//ImageTool.image = map;
		
		for(int k=0; k < args.length-1; k++) histogramTool.option(args[k]);
				
		histogramTool.selectImage(map, histogramTool.type);
		histogramTool.binres *= histogramTool.image.unit.value;
		
		try { histogramTool.writeHistogram(); }
		catch(IOException e) {
			System.err.println("ERROR! " + e.getMessage());
		}
	}
	
	public void selectImage(AstroMap map, String type) {
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

		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.flag[i][j] == 0) {
			int bin = zeroBin + (int) Math.round(image.data[i][j] / binres);		
			count[bin]++;
		}

		if(fileName == null) fileName = CRUSH.workPath + "histogram-" + type + "." + image.sourceName + ".dat"; 

		PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(fileName)));

		out.println("# histogram for " + image.fileName);
		out.println("# bin resolution is " + (binres / image.unit.value) + " " + image.unit.name);
		out.println("# bin\t" + type + "\tcount");
		out.println("# ----\t-----\t------");

		for(int i=0; i<bins; i++) out.println((i+1) + "\t" + Util.e5.format((i-zeroBin) * (binres / image.unit.value)) + "\t" + count[i]);

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
		System.out.println("             Copyright (C)2011 Attila Kovacs <kovacs[AT]astro.umn.edu>");
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
