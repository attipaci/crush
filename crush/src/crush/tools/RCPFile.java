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
package crush.tools;

import java.io.*;
import java.util.*;

import util.*;

public class RCPFile {
	Hashtable<Integer, Entry> table = new Hashtable<Integer, Entry>();
	
	public RCPFile(String fileName) throws IOException {
		Vector2D offset = new Vector2D();
		
		if(fileName.contains("@")) {
			int i = fileName.lastIndexOf('@');
			offset.parse(fileName.substring(i+1));
			System.err.print("@" + offset + " ");
			fileName = fileName.substring(0, i);
		}
		
		read(fileName);
		subtract(offset);
	}
	
	public void read(String fileName) throws IOException {
		table.clear();
		
		System.err.print("Reading " + fileName + "... ");
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			Entry entry = new Entry();
			try { 
				entry.parse(line); 
				table.put(entry.index, entry);
			}
			catch(IllegalArgumentException e) {}
		}
		in.close();
		
		System.err.println(table.size() + " entries found.");
	}

	public void subtract(Vector2D offset) {
		for(Entry entry : table.values()) {
			entry.x -= offset.x;
			entry.y -= offset.y;			
		}
	}
	
	public void print() {
		Set<Integer> keys = table.keySet();
		Vector<Integer> list = new Vector<Integer>(keys); 
		Collections.sort(list);
		for(int i : list) System.out.println(table.get(i));	
	}
	
	public void averageWith(RCPFile other) {
		for(int i : other.table.keySet()) {
			if(table.containsKey(i)) table.get(i).averageWith(other.table.get(i));
			else table.put(i, other.table.get(i));			
		}
	}
	
	public class Entry {
		int index;
		double x = Double.NaN;
		double y = Double.NaN;
		double sourceGain = Double.NaN;
		double skyGain = Double.NaN;	
		int n = 0;
		
		public void parse(String line) {
			StringTokenizer tokens = new StringTokenizer(line);
			if(tokens.countTokens() < 3) throw new IllegalArgumentException("not enough columns");
			
			n = 1;
			index = Integer.parseInt(tokens.nextToken());
			x = Double.parseDouble(tokens.nextToken());
			y = Double.parseDouble(tokens.nextToken());
			
			if(tokens.hasMoreTokens()) sourceGain = Double.parseDouble(tokens.nextToken());
			if(tokens.hasMoreTokens()) skyGain = Double.parseDouble(tokens.nextToken());
		}
		
		public void averageWith(Entry other) {
			if(index != other.index) throw new IllegalStateException("Mismatched indexes");
			double f = (double) n / (n + other.n);
			
			x = f * x + (1.0 - f) * other.x;
			y = f * y + (1.0 - f) * other.y;
			sourceGain = f * sourceGain + (1.0 - f) * other.sourceGain;
			skyGain = f * skyGain + (1.0 - f) * other.skyGain;
			
			n += other.n;
		}
		
		@Override
		public String toString() {
			String line =  index + "\t" + Util.f2.format(x) + "\t" + Util.f2.format(y);
			if(!Double.isNaN(sourceGain)) {
				line += "\t" + Util.f3.format(sourceGain);
				if(!Double.isNaN(skyGain)) line += "\t" + Util.f3.format(skyGain);
			}
 			return line;
		}
	}
}
