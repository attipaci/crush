/*******************************************************************************
 * Copyright (c) 2017 Attila Kovacs <attila[AT]sigmyne.com>.
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


import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;

import crush.instrument.GenericInstrument;
import jnum.Configurator;
import jnum.Unit;
import jnum.Util;
import jnum.fits.FitsHeaderEditing;
import jnum.fits.FitsHeaderParsing;
import jnum.fits.FitsToolkit;
import jnum.math.Coordinate2D;
import jnum.parallel.Parallelizable;
import jnum.reporting.BasicMessaging;
import jnum.text.TableFormatter;
import jnum.util.HashCode;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public abstract class SourceModel implements Serializable, Cloneable, TableFormatter.Entries, BasicMessaging, 
Parallelizable, FitsHeaderEditing, FitsHeaderParsing {
    /**
     * 
     */
    private static final long serialVersionUID = -6355660797531285811L;

    private Instrument<?> instrument;

    private String id;
    private String commandLine;
    
    
    private Vector<Scan<?,?>> scans;
    private int generation = 0;
    
    private double integrationTime = 0.0;    //  TODO new...

    public boolean enableWeighting = true;
    public boolean enableLevel = true;
    public boolean enableBias = true;   
  
    private StringBuffer processBrief = new StringBuffer();

    public SourceModel(Instrument<?> instrument) {
        setInstrument(instrument);
    }
    
    public String getID() { return id; }
    
    public void setID(String value) { this.id = value; }
    
    public String getCommandLine() { return commandLine; }
    
    public void setCommandLine(String value) { this.commandLine = value; }

    public List<Scan<?,?>> getScans() { return scans; }

    public void setScans(Vector<Scan<?,?>> scans) { this.scans = scans; }
   
    public final Scan<?, ?> getScan(int i) { return scans.get(i); }
    
    public final Scan<?, ?> getFirstScan() { return scans.get(0); }
    
    public int numberOfScans() { return scans == null ? 0 : scans.size(); }

    public void addScan(Scan<?,?> scan) { scans.add(scan); }

    public int getGeneration() { return generation; }

    public void nextGeneration() { generation++; }

    
    public double getIntegrationTime() { return integrationTime; }

    public void addIntegrationTime(double time) { integrationTime += time; }
  

    public void setInstrument(Instrument<?> instrument) {
        this.instrument = instrument;
        instrument.setParent(this);
    }

    public Instrument<?> getInstrument() { return instrument; }

    @Override
    public int hashCode() {
        int hash = super.hashCode() ^ generation;
        if(instrument != null) hash ^= instrument.hashCode();
        if(scans != null) hash ^= HashCode.sampleFrom(scans);
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if(!(o instanceof SourceModel)) return false;
        if(!super.equals(o)) return false;

        SourceModel model = (SourceModel) o;
        if(generation != model.generation) return false;
        if(!Util.equals(instrument, model.instrument)) return false;
        if(!Util.equals(scans, model.scans)) return false;
        return true;
    }

    
 
    
    public Configurator getOptions() {
        return instrument.getOptions();
    }


    @Override
    public Object clone() {
        try { return super.clone(); }
        catch(CloneNotSupportedException e) { return null; }
    }	



    public boolean hasOption(String name) {
        return getOptions().isConfigured(name);
    }

    public Configurator option(String name) {
        return getOptions().get(name);
    }

    public boolean hasSourceOption(String name) { return hasOption("source." + name); }

    public Configurator sourceOption(String name) { return option("source." + name); }



    public final SourceModel getWorkingCopy() { return getWorkingCopy(true); }

    public SourceModel getWorkingCopy(boolean withContents) {
        SourceModel copy = (SourceModel) clone();
        return copy;
    }

    public SourceModel getCleanLocalCopy() {
        final int threads = getParallel();
        final ExecutorService executor = getExecutor();

        noParallel();
        setExecutor(null);

        SourceModel copy = getWorkingCopy(false);
        
        copy.resetProcessing();

        setParallel(threads);
        setExecutor(executor);

        return copy;
    }


    public void addProcessBrief(String blurp) {
        processBrief.append(blurp);
    }

    public String getProcessBrief() { return new String(processBrief); }

    public void clearProcessBrief() { processBrief = new StringBuffer(); }

    public void createFrom(Collection<? extends Scan<?,?>> collection) throws Exception {
        this.scans = new Vector<Scan<?,?>>(collection);
        for(Scan<?,?> scan : scans) scan.setSourceModel(this);		

        // TODO remove this if source.setInstrument works in Pipeline...
        double janskyPerBeam = scans.get(0).instrument.janskyPerBeam();
        for(Scan<?,?> scan : scans) {
            scan.setSourceModel(this);
            for(Integration<?,?> integration : scan)
                integration.gain *= integration.instrument.janskyPerBeam() / janskyPerBeam;
        }

    }


    public double getAverageResolution() {
        double sum = 0.0, sumw = 0.0;

        for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) if(integration.instrument != instrument) {
            double wG2 = scan.weight * integration.gain * integration.gain;
            double resolution = integration.instrument.getResolution();
            sum += wG2 * resolution * resolution;
            sumw += wG2;
        }

        return sumw > 0.0 ? Math.sqrt(sum / sumw) : instrument.getResolution();
    }

   

    public final void renew() {
        resetProcessing();
        clearContent();
    }

    public void resetProcessing() {
        generation = 0;
        integrationTime = 0.0;
    }
    
    protected abstract void clearContent();


    public final void add(SourceModel increment, double weight) {
        if(!increment.isValid()) return;
        
        generation = Math.max(generation, increment.generation);
        integrationTime += increment.integrationTime;

        enableLevel &= increment.enableLevel;
        enableWeighting &= increment.enableWeighting;
        enableBias &= increment.enableBias;
        
        addModel(increment, weight);
    }
    
    public abstract void addModel(SourceModel model, double weight);

    public abstract void add(Integration<?,?> integration);

    public abstract void process(Scan<?,?> scan);

    public void postprocess(Scan<?,?> scan) {}

    public abstract void sync(Integration<?,?> integration);

    public abstract void setBase();

    public abstract void write(String path) throws Exception;

    public abstract boolean isValid();

    public abstract Coordinate2D getReference();

    public void suggestions() {
        boolean scanningProblemOnly = false;

        int scansWithFewPixels = 0;
        for(Scan<?, ?> scan : scans) for(Integration<?, ?> integration : scan) if(!checkPixelCount(integration)) {
            scansWithFewPixels++;
            break;
        }
        if(scansWithFewPixels > 0) scanningProblemOnly = troubleshootFewPixels();
        else if(!isValid() && generation > 0) CRUSH.suggest(this, suggestMakeValid());
        else return; // no problems, so nothing left to do...

        if(!scanningProblemOnly) CRUSH.suggest(this, "          Please consult the README and/or the GLOSSARY for details.");
    }

    public boolean isScanningProblemOnly() {
        boolean speedProblemOnly = true;

        for(int i=0; i<scans.size(); i++) {
            Scan<?,?> scan = scans.get(i);
            boolean lowSpeed = false;

            for(Integration<?,?> integration : scan) if(!checkPixelCount(integration)) {
                int driftN = (int) Math.round(integration.filterTimeScale / integration.instrument.samplingInterval);
                if(driftN <= 1) lowSpeed = true;
                else speedProblemOnly = false;
            }

            if(lowSpeed) CRUSH.suggest(this, "            * Low scanning speed in " + scan.getID() + ".");
        }

        return speedProblemOnly;
    }

    public boolean troubleshootFewPixels() {
        warning("It seems that one or more scans contain too few valid pixels for\n" +
                "contributing to the source model. This may be just fine, and probably\n" +
                "indicates that something was sub-optimal with the affected scan(s).\n");

        if(isScanningProblemOnly()) return true;

        StringBuffer buf = new StringBuffer();
        buf.append(
                "          If you feel that CRUSH should try harder with the scans flagged\n");
        buf.append(
                "          otherwise, you may try:\n");



        Configurator options = getOptions();
        if(options.isConfigured("deep")) buf.append(
                "            * Reduce with 'faint' instead of 'deep'.\n");
        else if(options.isConfigured("faint")) buf.append(
                "            * Reduce with default settings instead of 'faint'.\n");
        else if(!options.isConfigured("bright")) buf.append(
                "            * Reduce with 'bright'.\n");

        instrument.troubleshootFewPixels();

        if(hasOption("mappingpixels") || hasOption("mappingfraction")) {
            buf.append(
                    "            * Adjust 'mappingpixels' or 'mappigfraction' to allow source\n");
            buf.append(
                    "              extraction with fewer pixels.\n");
        }

        CRUSH.suggest(this, "\n" + new String(buf));

        return false;
    }

    public String suggestMakeValid() {
        return "            * Check the console output for any problems when reading scans.\n";
    }

    public abstract void process() throws Exception;


    public void sync() throws Exception {
        // Coupled with blanking...
        if(hasSourceOption("nosync")) return;
        if(hasSourceOption("coupling")) addProcessBrief("(coupling) ");

        addProcessBrief("(sync) ");

        final int nParms = countPoints();
      
        for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) {
            sync(integration);
            integration.sourceGeneration++;
            integration.scan.sourcePoints = nParms;
        }

        nextGeneration();
       
        setBase(); 
    }

    public double getBlankingLevel() {
        return hasOption("blank") ? option("blank").getDouble() : Double.NaN;
    }

    public double getClippingLevel() {
        return hasOption("clip") ? option("clip").getDouble() : Double.NaN;
    }

    public double getPointSize() { return instrument.getPointSize(); }

    public double getSourceSize() { return instrument.getSourceSize(); }

    public abstract String getSourceName();

    public abstract int countPoints();

    public abstract Unit getUnit();
    
    public Unit getNativeUnit() {
        return new Unit(hasOption("dataunit") ? option("dataunit").getValue() : "U", 1.0);
    }

    // Replace character sequences that are problematic in filenames with underscores "_"
    public String getCanonicalSourceName() {
        StringTokenizer tokens = new StringTokenizer(getSourceName(), " \t\r*?/\\\"");
        StringBuffer canonized = new StringBuffer();
        while(tokens.hasMoreTokens()) {
            canonized.append(tokens.nextToken());
            if(tokens.hasMoreTokens()) canonized.append('_');
        }
        return new String(canonized);
    }

    public String getDefaultCoreName() {
        Scan<?,?> first, last;
        first = last = scans.get(0);

        for(Scan<?,?> scan : scans) {
            if(scan.compareTo(first) < 0) first = scan;
            else if(scan.compareTo(last) > 0) last = scan;			
        }

        String name = getCanonicalSourceName() + "." + first.getID();
        if(scans.size() > 1) if(!first.getID().equals(last.getID())) name += "-" + last.getID();

        return name;
    }

    /*
	public ArrayList<Integration<?,?>> getIntegrations() {
		final ArrayList<Integration<?,?>> integrations = new ArrayList<Integration<?,?>>();
		for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) integrations.add(integration);
		return integrations;		
	}
     */

    public boolean checkPixelCount(Integration<?,?> integration) {
        Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels(0);
        int nObs = integration.instrument.getObservingChannels().size();

        // If there aren't enough good pixels in the scan, do not generate a map...
        if(integration.hasOption("mappingpixels")) if(pixels.size() < integration.option("mappingpixels").getInt()) {
            integration.comments += "(!ch)";
            return false;
        }

        // If there aren't enough good pixels in the scan, do not generate a map...
        if(integration.hasOption("mappingfraction")) if(pixels.size() < integration.option("mappingfraction").getDouble() * nObs) {
            integration.comments += "(!ch%)";
            return false;
        }

        return true;
    }


    public String getASCIIHeader() {
        StringBuffer header = new StringBuffer(
                "# CRUSH version: " + CRUSH.getFullVersion() + "\n" +
                        "# Instrument: " + instrument.getName() + "\n" +
                        "# Object: " + getSourceName() + "\n");

        if(!scans.isEmpty()) {
            Scan<?,?> firstScan = scans.get(0);	
            header.append("# Equatorial: " + firstScan.equatorial + "\n");
        }

        header.append("# Scans: ");

        for(int i=0; i<scans.size(); i++) header.append(scans.get(i).getID() + " ");
        header.append("\n");

        return new String(header);
    }


    @Override
    public Object getTableEntry(String name) {
        return null;
    }

    public double getGnuplotPNGFontScale(int size) {
        double dpc = (double) size / scans.size();

        if(dpc < 16.0) return 0.33;
        if(dpc < 20.0) return 0.4;
        if(dpc < 24.0 ) return 0.5;
        if(dpc < 32.0) return 0.6;
        if(dpc < 40.0) return 0.8;
        return 1.0;

    }
    
    
    @Override
    public void parseHeader(Header header) throws Exception {
        if(header.containsKey("INSTRUME")) {
            instrument = Instrument.forName(header.getStringValue("INSTRUME"));
            if(instrument == null) instrument = new GenericInstrument(header.getStringValue("INSTRUME"));
        }
        else instrument = new GenericInstrument("unknown");


        if(instrument instanceof GenericInstrument) {
            GenericInstrument generic = (GenericInstrument) instrument;

            if(header.containsKey("TELESCOP")) generic.setTelescopeName(header.getStringValue("TELESCOP"));
            else generic.setTelescopeName("unknown");
        }

        if(instrument.getOptions() == null) instrument.setOptions(new Configurator());

        instrument.parseImageHeader(header);
    }


    @Override
    public void editHeader(Header header) throws HeaderCardException { 
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(new HeaderCard("SCANS", scans.size(), "The number of scans in this composite image."));
        c.add(new HeaderCard("INTEGRTN", integrationTime / Unit.s, "The total integration time in seconds."));
        c.add(new HeaderCard("V2JY", instrument.janskyPerBeam(), "1 Jy/beam in instrument data units."));  

        if(instrument != null) {
            instrument.editImageHeader(scans, header);
            if(instrument.getOptions() != null) instrument.getOptions().editHeader(header);
        }

        CRUSH.addHistory(header);
        if(instrument != null) instrument.addHistory(header, scans);
    }

    
    public void addScanHDUsTo(Fits fits) throws FitsException, IOException {
        if(instrument == null) return;
        if(!hasOption("write.scandata")) return;    
        for(Scan<?,?> scan : scans) fits.addHDU(scan.getSummaryHDU(getOptions()));
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


    public synchronized SourceModel getCleanRecycledLocalCopy() {	
        if(recycler != null) if(!recycler.isEmpty()) {
            try { 
                SourceModel model = recycler.take(); 
                model.renew();
                return model;
            }
            catch(InterruptedException e) { error(e); }
        }

        return getCleanLocalCopy();
    }


    public synchronized void recycle() { 
        if(recycler == null) return;
        if(recycler.remainingCapacity() <= 0) {
            warning("Source recycler overflow.");
            return;
        }
        recycler.add(this);
    }

    public static synchronized void clearRecycler() { if(recycler != null) recycler.clear(); }

    public static synchronized void setRecyclerCapacity(int size) {
        if(size <= 0) recycler = null;
        else recycler = new ArrayBlockingQueue<SourceModel>(size);
    }


    public static ArrayBlockingQueue<SourceModel> recycler;

   
}

