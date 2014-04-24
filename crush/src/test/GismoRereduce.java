/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package test;

import java.io.*;
import java.util.*;

public class GismoRereduce {
	String crush = "./crush";
	String logFile = "../images/rereduce.log";
	String options = "-point -log -log.file=$LOGFILE";
	
	public static void main(String[] args) {
		GismoRereduce reducer = new GismoRereduce();
		//System.err.println("# Rereduce script...");
		double minObsMins = args.length > 1 ? Double.parseDouble(args[1]) : 0.0;
		
		try { reducer.rereduce(args[0], minObsMins); }
		catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void rereduce(String logFileName, double minObsMins) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(logFileName)));
		
		System.out.println("#!/bin/bash");
		System.out.println();
		System.out.println("CRUSH=\"" + crush + "\"");
		System.out.println("LOGFILE=\"" + logFile + "\"");
		System.out.println("OPTIONS=\"" + options + "\"");
		System.out.println();
		System.out.println();
		
		String line = null;
		in.readLine();
		
		boolean firstLine = true;
		
		while((line = in.readLine()) != null) if(line.length() > 0) {
			if(line.charAt(0) != '#') {
				StringTokenizer tokens = new StringTokenizer(line);
				if(tokens.countTokens() > 1) {	
					String id = tokens.nextToken();
					String sourceName = tokens.nextToken();

					tokens.nextToken(); // project
					tokens.nextToken(); // UT
					
					double obsmins = Double.parseDouble(tokens.nextToken());
					if(obsmins >= minObsMins) {
					
						StringTokenizer idbits = new StringTokenizer(id, ".");
						String date = idbits.nextToken();
						String scanNo = idbits.nextToken();
						
						String cmdLine = "$CRUSH gismo $OPTIONS -object=" + sourceName + " -date=" + date + " " + scanNo;
						//Runtime runtime = Runtime.getRuntime();

						System.out.println(cmdLine);
						//runtime.exec(cmdLine);
					}
				
				}
			}
			else System.out.println("echo \"" + line + "\" "+ (firstLine ? ">" : ">>") + " $LOGFILE");
			firstLine = false;
		}
		
		in.close();
	}
	
	
	
}
