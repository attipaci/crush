/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.scuba2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import kovacs.util.Configurator;


public class Scuba2Fits implements Comparable<Scuba2Fits> {
	private Configurator options;
	private File file;
	private String fileName;
	
	Fits fits;
	BasicHDU<?>[] HDU;
	
	public Scuba2Fits(String fileName, Configurator options) throws Exception {
		this.options = options;
		
		String lFileName = fileName.toLowerCase();
		
		if(lFileName.endsWith(".fits") || fileName.endsWith(".fits.gz")) setFile(new File(fileName));
		else if(lFileName.endsWith(".sdf")) {
			File fitsFile = new File(getFitsName(fileName));
			if(fitsFile.exists()) throw new IllegalStateException("SDF has FITS alternative already");
			setFile(fromSDF(fileName));
		}
		else if(lFileName.endsWith(".sdf.gz")) throw new IOException("Uncompress SDF '" + fileName + "' before use.");
		else throw new IllegalArgumentException("Input file is neither SDF or FITS...");
	}
	
	public void setFile(File file) throws FileNotFoundException, FitsException {
		this.file = null;
		this.fileName = null;
	
		if(!file.exists()) throw new FileNotFoundException("File not found: " + file.getPath());
		
		this.file = file;
		this.fileName = file.getName();
		
		fits = new Fits(file);
		HDU = fits.read();
	}
	
	public File getFile() { return file; }
	
	public String getFileName() { return fileName; }
	
	public BasicHDU<?>[] getHDUs() { return HDU; }
	
	public boolean hasOption(String key) {
		return options.isConfigured(key);
	}
	
	public Configurator option(String key) {
		return options.get(key);
	}
	
	public int getSubarrayIndex() { return fileName.charAt(2) - 'a'; }
	
	public String getDateString() { return fileName.substring(3, 11); }
	
	public int getScanNumber() { return Integer.parseInt(fileName.substring(12, 17)); }
	
	public int getSubscanNo() { return Integer.parseInt(fileName.substring(18, 22)); }

	
	@Override
	public int compareTo(Scuba2Fits other) {
		
		int d1 = Integer.parseInt(getDateString());
		int d2 = Integer.parseInt(other.getDateString());
		if(d1 < d2) return -1;
		if(d1 > d2) return 1;
		
		int i1 = getScanNumber();
		int i2 = other.getScanNumber();
		if(i1 < i2) return -1;
		if(i1 > i2) return 1;

		int s1 = getSubscanNo();
		int s2 = other.getSubscanNo();
		if(s1 < s2) return -1;
		if(s1 > s2) return 1;

		int a1 = getSubarrayIndex();
		int a2 = other.getSubarrayIndex();
		if(a1 < a2) return -1;
		if(a1 > a2) return 1;
		
		return 0;
	}

	
	public static String getFitsName(String sdfName) {
		return sdfName.substring(0, sdfName.length() - 4) + ".fits";		
	}
	
	
	private File fromSDF(String sdfName) throws IOException, FileNotFoundException {
		// If it's an SDF, check if an equivalent FITS exists also...
		// If so, then we can skip the SDF, and wait for the FITS to be read...
		File fitsFile = new File(getFitsName(sdfName));
		if(fitsFile.exists()) return fitsFile;
					
		File sdf = new File(sdfName);
		if(!sdf.exists()) throw new FileNotFoundException("SDF file '" + sdfName + "' not found."); 
					
		if(!options.isConfigured("ndf2fits")) 
			throw new FileNotFoundException("Please set path to 'ndf2fits' with the 'ndf2fits' option. E.g.\n"
					+ "  ndf2fits = /usr/local/starlink/bin/ndf2fits\n");
		
		String inName = sdf.getAbsolutePath();
		String command = options.get("ndf2fits").getValue();
		String outName = sdf.getParentFile().getPath() + File.separator + getFitsName(sdf.getName());
		
		Runtime runtime = Runtime.getRuntime();

		String commandLine = command + " " + inName + " " + outName + " proexts";
		String[] commandArray = { command, inName, outName, "proexts" };

		System.err.println(" Converting SDF to FITS...");
		System.err.println(" > " + commandLine);

		Process convert = runtime.exec(commandArray); 
		//BufferedReader err = new BufferedReader(new InputStreamReader(convert.getErrorStream()));

		//String line = null;
		//while((line = err.readLine()) != null) System.err.println("> " + line);

		try { 
			int retval = convert.waitFor(); 
			if(retval != 0) {
				System.err.println("WARNING! Conversion error. Check that 'ndf2fits' is correct, and that");
				System.err.println("         the 'datapath' directory is writeable.");
				//if(outFile.exists()) outFile.delete();
				throw new IOException("SDF to FITS conversion error.");
			}
			return new File(outName);
		}
		catch(InterruptedException e) {
			System.err.println("Interrupted!");
			System.exit(1);
		}

		return null;
	}
	
}
