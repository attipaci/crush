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
import java.text.NumberFormat;
import java.util.*;

import util.*;
import util.astro.EquatorialCoordinates;
import util.data.DataPoint;
import util.data.FFT;
import util.data.Statistics;
import util.data.TableEntries;
import util.data.WeightedPoint;
import util.data.WindowFunction;
import util.text.TableFormatter;

import nom.tam.fits.*;
import nom.tam.util.*;


/**
 * 
 * @author pumukli
 *
 * @param <InstrumentType>
 * @param <FrameType>
 * 
 * Always iterate frames first then channels. It's a lot faster this way, probably due to caching...
 */
public abstract class Integration<InstrumentType extends Instrument<?>, FrameType extends Frame> 
extends ArrayList<FrameType> 
implements Comparable<Integration<InstrumentType, FrameType>>, TableEntries {
	/**
	 * 
	 */
	private static final long serialVersionUID = 365675228828101776L;

	public Scan<InstrumentType, ?> scan;
	public InstrumentType instrument;
	
	public int integrationNo;	
	
	public String comments = new String();
	
	public float gain = 1.0F;
	public double zenithTau = 0.0;
	
	public Hashtable<String, Dependents> dependents = new Hashtable<String, Dependents>(); 
	public Hashtable<Mode, Signal> signals = new Hashtable<Mode, Signal>();	
	
	public boolean approximateSourceMap = false;
	public int sourceGeneration = 0;
	public double[] sourceSyncGain;
	
	public Chopper chopper;
	public DataPoint aveScanSpeed;
	
	public double filterTimeScale = Double.POSITIVE_INFINITY;
	public double nefd = Double.NaN; // It is readily cast into the Jy sqrt(s) units!!!
	
	protected boolean isDetectorStage = false;
	protected boolean isValid = false;
		
	// The integration should carry a copy of the instrument s.t. the integration can freely modify it...
	// The constructor of Integration thus copies the Scan instrument for private use...
	@SuppressWarnings("unchecked")
	public Integration(Scan<InstrumentType, ?> parent) {
		scan = parent;
		instrument = (InstrumentType) scan.instrument.copy();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Object clone() { 
		Integration<InstrumentType, FrameType> clone = (Integration<InstrumentType, FrameType>) super.clone();
		// TODO redo it safely, s.t. existing reduction steps copy over as well.
		clone.dependents = new Hashtable<String, Dependents>(); 
		clone.signals = new Hashtable<Mode, Signal>();

		return clone;
	}

	
	public int compareTo(Integration<InstrumentType, FrameType> other) {
		if(integrationNo == other.integrationNo) return 0;
		else return integrationNo < other.integrationNo ? -1 : 1;
	}
	
	public void reindex() {
		final int nt = size();
		for(int t=0; t<nt; t++) {
			final Frame exposure = get(t);
			if(exposure != null) exposure.index = t;
		}
	}
	
	public boolean hasOption(String key) {
		return instrument.hasOption(key);
	}
	
	public Configurator option(String key) {
		return instrument.option(key);
	}

	public void validate() {
		if(isValid) return;		
		
		System.err.println(" Processing integration " + getID() + ":");
		
		for(Frame frame : this) if(frame != null) frame.validate();
		

		int gapTolerance = hasOption("gap-tolerance") ? framesFor(Double.parseDouble("gap-tolerance") * Unit.s) : 0;
		if(hasGaps(gapTolerance)) fillGaps();
		else reindex();

		//if(hasOption("shift")) shiftData();
		if(hasOption("detect.chopped")) detectChopper();
		if(hasOption("frames")) selectFrames();
		// Explicit downsampling should precede v-clipping
		if(hasOption("downsample")) if(!option("downsample").equals("auto")) downsample();
		if(hasOption("vclip")) velocityClip();
		if(hasOption("aclip")) accelerationClip();

		calcScanSpeedStats();
			
		// Flag out-of-range data
		if(hasOption("range")) checkRange();

		// Automatic downsampling after vclipping...
		if(hasOption("downsample")) if(option("downsample").equals("auto")) downsample();
			
		trim();
		
		detectorStage();
		
		if(hasOption("tau")) {
			try { setTau(); }
			catch(Exception e) { System.err.println("   WARNING! Problem setting tau: " + e.getMessage()); }
		}
		if(hasOption("scale")) setScaling();
		if(hasOption("invert")) gain *= -1.0;
		
		if(!hasOption("noslim")) slim();
		if(hasOption("jackknife")) if(Math.random() < 0.5) {
			System.err.println("   JACKKNIFE! This integration will produce an inverted source.");
			gain *= -1.0;
		}
		if(hasOption("framejk")) {
			System.err.println("   JACKKNIFE! Randomly inverted frames in source.");
			for(Frame exposure : this) exposure.jackknife();
		}
		
		
		isValid = true;
		
		if(hasOption("speedtest")) speedTest();
	}
	
	public void invert() {
		for(Frame frame : this) if(frame != null) frame.invert();
	}
	
	public int getFrameCount(int excludeFlags) {
		int n=0;
		for(Frame exposure : this) if(exposure != null) if(exposure.isUnflagged(excludeFlags)) n++;
		return n;
	}
	
	
	public int getFrameCount(int excludeFlags, Channel channel, int excludeSamples) {
		int n=0;
		for(Frame exposure : this) if(exposure != null) 
			if(exposure.isUnflagged(excludeFlags)) if((exposure.sampleFlag[channel.index] & excludeSamples) == 0) n++;
		return n;
	}
	
	
	public void shiftData() {
		double dt = option("shift").getDouble();
		System.err.print(" Shifting by " + dt + " sec.");
		//shift(dt * Unit.s);
		System.err.println();		
	}
	
	
	public void selectFrames() {
		Range range = option("frames").getRange(true);
		int from = (int)range.min;
		int to = Math.min(size(), (int)range.max);
		
		Vector<FrameType> buffer = new Vector<FrameType>(to-from+1);
		
		for(int t=from; t<to; t++) buffer.add(get(t));
		clear();
		addAll(buffer);
		reindex();
	}
	
	public void checkRange() {
		if(!hasOption("range")) return;
		final Range range = option("range").getRange();
		
		System.err.print("   Flagging out-of-range data. ");
		
		int[] n = new int[instrument.size()];
		int N = 0;
		for(Frame exposure : this) if(exposure != null) {
			for(Channel channel : instrument) if(!range.contains(exposure.data[channel.index])) {
				exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
				n[channel.index]++;
			}
			N++;
		}
		
		if(!hasOption("range.flagfraction")) {
			System.err.println();
			return;
		}
			
		int flagged = 0;
		double critical = option("range.flagfraction").getDouble();
		for(Channel channel : instrument) {
			if((double) n[channel.index] / N > critical) {
				channel.flag(Channel.FLAG_DAC_RANGE | Channel.FLAG_DEAD);
				flagged++;
			}
		}
	
		System.err.println(flagged + " channel(s) discarded.");
	}
	
	public void downsample() {
		// Keep to the rule of thumb of at least 2.5 samples per beam
		if(option("downsample").equals("auto")) {
			// Choose downsampling to accomodate at least 90% of scanning speeds... 
			double maxv = aveScanSpeed.value + 1.25 * aveScanSpeed.rms();
			// Choose downsampling to accomodate at ~98% of scanning speeds... 
			//double maxv = aveScanSpeed.value + 2.0 * aveScanSpeed.rms();
			double maxInt = 0.4 * instrument.resolution / maxv;
			int factor = (int)Math.floor(maxInt / instrument.samplingInterval);
			if(factor > 1) downsample(factor);
			else return;
		}
		else {
			int factor = option("downsample").getInt();
			downsample(factor);
		}
		trim();		
	}
	
	public void setScaling() {
		try { setScaling(option("scale").getDouble()); }
		catch(NumberFormatException noworry) {
			instrument.setCalibrationTable(Util.getSystemPath(option("scale").getValue()));
			try { setScaling(1.0 / instrument.calibrationTable.getValue(getMJD())); }
			catch(ArrayIndexOutOfBoundsException e) { System.err.println("     WARNING! " + e.getMessage()); }
		}
	}

	public void setScaling(double value) {
		gain /= value;
		System.err.println("   Applying scaling factor " + Util.f3.format(value));		
	}

	// Try in this order:
	//   1. in-band value, e.g. "0.304"
	//   2. scaling relation, e.g. "225GHz", provided "tau.225GHz" is defined.
	//	 
	public void setTau() throws Exception {	
		String source = option("tau").getValue();
		
		try { setTau(Double.parseDouble(source)); }
		catch(NumberFormatException notanumber) {
			String scaling = source.toLowerCase();
			if(hasOption("tau." + scaling)) setTau(scaling, option("tau." + scaling).getDouble());
			else {
				instrument.setTauInterpolator(Util.getSystemPath(option("tau").getValue()));
				try { setTau(instrument.tauInterpolator.getValue(getMJD())); }
				catch(Exception e) { System.err.println("     WARNING! " + e.getMessage()); }
			}
		}
		catch(Exception e) { 
			System.err.println("    WARNING! " + e.getMessage()); 
			throw(e);
		}
	}
	
	public void setTau(String id, double value) {
		Vector2D t = (Vector2D) getTauCoefficients(id);
		Vector2D inband = (Vector2D) getTauCoefficients(instrument.name);
		setZenithTau(inband.x / t.x * (value - t.y) + inband.y);
	}
	
	public double getTau(String id, double value) {
		Vector2D t = (Vector2D) getTauCoefficients(id);
		Vector2D inband = (Vector2D) getTauCoefficients(instrument.name);
		return t.x / inband.x * (value - inband.y) + t.y;
	}
	
	public double getTau(String id) {
		return getTau(id, zenithTau);
	}
	
	public Vector2D getTauCoefficients(String id) {
		String key = "tau." + id.toLowerCase();
		
		if(!hasOption(key + ".a")) throw new IllegalStateException("   WARNING! " + key + " has undefined relationship.");
		
		Vector2D coeff = new Vector2D();
		coeff.x = option(key + ".a").getDouble();
		if(hasOption(key + ".b")) coeff.y = option(key + ".b").getDouble();
	
		return coeff;
	}
	
	public void setTau(double value) throws Exception {	
		if(this instanceof GroundBased) {
			try { setZenithTau(value); }
			catch(NumberFormatException e) {}
		}
		else for(Frame frame : this) frame.transmission = (float) Math.exp(-value);
	}
	
	
	
	
	public void setZenithTau(double value) {
		if(!(this instanceof GroundBased)) throw new UnsupportedOperationException("Only implementation of GroundBased can set a zenith tau.");
		System.err.println("   Setting zenith tau to " + Util.f3.format(value));
		zenithTau = value;
		for(Frame frame : this) if(frame != null) ((HorizontalFrame) frame).setZenithTau(zenithTau);
	}

	public void calcScanSpeedStats() {
		aveScanSpeed = getAverageScanningVelocity(0.5 * Unit.s);
		System.err.println("   Typical scanning speeds are " 
				+ Util.f1.format(aveScanSpeed.value/(instrument.getDefaultSizeUnit()/Unit.s)) 
				+ " +- " + Util.f1.format(aveScanSpeed.rms()/(instrument.getDefaultSizeUnit()/Unit.s)) 
				+ " " + instrument.getDefaultSizeName() + "/s");
	}
	
	public void velocityClip() {
		Range vRange = null;
		Configurator option = option("vclip");
		
		if(option.equals("auto"))
			vRange = new Range(0.2*instrument.resolution / instrument.getStability(), 0.4 * instrument.resolution / instrument.samplingInterval);	
		else {
			vRange = option.getRange(true);
			vRange.scale(Unit.arcsec / Unit.s);
		}
		if(hasOption("chopped")) vRange.min = 0.0;
		
		velocityCut(vRange);
	}
	
	public void accelerationClip() {
		double maxA = option("aclip").getDouble() * Unit.arcsec / Unit.s2;
		accelerationCut(maxA);
	}
	

	public void pointingAt(Vector2D offset) {
		for(Frame frame : this) if(frame != null) frame.pointingAt(offset);
	}
	
	
	public abstract FrameType getFrameInstance();
	
	public double getCrossingTime() {
		if(scan.sourceModel == null) return instrument.getSourceSize();
		return scan.sourceModel.getSourceSize(instrument);
	}
	
	public double getCrossingTime(double sourceSize) {		
		if(chopper != null) return Math.min(chopper.stareDuration(), sourceSize / aveScanSpeed.value);
		return sourceSize / aveScanSpeed.value;		
	}

	public double getPointSize() {
		return scan.sourceModel == null ? instrument.resolution : scan.sourceModel.getPointSize(instrument);
	}
	
	public double getPointCrossingTime() { 
		return getCrossingTime(getPointSize()); 
	}
	
	//public abstract double getSourceFootprint();
		
	public double getMJD() {
		return 0.5 * (getFirstFrame().MJD + getLastFrame().MJD);	
	}

	// Always returns a value between 1 and driftN...
	public int framesFor(double time) {
		return Math.max(1, Math.min(size(), (int)Math.round(Math.min(time, filterTimeScale) / instrument.samplingInterval)));	
	}	
	
	public int power2FramesFor(double time) {
		return FFT.getPaddedSize(Math.max(1, Math.min(size(), (int)Math.round(Math.min(time, filterTimeScale) / instrument.samplingInterval / Math.sqrt(2.0)))));	
	}
	
	public int filterFramesFor(String spec, double defaultValue) {
		int frames = Math.max(1, framesFor(defaultValue));
		
		if(spec == null) return frames;
			
		double driftT = Double.NaN;
		double stability = instrument.getStability();
					
		// 'auto' produces max 20% filtering, or less depending on 'stability'
		if(spec.equals("auto")) {
			if(hasOption("photometry")) driftT = stability;
			else driftT = Math.max(stability, 5.0 * getCrossingTime()); 
		}
		else driftT = Double.parseDouble(spec);
		
		frames = Math.max(1, framesFor(driftT));
		frames = Math.min(frames, size());
		
		return FFT.getPaddedSize(frames);
	}

	public FrameType getFirstFrame() {
		int t=0;
		while(get(t) == null) t++;
		return get(t);
	}
	
	public FrameType getLastFrame() {
		int t=size()-1;
		while(get(t) == null) t--;
		return get(t);
	}

	public boolean hasGaps(int tolerance) {
		double lastMJD = Double.NaN;
		for(FrameType exposure : this) if(!Double.isNaN(lastMJD)) {
			final int gap = (int)Math.round((exposure.MJD - lastMJD) * Unit.day / instrument.samplingInterval) - 1;
			if(gap > tolerance) return true;
		}
		return false;
	}
	
	public void fillGaps() {
		double lastMJD = Double.NaN;

		Vector<FrameType> buffer = new Vector<FrameType>(2*size());
		
		for(FrameType exposure : this) if(!Double.isNaN(lastMJD)) {
			final int gap = (int)Math.round((exposure.MJD - lastMJD) * Unit.day / instrument.samplingInterval) - 1;
			if(gap > 0) {
				System.err.println("   Inserting " + gap + " empty frames...");
				for(int i=gap; --i >= 0; ) buffer.add(null);
			}
						
			buffer.add(exposure);	
			lastMJD = exposure.MJD;
		}	
		
		if(size() != buffer.size()) {
			clear();
			addAll(buffer);
			reindex();
		}
	}
	
	public synchronized void trim() {
		trim(getFirstFrame().index, getLastFrame().index+1);
	}
	
	public synchronized void trim(int from, int to) {
		from = Math.max(0, from);
		to = Math.min(size(), to);
		
		// If no frames need to be discarded, then do nothing, except
		// ensuring efficient memory use...
		if(from == 0 && to == size()) {
			trimToSize();
			return;
		}
		
		if(from == 0) for(int t=size(); --t >= to; ) remove(t);
		else {
			ArrayList<FrameType> frames = new ArrayList<FrameType>(to - from);
			for(int t=from; t<to; t++) frames.add(get(t));

			clear();
			addAll(frames);
			reindex();
		}
		
		trimToSize();
		
		System.err.println("   Trimmed to " + size() + " frames.");
	}
	
	public synchronized void slim() {
		instrument.slim(false);
		for(Frame frame : this) if(frame != null) frame.slimTo(instrument);	
		instrument.reindex();
	}
	
	public synchronized void scale(double factor) {
		if(factor == 1.0) return;
		for(Frame frame : this) if(frame != null) frame.scale(factor);
	}
	

	public Range getRange(ChannelGroup<?> channels) {
		Range range = new Range();
		
		for(Frame frame : this) if(frame != null) {
			for(Channel channel : channels) if(frame.sampleFlag[channel.index] == 0) range.include(frame.data[channel.index]);
		}
		return range;		
	}

	public double getPA() {
		HorizontalFrame first = (HorizontalFrame) getFirstFrame();
		HorizontalFrame last = (HorizontalFrame) getLastFrame();
		return 0.5 * (Math.atan2(first.sinPA, first.cosPA) + Math.atan2(last.sinPA, last.cosPA));
	}
	
	public void removeOffsets(final boolean robust, final boolean quick) {
		removeDrifts(instrument, size(), robust, quick);
	}
	
	
	public void removeDrifts(final ChannelGroup<?> channels, final int targetFrameResolution, final boolean robust, final boolean quick) {
		//System.err.println("O >>> " + channels.size() + " > " + targetFrameResolution);
		
		int driftN = Math.min(size(), FFT.getPaddedSize(targetFrameResolution));
		final int step = quick ? (int) Math.pow(driftN, 2.0/3.0) : 1;
		filterTimeScale = Math.min(filterTimeScale, driftN * instrument.samplingInterval);
		
		Dependents parms = getDependents("drifts");
		
		WeightedPoint[] offsets = null; // The channel offsets for ML estimates
		WeightedPoint[] buffer = null;	// The timestream for robust estimates
			
		WeightedPoint[] aveOffset = new WeightedPoint[instrument.size()];
		for(int i=0; i<aveOffset.length; i++) aveOffset[i] = new WeightedPoint();
		
		if(driftN < size()) comments += (robust ? "[D]" : "D") + "(" + driftN + ")";
		else comments += robust ? "[O]" : "O";
		
		if(robust) {
			buffer = new WeightedPoint[(int) Math.ceil((double)size()/step)];
			for(int i=0; i<buffer.length; i++) buffer[i] = new WeightedPoint();
		}
		else {
			offsets = new WeightedPoint[instrument.size()];
			for(int i=0; i<offsets.length; i++) offsets[i] = new WeightedPoint();
		}
		
		int nt = size();
		for(int from=0; from < nt; from += driftN) {
			int to = Math.min(size(), from + driftN);
			parms.clear(channels, from, to);
			if(robust) removeRobustDrifts(channels, parms, from, to, step, buffer, aveOffset);
			else removeMLDrifts(channels, parms, from, to, step, offsets, aveOffset);
			parms.apply(channels, from, to);
		}
		
		// Store the mean offset as a channel property...
		for(Channel channel : channels) {
			final double G = isDetectorStage ? channel.getHardwareGain() : 1.0;
			channel.offset += G * aveOffset[channel.index].value;
		}
			
		if(driftN < size()) for(Channel channel : channels) {
			double crossingTime = getPointCrossingTime();	
			
			if(!Double.isNaN(crossingTime) && !Double.isInfinite(crossingTime)) {
				// Undo prior drift corrections....
				if(!Double.isInfinite(channel.filterTimeScale)) {
					if(channel.filterTimeScale > 0.0) 
						channel.sourceFiltering /= 1.0 - crossingTime / channel.filterTimeScale;
					else channel.sourceFiltering = 0.0;
				}
				// Apply the new drift correction
				channel.sourceFiltering *= 1.0 - crossingTime / Math.min(filterTimeScale, channel.filterTimeScale);
			}
			else channel.sourceFiltering = 0.0;
			channel.filterTimeScale = Math.min(filterTimeScale, channel.filterTimeScale);	
		}		
			
			
		// Make sure signals are filtered the same as time-streams...
		// TODO this is assuming all channels are filtered the same...
		for(Signal signal : signals.values()) signal.removeDrifts();
		
		if(CRUSH.debug) checkForNaNs(channels, 0, size());
	}
		
	private synchronized void removeMLDrifts(final ChannelGroup<?> group, final Dependents parms, final int fromt, int tot, final int step, final WeightedPoint[] buffer, final WeightedPoint[] aveOffset) {
		tot = Math.min(tot, size());
		
		// Clear the buffer
		for(final Channel channel : group) buffer[channel.index].noData();
		
		// Calculate the weight sums for every pixel...
		for(int t=fromt; t<tot; t+=step) {
			final Frame exposure = get(t);
			if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {
				for(final Channel channel : group) if(exposure.sampleFlag[channel.index] == 0) {
					final WeightedPoint increment = buffer[channel.index];
					increment.value += (exposure.relativeWeight * exposure.data[channel.index]);
					increment.weight += exposure.relativeWeight;
				}
			}
		}
		
		// Calculate the maximum-likelihood offsets and the channel dependence...
		for(final Channel channel : group) {
			final WeightedPoint offset = buffer[channel.index];
			if(offset.weight > 0.0) {
				offset.value /= offset.weight;
				parms.add(channel, 1.0);
				aveOffset[channel.index].average(offset);
			}
		}
		
		// Remove offsets from data and account frame dependence...
		for(int t=fromt; t<tot; t+=step) {
			final Frame exposure = get(t);
			if(exposure == null) continue;

			for(final Channel channel : group) {
				final WeightedPoint offset = buffer[channel.index];
				if(offset.weight > 0.0) { 
					exposure.data[channel.index] -= offset.value;
					parms.add(exposure, exposure.relativeWeight / offset.weight); 
				}
			}
		}
	}
	
	private synchronized void removeRobustDrifts(final ChannelGroup<?> group, final Dependents parms, final int fromt, int tot, final int step, final WeightedPoint[] buffer, final WeightedPoint[] aveOffset) {
		tot = Math.min(tot, size());
	
		final WeightedPoint offset = new WeightedPoint();
			
		for(final Channel channel : group) {		
			int n = 0;
			double sumw = 0.0;

			final int c = channel.index;
			
			for(int t=fromt; t<tot; t+=step) {
				final Frame exposure = get(t);
				if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] == 0) {
					final WeightedPoint point = buffer[n++];
					point.value = exposure.data[c];
					sumw += (point.weight = exposure.relativeWeight);
				}
			}

			if(sumw > 0.0) {
				Statistics.smartMedian(buffer, 0, n, 0.25, offset);				
				aveOffset[channel.index].average(offset);
				
				for(int t=fromt; t<tot; t+=step) {
					final Frame exposure = get(t);
					if(exposure == null) continue;
					exposure.data[c] -= offset.value;
					parms.add(exposure, exposure.relativeWeight / sumw);
				}
				parms.add(channel, 1.0);
			}
		}
	}

	public boolean decorrelate(final String modalityName, final boolean isRobust) {
		Modality<?> modality = instrument.modalities.get(modalityName);
		modality.solveGains = hasOption("gains");
		modality.phaseGains = hasOption("phasegains");
		modality.setOptions(option("correlated." + modality.name));
			
		if(modality.trigger != null) if(!hasOption(modality.trigger)) return false;
		
		String left = isRobust ? "[" : "";
		String right = isRobust ? "]" : "";
		
		comments += left + modality.id + right;
		int frameResolution = power2FramesFor(modality.resolution);
		if(frameResolution > 1) comments += "(" + frameResolution + ")";	
		
		
		if(modality instanceof CorrelatedModality) {
			CorrelatedModality correlated = (CorrelatedModality) modality;
			if(correlated.solveSignal) correlated.updateAllSignals(this, isRobust);
		}	
		
		// Continue to solve gains only if gains are derived per integration
		// If the gains span over scans, then return to allow them to be
		// derived globally...
		if(hasOption("gains.span") || hasOption("correlated." + modality.name + ".span")) return false;
		
		boolean isGainRobust = false;		
		if(modality.solveGains) {
			Configurator gains = option("gains");
			gains.mapValueTo("estimator");
			if(gains.isConfigured("estimator")) if(gains.get("estimator").equals("median")) isGainRobust = true; 
			
			if(modality.updateAllGains(this, isGainRobust)) {
				instrument.census();
				comments += instrument.mappingChannels;
			}
		}	
		
		return true;
	}
	
	public Dependents getDependents(String name) {
		return dependents.containsKey(name) ? dependents.get(name) : new Dependents(this, name);
	}


	public void getPixelWeights() {
		comments += "W";
		
		final ChannelGroup<?> liveChannels = instrument.getConnectedChannels();
	
		// Use the weight field for the weight-sum
		for(Channel channel : liveChannels) channel.weight = 0.0;	
		
		final double[] var = new double[instrument.size()];
		
		for(final Frame exposure : this) if(exposure != null) if(exposure.isUnflagged(Frame.WEIGHTING_FLAGS)) {	
			for(final Channel channel : liveChannels) if(exposure.sampleFlag[channel.index] == 0) {	
				final float value = exposure.data[channel.index];
				var[channel.index] += (exposure.relativeWeight * value * value);
				channel.weight += exposure.relativeWeight;
			}
		}

		for(final Channel channel : liveChannels) if(channel.weight > 0.0) {
			channel.dof = Math.max(0.0, 1.0 - channel.dependents / channel.weight);
			channel.variance = channel.weight > 0.0 ? var[channel.index] / channel.weight : 0.0;
			channel.weight = channel.variance > 0.0 ? channel.dof / channel.variance : 0.0;
		}

		flagWeights();
	}
	

	public void getDifferencialPixelWeights() {
		final int delta = framesFor(10.0 * getPointCrossingTime());
		
		comments += "w";

		final ChannelGroup<?> liveChannels = instrument.getConnectedChannels();
		
		// Use the weight field for the weight-sum...
		for(Channel channel : liveChannels) channel.weight = 0.0;
		
		final double[] var = new double[instrument.size()];
		final int nT = size();
		
		for(int t=delta; t<nT; t++) {
			final Frame exposure = get(t);
			final Frame prior = get(t-delta);
			
			if(exposure != null && prior != null) if(((exposure.flag | prior.flag) & Frame.WEIGHTING_FLAGS) == 0) {
				for(final Channel channel : liveChannels) if((exposure.sampleFlag[channel.index] | prior.sampleFlag[channel.index]) == 0) {
					final float diff = exposure.data[channel.index] - prior.data[channel.index];
					var[channel.index] += (exposure.relativeWeight * diff * diff);
					channel.weight += exposure.relativeWeight;		    
				}
			}
		}
 
		for(Channel channel : liveChannels) if(channel.weight > 0.0) {
			channel.dof = Math.max(0.0, 1.0 - channel.dependents / channel.weight);
			channel.variance = channel.weight > 0.0 ? 0.5 * var[channel.index] /  channel.weight : 0.0;
			channel.weight = channel.variance > 0.0 ? channel.dof / channel.variance : 0.0;
		}
		
		flagWeights();
	}

	public void getRobustPixelWeights() {
		comments += "[W]";
	
		final float[] dev2 = new float[size()];

		for(final Channel channel : instrument.getConnectedChannels()) {
			int points = 0;
			
			for(final Frame exposure : this) if(exposure != null) if(exposure.isUnflagged(Frame.WEIGHTING_FLAGS)) {
				if(exposure.sampleFlag[channel.index] == 0) {
					final float value = exposure.data[channel.index];
					dev2[points++] = exposure.relativeWeight * value * value;
				}		    
			}	
			
			if(points > 0) {
				channel.dof = Math.max(0.0, 1.0 - channel.dependents / points);
				channel.variance = points > 0 ? (Statistics.median(dev2, 0, points) / 0.454937) : 0.0;
				channel.weight = channel.variance > 0.0 ? channel.dof / channel.variance : 0.0;	
			}
		}
		
		flagWeights();
	}
	

	private void flagWeights() {
		try { 
			instrument.flagWeights(); 
			calcSourceNEFD();
		}
		catch(IllegalStateException e) { 
			comments += "(" + e.getMessage() + ")";
			nefd = Double.NaN;
		}
		
		comments += instrument.mappingChannels;
	}
	
	public void calcSourceNEFD() {
		nefd = instrument.getSourceNEFD();
		comments += "(" + Util.e2.format(nefd) + ")";	
	}

	public void getTimeWeights(final int blockSize) {
		comments += "tW(" + blockSize + ")";

		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		
		double sumfw = 0.0;
		int nFrames = 0;
		
		final int nT = (int)Math.ceil((float)size() / blockSize);
		
		for(int T=0; T<nT; T++) {
			int points = 0;
			double deps = 0.0;
			double sumChi2 = 0.0;
			
			final int fromt = T*blockSize;
			final int tot = Math.min(size(), fromt+blockSize);
			
			for(int t=fromt; t < tot; t++) {
				final Frame exposure = get(t);
				if(exposure == null) continue;

				for(final Channel channel : connectedChannels) if(exposure.sampleFlag[channel.index] == 0) {			
					final float value = exposure.data[channel.index];
					sumChi2 += (channel.weight * value * value);
					points++;
				}
				deps += exposure.dependents;
			}
				
			if(points > deps && sumChi2 > 0.0) {
				final double dof = 1.0 - deps/points;
				final float fw = (float) ((points-deps) / sumChi2);
			
				for(int t=fromt; t < tot; t++) {
					final Frame exposure = get(t);
					if(exposure == null) continue;
					exposure.unflag(Frame.FLAG_DOF);
					exposure.dof = dof;
					exposure.relativeWeight = fw;
					sumfw += fw;
					nFrames++;
				}
			}
			else for(int t=fromt; t < tot; t++) {
				final Frame exposure = get(t);
				if(exposure == null) continue;
				if(points > deps) exposure.relativeWeight = 1.0F;
				else {
					exposure.relativeWeight = 0.0F;
					exposure.flag(Frame.FLAG_DOF);
				}
			}
		}
		
		Range wRange = new Range();
		
		if(hasOption("weighting.frames.noiserange")) wRange = option("weighting.frames.noiserange").getRange(true);
		
		if(nFrames > 0) {
			final double avew = sumfw / nFrames;
			for(Frame exposure : this) if(exposure != null) {
				exposure.relativeWeight /= avew;		
				if(!wRange.contains(exposure.relativeWeight)) exposure.flag(Frame.FLAG_WEIGHT);
				else exposure.unflag(Frame.FLAG_WEIGHT);
			}
		}

	}
	
	
	
	public void getTimeStream(final Channel channel, final double[] data) {
		final int c = channel.index;
		final int nt = size();
		for(int t=0; t<nt; t++) {
			final Frame exposure = get(t);
			if(exposure == null) data[t] = 0.0;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = 0.0;
			else if(exposure.sampleFlag[c] != 0) data[t] = 0.0;
			else data[t] = exposure.data[c];
		}
		// Pad if necessary...
		if(data.length > nt) Arrays.fill(data, nt, data.length, 0.0);
	}
	
	public int getWeightedTimeStream(final Channel channel, final double[] data) {	
		final int c = channel.index;
		final int nt = size();
		int n=0;
		for(int t=nt; --t >= 0; ) {
			final Frame exposure = get(t);
			if(exposure == null) data[t] = 0.0;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = 0.0;
			else if(exposure.sampleFlag[c] != 0) data[t] = 0.0;
			else {
				data[t] = exposure.relativeWeight * exposure.data[c];
				n++;
			}
		}
		// Pad if necessary...
		if(data.length > nt) Arrays.fill(data, nt, data.length, 0.0);
		return n;
	}
	
	public int getWeightedTimeStream(final Channel channel, final float[] data) {	
		final int c = channel.index;
		final int nt = size();
		int n=0;
		for(int t=nt; --t >= 0; ) {
			final Frame exposure = get(t);
			if(exposure == null) data[t] = 0.0F;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = 0.0F;
			else if(exposure.sampleFlag[c] != 0) data[t] = 0.0F;
			else {
				data[t] = exposure.relativeWeight * exposure.data[c];
				n++;
			}
		}
		// Pad if necessary...
		if(data.length > nt) Arrays.fill(data, nt, data.length, 0.0F);
		return n;
	}
	
	public void getTimeStream(final Channel channel, final WeightedPoint[] data) {
		final int c = channel.index;
		final int nt = size();
		for(int t=0; t<nt; t++) {
			final Frame exposure = get(t);
			if(exposure == null) data[t].noData();
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t].noData();
			else if(exposure.sampleFlag[c] != 0) data[t].noData();
			else {
				final WeightedPoint point = data[t];
				point.value = (exposure.relativeWeight * exposure.data[c]);
				point.weight = exposure.relativeWeight;
			}
		}
		// Pad if necessary...
		if(data.length > nt) for(int t=nt; t<data.length; t++) data[t].noData();
	}

	
	
	// Changes:
	//  * Always goes ahead with whitening even when window size is large...
	public synchronized void whiten() {
		comments += "wh(";
		
		int windowSize = 2*framesFor(filterTimeScale);
		double level = hasOption("whiten.level") ? option("whiten.level").getDouble() : 2.0;
		Range measure = hasOption("whiten.proberange") ? option("whiten.proberange").getRange(true) : null; 
		boolean symmetric = hasOption("whiten.below");
		int minProbeChannels = hasOption("whiten.minchannels") ? option("whiten.minchannels").getInt() : 16;
		
		windowSize = FFT.getPaddedSize(windowSize);
		// convert critical whitening power level to amplitude...
		level = Math.sqrt(level);
		
		if(size() < windowSize) windowSize = FFT.getPaddedSize(size());
		
		final int n = FFT.getPaddedSize(size());
		final float[] data = new float[n];
		final int blocks = n/windowSize;
		
		final int nF = windowSize / 2;
		final double[] A = new double[nF];
		final double[] temp = new double[nF];
		final double[] phi = new double[nF];
		
		final Complex[] sourceSpectrum = new Complex[nF+1];
		for(int F=0; F<sourceSpectrum.length; F++) sourceSpectrum[F] = new Complex(); 
		
		final Complex[] filteredSpectrum = new Complex[nF+1];
		for(int F=0; F<filteredSpectrum.length; F++) filteredSpectrum[F] = new Complex(); 
		
		double sigma = getPointCrossingTime() / Util.sigmasInFWHM / instrument.samplingInterval;
		double dF = 1.0 / (windowSize * instrument.samplingInterval);
		
		int measureFrom = measure == null ? 0 : Math.max(0, (int) Math.floor(measure.min / dF));
		int measureTo = measure == null ? nF : Math.min(nF, (int) Math.ceil(measure.max / dF) + 1);
		
		// Make sure the probing range is contains enough channels
		// and that the range is valid...
		if(measureFrom > measureTo - minProbeChannels + 1) measureFrom = measureTo - minProbeChannels + 1;
		if(measureFrom < 0) measureFrom = 0;
		if(measureFrom > measureTo - minProbeChannels + 1) measureTo = Math.min(minProbeChannels + 1, nF);
		
		final double[] sourceProfile = new double[2*nF];
		sourceProfile[0] = 1.0;
		for(int t=1; t<=nF; t++) {
			double dev = t / sigma;
			double a = Math.exp(-0.5*dev*dev);
			sourceProfile[t] = sourceProfile[sourceProfile.length-t] = a;
		}
		
		FFT.uncheckedForward(sourceProfile, sourceSpectrum);
		
		double sumpwG = 0.0, aveSourceWhitening = 0.0;
		
		for(final Channel channel : instrument.getConnectedChannels()) {
			final int c = channel.index;
			
			// If the filterResponse array does not exist, create it...
			if(channel.filterResponse == null) {
				channel.filterResponse = new float[nF];
				Arrays.fill(channel.filterResponse, 1.0F);
			}
			
			// Put the time-stream data into an array, and FFT it...
			getWeightedTimeStream(channel, data);		
			
			// Average power in windows, then convert to rms amplitude
			FFT.forwardRealInplace(data);
			for(int F=0; F<nF; F++) {
				double sumP = 0.0;
				final int fromf = Math.max(2, 2 * F * blocks);
				int tof = Math.min(fromf + 2 * blocks, data.length);
				// Sum the power inside the spectral window...
				for(int f=fromf; f<tof; f++) sumP += data[f] * data[f];
				// Add the Nyquist component to the last bin...
				if(F == nF-1) {
					sumP += data[1] * data[1];
					tof++;
				}
				// Set the amplitude equal to the rms power...
				A[F] = tof > fromf ? Math.sqrt(2.0 * sumP / (tof - fromf)) : 0.0;
			}	

			
			System.arraycopy(A, 0, temp, 0, A.length);
			final double medA = Statistics.median(temp, measureFrom, measureTo);
			
			// Save the original amplitudes for later use...
			System.arraycopy(A, 0, temp, 0, A.length);
			
			final double sigmaA = medA / Math.sqrt(blocks);
			final double critical = medA * level + 2.0 * sigmaA;
			final double criticalBelow = medA / level - 2.0 * sigmaA;
			
			// Only whiten those frequencies which have a significant excess power
			// when compared to the specified level over the median spectral power.
				
			Arrays.fill(phi, 1.0); // To be safe initialize the scaling array here...
		
			// This is the whitening filter...
			for(int F=0; F<nF; F++) if(A[F] > 0.0) {
				// Check if there is excess power that needs filtering
				if(A[F] > critical) phi[F] = medA / A[F];
				else if(symmetric) if(A[F] < criticalBelow) phi[F] = medA / A[F];
				
				// If it was filtered prior, see if the filtering can be relaxed (not to overdo it...)
				if(channel.filterResponse[F] < 1.0) if(A[F] < medA) phi[F] = medA / A[F];
				else if(channel.filterResponse[F] > 1.0) if(A[F] > medA) phi[F] = medA / A[F];
				
				if(A[F] > 0.0) A[F] *= phi[F];
				else A[F] = medA;
			}
		
			
			// Do a neighbour based round as well, with different resolutions
			// (like a feature seek).
			if(hasOption("whiten.neighbours")) {
				int N = A.length;
				int maxBlock = N >> 2;
				double uncertainty = Math.sqrt(2.0 / blocks);
				double limit = 1.0 + 3.0 * uncertainty;
				
				for(int blockSize = 1; blockSize <= maxBlock; blockSize <<= 1) {	
					final int N1 = N-1;
					
					for(int F = 1; F < N1; F++) if(A[F] > 0.0) {
						double maxA = Math.max(A[F-1], A[F+1]);
						if(A[F] > maxA * limit) {
							final double rescale = maxA / A[F];
							for(int blockf = 0, f = F*blockSize; blockf < blockSize; blockf++, f++) phi[f] *= rescale;
							A[F] = maxA;
						}
					}				

					for(int F = 0; F < N1; F += 2) A[F>>1] = 0.5 * Math.hypot(A[F], A[F+1]);
					N >>= 1;

				uncertainty /= Math.sqrt(2.0);
				}	
			}
			
			// Renormalize the whitening scaling s.t. it is median-power neutral
			for(int F=0; F<nF; F++) temp[F] *= phi[F];
			double norm = medA / Statistics.median(temp, measureFrom, measureTo);
			if(Double.isNaN(norm)) norm = 1.0;
			
			
			double sumPreserved = 0.0;	
			for(int F=0; F<nF; F++) {
				phi[F] *= norm;
				channel.filterResponse[F] *= phi[F];				
				final double phi2 = channel.filterResponse[F] * channel.filterResponse[F];
				sumPreserved += phi2;
			}
	
			
			// Noisewhitening measures the relative noise amplitude after filtering
			double noisePowerWhitening = sumPreserved / nF;
			double noiseAmpWhitening = Math.sqrt(noisePowerWhitening);
			// Adjust the weights given that the measured noise amplitude is reduced
			// by the filter from its underlying value...
			channel.weight *= noiseAmpWhitening / channel.noiseWhitening;
			channel.noiseWhitening = noiseAmpWhitening;
	
			// Figure out how much filtering effect there is on the point source peaks...
			for(int F=0; F<nF; F++) {
				filteredSpectrum[F].copy(sourceSpectrum[F]);
				filteredSpectrum[F].scale(channel.filterResponse[F]);
			}
			
			// Calculate the filtered source profile...
			FFT.uncheckedBackward(filteredSpectrum, sourceProfile);	
			// Discount the effect of prior whitening...
			if(channel.directFiltering > 0.0) channel.sourceFiltering /= channel.directFiltering;
			// And apply the new values...
			channel.directFiltering = sourceProfile[0];		
			channel.sourceFiltering *= channel.directFiltering;
			
			// To calculate <G> = sum w G^2 / sum w G
			if(channel.isUnflagged()) {
				aveSourceWhitening += channel.directFiltering * channel.directFiltering / channel.variance;
				sumpwG += Math.abs(channel.directFiltering) / channel.variance;
			}
			
			toRejected(data, phi);
			
			FFT.backRealInplace(data);
			
			level(data, channel);
			
			for(Frame exposure : this) if(exposure != null) exposure.data[c] -= data[exposure.index];
			
		}
		
		comments += Util.f2.format(aveSourceWhitening/sumpwG) + ")";
				
	}


	protected boolean despikedNeighbours = false;
		
	public void despike(Configurator despike) {
		String method = despike.isConfigured("method") ? despike.get("method").getValue() : "absolute";
		
		double level = 10.0;
		despike.mapValueTo("level");
		if(despike.isConfigured("level")) level = despike.get("level").getDouble();
		
		double flagFraction = despike.isConfigured("flagfraction") ? despike.get("flagfraction").getDouble() : 1.0;
		int flagCount = despike.isConfigured("flagcount") ? despike.get("flagcount").getInt() : Integer.MAX_VALUE;
		int frameSpikes = despike.isConfigured("framespikes") ? despike.get("framespikes").getInt() : instrument.size();
		int featureWidth = 1;
		
		if(method.equalsIgnoreCase("neighbours")) despikeNeighbouring(level);
		else if(method.equalsIgnoreCase("absolute")) despikeAbsolute(level);
		else if(method.equalsIgnoreCase("gradual")) despikeGradual(level, 0.1);
		else if(method.equalsIgnoreCase("features")) {
			int maxT = framesFor(filterTimeScale) / 2;
			
			if(despike.isConfigured("width")) if(!despike.get("width").equals("auto"))
				maxT = (int)Math.ceil(despike.get("width").getDouble() * Unit.s / instrument.samplingInterval);
			
			despikeFeatures(level, maxT);
			featureWidth = maxT;
		}
	
		int regularSpikes = Frame.SAMPLE_SPIKE_FLAGS & ~Frame.SAMPLE_SPIKY_FEATURE;
			
		// Flag spiky frames first assumes that spikes tend to be caused in many pixels at once
		// rather than some pixels being inherently spiky...
		// Only do these for regular spikes (not features)...
		if(!method.equalsIgnoreCase("features")) flagSpikyFrames(regularSpikes, frameSpikes);
		
		if(method.equalsIgnoreCase("features")) {
			double featureFraction = 1.0 - Math.exp(-2*featureWidth*flagFraction);
			flagSpikyChannels(Frame.SAMPLE_SPIKY_FEATURE, featureFraction, 2*featureWidth*flagCount, Channel.FLAG_FEATURES);
		}
		else if(despikedNeighbours) flagSpikyChannels(regularSpikes, 2*flagFraction, 2*flagCount, Channel.FLAG_SPIKY);
		else flagSpikyChannels(regularSpikes, flagFraction, flagCount, Channel.FLAG_SPIKY);
	
	}
	
	
	public void despikeNeighbouring(double significance) {
		comments += "dN";

		//final int delta = framesFor(filterTimeScale);
		final int delta = 1;
		final int excludeSamples = Frame.SAMPLE_SOURCE_BLANK | Frame.SAMPLE_SKIP;
		despikedNeighbours = true;
		
		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		
		final float[] level = new float[instrument.size()];
		for(Channel channel : connectedChannels) level[channel.index] = (float) (significance * Math.sqrt(channel.variance));
		
		int nt = size();
		for(int t=delta; t < nt; t++) {
			final Frame exposure = get(t);
			final Frame prior = get(t-delta);
			
			if(exposure != null) if(prior != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(prior.isUnflagged(Frame.MODELING_FLAGS))  {
				final float chi = (float) (1.0 / Math.sqrt(exposure.relativeWeight) + 1.0 / Math.sqrt(prior.relativeWeight));
				
				for(final Channel channel : connectedChannels) if(((exposure.sampleFlag[channel.index] | prior.sampleFlag[channel.index]) & excludeSamples) == 0) {
					exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SPIKY_NEIGHBOUR;

					if(Math.abs(exposure.data[channel.index] - prior.data[channel.index]) > level[channel.index] * chi) {
						exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKY_NEIGHBOUR;
						prior.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKY_NEIGHBOUR;
					}
				}
			}	
		}
		
	}

	public void despikeAbsolute(double significance) {
		comments += "dA";
		
		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		final int excludeSamples = Frame.SAMPLE_SOURCE_BLANK | Frame.SAMPLE_SKIP;
		
		final float[] level = new float[instrument.size()];
		for(Channel channel : instrument) level[channel.index] = (float) (significance * Math.sqrt(channel.variance));

		for(final Frame exposure : this) if(exposure != null) {
			final float frameChi = 1.0F / (float)Math.sqrt(exposure.relativeWeight);
			for(final Channel channel : connectedChannels) if((exposure.sampleFlag[channel.index] & excludeSamples) == 0) {
				if(Math.abs(exposure.data[channel.index]) > level[channel.index] * frameChi) 
					exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;
				else 
					exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SPIKE;
			}
		}
	}
	

	public void despikeGradual(double significance, double depth) {
		comments += "dG";
		
		final float[] level = new float[instrument.size()];
		for(Channel channel : instrument) level[channel.index] = (float) (significance * Math.sqrt(channel.variance));
		
		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		
		final int excludeSamples = Frame.SAMPLE_SOURCE_BLANK | Frame.SAMPLE_SKIP;
		
		for(final Frame exposure : this) if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {	
			double maxdev = 0.0;
			final float frameChi = 1.0F / (float)Math.sqrt(exposure.relativeWeight);
			
			// Find the largest not yet flagged as spike deviation.
			for(final Channel channel : connectedChannels) if((exposure.sampleFlag[channel.index] & excludeSamples) == 0)
				maxdev = Math.max(maxdev, Math.abs(exposure.data[channel.index] / (float)channel.gain));
				
			if(maxdev > 0.0) {
				double minSignal = depth * maxdev;
				for(final Channel channel : connectedChannels) if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SOURCE_BLANK) == 0) {
					final double critical = Math.max(channel.gain * minSignal, level[channel.index] * frameChi);
					if(Math.abs(exposure.data[channel.index]) > critical) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;
					else exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SPIKE;
				}
			}
		}
		
	}
	
	public void despikeFeatures(double significance) {
		despikeFeatures(significance, framesFor(filterTimeScale));
	}
	
	public void despikeFeatures(double significance, int maxBlockSize) {
		if(maxBlockSize > size()) maxBlockSize = size();
		comments += "dF";
		
		final ChannelGroup<?> liveChannels = instrument.getConnectedChannels();
		
		final WeightedPoint[] timeStream = new WeightedPoint[size()];
		final int nt = size();
		for(int t=0; t<nt; t++) timeStream[t] = new WeightedPoint();		
		
		final WeightedPoint diff = new WeightedPoint();

		// Clear the spiky feature flag...
		for(Frame exposure : this) if(exposure != null) for(Channel channel : liveChannels) exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SPIKY_FEATURE;		
		
		for(final Channel channel : instrument) {
			getTimeStream(channel, timeStream);
			
			// check and divide...
			int n = size();
			for(int blockSize = 1; blockSize <= maxBlockSize; blockSize *= 2) {
				for(int T=1; T < n; T++) if(T < n) {
					diff.copy(timeStream[T]);
					diff.subtract(timeStream[T-1]);
					if(Math.abs(diff.value) * Math.sqrt(diff.weight) > significance) {
						for(int t=blockSize*(T-1), blockt=0; t<nt && blockt < 2*blockSize; t++, blockt++) {
							final Frame exposure = get(t);
							if(exposure != null) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKY_FEATURE;		
						}
					}
				}
				
				n /= 2;
				
				for(int to=0, from=0; to < n; to++, from += 2) {
					timeStream[to] = timeStream[from];
					timeStream[to].average(timeStream[from+1]);
				}
			}
		}	

	}
	
	public void flagSpikyChannels(int spikeTypes, double flagFraction, int minSpikes, int channelFlag) {
		int maxChannelSpikes = Math.max(minSpikes, (int)Math.round(flagFraction * size()));
		
		// Flag spiky channels even if spikes are in spiky frames
		//int frameFlags = LabocaFrame.MODELING_FLAGS & ~LabocaFrame.FLAG_SPIKY;
		
		// Only flag spiky channels if spikes are not in spiky frames
		int frameFlags = Frame.MODELING_FLAGS;
	
		for(Channel channel : instrument) channel.spikes = 0;
			
		for(Frame exposure : this) if(exposure != null) if(exposure.isUnflagged(frameFlags)) 
			for(Channel channel : instrument) if((exposure.sampleFlag[channel.index] & spikeTypes) != 0) channel.spikes++;
			
		for(Channel channel : instrument) {
			if(channel.spikes > maxChannelSpikes) channel.flag(channelFlag);
			else channel.unflag(channelFlag);
		}
		
		instrument.census();
		comments += instrument.mappingChannels;
	}
			
	public void flagSpikyFrames(int spikeTypes, double minSpikes) {
		int spikyFrames = 0;
		
		// Flag spiky frames even if spikes are in spiky channels.
		//int channelFlags = ~(LabocaPixel.FLAG_SPIKY | LabocaPixel.FLAG_FEATURES);
		
		// Flag spiky frames only if spikes are not in spiky channels.
		int channelFlags = ~0;
		
		for(Frame exposure : this) if(exposure != null) {
			int frameSpikes = 0;

			for(Channel channel : instrument) if(channel.isUnflagged(channelFlags))
				if((exposure.sampleFlag[channel.index] & spikeTypes) != 0) frameSpikes++;

			if(frameSpikes > minSpikes) {
				exposure.flag |= Frame.FLAG_SPIKY;
				spikyFrames++;
			}
			else exposure.flag &= ~Frame.FLAG_SPIKY;
		}
		
		comments += "(" + Util.f1.format(100.0*spikyFrames/size()) + "%)";
	}
	
	
	// 2*bins complex gains per pixel...
	public void deresonate(int[] channelSelection, double[] signal, Complex[][] gain, int bins) {
		//Complex[][] spectrum = new Complex[channelSelection.length][];
		
		// create average spectra for all channels
		// weighted average in phase together (with prior gains if available)
		// the 'signal' is averaged power signal
		// Complex gains by comparing 'signal' to the individual spectra
		// remove filtered periodic patches from timeStream of each channel...
	}	
	
	public synchronized boolean isDetectorStage() {
		return isDetectorStage;
	}
	
	public synchronized void detectorStage() { 
		if(isDetectorStage) return;
		
		final float[] G = new float[instrument.size()];
		for(Channel channel : instrument) G[channel.index] = (float) channel.getHardwareGain();
		
		for(Frame frame : this) if(frame != null) for(Channel channel : instrument)
			frame.data[channel.index] /= G[channel.index];
		
		isDetectorStage = true;		
	}
	
	public synchronized void readoutStage() { 
		if(!isDetectorStage) return;
		
		final float[] G = new float[instrument.size()];
		for(Channel channel : instrument) G[channel.index] = (float) channel.getHardwareGain();
		
		for(Frame frame : this) if(frame != null) for(Channel channel : instrument)
			frame.data[channel.index] *= G[channel.index];
		
		isDetectorStage = false;
	}
	
	public synchronized void clearData() {
		for(Frame exposure : this) if(exposure != null) for(Channel channel : instrument) exposure.data[channel.index] = 0.0F;
	}
	
	public synchronized void randomData() {
		Random random = new Random();
		
		float[] rms = new float[instrument.size()];
		for(Channel channel : instrument) rms[channel.index] = (float)(Math.sqrt(1.0/channel.weight));
		
		for(Frame exposure : this) if(exposure != null) for(Channel channel : instrument)
			exposure.data[channel.index] = rms[channel.index] * (float)random.nextGaussian();						
	
	}
	
	public void addCorrelated(CorrelatedSignal signal) throws IllegalAccessException {	
		final Mode mode = signal.getMode();
		final float[] gain = mode.getGains();
		final int nc = mode.channels.size();
		
		for(final Frame exposure : this) {
			final float C = signal.valueAt(exposure);
			for(int k=0; k<nc; k++) exposure.data[mode.channels.get(k).index] += gain[k] * C;
		}
	}
	
	public void addSource(EquatorialCoordinates coords, double peak, double FWHM) {
		addSource(coords, peak, FWHM, instrument.getPixels(), Mode.TOTAL_POWER);
	}
	
	public void addSource(EquatorialCoordinates coords, double peak, double FWHM, Collection<? extends Pixel> pixels, int signalMode) {
		final double[] sourceGain = instrument.getSourceGains(false);
		final EquatorialCoordinates equatorial = new EquatorialCoordinates();
		final double sigma = FWHM / Util.sigmasInFWHM;
		final double A = -0.5 / (sigma * sigma);
		
		for(Frame exposure : this) if(exposure != null) {
			final double fG = gain * exposure.getSourceGain(signalMode);
			
			for(Pixel pixel : pixels) {
				exposure.getEquatorial(pixel.getPosition(), equatorial);
				final double d = equatorial.distanceTo(coords);
				final double value = peak * Math.exp(A*d*d);
				for(final Channel channel : pixel) exposure.data[channel.index] += sourceGain[channel.index] * fG * value;
			}
		}
	
	}
	
	
	
	public Vector2D[] getPositions(int type) {
		Vector2D[] position = new Vector2D[size()];
		SphericalCoordinates coords = new SphericalCoordinates();

		final int nt = size();
		for(int t=0; t<nt; t++) {
			final Frame exposure = get(t);
			if(exposure != null) {
				position[t] = new Vector2D();
				Vector2D pos = position[t];
			
				// Telescope motion should be w/o chopper...
				// TELESCOPE motion with or w/o SCANNING and CHOPPER
				if((type & Motion.TELESCOPE) != 0) {
					coords.copy(exposure.getNativeCoords());
					// Subtract the chopper motion if it is not requested...
					if((type & Motion.CHOPPER) == 0) coords.subtractNativeOffset(exposure.chopperPosition);
					pos.x = coords.x;
					pos.y = coords.y;
				}

				// Scanning includes the chopper motion
				// SCANNING with or without CHOPPER
				else if((type & Motion.SCANNING) != 0) {
					exposure.getEquatorialOffset(pos);
					exposure.equatorialToNative(pos);
					// Subtract the chopper motion if it is not requested...
					if((type & Motion.CHOPPER) == 0) pos.subtract(exposure.chopperPosition);
				}	

				// CHOPPER only...
				else if(type == Motion.CHOPPER) pos.copy(exposure.chopperPosition);

			}
		}
		
		return position;
	}
	
	public Vector2D[] getSmoothPositions(int type) {
		double T = hasOption("positions.smooth") ? option("positions.smooth").getDouble() * Unit.s : instrument.samplingInterval;
		return getSmoothPositions(type, framesFor(T));
	}
	
	public Vector2D[] getSmoothPositions(int type, int n) {
		Vector2D[] pos = getPositions(type);
		if(n < 2) return pos;
		
		Vector2D[] smooth = new Vector2D[size()];
		int empties = 0;

		Vector2D sum = new Vector2D();
		
		for(int t=0; t<n-1; t++) {
			Vector2D position = pos[t];
			if(position == null) empties++;
			else sum.add(position);
		}

		final int nm = n/2;
		final int np = n - nm;
		final int tot = size()-np-1;
		
		for(int t=nm; t<tot; t++) {
			if(pos[t + np] == null) empties++;
			else sum.add(pos[t + np]);
		
			if(n > empties) {
				smooth[t] = (Vector2D) sum.clone();
				smooth[t].scale(1.0 / (n-empties));
			}
			
			if(pos[t - nm] == null) empties--;
			else sum.subtract(pos[t - nm]);
		}
	
		return smooth;
	}
	
	
	public Signal getPositionSignal(Mode mode, int type, Motion direction) {
		Vector2D[] pos = getSmoothPositions(type);
		double[] data = new double[size()];	
		for(int t=0; t<data.length; t++) data[t] = pos[t] == null ? Float.NaN : direction.getValue(pos[t]);
		return new Signal(mode, this, data);
	}
	
	public Vector2D[] getScanningVelocities() { 
		double T = hasOption("positions.smooth") ? option("positions.smooth").getDouble() * Unit.s : instrument.samplingInterval; 
		return getScanningVelocities(T); 	
	}
	
	public Vector2D[] getScanningVelocities(double smoothT) {
		Vector2D[] pos = getSmoothPositions(Motion.SCANNING | Motion.CHOPPER, framesFor(smoothT));
		Vector2D[] v = new Vector2D[size()];

		final int ntm1 = size()-1;
		for(int t=1; t<ntm1; t++) {
			if(pos[t+1] == null || pos[t-1] == null) v[t] = null;
			else {
				v[t] = new Vector2D();
				v[t].x = (pos[t+1].x - pos[t-1].x);
				v[t].y = (pos[t+1].y - pos[t-1].y);
				v[t].scale(0.5 / instrument.samplingInterval);
			}
		}

		return v;
	}
	
	public DataPoint getAverageScanningVelocity(double smoothT) {
		Vector2D[] v = getScanningVelocities();		
		float[] speed = new float[v.length];
		
		int n=0;
		for(int t=0; t<v.length; t++) if(v[t] != null) speed[n++] = (float) v[t].length();
		double avev = n > 0 ? Statistics.median(speed, 0, n) : Double.NaN;
		
		n=0;
		for(int t=0; t<v.length; t++) if(v[t] != null) {
			float dev = (float) (speed[n] - avev);
			speed[n++] = dev*dev;
		}
		double w = n > 0 ? 0.454937/Statistics.median(speed, 0, n) : 0.0;
		
		return new DataPoint(new WeightedPoint(avev, w));
	}
	
	public int velocityCut(Range v) { 
		double T = hasOption("positions.smooth") ? option("positions.smooth").getDouble() * Unit.s : instrument.samplingInterval; 
		return velocityCut(v, T); 
	}

	public int velocityCut(Range range, double smoothT) {
		System.err.print("   Discarding unsuitable mapping speeds. ");
	
		Vector2D[] v = getScanningVelocities(smoothT);
		
		int flagged = 0;
		int cut = 0;

		final int nt = size();
		for(int t=0; t<nt; t++) if(get(t) != null) {
			if(v[t] == null) {
				set(t, null);
				cut++;
			}
			else {	
				double speed = v[t].length();
				if(speed < range.min) {
					get(t).flag(Frame.SKIP_SOURCE);
					flagged++;
				}
				else if(speed > range.max) {
					set(t, null);
					cut++;
				}
			}
		}
		
		System.err.print("[" + (int)Math.round(100.0 * flagged / size()) + "% flagged, ");
		System.err.println((int)Math.round(100.0 * cut / size()) + "% clipped]");

		return cut;
	}

	public int accelerationCut(double maxA) {
		System.err.print("   Discarding excessive telescope accelerations. ");
	
		Vector2D[] a = getAccelerations();

		int cut = 0;
		final int nt = size();
		for(int t=0; t<nt; t++) if(get(t) != null) {
			Vector2D value = a[t];
			
			if(value == null) {
				set(t, null);
				cut++;
			}
			else if(!(value.length() <= maxA)) {
				set(t, null);
				cut++;
			}
		}

		System.err.println("[" + (int)Math.round(100.0 * cut / size()) + "% clipped]");
		
		return cut;
	}
	
	public Vector2D[] getAccelerations() { 
		double T = hasOption("positions.smooth") ? option("positions.smooth").getDouble() * Unit.s : instrument.samplingInterval; 
		return getAccelerations(T); 	
	}
	
	public Vector2D[] getAccelerations(double smoothT) {
		Vector2D[] pos = getSmoothPositions(Motion.TELESCOPE, framesFor(smoothT));
		Vector2D[] a = new Vector2D[size()];
	
		final int ntm1 = size() - 1;
		for(int t=1; t<ntm1; t++) {
			if(pos[t] == null || pos[t+1] == null || pos[t-1] == null) a[t] = null;
			else {
				a[t] = new Vector2D();
				a[t].x = (pos[t+1].x + pos[t-1].x - 2.0*pos[t].x);
				a[t].y = (pos[t+1].y + pos[t-1].y - 2.0*pos[t].y);
				a[t].scale(1.0/instrument.samplingInterval);
			}
		}
	
		return a;
	}
	
	
	public Signal getAccelerationSignal(Mode mode, Motion direction) {
		Vector2D[] a = getAccelerations();
		double[] data = new double[size()];	
		for(int t=0; t<data.length; t++) data[t] = a[t] == null ? Float.NaN : direction.getValue(a[t]);
		return new Signal(mode, this, data);
	}
	

	public synchronized void highPassFilter(double T) {
		int Nt = FFT.getPaddedSize(size());
	
		// sigmat sigmaw = 1 ->
		// Dt sigmaw = 2.35 
		// Dt 2Pi sigmaf = 2.35 --> sigmaf = 2.35/2Pi 1/Dt
		// df = 1/(Nt*dt);
		// sigmaf / df = 2.35/2Pi * (Nt*dt)/Dt 
	
		double sigma_f = 2.35/(2.0*Math.PI) * Nt * instrument.samplingInterval / T;	
		
		double A = 0.5/(sigma_f*sigma_f);
		double[] response = new double[Nt >> 1];
		for(int f=response.length; --f >= 0; ) response[f] = -Math.expm1(-A*f*f);
		
		filter(instrument.getConnectedChannels(), response);
		
		comments += "h";
		filterTimeScale = T;
	}

	
	public synchronized void filter(ChannelGroup<? extends Channel> channels, double[] response) {
		comments += "F";
		
		float[] signal = new float[FFT.getPaddedSize(size())];
		
		for(final Channel channel : channels) {
			final int c = channel.index;
			getWeightedTimeStream(channel, signal);
			
			FFT.forwardRealInplace(signal);
			
			toRejected(signal, response);
			
			FFT.backRealInplace(signal);
			
			// Re-level the useful part of the signal
			level(signal, channel);
				
			for(Frame exposure : this) if(exposure != null) exposure.data[c] -= signal[exposure.index];
		}
	}

	protected void toRejected(float[] spectrum, double[] response) {
		// Calculate the idealized filter (no flags, no padding).
		spectrum[0] *= 1.0 - response[0];
		spectrum[1] *= 1.0 - response[response.length-1];
		
		for(int i=spectrum.length; --i >= 2; ) {
			final int F = (int) ((long)i * response.length / spectrum.length);
			final float rejection = (float) (1.0 - response[F]);
			spectrum[i] *= rejection;
			spectrum[--i] *= rejection;
		}
	}
	
	
	protected void level(float[] signal, Channel channel) {
		final int c = channel.index;
		double sum = 0.0, sumw = 0.0;
		for(int i=size(); --i >= 0; ) {
			final Frame exposure = get(i);
			if(exposure == null) continue;
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
			if(exposure.sampleFlag[c] != 0) continue;
			sum += exposure.relativeWeight * signal[i];
			sumw += exposure.relativeWeight;
		}
		if(sumw <= 0.0) Arrays.fill(signal, 0, size(), 0.0F);
		else {
			float ave = (float) (sum / sumw);
			for(int i=size(); --i >= 0; ) signal[i] -= ave;			
		}
	}
	
			
	public void checkForNaNs(Iterable<? extends Channel> channels, int from, int to) {
		comments += "?";
		
		to = Math.min(to, size());
		
		for(int t=from; t<to; t++) {
			Frame exposure = get(t);
			
			if(exposure != null) for(Channel channel : channels) {

				if(Float.isNaN(exposure.data[channel.index])) {
					comments += "NaN: " + exposure.index + "," + channel.index;
					System.err.println("   " + comments);
					System.exit(1);
				}

				if(Float.isInfinite(exposure.data[channel.index])) {
					comments += "Inf: " + exposure.index + "," + channel.index;
					System.err.println("   " + comments);
					System.exit(1);
				}

				/*
				if(exposure.data[channel.index] > 10.0) {
					comments += "Big: " + exposure.index + "," + channel.index;
					System.err.println("   " + comments);
					System.exit(1);
				}
				*/
			}
		}
	
	}

	// Downsampling strictly requires that no null frames contribute, s.t. the downsampled noise remains
	// uniform...
	// The alternative would be to include sample count in weights everywhere...
	@SuppressWarnings("unchecked")
	public synchronized void downsample(int n) {
		System.err.print("   Downsampling by " + n);
		
		final int windowSize = (int)Math.round(1.82 * WindowFunction.getEquivalenWidth("Hann")*n);
		final int centerOffset = windowSize/2 + 1;
		final double[] w = WindowFunction.get("Hann", windowSize);
		final Vector<FrameType> buffer = new Vector<FrameType>((size()-windowSize)/n+1); 
		final double[] value = new double[instrument.size()];
		
		// Normalize window function to absolute intergral 1
		double norm = 0.0;
		for(int i=0; i<w.length; i++) norm += Math.abs(w[i]);
		for(int i=0; i<w.length; i++) w[i] /= norm;
		
		final int nt = size();
		
		for(int T=0, to=windowSize; to <= nt; T++, to += n) {
			FrameType central = get(to-centerOffset);
			FrameType downsampled = null;
			
			if(central != null) {
				Arrays.fill(value, 0.0);
					
				for(int t=to-windowSize; t<to; t++) {
					final FrameType exposure = get(t);
					if(exposure == null) {
						central = null;
						break;
					}
					final double weight = w[to-t-1];
					for(Channel channel : instrument) value[channel.index] += weight * exposure.data[channel.index];
				}	
				
				if(central != null) {
					downsampled = (FrameType) central.clone();
					for(Channel channel : instrument) downsampled.data[channel.index] = (float) value[channel.index];
				}
			}
			
			buffer.add(downsampled);
		}
		
		System.err.println(" to " + buffer.size() + " frames.");
	
		instrument.samplingInterval *= n;
		instrument.integrationTime *= n;
		
		clear();
		trimToSize();
		ensureCapacity(buffer.size());
		addAll(buffer);
		reindex();
	}

	// TODO redo in a smarter way, with Frames providing the data...
	/*
	public synchronized void shift(double dt) {
		double[][] coords = new double[7][size()];
		for(int t=0; t<size(); t++) {
			if(get(t) != null) {
				Frame exposure = get(t);
				if(exposure.isUnflagged(Frame.BAD_DATA)) {
					coords[0][t] = exposure.equatorial.RA();
					coords[1][t] = exposure.equatorial.DEC();
					coords[2][t] = exposure.LST;
					coords[3][t] = exposure.horizontal.AZ();
					coords[4][t] = exposure.horizontal.EL();
					coords[5][t] = exposure.getTrackingOffset().x;
					coords[6][t] = exposure.getTrackingOffset().y;
				}
				else for(int i=0; i<7; i++) coords[i][t] = Double.NaN;
			}
			else for(int i=0; i<7; i++) coords[i][t] = Double.NaN;
		}
	
		for(int i=0; i<7; i++) coords[i] = ArrayExtras.shift(coords[i], dt/instrument.samplingInterval, 0.5);
	
		for(int t=0; t<size(); t++) if(get(t) != null) {
			if(Double.isNaN(coords[0][t])) set(t, null);
			else {
				Frame exposure = get(t);
				exposure.equatorial.setRA(coords[0][t]);
				exposure.equatorial.setDEC(coords[1][t]);
				exposure.LST = coords[2][t];
				exposure.horizontal.setAZ(coords[3][t]);
				exposure.horizontal.setEL(coords[4][t]);
				exposure.getTrackingOffset().x = coords[5][t];
				exposure.getTrackingOffset().y = coords[6][t];		
			}
		}
	}
	*/

	public synchronized void offset(double value) {
		for(Frame exposure : this) if(exposure != null) {
			for(Channel channel : instrument) if(channel.flag == 0) exposure.data[channel.index] += value;			
		}
	}


	public void writeASCIITimeStream() throws IOException {
		String filename = CRUSH.workPath + File.separator + scan.getID() + "-" + getID() + ".tms";
		PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(filename), 1000000));
		out.println(Util.e3.format(1.0/instrument.samplingInterval));
		final int nc = instrument.size();
		
		for(Frame exposure : this) {
			boolean isEmpty = true;
			if(exposure != null) if(exposure.isUnflagged(Frame.BAD_DATA)) {
				isEmpty = false;
				for(int c=0; c<nc; c++) 
					out.print(Util.e5.format((exposure.sampleFlag[c] & Frame.SAMPLE_SPIKE_FLAGS) != 0 ? Double.NaN : exposure.data[c]) + "\t");
			}
			if(isEmpty) for(int c=0; c<nc; c++) out.print(Double.NaN + "\t\t");
			out.println();
				
		}
		out.flush();
		out.close();
		System.err.println("Written ASCII time-streams to " + filename);
	}

	public double[][] getCovariance() {
		System.err.print(" Calculating Covariance Matrix (this may take a while...)");
		
		double[][] covar = new double[instrument.size()][instrument.size()];
		int[][] n = new int[instrument.size()][instrument.size()];
		
		for(Frame exposure : this) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS)) {
			for(int c1=instrument.size(); --c1>=0; ) if(instrument.get(c1).flag == 0) if(exposure.sampleFlag[c1] == 0) {
				final double[] rowC = covar[c1];
				final int[] rowN = n[c1];
				for(int c2=instrument.size(); --c2 > c1; ) if(instrument.get(c2).flag == 0) if(exposure.sampleFlag[c2] == 0) {
					rowC[c2] += exposure.relativeWeight * exposure.data[c1] * exposure.data[c2];
					rowN[c2]++;
				}
			}
		}

		for(int c1=instrument.size(); --c1>=0; ) {
			final double[] rowC = covar[c1];
			final int[] rowN = n[c1];
			for(int c2=instrument.size(); --c2 > c1; ) {
				rowC[c2] *= Math.sqrt(instrument.get(c1).weight * instrument.get(c2).weight) / rowN[c2];
				covar[c2][c1] = rowC[c2];
			}
		}
	
		System.err.println();
		
		return covar;
	}

	public double[][] getGroupCovariance(ChannelDivision<?> division, double[][] covar) {	
		int n = 0;
		for(ChannelGroup<?> channels : division) n+= channels.size();
	
		double[][] groupedCovar = new double[n][n];
		for(int k=0; k<n; k++) Arrays.fill(groupedCovar[k], Double.NaN);
	
		
		int k1 = 0;
		for(ChannelGroup<?> g1 : division) for(Channel ch1 : g1) {
			int k2 = 0;
			for(ChannelGroup<?> g2 : division) for(Channel ch2 : g2) groupedCovar[k1][k2++] = covar[ch1.index][ch2.index];
			k1++;
		}
	
		return groupedCovar;
	}

	public double[][] getFullCovariance(double[][] covar) {
		
		double[][] fullCovar = new double[instrument.storeChannels][instrument.storeChannels];
		
		for(int i=0; i<fullCovar.length; i++) Arrays.fill(fullCovar[i], Double.NaN);
		
		for(Channel c1 : instrument) for(Channel c2 : instrument)
			fullCovar[c1.dataIndex-1][c2.dataIndex-1] = covar[c1.index][c2.index];
			
		return fullCovar;
	}

	protected void writeCovariance(String name, double[][] covar) throws IOException, FitsException {
		if(!name.endsWith(".fits")) name += "-" + scan.getID() + "-" + getID() + ".fits";
		if(covar == null) return;
		Fits fits = new Fits();
		BasicHDU hdu = Fits.makeHDU(covar);
		fits.addHDU(hdu);
		fits.write(new BufferedDataOutputStream(new FileOutputStream(name)));	
		System.err.println(" Written " + name);
	}

	float[][] getSpectra() {
		return getSpectra("Hamming", 2*framesFor(filterTimeScale));
	}
	
	float[][] getSpectra(String windowName, int windowSize) {
		double[] w = WindowFunction.get(windowName, windowSize);

		// System.err.println("  Calculating Power spectra.");
		float[] data = new float[size()];
		float[][] spectra = new float[instrument.size()][];
		double df = 1.0 / (instrument.samplingInterval * windowSize);	
		float Jy = gain * (float) instrument.janskyPerBeam();
		
		final int nt = size();

		for(final Channel channel : instrument) {
			for(int t=nt; --t >= 0; ) {
				final Frame exposure = get(t);
				if(exposure == null) data[t] = 0.0F;
				else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = 0.0F;
				else if(exposure.sampleFlag[channel.index] != 0) data[t] = 0.0F;
				else data[t] = exposure.data[channel.index];
			}
			float[] spectrum = FFT.averagePower(data, w);
			float[] channelSpectrum = new float[spectrum.length];
			
			for(int i=spectrum.length; --i>=0; ) channelSpectrum[i] = (float) Math.sqrt(spectrum[i] / df) / Jy;
			
			spectra[channel.index] = channelSpectrum;
		}
  
		return spectra;
	}
	
	public void writeSpectra(String windowName, int windowSize) throws IOException {
		String fileName = CRUSH.workPath + File.separator + scan.getID() + "-" + getID() + ".spec";

		float[][] spectrum = getSpectra(windowName, windowSize);
		double df = 1.0 / (instrument.samplingInterval * windowSize);
		
		PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileName), 1000000));

		out.println("# CRUSH Residual Detector Power Spectra");
		out.println();
		out.println(getASCIIHeader());
		out.println("# Window Function: " + windowName);
		out.println("# Window Size: " + windowSize + " samples");
		out.println("# PSD unit: 'Jy/sqrt(Hz)'");
		out.println();
		out.println("# f(Hz)\t PSD(ch=1)...");

		for(int f=1; f<spectrum[0].length; f++) {
			out.print(Util.e3.format(f*df));
			for(Channel channel : instrument) out.print("\t" + Util.e3.format(spectrum[channel.index][f]));
			out.println();
		}

		out.flush();
		out.close();

		System.err.println("Written Power spectra to " + fileName);

	}


	public void writeCovariances() {
		String scanID = getFullID("-");
		
		double[][] covar = getCovariance(); 
		Vector<String> specs = hasOption("write.covar") ? option("write.covar").getList() : new Vector<String>();
		String prefix = CRUSH.workPath + File.separator + "covar";
		
		// If no argument is specified, write the full covariance in backend channel ordering
		if(specs.size() == 0) specs.add("full");

		for(String name : specs) {
			if(name.equalsIgnoreCase("full")){
				try { writeCovariance(prefix + "-" + scanID + ".fits", getFullCovariance(covar)); }
				catch(Exception e) { e.printStackTrace(); }	
			}
			else if(name.equalsIgnoreCase("reduced")){
				try { writeCovariance(prefix + "-" + scanID + ".reduced.fits", covar); }
				catch(Exception e) { e.printStackTrace(); }	
			}	
			else {
				ChannelDivision<?> division = instrument.divisions.get(name);
				if(division == null) System.err.println("   Cannot write covariance for " + name + ". Undefined grouping.");
				else {
					try { writeCovariance(prefix + "-" + scanID + "." + name + ".fits", getGroupCovariance(division, covar)); }
					catch(Exception e) { e.printStackTrace(); }	
				}
			}
		}
	}
	
	public void writeProducts() {
		String scanID = getFullID("-");

		if(hasOption("write.pixeldata")) {
			String fileName = CRUSH.workPath + File.separator + "pixel-" + scanID + ".dat";
			try { instrument.writePixelData(fileName, getASCIIHeader()); }
			catch(Exception e) { e.printStackTrace(); }
		}

		if(hasOption("write.covar")) writeCovariances();
			
		if(hasOption("write.ascii")) {
			try { writeASCIITimeStream(); }
			catch(Exception e) { e.printStackTrace(); }
		}
		
		if(hasOption("write.phases")) if(isPhaseModulated()) {
			try { getPhases().write(); }
			catch(Exception e) { e.printStackTrace(); }
		}
		
		if(hasOption("write.spectrum")) {
			Configurator spectrumOption = option("write.spectrum");
			String argument = spectrumOption.getValue();
			String windowName = argument.length() == 0 ? "Hamming" : argument;
			int windowSize = spectrumOption.isConfigured("size") ? spectrumOption.get("size").getInt() : 2*framesFor(filterTimeScale);
		
			try { writeSpectra(windowName, windowSize); }
			catch(Exception e) { e.printStackTrace(); }
		}
		
		if(hasOption("write.signals")) for(Mode mode : signals.keySet()) {
			try { 
				PrintStream out = new PrintStream(new FileOutputStream(mode.name + ".tms"));
				signals.get(mode).print(out);
				System.err.println("Written " + mode.name + ".tms");
				out.close();
			}
			catch(IOException e) {}
		}
	}
	
	public void getFitsData(LinkedHashMap<String, Object> data) {
		data.put("Obs", new int[] { integrationNo });
		data.put("Integration_Time", new double[] { size()*instrument.integrationTime });
		data.put("Frames", new int[] { size() });
		data.put("Relative_Gain", new double[] { gain });
		data.put("NEFD", new double[] { nefd } );
		data.put("Hipass_Timescale", new double[] { filterTimeScale / Unit.s } );
		data.put("Filter_Resolution", new double[] { 0.5/FFT.getPaddedSize(framesFor(filterTimeScale)) } );
	}
	
	
	public void addDetails(LinkedHashMap<String, Object> data) {
		int[] dataIndex = new int[instrument.size()];
		float[] channelGain = new float[instrument.size()];
		float[] channelOffset = new float[instrument.size()];
		float[] channelWeight = new float[instrument.size()];
		int[] channelFlags = new int[instrument.size()];
		float[][] filterProfile = new float[instrument.size()][];
		short[] channelSpikes = new short[instrument.size()];

		for(Channel channel : instrument) {
			dataIndex[channel.index] = channel.dataIndex;
			channelGain[channel.index] = (float) channel.gain;
			channelOffset[channel.index] = (float) channel.offset;
			channelWeight[channel.index] = (float) channel.weight;
			channelFlags[channel.index] = channel.flag;
			filterProfile[channel.index] = channel.filterResponse;
			channelSpikes[channel.index] = (short) channel.spikes;
		}
		
		data.put("Channel_Index", dataIndex);
		data.put("Channel_Gain", channelGain);
		data.put("Channel_Offset", channelOffset);
		data.put("Channel_Weight", channelWeight);
		data.put("Channel_Flags", channelFlags);
		data.put("Channel_Spikes", channelSpikes);
		data.put("Filter_Profile", filterProfile);
		data.put("Noise_Spectrum", getSpectra());		
	}
	
	
	public void speedTest() {
		Frame[] frame = new Frame[size()];
		frame = toArray(frame);
		
		Channel[] channel = new Channel[instrument.size()];
		channel = instrument.toArray(channel);
		
		int m=0;
		int iters = 10;
		
		// First the array...
		int i = 0;
		long a = System.currentTimeMillis();
		for(int k=0; k<iters; k++) for(int t=0; t<frame.length; t++) {
			final Frame exposure = frame[t];
			if(exposure != null) for(int c=0; c<channel.length; c++) {
				final Channel pixel = channel[c];
				i += exposure.sampleFlag[pixel.index];
			}
		}
		a = System.currentTimeMillis() - a;
		m += i; 
		
		// Then the ArrayList
		i = 0;
		long b = System.currentTimeMillis();
		for(int k=0; k<iters; k++)  for(Frame exposure : this) if(exposure != null) for(Channel pixel : instrument) 
			i += exposure.sampleFlag[pixel.index];
		b = System.currentTimeMillis() - b;
		m += i;
		
		// Then with two operations
		i = 0;
		int j=0;
		long c = System.currentTimeMillis();
		for(int k=0; k<iters; k++) for(Frame exposure : this) if(exposure != null) for(Channel pixel : instrument) {
			i += exposure.sampleFlag[pixel.index];
			j += i;
		}
		c = System.currentTimeMillis() - c;
		m += i;
		m += j;
		
		
		// Then the ArrayList
		i = 0;
		Vector<Frame> frames = new Vector<Frame>();
		Vector<Channel> channels = new Vector<Channel>();
		frames.addAll(this);
		channels.addAll(instrument);
		
		long d = System.currentTimeMillis();
		for(int k=0; k<iters; k++)  for(Frame exposure : frames) if(exposure != null) for(Channel pixel : channels) 
			i += exposure.sampleFlag[pixel.index];
		d = System.currentTimeMillis() - d;
		m += i;
		
		// Then the ArrayList in inverted order
		i = 0;
		long e = System.currentTimeMillis();
		for(int k=0; k<iters; k++) for(Channel pixel : instrument) for(Frame exposure : this) if(exposure != null) 
			i += exposure.sampleFlag[pixel.index];
		e = System.currentTimeMillis() - e;
		m += i;
		
		
		// array (inverted order)...
		i = 0;
		long f = System.currentTimeMillis();
		for(int k=0; k<iters; k++) for(int p=0; p<channel.length; p++) {
			final Channel pixel = channel[p];
			for(int t=frame.length; --t >= 0; ) {
				final Frame exposure = frame[t];
				if(exposure != null) i += exposure.sampleFlag[pixel.index];
			}
		}
		f = System.currentTimeMillis() - f;
		m += i; 
		
		long addTime = c - b;
		a -= addTime;
		b -= addTime;
		d -= addTime;
		e -= addTime;
		f -= addTime;
		
		System.err.println("> " + Integer.toHexString(m));
		System.err.println("# array:     " + a/iters + " ms\t(inverted: " + f/iters + " ms)");
		System.err.println("# ArrayList: " + b/iters + " ms\t(inverted: " + e/iters + " ms)");
		System.err.println("# Vector:    " + d/iters + " ms");
		
		System.exit(0);
	}
	
	public void detectChopper() {
		
		Signal x = getPositionSignal(null, Motion.CHOPPER, Motion.X);
		Signal y = getPositionSignal(null, Motion.CHOPPER, Motion.Y);
		
		x.level(true);
		y.level(true);
		
		int xTransitions = 0, yTransitions = 0;
		int xFrom = -1, xTo = -1, yFrom = -1, yTo = -1;
		
		Frame first = getFirstFrame();
		boolean xPositive = x.valueAt(first) > 0.0;
		boolean yPositive = y.valueAt(first) > 0.0; 
		final int nt = size();
		
		double sumA = 0.0, sumw = 0.0;
		
		float[] distance = new float[nt];
		final double threshold = instrument.getMinBeamFWHM() / 2.5;
		int n=0;
			
		for(int t=1; t<nt; t++) {
			final Frame exposure = get(t);
			if(exposure == null) continue;
			
			final double dx = x.valueAt(exposure);
			final double dy = y.valueAt(exposure);
			
			// Check for x-transition
			if((dx > threshold && !xPositive) || (dx < -threshold && xPositive)) {
				xPositive = !xPositive;
				if(xTransitions == 0) xFrom = t;
				else xTo = t;
				xTransitions++;
			}
			// Check for y-transition
			if((dy > threshold && !yPositive) || (dy < -threshold && yPositive)) {
				yPositive = !yPositive;
				if(yTransitions == 0) yFrom = t;
				else yTo = t;
				yTransitions++;
			}
			
			if(xTransitions > 0 || yTransitions > 0) {
				double d = Math.hypot(dx, dy);
				if(d > threshold) {
					int sign = (dx < 0.0) ? -1 : 1;
					if(dx == 0.0) sign = (dy < 0.0) ? -1 : 1;
					sumA += d * Math.atan2(sign*dy, sign*dx);
					sumw += d;			
				}
				distance[n++] = (float) d;
			}
		}
		
		int transitions = Math.max(xTransitions, yTransitions);
		int from = xTransitions > yTransitions ? xFrom : yFrom;
		int to = xTransitions > yTransitions ? xTo : yTo;
		
		if(transitions > 2) {
			for(int t=to; t<nt; t++) if(get(t) != null) n--;	
			
			double dt = get(to).MJD - get(from).MJD;
			dt *= Unit.day;
			
			chopper = new Chopper();
			chopper.amplitude = Statistics.median(distance, 0, n);
			if(chopper.amplitude < threshold) {
				chopper = null;
				System.err.println("   Small chopper fluctuations (assuming chopper not used).");
				instrument.options.forget("chopped");
				return;
			}
			chopper.positions = 2;
			chopper.frequency = (transitions-1) / (2.0*dt);
			chopper.angle = sumA / sumw;
	
			int steady = 0;
			for(int k=0; k<n; k++) if(Math.abs(distance[k] - chopper.amplitude) < threshold) steady++;
			chopper.efficiency = (double)steady / n;
			
			System.err.println("   Chopper detected: " + chopper.toString());
			instrument.options.process("chopped", "");
		}
		else {
			System.err.println("   Chopper not used.");
			instrument.options.forget("chopped");
		}	
	}

	public String getASCIIHeader() {
		return 
			"# Instrument: " + instrument.name + "\n" +
			"# Scan: " + scan.getID() + "\n" +
			(scan.size() > 1 ? "# Integration: " + (integrationNo + 1) + "\n" : "") +
			"# Object: " + scan.sourceName + "\n" +
			"# Date: " + scan.timeStamp + " (MJD: " + scan.MJD + ")\n" +
			"# Project: " + scan.project + "\n" +
			"# Exposure: " + (getFrameCount(Frame.SOURCE_FLAGS) * instrument.integrationTime) + " s.\n" +
			"# Equatorial: " + scan.equatorial + "\n" +
			(scan instanceof GroundBased ? "# Horizontal: " + scan.horizontal + "\n" : "") +
			"# CRUSH version: " + CRUSH.version;
	}
	
	public String getID() {
		return Integer.toString(integrationNo + 1);
	}
	
	public String getFullID(String separator) {
		return scan.getID() + separator + (integrationNo+1);		
	}
	
	public String getDisplayID() {
		return scan.size() > 1 | scan.hasSiblings ? getFullID("|") : scan.getID();
	}

	public synchronized boolean perform(String task) {
		boolean isRobust = false;
		if(hasOption("estimator")) if(option("estimator").equals("median")) isRobust = true;
		
		boolean accomplished = true;
		
		if(task.equals("offsets")) {
			removeDrifts(instrument, size(), isRobust, isRobust);	    
		}
		else if(task.equals("drifts")) {
			if(isPhaseModulated()) return false;
			Configurator driftOptions = option("drifts");
			String method = driftOptions.isConfigured("method") ? driftOptions.get("method").getValue() : "blocks"; 
			int driftN = filterFramesFor(option("drifts").getValue(), 10.0*Unit.sec);
			if(method.equalsIgnoreCase("fft")) highPassFilter(driftN * instrument.samplingInterval);
			else removeDrifts(instrument, driftN, isRobust, false);
		}
		else if(task.startsWith("correlated.")) {	
			String modalityName = task.substring(task.indexOf('.')+1);
			accomplished = decorrelate(modalityName, isRobust);
		}
		else if(task.equals("weighting")) {
			getWeights();
		}
		else if(task.equals("weighting.frames")) {
			int n = hasOption("weighting.frames.resolution") ? filterFramesFor(option("weighting.frames.resolution").getValue(), 10.0 * Unit.s) : 1;
			getTimeWeights(FFT.getPaddedSize(n));
			updatePhases();
		}
		else if(task.startsWith("despike")) {
			despike(option(task));
			updatePhases();
		}
		else if(task.equals("whiten")) {
			whiten();
			if(indexOf("whiten") > indexOf("weighting")) getWeights();
			updatePhases();
		}
		else return false;
		
		if(!accomplished) return false;
		
		comments += " ";	
	
		Thread.yield();
		
		return true;
	}

	public boolean isPhaseModulated() {
		if(chopper == null) return false;
		if(chopper.phases == null) return false;
		if(chopper.phases.isEmpty()) return false;
		return true;
	}
	
	public void updatePhases() {
		if(isPhaseModulated()) getPhases().update(instrument);
	}
	
	public PhaseSet getPhases() {
		return chopper == null ? null : chopper.phases;
	}
	
	public void getWeights() {
		String method = "rms";
		Configurator weighting = option("weighting");
		weighting.mapValueTo("method");
		
		if(weighting.isConfigured("method")) method = weighting.get("method").getValue().toLowerCase();
		
		getWeights(method);
	}
	
	public void getWeights(String method) {
		if(method.equals("robust")) getRobustPixelWeights();
		else if(method.equals("differential")) getDifferencialPixelWeights();
		else getPixelWeights();	
	}
	

	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
			
		if(name.equals("scale")) return Util.defaultFormat(gain, f);
		else if(name.equals("NEFD")) return Util.defaultFormat(nefd, f);
		else if(name.equals("zenithtau")) return Util.defaultFormat(zenithTau, f);
		else if(name.equals("tau")) return Util.defaultFormat(zenithTau / Math.cos(scan.horizontal.EL()), f);
		else if(name.startsWith("tau.")) {
			String id = name.substring(4).toLowerCase();
			return Util.defaultFormat(getTau(id), f);
		}
		else if(name.equals("scanspeed")) return Util.defaultFormat(aveScanSpeed.value / (Unit.arcsec / Unit.s), f);
		else if(name.equals("rmsspeed")) return Util.defaultFormat(aveScanSpeed.rms() / (Unit.arcsec / Unit.s), f);
		else if(name.equals("hipass")) return Util.defaultFormat(filterTimeScale / Unit.s, f);
		else if(name.equals("chopfreq")) {
			if(chopper == null) return "---";
			else return  Util.defaultFormat(chopper.frequency / Unit.Hz, f);
		}
		else if(name.equals("chopthrow")) {
			if(chopper == null) return "---";
			else return  Util.defaultFormat(2.0 * chopper.amplitude / instrument.getDefaultSizeUnit(), f);
		}
		else if(name.equals("chopeff")) {
			if(chopper == null) return "---";
			else return  Util.defaultFormat(chopper.efficiency, f);
		}
		else return instrument.getFormattedEntry(name, formatSpec);
	}
	
}
