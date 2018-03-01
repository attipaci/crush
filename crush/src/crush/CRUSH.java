/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import jnum.Configurator;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroTime;
import jnum.astro.LeapSeconds;
import jnum.fits.FitsToolkit;
import jnum.parallel.ParallelTask;
import jnum.reporting.BasicMessaging;
import jnum.reporting.Broadcaster;
import jnum.reporting.ConsoleReporter;
import jnum.reporting.Reporter;
import jnum.text.VersionString;
import nom.tam.fits.*;
import nom.tam.util.Cursor;


/**
 * 
 * @author Attila Kovacs
 * @version 2.41
 * 
 */
public class CRUSH extends Configurator implements BasicMessaging {
    /**
     * 
     */
    private static final long serialVersionUID = 6284421525275783456L;

    private static String version = "2.41-2";
    private static String revision = "";

    public static String workPath = ".";
    public static String home = ".";
    public static boolean debug = false;

    public Instrument<?> instrument;
    public Vector<Scan<?,?>> scans = new Vector<Scan<?,?>>();
    public SourceModel source;
    public String commandLine;

    public static int maxThreads = 1;
    public static volatile ExecutorService executor, sourceExecutor;
    

    public int parallelScans = 1;
    public int parallelTasks = 1;

    private ArrayList<Pipeline> pipelines;
    private Vector<Integration<?, ?>> queue = new Vector<Integration<?, ?>>();

    private int configDepth = 0;	// Used for 'nested' output of invoked configurations.


    public static void main(String[] args) {
        info();

        Util.setReporter(broadcaster);

        if(args.length == 0) {
            usage();
            new CRUSH().checkJavaVM(0);
            System.exit(0);			
        }

        if(args[0].equalsIgnoreCase("-help")) {
            help(null);
            System.exit(0);	
        }

        home = System.getenv("CRUSH");
        if(home == null) home = ".";

        CRUSH crush = new CRUSH(args[0]);

        crush.checkJavaVM(5);     
        crush.checkForUpdates();	
        
        try { 
            crush.init(args);
            crush.reduce(); 
        }
        catch(Exception e) { crush.error(e); }

        // TODO should not be needed if background processes are all wrapped up...
        crush.exit(0);
    }

    private CRUSH() {
        Locale.setDefault(Locale.US);
        FitsFactory.setUseHierarch(true);
        FitsFactory.setLongStringsEnabled(true);
    }

    public CRUSH(String instrumentName) {
        this();

        instrument = Instrument.forName(instrumentName.toLowerCase());
        instrument.setOptions(this);

        if(instrument == null) {
            error("Unknown instrument " + instrumentName);
            System.exit(1);
        }

        info(this, "Instrument is " + instrument.getName().toUpperCase());
    }

    public boolean hasOption(String name) {
        return isConfigured(name);
    }

    public Configurator option(String name) { return get(name); }

    public void init(String[] args)throws Exception {	 
        readConfig("default.cfg");
          
        commandLine = args[0];

        for(int i=1; i<args.length; i++) if(args[i].length() > 0) {
            commandLine += " " + args[i]; 
            parseArgument(args[i]);
        }	

        validate();
    }
    
    private void parseArgument(String arg) {
        if(arg.charAt(0) == '-') parseSilent(arg.substring(1));
        else read(arg);
    }

    @Override
    public void process(String key, String value) {
        if(key.startsWith("env.[")) processEnvironmentOption(key.substring(5, key.indexOf("]")), value); 
        else if(key.startsWith("property.[")) processPropertyOption(key.substring(10, key.indexOf("]")), value); 
        else if(key.equals("debug")) {
            Util.debug = debug = true;
            consoleReporter.setLevel(ConsoleReporter.LEVEL_DEBUG);
            debug("java: " + Util.getProperty("java.vendor") + ": " + Util.getProperty("java.version"));
            debug("java-path: " + Util.getProperty("java.home"));
            debug("jre: " + Util.getProperty("java.runtime.name") + ": " + Util.getProperty("java.runtime.version"));
            debug("jvm: " + Util.getProperty("java.vm.name") + ": " + Util.getProperty("java.vm.version"));	        
        }
        else if(key.equals("help")) help(instrument);
        else if(key.equals("quiet")) consoleReporter.setLevel(ConsoleReporter.LEVEL_RESULT);
        else if(key.equals("veryquiet")) consoleReporter.setLevel(ConsoleReporter.LEVEL_STATUS);
        else if(key.equals("list.divisions")) instrument.printCorrelatedModalities(System.err);
        else if(key.equals("list.response")) instrument.printResponseModalities(System.err);
        else {
            try { super.process(key, value); }
            catch(LockedException e) {} // TODO
        }
    }


    public String getConfigPath() {
        return CRUSH.home + File.separator + "config";
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
            String path = getConfigPath() + File.separator + fileName;
            super.readConfig(path); 
            if(instrument != null) instrument.registerConfigFile(path);
            found = true;
        }
        catch(IOException e) {}

        try { 
            String path = userConfPath + fileName;
            super.readConfig(path); 
            if(instrument != null) instrument.registerConfigFile(path);
            found = true;
        }
        catch(IOException e) {}

        try { 
            String path = instrument.getConfigPath() + fileName;
            super.readConfig(path); 
            if(instrument != null) instrument.registerConfigFile(path);
            found = true;
        }
        catch(IOException e) { }

        // read the instrument overriderrs (if any).
        try { 
            String path = userConfPath + instrument.getName() + File.separator + fileName;
            super.readConfig(path); 
            if(instrument != null) instrument.registerConfigFile(path);
            found = true;
        }
        catch(IOException e) {}

        // If no matching config was found in any of the standard locations, then try it as an absolute
        // config path...
        if(!found) {
            // read the instrument overriderrs (if any).
            try { 
                super.readConfig(fileName); 
                if(instrument != null) instrument.registerConfigFile(fileName);
            }
            catch(IOException e) {}

        }

        configDepth--;
    }

    @Override
    public String getProperty(String name) {
        if(name.equals("instrument")) return instrument.getName();
        if(name.equals("configpath")) return getConfigPath();
        else if(name.equals("version")) return version;
        else if(name.equals("fullversion")) return getFullVersion();
        else return super.getProperty(name);
    }

    public void validate() throws Exception {	
        consoleReporter.addLine();

        if(!debug) Logger.getLogger(HeaderCard.class.getName()).setLevel(Level.WARNING);
        
        if(scans.size() == 0) {
            warning("No scans to reduce. Exiting.");
            consoleReporter.addLine();
            exit(1);
        }

         
        try { instrument.validate(scans); }
        catch(Error e) {
            error(e);
            exit(1);
        }
        catch(Exception e) {
            warning(e);
        }

        Integration.clearRecycler();
        Instrument.clearRecycler();
        SourceModel.clearRecycler();
        
        setObservingTimeOptions();
        
        // Make the global options derive from those of the first scan...
        // This way any options that were activated conditionally for that scan become 'global' starters as well...
        instrument = (Instrument<?>) scans.get(0).instrument.copy();
        
        // Keep only the non-specific global options here...
        for(Scan<?,?> scan : scans) instrument.getOptions().intersect(scan.instrument.getOptions()); 		
        for(int i=scans.size(); --i >=0; ) if(scans.get(i).isEmpty()) scans.remove(i);
        
        System.gc();
              
        if(!isConfigured("lab")) initSourceModel();

        initPipelines();

    }
    
    private void setObservingTimeOptions() {
        if(!instrument.getOptions().containsKey("obstime")) return;

        double obsTime = getTotalObservingTime();
      
        Hashtable<String, Vector<String>> conditions = option("obstime").conditionals;
        for(String condition : conditions.keySet()) {
            if(condition.length() < 2) continue;

            try {
                char op = condition.charAt(0);
                double threshold = Double.parseDouble(condition.substring(1)) * Unit.s;

                if(op == '<') {
                    if(obsTime < threshold) parseAllScans(conditions.get(condition));
                }
                else if(op == '>') {
                    if(obsTime > threshold) parseAllScans(conditions.get(condition));
                }         
            }
            catch(NumberFormatException e) {
                warning("Cannot interpret obstime condition: [" + condition + "].");
            }
        }
    }
    
    public void parseAllScans(Vector<String> options) {
        for(Scan<?, ?> scan : scans) scan.instrument.getOptions().parseAll(options);
    }

    public double getTotalObservingTime() {
        double exposure = 0.0;
        for(Scan<?,?> scan : scans) exposure += scan.getObservingTime();
        return exposure;
    }

    

    private void initSourceModel() throws Exception {
        consoleReporter.addLine();

        // TODO Using the global options (intersect of scan options) instead of the first scan's
        // for the source does not work properly (clipping...)
        source = scans.get(0).instrument.getSourceModelInstance();
        
        if(source != null) {
            source.setCommandLine(commandLine);
            source.createFrom(scans);
            source.setExecutor(sourceExecutor);
            source.setParallel(CRUSH.maxThreads);
            setObjectOptions(source.getSourceName());
        }

        consoleReporter.addLine();

    }

    private void initPipelines() {
        updateRuntimeConfig();

        String parallelMode = "hybrid";
        if(hasOption("parallel")) parallelMode = option("parallel").getValue().toLowerCase();

        if(parallelMode.equals("scans")) {
            parallelScans = maxThreads;
            parallelTasks = 1;
        }
        else if(parallelMode.equals("ops")) {
            parallelScans = 1;
            parallelTasks = maxThreads;
        }
        else {		
            parallelTasks = Math.max(1, maxThreads / scans.size());
            parallelScans = Math.min(scans.size(), Math.max(1, maxThreads / parallelTasks));   
        }

        info("Will use " + parallelScans + " x " + parallelTasks + " grid of threads.");

        pipelines = new ArrayList<Pipeline>(parallelScans); 
        for(int i=0; i<parallelScans; i++) {
            Pipeline pipeline = new Pipeline(this, parallelTasks);
            pipeline.setSourceModel(source);
            pipelines.add(pipeline);
        }


        for(int i=0; i<scans.size(); i++) {
            Scan<?,?> scan = scans.get(i);	
            pipelines.get(i % parallelScans).scans.add(scan);
            for(Integration<?,?> integration : scan) integration.setThreadCount(parallelTasks); 
        }		
    }


    public void updateRuntimeConfig() {
        if(containsKey("outpath")) setOutpath();

        maxThreads = Runtime.getRuntime().availableProcessors();

        if(isConfigured("threads")) {
            maxThreads = get("threads").getInt();
            if(maxThreads < 1) maxThreads = 1;
        }
        else if(isConfigured("idle")) {
            String spec = get("idle").getValue();
            if(spec.charAt(spec.length() - 1) == '%') maxThreads -= (int)Math.round(0.01 * 
                    Double.parseDouble(spec.substring(0, spec.length()-1)) * maxThreads);
            else maxThreads -= get("idle").getInt();
        }
        maxThreads = Math.max(1, maxThreads);

        Instrument.setRecyclerCapacity((maxThreads + 1) << 2);
        Integration.setRecyclerCapacity((maxThreads + 1) << 2);
        SourceModel.setRecyclerCapacity(maxThreads << 1);

       
        final ExecutorService oldExecutor = executor;
        final ExecutorService oldSourceExecutor = executor;
     
        // Replace the executors first...
        executor = Executors.newFixedThreadPool(maxThreads);
        sourceExecutor = Executors.newFixedThreadPool(maxThreads);
        
        if(source != null) source.setExecutor(sourceExecutor);
        
        // Then, shut down the old executor (releases thread resources back to the OS!)
        if(oldExecutor != null) oldExecutor.shutdown();
        if(oldSourceExecutor != null) oldSourceExecutor.shutdown();
    }

    public void setOutpath() {
        workPath = get("outpath").getPath();
        File workFolder = new File(workPath);	
        if(workFolder.exists()) return;	

        warning("The specified output path does not exists: '" + workPath + "'");

        if(!hasOption("outpath.create")) {
            error("Invalid static output path.");
            suggest(this,
                    "       * change 'outpath' to an existing directory, or\n" +
                    "       * set 'outpath.create' to create the path automatically.");
            exit(1);
        }

        info("-----> Creating output folder.");	
        try {
            if(!workFolder.mkdirs()) {
                error("Output path could not be created: unknown error.");
                suggest(this, "       * Try change 'outpath'.");
                exit(1);
            }
        }
        catch(SecurityException e) {
            error("Output path could not be created: " + e.getMessage());
            suggest(this, "       * Try change 'outpath'.");
            exit(1);
        }
    }

    public void read(String scanID) {
        StringTokenizer list = new StringTokenizer(scanID, "; \t");

        updateRuntimeConfig();

        if(list.countTokens() > 1) {
            while(list.hasMoreTokens()) read(list.nextToken());
            return;
        }

        consoleReporter.addLine();

        status(this, "Reading scan: " + scanID);
        
        if(hasOption("leapseconds")) LeapSeconds.dataFile = option("leapseconds").getPath();

        try {
            Scan<?,?> scan = null;
            if(isConfigured("obslog")) {
                scan = instrument.readScan(scanID, false);
                scan.writeLog(get("obslog"),  workPath + File.separator + instrument.getName() + ".obs.log");
            }
            else { 
                scan = instrument.readScan(scanID, true);
                scan.validate();
                if(scan.size() == 0) warning(scan, "Scan " + scan.getID() + " contains no valid data. Skipping.");
                else if(isConfigured("subscans.split")) scans.addAll(scan.split());	
                else scans.add(scan);

                System.gc();		
            }
        }
        catch(OutOfMemoryError e) {
            if(e.getMessage().equals("unable to create new native thread")) {
                error("Exceeded the maximum allowed user processes.");
                suggest(this,
                        "   * Try increase the user processes limit. E.g.:\n" +
                                "       $ ulimit -u 65536\n\n" +
                                "   * Decrease the number of parallel threads used by CRUSH:\n" +
                        "       $ crush [...] -threads=4 [...]\n");
            }

            else {
                error("Ran of of memory while reading scan.");
                suggest(this,
                        "   * Increase the amount of memory available to crush, by editing the '-Xmx'\n" +
                                "     option to Java in 'wrapper.sh' (or 'wrapper.bat' for Windows).\n\n" +
                                "   * If using 64-bit Unix OS and Java, you can also add the '-d64' option to\n" +
                                "     allow Java to access over 2GB.\n\n" +
                                "   * Try reduce scans in smaller chunks. You can then use 'coadd' to combine\n" +
                                "     the maps post reduction. Note: it is always preferable to try reduce all" +
                        "     scans together, if there is a way to fit them into memory.\n");
            }

            exit(1);				
        }
        catch(UnsupportedScanException e) {
            warning("Unsupported scan type: " + e.getMessage() + "\n");
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
                catch(Exception parseError) { error(parseError); }
            }
            else warning(e);
        }
        catch(Exception e) {
            warning(e);
            if(!debug) suggest(this, "        (use '-debug' to obtain additional information on this error.)");
        }	
    }


    public void reduce() throws Exception {	
        int rounds = 0;

        status(this, "Reducing " + scans.size() + " scan(s).");

        if(isConfigured("bright")) info("Bright source reduction.");
        else if(isConfigured("faint")) info("Faint source reduction.");
        else if(isConfigured("deep")) info("Deep source reduction.");
        else info("Default reduction.");

        if(isConfigured("extended")) info("Assuming extended source(s).");

        info("Assuming " + Util.f1.format(instrument.getSourceSize()/instrument.getSizeUnit().value()) + " " + instrument.getSizeUnit().name() + " sized source(s).");

        if(isConfigured("rounds")) rounds = get("rounds").getInt();

        for(int iteration=1; iteration<=rounds; iteration++) {
            consoleReporter.addLine();
            info("Round " + iteration + ": ");	

            setIteration(iteration, rounds);	
          
            iterate();	
        }

        consoleReporter.addLine();

        writeProducts();

        status(this, "Done.");
        consoleReporter.addLine();
    }
    
    private void writeProducts() {
        if(source != null) {
            source.suggestions();

            if(source.isValid()) {
                try { source.write(workPath); }
                catch(Exception e) { error(e); }
            }
            else warning("The reduction did not result in a source model.");
        }

        for(Scan<?,?> scan : scans) scan.writeProducts();   
    }

    public void iterate() throws Exception {
        List<String> ordering = get("ordering").getLowerCaseList();
        ArrayList<String> tasks = new ArrayList<String>(ordering.size());

        for(int i=0; i < ordering.size(); i++) {
            final String task = ordering.get(i);
            tasks.add(task);

            if(solveSource()) if(task.startsWith("source"))  {
                iterate(tasks);
                tasks.clear();
            }
        }

        if(!tasks.isEmpty()) iterate(tasks);

        consoleReporter.addLine();
    }


    public void iterate(List<String> tasks) throws Exception {
        consoleReporter.addLine();

        queue.clear();
        for(Scan<?,?> scan : scans) queue.addAll(scan);

        if(solveSource()) if(tasks.contains("source")) source.renew();

        for(int i=0; i<pipelines.size(); i++) {
            final Pipeline pipeline = pipelines.get(i);
            pipeline.setOrdering(tasks);
            new Thread(pipeline).start();
        }

        summarize();

        if(solveSource()) if(tasks.contains("source")) {
            source.process();
            source.sync();
            
            info(" [Source] " + source.getProcessBrief());
            source.clearProcessBrief();
        }

        if(isConfigured("whiten")) if(get("whiten").isConfigured("once")) purge("whiten");
    }



    public synchronized void checkout(Integration<?,?> integration) {
        queue.remove(integration);
        notifyAll();
    }

    public synchronized void summarize() throws InterruptedException {
        // Go in order.
        // If next one disappears from queue then print summary
        // else wait();

        for(Scan<?, ?> scan : scans) for(Integration<?,?> integration : scan) {
            while(queue.contains(integration)) wait();
            summarize(integration);
            notifyAll();
        }	
    }

    public void summarize(Integration<?,?> integration) {
        info(" [" + integration.getDisplayID() + "] " + integration.comments);
        integration.comments = new String();
    }	

    public final void setIteration(int i, int rounds) {	
        setIteration(this, i, rounds);
        for(Scan<?,?> scan : scans) scan.setIteration(i, rounds);   
    }

    public void setObjectOptions(String sourceName) {
        //debug("Setting global options for " + sourceName);
        sourceName = sourceName.toLowerCase();

        if(!containsKey("object")) return;

        Hashtable<String, Vector<String>> settings = get("object").conditionals;
        for(String spec : settings.keySet()) if(sourceName.startsWith(spec)) 
            instrument.getOptions().parseAll(settings.get(spec));

    }


    public boolean solveSource() {
        if(source == null) return false;
        return isConfigured("source");
    }


    public static void setIteration(Configurator config, int i, int rounds) {	
        if(!config.branches.containsKey("iteration")) return;
        Hashtable<String, Vector<String>> settings = config.branches.get("iteration").conditionals;

        // Parse explicit iteration settings
        if(settings.containsKey(i + "")) config.parseAll(settings.get(i + ""));		

        // Parse relative iteration settings
        for(String spec : settings.keySet()) if(spec.endsWith("%")) {
            int k = (int) Math.round(rounds * 0.01 * Double.parseDouble(spec.substring(0, spec.length()-1)));
            if(i == k) config.parseAll(settings.get(spec));
        }

        // Parse end-based settings
        String spec = "last" + (i==rounds ? "" : "-" + (rounds-i));
        if(settings.containsKey(spec)) config.parseAll(settings.get(spec));
    }

    public static String getCopyrightString() { return Util.getCopyrightString(); }

    public static void info() {
        System.err.println(
                "\n" +
                        " -----------------------------------------------------------------------------\n" +
                        " crush -- Reduction and imaging tool for astronomical cameras.\n" +
                        "          Version: " + getFullVersion() + "\n" + 
                        "          Featuring: jnum " + Util.getFullVersion() + ", nom.tam.fits " + Fits.version() + "\n" +
                        "          http://www.submm.caltech.edu/~sharc/crush\n" +
                        "          " + getCopyrightString() + "\n" +
                        " -----------------------------------------------------------------------------\n");	
    }

    public static void usage() {
        System.out.println(
                " Usage: crush <instrument> [options] <scanlist> [[options] <scanlist> ...]\n" +
                        "\n" +
                        "    <instrument>     Instrument name, e.g. 'hawc+', 'gismo', 'scuba2'...\n" +
                        "                     Try 'crush -help' for a list of supported cameras.\n" +
                        "    [options]        Various configuration options. See README and GLOSSARY.\n" +
                        "                     Global settings must precede scans on the argument list.\n" +
                        "                     Each scan will use all options listed before it.\n" +
                        "    <scanlist>       A list of scan numbers/IDs and/or filenames to reduce.\n" +
                        "                     E.g.: 10628-10633 11043 myscan.fits\n" +
                        "\n" +
                        "   Try 'crush <instrument> -poll' for a list of current settings or\n" +
                        "   'crush -help' for a full list of instruments and brief list of commonly\n" +
                "   used options.\n"); 
    }


    public static void help(Instrument<?> instrument) {
        System.out.println(
                "\n" + (instrument != null ? 
                        " Usage: crush " + instrument.getName() + " [options] <scanlist> [[options] <scanlist> ...]\n\n"
                        : " Usage: crush <instrument> [options] <scanlist> [[options] <scanlist> ...]\n" +
                        "\n" +
                        " Supported instruments (mandatory first argument to crush):\n" +
                        "\n" +
                        "     aszca          APEX S-Z Camera.\n" +
                        "     gismo          Goddard-IRAM 2mm Observer.\n" +
                        "     hawc+          SOFIA/HAWC+ camera.\n" +
                        "     laboca         LABOCA 870um camera at APEX.\n" +
                        "     mako           MAKO 350um KID demo camera at the CSO.\n" +
                        "     mako2          Gen. 2 MAKO 350um/850um KID demo camera at CSO.\n" +
                        "     mustang2       MUSTANG-2 3mm camera at the GBT.\n" +
                        "     p-artemis      ArTeMiS prototype at APEX.\n" +
                        "     polka          PolKa polarimetry frontend for LABOCA.\n" +
                        "     saboca         SABOCA 350um camera at APEX.\n" +
                        "     scuba2         SCUBA-2 450um/850um camera at the JCMT.\n" +
                        "     sharc          Original SHARC 350um camera at the CSO.\n" +
                        "     sharc2         SHARC-2 350um/450um/850um camera at the CSO.\n" +
                        "\n" +		
                        " Try 'crush <instrument> -help' to get an instrument-specific version of this\n" +
                        " help screen.\n\n") +
                " For full and detailed description of all options please consult the GLOSSARY.\n" +
                " Here are some commonly used options" +
                (instrument == null ? ":" : " for " + instrument.getName() + ":") + "\n\n" +
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
                "     -conditions    List all conditional settings.\n");
        
        System.exit(0);
    }

    public String getReleaseVersion() {  
        String version = null;

        try {
            URL versionURL = new URL("http://www.submm.caltech.edu/~sharc/crush/v2/release.version");
            URLConnection connection = versionURL.openConnection();
            try {
                connection.setConnectTimeout(TCP_CONNECTION_TIMEOUT);
                connection.setReadTimeout(TCP_READ_TIMEOUT);
                connection.connect();

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                version = in.readLine();
                in.close();
            } 
            catch(IOException e) {
                if(e instanceof SocketTimeoutException) 
                    warning("Timed out while awaiting version update information.");
                else 
                    warning("Could not get version update information.");

                if(debug) warning(e);
            }

        }
        catch(MalformedURLException e) { error(e); }
        catch(IOException e) {
            warning("No connection to version update server.");
            if(debug) warning(e);
        }

        return version;
    }


    public void checkForUpdates() {	
        
        if(System.getenv("CRUSH_NO_UPDATE_CHECK") != null) {
            CRUSH.detail(this, "Skipping update checking.");
            return;
        }
       
        String releaseVersion = getReleaseVersion();	
        if(releaseVersion == null) return;

        VersionString release = new VersionString(releaseVersion);
        if(release.compareTo(new VersionString(version)) <= 0) return; 

        for(int i=0; i<8; i++) System.err.print("**********");
        System.err.println();

        warning(
                "A NEW CRUSH-2 RELEASE IS NOW AVAILABLE FOR DOWNLOAD!!!\n" +
                        "\n" + 
                        "Version: " + releaseVersion + "\n" +
                        "Available at: www.submm.caltech.edu/~sharc/crush\n\n" +
                        "You should always update to the latest release to take advantage of critical " +
                "bug fixes, improvements, and new features.");

        for(int i=0; i<8; i++) System.err.print("**********");
        System.err.println();

        countdown(5);	

    }

    public void checkJavaVM(int countdown) {
        if(System.getenv("CRUSH_NO_VM_CHECK") != null) {
            CRUSH.detail(this, "Skipping VM checking.");
            return;
        }
        
        String name = System.getProperty("java.vm.name");
        if(name.startsWith("GNU") || name.contains("libgcj")) {
            for(int i=0; i<8; i++) System.err.print("**********");
            System.err.println();

            warning(
                    "You appear to be running CRUSH with GNU Java (libgcj).\n" +
                            "\n" +
                            "The GNU Java virtual machine is rather slow and is known for " +
                            "producing unexpected errors during the reduction.\n" +
                            "It is highly recommended that you install and use a more reliable " +
                            "Java Runtime Environment (JRE).\n" +
                            "\n" +
                            "Please check for available Java packages for your system or see " +
                            "http://www.submm.caltech.edu/~sharc/crush/download.html " +
                            "for possible Java downloads.\n" +
                            "\n" +
                            "If you already have another Java installations on your system " +
                            "you can edit the 'JAVA' variable in 'wrapper.sh' inside the CRUSH " +
                    "distribution directory to point to the desired java executable.");


            for(int i=0; i<8; i++) System.err.print("**********");
            System.err.println();
            System.err.println();

            if(countdown > 0) {
                suggest(this, "         You may ignore this warning and proceed at your own risk shortly...");
                countdown(countdown);
            }
        }

    }

    public static void countdown(int seconds) {
        String quickstart = System.getProperty("CRUSH_QUICKSTART");
        if(quickstart != null) return; 

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

    public static void addHistory(Header header) throws HeaderCardException {
        // Add the reduction to the history...
        AstroTime timeStamp = new AstroTime();
        timeStamp.now();
        
        FitsToolkit.addHistory(FitsToolkit.endOf(header), "Reduced: crush v" + CRUSH.getFullVersion() + " @ " + timeStamp.getFitsTimeStamp());			
    }

    @Override
    public void editHeader(Header header) throws HeaderCardException {        
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        // Add the system descriptors...	
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
        c.add(new HeaderCard("COMMENT", " CRUSH runtime configuration section", false));
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));

        c.add(new HeaderCard("CRUSHVER", getFullVersion(), "CRUSH version information."));		

        if(commandLine != null) {
            StringTokenizer args = new StringTokenizer(commandLine);
            c.add(new HeaderCard("ARGS", args.countTokens(), "The number of arguments passed from the command line."));
            int i=1;
            while(args.hasMoreTokens()) FitsToolkit.addLongKey(c, "ARG" + (i++), args.nextToken(), "Command-line argument.");
        }
        
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
        c.add(new HeaderCard("COMMENT", " CRUSH Java VM & OS section", false));
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));

        c.add(new HeaderCard("JAVA", Util.getProperty("java.vendor"), "Java vendor name."));
        c.add(new HeaderCard("JAVAVER", Util.getProperty("java.version"), "The Java version."));

        FitsToolkit.addLongKey(c, "JAVAHOME", Util.getProperty("java.home"), "Java location.");
        c.add(new HeaderCard("JRE", Util.getProperty("java.runtime.name"), "Java Runtime Environment."));
        c.add(new HeaderCard("JREVER", Util.getProperty("java.runtime.version"), "JRE version."));
        c.add(new HeaderCard("JVM", Util.getProperty("java.vm.name"), "Java Virtual Machine."));
        c.add(new HeaderCard("JVMVER", Util.getProperty("java.vm.version"), "JVM version."));

        c.add(new HeaderCard("OS", Util.getProperty("os.name"), "Operation System name."));
        c.add(new HeaderCard("OSVER", Util.getProperty("os.version"), "OS version."));
        c.add(new HeaderCard("OSARCH", Util.getProperty("os.arch"), "OS architecture."));

        c.add(new HeaderCard("CPUS", Runtime.getRuntime().availableProcessors(), "Number of CPU cores/threads available."));
        c.add(new HeaderCard("DMBITS", Util.getProperty("sun.arch.data.model"), "Bits in data model."));
        c.add(new HeaderCard("CPENDIAN", Util.getProperty("sun.cpu.endian"), "CPU Endianness."));
        c.add(new HeaderCard("MAXMEM", Runtime.getRuntime().maxMemory() / (1024 * 1024), "MB of available memory."));

        c.add(new HeaderCard("COUNTRY", Util.getProperty("user.country"), "The user country."));
        c.add(new HeaderCard("LANGUAGE", Util.getProperty("user.language"), "The user language."));


        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
        c.add(new HeaderCard("COMMENT", " CRUSH configuration section", false));
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));

        super.editHeader(header);

        c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
        c.add(new HeaderCard("COMMENT", " End of CRUSH configuration section", false));
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
    }


    public void exit(int exitValue) {
        if(instrument != null) instrument.shutdown();
        Util.setDefaultReporter();
        System.exit(exitValue);
    }


    @Override
    public void info(String message) { CRUSH.info(this, message); }

    @Override
    public void notify(String message) { CRUSH.notify(this, message); }

    @Override
    public void debug(String message) { CRUSH.debug(this, message); }

    @Override
    public void warning(String message) { CRUSH.warning(this, message); }

    @Override
    public void warning(Exception e, boolean debug) { CRUSH.warning(this, e, debug); }

    @Override
    public void warning(Exception e) { CRUSH.warning(this, e); }

    @Override
    public void error(String message) { CRUSH.error(this, message); }

    @Override
    public void error(Throwable e, boolean debug) { CRUSH.error(this, e, debug); }

    @Override
    public void error(Throwable e) { CRUSH.error(this, e); }


    public static void info(Object owner, String message) { broadcaster.info(owner, message); }

    public static void notify(Object owner, String message) { broadcaster.notify(owner, message); }

    public static void debug(Object owner, String message) { broadcaster.debug(owner, message); }

    public static void warning(Object owner, String message) { broadcaster.warning(owner, message); }

    public static void warning(Object owner, Exception e, boolean debug) { broadcaster.warning(owner, e, debug); }

    public static void warning(Object owner, Exception e) { broadcaster.warning(owner, e, debug); }

    public static void error(Object owner, String message) { broadcaster.error(owner, message); }

    public static void error(Object owner, Throwable e, boolean debug) { broadcaster.error(owner, e, debug); }

    public static void error(Object owner, Throwable e) { broadcaster.error(owner, e); }

    public static void status(Object owner, String message) { broadcaster.status(owner, message); }

    public static void result(Object owner, String message) { broadcaster.result(owner, message); }

    public static void detail(Object owner, String message) { broadcaster.detail(owner, message); }

    public static void values(Object owner, String message) { broadcaster.values(owner, message); }

    public static void suggest(Object owner, String message) { broadcaster.suggest(owner, message); }

    public static void trace(Throwable e) { broadcaster.trace(e); }





    public static abstract class Fork<ReturnType> extends ParallelTask<ReturnType> {
        private Exception exception;
        private int size;
        private int parallelism;

        public Fork(int size, int parallel) { 
            this.size = size; 
            this.parallelism = parallel;
        }

        @Override
        protected void processChunk(int index, int threadCount) {
            for(int k=size - index - 1; k >= 0; k -= threadCount) {
                if(isInterrupted()) return;
                processIndex(k);
                Thread.yield();
            }
        }

        protected abstract void processIndex(int index);


        public void process() {
            exception = null;
            process(parallelism);
        }

        @Override
        public void process(int threads) {
            try {
                if(executor != null) process(threads, executor);
                else process(threads);			
            } catch(Exception e) { 
                CRUSH.warning(this, executor.getClass().getSimpleName() + ": " + e.getMessage());
                if(debug) trace(e);
                this.exception = e;
            }
        }


        public boolean hasException() { return exception != null; }

        public Exception getLastException() { return exception; }
    }



    public static void add(Reporter r) { broadcaster.add(r); }

    public static void remove(Reporter r) { broadcaster.remove(r); }

    public static void removeReporter(String id) { broadcaster.remove(id); }



    public static CRUSHConsoleReporter consoleReporter = new CRUSHConsoleReporter("crush-console");
    public static Broadcaster broadcaster = new Broadcaster("CRUSH-broadcast", consoleReporter);



    public static final int TCP_CONNECTION_TIMEOUT = 3000;
    public static final int TCP_READ_TIMEOUT = 2000;


}

