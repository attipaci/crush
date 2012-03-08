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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import util.Util;

public class SyntheticRCP {
	ArrayList<Coefficient> xcoeffs = new ArrayList<Coefficient>();
	ArrayList<Coefficient> ycoeffs = new ArrayList<Coefficient>();
	
	
	public static void main(String[] args) {
		SyntheticRCP distortion = new SyntheticRCP();
		
		try {
			distortion.parse(args[0]);
			distortion.print();
		}
		catch(Exception e) { e.printStackTrace(); }
		
	}
	
	public void parse(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		int n = 0;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			
			String spec = tokens.nextToken();
			
			try { 
				xcoeffs.add(new Coefficient(spec, tokens.nextToken())); 
				n++;
			}
			catch(Exception e) { System.err.println(e.getMessage()); }
			
			try { 
				ycoeffs.add(new Coefficient(spec, tokens.nextToken())); 
				n++;
			}
			catch(Exception e) { System.err.println(e.getMessage()); }
		}
		
		System.err.println(n + " coefficients parsed.");
		
		in.close();
	}

	public double getValue(ArrayList<Coefficient> coeffs, double x, double y) {
		double value = 0.0;
		for(Coefficient c : coeffs) value += c.valueAt(x, y);
		return value;		
	}
	
	public void print() {
		System.out.println("# Synthetic RCP for GISMO ");
		for(int i=0; i<128; i++) {
			double dc = i % 8 - 3.5;
			double dr = i / 8 - 7.5;
			
			System.out.print((i+1) + "\t1.0\t1.0\t");
			System.out.print(Util.f1.format(getValue(xcoeffs, dc, dr)) + "\t");
			System.out.print(Util.f1.format(getValue(ycoeffs, dc, dr)) + "\n");
		}
	}
	
	class Coefficient {
		int xOrder, yOrder;
		double value;		
	
		public Coefficient(String spec, String value) throws NumberFormatException, ParseException {
			this.value = Double.parseDouble(value);
			
			xOrder = yOrder = 0;
			
			spec.toLowerCase();
			
			int from = 0;
			while(from < spec.length()) from = parse(spec, from);
		}
		
		public double valueAt(double x, double y) {
			return value * Math.pow(x, xOrder) * Math.pow(y, yOrder);
		}
		
		private int parse(String spec, int from) throws NumberFormatException, ParseException {
			char c = spec.charAt(from);
			
			switch(c) {
			case '0' : break;
			case 'x' :
				from++;
				c = from < spec.length() ? spec.charAt(from) : '1';
				if(c == 'y') xOrder++;
				else xOrder += Integer.parseInt(c + "");
				break;
			case 'y' :
				from++;
				c = from < spec.length() ? spec.charAt(from) : '1';
				if(c == 'x') yOrder++;
				else yOrder += Integer.parseInt(c + "");
			}
			
			System.err.println("> " + xOrder + "," + yOrder + " : " + value);
			
			return from+1;
		}
		
	}
	
	
}
