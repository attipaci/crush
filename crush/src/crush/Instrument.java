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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.text.*;

import kovacs.astro.AstroTime;
import kovacs.data.Statistics;
import kovacs.math.Range;
import kovacs.text.TableFormatter;
import kovacs.util.*;
import crush.sourcemodel.*;
import nom.tam.fits.*;
import nom.tam.util.*;


public abstract class Instrument<ChannelType extends Channel> extends ChannelGroup<ChannelType> 
implements TableFormatter.Entries {	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7651803433436713372L;
	
	private Configurator options, startupOptions;
	
	public double integrationTime;
	public double samplingInterval;
	
	public double resolution;
	public double gain = 1.0; // The electronic amplification
	public double sourceGain = 1.0;
	public double MJD;
	
	public int nonDetectorFlags = Channel.FLAG_DEAD;
	
	public int storeChannels; // The number of channels stored in the data files...
	public int mappingChannels;
	
	public Hashtable<String, ChannelGroup<ChannelType>> groups;
	public Hashtable<String, ChannelDivision<ChannelType>> divisions;
	public Hashtable<String, Modality<?>> modalities;
			
	public boolean isInitialized = false, isValid = false;
	
	public Mount mount;
	
	public Instrument(String name) {
		super(name);
		options = new Configurator();
		startupOptions = options;
	}
	
	public Instrument(String name, int size) {
		super(name, size);
		storeChannels = size;
	}
		
	// Load the static instrument settings, which are not meant to be date-dependent...
	public void setOptions(Configurator options) {
		this.options = options;
		
		isValid = false;
		isInitialized = false;
		startupOptions = null;
		
		if(hasOption("resolution")) resolution = option("resolution").getDouble() * getSizeUnit();
		if(hasOption("gain")) gain = option("gain").getDouble();
	}
	
	public Configurator getOptions() {
		return options;
	}
	
	public Configurator getStartupOptions() { return startupOptions; }
	
	// TODO check for incompatible scans
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		if(hasOption("jackknife.alternate")) {
			System.err.println(" JACKKNIFE! Alternating scans.");
			for(int i=scans.size(); --i >= 0; ) if(i%2 == 1) 
				for(Integration<?,?> subscan : scans.get(i)) subscan.gain *= -1.0;
		}
	}
	
	@Override
	public ChannelGroup<ChannelType> copy() {
		Instrument<ChannelType> copy = (Instrument<ChannelType>) super.copy();
		
		for(Channel channel : copy) channel.instrument = copy;
	
		// TODO this needs to be done properly???
		if(options != null) copy.setOptions(options.copy());
		copy.groups = null;
		copy.divisions = null;
		copy.modalities = null;
		
		if(isInitialized) copy.initialize();
		
		return copy;
	}
	
	public void initialize() {		
		reindex();
		
		initGroups();
		initDivisions();
		initModalities();		
		
		isInitialized = true;
	}
	
	public void validate(Scan<?,?> scan) {
		startupOptions = options.copy();
		
		if(!isInitialized) initialize();
		
		loadChannelData();
		
		if(hasOption("blind")) setBlindChannels(option("blind").getIntegers()); 
		if(hasOption("flag")) flagPixels(option("flag").getIntegers()); 	
		if(hasOption("flatweights")) flattenWeights();
		
		if(hasOption("uniform")) uniformGains();
		if(hasOption("gainnoise")) {
			Random random = new Random();
			double level = option("gainnoise").getDouble();
			for(Channel channel : this) channel.gain *= 1.0 + level * random.nextGaussian();
		}
				
		if(hasOption("source.fixedgains")) {
			fixedSourceGains();
			System.out.println(" Will use static source gains.");
		}
		
		if(hasOption("jackknife.channels")) {
			System.err.println("   JACKKNIFE! Randomly inverted channels in source.");
			for(Channel channel : this) if(Math.random() < 0.5) channel.coupling *= -1.0;
		}
		
		for(Channel channel : this) {
			channel.spikes = 0;
			channel.dof = 1.0;
			if(Double.isNaN(channel.variance)) channel.variance = 1.0 / channel.weight;
		}

		census();
		
		isValid = true;
	}
	
	public abstract String getTelescopeName();
	
	public double janskyPerBeam() {
		if(hasOption("jansky")) {
			double jansky = option("jansky").getDouble();
			if(hasOption("jansky.inverse")) jansky = 1.0 / jansky;
			return jansky / getDataUnit().value();
		}
		else return 1.0; // Safety pin...
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
	
	public void setMJDOptions(double MJD) {
		if(!options.containsKey("mjd")) return;
		
		this.MJD = MJD;
		
		Hashtable<String, Vector<String>> settings = option("mjd").conditionals;
		
		for(String rangeSpec : settings.keySet()) 
			if(Range.parse(rangeSpec, true).contains(MJD)) options.parse(settings.get(rangeSpec));
			
	}
	
	
	public void setDateOptions(double MJD) {
		if(!options.containsKey("date")) return;
	
		this.MJD = MJD;
		
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
					//System.err.println("### Setting options for " + rangeSpec);
					options.parse(settings.get(rangeSpec));
				}
			}
			catch(ParseException e) { System.err.println("   WARNING! " + e.getMessage()); }
		}
	}
	
	public void setSerialOptions(int serialNo) {
		if(!options.containsKey("serial")) return;
			
		// Make options an independent set of options, setting MJD specifics...
		Hashtable<String, Vector<String>> settings = option("serial").conditionals;
		for(String rangeSpec : settings.keySet()) if(Range.parse(rangeSpec, true).contains(serialNo)) {
			//System.err.println("### Setting options for " + rangeSpec);
			options.parse(settings.get(rangeSpec));
		}
	}
	
	public void setObjectOptions(String sourceName) {
		//System.err.println(">>> Setting local options for " + sourceName);
		sourceName = sourceName.toLowerCase();
		
		if(!options.containsKey("object")) return;
			
		// Make options an independent set of options, setting object specifics...
		Hashtable<String, Vector<String>> settings = option("object").conditionals;
		for(String spec : settings.keySet()) if(sourceName.startsWith(spec)) {
			options.parse(settings.get(spec));
		}
	}
	
	public String getDataLocationHelp() {
		return "";
	}
	
	public String getCommonHelp() {
		return "";
	}
	
	public boolean hasOption(String name) {
		return options.isConfigured(name);
	}
	
	public Configurator option(String name) {
		return options.get(name);
	}
	
	public void setOption(String line) {
		options.parse(line);
	}
	
	public void forget(String key) {
		options.forget(key);
	}
	
	public Scan<?, ?> readScan(String descriptor) throws Exception {
		Scan<?, ?> scan = getScanInstance();
		scan.read(descriptor, true);
		return scan;
	}
	
	// TODO ability to flag groups divisions...
	// perhaps flag.group, and flag.division...
	public void flagPixels(Collection<Integer> list) {
		Hashtable<Integer, ChannelType> lookup = getFixedIndexLookup();
		System.err.println(" Flagging " + list.size() + " channels");
		for(int beIndex : list) if(lookup.containsKey(beIndex)) lookup.get(beIndex).flag(Channel.FLAG_DEAD);
	}
	
	public void killChannels(int pattern, int ignorePattern) {
		// Anything flagged as blind so far should be flagged as dead instead...
		for(Channel channel : this) if(channel.isFlagged(pattern)) if(channel.isUnflagged(ignorePattern)) {
			channel.unflag(pattern);
			channel.flag(Channel.FLAG_DEAD);
		}
	}
	
	public void killChannels(int pattern) {
		killChannels(pattern, 0);
	}
	
	
	public void setBlindChannels(Collection<Integer> list) {
		killChannels(Channel.FLAG_BLIND);
		
		System.err.println(" Defining " + list.size() + " blind channels.");		
		
		Hashtable<Integer, ChannelType> lookup = getFixedIndexLookup();
		
		for(int beIndex : list) {
			ChannelType channel = lookup.get(beIndex);
			if(channel != null) {
				channel.unflag();
				channel.flag(Channel.FLAG_BLIND);
			}
		}
	}
	
	public void loadChannelData() {
		if(hasOption("pixeldata")) {
			Configurator c = option("pixeldata");
			if(!c.getValue().equalsIgnoreCase("write")) {
				try { loadChannelData(c.getPath()); }
				catch(IOException e) { System.err.println("WARNING! Cannot read pixel data. Using default gains & flags."); }
			}
		}	
		
		if(hasOption("wiring")) { 
			try { readWiring(option("wiring").getPath()); }	
			catch(IOException e) {
				System.err.println("ERROR! Cannot read wiring data. Specific channel divisions not established.");
				return;
			}
		}
	}
	
	public abstract void readWiring(String fileName) throws IOException;
	
	public double getPointSize() { return resolution; }

	public double getSourceSize() {
		double sourceSize = hasOption("sourcesize") ? 
				Math.hypot(option("sourcesize").getDouble() * getSizeUnit(), resolution) : resolution;
				return sourceSize;
	}
	
	public abstract double getSizeUnit();
	
	public abstract String getSizeName();
	
	public void census() {
		mappingChannels = 0;
		for(Channel channel : this) if(channel.flag == 0) if(channel.weight > 0.0) mappingChannels++;
	}
	
	public String getConfigPath() {
		return CRUSH.home + File.separator + getName() + File.separator;
	}
	
	public ChannelGroup<ChannelType> getConnectedChannels() {
		return groups.get("connected");
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
	
	public void initGroups() {
		groups = new Hashtable<String, ChannelGroup<ChannelType>>();
		
		addGroup("all", copyGroup());
		addGroup("connected", copyGroup().discard(Channel.FLAG_DEAD));
		addGroup("detectors", copyGroup().discard(nonDetectorFlags));
		addGroup("obs-channels", copyGroup().discard(nonDetectorFlags | Channel.FLAG_BLIND));
		addGroup("sensitive", copyGroup().discard(nonDetectorFlags | Channel.FLAG_BLIND | Channel.FLAG_SENSITIVITY));
		addGroup("blinds", copyGroup().discard(nonDetectorFlags).discard(Channel.FLAG_BLIND, ChannelGroup.KEEP_ANY_FLAG));
		
		if(options.containsKey("group")) {
			Hashtable<Integer, ChannelType> lookup = getFixedIndexLookup();
			Configurator option = option("group");
			for(String name : option.getTimeOrderedKeys()) {
				ChannelGroup<ChannelType> channels = new ChannelGroup<ChannelType>(name);
				for(int backendIndex : option.get(name).getIntegers()) {
					ChannelType channel = lookup.get(backendIndex);
					if(channel != null) if(channel.isUnflagged(Channel.FLAG_DEAD)) channels.add(channel);
				}
				addGroup(name, channels);
			}
		}
	}
	
	public void initDivisions() {
		divisions = new Hashtable<String, ChannelDivision<ChannelType>>();
		
		addDivision(new ChannelDivision<ChannelType>("all", groups.get("all")));
		addDivision(new ChannelDivision<ChannelType>("connected", groups.get("connected")));
		addDivision(new ChannelDivision<ChannelType>("detectors", groups.get("detectors")));
		addDivision(new ChannelDivision<ChannelType>("obs-channels", groups.get("obs-channels")));
		addDivision(new ChannelDivision<ChannelType>("sensitive", groups.get("sensitive")));
		addDivision(new ChannelDivision<ChannelType>("blinds", groups.get("blinds")));

		if(options.containsKey("division")) {
			Configurator option = option("division");
			for(String name : option.getTimeOrderedKeys()) {
				ChannelDivision<ChannelType> division = new ChannelDivision<ChannelType>(name);
				for(String groupName : option.get(name).getList()) {
					ChannelGroup<ChannelType> group = groups.get(groupName);
					if(group != null) division.add(group);
					else System.err.println(" WARNING! Group '" + groupName + "' is undefined.");
				}
				addDivision(division);
			}
		}
	}
	
	public void initModalities() {
		modalities = new Hashtable<String, Modality<?>>();
		
		try { addModality(new CorrelatedModality("all", "Ca", divisions.get("all"), Channel.class.getField("gain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("connected", "Cc", divisions.get("connected"), Channel.class.getField("gain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("detectors", "Cd", divisions.get("detectors"), Channel.class.getField("gain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
		
		try { addModality(new CorrelatedModality("obs-channels", "C", divisions.get("obs-channels"), Channel.class.getField("gain"))); }
		catch(NoSuchFieldException e) { e.printStackTrace(); }
	
		
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
				for(CorrelatedMode mode : blinds) mode.skipChannels = Channel.FLAG_DEAD;
				addModality(blinds);
			}
			catch(NoSuchFieldException e) {
				System.err.println(" WARNING! " + getChannelInstance(-1).getClass().getSimpleName() + " has no 'temperatureGain' for blind correction");	
			}
		}
		
		modalities.get("all").setGainFlag(Channel.FLAG_GAIN);
		modalities.get("connected").setGainFlag(Channel.FLAG_GAIN);
		modalities.get("detectors").setGainFlag(Channel.FLAG_GAIN);
		modalities.get("obs-channels").setGainFlag(Channel.FLAG_GAIN);
			
		if(options.containsKey("division")) {
			Configurator option = option("division");
			for(String name : option.getTimeOrderedKeys()) {
				String id = option.isConfigured(name + ".id") ? option(name + ".id").getValue() : name;
				if(option.isConfigured(name + ".gainfield")) {
					try {
						Field field = getChannelInstance(-1).getClass().getField(option(name + ".gainfield").getValue());
						addModality(new CorrelatedModality(name, id, divisions.get(name), field));
					} 
					catch(NoSuchFieldException e) { 
						System.err.println(" WARNING! No gain field '" + option(name + ".gainfield").getValue() + "'."); 
					}
				}
				else addModality(new CorrelatedModality(name, id, divisions.get(name)));
					
				if(option.isConfigured(name + ".gainflag")) {
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
	
	public void flattenWeights() {
		System.err.println(" Switching to flat channel weights.");
		double sum = 0.0, sumG2 = 0.0;
		for(Channel channel : this) if(channel.isUnflagged(Channel.HARDWARE_FLAGS)) {
			double G2 = channel.gain * channel.gain;
			sum += channel.weight * G2;
			sumG2 += G2;
		}
		double w = sumG2 > 0.0 ? sum / sumG2 : 1.0;
		
		for(Channel channel : this) channel.weight = w;
	}
	
	public void uniformGains() {
		for(Channel channel : this) channel.uniformGains();	
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
	
	/* TODO
	// Replace the listed flag types with DEAD...
	public void killChannels(List<String> killTypes, List<String> ignoreTypes) {
		int killPattern = 0, ignorePattern = 0;
		for(String name : killTypes) killPattern |= Channel.getFlag(name);
		for(String name : ignoreTypes) ignorePattern |= Channel.getFlag(name);
		killChannels(killPattern, ignorePattern);
	}
	*/
	
	public String getChannelDataHeader() {
		return "ch\tgain\tweight\t\tflag";
	}
	
	public void writeChannelData(String filename, String header) throws IOException {
		PrintWriter out = new PrintWriter(new FileOutputStream(filename));
		
		out.println("# CRUSH Pixel Data File");
		out.println("#");
		if(header != null) {
			out.println(header);
			out.println("#");
		}	
		out.println("# " + getChannelDataHeader());
		
		standardWeights();
		
		for(ChannelType channel : this) if(channel.isUnflagged(Channel.FLAG_DEAD)) out.println(channel);
		out.close();
		
		System.err.println(" Written " + filename);
		
		dataWeights();
	}

	// The pixel data file should contain the blind channel information as well...
	// create the channel groups based on the wiring scheme.

	public void loadChannelData(String fileName) throws IOException {
		System.err.println(" Loading pixel data from " + fileName);
			
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
	
		Hashtable<String, ChannelType> lookup = getIDLookup();
	
		// Channels not contained in the data file are assumed dead...
		for(Channel channel : this) channel.flag(Channel.FLAG_DEAD);
		
		while((line=in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {	
			try {
				StringTokenizer tokens = new StringTokenizer(line);
				ChannelType channel = lookup.get(tokens.nextToken());
				if(channel != null) {
					// Channels in the file are not dead after all, or let ChannelType.parse decide that....
					channel.unflag(Channel.FLAG_DEAD);
					channel.parseValues(tokens);
					if(channel.gain == 0.0) channel.flag(Channel.FLAG_BLIND);
				}
			}
			catch(NumberFormatException e){ e.printStackTrace(); }
		}
		in.close();
		
		standardWeights = true;
		
		if(integrationTime > 0.0) dataWeights();
	}
	
	private boolean standardWeights = false;
	
	public synchronized void standardWeights() {
		if(standardWeights) return;
		for(Channel channel : this) channel.weight /= Math.sqrt(integrationTime);
		standardWeights = true;
	}
	
	public synchronized void dataWeights() {
		if(!standardWeights) return;
		for(Channel channel : this) channel.weight *= Math.sqrt(integrationTime);
		standardWeights = false;
	}
	
	public abstract ChannelType getChannelInstance(int backendIndex);
	
	public abstract Scan<?, ?> getScanInstance();
	
	public Scan<?, ?> readScan(String descriptor, boolean readFully) throws Exception {
		Scan<?, ?> scan = getScanInstance();
		scan.read(descriptor, readFully);
		return scan;
	}
	
	public SourceModel getSourceModelInstance() {
		if(hasOption("source.type")) {
			String type = option("source.type").getValue();
			if(type.equals("skydip")) return new SkyDip(this);		
			if(type.equals("map")) return new ScalarMap(this);
			if(type.equals("null")) return null;
			return null;
		}
		return null;
	}  
	
	public abstract int getPixelCount();
	
	public abstract Collection<? extends Pixel> getPixels();
	
	public abstract Collection<? extends Pixel> getMappingPixels();
	
	public Hashtable<Integer, ChannelType> getFixedIndexLookup() {
		Hashtable<Integer, ChannelType> lookup = new Hashtable<Integer, ChannelType>();
		for(ChannelType channel : this) lookup.put(channel.getFixedIndex(), channel);
		return lookup;
	}
	
	public Hashtable<String, ChannelType> getIDLookup() {
		Hashtable<String, ChannelType> lookup = new Hashtable<String, ChannelType>();
		for(ChannelType channel : this) lookup.put(channel.getID(), channel);
		return lookup;
	}
	
	public ChannelDivision<ChannelType> getDivision(String name, Field field, int discardFlags) throws IllegalAccessException {
		Hashtable<Integer, ChannelGroup<ChannelType>> table = new Hashtable<Integer, ChannelGroup<ChannelType>>();
	
		for(ChannelType channel : this) if(channel.isUnflagged(discardFlags)) {
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
	
	public synchronized void addGroup(String name, Vector<Integer> backendIndexes) {
		Hashtable<Integer, ChannelType> lookup = getFixedIndexLookup();
		ChannelGroup<ChannelType> group = new ChannelGroup<ChannelType>(name);
		for(int be : backendIndexes) {
			ChannelType pixel = lookup.get(be);
			if(pixel != null) group.add(pixel);
		}
		if(group.size() == 0) System.err.println("   WARNING! empty group '" + name + "'.");
		else groups.put(name, group);		
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
		if(!isInitialized) initialize();
		
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
		if(!isInitialized) initialize();
		
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
	
	/*
	public void normalizeGains(int method) {	
		double aveG = 0.0, sumw = 0.0;
		for(Channel channel : this) if(channel.isUnflagged(~Channel.FLAG_BLIND)) { 
			double G = channel.gain;
			if(method == GAINS_BIDIRECTIONAL) G = Math.abs(G);
			aveG += channel.weight * G; 
			sumw += channel.weight;
		}	
		if(sumw > 0) aveG /= sumw;
		for(Channel channel : this) channel.gain  /= aveG;
	}
	*/
	
	public synchronized void reindex() {
		for(int k=size(); --k >= 0; ) get(k).index = k;
	}
	
	@Override
	public boolean slim() {
		return slim(true);
	}
	
	public synchronized boolean slim(boolean reindex) {
		if(super.slim()) {
			// remove discarded channels from groups (and divisiongroups) also...
			slimGroups();
			if(reindex) reindex();
			System.err.println("   Slimmed to " + size() + " live pixels.");
			return true;
		}
		return false;
	}
	
	public void slimGroups() {
		Hashtable<Integer, ChannelType> lookup = getFixedIndexLookup();
		for(String name : groups.keySet()) slimGroup(groups.get(name), lookup);
		for(String name : divisions.keySet()) for(ChannelGroup<?> group : divisions.get(name)) slimGroup(group, lookup);  
		for(String name : modalities.keySet()) for(Mode mode : modalities.get(name)) slimGroup(mode.getChannels(), lookup); 	
	}
	
	public void slimGroup(ChannelGroup<?> group, Hashtable<Integer, ChannelType> lookup) {
		for(int c=group.size(); --c >= 0; ) if(!lookup.containsKey(group.get(c).getFixedIndex())) group.remove(c);
		group.trimToSize();
	}
	
	@Override
	public ChannelGroup<ChannelType> discard(int flagPattern, int criterion) {
		super.discard(flagPattern, criterion);
		reindex();	
		return this;
	}
		
	public double[] getSourceGains(boolean filterCorrected) {
		final double[] sourceGain = new double[size()];
		boolean fixedGains = hasOption("source.fixedgains");
		
		for(Channel channel : this) {
			sourceGain[channel.index] = fixedGains ? channel.coupling : channel.coupling * channel.gain;
			if(filterCorrected) sourceGain[channel.index] *= channel.sourceFiltering;
		}
		
		return sourceGain;
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
		
		double averageFiltering;
		double sumwG = 0.0, sumwG2 = 0.0;
		for(Channel channel : this) if(channel.isUnflagged()) {			
			double G = sourceGain[channel.index];
			double w = channel.weight * G * G;
			double phi = channel.sourceFiltering;
			sumwG2 += w * phi * phi;
			sumwG += w * phi;			
		}
		if(sumwG > 0.0) averageFiltering = sumwG2 / sumwG;
		else averageFiltering = 0.0;
		return averageFiltering;
	}
	
	
	// Flag according to noise weights (but not source weights)
	public void flagWeights() {
		Range weightRange = new Range();
		weightRange.full();
		
		
		if(hasOption("weighting.noiserange")) {
			Range noiseRange = option("weighting.noiserange").getRange(true);
			weightRange.setMin(1.0 / (noiseRange.max() * noiseRange.max()));
			if(noiseRange.min() != 0.0) weightRange.setMax(1.0 / (noiseRange.min() * noiseRange.min()));
		}
	
		// Flag channels with insufficient degrees of freedom
		double[] weights = new double[size()];
		int n=0;
		for(Channel channel : getDetectorChannels()) {
			if(channel.dof > 0.0) {
				channel.unflag(Channel.FLAG_DOF);
				if(channel.isUnflagged(Channel.FLAG_GAIN)) weights[n++] = Math.log1p(channel.weight * channel.gain * channel.gain);
			}
			else channel.flag(Channel.FLAG_DOF);
		}
		if(n == 0) throw new IllegalStateException("DOF?");
		
		// Use robust mean (with 10% tails) to estimate average weight.
		double aveW = Math.expm1(n > 10 ? Statistics.robustMean(weights, 0, n, 0.1) : Statistics.median(weights));	
		double maxWeight = weightRange.max() * aveW;
		double minWeight = weightRange.min() * aveW;	
		double sumw = 0.0;
		
		// Flag out channels with unrealistically small or large source weights
		for(Channel channel : getDetectorChannels()) {
			channel.unflag(Channel.FLAG_SENSITIVITY);
			double w = channel.weight * channel.gain * channel.gain;
			if(w > maxWeight) channel.flag(Channel.FLAG_SENSITIVITY);
			else if(w < minWeight) channel.flag(Channel.FLAG_SENSITIVITY);		
			else if(channel.isUnflagged()) sumw += w;
		}
		
		census();
		
		if(sumw == 0.0) {
			if(mappingChannels > 0) throw new IllegalStateException("FLAG");
			else throw new IllegalStateException("----");
		}
		
		
	}
	
	public double getSourceNEFD() {		
		double sumpw = 0.0;
		for(Channel channel : getObservingChannels()) if(channel.flag == 0 && channel.variance > 0.0)
			sumpw += channel.sourceFiltering * channel.sourceFiltering / channel.variance;
		
		return Math.sqrt(size()*integrationTime/sumpw) / (sourceGain * janskyPerBeam());
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
			gains[channel.getFixedIndex()-1] = (float) channel.gain;
			weights[channel.getFixedIndex()-1] = (float) channel.weight;
			flags[channel.getFixedIndex()-1] = channel.flag;
		}
		
		dataWeights();
		
		data.put("Channel_Gains", gains);
		data.put("Channel_Weights", weights);
		data.put("Channel_Flags", flags);
	}
	
	
	
	public void editImageHeader(Cursor cursor) throws HeaderCardException {
		cursor.add(new HeaderCard("TELESCOP", getTelescopeName(), "Telescope name."));
		cursor.add(new HeaderCard("INSTRUME", getName(), "The instrument used."));	
		
		// The data descriptors
		cursor.add(new HeaderCard("BEAM", resolution / Unit.arcsec, "The instrument FWHM (arcsec) of the beam."));
	}
	
	public void editScanHeader(Header header) throws HeaderCardException {
		
	}
	
	public void parseHeader(Header header) {
		resolution = header.getDoubleValue("BEAM", resolution / Unit.arcsec) * Unit.arcsec;
	}
	
	public static Instrument<?> forName(String name) {
		File file = new File(CRUSH.home + File.separator + "instruments" + File.separator + name.toLowerCase());
		
		if(!file.exists()) {
			System.err.println("ERROR! '" + name + "' is not registered in instruments directory.");
			return null;
		}
		
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			String className = in.readLine();
			in.close();
			return (Instrument<?>) Class.forName(className).newInstance(); 
		}
		catch(IOException e) {
			System.err.println("ERROR! Problem reading '" + file.getName() + "'");
			return null;
		}
		catch(Exception e) { 
			System.err.println("ERROR! " + e.getMessage());
			e.printStackTrace();
			return null; 	
		}
	}
	
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
		
		if(name.equals("gain")) return Util.defaultFormat(gain, f);
		else if(name.equals("sampling")) return Util.defaultFormat(samplingInterval / Unit.s, f);
		else if(name.equals("rate")) return Util.defaultFormat(Unit.s / samplingInterval, f);
		else if(name.equals("okchannels")) return Integer.toString(mappingChannels);
		else if(name.equals("channels")) return Integer.toString(size());
		else if(name.equals("maxchannels")) return Integer.toString(storeChannels);
		else if(name.equals("mount")) return mount.name();
		else if(name.equals("resolution")) return Util.defaultFormat(resolution / getSizeUnit(), f);
		else if(name.equals("sizeunit")) return getSizeName();
		else if(name.equals("ptfilter")) return Util.defaultFormat(getAverageFiltering(), f);
		else if(name.equals("FWHM")) return Util.defaultFormat(getAverageBeamFWHM() / getSizeUnit(), f);
		else if(name.equals("minFWHM")) return Util.defaultFormat(getMinBeamFWHM() / getSizeUnit(), f);
		else if(name.equals("maxFWHM")) return Util.defaultFormat(getMaxBeamFWHM() / getSizeUnit(), f);
		else if(name.equals("stat1f")) return Util.defaultFormat(getOneOverFStat(), f);
		
		return TableFormatter.NO_SUCH_DATA;
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
		System.err.println("            * Disable gain estimation for one or more modalities. E.g.:");
		
		for(String modName : getModalityNames()) if(hasOption("correlated." + modName)) if(!hasOption("correlated." + modName + ".nogains"))
			System.err.println("                '-correlated." + modName + ".noGains'");
		
		if(hasOption("gains")) System.err.println("            * Disable gain estimation globally with '-forget=gains'."); 
		if(hasOption("despike")) System.err.println("            * Disable despiking with '-forget=despike'.");
		if(hasOption("weighting")) if(hasOption("weighting.noiseRange")) 
			System.err.println("            * Adjust noise flagging via 'weighting.noiseRange'.");
		
	}
	
	public final static int GAINS_SIGNED = 0;
	public final static int GAINS_BIDIRECTIONAL = 1;

	
}
