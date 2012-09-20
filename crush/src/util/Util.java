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
// Copyright (c) 2007 Attila Kovacs 

package util;

import nom.tam.fits.*;
import nom.tam.util.*;

import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import java.text.*;

import util.text.AngleFormat;
import util.text.HourAngleFormat;
import util.text.SignificantFigures;
import util.text.TimeFormat;

public final class Util {
	public final static DecimalFormat f0 = new DecimalFormat("0");
	public final static DecimalFormat f1 = new DecimalFormat("0.0");
	public final static DecimalFormat f2 = new DecimalFormat("0.00");
	public final static DecimalFormat f3 = new DecimalFormat("0.000");
	public final static DecimalFormat f4 = new DecimalFormat("0.0000");
	public final static DecimalFormat f5 = new DecimalFormat("0.00000");
	public final static DecimalFormat f6 = new DecimalFormat("0.000000");
	public final static DecimalFormat f7 = new DecimalFormat("0.0000000");
	public final static DecimalFormat f8 = new DecimalFormat("0.00000000");
	public final static DecimalFormat f9 = new DecimalFormat("0.000000000");
	
	public final static DecimalFormat F0 = new DecimalFormat("0");
	public final static DecimalFormat F1 = new DecimalFormat("0.#");
	public final static DecimalFormat F2 = new DecimalFormat("0.##");
	public final static DecimalFormat F3 = new DecimalFormat("0.###");
	public final static DecimalFormat F4 = new DecimalFormat("0.####");
	public final static DecimalFormat F5 = new DecimalFormat("0.#####");
	public final static DecimalFormat F6 = new DecimalFormat("0.######");
	public final static DecimalFormat F7 = new DecimalFormat("0.#######");
	public final static DecimalFormat F8 = new DecimalFormat("0.########");
	public final static DecimalFormat F9 = new DecimalFormat("0.#########");

	public final static DecimalFormat e0 = new DecimalFormat("0E0");
	public final static DecimalFormat e1 = new DecimalFormat("0.0E0");
	public final static DecimalFormat e2 = new DecimalFormat("0.00E0");
	public final static DecimalFormat e3 = new DecimalFormat("0.000E0");
	public final static DecimalFormat e4 = new DecimalFormat("0.0000E0");
	public final static DecimalFormat e5 = new DecimalFormat("0.00000E0");
	public final static DecimalFormat e6 = new DecimalFormat("0.000000E0");
	public final static DecimalFormat e7 = new DecimalFormat("0.0000000E0");
	public final static DecimalFormat e8 = new DecimalFormat("0.00000000E0");
	public final static DecimalFormat e9 = new DecimalFormat("0.000000000E0");

	public final static DecimalFormat E0 = new DecimalFormat("0E0");
	public final static DecimalFormat E1 = new DecimalFormat("0.#E0");
	public final static DecimalFormat E2 = new DecimalFormat("0.##E0");
	public final static DecimalFormat E3 = new DecimalFormat("0.###E0");
	public final static DecimalFormat E4 = new DecimalFormat("0.####E0");
	public final static DecimalFormat E5 = new DecimalFormat("0.#####E0");
	public final static DecimalFormat E6 = new DecimalFormat("0.######E0");
	public final static DecimalFormat E7 = new DecimalFormat("0.#######E0");
	public final static DecimalFormat E8 = new DecimalFormat("0.########E0");
	public final static DecimalFormat E9 = new DecimalFormat("0.#########E0");
	
	public final static DecimalFormat d1 = new DecimalFormat("0");
	public final static DecimalFormat d2 = new DecimalFormat("00");
	public final static DecimalFormat d3 = new DecimalFormat("000");
	public final static DecimalFormat d4 = new DecimalFormat("0000");
	public final static DecimalFormat d5 = new DecimalFormat("00000");
	public final static DecimalFormat d6 = new DecimalFormat("000000");
	public final static DecimalFormat d7 = new DecimalFormat("0000000");
	public final static DecimalFormat d8 = new DecimalFormat("00000000");
	public final static DecimalFormat d9 = new DecimalFormat("000000000");
	
	public final static SignificantFigures s1 = new SignificantFigures(1);
	public final static SignificantFigures s2 = new SignificantFigures(2);
	public final static SignificantFigures s3 = new SignificantFigures(3);
	public final static SignificantFigures s4 = new SignificantFigures(4);
	public final static SignificantFigures s5 = new SignificantFigures(5);
	public final static SignificantFigures s6 = new SignificantFigures(6);
	public final static SignificantFigures s7 = new SignificantFigures(7);
	public final static SignificantFigures s8 = new SignificantFigures(8);
	public final static SignificantFigures s9 = new SignificantFigures(9);
	public final static SignificantFigures s10 = new SignificantFigures(10);
	
	public final static SignificantFigures S1 = new SignificantFigures(1, false);
	public final static SignificantFigures S2 = new SignificantFigures(2, false);
	public final static SignificantFigures S3 = new SignificantFigures(3, false);
	public final static SignificantFigures S4 = new SignificantFigures(4, false);
	public final static SignificantFigures S5 = new SignificantFigures(5, false);
	public final static SignificantFigures S6 = new SignificantFigures(6, false);
	public final static SignificantFigures S7 = new SignificantFigures(7, false);
	public final static SignificantFigures S8 = new SignificantFigures(8, false);
	public final static SignificantFigures S9 = new SignificantFigures(9, false);
	public final static SignificantFigures S10 = new SignificantFigures(10, false);

	public final static HourAngleFormat hf0 = new HourAngleFormat(0);
	public final static HourAngleFormat hf1 = new HourAngleFormat(1);
	public final static HourAngleFormat hf2 = new HourAngleFormat(2);
	public final static HourAngleFormat hf3 = new HourAngleFormat(3);

	public final static AngleFormat af0 = new AngleFormat(0);
	public final static AngleFormat af1 = new AngleFormat(1);
	public final static AngleFormat af2 = new AngleFormat(2);
	public final static AngleFormat af3 = new AngleFormat(3);
	
	public final static TimeFormat tf0 = new TimeFormat(0);
	public final static TimeFormat tf1 = new TimeFormat(1);
	public final static TimeFormat tf2 = new TimeFormat(2);
	public final static TimeFormat tf3 = new TimeFormat(3);
	
	
	
	public final static DecimalFormat[] e = { e0, e1, e2, e3, e4, e5, e6, e7, e8, e9 },
		E = { E0, E1, E2, E3, E4, E5, E6, E7, E8, E9 },
		f = { f0, f1, f2, f3, f4, f5, f6, f7, f8, f9 },
		F = { F0, F1, F2, F3, F4, F5, F6, F7, F8, F9 },
		d = { null, d1, d2, d3, d4, d5, d6, d7, d8, d9 },
		s = { null, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10},
		S = { null, S1, S2, S3, S4, S5, S6, S7, S8, S9, S10}
	;

	public static DecimalFormat getDecimalFormat(double significance) {
		return getDecimalFormat(significance, 6, true);
	}
	
	public static DecimalFormat getDecimalFormat(double significance, boolean trailingZeroes) {
		return getDecimalFormat(significance, 6, trailingZeroes);
	}
	
	public static DecimalFormat getDecimalFormat(double significance, int maxDecimals) {
		return getDecimalFormat(significance, maxDecimals, true);
	}
	
	public static DecimalFormat getDecimalFormat(double significance, int maxDecimals, boolean trailingZeroes) {
		if(Double.isNaN(significance)) return trailingZeroes ?  f1 : F1;
		if(significance == 0.0) return trailingZeroes ? f2 : F2;
		int figures = Math.min(maxDecimals, (int) Math.floor(Math.log10(Math.abs(significance))) + 2);
		figures = Math.max(1, figures);
		return trailingZeroes ? s[figures] : S[figures];
	}
	
	/**
       Return the standardized angle for a given angle.
       @return an angle between -Pi and Pi.       
	 */
	public final static double standardAngle(double angle) {
		angle = Math.IEEEremainder(angle, 2.0 * Math.PI);
		return angle;
	}

	/**
       Return the time of day for a given time value.
       @return time betweem 0-24h.       
	 */
	public final static double timeOfDay(double time) {
		time = Math.IEEEremainder(time, Unit.day);
		if(time < 0.0) time += 24.0 * Unit.hour;
		return time;
	}

	/**
       Return the equivalent time value for angle. Useful for converting angles to right-ascention or hour-angle
       @return a time.      
       @see angleOfTime
	 */
	public final static double timeOfAngle(double angle) {
		return timeOfDay(angle * Unit.second/Unit.secondAngle);
	}

	/**
       Return the equivalent angle value for a time value. Useful for converting right-ascention or hour-angle to radians.
       @return an angle.
       @see timeOfAngle
	 */
	public final static double angleOfTime(double time) {
		return standardAngle(time * Unit.secondAngle/Unit.second);
	}

	/**
       Construct an angle from degree, arc-minute and arc-second values.
       @param degree
       @param arcminute
       @param arcsecond
       @return an angle.
       @see DMS
	 */    
	public static double DMS(int D, int M, double S) {
		return D*Unit.degree + M*Unit.arcmin + S*Unit.arcsec;
	}

	/**
       Construct an time from hour, minute and second values.
       @param degree
       @param arcminute
       @param arcsecond
       @return an angle.
       @see HMS
	 */ 

	public static double HMS(int H, int M, double S) {
		return H*Unit.hour + M * Unit.minute + S * Unit.second;
	} 

	public static String HMS(double time) { return HMS(time, f[3]); }

	public static String HMS(double time, int precision) { return HMS(time, f[precision]); }

	public static String HMS(double time, DecimalFormat df) {
		if(time < 0.0) return("-" + HMS(-time, df));

		time /= Unit.hour; int hour = (int)time; time -= hour;
		time *= 60.0; int min = (int)time; time -= min;

		return d2.format(hour) + ":" + d2.format(min) + ":" + df.format(60.0 * time);
	}

	public static String shortHMS(double time, DecimalFormat df) {
		if(time < 0.0) return("-" + shortHMS(-time, df));

		time /= Unit.hour; int hour = (int)time; time -= hour;
		time *= 60.0; int min = (int)time; time -= min;

		return (hour > 0 ? d2.format(hour) + ":" : "") + (min > 0 ? d2.format(min) + ":" : "") + df.format(60.0 * time);
	}



	public static String DMS(double angle) { return DMS(angle, f[3]); }

	public static String DMS(double angle, int precision) { return DMS(angle, f[precision]); }

	public static String DMS(double angle, DecimalFormat df) {
		if(angle < 0.0) return("-" + DMS(-angle, df));

		angle /= Unit.degree; int d = (int)angle; angle -= d;
		angle *= 60.0; int m = (int)angle; angle -= m;

		return d2.format(d) + ":" + d2.format(m) + ":" + df.format(60.0 * angle);
	}

	public static String shortDMS(double angle, DecimalFormat df) {
		if(angle < 0.0) return("-" + shortDMS(-angle, df));

		angle /= Unit.degree; int d = (int)angle; angle -= d;
		angle *= 60.0; int m = (int)angle; angle -= m;

		return (d > 0 ? d2.format(d) + ":" : "") + (m > 0 ? d2.format(m) + ":" : "") + df.format(60.0 * angle);
	}


	public static double parseTime(String HMS) {
		StringTokenizer tokens = new StringTokenizer(HMS, " \t\n\r:hmsHMS");

		double time = Integer.parseInt(tokens.nextToken()) * Unit.hour;
		time += Integer.parseInt(tokens.nextToken()) * Unit.min;
		time += Double.parseDouble(tokens.nextToken()) * Unit.second;

		return time;
	}

	public static double parseAngle(String DMS) {
		StringTokenizer tokens = new StringTokenizer(DMS, "- \t\n\r:dmsDMS");

		double angle = Integer.parseInt(tokens.nextToken()) * Unit.degree;
		angle += Integer.parseInt(tokens.nextToken()) * Unit.arcmin;
		angle += Double.parseDouble(tokens.nextToken()) * Unit.arcsec;

		if(DMS.indexOf('-') >= 0) angle *= -1;

		return angle;
	}

	public static boolean parseBoolean(String value) throws NumberFormatException {
		// Yes/No
		if(value.equalsIgnoreCase("y")) return true;
		if(value.equalsIgnoreCase("yes")) return true;
		if(value.equalsIgnoreCase("n")) return false;
		if(value.equalsIgnoreCase("no")) return false;
		// True/False
		if(value.equalsIgnoreCase("t")) return true;
		if(value.equalsIgnoreCase("true")) return true;
		if(value.equalsIgnoreCase("f")) return false;
		if(value.equalsIgnoreCase("false")) return false;
		// On/Off
		if(value.equalsIgnoreCase("on")) return true;
		if(value.equalsIgnoreCase("off")) return false;
		// 1/0
		if(value.equalsIgnoreCase("1")) return true;
		if(value.equalsIgnoreCase("0")) return false;

		throw new NumberFormatException(" ERROR! Illegal Boolean value: " + value);
	}

	static double[] lineCoeff = new double[2];
	public static double[] lineFit(double[] y, double[] w) {
		double s=0.0, sx=0.0, sy=0.0, sxx=0.0, sxy=0.0;

		for(int i=0; i<y.length; i++) {
			s += w[i];
			sx += w[i]*i;
			sy += w[i]*y[i];
			sxx += w[i]*i*i;
			sxy += w[i]*i*y[i];
		}
		double D = s*sxx - sx*sx;

		lineCoeff[0] = (sxx*sy - sx*sxy) / D;
		lineCoeff[1] = (s*sxy - sx*sy) / D;

		return lineCoeff;
	}

	// implement with Properties...
	public static void printContents(Object object) {
		System.out.println("Contents of " + object.getClass().getName() + ":");
		System.out.println("-------------------------------------------------------------");

		Field[] field = object.getClass().getFields();
		for(int i=0; i<field.length; i++) {
			try {
				Object value = field[i].get(object);
				System.out.println("  " + field[i].getName() + " = " + (value == null ? "null" : value.toString())); 
			}
			catch(IllegalAccessException e) {}
		}

		System.out.println("-------------------------------------------------------------");
	}

	public static int msb(int value) {
		return log2floor(value);
	}
	
	public static int log2floor(int value) {		
		int bits = 0;
		while((value >> bits) > 0) bits++;
		return bits-1;
	}
	
	public static int log2ceil(int value) {
		int p = log2floor(value);
		return 1<<p == value ? p : p+1;
	}
	
	public static int log2round(int value) {
		int pfloor = log2floor(value);
		int floor = 1<<pfloor;
		if(value == floor) return pfloor;
		
		return (double) value / floor < (double)(floor<<1) / value ? pfloor : pfloor + 1;
	}
	
	public static int pow2floor(int value) { return 1 << log2floor(value); }
	
	public static int pow2ceil(int value) { return 1 << log2ceil(value); }
	
	public static int pow2round(int value) { return 1 << log2round(value); }

	
	/*
    public static double fitsDouble(double value) {
	if(value < 0.0 && value > -1.0) {
	    DecimalFormat df = new DecimalFormat("0.0000000000000E0");
	    return Double.parseDouble(df.format(value));
	}
	else if(value > 0.0 && value < 1.0) {
	    DecimalFormat df = new DecimalFormat("0.00000000000000E0");
	    return Double.parseDouble(df.format(value));
	}
	else return value;
    }

    public static void addLongFitsKey(Header header, String key, String value, String comment) 
	throws FitsException, HeaderCardException {

	Cursor cursor = header.iterator();
	while(cursor.hasNext()) cursor.next();
	addLongFitsKey(cursor, key, value, comment);
    }

    public static String getLongFitsKey(Header header, String key) {
	String value = header.getStringValue(key);

	if(value == null) {
	    value = new String();
	    char ext = 'A';
	    String part;
	    do {
		part = header.getStringValue(key + ext);
		if(part != null) value += part;
		ext++;
	    } 
	    while(part != null);
	}

	return value;
    }


    public static void addLongFitsKey(Cursor cursor, String key, String value, String comment) 
	throws FitsException, HeaderCardException {

	final int size = 65 - comment.length();

	if(value.length() <= size) {
	    cursor.add(new HeaderCard(key, value, comment));
	    return;
	}

	int start = 0;	
	char ext = 'A';

	while(start < value.length()) {
	    int end = start + size;
	    if(end > value.length()) end = value.length();

	    cursor.add(new HeaderCard(key + ext, value.substring(start, end), comment));

	    ext++;
	    start = end;
	}
    }
	 */
	
	public static String getSystemPath(String spec) {
		String homes = System.getProperty("user.home");
		
		// Make sure that UNIX-type pathnames are always accepted since these are default
		// to configuration files. This comes at the small price that pathnames are not
		// allowed to contain '/' characters other than path separators...
		if(!File.separator.equals("/")) spec = spec.replace('/', File.separator.charAt(0));
		homes = homes.substring(0, homes.lastIndexOf(File.separator) + 1);
		
		String text = "";
		
		// See if it's a home directory specification starting with '~'...
		if(spec.charAt(0) == '~') {
			int userChars = spec.indexOf(File.separator) - 1;
			if(userChars < 0) userChars = spec.length() - 1;
			if(userChars == 0) text = System.getProperty("user.home");
			else text += homes + spec.substring(1, 1 + userChars);
			spec = spec.substring(userChars + 1);
		}
		
		while(spec.contains("{$")) {
			int from = spec.indexOf("{$");
			int to = spec.indexOf("}");
			if(to < 0) return text + spec;
			
			text += spec.substring(0, from);
			text += System.getenv(spec.substring(from+2, to));
			
			spec = spec.substring(to+1);
		}
		
		text += spec;
		
		return text;
		
	}

	public static void addLongHierarchKey(Cursor cursor, String key, String value) throws FitsException, HeaderCardException {
		addLongHierarchKey(cursor, key, 0, value);
	}

	static int minFitsValueLength = 5;
	
	public static String getAbbreviatedHierarchKey(String key) {
		int max = 66 - minFitsValueLength;
		if(key.length() <= max) return key;
		
		int n = (max - 3) / 2;
		return key.substring(0, n) + "---" + key.substring(key.length() - n, key.length());
	}
	
	public static void addLongHierarchKey(Cursor cursor, String key, int part, String value) throws FitsException, HeaderCardException {	
		key = getAbbreviatedHierarchKey(key);
		if(value.length() == 0) value = "true";
		
		String alt = part > 0 ? "." + part : "";

		int available = 69 - (key.length() + alt.length() + 3);

		if(available < 1) {
			System.err.println("WARNING! Cannot write FITS key: " + key);
			return;
		}
		
		if(value.length() < available) cursor.add(new HeaderCard("HIERARCH." + key + alt, value, null));
		else { 
			if(alt.length() == 0) {
				part = 1;
				alt = "." + part;
				available -= 2;
			}

			cursor.add(new HeaderCard("HIERARCH." + key + alt, value.substring(0, available), null));
			addLongHierarchKey(cursor, key, (char)(part+1), value.substring(available)); 
		}
	}
	
	public static void addLongFitsKey(Header header, String key, String value, String comment) 
	throws FitsException, HeaderCardException {
		Cursor cursor = header.iterator();
		while(cursor.hasNext()) cursor.next();
		addLongFitsKey(cursor, key, value, comment);
	}

	public static String getLongFitsKey(Header header, String key) {
		if(key.length() >= 8) key = key.substring(0, 6) + "-";
	
		String value = header.getStringValue(key);

		if(value == null) {
			value = new String();
			char ext = 'A';
			String part;
			do {
				part = header.getStringValue(key + ext);
				if(part != null) value += part;
				ext++;
			} 
			while(part != null);
		}

		return value;
	}


	public static void addLongFitsKey(Cursor cursor, String key, String value, String comment) 
	throws FitsException, HeaderCardException {
		if(key.length() >= 8) key = key.substring(0, 6) + "-";
		
		final int size = 65 - comment.length();

		if(value.length() <= size) {
			cursor.add(new HeaderCard(key, value, comment));
			return;
		}

		int start = 0;	
		char ext = 'A';

		while(start < value.length()) {
			int end = start + size;
			if(end > value.length()) end = value.length();

			cursor.add(new HeaderCard(key + ext, value.substring(start, end), comment));

			ext++;
			start = end;
		}
	}

	
	public static short unsigned(byte b) {
		return ((short)(b & 0xff));
	}	

	
	public static int unsigned(short s) {
		return (s & 0xffff);
	}

	
	public static long unsigned(int i) {
		return (i & 0xffffffffL);
	}

	
	public static byte unsignedByte(short value) {
		return (byte)(value & 0xff);
	}
	
	public static short unsignedShort(int value) {
		return (short)(value & 0xffff);
	}
	
	public static int unsignedInt(long value) {
		return (int)(value & 0xffffffffL);
	}
	
	public static long pseudoUnsigned(long l) {
		return l < 0L ? Long.MAX_VALUE : l;
	}
	
	
	
	public static short[] unsigned(byte[] b) {
		short[] s = new short[b.length];
		for(int i=0; i<b.length; i++) s[i] = unsigned(b[i]);	
		return s;
	}
	
	public static int[] unsigned(short[] s) {
		int[] i = new int[s.length];
		for(int j=0; j<s.length; j++) i[j] = unsigned(s[j]);	
		return i;
	}
	
	public static long[] unsigned(int[] i) {
		long[] l = new long[i.length];
		for(int j=0; j<i.length; j++) l[j] = unsigned(i[j]);	
		return l;
	}
	
	public static void pseudoUnsigned(long[] l) {
		for(int j=0; j<l.length; j++) l[j] = pseudoUnsigned(l[j]);	
	}
	
	public static String getProperty(String name) {
		String value = System.getProperty(name);
		return value == null ? "n/a" : value;
	}
	
	public static String defaultFormat(double value, NumberFormat f) {
		return f == null ? Double.toString(value) : f.format(value);
	}
	
	public static String fromEscapedString(String value) throws IllegalStateException {
		StringBuffer buffer = new StringBuffer(value.length());
		for(int i=0; i<value.length(); i++) {
			final char c = value.charAt(i);
			if(c == '\\') {
				if(++i == value.length()) throw new IllegalStateException("Illegal escape character at the end of string.");
				switch(value.charAt(i)) {
				case '\\' : buffer.append('\\'); break;
				case 't' : buffer.append('\t'); break;
				case 'n' : buffer.append('\n'); break;
				case 'r' : buffer.append('\r'); break;
				case 'b' : buffer.append('\b'); break;
				case '"' : buffer.append('\"'); break;
				case '\'' : buffer.append('\''); break;
				default : throw new IllegalStateException("Illegal escape sequence '\\" + value.charAt(i) + "'.");
				}
			}
			else buffer.append(c);
		}
		
		return new String(buffer);
	}
	
	public static String toEscapedString(String value) {
		StringBuffer buffer = new StringBuffer(2*value.length());
		for(int i=0; i<value.length(); i++) {
			char c = value.charAt(i);
			switch(c) {
			case '\\' : buffer.append("\\\\"); break;
			case '\t' : buffer.append("\\t"); break;
			case '\n' : buffer.append("\\n"); break;
			case '\r' : buffer.append("\\r"); break;
			case '\b' : buffer.append("\\b"); break;
			case '\"' : buffer.append("\\\""); break;
			case '\'' : buffer.append("\\\'"); break;
			default : buffer.append(c);
			}
		}
		
		return new String(buffer);
	}
	
	public static double sinc(double x) { return x == 0.0 ? 1.0 : Math.sin(x) / x; } 
	
	public static final double sigmasInFWHM = 2.0*Math.sqrt(2.0*Math.log(2.0));
}


