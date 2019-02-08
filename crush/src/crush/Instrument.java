/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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
import java.lang.reflect.Field;
import java.util.*;
import java.text.*;

import crush.instrument.GeometricIndexed;
import crush.instrument.InstantFocus;
import crush.instrument.NonOverlapping;
import crush.instrument.Overlap;
import crush.instrument.SkyGradient;
import crush.motion.AccelerationResponse;
import crush.motion.ChopperResponse;
import crush.motion.PointingResponse;
import crush.sourcemodel.*;
import jnum.Configurator;
import jnum.Constant;
import jnum.ExtraMath;
import jnum.LockedException;
import jnum.Unit;
import jnum.astro.AstroTime;
import jnum.data.DataPoint;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;
import jnum.data.image.FlatGrid2D;
import jnum.data.image.Grid2D;
import jnum.fits.FitsToolkit;
import jnum.io.LineParser;
import jnum.math.Coordinate2D;
import jnum.math.Range;
import jnum.math.Vector2D;
import jnum.projection.DefaultProjection2D;
import jnum.projection.Projector2D;
import jnum.reporting.BasicMessaging;
import jnum.text.SmartTokenizer;
import jnum.text.TableFormatter;
import nom.tam.fits.*;
import nom.tam.util.Cursor;

/**
 * A class that captures an instrument's configuration/operation state (properties) at a given point in time, such as for a chunk 
 * of contiguous stream of data, such as a scan or an integration, together with its own set of configuration options.
 * <p>
 * 
 * Every scan/integration should have a fully independent <code>Instrument</code> object (with all their channels and properties
 * also being fully independent) s.t. modifications to one scan/integration will not affect another.
 * Independent instrument states may be derived from one another, using {@link #copy()}, migrating
 * all properties of one instrument to another indepenent one. 
 * <p>
 * 
 * Instruments primarily consists of a number of detector channels, which provide timestream data (see {@link Frame}).
 * These channels may be organized into various {@link ChannelGroup}s and {@link Pixel}s, after the channels have
 * been fully populated (see {@link #createEnsembles()}, {@link #createLayout()}). 
 * <p>
 * 
 * @author Attila Kovacs <attila@sigmyne.com>
 *
 * @param <ChannelType>
 */
public abstract class Instrument<ChannelType extends Channel> extends ChannelGroup<ChannelType> 
implements TableFormatter.Entries, BasicMessaging {	
    /**
     * 
     */
    private static final long serialVersionUID = -7651803433436713372L;

    private Configurator options, initialOptions;
    private PixelLayout layout;

    private double frequency;
    private double resolution;
    private double overlapPointSize = Double.NaN;

    public double integrationTime;
    public double samplingInterval;

    public double gain = 1.0; // The electronic amplification

    public int storeChannels; // The number of channels stored in the data files...
    public int mappingChannels;

    public Hashtable<String, ChannelGroup<ChannelType>> groups;
    public Hashtable<String, ChannelDivision<ChannelType>> divisions;
    public Hashtable<String, Modality<?>> modalities;

    private Object parent;

    private boolean hasEnsembles = false;
    private boolean standardWeights = false;

    public Instrument(String name) {
        super(name);
        initialOptions = options;
    }

    public Instrument(String name, int size) {
        super(name, size);
        storeChannels = size;
    }

    @Override
    public Instrument<ChannelType> copy() {
        Instrument<ChannelType> copy = (Instrument<ChannelType>) super.copy();

        for(Channel channel : copy) channel.instrument = copy;

        if(options != null) copy.options = options.copy();

        if(layout != null) copy.layout = null;

        // TODO this needs to be done properly???
        copy.groups = null;
        copy.divisions = null;
        copy.modalities = null;

        if(hasEnsembles) {
            copy.hasEnsembles = false;
            copy.createEnsembles();
        }

        if(layout != null) copy.layout = layout.copyFor(copy);

        if(overlapPointSize > 0.0) {
            copy.overlapPointSize = Double.NaN;
            copy.calcOverlaps(overlapPointSize);
        }

        return copy;
    }
    
    

    public void setParent(Object o) { this.parent = o; }

    public Object getParent() { return parent; }


    // Load the static instrument settings, which are not meant to be date-dependent...
    public void setOptions(Configurator options) {
        this.options = options;

        hasEnsembles = false;
        initialOptions = null;
    }

    public Configurator getOptions() {
        return options;
    }

    public Configurator getInitialOptions() { return initialOptions; }


    public String getOutputPath() {
        if(hasOption("outpath")) return option("outpath").getPath();
        return ".";
    }


    public Projector2D<?> getProjectorInstance(Coordinate2D reference) {      
        return new Projector2D<Coordinate2D>(new DefaultProjection2D(reference));      
    }


    public Grid2D<?> getGridInstance() { return new FlatGrid2D(); }

    
    protected abstract PixelLayout getLayoutInstance();

    
    public PixelLayout createLayout() {
        layout = getLayoutInstance(); 
        return layout;
    }

    
    public PixelLayout getLayout() { return layout; }

    
    protected final void createEnsembles() {
        if(isEmpty()) return;

        createGroups();
        createDivisions();
        createModalities(); 

        hasEnsembles = true;
    }
    
    @Override
    public boolean add(ChannelType channel) {
        channel.index = size();
        return super.add(channel);
    }



    /**
     * Applies configurations settings based on its own {@link Configurator} options. Typically one should
     * configure the instrument <i>after</i> reading the stored configuration from files (so that they can 
     * override the stored information) but <i>before</i> reading actual data, since the interpretation of 
     * the data may depend on the specified instrument options.
     *             
     * @see #setOptions(Configurator)
     * @see #setObjectOptions(String)
     * @see #setDateOptions(double)
     * @see #setMJDOptions(double)
     * @see #setFitsHeaderOptions(Header)
     * @see #setOption(String)
     */
    public void configure() {
        initialOptions = options.copy();

        if(hasOption("beam")) setResolution(option("beam").getDouble() * getSizeUnit().value());

        if(hasOption("frequency")) frequency = option("frequency").getDouble() * Unit.Hz;
        else if(hasOption("wavelength")) frequency = Constant.c / (option("wavelength").getDouble() * Unit.um);

        if(hasOption("resolution")) setResolution(option("resolution").getDouble() * getSizeUnit().value());
        if(hasOption("gain")) gain = option("gain").getDouble();
    }


    /**
     * Validates this instrument state for the given scan, populating all fields and properties given the
     * options for the instrument, and loading appropriate configuration files.
     * <p>
     * 
     * The instrument should be populated with all canonical channels prior to calling this method (e.g. via
     * a {@link #populate(int)} call or otherwise).
     * <p>
     * 
     * If the pixel layout has not been initialized, this will call {@link #getLayoutInstance()} as necessary
     * before calling {@link PixelLayout#validate()}.
     * <p>
     * 
     * After the validation the instrument should have all its canonical channels and pixels added and
     * configured. Pruning (slimming) of these may happen at a later stage, during {@link Integration#validate()}, if the 
     * <code>noslim</code> option isn't set.
     * <p>
     *    
     */
    public void validate() {
        if(isEmpty()) return;

        reindex(); 
        
        if(layout == null) createLayout();
        layout.validate();

        createEnsembles();

        loadChannelData();   

        flagChannels();

        if(hasOption("flatweights")) flattenWeights();

        if(hasOption("uniform")) for(Channel channel : this) channel.uniformGains();

        if(hasOption("gainnoise")) {
            Random random = new Random();
            double level = option("gainnoise").getDouble();
            for(Channel channel : this) channel.gain *= 1.0 + level * random.nextGaussian();
        }

        if(hasOption("sourcegains")) {
            info("Merging channel couplings into correlated signal gains.");
            for(Channel channel : this) {
                channel.gain *= channel.coupling;
                channel.coupling = 1.0;
            }
        }

        try { normalizeArrayGains(); }
        catch(Exception e) {
            warning("Normalization failed: " + e.getMessage());
            if(CRUSH.debug) CRUSH.trace(e);
        }

        if(hasOption("source.fixedgains")) {
            info("Will use static source gains.");
            fixedSourceGains();
        }

        if(hasOption("jackknife.channels")) jackknife();

        for(Channel channel : this) {
            channel.spikes = 0;
            channel.dof = 1.0;
            if(Double.isNaN(channel.variance)) channel.variance = channel.weight > 0.0 ? 1.0 / channel.weight : 0.0;
        }	
        census();
        
        if(CRUSH.debug) {
            debug("mapping channels: " + mappingChannels);
            debug("mapping pixels: " + getMappingPixels(~getSourcelessChannelFlags()).size());
        }
    }
    

    public void jackknife() {
        notify("JACKKNIFE: Randomly inverted channels in source.");
        for(Channel channel : this) if(Math.random() < 0.5) channel.coupling *= -1.0;
    }

    protected void flagChannels() {
        if(hasOption("blind")) setBlindChannels(option("blind").getList()); 
        if(hasOption("flag")) flagChannels(option("flag").getList()); 

        if(options.containsKey("flag")) flagFields(options.option("flag"));

        for(Channel channel : this) if(channel.weight == 0.0) channel.flag(Channel.FLAG_DEAD);

        setChannelFlagDefaults();
    }

    protected void setChannelFlagDefaults() {
        // Set DEAD / BLIND channel defaults...
        final int discardFlags = Channel.FLAG_DEAD | Channel.FLAG_DISCARD;

        for(Channel channel : this) {
            if(channel.isFlagged(discardFlags)) 
                channel.coupling = channel.gain = channel.weight = channel.variance = 0.0;
            if(channel.isFlagged(Channel.FLAG_BLIND))
                channel.coupling = 0.0;
        }
    }


    public int getSourcelessChannelFlags() { return Channel.FLAG_BLIND | Channel.FLAG_DEAD | Channel.FLAG_DISCARD; }

    protected float normalizeArrayGains() throws Exception {
        info("Normalizing relative channel gains.");    
        CorrelatedMode array = (CorrelatedMode) modalities.get("obs-channels").get(0);
        return array.normalizeGains();
    }


    // TODO check for incompatible scans
    public void validate(Vector<Scan<?>> scans) throws Exception {
        if(hasOption("jackknife.alternate")) {
            notify("JACKKNIFE! Alternating scans.");
            for(int i=scans.size(); --i >= 0; ) if(i%2 != 0) 
                for(Integration<?> subscan : scans.get(i)) subscan.gain *= -1.0;
        }  
    }
    
    public void registerConfigFile(String fileName) {}

    public abstract String getTelescopeName();

    public double janskyPerBeam() {
        if(hasOption("jansky")) {
            double jansky = option("jansky").getDouble();
            if(hasOption("jansky.inverse")) jansky = 1.0 / jansky;
            return jansky / getDataUnit().value();
        }
        return 1.0; // Safety pin...
    }


    public double kelvin() {
        if(hasOption("kelvin")) return option("kelvin").getDouble();
        else if(hasOption("k2jy")) return option("k2jy").getDouble() * janskyPerBeam();
        else return Double.NaN;		
        // TODO safety return 1.0?
    }

    public Unit getDataUnit() {
        if(hasOption("dataunit")) return Unit.get(option("dataunit").getValue());
        return Unit.counts;		
    }


    public void setDateOptions(double MJD) {
        setMJDOptions(MJD);
        
        if(!options.containsKey("date")) return;

        Hashtable<String, Vector<String>> settings = option("date").conditionals;

        for(String rangeSpec : settings.keySet()) {
            try {			
                StringTokenizer tokens = new StringTokenizer(rangeSpec, "-");
                Range mjdRange = new Range();
                String spec = tokens.nextToken().replace('.', '-');
                mjdRange.setMin(spec.equals("*") ? Double.NEGATIVE_INFINITY : AstroTime.forFitsTimeStamp(spec).getMJD());
                mjdRange.setMax(Double.POSITIVE_INFINITY);
                if(tokens.hasMoreTokens()) {
                    spec = tokens.nextToken().replace('.', '-');
                    mjdRange.setMax(spec.equals("*") ? Double.POSITIVE_INFINITY : AstroTime.forFitsTimeStamp(spec).getMJD());
                }

                if(mjdRange.contains(MJD)) {
                    //debug("Setting options for " + rangeSpec);
                    options.parseAll(settings.get(rangeSpec));
                }
            }
            catch(ParseException e) { warning(e); }
        }
    }
    
    private void setMJDOptions(double MJD) {
        if(!options.containsKey("mjd")) return;

        Hashtable<String, Vector<String>> settings = option("mjd").conditionals;

        for(String rangeSpec : settings.keySet()) 
            if(Range.from(rangeSpec, true).contains(MJD)) options.parseAll(settings.get(rangeSpec));        
    }

    public void setSerialOptions(int serialNo) {
        if(!options.containsKey("serial")) return;

        // Make options an independent set of options, setting MJD specifics...
        Hashtable<String, Vector<String>> settings = option("serial").conditionals;
        for(String rangeSpec : settings.keySet()) if(Range.from(rangeSpec, true).contains(serialNo)) {
            //debug("Setting options for " + rangeSpec);
            options.parseAll(settings.get(rangeSpec));
        }
    }

    public void setObjectOptions(String sourceName) {
        //debug("Setting local options for " + sourceName);
        sourceName = sourceName.toLowerCase();

        if(!options.containsKey("object")) return;

        // Make options an independent set of options, setting object specifics...
        Hashtable<String, Vector<String>> settings = option("object").conditionals;
        for(String spec : settings.keySet()) if(sourceName.startsWith(spec)) {
            options.parseAll(settings.get(spec));
        }
    }

    public void setFitsHeaderOptions(Header header) {
        if(!options.containsKey("fits")) return;

        info("Setting FITS header options.");

        final Hashtable<String, Vector<String>> conditionals = options.option("fits").conditionals;

        for(String condition : conditionals.keySet()) {		    
            StringTokenizer tokens = new StringTokenizer(condition, "?");
            if(!tokens.hasMoreTokens()) continue;
            String key = tokens.nextToken().toUpperCase();

            if(!header.containsKey(key)) continue;
            String value = tokens.hasMoreTokens() ? tokens.nextToken() : null;		 
            String cardValue = header.findCard(key).getValue();

            if(value == null) options.parseAll(conditionals.get(condition));
            else if(value.charAt(0) == '!') {
                if(!cardValue.equalsIgnoreCase(value.substring(1))) options.parseAll(conditionals.get(condition));
            }
            else if(cardValue.equalsIgnoreCase(value)) options.parseAll(conditionals.get(condition));
        }

        setFitsAssignments(header);
    }	

    protected void setFitsAssignments(Header header) {
        if(!options.containsKey("fits.assign")) return;

        Configurator assignments = options.option("fits.assign");

        for(String fitsKey : assignments.branches.keySet()) {
            fitsKey = fitsKey.toUpperCase();
            if(header.containsKey(fitsKey)) {
                String arg = options.option("fits.assign." + fitsKey).getValue();
                StringTokenizer tokens = new StringTokenizer(arg, " \t:=");
                if(tokens.countTokens() == 0) continue;
                String option = tokens.nextToken();

                try { 
                    String value = header.findCard(fitsKey).getValue();
                    CRUSH.debug(Scan.class, "FITS assign " + fitsKey + " --> " + option + " = " + value);
                    options.parse(option + "=" + value); 
                }
                catch(LockedException e) {}
            }       
        }

    }

    public String getDataLocationHelp() {
        return "";
    }

    public String getMapConfigHelp() {
        return "";
    }

    public String getScanOptionsHelp() {
        return "";
    }

    public String getReductionModesHelp() {
        return "";
    }

    public boolean hasOption(String name) {
        return options.hasOption(name);
    }

    public Configurator option(String name) {
        return options.option(name);
    }

    public boolean setOption(String line) {
        return options.setOption(line);
    }

    public boolean forget(String key) {
        return options.forgetSilent(key);
    }

    public Scan<?> readScan(String descriptor) throws Exception {
        Scan<?> scan = getScanInstance();
        scan.read(descriptor, true);
        return scan;
    }

    public void flagFields(Configurator option) {
        for(String name : option.getKeys(false)) if(option.hasOption(name)) flagField(name, option.option(name).getList());   
    }

    public void flagField(String fieldName, List<String> specs) {
        Channel firstChannel = get(0);

        Field field = firstChannel.getFieldFor(fieldName);
        if(field == null) {
            warning("No accessible field " + fieldName + " in " + firstChannel.getClass().getSimpleName() + "."); 
            return;
        } 

        flag(field, specs);
    }


    public void flag(Field pixelField, List<String> specs) {
        info("Flagging channels by " + pixelField.getName() + " values");

        for(String spec : specs) {
            try { 
                flag(pixelField, Integer.parseInt(spec)); 
                continue;
            }
            catch(NumberFormatException e) {}

            Range r = Range.from(spec, false);
            if(r.isEmpty()) r = Range.from(spec, true);
            if(r.isEmpty()) {
                warning("Could not parse flag." + pixelField.getName() + " indices from: " + spec);
                continue;
            }

            int from = (int) r.min();
            int to = (int) r.max();

            for(int i=from; i<=to; i++) flag(pixelField, i);   
        }

    }

    public void flag(Field pixelField, int value) {
        for(ChannelType channel : this) {
            try { if(pixelField.getInt(channel) == value) channel.flag(Channel.FLAG_DEAD); }
            catch(IllegalAccessException e) {}
        }
    }

    // TODO ability to flag groups divisions...
    // perhaps flag.group, and flag.division...
    public void flagChannels(Collection<String> list) {
        ChannelLookup<ChannelType> lookup = new ChannelLookup<ChannelType>(this);
        ChannelGroup<ChannelType> channels = new ChannelGroup<ChannelType>("flag", list.size());

        for(String spec : list) channels.add(spec, lookup); 

        info("Flagging " + channels.size() + " channels.");

        for(ChannelType channel : channels) channel.flag(Channel.FLAG_DEAD);
    }

    public void killChannels(final int pattern, final int ignorePattern) {
        // Anything flagged as blind so far should be flagged as dead instead...
        for(Channel channel : this) if(channel.isFlagged(pattern)) if(channel.isUnflagged(ignorePattern)) {
            channel.unflag(pattern);
            channel.flag(Channel.FLAG_DEAD);
        }
    }

    public void killChannels(int pattern) {
        killChannels(pattern, 0);
    }


    public void setBlindChannels(Collection<String> list) {
        killChannels(Channel.FLAG_BLIND);

        info("Defining " + list.size() + " blind channels.");		

        ChannelLookup<ChannelType> lookup = new ChannelLookup<ChannelType>(this);

        for(String id : list) {
            ChannelType channel = lookup.get(id);
            if(channel != null) {
                channel.unflag();
                channel.flag(Channel.FLAG_BLIND);
            }
        }
    }

    protected void loadChannelData() {
        if(hasOption("pixeldata")) {
            try { loadChannelData(option("pixeldata").getPath()); }
            catch(IOException e) { warning("Cannot read pixel data. Using default gains & flags."); }
        }	
        
        
        if(hasOption("wiring")) { 
            try { readWiring(option("wiring").getPath()); } 
            catch(IOException e) { warning("Cannot read wiring data. Specific channel divisions not established."); }
        }
    }

    /**
     * Subclasses may use this method to populate channel fields from data placed in files. This method is
     * called with the argument set by the <code>wiring</code> configuration option.
     * 
     * 
     * @param fileName      The path/name of the file containing the channel field data.
     * @throws IOException  If the file cannot be found / read.
     */
    protected void readWiring(String fileName) throws IOException {}

    public double getFrequency() { return frequency; }

    protected void setFrequency(double Hz) { frequency = Hz; }

    public double getResolution() { return resolution; }

    public void setResolution(double value) { resolution = value; }

    public final double getPointSize() { return getResolution(); }

    public double getSourceSize() {
        double sourceSize = hasOption("sourcesize") ? 
                ExtraMath.hypot(option("sourcesize").getDouble() * getSizeUnit().value(), getPointSize()) : getPointSize();
                return sourceSize;
    }

    public Unit getSizeUnit() { return arcsec; }

    public void census() {
        mappingChannels = 0;      
        for(Channel channel : getObservingChannels()) if(channel.isUnflagged()) if(channel.weight > 0.0) mappingChannels++;
    }

    public String getConfigPath() {
        return CRUSH.home + File.separator + "config" + File.separator + getName() + File.separator;
    }

    public ChannelGroup<ChannelType> getLiveChannels() {
        return groups.get("live");
    }

    public ChannelGroup<ChannelType> getDetectorChannels() {
        return groups.get("detectors");
    }

    public ChannelGroup<ChannelType> getObservingChannels() {
        return groups.get("obs-channels");
    }

    public ChannelGroup<ChannelType> getSensitiveChannels() {
        return groups.get("sensitive");
    }

    public ChannelGroup<ChannelType> getBlindChannels() {
        return groups.get("blinds");
    }

    public int getNonDetectorFlags() { return Channel.FLAG_DEAD; }

    protected void createGroups() {
        groups = new Hashtable<String, ChannelGroup<ChannelType>>();

        addGroup("all", createGroup());
        addGroup("live", createGroup().discard(Channel.FLAG_DEAD));
        addGroup("detectors", createGroup().discard(getNonDetectorFlags()));
        addGroup("obs-channels", createGroup().discard(getNonDetectorFlags() | Channel.FLAG_BLIND));
        addGroup("sensitive", createGroup().discard(getNonDetectorFlags() | Channel.FLAG_BLIND | Channel.FLAG_SENSITIVITY));
        addGroup("blinds", createGroup().discard(getNonDetectorFlags()).discard(Channel.FLAG_BLIND, ChannelGroup.KEEP_ANY_FLAG));

        if(options.containsKey("group")) {
            ChannelLookup<ChannelType> lookup = new ChannelLookup<ChannelType>(this);

            Configurator option = option("group");
            for(String name : option.getTimeOrderedKeys()) {
                ChannelGroup<ChannelType> channels = new ChannelGroup<ChannelType>(name);
                for(String spec : option.option(name).getList()) channels.add(spec, lookup, Channel.FLAG_DEAD);
                addGroup(name, channels);
            }
        }
    }

    protected void createDivisions() {
        divisions = new Hashtable<String, ChannelDivision<ChannelType>>();

        addDivision(new ChannelDivision<ChannelType>("all", groups.get("all")));
        addDivision(new ChannelDivision<ChannelType>("live", groups.get("live")));
        addDivision(new ChannelDivision<ChannelType>("detectors", groups.get("detectors")));
        addDivision(new ChannelDivision<ChannelType>("obs-channels", groups.get("obs-channels")));
        addDivision(new ChannelDivision<ChannelType>("sensitive", groups.get("sensitive")));
        addDivision(new ChannelDivision<ChannelType>("blinds", groups.get("blinds")));

        if(options.containsKey("division")) {
            Configurator option = option("division");
            for(String name : option.getTimeOrderedKeys()) {
                ChannelDivision<ChannelType> division = new ChannelDivision<ChannelType>(name);
                for(String groupName : option.option(name).getList()) {
                    ChannelGroup<ChannelType> group = groups.get(groupName);
                    if(group != null) division.add(group);
                    else warning("Channel group '" + groupName + "' is undefined.");
                }
                addDivision(division);
            }
        }
    }

    protected void createModalities() {
        modalities = new Hashtable<String, Modality<?>>();

        try { addModality(new CorrelatedModality("all", "Ca", divisions.get("all"), Channel.class.getField("gain"))); }
        catch(NoSuchFieldException e) { error(e); }

        try { addModality(new CorrelatedModality("live", "Cl", divisions.get("live"), Channel.class.getField("gain"))); }
        catch(NoSuchFieldException e) { error(e); }

        try { addModality(new CorrelatedModality("detectors", "Cd", divisions.get("detectors"), Channel.class.getField("gain"))); }
        catch(NoSuchFieldException e) { error(e); }

        try { addModality(new CorrelatedModality("obs-channels", "C", divisions.get("obs-channels"), Channel.class.getField("gain"))); }
        catch(NoSuchFieldException e) { error(e); }

        try { addModality(new CorrelatedModality("coupling", "Cc", divisions.get("obs-channels"), Channel.class.getField("coupling"))); }
        catch(NoSuchFieldException e) { error(e); }

        try { addModality(modalities.get("obs-channels").new CoupledModality("sky", "Cs", Channel.class.getField("coupling"))); }
        catch(NoSuchFieldException e) { error(e); }

        try { addModality(modalities.get("obs-channels").new NonLinearity("nonlinearity", "n", Channel.class.getField("nonlinearity"))); } 
        catch(NoSuchFieldException e) { error(e); }

        // Gradients
        CorrelatedMode common = (CorrelatedMode) modalities.get("obs-channels").get(0);

        CorrelatedMode gx = common.new CoupledMode(new SkyGradient.X());
        gx.name = "gradients:x";
        CorrelatedMode gy = common.new CoupledMode(new SkyGradient.Y());
        gy.name = "gradients:y";

        CorrelatedModality gradients = new CorrelatedModality("gradients", "G");
        gradients.add(gx);
        gradients.add(gy);

        addModality(gradients);

        // Add pointing response modes...
        addModality(new Modality<PointingResponse>("telescope-x", "Tx", divisions.get("detectors"), PointingResponse.class));
        addModality(new Modality<PointingResponse>("telescope-y", "Ty", divisions.get("detectors"), PointingResponse.class));

        // Add acceleration response modes...
        addModality(new Modality<AccelerationResponse>("accel-x", "ax", divisions.get("detectors"), AccelerationResponse.class));
        addModality(new Modality<AccelerationResponse>("accel-y", "ay", divisions.get("detectors"), AccelerationResponse.class));
        addModality(new Modality<AccelerationResponse>("accel-x^2", "axs", divisions.get("detectors"), AccelerationResponse.class));
        addModality(new Modality<AccelerationResponse>("accel-y^2", "ays", divisions.get("detectors"), AccelerationResponse.class));
        addModality(new Modality<AccelerationResponse>("accel-|x|", "a|x|", divisions.get("detectors"), AccelerationResponse.class));
        addModality(new Modality<AccelerationResponse>("accel-|y|", "a|y|", divisions.get("detectors"), AccelerationResponse.class));
        addModality(new Modality<AccelerationResponse>("accel-mag", "am", divisions.get("detectors"), AccelerationResponse.class));
        addModality(new Modality<AccelerationResponse>("accel-norm", "an", divisions.get("detectors"), AccelerationResponse.class));

        // Add Chopper response modes...
        addModality(new Modality<ChopperResponse>("chopper-x", "cx", divisions.get("detectors"), ChopperResponse.class));
        addModality(new Modality<ChopperResponse>("chopper-y", "cy", divisions.get("detectors"), ChopperResponse.class));
        addModality(new Modality<ChopperResponse>("chopper-x^2", "cxs", divisions.get("detectors"), ChopperResponse.class));
        addModality(new Modality<ChopperResponse>("chopper-y^2", "cys", divisions.get("detectors"), ChopperResponse.class));
        addModality(new Modality<ChopperResponse>("chopper-|x|", "c|x|", divisions.get("detectors"), ChopperResponse.class));
        addModality(new Modality<ChopperResponse>("chopper-|y|", "c|y|", divisions.get("detectors"), ChopperResponse.class));
        addModality(new Modality<ChopperResponse>("chopper-mag", "cm", divisions.get("detectors"), ChopperResponse.class));
        addModality(new Modality<ChopperResponse>("chopper-norm", "cn", divisions.get("detectors"), ChopperResponse.class));


        if(hasOption("blind")) {
            try {
                CorrelatedModality blinds = new CorrelatedModality("blinds", "Bl", divisions.get("blinds"), getChannelInstance(-1).getClass().getField("temperatureGain")); 
                for(CorrelatedMode mode : blinds) mode.skipFlags = Channel.FLAG_DEAD;
                addModality(blinds);
            }
            catch(NoSuchFieldException e) {
                warning(getChannelInstance(-1).getClass().getSimpleName() + " has no 'temperatureGain' for blind correction");	
            }
        }

        modalities.get("all").setGainFlag(Channel.FLAG_GAIN);
        modalities.get("live").setGainFlag(Channel.FLAG_GAIN);
        modalities.get("detectors").setGainFlag(Channel.FLAG_GAIN);
        modalities.get("obs-channels").setGainFlag(Channel.FLAG_GAIN);

        if(options.containsKey("division")) {
            Configurator option = option("division");
            for(String name : option.getTimeOrderedKeys()) {
                String id = option.hasOption(name + ".id") ? option(name + ".id").getValue() : name;
                if(option.hasOption(name + ".gainfield")) {
                    try {
                        Field field = getChannelInstance(-1).getClass().getField(option(name + ".gainfield").getValue());
                        addModality(new CorrelatedModality(name, id, divisions.get(name), field));
                    } 
                    catch(NoSuchFieldException e) { 
                        warning("No gain field '" + option(name + ".gainfield").getValue() + "'."); 
                    }
                }
                else addModality(new CorrelatedModality(name, id, divisions.get(name)));

                if(option.hasOption(name + ".gainflag")) {
                    modalities.get(name).setGainFlag(option(name + ".gainflag").getInt());
                }
            }
        }

    }

    public List<String> getDivisionNames() {
        ArrayList<String> keys = new ArrayList<String>();
        for(String key : divisions.keySet()) keys.add(key);
        Collections.sort(keys);
        return keys;
    }

    public List<String> getModalityNames() {
        ArrayList<String> keys = new ArrayList<String>();
        for(String key : modalities.keySet()) keys.add(key);
        Collections.sort(keys);
        return keys;
    }

    // TODO parallelize...
    public void flattenWeights() {
        info("Switching to flat channel weights.");
        double sum = 0.0, sumG2 = 0.0;
        for(Channel channel : this) if(channel.isUnflagged(Channel.HARDWARE_FLAGS)) {
            double G2 = channel.gain * channel.gain;
            sum += channel.weight * G2;
            sumG2 += G2;
        }
        double w = sumG2 > 0.0 ? sum / sumG2 : 1.0;

        for(Channel channel : this) channel.weight = w;
    }


    public void uniformGains(Field field) throws IllegalAccessException {
        for(Channel channel : this) field.setDouble(channel, 1.0);		
    }

    private boolean fixedSourceGains = false;

    public void fixedSourceGains() {
        if(fixedSourceGains) return;
        for(Channel channel : this) channel.coupling *= channel.gain;
        fixedSourceGains = true;
    }

    public String getChannelDataHeader() {
        return "ch\tgain\tweight\t\tflag";
    }

    public String getChannelFlagGuide(String prepend) {	    
        if(isEmpty()) return prepend;

        StringBuffer buf = new StringBuffer();

        ArrayList<Integer> values = new ArrayList<Integer>(Channel.flagSpace.getValues());
        Collections.sort(values);

        for(int i=0; i<values.size(); i++) buf.append(prepend + Channel.flagSpace.get(values.get(i)) + "\n");

        return new String(buf);
    }

    public void writeChannelData(String filename, String header) throws IOException {
        PrintWriter out = new PrintWriter(new FileOutputStream(filename));

        out.println("# CRUSH Pixel Data File");
        out.println("#");
        if(header != null) {
            out.println(header);
            out.println("#");
        }	

        out.print(getChannelFlagGuide("# Flag "));
        out.println("#");
        out.println("# " + getChannelDataHeader());

        standardWeights();

        for(ChannelType channel : this) out.println(channel);
        out.close();

        notify("Written " + filename);

        sampleWeights();
    }




    // The pixel data file should contain the blind channel information as well...
    // create the channel groups based on the wiring scheme.
    public void loadChannelData(String fileName) throws IOException {
        info("Loading pixel data from " + fileName);

        final ChannelLookup<ChannelType> lookup = new ChannelLookup<ChannelType>(this);

        // Channels not contained in the data file are assumed dead...
        for(Channel channel : this) channel.flag(Channel.FLAG_DEAD);

        new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                String id = tokens.nextToken();

                ChannelType channel = lookup.get(id);
                if(channel == null) return false;

                // Channels in the file are not dead after all, or let ChannelType.parse decide that....
                channel.unflag(Channel.FLAG_DEAD);
                channel.parseValues(tokens);
                if(channel.gain == 0.0) channel.flag(Channel.FLAG_BLIND);
                return true;
            }

        }.read(fileName);

        standardWeights = true;

        if(integrationTime > 0.0) sampleWeights();
    }


  
    public void flagInvalidPositions() {
        for(Pixel pixel : getPixels()) if(pixel.getPosition().length() > 1 * Unit.deg) 
            for(Channel channel : pixel) channel.flag(Channel.FLAG_BLIND);
    }


    public abstract int maxPixels();








    public synchronized void standardWeights() {
        if(standardWeights) return;
        for(Channel channel : this) channel.weight /= Math.sqrt(integrationTime);
        standardWeights = true;
    }

    public synchronized void sampleWeights() {
        if(!standardWeights) return;
        for(Channel channel : this) channel.weight *= Math.sqrt(integrationTime);
        standardWeights = false;
    }

    public abstract ChannelType getChannelInstance(int backendIndex);

    public abstract Scan<?> getScanInstance();

    /**
     * This is a convenience method for populating the instrument with a fixed number of channels.
     * Channels contained within this instrument prior to the call will be discarded, so only
     * the channels added by this method will be part of the instrument after the call.
     * It calls {@link #getChannelInstance(int)} in sequence 1 ... <code>channels</code>.
     * 
     * @param channels      The number of channels to add to this instrument.
     */
    public void populate(int channels) {
        clear();
        ensureCapacity(channels);
        for(int i=0; i<channels; i++) add(getChannelInstance(i));
    }

    public Scan<?> readScan(String descriptor, boolean readFully) throws Exception {
        Scan<?> scan = getScanInstance();
        scan.read(descriptor, readFully);
        return scan;
    }

    public SourceModel getSourceModelInstance(List<Scan<?>> scans) {
        if(hasOption("source.type")) {
            String type = option("source.type").getValue();
            if(type.equals("skydip")) return new SkyDip(this);		
            if(type.equals("map")) return new IntensityMap(this);
            if(type.equals("pixelmap")) return new PixelMap(this);
            if(type.equals("null")) return null;
            return null;
        }

        return null;
    }  

    public final int getPixelCount() { return layout.getPixelCount(); }

    public final List<? extends Pixel> getPixels() { return layout.getPixels(); }

    public final List<? extends Pixel> getMappingPixels(int keepFlags) { return layout.getMappingPixels(keepFlags); }


    /**
     *  Returns the offset of the pointing center from the the rotation center for a given rotation...
     *  
     *  @param rotationAngle    
     *  
     *  @return
     */
    public Vector2D getPointingOffset(double rotationAngle) {
        return new Vector2D();
    }



    public final double getRotationAngle() {
        return layout.getRotation();
    }

    public void setRotationAngle(double angle) {
        layout.setRotation(angle);
    }

    public void rotate(double angle) {
        layout.rotate(angle);
    }

    public ChannelDivision<ChannelType> getDivision(String name, Field field, int discardFlags) throws IllegalAccessException {
        HashMap<Integer, ChannelGroup<ChannelType>> table = new HashMap<Integer, ChannelGroup<ChannelType>>();

        for(int i=0; i<size(); i++) {
            ChannelType channel = get(i);
            if(channel.isFlagged(discardFlags)) continue;

            int group = field.getInt(channel);
            if(!table.containsKey(group)) table.put(group, new ChannelGroup<ChannelType>(field.getName() + "-" + group));
            table.get(group).add(channel);
        }

        ChannelDivision<ChannelType> organizer = new ChannelDivision<ChannelType>(name);
        for(int group : table.keySet()) organizer.add(table.get(group));

        return organizer;
    }


    public void addGroup(ChannelGroup<ChannelType> group) {
        groups.put(group.getName(), group);		
    }

    public void addGroup(String name, ChannelGroup<ChannelType> group) {
        groups.put(name, group);		
    }


    public void addDivision(ChannelDivision<ChannelType> division) {
        divisions.put(division.name, division);
        for(ChannelGroup<ChannelType> group : division) addGroup(group);
    }

    public void addModality(Modality<?> modality) {
        modalities.put(modality.name, modality);
    }

    public List<String> listModalities() {
        ArrayList<String> names = new ArrayList<String>(modalities.keySet());
        Collections.sort(names);
        return names;
    }

    public List<String> listDivisions() {
        ArrayList<String> names = new ArrayList<String>(divisions.keySet());
        Collections.sort(names);
        return names;
    }

    public void printCorrelatedModalities(PrintStream out) {
        if(!hasEnsembles) createEnsembles();

        List<String> names = listModalities();
        out.println("\nAvailable pixel divisions for " + getName() + ": \n");
        for(String name : names) {
            Modality<?> modality = modalities.get(name);
            if(modality instanceof CorrelatedModality) {
                out.print(hasOption("correlated." + name) ? " (*) " : "     ");
                out.println(name);
            }
        }
        out.println();

    }

    public void printResponseModalities(PrintStream out) {
        if(!hasEnsembles) createEnsembles();

        List<String> names = listModalities();
        out.println("\nAvailable response modalities for " + getName() + ": \n");
        for(String name : names) {
            Modality<?> modality = modalities.get(name);
            if(!(modality instanceof CorrelatedModality)) {
                out.print(hasOption("correlated." + name) ? " (*) " : "     ");
                out.println(name);
            }
        }
        out.println();
    }


    public void reindex() {
        for(int k=size(); --k >= 0; ) get(k).index = k;
    }


    @Override
    public final boolean removeFlagged(int discardFlags) {
        return slim(discardFlags, true);
    }

    public boolean slim(int discardFlags, boolean reindex) {
        if(!super.removeFlagged(discardFlags)) return false;
        
        if(reindex) reindex();
        
        // Re-create all groups / divisions / modalities from the reduced set of channels...
        createEnsembles();
      
        if(layout != null) layout.slim(discardFlags);

        info("Slimmed to " + size() + " live channels.");
        return true;
    }


    @Override
    public ChannelGroup<ChannelType> discard(int flagPattern, int criterion) {
        super.discard(flagPattern, criterion);
        reindex();	
        return this;
    }

    public void loadTempHardwareGains() {
        for(Channel channel : this) channel.temp = (float) channel.getReadoutGain();
    }

    public double[] getSourceGains(final boolean filterCorrected) {
        final double[] G = new double[size()];
        final boolean fixedGains = hasOption("source.fixedgains");

        for(Channel channel : this) {
            G[channel.index] = channel.coupling;

            Pixel pixel = channel.getPixel();
            if(pixel != null) G[channel.index] *= pixel.coupling;

            if(!fixedGains) G[channel.index] *= channel.gain;
            if(filterCorrected) G[channel.index] *= channel.sourceFiltering;
        }

        return G;
    }

    public double getMinBeamFWHM() {
        double min = Double.POSITIVE_INFINITY;
        for(Pixel pixel : getPixels()) min = Math.min(pixel.getResolution(), min);
        return min;
    }

    public double getMaxBeamFWHM() {
        double max = Double.NEGATIVE_INFINITY;
        for(Pixel pixel : getPixels()) max = Math.min(pixel.getResolution(), max);
        return max;
    }

    public double getAverageBeamFWHM() {
        double sum = 0.0;
        int n=0;
        for(Pixel pixel : getPixels()) {
            sum += pixel.getResolution();
            n++;
        }
        return sum / n;
    }

    public double getAverageFiltering() {
        final double[] sourceGain = getSourceGains(false);		
        double sumwG = 0.0, sumwG2 = 0.0;

        for(Channel channel : this) if(channel.isUnflagged()) {
            double G = sourceGain[channel.index];
            double w = channel.weight * G * G;
            double phi = channel.sourceFiltering;
            sumwG2 += w * phi * phi;
            sumwG += w * phi;	
        }

        double averageFiltering = 0.0;
        if(sumwG > 0.0) averageFiltering = sumwG2 / sumwG;
        else averageFiltering = 0.0;
        return averageFiltering;
    }


    // Flag according to noise weights (but not source weights)
    public void flagWeights() {
        final Range weightRange = new Range();
        weightRange.full();

        if(hasOption("weighting.noiserange")) {
            Range noiseRange = option("weighting.noiserange").getRange(true);
            weightRange.setMin(1.0 / (noiseRange.max() * noiseRange.max()));
            if(noiseRange.min() != 0.0) weightRange.setMax(1.0 / (noiseRange.min() * noiseRange.min()));
        }

        // Flag channels with insufficient degrees of freedom
        final double[] weights = getDoubles();
        int n=0;

        final ChannelGroup<?> channels = getDetectorChannels();
        final int excludeFlags = Channel.HARDWARE_FLAGS | Channel.FLAG_GAIN;

        for(Channel channel : channels)  {

            if(channel.dof > 0.0) channel.unflag(Channel.FLAG_DOF);
            else {
                channel.flag(Channel.FLAG_DOF);
                continue;
            }

            if(channel.isFlagged(excludeFlags)) continue;

            if(channel.gain == 0.0) continue;
            if(channel.weight <= 0.0) continue;
            if(channel.weight == Channel.DEFAULT_WEIGHT) continue;  // Skip default weights also...

            weights[n++] = Math.log1p(channel.gain * channel.gain * channel.weight);
        }
        if(n == 0) throw new IllegalStateException("DOF?");

        // Use robust mean (with 10% tails) to estimate average weight.
        weightRange.scale(Math.expm1(n > 10 ? 
                Statistics.Inplace.robustMean(weights, 0, n, 0.1) : Statistics.Inplace.median(weights, 0, n)
                ));

        Instrument.recycle(weights);

        double sumw = 0.0;
        for(Channel channel : channels) {  
            final double wG2 = channel.gain * channel.gain * channel.weight;

            if(!weightRange.contains(wG2)) channel.flag(Channel.FLAG_SENSITIVITY);	
            // TODO safety pin, in case a channel isn't assigned a proper noise weight...
            else if(channel.weight == Channel.DEFAULT_WEIGHT) channel.flag(Channel.FLAG_SENSITIVITY);
            else {
                channel.unflag(Channel.FLAG_SENSITIVITY);
                if(channel.isUnflagged()) sumw += wG2;
            }
        }	

        if(sumw == 0.0) {
            if(mappingChannels > 0) throw new IllegalStateException("FLAG");
            throw new IllegalStateException("----");
        }

        census();
    }


    public double getSourceNEFD(double gain) {
        final double G[] = getSourceGains(true);

        double sumpw = 0.0;

        for(Channel channel : this) {
            if(channel.isFlagged()) continue;
            if(G[channel.index] == 0.0) continue;

            sumpw += G[channel.index] * G[channel.index] / channel.variance; 
        }

        return Math.sqrt(integrationTime * mappingChannels / sumpw) / Math.abs(gain);
    }


    // 10 s or the value set by 'stability'
    public double getStability() {
        if(hasOption("stability")) return option("stability").getDouble();
        return 10.0 * Unit.s;
    }


    public double getOneOverFStat() {
        double sum = 0.0, sumw = 0.0;

        for(Channel channel : getObservingChannels()) if(channel.isUnflagged()) if(!Double.isNaN(channel.oneOverFStat)) {
            sum += channel.weight * channel.oneOverFStat * channel.oneOverFStat;
            sumw += channel.weight;
        }
        return Math.sqrt(sum / sumw);
    }

    public void getFitsData(LinkedHashMap<String, Object> data) {
        float[] gains = new float[storeChannels];
        float[] weights = new float[storeChannels];
        int[] flags = new int[storeChannels];

        Arrays.fill(gains, Float.NaN);
        Arrays.fill(weights, Float.NaN);
        Arrays.fill(flags, Channel.FLAG_DEAD);

        standardWeights();

        for(Channel channel : this) {
            gains[channel.getFixedIndex()] = (float) channel.gain;
            weights[channel.getFixedIndex()] = (float) channel.weight;
            flags[channel.getFixedIndex()] = channel.getFlags();
        }

        sampleWeights();

        data.put("Channel_Gains", gains);
        data.put("Channel_Weights", weights);
        data.put("Channel_Flags", flags);
    }

    public void parseImageHeader(Header header) {
        setResolution(header.getDoubleValue("BEAM", getResolution() / Unit.arcsec) * Unit.arcsec);        
    }

    public void editImageHeader(List<Scan<?>> scans, Header header) throws HeaderCardException {
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);

        c.add(new HeaderCard("TELESCOP", getTelescopeName(), "Telescope name."));
        c.add(new HeaderCard("INSTRUME", getName(), "The instrument used."));	
        c.add(new HeaderCard("V2JY", janskyPerBeam(), "1 Jy/beam in instrument data units."));
        c.add(new HeaderCard("BEAM", getResolution() / Unit.arcsec, "The instrument FWHM (arcsec) of the beam."));
    }

    public void editScanHeader(Header header) throws HeaderCardException {
        if(hasOption("write.scandata.details")) Channel.flagSpace.editHeader(header, 'C');
    }

    public void addHistory(Header header, List<Scan<?>> scans) throws HeaderCardException {}

    // Sequential, because it is usually called from a parallel environment
    public void calcOverlaps(final double pointSize) {
        if(this instanceof NonOverlapping) return;

        if(pointSize == overlapPointSize) return;

        for(Channel channel : this) channel.clearOverlaps();

        if(this instanceof GeometricIndexed) calcGeometricOverlaps((GeometricIndexed) this, pointSize);
        else new Fork<Void>() {
            @Override
            protected void process(ChannelType channel) {
                for(int k=channel.index; --k >= 0; ) {
                    final Channel other = get(k);
                    final Overlap<Channel> overlap = new Overlap<Channel>(channel, other, channel.overlap(other, pointSize));
                    if(overlap.isNull()) continue;
                    channel.addOverlap(overlap);
                    other.addOverlap(overlap);
                }	
            }	
        }.process();

        overlapPointSize = pointSize;
    }

    // Sequential, because it is usually called from a parallel environment...
    private void calcGeometricOverlaps(final GeometricIndexed geometric, final double pointSize) {
        final double radius = 2.0 * pointSize;
        final ChannelLookup<ChannelType> lookup = new ChannelLookup<ChannelType>(this);

        double nbeams = 2.0 * pointSize / getResolution();
        final ArrayList<Integer> nearbyIndex = new ArrayList<Integer>((int) Math.ceil(nbeams * nbeams));

        for(Channel channel : this) {
            nearbyIndex.clear();
            geometric.addLocalFixedIndices(channel.getFixedIndex(), radius, nearbyIndex);
        }

        new Fork<Void>() {
            @Override
            protected void process(ChannelType channel) {
                for(int fixedIndex : nearbyIndex) {
                    if(fixedIndex >= channel.getFixedIndex()) continue;

                    final Channel other = lookup.get(fixedIndex);
                    if(other == null) continue;

                    final Overlap<Channel> overlap = new Overlap<Channel>(channel, other, channel.overlap(other, pointSize));
                    channel.addOverlap(overlap);
                    other.addOverlap(overlap);
                }
            }	
        }.process();

        overlapPointSize = pointSize;
    }

    public static Instrument<?> forName(String name) throws Exception {
        File file = new File(CRUSH.home + File.separator + "config" + File.separator + name.toLowerCase() + File.separator + "class");

        if(!file.exists()) throw new IllegalArgumentException("Unknown instrument: '" + name + "'");


        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String className = in.readLine();
        in.close();
        return (Instrument<?>) Class.forName(className).getConstructor().newInstance(); 
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("gain")) return gain;
        if(name.equals("sampling")) return samplingInterval / Unit.s;
        if(name.equals("rate")) return Unit.s / samplingInterval;
        if(name.equals("okchannels")) return mappingChannels;
        if(name.equals("channels")) return size();
        if(name.equals("maxchannels")) return storeChannels;
        if(name.equals("resolution")) return getResolution() / getSizeUnit().value();
        if(name.equals("sizeunit")) return getSizeUnit().name();
        if(name.equals("ptfilter")) return getAverageFiltering();
        if(name.equals("FWHM")) return getAverageBeamFWHM() / getSizeUnit().value();
        if(name.equals("minFWHM")) return getMinBeamFWHM() / getSizeUnit().value();
        if(name.equals("maxFWHM")) return getMaxBeamFWHM() / getSizeUnit().value();
        if(name.equals("stat1f")) return getOneOverFStat();

        return layout.getTableEntry(name);        
    }

    public String getFocusString(InstantFocus focus) {

        if(focus == null) return " No instant focus.";

        String info = "";

        Unit mm = Unit.get("mm");

        if(focus.getX() != null) info += "\n  Focus.dX --> " + focus.getX().toString(mm);			
        if(focus.getY() != null) info += "\n  Focus.dY --> " + focus.getY().toString(mm);			
        if(focus.getZ() != null) info += "\n  Focus.dZ --> " + focus.getZ().toString(mm);

        return info;
    }


    @Override
    public String toString() { return "Instrument " + getName(); }

    public void troubleshootFewPixels() {
        StringBuffer buf = new StringBuffer();

        buf.append("            * Disable gain estimation for one or more modalities. E.g.:\n");

        for(String modName : getModalityNames()) if(hasOption("correlated." + modName)) if(!hasOption("correlated." + modName + ".nogains"))
            buf.append("                '-correlated." + modName + ".noGains'\n");

        if(hasOption("gains")) buf.append("            * Disable gain estimation globally with '-forget=gains'.\n"); 
        if(hasOption("despike")) buf.append("            * Disable despiking with '-forget=despike'.\n");
        if(hasOption("weighting")) if(hasOption("weighting.noiseRange")) 
            buf.append("            * Adjust noise flagging via 'weighting.noiseRange'.\n");

        CRUSH.suggest(getParent(), new String(buf));
    }



    public int[] getInts() { return recycler.getIntArray(size()); }

    public float[] getFloats() { return recycler.getFloatArray(size()); }

    public double[] getDoubles() { return recycler.getDoubleArray(size()); }

    public DataPoint[] getDataPoints() { return recycler.getDataPointArray(size()); }



    public void shutdown() {}


    @Override
    public void info(String message) { CRUSH.info(getParent(), message); }

    @Override
    public void notify(String message) { CRUSH.notify(getParent(), message); }

    @Override
    public void debug(String message) { CRUSH.debug(getParent(), message); }

    @Override
    public void warning(String message) { CRUSH.warning(getParent(), message); }

    @Override
    public void warning(Exception e, boolean debug) { CRUSH.warning(getParent(), e, debug); }

    @Override
    public void warning(Exception e) { CRUSH.warning(getParent(), e); }

    @Override
    public void error(String message) { CRUSH.error(getParent(), message); }

    @Override
    public void error(Throwable e, boolean debug) { CRUSH.error(getParent(), e, debug); }

    @Override
    public void error(Throwable e) { CRUSH.error(getParent(), e); }




    public static void recycle(int[] array) { recycler.recycle(array); }

    public static void recycle(float[] array) { recycler.recycle(array); }

    public static void recycle(double[] array) { recycler.recycle(array); }

    public static void recycle(WeightedPoint[] array) { recycler.recycle(array); }

    public static void setRecyclerCapacity(int size) { recycler.setSize(size); }

    public static void clearRecycler() { recycler.clear(); }

    private static Recycler recycler = new Recycler();

    public final static int GAINS_SIGNED = 0;
    public final static int GAINS_BIDIRECTIONAL = 1;

    // TODO no lookup access?
    public final static Unit arcsec = Unit.get("arcsec");
    public final static Unit arcmin = Unit.get("arcmin");
    public final static Unit deg = Unit.get("deg");

}
