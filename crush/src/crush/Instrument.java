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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.text.*;

import util.*;
import util.astro.AstroTime;
import util.data.Statistics;
import util.data.TableEntries;
import util.text.TableFormatter;
import crush.sourcemodel.*;

import nom.tam.fits.*;
import nom.tam.util.*;


public abstract class Instrument<ChannelType extends Channel> extends ChannelGroup<ChannelType> 
implements TableEntries {	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7651803433436713372L;
	
	public Configurator options, startupOptions;
	
	public double integrationTime;
	public double samplingInterval;
	
	public double resolution;
	public double gain = 1.0;
	public double MJD;
	
	public int nonDetectorFlags = Channel.FLAG_DEAD;
	
	public int storeChannels; // The number of channels stored in the data files...
	public int mappingChannels;
	
	public Hashtable<String, ChannelGroup<ChannelType>> groups = new Hashtable<String, ChannelGroup<ChannelType>>();
	public Hashtable<String, ChannelDivision<ChannelType>> divisions = new Hashtable<String, ChannelDivision<ChannelType>>();
	public Hashtable<String, Modality<?>> modalities = new Hashtable<String, Modality<?>>();	
		
	// These are initialized ad-hoc by getTau() and getScaling()...
	public TauInterpolator tauInterpolator;
	public CalibrationTable calibrationTable;
	
	public boolean initialized = false, validated = false;
	
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
	public void initialize() {
		if(hasOption("resolution")) resolution = option("resolution").getDouble() * getDefaultSizeUnit();
		initialized = true;
	}
	
	// TODO check for incompatible scans
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		
	}
	
	@Override
	public ChannelGroup<ChannelType> copy() {
		Instrument<ChannelType> copy = (Instrument<ChannelType>) super.copy();
		
		for(Channel channel : copy) channel.instrument = copy;
		
		// TODO this needs to be done properly???
		if(options != null) copy.options = options.copy();
		copy.groups = new Hashtable<String, ChannelGroup<ChannelType>>();
		copy.divisions = new Hashtable<String, ChannelDivision<ChannelType>>();
		copy.modalities = new Hashtable<String, Modality<?>>();
		
		// TODO what should instrument.copy() do with the calibration and tau tables?
		copy.addGroups();
		copy.addDivisions();
		copy.addModalities();
		
		return copy;
	}
	
	public void validate(double MJD) {
		setMJDOptions(MJD);
		setDateOptions(MJD);
		validate();
	}
	
	// Instrument validation should happen sometime during reading...
	public void validate() {
		startupOptions = options.copy();
		reindex();
	
		if(hasOption("resolution")) resolution = option("resolution").getDouble() * getDefaultSizeUnit();
		if(hasOption("gain")) gain = option("gain").getDouble();
		
		loadChannelData();
		if(hasOption("blind")) markBlinds(option("blind").getIntegers()); 
		if(hasOption("flag")) flagPixels(option("flag").getIntegers()); 
		
		addGroups();
		addDivisions();
		addModalities();
		
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
		
		if(hasOption("channeljk")) {
			System.err.println("   JACKKNIFE! Randomly inverted channels in source.");
			for(Channel channel : this) if(Math.random() < 0.5) channel.coupling *= -1.0;
		}
		
		for(Channel channel : this) {
			channel.spikes = 0;
			channel.dof = 1.0;
			if(Double.isNaN(channel.variance)) channel.variance = 1.0 / channel.weight;
		}

		census();
		
		validated = true;
	}
	
	public abstract String getTelescopeName();
	
	public void setCalibrationTable(String fileName) {
		if(calibrationTable == null) readCalibrationTable(fileName);
		else if(!fileName.equals(calibrationTable.fileName)) readCalibrationTable(fileName);	
	}
	
	public void setTauInterpolator(String fileName) {
		if(tauInterpolator == null) readTauInterpolator(fileName);
		else if(!fileName.equals(tauInterpolator.fileName)) readTauInterpolator(fileName);		
	}
	
	protected void readCalibrationTable(String fileName) {
		//System.err.print("   ");
		try { calibrationTable = new CalibrationTable(Util.getSystemPath(option("scale").getValue())); }
		catch(IOException e) { System.err.println("WARNING! Calibration table could not be read."); }		
	}
	
	protected void readTauInterpolator(String fileName) {
		//System.err.print("   ");
		try { tauInterpolator = new TauInterpolator(Util.getSystemPath(option("tau").getValue())); }
		catch(IOException e) { System.err.println("WARNING! Tau interpolator table could not be read."); }	
	}
	
	public double janskyPerBeam() {
		if(hasOption("jansky")) {
			double jansky = option("jansky").getDouble();
			if(hasOption("jansky.inverse")) jansky = 1.0 / jansky;
			return jansky / getDataUnit().value;
		}
		else return 1.0; // Safety pin...
	}
	
	public Unit getDataUnit() {
		if(hasOption("dataunit")) return Unit.get(option("dataunit").getValue());
		return Unit.get("count");		
	}
	
	public void setMJDOptions(double MJD) {
		if(!options.containsKey("mjd")) return;
		
		// Make options an independent set of options, setting MJD specifics...
		options = options.copy();
		this.MJD = MJD;
		
		Hashtable<String, Vector<String>> settings = option("mjd").conditionals;
		
		for(String rangeSpec : settings.keySet()) 
			if(Range.parse(rangeSpec, true).contains(MJD)) options.parse(settings.get(rangeSpec));
	}
	
	public void setDateOptions(double MJD) {
		if(!options.containsKey("date")) return;
	
		// Make options an independent set of options, setting MJD specifics...
		options = options.copy();
		this.MJD = MJD;
		
		Hashtable<String, Vector<String>> settings = option("date").conditionals;
			
		for(String rangeSpec : settings.keySet()) {
			try {			
				StringTokenizer tokens = new StringTokenizer(rangeSpec, "-");
				Range mjdRange = new Range();
				String spec = tokens.nextToken();
				mjdRange.min = spec.equals("*") ? Double.NEGATIVE_INFINITY : AstroTime.forSimpleDate(spec).getMJD();
				mjdRange.max = Double.POSITIVE_INFINITY;
				if(tokens.hasMoreTokens()) {
					spec = tokens.nextToken();
					mjdRange.max = spec.equals("*") ? Double.POSITIVE_INFINITY : AstroTime.forSimpleDate(spec).getMJD();
				}
				
				if(mjdRange.contains(MJD)) options.parse(settings.get(rangeSpec));
			}
			catch(ParseException e) { System.err.println("   WARNING! " + e.getMessage()); }
		}
	}
	
	public void setSerialOptions(int serialNo) {
		if(!options.containsKey("serial")) return;
		
		options = options.copy();
		
		// Make options an independent set of options, setting MJD specifics...
		Hashtable<String, Vector<String>> settings = option("serial").conditionals;
		for(String rangeSpec : settings.keySet()) if(Range.parse(rangeSpec, true).contains(serialNo)) 
			options.parse(settings.get(rangeSpec));
	}
	
	public boolean hasOption(String name) {
		return options.isConfigured(name);
	}
	
	public Configurator option(String name) {
		return options.get(name);
	}
	
	public Scan<?, ?> readScan(String descriptor) throws Exception {
		Scan<?, ?> scan = getScanInstance();
		scan.read(descriptor, true);
		return scan;
	}
	
	// TODO ability to flag groups divisions...
	// perhaps flag.group, and flag.division...
	public void flagPixels(Vector<Integer> list) {
		Hashtable<Integer, ChannelType> lookup = getChannelLookup();
		System.err.println(" Flagging " + list.size() + " channels");
		for(int beIndex : list) if(lookup.containsKey(beIndex)) lookup.get(beIndex).flag(Channel.FLAG_DEAD);
	}
	
	public void markBlinds(Vector<Integer> list) {
		// Anything flagged as blind so far should be flagged as dead instead...
		for(Channel channel : this) if(channel.isFlagged(Channel.FLAG_BLIND)) {
			channel.unflag(Channel.FLAG_BLIND);
			channel.flag(Channel.FLAG_DEAD);
		}
		
		Hashtable<Integer, ChannelType> lookup = getChannelLookup();
		
		System.err.println(" Marking " + list.size() + " channels as blind.");		
		
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
			String arg = option("pixeldata").getValue();
			if(!arg.equalsIgnoreCase("write")) {
				try { loadPixelData(Util.getSystemPath(arg)); }
				catch(IOException e) { System.err.println("WARNING! Cannot read pixel data. Using default gains & flags."); }
			}
		}	
		
		if(hasOption("wiring")) { 
			try { readWiring(Util.getSystemPath(option("wiring").getValue())); }	
			catch(IOException e) {
				System.err.println("ERROR! Cannot read wiring data. Specific channel divisions not established.");
				return;
			}
		}
	}
	
	public abstract void readWiring(String fileName) throws IOException;
	
	public abstract double getDefaultSizeUnit();
	
	public abstract String getDefaultSizeName();
	
	public void census() {
		mappingChannels = 0;
		for(Channel channel : this) if(channel.flag == 0) if(channel.weight > 0.0) mappingChannels++;
	}
	
	public String getDefaultConfigPath() {
		return CRUSH.home + File.separator + name + File.separator;
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
	
	public void addGroups() {
		addGroup("all", getChannels());
		addGroup("connected", getChannels().discard(Channel.FLAG_DEAD));
		addGroup("detectors", getChannels().discard(nonDetectorFlags));
		addGroup("obs-channels", getChannels().discard(nonDetectorFlags | Channel.FLAG_BLIND));
		addGroup("sensitive", getChannels().discard(nonDetectorFlags | Channel.FLAG_BLIND | Channel.FLAG_SENSITIVITY));
		addGroup("blinds", getChannels().discard(nonDetectorFlags).discard(Channel.FLAG_BLIND, ChannelGroup.KEEP_ANY_FLAG));
		
		if(options.containsKey("group")) {
			Hashtable<Integer, ChannelType> lookup = getChannelLookup();
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
	
	public void addDivisions() {
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
	
	public void addModalities() {
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
	
	public Vector<String> getDivisionNames() {
		Vector<String> keys = new Vector<String>();
		for(String key : divisions.keySet()) keys.add(key);
		Collections.sort(keys);
		return keys;
	}
	
	public Vector<String> getModalityNames() {
		Vector<String> keys = new Vector<String>();
		for(String key : modalities.keySet()) keys.add(key);
		Collections.sort(keys);
		return keys;
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
	
	// create the channel groups based on the wiring scheme.
	
	public String getPixelDataHeader() {
		return "ch\tgain\tweight\t\tflag";
	}
	
	public void writePixelData(String filename, String header) throws IOException {
		PrintWriter out = new PrintWriter(new FileOutputStream(filename));
		
		out.println("# CRUSH Pixel Data File");
		out.println("#");
		if(header != null) {
			out.println(header);
			out.println("#");
		}	
		out.println("# " + getPixelDataHeader());
		
		standardWeights();
		
		for(ChannelType channel : this) if(channel.isUnflagged(Channel.FLAG_DEAD)) out.println(channel);
		out.close();
		
		System.err.println(" Written " + filename);
		
		dataWeights();
	}

	// The pixel data file should contain the blind channel information as well...
	public void loadPixelData(String fileName) throws IOException {
		System.err.println(" Loading pixel data from " + fileName);
			
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
	
		Hashtable<Integer, ChannelType> lookup = getChannelLookup();
	
		// Channels not contained in the data file are assumed dead...
		for(Channel channel : this) channel.flag(Channel.FLAG_DEAD);
		
		while((line=in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {	
			try {
				StringTokenizer tokens = new StringTokenizer(line);
				ChannelType channel = lookup.get(Integer.parseInt(tokens.nextToken()));
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
	
	public SourceModel<?, ?> getSourceModelInstance() {
		if(hasOption("source.type")) {
			String type = option("source.type").getValue();
			if(type.equals("skydip")) return new SkyDip<Instrument<?>, Scan<?, ?>>(this);		
			if(type.equals("map")) return new ScalarMap<Instrument<?>, Scan<?, ?>>(this);
			if(type.equals("null")) return null;
			return null;
		}
		return null;
	}  
	
	public abstract int getPixelCount();
	
	public abstract Collection<? extends Pixel> getPixels();
	
	public abstract Collection<? extends Pixel> getMappingPixels();
	
	public Hashtable<Integer, ChannelType> getChannelLookup() {
		Hashtable<Integer, ChannelType> lookup = new Hashtable<Integer, ChannelType>();
		for(ChannelType channel : this) lookup.put(channel.dataIndex, channel);
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
		groups.put(group.name, group);		
	}
	
	public void addGroup(String name, ChannelGroup<ChannelType> group) {
		groups.put(name, group);		
	}
	
	public synchronized void addGroup(String name, Vector<Integer> backendIndexes) {
		Hashtable<Integer, ChannelType> lookup = getChannelLookup();
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
		for(int k=0; k<size(); k++) get(k).index = k;
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
		Hashtable<Integer, ChannelType> lookup = getChannelLookup();
		for(String name : groups.keySet()) slimGroup(groups.get(name), lookup);
		for(String name : divisions.keySet()) for(ChannelGroup<?> group : divisions.get(name)) slimGroup(group, lookup);  
		for(String name : modalities.keySet()) for(Mode mode : modalities.get(name)) slimGroup(mode.channels, lookup); 	
	}
	
	public void slimGroup(ChannelGroup<?> group, Hashtable<Integer, ChannelType> lookup) {
		for(int c=0; c<group.size(); c++) if(!lookup.containsKey(group.get(c).dataIndex)) group.remove(c--);
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
		if(mappingChannels == 0) throw new IllegalStateException("----");
		
		Range wRange = new Range();
		wRange.fullRange();
		
		if(hasOption("weighting.noiserange")) {
			Range noiseRange = option("weighting.noiserange").getRange(true);
			wRange.min = 1.0 / (noiseRange.max * noiseRange.max);
			if(noiseRange.min != 0.0) wRange.max = 1.0 / (noiseRange.min * noiseRange.min);
		}
	
		// Flag channels with insufficient degrees of freedom
		double[] weights = new double[size()];
		int n=0;
		for(Channel channel : getDetectorChannels()) {
			if(channel.dof > 0.0) {
				channel.unflag(Channel.FLAG_DOF);
				if(channel.isUnflagged(Channel.FLAG_GAIN)) weights[n++] = channel.weight * channel.gain * channel.gain;
			}
			else channel.flag(Channel.FLAG_DOF);
		}
		if(n == 0) throw new IllegalStateException("DOF?");
		
		// Use robust mean (with 10% tails) to estimate average weight.
		double aveSW = n > 0 ? Statistics.robustMean(weights, 0, n, 0.1) : 0.0;	
		double maxWeight = wRange.max * aveSW;
		double minWeight = wRange.min * aveSW;	
		double sumw = 0.0;
		
		// Flag out channels with unrealistically small or large source weights
		for(Channel channel : getDetectorChannels()) {
			double w = channel.weight * channel.gain * channel.gain;
			
			channel.unflag(Channel.FLAG_SENSITIVITY);	
			if(w > maxWeight) channel.flag(Channel.FLAG_SENSITIVITY);
			else if(w <= minWeight) channel.flag(Channel.FLAG_SENSITIVITY);		
			else if(channel.isUnflagged()) sumw += w;
		}
		if(sumw <= 0.0) throw new IllegalStateException("NEFD");
		
		census();
	}
	
	public double getSourceNEFD() {		
		double sumpw = 0.0;
		for(Channel channel : getObservingChannels()) if(channel.flag == 0 && channel.variance > 0.0)
			sumpw += channel.sourceFiltering * channel.sourceFiltering / channel.variance;
	
		return Math.sqrt(size()*integrationTime/sumpw) / janskyPerBeam();
	}
	

	// 10 s or the value set by 'stability'
	public double getStability() {
		if(hasOption("stability")) return option("stability").getDouble();
		return 10.0 * Unit.s;
	}
	
	public double getSourceSize() {
		double sourceSize = hasOption("sourcesize") ? 
				option("sourcesize").getDouble() * getDefaultSizeUnit() : resolution;
		if(sourceSize < resolution) sourceSize = resolution;
		return sourceSize;
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
			gains[channel.dataIndex-1] = (float) channel.gain;
			weights[channel.dataIndex-1] = (float) channel.weight;
			flags[channel.dataIndex-1] = channel.flag;
		}
		
		dataWeights();
		
		data.put("Channel_Gains", gains);
		data.put("Channel_Weights", weights);
		data.put("Channel_Flags", flags);
	}
	
	
	
	public void editImageHeader(Cursor cursor) throws HeaderCardException {
		cursor.add(new HeaderCard("TELESCOP", getTelescopeName(), "Telescope name."));
		cursor.add(new HeaderCard("INSTRUME", name, "The instrument used."));	
		
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
		else if(name.equals("resolution")) return Util.defaultFormat(resolution / getDefaultSizeUnit(), f);
		else if(name.equals("sizeunit")) return getDefaultSizeName();
		else if(name.equals("ptfilter")) return Util.defaultFormat(getAverageFiltering(), f);
		else if(name.equals("FWHM")) return Util.defaultFormat(getAverageBeamFWHM() / getDefaultSizeUnit(), f);
		else if(name.equals("minFWHM")) return Util.defaultFormat(getMinBeamFWHM() / getDefaultSizeUnit(), f);
		else if(name.equals("maxFWHM")) return Util.defaultFormat(getMaxBeamFWHM() / getDefaultSizeUnit(), f);
		
		return TableFormatter.NO_SUCH_DATA;
	}
	
	public final static int GAINS_SIGNED = 0;
	public final static int GAINS_BIDIRECTIONAL = 1;

	
}
