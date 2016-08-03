/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;

import crush.CRUSH;
import jnum.Unit;
import jnum.Util;
import jnum.io.LineParser;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;


public class SyntheticRCP {
	ArrayList<Coefficient> xcoeffs = new ArrayList<Coefficient>();
	ArrayList<Coefficient> ycoeffs = new ArrayList<Coefficient>();
	
	Hashtable<Integer,Double> sourceGains;
	Hashtable<Integer,Double> skyGains;
	
	double rotate = 0.0;
	
	public static void main(String[] args) {
		SyntheticRCP distortion = new SyntheticRCP();
		
		try {
			if(args.length == 0) usage();
			distortion.parse(args[0]);
			if(args.length > 1) distortion.gainsFrom(args[1]);
			if(args.length > 2) distortion.rotate = Double.parseDouble(args[2]) * Unit.deg;
			distortion.print(System.out);
		}
		catch(Exception e) { CRUSH.error(SyntheticRCP.class, e); }
		
	}
	
	public void parse(String fileName) throws IOException {	
	    new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                StringTokenizer tokens = new StringTokenizer(line);
                String spec = tokens.nextToken();
              
                xcoeffs.add(new Coefficient(spec, tokens.nextToken())); 
                ycoeffs.add(new Coefficient(spec, tokens.nextToken())); 
                return true;
            }
	    }.read(fileName);
		
		CRUSH.info(this, (xcoeffs.size() + ycoeffs.size()) + " coefficients parsed.");
	}

	public void gainsFrom(String rcpFile) throws IOException {		
		CRUSH.info(this, "Loading gains from " + rcpFile);
		
		sourceGains = new Hashtable<Integer, Double>();
		skyGains = new Hashtable<Integer, Double>();
	
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                int channel = tokens.nextInt();
                sourceGains.put(channel, tokens.nextDouble());
                if(tokens.hasMoreTokens()) skyGains.put(channel, tokens.nextDouble());
                return true;
            }
		}.read(rcpFile);
	}
	
	public double getValue(ArrayList<Coefficient> coeffs, double x, double y) {
		double value = 0.0;
		for(Coefficient c : coeffs) value += c.valueAt(x, y);
		return value;		
	}
	
	public void print(PrintStream out) {
		out.println("# Synthetic RCP ");
		for(int i=0; i<128; i++) {
			double dc = i % 8 - 3.5;
			double dr = i / 8 - 7.5;
			
			int channel = (i+1);
			double sourceGain = 1.0;
			double skyGain = 1.0;
			
			if(sourceGains != null) if(sourceGains.containsKey(channel)) sourceGain = sourceGains.get(channel);
			if(skyGains != null) if(skyGains.containsKey(channel)) skyGain = skyGains.get(channel);
			
			Vector2D pos = new Vector2D(
						getValue(xcoeffs, dc, dr),
						getValue(ycoeffs, dc, dr)
					);
			
			pos.rotate(rotate);
			
			out.print(channel + "\t" + sourceGain + "\t" + skyGain + "\t");
			out.print(Util.f1.format(pos.x()) + "\t");
			out.print(Util.f1.format(pos.y()) + "\n");
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
			
			CRUSH.detail(this, "> " + xOrder + "," + yOrder + " : " + value);
			
			return from+1;
		}
		
	}
	
	public static void usage() {
		System.err.println();
		String info = 
			"  -----------------------------------------------------------------------------\n" +
			"  SyntheticRCP -- RCP generation tool.\n" +
			"                  Copyright (C)2011 Attila Kovacs <attila[AT]submm.caltech.edu>\n" +
			"  -----------------------------------------------------------------------------\n" +	
			"\n" +
			"  Usage: java crush.array.SyntheticRCP <distortion> [rcp [rotation]]\n" +
			"\n" +
			"    <distortion>  A list of distortion parameters. See e.g. 'gismo/distortion.dat'.\n" +
			"    [rcp]         (optional) An observed RCP file for source gains.\n" +
			"    [rotation]	   (optional) Presume a rotation in degrees.\n";
		
		System.out.println(info);
		System.exit(0);
	}
}
