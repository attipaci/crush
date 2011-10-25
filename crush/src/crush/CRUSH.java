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
// Copyright (c) 2007,2008,2009,2010 Attila Kovacs

package crush;

import java.io.*;
import java.net.*;
import java.util.*;

import util.*;
import util.text.VersionInformation;
import nom.tam.fits.*;
import nom.tam.util.*;

/**
 * 
 * @author Attila Kovacs
 * @version 2.11-a1
 * 
 */
public class CRUSH extends Configurator {
	private static String version = "2.11-a1";
	private static String revision = "devel.3";
	public static String workPath = ".";
	public static String home = ".";
	public static boolean debug = false;
	
	public Instrument<?> instrument;
	public SourceModel<?,?> source;
	public Vector<Scan<?,?>> scans = new Vector<Scan<?,?>>();
	public String commandLine;
	
	public static int maxThreads = 1;
	
	static { 
		Locale.setDefault(Locale.US); 
		Header.setLongStringsEnabled(true);
	}
	
	public static void main(String[] args) {
		info();
			
		if(args.length == 0) {
			usage();
			checkJavaVM(0);
			System.exit(0);			
		}
		
		if(args[0].equalsIgnoreCase("-help")) {
			help();
			System.exit(0);	
		}
		
		checkJavaVM(5);
		
		home = System.getenv("CRUSH");
		if(home == null) home = ".";
		
		CRUSH crush = new CRUSH(args[0]);
		crush.init(args);
		
		try { crush.reduce(); }
		catch(InterruptedException e) {}
	}

	public CRUSH(String instrumentName) {
		checkForUpdates();
	
		instrument = Instrument.forName(instrumentName.toLowerCase());
		
		if(instrument == null) {
			System.err.println("Warning! Unknown instrument " + instrumentName);
			System.exit(1);
		}
		
		System.err.println("Instrument is " + instrument.name.toUpperCase());
		instrument.options = this;
	}
	
	public void init(String[] args) {
		readConfig("default.cfg");
		commandLine = args[0];
		
		for(int i=1; i<args.length; i++) if(args[i].length() > 0) {
			commandLine += " " + args[i]; 
			
			if(args[i].charAt(0) == '-') {
				String option = args[i].substring(1);
				parse(option);
				if(option.equals("debug")) debug=true;
			}
			else {
				if(!instrument.initialized) instrument.initialize();
				read(args[i]);
			}
		}	

		validate();
	}
	

	@Override
	public void readConfig(String fileName) {
		String userConfPath = System.getProperty("user.home") + File.separator + ".crush2"+ File.separator;
		boolean found = false;
		
		try { 
			super.readConfig(CRUSH.home + File.separator + fileName); 
			found = true;
		}
		catch(IOException e) {}
		
		try { 
			super.readConfig(userConfPath + fileName); 
			found = true;
		}
		catch(IOException e) {}
		
		try { 
			super.readConfig(instrument.getDefaultConfigPath() + fileName); 
			found = true;
		}
		catch(IOException e) { }
		
		// read the instrument overriderrs (if any).
		try { 
			super.readConfig(userConfPath + instrument.name + File.separator + fileName); 
			found = true;
		}
		catch(IOException e) {}
		
		// If no matching config was found in any of the standard locations, then try it as an absolute
		// config path...
		if(!found) {
			// read the instrument overriderrs (if any).
			try { super.readConfig(fileName); }
			catch(IOException e) {}
			
		}
	}
	
	public void validate() {			
		if(scans.size() == 0) {
			System.err.println("No scans to reduce. Exiting.");
			System.exit(1);
		}
				
		try { instrument.validate(scans); }
		catch(Error e) {
			System.err.println("ERROR! " + e.getMessage());
			System.exit(1);
		}
		catch(Exception e) {
			System.err.println("WARNING! " + e.getMessage());
		}
		
		// Keep only the non-specific global options here...
		for(Scan<?,?> scan : scans) instrument.options.intersect(scan.instrument.options); 		
		for(int i=0; i<scans.size(); i++) if(scans.get(i).isEmpty()) scans.remove(i--);
		
		//Collections.sort(scans);
		
		update();
		System.err.println(" Will use " + Math.min(scans.size(), maxThreads) + " CPU core(s).");
	}
	
	
	public void update() {
		maxThreads = Runtime.getRuntime().availableProcessors();
		if(isConfigured("reservecpus")) maxThreads -= get("reservecpus").getInt();
		maxThreads = Math.max(maxThreads, 1);
		
		if(containsKey("outpath")) workPath = Util.getSystemPath(get("outpath").getValue()); 
	}

	public void read(String scanID) {
		StringTokenizer list = new StringTokenizer(scanID, "; \t");
		update();
		
		if(list.countTokens() > 1) {
			while(list.hasMoreTokens()) read(list.nextToken());
		}
		else { 
			try {
				Scan<?,?> scan = (Scan<?,?>) instrument.getScanInstance();
				if(isConfigured("obslog")) {
					scan.read(scanID, false);
					scan.writeLog(get("obslog"),  workPath + File.separator + instrument.name + ".obs.log");
				}
				else { 
					scan.read(scanID, true);
					if(scan.size() == 0) System.err.println(" WARNING! Scan " + scan.getID() + " contains no valid data. Skipping.");
					else if(isConfigured("subscans.split")) scans.addAll(scan.split());
					else scans.add(scan);	
					System.gc();		
				}
				System.err.println();
			}
			catch(OutOfMemoryError e) {
				System.err.println("ERROR! Ran of of memory while reading scan.");
				System.err.println();
				System.err.println("   * Increase the amount of memory available to crush, by editing the '-Xmx'");
				System.err.println("     option to Java in 'wrapper.sh' (or 'wrapper.bat' for Windows).");
				System.err.println();
				System.err.println("   * If using 64-bit Unix OS and Java, you can also add the '-d64' option to");
				System.err.println("     allow Java to access over 2GB.");
				System.err.println();
				System.err.println("   * Try reduce scans in smaller chunks. You can then use 'coadd' to combine");
				System.err.println("     the maps post reduction. Note: it is always preferable to try reduce all");
				System.err.println("     scans together, if there is a way to fit them into memory.");
			    System.err.println();
			
			    System.exit(1);				
			}
			catch(FileNotFoundException e) { 
				// If it has '-', try to see if it can be read as a range...
				if(scanID.contains("-")) {
					try { 
						StringTokenizer range = new StringTokenizer(scanID, "-");
						int from = Integer.parseInt(range.nextToken());
						int to = Integer.parseInt(range.nextToken());
						for(int no = from; no <= to; no++) read(no + "");
					}
					catch(Exception parseError) {
						System.err.println(" ERROR! " + parseError.getMessage()); 
						if(debug) e.printStackTrace();
					}
				}
				else {
					System.err.println("\n ERROR! " + e.getMessage() + "\n");
					if(debug) e.printStackTrace();
				}
			}
			catch(IOException e) { 
				System.err.println(" ERROR! " + e.getMessage());
				if(debug) e.printStackTrace();
			}
			catch(Exception e) { e.printStackTrace(); }

		}	
	}
	

	public void reduce() throws InterruptedException {	
		int rounds = 0;
	
		System.out.println();
		
		source = ((Instrument<?>) instrument.copy()).getSourceModelInstance();
		
		if(source != null) {
			source.commandLine = commandLine;
			source.setOptions(this);
			source.createFrom(scans);
		}
	
		System.err.println();
		
		setObjectOptions(source.getSourceName());
			
		if(isConfigured("bright")) System.out.println(" Bright source reduction.");
		else if(isConfigured("faint")) System.out.println(" Faint source reduction.");
		else if(isConfigured("deep")) System.out.println(" Deep source reduction.");
		else System.out.println(" Default reduction.");
	
		if(isConfigured("extended")) System.out.println(" Assuming extended source(s).");
		
		System.out.println(" Assuming " + Util.f1.format(instrument.getSourceSize()/instrument.getDefaultSizeUnit()) + " " + instrument.getDefaultSizeName() + " sized source(s).");
		
	
		
		if(isConfigured("rounds")) rounds = get("rounds").getInt();
		
		for(int iteration=1; iteration<=rounds; iteration++) {
			System.err.println();
			System.err.println(" Round " + iteration + ": ");	

			setIteration(iteration, rounds);	
			
			for(Scan<?,?> scan : scans) {
				scan.instrument.options.setIteration(iteration, rounds);	
				for(Integration<?,?> integration : scan) if(integration.instrument != scan.instrument)  
				integration.instrument.options.setIteration(iteration, rounds);	
			}
			
			iterate();	
		}

		System.err.println();
		
		
		if(source != null) {
			try { source.write(workPath); }
			catch(Exception e) { e.printStackTrace(); }
 		}
		
		for(Scan<?,?> scan : scans) scan.writeProducts();	
		
		System.err.println();
	}
	
	public synchronized void iterate() throws InterruptedException {
		List<String> ordering = get("ordering").getLowerCaseList();
		ArrayList<String> tasks = new ArrayList<String>();
		
		for(String task : ordering) {
			tasks.add(task);
			if(solveSource()) if(task.startsWith("source"))  {
				iterate(tasks);
				tasks.clear();
			}
		}
		
		if(!tasks.isEmpty()) iterate(tasks);
		
		System.err.println();
	}
	
	public synchronized void iterate(List<String> tasks) throws InterruptedException {
		while(!queue.isEmpty()) wait();
		
		ArrayList<Pipeline> threads = new ArrayList<Pipeline>();
		System.err.println();
			
		threads.clear();	
		
		for(int i=0; i<maxThreads; i++) {
			Pipeline thread = new Pipeline(this);
			thread.setOrdering(tasks);
			threads.add(thread);
		}
		
		for(int i=0; i<scans.size(); i++) threads.get(i % maxThreads).addScan(scans.get(i));
		
		for(int i=1; i<maxThreads && i<threads.size();) {
			if(threads.get(i).scans.size() == 0) threads.remove(i);
			else i++;
		}

		
		if(solveSource()) if(tasks.contains("source")) source.reset();
				
		for(Pipeline thread : threads) thread.start();
	
		summarize();
		
		if(solveSource()) if(tasks.contains("source")) {
			System.err.print("  [Source] ");
			source.sync();
		}
		
			
		if(isConfigured("whiten")) if(get("whiten").isConfigured("once")) purge("whiten");
		
		System.gc();
	}
	
	public void setObjectOptions(String sourceName) {
		//System.err.println(">>> Setting global options for " + sourceName);
		sourceName = sourceName.toLowerCase();
		
		if(!containsKey("object")) return;

		Hashtable<String, Vector<String>> settings = get("object").conditionals;
		for(String spec : settings.keySet()) if(sourceName.startsWith(spec)) 
			parse(settings.get(spec));
	
	}
	
	
	public boolean solveSource() {
		if(source == null) return false;
		return isConfigured("source");
	}
	
	Vector<Integration<?, ?>> queue = new Vector<Integration<?, ?>>();
	
	public synchronized void checkout(Integration<?,?> integration) {
		queue.remove(integration);
		notifyAll();
	}
	
	public synchronized void summarize() throws InterruptedException {
		// Go in order.
		// If next one dissappears from queue then print summary
		// else wait();
		
		for(Scan<?, ?> scan : scans) for(Integration<?,?> integration : scan) {
			while(queue.contains(integration)) wait();
			summarize(integration);
			notifyAll();
		}	
	}

	
	public void summarize(Integration<?,?> integration) {
		System.err.print("  [" + integration.getDisplayID() + "] ");
		System.err.println(integration.comments);
		integration.comments = new String();
	}	


	public static void info() {
		String info = "\n" +
			"  ----------------------------------------------------------------------\n" +
			"  crush -- Reduction and imaging tool for bolometer arrays.\n" +
			"           Version " + getFullVersion() + "\n" + 
			"           http://www.submm.caltech.edu/~sharc/crush\n" +
			"           Copyright (C)2011 Attila Kovacs <kovacs[AT]astro.umn.edu>\n" +
			"  ----------------------------------------------------------------------\n";	
		System.err.println(info);
	}

	public static void usage() {
		String info = "  Usage: crush <instrument> [options] <scanslist> [[options] <scanlist> ...]\n" +
			"\n" +
			"    <instrument>    'sharc2', 'laboca', 'saboca', 'aszca', 'p-artemis',\n" +
			"                    'polka', 'gismo' (or 'scuba2').\n" +
			"    [options]       various configuration options. See README for details.\n" +
			"    <scanlist>      A list of scan numbers (or names) to reduce. Can mix\n" +
			"                    file names, individual scan numbers, and ranges. E.g.\n" +
			"                       10628-10633 11043 myscan.fits\n" +
			"\n" +
			"   Try 'crush <instrument> -poll' for a list of current settings.\n" +
			"   or 'crush -help' for a brief list of commonly used options.\n"; 
		
		System.out.println(info);
	}
	
	// TODO Update most common options
	public static void help() {
		String info = 
			" Some commonly used options. For full and detailed description of all options.\n" +
			" please consult the GLOSSARY.\n\n" +
			"   Location of raw data:\n" +
			"     -datapath=    Specify the path to the raw data.\n" +
			"     -project=     Specify the project ID (APEX only) in upper case.\n" +
			"\n" +
			"   Optimize reduction by source type:\n" +
			"     -bright       Reduce bright sources (S/N > 1000).\n" +
			"     -faint        Use with faint sources (S/N < 10).\n" +
			"     -deep         Use with deep fields (point sources).\n" +
			"     -extended     Assume extended structures (>= FOV).\n" +
			"     -point        Reduced pointing scans and suggest pointing corrections.\n" +
			"     -skydip       Reduce skydips to obtain in-band tau.\n" +
			"\n" +
			"   Options for the output map:\n" +
			"     -outpath=     Specify the directory where output files will go.\n" +
			"     -name=        Specify the output FITS map file name (rel. to outpath).\n" +
			"     -projection=  The spherical projection to use (e.g. SIN, TAN, SFL...)\n" +
			"     -grid=        The map pixelization (arcsec).\n" +
			"     -altaz        Reduce in horizontal coordinates (e.g. for pointing).\n" +
			"\n" +
			"   Commonly used options for scans:\n" +
			"     -tau=         Specify a zenith tau to use.\n" +
			"     -scale=       Apply a calibration factor to the scan(s).\n" +
			"     -pointing=    x,y pointing corrections in arcsec.\n" +
			"\n" +
			"   Alternative reduction modes:\n" +
			"     -point        Reduced pointing scans and suggest pointing corrections.\n" +
			"     -skydip       Reduce skydips to obtain in-band tau.\n" +
			"     -beammap      Derive pixel position data from beam maps.\n" +
			"     -split        Indicate that the scans are part of a larger dataset.\n" +
			"\n" +
			"   Other useful options:\n" +
			"     -forget=      Comma separated list of options to unset.\n" +
			"     -blacklist=   Comma separated list of options to ignore.\n" +
			"     -config=      Load configuration file.\n" +
			"     -poll         Poll the currently set options.\n" +
			"\n";
		
		System.out.println(info);
	}
	
	public static String getReleaseVersion() {       
		try {
			URL versionURL = new URL("http://www.submm.caltech.edu/~sharc/crush/v2/release.version");
			URLConnection connection = versionURL.openConnection();
			try {
				connection.setConnectTimeout(3000);
				connection.setReadTimeout(3000);
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				return in.readLine();
			} 
			catch(IOException e) {
				System.err.println("WARNING! Timed out while awaiting version update information.");
			}
		}
		catch(MalformedURLException e) { e.printStackTrace(); }
		catch(IOException e) {
			System.err.println("WARNING! No connection to version update server.");
		}

		return null;
	}
	  
	
	public static void checkForUpdates() {	
		String releaseVersion = getReleaseVersion();
		if(releaseVersion == null) return;
		
		VersionInformation release = new VersionInformation(releaseVersion);
		if(release.compareTo(new VersionInformation(version)) <= 0) return; 

		for(int i=0; i<8; i++) System.err.print("**********");
		System.err.println();
		
		System.err.println("  A NEW CRUSH-2 RELEASE IS NOW AVAILABLE FOR DOWNLOAD!!! "); 
		System.err.println();
		System.err.println("  Version: " + releaseVersion);
		System.err.println();
		System.err.println("  Get it from:  www.submm.caltech.edu/~sharc/crush");
		System.err.println();
		System.err.println("  You should always update to the latest release to take advantage of critical");
		System.err.println("  bug fixes, improvements and new features.");
		
		for(int i=0; i<8; i++) System.err.print("**********");
		System.err.println();
		
		System.err.println();
		countdown(5);	
		System.err.println();
	}
	
	public static void checkJavaVM(int countdown) {
		String name = System.getProperty("java.vm.name");
		if(name.startsWith("GNU") | name.contains("libgcj")) {
			for(int i=0; i<8; i++) System.err.print("**********");
			System.err.println();
			System.err.println("WARNING! You appear to be running CRUSH with GNU Java (libgcj).");
			System.err.println("         The GNU Java virtual machine is rather buggy and is known for");
			System.err.println("         producing unexpected errors during the reduction.");
			System.err.println("         It is highly recommended that you install and use a more reliable");
			System.err.println("         Java Runtime Environment (JRE).");
			System.err.println();
			System.err.println("         Please check for available Java packages for your system or see");
			System.err.println("         http://www.submm.caltech.edu/~sharc/crush/download.html");
			System.err.println("         for possible Java downloads.");
			System.err.println();
			System.err.println("         If you already have alternative Java installations on your system");
			System.err.println("         you can edit the 'JAVA' variable in 'wrapper.sh', inside the CRUSH");
			System.err.println("         distribution directory to point to the desired java executable");
			for(int i=0; i<8; i++) System.err.print("**********");
			System.err.println();
			System.err.println();
			
			if(countdown > 0) {
				System.err.println("         You may ignore this warning and proceed at your own risk shortly...");
				System.err.println();
			
				countdown(countdown);
				System.err.println();
			}
		}
		
	}
	
	public static void countdown(int seconds) {
		for(int i=seconds; i>0; i--) {
			System.err.print("\rWill continue in " + i + " seconds.");
			try { Thread.sleep(1000); }
			catch(InterruptedException e) {};
		}
		System.err.println("\rContinuing...                          ");
	}

	public static String getFullVersion() {
		if(revision == null) return version;
		if(revision.length() == 0) return version;
		return version + " (" + revision + ")";
	}
	
	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException, FitsException {
		// Add the system descriptors...
		
		cursor.add(new HeaderCard("CRUSHVER", getFullVersion(), "CRUSH version information."));
		
		cursor.add(new HeaderCard("JAVA", Util.getProperty("java.vendor"), "Java vendor name."));
		cursor.add(new HeaderCard("JAVAVER", Util.getProperty("java.version"), "The Java version."));
		cursor.add(new HeaderCard("JAVAHOME", Util.getProperty("java.home"), "Java location."));
		cursor.add(new HeaderCard("JRE", Util.getProperty("java.runtime.name"), "Java Runtime Environment."));
		cursor.add(new HeaderCard("JREVER", Util.getProperty("java.runtime.version"), "JRE version."));
		cursor.add(new HeaderCard("JVM", Util.getProperty("java.vm.name"), "Java Virtual Machine."));
		cursor.add(new HeaderCard("JVMVER", Util.getProperty("java.vm.version"), "JVM version."));
		
		cursor.add(new HeaderCard("OS", Util.getProperty("os.name"), "Operation System name."));
		cursor.add(new HeaderCard("OSVER", Util.getProperty("os.version"), "OS version."));
		cursor.add(new HeaderCard("OSARCH", Util.getProperty("os.arch"), "OS architecture."));
		
		cursor.add(new HeaderCard("CPUS", Runtime.getRuntime().availableProcessors(), "Number of CPU cores/threads available."));
		cursor.add(new HeaderCard("DMODEL", Util.getProperty("sun.arch.data.model"), "Bits in data model."));
		cursor.add(new HeaderCard("CPUEND", Util.getProperty("sun.cpu.endian"), "CPU Endianness."));
		cursor.add(new HeaderCard("MAXMEM", Runtime.getRuntime().maxMemory() / (1024 * 1024), "MB of available memory."));
			
		cursor.add(new HeaderCard("COUNTRY", Util.getProperty("user.country"), "The user country."));
		cursor.add(new HeaderCard("LANGUAGE", Util.getProperty("user.language"), "The user language."));
		
		super.editHeader(cursor);
	}
	
	
	/*
	public static void printMessages() {
		try {
			URL messageURL = new URL("http://www.submm.caltech.edu/~sharc/crush/v2/messages");
			URLConnection connection = messageURL.openConnection();
			try {
				connection.setConnectTimeout(3000);
				connection.setReadTimeout(3000);
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line = "";

				System.err.println();
				System.err.println("[www] Message from www.submm.caltech.edu/~sharc/crush/v2: ");
				while((line = in.readLine()) != null) System.err.println("[CRUSH] " + line);
				System.err.println();
			}
			catch(IOException e) {
				System.err.println("WARNING! Timed out while awaiting messages from CRUSH server.");
			}
		} 
		catch(MalformedURLException e) { e.printStackTrace(); }
		catch(IOException e) {
			System.err.println("WARNING! No connection to messages server.");
		}
	}
	*/
}

