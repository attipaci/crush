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
package crush.telescope.iram;

import java.io.*;

import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.astro.HorizontalCoordinates;
import jnum.io.LineParser;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;

public class IRAMPointingModel {
	public double[] P = new double[1+CONSTANTS];
	public double[] c = new double[1+CONSTANTS];
	public double[] s = new double[1+CONSTANTS];
	
	public double dydT = 0.0, dxdT = 0.0, y0 = 0.0;
	
	
	public boolean isStatic = false;
	
	
	public IRAMPointingModel() {}
	
	public IRAMPointingModel(String fileName) throws IOException {
		this();
		read(fileName);
	}
	
	public Vector2D getCorrection(HorizontalCoordinates horizontal, double UT, double Tamb) {
		return new Vector2D(getDX(horizontal, UT, Tamb) * Unit.arcsec, getDY(horizontal, UT, Tamb) * Unit.arcsec);
	}
	
	public double P(int n, double UT) {
		double theta = UT * (Constant.twoPi / Unit.day);
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
		P[10] += offset.x();
		P[11] += offset.y();
	}
	
	public double getDX(HorizontalCoordinates horizontal, double UT, double Tamb) {
		double cosE = horizontal.cosLat();
		double sinE = horizontal.sinLat();
		double sinA = Math.sin(horizontal.AZ());
		double cosA = Math.cos(horizontal.AZ());
		double sin2A = Math.sin(2.0 * horizontal.AZ());
		double cos2A = Math.cos(2.0 * horizontal.AZ());
	
		
		// 2012-03-06
		// Nasmyth offsets H & V are now equivalent to pointing model (P10/P11).
		// Note, that Juan's fit gives V = -P11!
		return P(AZ_ENC_OFFSET, UT) * cosE + P(AZ_POINTING, UT) + P(INCL_EL_AXIS, UT) * sinE 
			+ (P(INCL_AZ_AXIS_NS, UT) * cosA + P(INCL_AZ_AXIS_EW, UT) * sinA) * sinE + P(DEC_ERROR, UT) * sinA
			- P(NASMYTH_H, UT) * cosE - P(NASMYTH_V, UT) * sinE 
			+ P(AZ_ECCENT_COS, UT) * sin2A + P(AZ_ECCENT_SIN, UT) * cos2A
			+ dxdT * (Tamb - Constant.zeroCelsius);
	}
	
	public double getDY(HorizontalCoordinates horizontal, double UT, double Tamb) {
		double cosE = horizontal.cosLat();
		double sinE = horizontal.sinLat();
		double sinA = Math.sin(horizontal.AZ());
		double cosA = Math.cos(horizontal.AZ());
		
		// 2012-03-06
		// Nasmyth offsets H & V are now equivalent to pointing model (P10/P11).
		// Note, that Juan's fit gives V = -P11!
		return -P(INCL_AZ_AXIS_NS, UT) * sinA + (P(INCL_AZ_AXIS_EW, UT) + P(DEC_ERROR, UT) * sinE) * cosA 
			+ P(EL_POINTING, UT) + P(GRAV_BENDING_COS, UT) * cosE + P(GRAV_BENDING_SIN, UT) * sinE 
			+ P(NASMYTH_H, UT) * sinE - P(NASMYTH_V, UT) * cosE 
			+ P(THIRD_REFRACT, UT) * cosE/sinE
			+ dydT * (Tamb - Constant.zeroCelsius);
	}
	
	public void write(String fileName) throws IOException {
	    try(PrintWriter out = new PrintWriter(new FileOutputStream(fileName))) {
	        for(int i=1; i<P.length; i++) 
	            out.println("P" + i + " = " + Util.f2.format(P[i]) + ", " + Util.f2.format(c[i]) + ", " + Util.f2.format(s[i])); 
	        out.println("TX = " + Util.f2.format(dxdT));
	        out.println("TY = " + Util.f2.format(dydT));
	        out.close();
	    }
	}
	
	public void read(String fileName) throws IOException {
	    new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line, " \t\r=:, ");
                if(!tokens.hasMoreTokens()) return false;
                String constant = tokens.nextToken().toLowerCase();
                if(constant.startsWith("p")) {
                    int i = Integer.parseInt(constant.substring(1));
                    if(tokens.hasMoreTokens()) P[i] = tokens.nextDouble();
                    if(tokens.hasMoreTokens()) c[i] = tokens.nextDouble();
                    if(tokens.hasMoreTokens()) s[i] = tokens.nextDouble();
                }
                else if(constant.equals("t")) dydT = tokens.nextDouble();
                else if(constant.equals("tx")) dxdT = tokens.nextDouble();
                else if(constant.equals("ty")) dydT = tokens.nextDouble();  
                return true;
            }	        
	    }.read(fileName);

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
