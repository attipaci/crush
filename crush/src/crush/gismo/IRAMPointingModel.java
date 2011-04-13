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

package crush.gismo;

import util.Unit;
import util.Util;
import util.Vector2D;
import util.astro.HorizontalCoordinates;
import java.io.*;
import java.util.StringTokenizer;

public class IRAMPointingModel {
	double[] P = new double[12];
	
	public IRAMPointingModel() {}
	
	public IRAMPointingModel(String fileName) throws IOException {
		this();
		read(fileName);
	}
	
	public Vector2D getCorrection(HorizontalCoordinates horizontal) {
		return new Vector2D(getDX(horizontal) * Unit.arcsec, getDY(horizontal) * Unit.arcsec);
	}
	
	public double getDX(HorizontalCoordinates horizontal) {
		double cosE = horizontal.cosLat;
		double sinE = horizontal.sinLat;
		double sinA = Math.sin(horizontal.x);
		double cosA = Math.cos(horizontal.x);
		double H = P[10];
		double V = P[11];
		
		return P[1] * cosE + P[2] + P[3] * sinE + (P[4] * cosA + P[5] * sinA) * sinE + P[6] * sinA
			+ H * cosE + V * sinE;
	}
	
	public double getDY(HorizontalCoordinates horizontal) {
		double cosE = horizontal.cosLat;
		double sinE = horizontal.sinLat;
		double sinA = Math.sin(horizontal.x);
		double cosA = Math.cos(horizontal.x);
		double H = P[10];
		double V = P[11];
		
		return -P[4] * sinA + P[5] * cosA + P[7] + P[8] * cosE + P[9] * sinE - H * sinE + V * cosE;
	}
	
	public void write(String fileName) throws IOException {
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		for(int i=1; i<P.length; i++) out.println("P" + i + " = " + Util.f2.format(P[i])); 
		out.close();
	}
	
	public void read(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line, " \t\r=:");
			if(tokens.hasMoreTokens()) {
				String constant = tokens.nextToken().toLowerCase();
				if(constant.startsWith("p")) {
					int i = Integer.parseInt(constant.substring(1));
					if(tokens.hasMoreTokens()) P[i] = Double.parseDouble(tokens.nextToken());
				}
			}
		}
		
	}
}
