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
 * @version 2.50
 * 
 */
public class CRUSH extends Configurator implements BasicMessaging {
    /**
     * 
     */
    private static final long serialVersionUID = 6284421525275783456L;

    private final static String version = "2.50-a2";
    private final static String revision = "devel.4";

    public static String home = ".";
    public static boolean debug = false;
    
    public static int maxThreads = 1;
    public static volatile ExecutorService executor;
    
    
    public Instrument<?> instrument;
    public Vector<Scan<?>> scans = new Vector<>();
    public SourceModel source;
    public String[] commandLine;
    

    public volatile ExecutorService sourceExecutor;
    
    public int parallelScans = 1;
    public int parallelTasks = 1;

    private ArrayList<Pipeline> pipelines;
    private Vector<Integration<?>> queue = new Vector<>();

    private int configDepth = 0;	// Used for 'nested' output of invoked configurations.


    public static void main(String[] args) {        
        CRUSH crush = new CRUSH();

        crush.checkJavaVM(5);     
        crush.checkForUpdates();	
        
        try { 
            crush.init(args);
            crush.reduce(); 
        }
        catch(Exception e) { crush.error(e); }

        crush.shutdown();
        
        // TODO should not be needed if background processes are all wrapped up...
        System.exit(0);
    }

    private CRUSH() {
        info();
        
        Locale.setDefault(Locale.US);
        FitsFactory.setUseHierarch(true);
        FitsFactory.setLongStringsEnabled(true);

        Util.setReporter(broadcaster);

        if(home == null) home = ".";
    }
    
    /**
     * Public constructor that may be used to create a CRUSH instance inside another Java program.
     * One should create a fresh CRUSH instance at the beginning of each reduction run, as it is not guranteed that
     * CRUSH will return to a 'fresh' state at the end of a previous run.
     * 
     * 
     * @param instrumentName    The (case-insensitive) name of the CRUSH-supported instrument for which to instantiate a
     *                          a new pipeline instance. E.g. <tt>"hirmes"</tt> or <tt>"hawc+"</tt>.
     * @throws Exception        An appropriate exception will be thrown if the specified instrument is unrecognised or if
     *                          the pipeline could not be fully initialized for that specific instrument.
     */
    public CRUSH(String instrumentName) throws Exception {
        this();
        setInstrument(instrumentName);
    }
    
    /**
     * Initializes CRUSH for the given instrument, and no extra options...
     * 
     * @param instrumentName    The instrument to use CRUSH with, e.g. <tt>"sharc2"</tt>, or <tt>"hirmes"</tt>
     * @throws Exception        If CRUSH could not be initialized with the specified instrument name.
     */
    private final void setInstrument(String instrumentName) throws Exception {
        instrument = Instrument.forName(instrumentName.toLowerCase());      
        instrument.setOptions(this);

        info(this, "Instrument is " + instrument.getName().toUpperCase());     
     
        clear();        

        readConfig("default.cfg");
    }
    
    
    /**
     * Initializes CRUSH for the given argument list, as if the list were command-line arguments. 
     * 
     * 
     * @param args          The list of argument to initialize CRUSH with. The first argument must be the
     *                      instrument name to use CRUSH with, e.g. <tt>"sharc2"</tt> or <tt>"hirmes"</tt>, or 
     *                      else <tt>"-help"</tt> to print just a help screen. The arguments that follow must
     *                      either be options, starting with a dash ('-'), or else scan specifiers. E.g.   
     *                                       
     *                      <pre>
     *                         { "hirmes", "-faint", "-flight=405", "68", "70-77" }
     *                      </pre>
     *                      
     * @throws Exception    An exception indicative of a problem encountered during initialization.
     */  
    private void init(String[] args) throws Exception {	 
        if(args.length == 0) {
            usage();
            System.exit(0);         
        }

        if(args[0].equalsIgnoreCase("-help")) {
            help(null);
            System.exit(0); 
        }
        
        try { setInstrument(args[0]); }
        catch(Exception e) {
            error(e.getMessage());
            System.exit(1);
        }
        
        commandLine = args;
        for(int i=1; i<args.length; i++) if(args[i].length() > 0) parseArgument(args[i]);
    }

    
    private void parseArgument(String arg) throws OutOfMemoryError {
        if(arg.charAt(0) == '-') setOption(arg.substring(1));
        else {
            try { read(arg); }
            catch(Exception e) {}
        }
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
    
    public void readConfigFile(String fileName) throws IOException {
        super.readConfig(fileName);
        if(instrument != null) instrument.registerConfigFile(fileName);
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
            readConfigFile(getConfigPath() + File.separator + fileName);
            found = true;
        }
        catch(IOException e) {}

        try { 
            readConfigFile(userConfPath + fileName);
            found = true;
        }
        catch(IOException e) {}

        try { 
            readConfigFile(instrument.getConfigPath() + fileName);
            found = true;
        }
        catch(IOException e) { }

        // read the instrument overriderrs (if any).
        try { 
            readConfigFile(userConfPath + instrument.getName() + File.separator + fileName);
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

    private void validate() throws Exception {	
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
        instrument = scans.get(0).getInstrument().copy();
        instrument.setOptions(this);
        
        // Keep only the non-specific global options here...
        for(Scan<?> scan : scans) instrument.getOptions().intersect(scan.getOptions()); 		
        for(int i=scans.size(); --i >=0; ) if(scans.get(i).isEmpty()) scans.remove(i);
        
        System.gc();
              
        if(!hasOption("lab")) initSourceModel();

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
        for(Scan<?> scan : scans) scan.getOptions().parseAll(options);
    }

    public double getTotalObservingTime() {
        double exposure = 0.0;
        for(Scan<?> scan : scans) exposure += scan.getObservingTime();
        return exposure;
    }

    

    private void initSourceModel() throws Exception {
        consoleReporter.addLine();

        source = scans.get(0).getInstrument().getSourceModelInstance(scans);
        
        if(source != null) {
            source.createFrom(scans);
            source.setExecutor(sourceExecutor);
            source.setParallel(maxThreads);
            setObjectOptions(source.getSourceName());
        }
        else {
            warning("No source model or invalid source model type.");
        }

        consoleReporter.addLine();

    }

    private void initPipelines() throws Exception {
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

        pipelines = new ArrayList<>(parallelScans); 
        for(int i=0; i<parallelScans; i++) {
            Pipeline pipeline = new Pipeline(this, parallelTasks);
            pipeline.setSourceModel(source);
            pipelines.add(pipeline);
        }


        for(int i=0; i<scans.size(); i++) {
            Scan<?> scan = scans.get(i);	
            pipelines.get(i % parallelScans).scans.add(scan);
            for(Integration<?> integration : scan) integration.setThreadCount(parallelTasks); 
        }		
    }


    private void updateRuntimeConfig() throws Exception {
        setOutpath();

        maxThreads = Runtime.getRuntime().availableProcessors();

        if(hasOption("threads")) {
            maxThreads = option("threads").getInt();
            if(maxThreads < 1) maxThreads = 1;
        }
        else if(hasOption("idle")) {
            String spec = option("idle").getValue();
            if(spec.charAt(spec.length() - 1) == '%') maxThreads -= (int)Math.round(0.01 * 
                    Double.parseDouble(spec.substring(0, spec.length()-1)) * maxThreads);
            else maxThreads -= option("idle").getInt();
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

    private void setOutpath() throws Exception {
        File workFolder = new File(instrument.getOutputPath());	
        if(workFolder.exists()) return;	

        warning("The specified output path does not exists: '" + instrument.getOutputPath() + "'");

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
                throw(new IOException("Output path could not be created: unknown error."));
            }
        }
        catch(SecurityException e) {
            error("Output path could not be created: " + e.getMessage());
            suggest(this, "       * Try change 'outpath'.");
            throw(e);
        }
    }

    
    /**
     * Attempts to load a scan's data for the current instrument, and using the current configuration settings.  
     * 
     * 
     * @param scanID    Scan file name or path (absolute, or relative to <tt>datapath</tt>), or scan IDs, numbers, or ranges, depending
     *                  on what ways of specifiying scans is available for the given instrument.
     * @throws OutOfMemoryError If we ran out of Java heap space while loading the scan. You way need to tweak the '-Xmx' runtime
     *          option to java to allow it to use more RAM.
     * @throws FileNotFoundException if no scan file could be matched to the given scanID under the current set of configuration
     *          options (e.g. <tt>datapath</tt>, <tt>object</tt>, <tt>date</tt>, or <tt>flight</tt> &mdash; depending what the 
     *          current instrument might use for locating data).
     * @throws UnsupportedScanException If a scan file was found but the file is not supported by the current instrument. For
     *          example if you try to load a SHARC-2 scan while CRUSH was initialized for HIRMES.
     * @throws Exception An apppropriate exception is thrown if the Scan cannot be located, or else could not be properly read
     *          or loaded.
     */
    public void read(String scanID) throws OutOfMemoryError, FileNotFoundException, UnsupportedScanException, Exception {
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
            Scan<?> scan = null;
            if(hasOption("obslog")) {
                scan = instrument.readScan(scanID, false);
                scan.writeLog(option("obslog"),  instrument.getOutputPath() + File.separator + instrument.getName() + ".obs.log");
            }
            else { 
                scan = instrument.readScan(scanID, true);
                scan.validate();
                if(scan.size() == 0) warning(scan, "Scan " + scan.getID() + " contains no valid data. Skipping.");
                else if(hasOption("subscans.split")) scans.addAll(scan.split());	
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
            throw e;
        }
        catch(UnsupportedScanException e) {
            warning("Unsupported scan type: " + e.getMessage() + "\n");
            throw e;
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
                    error(parseError); 
                    throw parseError;
                }
            }
            else {
                warning(e);
                throw e;
            }
        }
        catch(Exception e) {
            warning(e);
            if(!debug) suggest(this, "        (use '-debug' to obtain additional information on this error.)");
            throw e;
        }	
    }

    /**
     * Runs the reduction pipeline for the currently loaded scans and reduction options. This may take a while, but you
     * can get messages from CRUSH on what's going on via your own {@link jnum.reporter.Reporter} implementation that you can
     * add to CRUSH's message broadcaster (see {@link #add(Reporter)}).
     * 
     * 
     * @throws Exception    An appropriate exception if something did not go as expected during the reduction process.
     * 
     * @see #add(Reporter)
     * @see #getBroadcaster()
     */
    public void reduce() throws Exception {	
        validate();
        
        int rounds = 0;

        status(this, "Reducing " + scans.size() + " scan(s).");

        if(hasOption("bright")) info("Bright source reduction.");
        else if(hasOption("faint")) info("Faint source reduction.");
        else if(hasOption("deep")) info("Deep source reduction.");
        else info("Default reduction.");

        if(hasOption("extended")) info("Assuming extended source(s).");

        info("Assuming " + Util.f1.format(instrument.getSourceSize()/instrument.getSizeUnit().value()) + " " + instrument.getSizeUnit().name() + " sized source(s).");

        if(hasOption("rounds")) rounds = option("rounds").getInt();
        
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
                try { source.write(); }
                catch(Exception e) { error(e); }
            }
            else warning("The reduction did not result in a source model.");
        }

        for(Scan<?> scan : scans) scan.writeProducts();   
    }

    public void iterate() throws Exception {
        List<String> ordering = option("ordering").getLowerCaseList();
        ArrayList<String> tasks = new ArrayList<>(ordering.size());

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
        for(Scan<?> scan : scans) queue.addAll(scan);

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

        if(hasOption("whiten")) if(option("whiten").hasOption("once")) purge("whiten");
    }



    public synchronized void checkout(Integration<?> integration) {
        queue.remove(integration);
        notifyAll();
    }

    public synchronized void summarize() throws Exception {
        // Go in order.
        // If next one disappears from queue then print summary
        // else wait();

        for(Scan<?> scan : scans) for(Integration<?> integration : scan) {
            while(queue.contains(integration)) wait();
            
            // Check for exceptions...
            for(Pipeline p : pipelines) if(p.getException() != null) throw p.getException();
            
            summarize(integration);
            notifyAll();
        }	
    }

    public void summarize(Integration<?> integration) {
        info(" [" + integration.getDisplayID() + "] " + integration.comments);
        integration.comments = new StringBuffer();
    }	

    public final void setIteration(int i, int rounds) {	
        setIteration(this, i, rounds);
        for(Scan<?> scan : scans) scan.setIteration(i, rounds);   
    }

    public void setObjectOptions(String sourceName) {
        //debug("Setting global options for " + sourceName);
        sourceName = sourceName.toLowerCase();

        if(!containsKey("object")) return;

        Hashtable<String, Vector<String>> settings = option("object").conditionals;
        for(String spec : settings.keySet()) if(sourceName.startsWith(spec)) 
            instrument.getOptions().parseAll(settings.get(spec));

    }


    public boolean solveSource() {
        if(source == null) return false;
        return hasOption("source");
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
                        "          http://www.sigmyne.com/crush\n" +
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
                        "   'crush -help' for a full list of instruments and a brief list of commonly\n" +
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
                        "     gismo          Goddard-IRAM 2mm Observer.\n" +
                        "     hawc+          SOFIA/HAWC+ camera.\n" +
                        "     hirmes         SOFIA/HIRMES camera.\n" +
                        "     laboca         LABOCA 870um camera at APEX.\n" +
                        "     polka          PolKa polarimetry frontend for LABOCA.\n" +
                        "     saboca         SABOCA 350um camera at APEX.\n" +
                        "     scuba2         SCUBA-2 450um/850um camera at the JCMT.\n" +
                        "     sharc2         SHARC-2 350um/450um/850um camera at the CSO.\n" +
                        "\n" +		
                        " Try 'crush <instrument> -help' to get an instrument-specific version of this\n" +
                        " help screen.\n\n") +
                " For full and detailed description of all options please consult the GLOSSARY" +
                (instrument == null ? ".\n" : ",\n and/or README." + instrument.getName() + ".") +
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
                "     -gzip          Compress the output with gzip if possible.\n" +
                "     -final:smooth= Smoothing in the final iteration, either as FWHM (arcsec)\n" +
                "                    or one of: 'minimal', 'halfbeam', '2/3beam, 'beam'\n" +
                (instrument != null ? instrument.getMapConfigHelp() : "") +
                "\n" +
                "   Commonly used options for scans:\n" +
                "     -tau=          Specify an in-band zenith tau, source ID, or interpolation\n" +
                "                    table to use. E.g.: '1.036', '225GHz', or '~/tau.dat'.\n" +
                "     -tau.<id>=     Specify a zenith tau value or interpolation table for <id>.\n" +
                "                    E.g. 'tau.225GHz=0.075'.\n" +
                "     -scale=        Apply a calibration correction factor to the scan(s).\n" +
                "     -pointing=     x,y pointing corrections in arcsec.\n" +
                (instrument != null ? instrument.getScanOptionsHelp() : "") +
                "\n" +
                "   Alternative reduction modes:\n" +
                "     -point         Reduce pointing/calibration scans.\n" +
                "     -skydip        Reduce skydips to obtain in-band zenith opacity.\n" +
                "     -pixelmap      Derive pixel position data from beam maps.\n" +
                "     -split         Indicate that the scans are part of a larger dataset.\n" +
                (instrument != null ? instrument.getReductionModesHelp() : "") +
                "\n" +
                "   Other useful options:\n" +
                "     -show          Display the result (if possible) at the end.\n" +
                "     -forget=       Comma separated list of options to unset.\n" +
                "     -blacklist=    Comma separated list of options to ignore.\n" +
                "     -config=       Load configuration file.\n" +
                "     -poll          Poll the currently set options.\n" +
                "     -conditions    List all conditional settings.\n");
        
    }

    public static String getReleaseVersion() {  
        String version = null;

        try {
            URLConnection connection = new URL("http://www.sigmyne.com/crush/v2/release.version").openConnection();

            try {
                connection.setConnectTimeout(TCP_CONNECTION_TIMEOUT);
                connection.setReadTimeout(TCP_READ_TIMEOUT);
                connection.connect();
                
                try(BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    version = in.readLine();
                    in.close();
                }
                catch(IOException e) {
                    Util.warning(CRUSH.class, "Could not get version update information.");    
                    if(debug) Util.warning(CRUSH.class, e);
                }
            } 
            catch(SocketTimeoutException e) {
                Util.warning(CRUSH.class, "Timed out while awaiting version update information.");
            }
            
        }
        catch(MalformedURLException e) { Util.error(CRUSH.class, e); }
        catch(IOException e) {
            Util.warning(CRUSH.class, "No connection to version update server.");
            if(debug) Util.warning(CRUSH.class, e);
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
            c.add(new HeaderCard("ARGS", commandLine.length-1, "Number of command line arguments."));
            for(int i=1; i<commandLine.length; i++) FitsToolkit.addLongKey(c, "ARG" + i, commandLine[i], "Command-line argument.");
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

    public void shutdown() {
        if(instrument != null) instrument.shutdown();
        Util.setDefaultReporter();
    }

    public void exit(int exitValue) {
        shutdown();
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
            }
        }

        protected abstract void processIndex(int index);


        public final void process() {
            exception = null;
            process(parallelism, executor);
        }

        @Override
        public final void process(int threads, ExecutorService executor) {
            try { super.process(threads, executor); } 
            catch(Exception e) { 
                CRUSH.warning(this, (executor == null ? "<thread>" : executor.getClass().getSimpleName()) + ": " + e.getMessage());
                if(debug) trace(e);
                this.exception = e;
            }
        }


        public boolean hasException() { return exception != null; }

        public Exception getLastException() { return exception; }
    }


    /**
     * Gets CRUSH's message broadcaster.
     * 
     * 
     * @return The message broadcaster used by CRUSH.
     */
    public static Broadcaster getBroadcaster() { return broadcaster; }

    /**
     * Adds a message consumer ({@link jnum.reporting.Reporter}) to which CRUSH messages will be broadcast to.
     * 
     * @param r     The additional Reporter object that will be used to consume CRUSH messages.
     * @return The prior 
     */
    public static Reporter addReporter(Reporter r) { return broadcaster.add(r); }

    /**
     * Removes the given {@link jnum.reporting.Reporter} object from CRUSH's message broadcast. CRUSH will send no more messages 
     * for that reporter to consume.
     * 
     * @param r     The message cosumer to remove from CRUSH's message broadcast.
     */
    public static void removeReporter(Reporter r) { broadcaster.remove(r); }

    /**
     * Removes a message consumer, identified by its String ID, from CRUSH's message broadcasting.
     * 
     * 
     * @param id    The String ID of the {@link jnum.reporting.Reporter} object to be removed from CRUSH broadcasts.
     * @return The Reporter object that was removed from CRUSH message broadcasts, or null if no 
     *      Reporter by this ID was subscribed to CRUSH broadcasts.
     */
    public static Reporter removeReporter(String id) { return broadcaster.remove(id); }
    

    
    public static CRUSHConsoleReporter consoleReporter = new CRUSHConsoleReporter("crush-console");
    private static Broadcaster broadcaster = new Broadcaster("CRUSH-broadcast", consoleReporter);

    static {
        home = System.getenv("CRUSH");
    }

    public static final int TCP_CONNECTION_TIMEOUT = 1000;
    public static final int TCP_READ_TIMEOUT = 1000;


}

