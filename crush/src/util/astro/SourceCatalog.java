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
package util.astro;

import java.io.*;
import java.util.Vector;
import java.text.*;

import crush.sourcemodel.AstroImage;
import crush.sourcemodel.GaussianSource;

public class SourceCatalog extends Vector<GaussianSource> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7728245572373531025L;

	public void insert(AstroImage image) {
		for(GaussianSource source : this) source.add(image);
	}
	
	public void remove(AstroImage image) {
		for(GaussianSource source : this) source.subtract(image);
	}
	
	public void read(String fileName, AstroImage image) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			try { add(new GaussianSource(line, image)); }
			catch(ParseException e) { System.err.println("WARNING! Cannot parse: " + line); }
		}
		System.err.println(" Source catalog loaded: " + size() + " source(s).");
	}
}
