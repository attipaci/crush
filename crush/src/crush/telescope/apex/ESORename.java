/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
package crush.telescope.apex;

import nom.tam.fits.*;

import java.io.*;

public final class ESORename {

	public static void main(String[] args) {
		if(args.length == 0) {
			usage();
			System.exit(0);
		}
		
		for(int i=0; i<args.length; i++) {
			try { rename(args[i]); }
			catch(Exception e) { 
				System.err.println(" WARNING! " + args[i] + ": " + e.getMessage());				
			}
		}
	}
	
	private static void rename(String fileName) throws Exception {
		File file = new File(fileName);
	
		boolean isCompressed = fileName.endsWith(".gz") || fileName.endsWith(".Z");
		String ext = ".fits";
		if(isCompressed) ext += fileName.substring(fileName.lastIndexOf("."));
	
		try(Fits fits = new Fits(file, isCompressed)) {	
		    String origName = fits.getHDU(0).getHeader().getStringValue("ORIGFILE") + ext;
		    fits.close();

		    String stem = "";
		    if(fileName.contains(File.separator)) stem += fileName.substring(0, 1 + fileName.lastIndexOf(File.separator));
		    File origFile = new File(stem + origName);

		    System.err.println(" --> " + origName);

		    file.renameTo(origFile);
		}

	}
	
	private static void usage() {
		System.err.println();
		System.err.println("  -------------------------------------------------------------------");
		System.err.println("  esorename -- Rename ESO archive files to their original file names.");
		System.err.println("               Copyright (C)2010 Attila Kovacs");
		System.err.println("  -------------------------------------------------------------------");
		System.err.println();
		System.err.println("  Usage: esorename <filenames>");
		System.err.println();	
	}
	
}
