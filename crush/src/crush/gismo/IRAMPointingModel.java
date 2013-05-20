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
	
	double dydT = 0.0, y0 = 0.0;
	
	
	boolean isStatic = false;
	
	
	public IRAMPointingModel() {}
	
	public IRAMPointingModel(String fileName) throws IOException {
		this();
		read(fileName);
	}
	
	public Vector2D getCorrection(HorizontalCoordinates horizontal, double UT, double Tamb) {
		return new Vector2D(getDX(horizontal, UT) * Unit.arcsec, getDY(horizontal, UT, Tamb) * Unit.arcsec);
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
		P[10] += offset.getX();
		P[11] += offset.getY();
	}
	
	public double getDX(HorizontalCoordinates horizontal, double UT) {
		double cosE = horizontal.cosLat();
		double sinE = horizontal.sinLat();
		double sinA = Math.sin(horizontal.getX());
		double cosA = Math.cos(horizontal.getX());
		double sin2A = Math.sin(2.0 * horizontal.getX());
		double cos2A = Math.cos(2.0 * horizontal.getX());
	
		
		// 2012-03-06
		// Nasmyth offsets H & V are now equivalent to pointing model (P10/P11).
		// Note, that Juan's fit gives V = -P11!
		return P(AZ_ENC_OFFSET, UT) * cosE + P(AZ_POINTING, UT) + P(INCL_EL_AXIS, UT) * sinE 
			+ (P(INCL_AZ_AXIS_NS, UT) * cosA + P(INCL_AZ_AXIS_EW, UT) * sinA) * sinE + P(DEC_ERROR, UT) * sinA
			- P(NASMYTH_H, UT) * cosE - P(NASMYTH_V, UT) * sinE 
			+ P(AZ_ECCENT_COS, UT) * sin2A + P(AZ_ECCENT_SIN, UT) * cos2A;
	}
	
	public double getDY(HorizontalCoordinates horizontal, double UT, double Tamb) {
		double cosE = horizontal.cosLat();
		double sinE = horizontal.sinLat();
		double sinA = Math.sin(horizontal.getX());
		double cosA = Math.cos(horizontal.getX());
		
		// 2012-03-06
		// Nasmyth offsets H & V are now equivalent to pointing model (P10/P11).
		// Note, that Juan's fit gives V = -P11!
		return -P(INCL_AZ_AXIS_NS, UT) * sinA + (P(INCL_AZ_AXIS_EW, UT) + P(DEC_ERROR, UT) * sinE) * cosA 
			+ P(EL_POINTING, UT) + P(GRAV_BENDING_COS, UT) * cosE + P(GRAV_BENDING_SIN, UT) * sinE 
			+ P(NASMYTH_H, UT) * sinE - P(NASMYTH_V, UT) * cosE 
			+ P(THIRD_REFRACT, UT) * cosE/sinE
			+ dydT * (Tamb - 273.16 * Unit.K);
	}
	
	public void write(String fileName) throws IOException {
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		for(int i=1; i<P.length; i++) 
			out.println("P" + i + " = " + Util.f2.format(P[i]) + ", " + Util.f2.format(c[i]) + ", " + Util.f2.format(s[i])); 
		out.println("T = " + Util.f2.format(dydT));
		out.close();
	}
	
	public void read(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
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
				else if(constant.equals("t")) dydT = Double.parseDouble(tokens.nextToken());
			}
		}
		
		in.close();
	}
	
	public final static int CONSTANTS = 14;
	
	public final static int AZ_ENC_OFFSET = 1;
	public final static int AZ_POINTING = 2;
	public final static int INCL_EL_AXIS = 3;
	public final static int INCL_AZ_AXIS_NS = 4;
	public final static int INCL_AZ_AXIS_EW = 5;
	public final static int DEC_ERROR = 6;
	public final static int EL_POINTING = 7;
	public final static int GRAV_BENDING_COS = 8;
	public final static int GRAV_BENDING_SIN = 9;
	public final static int NASMYTH_H = 10;
	public final static int NASMYTH_V = 11;
	public final static int AZ_ECCENT_COS = 12;
	public final static int AZ_ECCENT_SIN = 13;
	public final static int THIRD_REFRACT = 14;
	
}
