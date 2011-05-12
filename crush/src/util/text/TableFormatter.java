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
package util.text;

import java.util.*;
import java.text.*;

import util.Util;

public final class TableFormatter {

	public static String format(Entries entries, String format) {
		return format(entries, format, " \t,=");
	}
	
	public static String format(Entries entries, String format, String separators) {
		StringTokenizer tokens = new StringTokenizer(format, separators, true);
		StringBuffer line = new StringBuffer();
		
		while(tokens.hasMoreTokens()) {
			String token = tokens.nextToken();
			boolean separator = false;
		
			if(token.length() == 1) if(separators.contains(token)) separator = true;
			
			if(separator) line.append(token);
			else {
				String formatSpec = null;
				if(token.contains("(")) {
					int i = token.indexOf('(');
					formatSpec = token.substring(i+1, token.indexOf(')'));
					token = token.substring(0, i);
				}
				
				line.append(entries.getFormattedEntry(token, formatSpec));				
			}
		}
		return new String(line);		
	}
	
	public static NumberFormat getNumberFormat(String spec) {
		if(spec == null) return null;
		if(spec.length() == 0) return null;
		
 		char type = spec.charAt(0);
		switch(type) {
		case 'd' : return Util.d[Integer.parseInt(spec.substring(1))];
		case 'f' : return Util.f[Integer.parseInt(spec.substring(1))];
		case 'e' : return Util.e[Integer.parseInt(spec.substring(1))];
		case 's' : return Util.s[Integer.parseInt(spec.substring(1))];
		case 'a' : 
			AngleFormat af = new AngleFormat();
			char sepSpec = spec.charAt(1);
			switch(sepSpec) {
			case ':' : af.colons(); break;
			case 's' : af.symbols(); break;
			case 'l' : af.letters(); break;
			}
			af.setDecimals(Integer.parseInt(spec.substring(2, 3)));
			if(spec.length() > 3) {
				char levelSpec = spec.charAt(3);
				switch(levelSpec) {
				case 'd' : af.topLevel = AngleFormat.DEGREE;
				case 'm' : af.topLevel = AngleFormat.MINUTE;
				case 's' : af.topLevel = AngleFormat.SECOND;
				}
			}
			if(spec.length() > 4) {
				char levelSpec = spec.charAt(4);
				switch(levelSpec) {
				case 'd' : af.bottomLevel = AngleFormat.DEGREE;
				case 'm' : af.bottomLevel = AngleFormat.MINUTE;
				case 's' : af.bottomLevel = AngleFormat.SECOND;
				}
			}
			return af;
		case 'h' : 
			HourAngleFormat hf = new HourAngleFormat();
			sepSpec = spec.charAt(1);
			switch(sepSpec) {
			case ':' : hf.colons(); break;
			case 's' : hf.symbols(); break;
			case 'l' : hf.letters(); break;
			}
			hf.setDecimals(Integer.parseInt(spec.substring(2, 3)));
			if(spec.length() > 3) {
				char levelSpec = spec.charAt(3);
				switch(levelSpec) {
				case 'd' : hf.topLevel = HourAngleFormat.DEGREE;
				case 'm' : hf.topLevel = HourAngleFormat.MINUTE;
				case 's' : hf.topLevel = HourAngleFormat.SECOND;
				}
			}
			if(spec.length() > 4) {
				char levelSpec = spec.charAt(4);
				switch(levelSpec) {
				case 'd' : hf.bottomLevel = HourAngleFormat.DEGREE;
				case 'm' : hf.bottomLevel = HourAngleFormat.MINUTE;
				case 's' : hf.bottomLevel = HourAngleFormat.SECOND;
				}
			}
			return hf;
		case 't' : 
			TimeFormat tf = new TimeFormat();
			sepSpec = spec.charAt(1);
			switch(sepSpec) {
			case ':' : tf.colons(); break;
			case 's' : tf.symbols(); break;
			case 'l' : tf.letters(); break;
			}
			tf.setDecimals(Integer.parseInt(spec.substring(2, 3)));
			if(spec.length() > 3) {
				char levelSpec = spec.charAt(3);
				switch(levelSpec) {
				case 'h' : tf.topLevel = TimeFormat.HOUR;
				case 'm' : tf.topLevel = TimeFormat.MINUTE;
				case 's' : tf.topLevel = TimeFormat.SECOND;
				}
			}
			if(spec.length() > 4) {
				char levelSpec = spec.charAt(4);
				switch(levelSpec) {
				case 'h' : tf.bottomLevel = TimeFormat.HOUR;
				case 'm' : tf.bottomLevel = TimeFormat.MINUTE;
				case 's' : tf.bottomLevel = TimeFormat.SECOND;
				}
			}
			return tf;
		}
		return null;
	}
	
	public interface Entries {

		public String getFormattedEntry(String name, String formatSpec);

	}
	
	public static String NO_SUCH_DATA = "(n/a)";
}
