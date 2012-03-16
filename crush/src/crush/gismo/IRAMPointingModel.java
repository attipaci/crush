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
	double[] P = new double[1+CONSTANTS];
	double[] c = new double[1+CONSTANTS];
	double[] s = new double[1+CONSTANTS];
	
	boolean isStatic = false;
	
	public final static int CONSTANTS = 16;
	
	public IRAMPointingModel() {}
	
	public IRAMPointingModel(String fileName) throws IOException {
		this();
		read(fileName);
	}
	
	public Vector2D getCorrection(HorizontalCoordinates horizontal, double UT) {
		return new Vector2D(getDX(horizontal, UT) * Unit.arcsec, getDY(horizontal, UT) * Unit.arcsec);
	}
	
	public double P(int n, double UT) {
		double theta = UT * (2.0 * Math.PI / Unit.day);
		return P[n] + (isStatic ? 0.0 : c[n] * Math.cos(theta) + s[n] * Math.sin(theta));
	}
	
	public void subtract(IRAMPointingModel model) {
		for(int i=P.length; --i >= 0; ) {
			P[i] -= model.P[i];
			c[i] -= model.c[i];
			s[i] -= model.s[i];			
		}
	}
	
	public void setStatic(boolean value) {
		isStatic = value;
	}
	
	public void addNasmythOffset(Vector2D offset) {
		P[10] += offset.x;
		P[11] += offset.y;
	}
	
	public double getDX(HorizontalCoordinates horizontal, double UT) {
		double cosE = horizontal.cosLat;
		double sinE = horizontal.sinLat;
		double sinA = Math.sin(horizontal.x);
		double cosA = Math.cos(horizontal.x);
		double sin2A = Math.sin(2.0 * horizontal.x);
		double cos2A = Math.cos(2.0 * horizontal.x);
		
		double H = P(10, UT);
		double V = P(11, UT);
		
		// 2012-03-06
		// Nasmyth offsets H & V are now equivalent to pointing model (P10/P11).
		// Note, that Juan's fit gives V = -P11!
		return P(1, UT) * cosE + P(2, UT) + P(3, UT) * sinE 
			+ (P(4, UT) * cosA + P(5, UT) * sinA) * sinE + P(6, UT) * sinA
			- H * cosE - V * sinE 
			+ P(12, UT) * sin2A + P(13, UT) * cos2A;
	}
	
	public double getDY(HorizontalCoordinates horizontal, double UT) {
		double cosE = horizontal.cosLat;
		double sinE = horizontal.sinLat;
		double sinA = Math.sin(horizontal.x);
		double cosA = Math.cos(horizontal.x);
		double sin2A = Math.sin(2.0 * horizontal.x);
		double cos2A = Math.cos(2.0 * horizontal.x);
		
		double H = P(10, UT);
		double V = P(11, UT);
		
		// 2012-03-06
		// Nasmyth offsets H & V are now equivalent to pointing model (P10/P11).
		// Note, that Juan's fit gives V = -P11!
		return -P(4, UT) * sinA + (P(5, UT) + P(6, UT) * sinE) * cosA + P(7, UT) 
			+ P(8, UT) * cosE + P(9, UT) * sinE + H * sinE - V * cosE 
			+ P(14, UT) * cosE/sinE + P(15, UT) * sin2A + P(16, UT) * cos2A;
	}
	
	public void write(String fileName) throws IOException {
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		for(int i=1; i<P.length; i++) 
			out.println("P" + i + " = " + Util.f2.format(P[i]) + ", " + Util.f2.format(c[i]) + ", " + Util.f2.format(s[i])); 
		out.close();
	}
	
	public void read(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(Util.getSystemPath(fileName))));
		String line;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line, " \t\r=:, ");
			if(tokens.hasMoreTokens()) {
				String constant = tokens.nextToken().toLowerCase();
				if(constant.startsWith("p")) {
					int i = Integer.parseInt(constant.substring(1));
					if(tokens.hasMoreTokens()) P[i] = Double.parseDouble(tokens.nextToken());
					if(tokens.hasMoreTokens()) c[i] = Double.parseDouble(tokens.nextToken());
					if(tokens.hasMoreTokens()) s[i] = Double.parseDouble(tokens.nextToken());
				}
			}
		}
		
	}
	
}
