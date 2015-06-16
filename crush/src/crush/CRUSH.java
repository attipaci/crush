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
package crush;

import java.io.*;
import java.net.*;
import java.util.*;

import kovacs.astro.LeapSeconds;
import kovacs.fits.FitsExtras;
import kovacs.text.VersionString;
import kovacs.util.*;
import nom.tam.fits.*;
import nom.tam.util.*;

/**
 * 
 * @author Attila Kovacs
 * @version 2.23-b5
 * 
 */
public class CRUSH extends Configurator {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6284421525275783456L;
	
	private static String version = "2.23-1";
	private static String revision = "";
	public static String workPath = ".";
	public static String home = ".";
	public static boolean debug = false;
	
	public Instrument<?> instrument;
	public Vector<Scan<?,?>> scans = new Vector<Scan<?,?>>();
	public SourceModel source;
	public String commandLine;
	
	public static int maxThreads = 1;
	
	private int configDepth = 0;	// Used for 'nested' output of invoked configurations.
	
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
			help(null);
			System.exit(0);	
		}
		
		checkJavaVM(5);
		checkForUpdates();
		
		home = System.getenv("CRUSH");
		if(home == null) home = ".";
		
		LeapSeconds.dataFile = home + File.separator + "data" + File.separator + "leap-seconds.list";
		
		CRUSH crush = new CRUSH(args[0]);
		crush.init(args);
		
		try { crush.reduce(); }
		catch(Exception e) { e.printStackTrace(); }
	}

	public CRUSH(String instrumentName) {
		instrument = Instrument.forName(instrumentName.toLowerCase());
		instrument.setOptions(this);
		
		if(instrument == null) {
			System.err.println("Warning! Unknown instrument " + instrumentName);
			System.exit(1);
		}
		
		System.err.println("Instrument is " + instrument.getName().toUpperCase());
	}
	
	public boolean hasOption(String name) {
		return isConfigured(name);
	}
	
	public Configurator option(String name) { return get(name); }
	
	public void init(String[] args) {	
		readConfig("default.cfg");
		commandLine = args[0];
		
		for(int i=1; i<args.length; i++) if(args[i].length() > 0) {
			commandLine += " " + args[i]; 
		
			if(args[i].charAt(0) == '-') parse(args[i].substring(1));
			else read(args[i]);
		}	
		
		validate();
	}
	
	@Override
	public void process(String key, String value) {
		if(key.equals("debug")) debug=true;
		else if(key.equals("help")) help(instrument);
		else if(key.equals("list.divisions")) instrument.printCorrelatedModalities(System.err);
		else if(key.equals("list.response")) instrument.printResponseModalities(System.err);
		else super.process(key, value);
	}
	

	@Override
	public void readConfig(String fileName) {
		String userConfPath = System.getProperty("user.home") + File.separator + ".crush2"+ File.separator;
		boolean found = false;
		
		if(configDepth > 0) {
			for(int i=configDepth; --i >= 0; ) System.err.print("  ");
			System.err.print("<-- ");
		}
		
		configDepth++;
		
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
			super.readConfig(instrument.getConfigPath() + fileName); 
			found = true;
		}
		catch(IOException e) { }
		
		// read the instrument overriderrs (if any).
		try { 
			super.readConfig(userConfPath + instrument.getName() + File.separator + fileName); 
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
		
		configDepth--;
	}
	
	@Override
	public String getProperty(String name) {
		if(name.equals("instrument")) return instrument.getName();
		else if(name.equals("version")) return version;
		else if(name.equals("fullversion")) return getFullVersion();
		else return super.getProperty(name);
	}
	
	public void validate() {			
		if(scans.size() == 0) {
			System.err.println("No scans to reduce. Exiting.");
			System.exit(1);
		}
		
		// Make the global options derive from those of the first scan...
		instrument = (Instrument<?>) scans.get(0).instrument.copy();
			
		try { instrument.validate(scans); }
		catch(Error e) {
			System.err.println("ERROR! " + e.getMessage());
			System.exit(1);
		}
		catch(Exception e) {
			System.err.println("WARNING! " + e.getMessage());
		}
		
		
		// Keep only the non-specific global options here...
		for(Scan<?,?> scan : scans) instrument.getOptions().intersect(scan.instrument.getOptions()); 		
		for(int i=scans.size(); --i >=0; ) if(scans.get(i).isEmpty()) scans.remove(i);
			
		update();
		
		int threads = Math.min(scans.size(), maxThreads);
		
		System.err.println(" Will use " + threads + " CPU core(s).");
	}
	
	
	public void update() {
		maxThreads = Runtime.getRuntime().availableProcessors();
		if(isConfigured("idle")) {
			String spec = get("idle").getValue();
			if(spec.charAt(spec.length() - 1) == '%') 
					maxThreads -= (int)Math.round(0.01 * Double.parseDouble(spec.substring(0, spec.length()-1)) * maxThreads);
			else 
					maxThreads -= get("idle").getInt();
		}
		maxThreads = Math.max(1, maxThreads);
		
		if(containsKey("outpath")) setOutpath();
	}
	
	public void setOutpath() {
		workPath = get("outpath").getPath();
		File workFolder = new File(workPath);	
		if(workFolder.exists()) return;	
		
		System.err.println(" WARNING! The specified output folder does not exists:");
		System.err.println("          '" + workPath + "'");

		if(!hasOption("outpath.create")) {
			System.err.println(" ERROR! Invalid output path. Try:");
			System.err.println();
			System.err.println("        * change 'outpath' to an existing directory, or");
			System.err.println("        * set 'outpath.create' to create the path automatically.");
			System.err.println();
			System.exit(1);
		}

		System.err.println(" -------> Creating output folder.");	
		try {
			if(!workFolder.mkdirs()) {
				System.err.println(" ERROR! Output path could not be created: unknown error.");
				System.err.println("        Try change 'outpath'.");
				System.exit(1);
			}
		}
		catch(SecurityException e) {
			System.err.println(" ERROR! Output path could not be created: " + e.getMessage());
			System.err.println("        Try change 'outpath'.");
			System.exit(1);
		}
	}

	public void read(String scanID) {
		StringTokenizer list = new StringTokenizer(scanID, "; \t");
		update();
		
		if(list.countTokens() > 1) {
			while(list.hasMoreTokens()) read(list.nextToken());
		}
		else { 
			try {
				Scan<?,?> scan = null;
				if(isConfigured("obslog")) {
					scan = instrument.readScan(scanID, false);
					scan.writeLog(get("obslog"),  workPath + File.separator + instrument.getName() + ".obs.log");
				}
				else { 
					scan = instrument.readScan(scanID, true);
					if(scan.size() == 0) System.err.println(" WARNING! Scan " + scan.getID() + " contains no valid data. Skipping.");
					else if(isConfigured("subscans.split")) scans.addAll(scan.split());
					
					/*
					else if(isConfigured("segment")) {
						double segmentTime = 60.0 * Unit.s;
						try { segmentTime = option("segment").getDouble() * Unit.s; }
						catch(Exception e) {}
						scans.addAll(scan.segmentTo(segmentTime));
					}
					*/
					
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
			catch(Exception e) {
				System.err.println(" ERROR! " + e.getMessage());
				if(debug) e.printStackTrace(); 
				else System.err.println("        (use '-debug' to obtain additional information on this error.)");
			}

		}	
	}
	

	public void reduce() throws Exception {	
		int rounds = 0;
	
		System.out.println();
		
		// TODO Using the global options (intersect of scan options) instead of the first scan's
		// for the source does not work properly (clipping...)
		source = scans.get(0).instrument.getSourceModelInstance();
			
		if(source != null) {
			source.commandLine = commandLine;
			source.createFrom(scans);
		}
	
		System.err.println();
		
		setObjectOptions(source.getSourceName());
			
		if(isConfigured("bright")) System.out.println(" Bright source reduction.");
		else if(isConfigured("faint")) System.out.println(" Faint source reduction.");
		else if(isConfigured("deep")) System.out.println(" Deep source reduction.");
		else System.out.println(" Default reduction.");
	
		if(isConfigured("extended")) System.out.println(" Assuming extended source(s).");
		
		System.out.println(" Assuming " + Util.f1.format(instrument.getSourceSize()/instrument.getSizeUnitValue()) + " " + instrument.getSizeName() + " sized source(s).");
		
		
		if(isConfigured("rounds")) rounds = get("rounds").getInt();
		
		for(int iteration=1; iteration<=rounds; iteration++) {
			System.err.println();
			System.err.println(" Round " + iteration + ": ");	

			setIteration(iteration, rounds);	
			
			for(Scan<?,?> scan : scans) {
				scan.instrument.getOptions().setIteration(iteration, rounds);	
				for(Integration<?,?> integration : scan) if(integration.instrument != scan.instrument)  
				integration.instrument.getOptions().setIteration(iteration, rounds);	
			}
			
			iterate();	
		}

		System.err.println();
		
		
		if(source != null) {
			source.suggestions();
			
			if(source.isValid()) {
				try { source.write(workPath); }
				catch(Exception e) { e.printStackTrace(); }
			}
			else System.err.println(" WARNING! The reduction did not result in a source model.");
		}
		
		
		for(Scan<?,?> scan : scans) scan.writeProducts();	
		
		System.err.println();
	}
	
	public synchronized void iterate() throws Exception {
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
	
	public synchronized void iterate(List<String> tasks) throws Exception {
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
		
		if(solveSource()) if(tasks.contains("source")) {
			source.setParallel(CRUSH.maxThreads);
			source.reset(true);
		}
				
		for(Pipeline thread : threads) thread.start();
	
		summarize();
		
		if(solveSource()) if(tasks.contains("source")) {
			System.err.print("  [Source] ");
			source.setParallel(CRUSH.maxThreads);
			source.process(true);
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
			"  ----------------------------------------------------------------------------\n" +
			"  crush -- Reduction and imaging tool for astronomical cameras.\n" +
			"           Version: " + getFullVersion() + "\n" + 
			"           Utilities: " + Util.getFullVersion() + ", nom.tam.fits: " + Fits.version() + "\n" +
			"           http://www.submm.caltech.edu/~sharc/crush\n" +
			"           Copyright (C)2015 Attila Kovacs <attila[AT]caltech.edu>\n" +
			"  ----------------------------------------------------------------------------\n";	
		System.err.println(info);
	}

	public static void usage() {
		String info = "  Usage: crush <instrument> [options] <scanlist> [[options] <scanlist> ...]\n" +
			"\n" +
			"    <instrument>     'sharc', 'sharc2', 'laboca', 'saboca', 'aszca', 'polka'\n" +
			"                     'p-artemis', 'gismo', 'mako', 'mako2', 'hawc+'\n" + 
			"                     (or 'scuba2').\n" +
			"    [options]        Various configuration options. See README for details.\n" +
			"                     Global settings must precede scans on the argument list.\n" +
			"                     Each scan will use all options listed before it.\n" +
			"    <scanlist>       A list of scan numbers (or names) to reduce. Can mix\n" +
			"                     file names, individual scan numbers, and ranges. E.g.\n" +
			"                       10628-10633 11043 myscan.fits\n" +
			"\n" +
			"   Try 'crush <instrument> -poll' for a list of current settings.\n" +
			"   or 'crush -help' for a brief list of commonly used options.\n"; 
		
		System.out.println(info);
	}
	

	public static void help(Instrument<?> instrument) {
		String info = 
			" Some commonly used options. For full and detailed description of all options.\n" +
			" please consult the GLOSSARY.\n\n" +
			"   Location of input files:\n" +
			"     -datapath=     Specify the path to the raw data.\n" +
			(instrument != null ? instrument.getDataLocationHelp() : "") +
			"\n" +
			"   Optimize reduction by source type:\n" +
			"     -bright        Reduce bright sources (S/N > 1000).\n" +
			"     -faint         Use with faint sources (S/N < 10).\n" +
			"     -deep          Use with deep fields (point sources).\n" +
			"     -extended      Assume extended structures (>= FOV/2).\n" +
			"     -moving        Target is a moving object (e.g. planet, asteroid, or moon).\n"	+	
			"\n" +
			"   Options for the output map:\n" +
			"     -outpath=      Specify the directory where output files will go.\n" +
			"     -name=         Specify the output FITS map file name (rel. to outpath).\n" +
			"     -projection=   The spherical projection to use (e.g. SIN, TAN, SFL...)\n" +
			"     -grid=         The map pixelization (arcsec).\n" +
			"     -altaz         Reduce in horizontal coordinates (e.g. for pointing).\n" +
			"     -ecliptic      Reduce in Ecliptic coordinates.\n" +
			"     -galactic      Reduce in Galactic coordinates.\n" +
			"     -final:smooth= Smoothing in the final iteration, either as FWHM (arcsec)\n" +
			"                    or one of: 'minimal', 'halfbeam', '2/3beam, 'beam'\n" +
			"\n" +
			"   Commonly used options for scans:\n" +
			"     -tau=          Specify an in-band zenith tau, source ID, or interpolation\n" +
			"                    table to use. E.g.: '1.036', '225GHz', or '~/tau.dat'.\n" +
			"     -tau.<id>=     Specify a zenith tau value or interpolation table for <id>.\n" +
			"                    E.g. 'tau.225GHz=0.075'.\n" +
			"     -scale=        Apply a calibration correction factor to the scan(s).\n" +
			"     -pointing=     x,y pointing corrections in arcsec.\n" +
			(instrument != null ? instrument.getCommonHelp() : "") +
			"\n" +
			"   Alternative reduction modes:\n" +
			"     -point         Reduce pointing/calibration scans.\n" +
			"     -skydip        Reduce skydips to obtain in-band zenith opacity.\n" +
			"     -beammap       Derive pixel position data from beam maps.\n" +
			"     -split         Indicate that the scans are part of a larger dataset.\n" +
			"\n" +
			"   Other useful options:\n" +
			"     -show          Display the result (if possible) at the end.\n" +
			"     -forget=       Comma separated list of options to unset.\n" +
			"     -blacklist=    Comma separated list of options to ignore.\n" +
			"     -config=       Load configuration file.\n" +
			"     -poll          Poll the currently set options.\n" +
			"     -conditions    List all conditional settings.\n" +
			"\n";
		
		System.out.println(info);
	}
	
	public static String getReleaseVersion() {  
		String version = null;
		
		try {
			URL versionURL = new URL("http://www.submm.caltech.edu/~sharc/crush/v2/release.version");
			URLConnection connection = versionURL.openConnection();
			try {
				connection.setConnectTimeout(3000);
				connection.setReadTimeout(2000);
				connection.connect();
				
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				version = in.readLine();
				in.close();
			} 
			catch(IOException e) {
				if(e instanceof SocketTimeoutException) 
					System.err.println("WARNING! Timed out while awaiting version update information.");
				else 
					System.err.println("WARNING! Could not get version update information.");
				
				if(debug) e.printStackTrace();
			}
			
		}
		catch(MalformedURLException e) { e.printStackTrace(); }
		catch(IOException e) {
			System.err.println("WARNING! No connection to version update server.");
			if(debug) e.printStackTrace();
		}
		
		return version;
	}
	  
	
	public static void checkForUpdates() {	
		String releaseVersion = getReleaseVersion();	
		if(releaseVersion == null) return;
		
		VersionString release = new VersionString(releaseVersion);
		if(release.compareTo(new VersionString(version)) <= 0) return; 

		for(int i=0; i<8; i++) System.err.print("**********");
		System.err.println();
		
		System.err.println("  A NEW CRUSH-2 RELEASE IS NOW AVAILABLE FOR DOWNLOAD!!! "); 
		System.err.println();
		System.err.println("  Version: " + releaseVersion);
		System.err.println();
		System.err.println("  Get it from:  www.submm.caltech.edu/~sharc/crush");
		System.err.println();
		System.err.println("  You should always update to the latest release to take advantage of critical");
		System.err.println("  bug fixes, improvements, and new features.");
		
		for(int i=0; i<8; i++) System.err.print("**********");
		System.err.println();
		
		countdown(5);	
		
	}
	
	public static void checkJavaVM(int countdown) {
		String name = System.getProperty("java.vm.name");
		if(name.startsWith("GNU") | name.contains("libgcj")) {
			for(int i=0; i<8; i++) System.err.print("**********");
			System.err.println();
			System.err.println("WARNING! You appear to be running CRUSH with GNU Java (libgcj).");
			System.err.println("         The GNU Java virtual machine is rather slow and is known for");
			System.err.println("         producing unexpected errors during the reduction.");
			System.err.println("         It is highly recommended that you install and use a more reliable");
			System.err.println("         Java Runtime Environment (JRE).");
			System.err.println();
			System.err.println("         Please check for available Java packages for your system or see");
			System.err.println("         http://www.submm.caltech.edu/~sharc/crush/download.html");
			System.err.println("         for possible Java downloads.");
			System.err.println();
			System.err.println("         If you already have another Java installations on your system");
			System.err.println("         you can edit the 'JAVA' variable in 'wrapper.sh', inside the CRUSH");
			System.err.println("         distribution directory to point to the desired java executable");
			for(int i=0; i<8; i++) System.err.print("**********");
			System.err.println();
			System.err.println();
			
			if(countdown > 0) {
				System.err.println("         You may ignore this warning and proceed at your own risk shortly...");
				countdown(countdown);
			}
		}
		
	}
	
	public static void countdown(int seconds) {
		System.err.println();
		
		for(int i=seconds; i>0; i--) {
			System.err.print("\rWill continue in " + i + " seconds.");
			try { Thread.sleep(1000); }
			catch(InterruptedException e) {}
		}
		System.err.println("\rContinuing...                          ");
		System.err.println();
		
	}

	public static String getVersion() {
		return version;
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
		
		// Add the command-line reduction options
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
		cursor.add(new HeaderCard("COMMENT", " CRUSH command line arguments section", false));
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
			
				
		if(commandLine != null) {
			StringTokenizer args = new StringTokenizer(commandLine);
			cursor.add(new HeaderCard("ARGS", args.countTokens(), "The number of arguments passed from the command line."));
			int i=1;
			while(args.hasMoreTokens()) FitsExtras.addLongKey(cursor, "ARG" + (i++), args.nextToken(), "Command-line argument.");
		}
		
		
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
		cursor.add(new HeaderCard("COMMENT", " CRUSH VM/OS section", false));
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
	
		cursor.add(new HeaderCard("JAVA", Util.getProperty("java.vendor"), "Java vendor name."));
		cursor.add(new HeaderCard("JAVAVER", Util.getProperty("java.version"), "The Java version."));
		
		FitsExtras.addLongKey(cursor, "JAVAHOME", Util.getProperty("java.home"), "Java location.");
		cursor.add(new HeaderCard("JRE", Util.getProperty("java.runtime.name"), "Java Runtime Environment."));
		cursor.add(new HeaderCard("JREVER", Util.getProperty("java.runtime.version"), "JRE version."));
		cursor.add(new HeaderCard("JVM", Util.getProperty("java.vm.name"), "Java Virtual Machine."));
		cursor.add(new HeaderCard("JVMVER", Util.getProperty("java.vm.version"), "JVM version."));
		
		cursor.add(new HeaderCard("OS", Util.getProperty("os.name"), "Operation System name."));
		cursor.add(new HeaderCard("OSVER", Util.getProperty("os.version"), "OS version."));
		cursor.add(new HeaderCard("OSARCH", Util.getProperty("os.arch"), "OS architecture."));
		
		cursor.add(new HeaderCard("CPUS", Runtime.getRuntime().availableProcessors(), "Number of CPU cores/threads available."));
		cursor.add(new HeaderCard("DMBITS", Util.getProperty("sun.arch.data.model"), "Bits in data model."));
		cursor.add(new HeaderCard("CPENDIAN", Util.getProperty("sun.cpu.endian"), "CPU Endianness."));
		cursor.add(new HeaderCard("MAXMEM", Runtime.getRuntime().maxMemory() / (1024 * 1024), "MB of available memory."));
			
		cursor.add(new HeaderCard("COUNTRY", Util.getProperty("user.country"), "The user country."));
		cursor.add(new HeaderCard("LANGUAGE", Util.getProperty("user.language"), "The user language."));
		
		
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
		cursor.add(new HeaderCard("COMMENT", " CRUSH configuration section", false));
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
		
		super.editHeader(cursor);
		
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
		cursor.add(new HeaderCard("COMMENT", " End of CRUSH configuration sections", false));
		cursor.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
	}
	
	
	/*
	public static void printMessages() {
		try {
			URL messageURL = new URL("http://www.submm.caltech.edu/~sharc/crush/v2/messages");
			URLConnection connection = messageURL.openConnection();
			try {
				connection.setConnectTimeout(3000);
				connection.setReadTimeout(3000);
				connection.connect();
				
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line = "";

				System.err.println();
				System.err.println("[www] Message from www.submm.caltech.edu/~sharc/crush/v2: ");
				while((line = in.readLine()) != null) System.err.println("[CRUSH] " + line);
				System.err.println();
				
				in.close();
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

