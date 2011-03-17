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
package util;

import java.io.*;

public class LogFile {
	String path;
	String format;
	int version = 0;
	int conflictPolicy = CONFLICT_DEFAULT;
	
	public LogFile(String path, String format, int conflictPolicy) throws IOException {
		this.path = path;
		this.format = format;
		this.conflictPolicy = conflictPolicy;
		check();
	}
	
	protected void check() throws IOException {
		File file = getFile();

		// If there is no prior log by that name, then simply create it and return....
		if(!file.exists()) {
			PrintWriter out = new PrintWriter(new FileOutputStream(file));
			out.println("# " + format);
			out.close();
			return;
		}
					
		// Otherwise check if the headers match...
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(getFile())));
		String header = in.readLine();
		if(header == null) throw new IllegalStateException("Empty log file header.");
		if(header.charAt(0) != '#') throw new IllegalStateException("Illegal log file header.");
		if(header.substring(2).equals(format)) return;
		
		// Conflict...
		if(conflictPolicy == CONFLICT_OVERWRITE) {
			// Delete the previous log file, and create a new one with a header...
			file.delete();
			check();
		}
		else if(conflictPolicy == CONFLICT_VERSION) {
			// Increment the version number until the conflict is avoided...
			version++;
			check();
		}
	}
	
	public String getFileName() {
		return path + getVersionExtension();
	}
	
	protected File getFile() {
		return new File(getFileName());
	}

	
	protected String getVersionExtension() {
		return version == 0 ? "" : "." + version; 
	}
	
	public void delete() {
		File file = getFile();
		if(file.exists()) file.delete();
	}
	
	public void deleteAll() {
		
	}
	
	public PrintStream getPrintStream() throws IOException {
		return new PrintStream(new BufferedOutputStream(new FileOutputStream(getFile(), true)));
	}
	
	
	public void add(String entry) throws IOException {
		PrintStream out = getPrintStream();
		out.println(entry);
		out.close();
	}
	
	
	public final static int CONFLICT_OVERWRITE = 0;
	public final static int CONFLICT_VERSION = 1;
	public final static int CONFLICT_DEFAULT = CONFLICT_VERSION;
}
