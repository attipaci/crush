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
package test;

import nom.tam.fits.*;
import nom.tam.util.*;
import java.io.*;
import java.util.Locale;

public class FitsDeferredTest {
	int serialNo;
	float[][][] images = new float[4][][];
	BasicHDU[] hdus;
	
	static { Locale.setDefault(Locale.GERMANY); }
	
	public static void main(String[] args) throws Exception {
		FitsDeferredTest test = new FitsDeferredTest();
		test.read(args[0]);
		System.gc();
		test.write("test.fits");
	}
	
	public void read(String fileName) throws Exception {
		Fits f = new Fits(fileName);
		hdus = f.read();
		for(int i=0; i<4; i++) images[i] = (float[][]) hdus[i].getData().getData();
		
		Header header = hdus[4].getHeader();
		serialNo = header.getIntValue("SCANNO");
	}
	
	public void write(String fileName) throws Exception {
		Fits g = new Fits();
		for (int i=0; i<4; i += 1) {
			ImageHDU nw = (ImageHDU) Fits.makeHDU(images[i]);
			g.addHDU(nw);
		}

		BasicHDU nw = Fits.makeHDU(hdus[4].getData());
		nw.addValue("SCANNO", serialNo, "");
		g.addHDU(nw);
		
		//BufferedFile bf = new BufferedFile(fileName, "rw");
		BufferedDataOutputStream bf = new BufferedDataOutputStream(new FileOutputStream(fileName));
		
		System.err.println("PI = " + Math.PI);
		
		g.write(bf);		
	}
	
} 
