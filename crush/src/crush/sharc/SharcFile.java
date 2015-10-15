/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sharc;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;

import crush.CRUSH;
import kovacs.io.VAXDataInputStream;
import kovacs.math.Vector2D;
import kovacs.util.Configurator;


public class SharcFile extends Hashtable<Integer, SharcScan> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 915274225920741701L;
	Sharc sharc;
	String fileName;
	String id;
	VAXDataInputStream in;
	
	static Hashtable<String, SharcFile> files = new Hashtable<String, SharcFile>();
	
	int instrumentID, scanType, fileSize, fileRevisionNumber;
	int day, year, firstScan, numberScans;
	static int paddingBytes = 160;
	
	Vector2D[] pixelOffsets = new Vector2D[Sharc.pixels];	// Pixel positions az/el
	
	public static void main(String[] args) {
		try {
			Sharc sharc = new Sharc();
			sharc.setOptions(new Configurator());
			
			System.out.println();
			SharcFile file = SharcFile.get(sharc, args[0]);

			System.out.println();
			file.get(1).printInfo(System.out);
		}
		catch(Exception e) { e.printStackTrace(); }
	}
	
	private SharcFile(Sharc instrument, String fileName) throws IOException {
		this.sharc = instrument;
		this.fileName = fileName;
		
		id = new File(fileName).getName();
		if(id.contains(".")) id = id.substring(0, id.lastIndexOf('.'));
		
		in = new VAXDataInputStream(new BufferedInputStream(new FileInputStream(fileName), bufferSize));
		readFully();
		System.err.println();
		
		files.put(fileName, this);
	}
	
	protected void readFully() throws IOException {
		readHeader();
		//printInfo(System.out);
		
		System.out.println();
		System.out.println(" [" + fileName + "]");
		System.out.println();
		
		try { for(;;) readScan(); }
		catch(EOFException e) {}
		catch(IOException e) { CRUSH.warning(e); }
	
		//listScans();
	}
	
	protected void readScan() throws IOException {
		
		SharcScan scan = new SharcScan(sharc, this);
	
		scan.readHeader(in, size()+1);
		System.err.println("  " + scan.toString());
		scan.readScanRow(in);	
		
		put(scan.getSerial(), scan);		
	}
	
	public void listScans() {
		for(int i=0; i<size(); i++) System.out.println("  " + get(i).toString());
	}
	
	
	protected void readHeader() throws IOException {
		// header 1
		instrumentID = in.readInt();
		scanType = in.readInt();
		fileSize = in.readInt();
		fileRevisionNumber = in.readInt();
		day = in.readInt();
		year = in.readInt();
		firstScan = in.readInt();
		numberScans = in.readInt();
		in.skip(paddingBytes);
		
		// header 2
		for(int i=0; i<Sharc.pixels; i++) pixelOffsets[i] = new Vector2D(in.readFloat(), in.readFloat());
		
	}
	
	public void printInfo(PrintStream out) {
		out.println("[" + fileName + "]");
		out.println("  instrument: " + instrumentID);
		out.println("  scanType: " + scanType);
		out.println("  fileSize: " + fileSize);
		out.println("  revision: " + fileRevisionNumber);
		out.println("  date: " + day + ", " + year);
		out.println("  firstScan: " + firstScan);
		out.println("  scans: " + numberScans);
		
		// These are the pixel offsets in arcsecs...
		//out.println("  offsets:");
		//for(int i=0; i<Sharc.pixels; i++) out.println("\t" + i + ": " + offsets[i].toString(Util.e3));
		
		out.println();
	}
	
	public static SharcFile get(Sharc sharc, String fileName) throws IOException {
		SharcFile file = files.get(fileName);
		return file == null ? new SharcFile(sharc, fileName) : file;
	}
	
	public static int bufferSize = 1<<20;	// 1 MB
}
