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
package test;

import nom.tam.fits.*;
import nom.tam.util.*;

import java.io.*;
import java.util.*;

public class GismoVis {

	public static void main(String[] args) {
		String fileName = args[0];
		float[][] image = new float[16][8];
		for(int i=0; i<image.length; i++) Arrays.fill(image[i], Float.NaN);
		Vector<String> cols = new Vector<String>();
		
		
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
			String line = null;
			
			while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
				StringTokenizer tokens = new StringTokenizer(line);
				cols.clear();
				while(tokens.hasMoreTokens()) cols.add(tokens.nextToken());
		
				int channel = Integer.parseInt(cols.get(0)) - 1;
				int flag = Integer.decode(cols.get(3));
				//double value = 1.0/(Float.parseFloat(cols.get(1)) * Math.sqrt(Float.parseFloat(cols.get(2)))) / 8.26;				
				double value = Float.parseFloat(cols.get(1));
				
				if((flag & 0x203) == 0) image[channel/8][channel%8] = (float) value;	
			}
			
			in.close();
			
			Fits fits = new Fits();
			fits.addHDU(Fits.makeHDU(image));
			
			BufferedDataOutputStream file = new BufferedDataOutputStream(new FileOutputStream("gismovis.fits"));
			fits.write(file);
			fits.close();
		}
		catch(Exception e) { e.printStackTrace(); }
		
	}
	
}
