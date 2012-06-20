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

import crush.filters.*;

import util.*;
import util.data.*;
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
implements Comparable<Integration<InstrumentType, FrameType>>, TableFormatter.Entries {
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
	public MultiFilter filter;
	
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
		clone.filter = null;

		return clone;
	}

	
	public int compareTo(Integration<InstrumentType, FrameType> other) {
		if(integrationNo == other.integrationNo) return 0;
		else return integrationNo < other.integrationNo ? -1 : 1;
	}
	
	public void reindex() {
		for(int t=size(); --t >= 0; ) {
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
		
		// Incorporate the relative instrument gain (under loading) in the scan gain...
		gain *= instrument.sourceGain;	
		
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

		// Continue only if enough valid channels remain...
		int minChannels = hasOption("mappingpixels") ? option("mappingpixels").getInt() : 2;
		if(instrument.mappingChannels < minChannels)
			throw new IllegalStateException("Too few valid channels (" + instrument.mappingChannels + ").");
		
		// Automatic downsampling after vclipping...
		if(hasOption("downsample")) if(option("downsample").equals("auto")) downsample();
	
		
		// Discard invalid frames at the beginning and end of the integration...
		trim();
			
		// Continue only if integration is long enough to be processed...
		int minFrames = hasOption("subscan.minlength") ? (int) Math.floor(option("subscan.minlength").getDouble() / instrument.samplingInterval) : 2;
		int mappingFrames = getFrameCount(Frame.SOURCE_FLAGS);
		if(getFrameCount(Frame.SOURCE_FLAGS) < minFrames) 
			throw new IllegalStateException("Integration is too short (" + Util.f1.format(mappingFrames * instrument.samplingInterval / Unit.s) + " seconds).");
		
		// Filter motion only after downsampling...
		if(hasOption("filter.ordering")) setupFilters();
		
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
		if(hasOption("jackknife.frames")) {
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
	
	/* TODO
	public void shiftData() {
		double dt = option("shift").getDouble();
		System.err.print(" Shifting by " + dt + " sec.");
		//shift(dt * Unit.s);
		System.err.println();		
	}
	*/
	
	public void selectFrames() {
		Range range = option("frames").getRange(true);
		final int from = (int)range.min();
		final int to = Math.min(size(), (int)range.max());
		
		final ArrayList<FrameType> buffer = new ArrayList<FrameType>(to-from+1);
		
		for(int t=from; t<to; t++) buffer.add(get(t));
		clear();
		addAll(buffer);
		reindex();
	}
	
	public void checkRange() {
		if(!hasOption("range")) return;
		final Range range = option("range").getRange();
		
		System.err.print("   Flagging out-of-range data. ");
		
		final int[] n = new int[instrument.size()];
		int N = 0;
		for(final Frame exposure : this) if(exposure != null) {
			for(final Channel channel : instrument) if(!range.contains(exposure.data[channel.index])) {
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
		final double critical = option("range.flagfraction").getDouble();
		for(final Channel channel : instrument) {
			if((double) n[channel.index] / N > critical) {
				channel.flag(Channel.FLAG_DAC_RANGE | Channel.FLAG_DEAD);
				flagged++;
			}
		}
	
		System.err.println(flagged + " channel(s) discarded.");
		
		instrument.census();
	}
	
	public void downsample() {
		// Keep to the rule of thumb of at least 2.5 samples per beam
		if(option("downsample").equals("auto")) {
			// Choose downsampling to accomodate at least 90% of scanning speeds... 
			double maxv = aveScanSpeed.value() + 1.25 * aveScanSpeed.rms();
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
			String calName = option("scale").getPath();
			try { setScaling(1.0 / CalibrationTable.get(calName).getValue(getMJD())); }
			catch(ArrayIndexOutOfBoundsException e) { System.err.println("     WARNING! " + e.getMessage()); }
			catch(IOException e) { System.err.println("WARNING! Calibration table could not be read."); }
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
				String tauName = option("tau").getPath();
				try { setTau(TauInterpolator.get(tauName).getValue(getMJD())); }
				catch(ArrayIndexOutOfBoundsException e) { System.err.println("     WARNING! " + e.getMessage()); }
				catch(IOException e) { System.err.println("WARNING! Tau interpolator table could not be read."); }
			}
		}
		catch(Exception e) { 
			System.err.println("    WARNING! " + e.getMessage()); 
			throw(e);
		}
	}
	
	public void setTau(String id, double value) {
		Vector2D t = (Vector2D) getTauCoefficients(id);
		Vector2D inband = (Vector2D) getTauCoefficients(instrument.getName());
		setZenithTau(inband.getX() / t.getX() * (value - t.getY()) + inband.getY());
	}
	
	public double getTau(String id, double value) {
		Vector2D t = (Vector2D) getTauCoefficients(id);
		Vector2D inband = (Vector2D) getTauCoefficients(instrument.getName());
		return t.getX() / inband.getX() * (value - inband.getY()) + t.getY();
	}
	
	public double getTau(String id) {
		return getTau(id, zenithTau);
	}
	
	public Vector2D getTauCoefficients(String id) {
		String key = "tau." + id.toLowerCase();
		
		if(!hasOption(key + ".a")) throw new IllegalStateException("   WARNING! " + key + " has undefined relationship.");
		
		Vector2D coeff = new Vector2D();
		coeff.setX(option(key + ".a").getDouble());
		if(hasOption(key + ".b")) coeff.setY(option(key + ".b").getDouble());
	
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
				+ Util.f1.format(aveScanSpeed.value()/(instrument.getSizeUnit()/Unit.s)) 
				+ " +- " + Util.f1.format(aveScanSpeed.rms()/(instrument.getSizeUnit()/Unit.s)) 
				+ " " + instrument.getSizeName() + "/s");
	}
	
	public void velocityClip() {
		Range vRange = null;
		Configurator option = option("vclip");
		
		if(option.equals("auto"))
			// Move at least 5 beams over the stability timescale
			// But less that 1/2.5 beams per sample to avoid smearing
			vRange = new Range(0.2*instrument.resolution / instrument.getStability(), 0.4 * instrument.resolution / instrument.samplingInterval);	
		else {
			vRange = option.getRange(true);
			vRange.scale(Unit.arcsec / Unit.s);
		}
		if(hasOption("chopped")) vRange.setMin(0.0);
		
		velocityCut(vRange);
	}
	
	public void accelerationClip() {
		double maxA = option("aclip").getDouble() * Unit.arcsec / Unit.s2;
		accelerationCut(maxA);
	}
	

	public void pointingAt(Vector2D offset) {
		for(final Frame frame : this) if(frame != null) frame.pointingAt(offset);
	}
	
	
	public void setupFilters() {
		System.err.println("   Configuring filters.");
		List<String> ordering = option("filter.ordering").getList();
		filter = new MultiFilter(this);
		for(final String name : ordering) {	
			Filter f = getFilter(name);
			if(f == null) System.err.println(" WARNING! No filter for '" + name + "'.");
			else filter.addFilter(f);
		}	
	}
	
	public Filter getFilter(String name) {
		name = name.toLowerCase();
		if(name.equals("motion")) return new MotionFilter(this, filter.getData());
		else if(name.equals("kill")) return new KillFilter(this, filter.getData());
		else if(name.equals("whiten")) return new WhiteningFilter(this, filter.getData());
		else return null;
	}
	
	public abstract FrameType getFrameInstance();
	
	public double getCrossingTime() {
		if(scan.sourceModel == null) return getCrossingTime(scan.sourceModel.getSourceSize());
		return getCrossingTime(scan.sourceModel.getSourceSize());
	}
	
	public double getCrossingTime(double sourceSize) {		
		if(chopper != null) return Math.min(chopper.stareDuration(), sourceSize / aveScanSpeed.value());
		return sourceSize / aveScanSpeed.value();		
	}

	public double getPointSize() {
		return scan.sourceModel == null ? instrument.resolution : scan.sourceModel.getPointSize();
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
		if(spec.equals("max")) {
			driftT = size();
		}
		else if(spec.equals("auto")) {
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

		final ArrayList<FrameType> buffer = new ArrayList<FrameType>(2*size());
		
		for(final FrameType exposure : this) if(!Double.isNaN(lastMJD)) {
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
			final ArrayList<FrameType> frames = new ArrayList<FrameType>(to - from);
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
		for(final Frame frame : this) if(frame != null) frame.slimTo(instrument);	
		instrument.reindex();
	}
	
	public synchronized void scale(double factor) {
		if(factor == 1.0) return;
		for(final Frame frame : this) if(frame != null) frame.scale(factor);
	}
	

	public Range getRange(ChannelGroup<?> channels) {
		Range range = new Range();
		
		for(final Frame frame : this) if(frame != null) {
			for(final Channel channel : channels) if(frame.sampleFlag[channel.index] == 0) range.include(frame.data[channel.index]);
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
		
		final int driftN = Math.min(size(), FFT.getPaddedSize(targetFrameResolution));
		final int step = quick ? (int) Math.pow(driftN, 2.0/3.0) : 1;
		filterTimeScale = Math.min(filterTimeScale, driftN * instrument.samplingInterval);
		
		final Dependents parms = getDependents("drifts");

		WeightedPoint[] buffer = null;	// The timestream for robust estimates
			
		final WeightedPoint[] aveOffset = new WeightedPoint[instrument.size()];
		for(int i=aveOffset.length; --i >= 0; ) aveOffset[i] = new WeightedPoint();
		
		if(driftN < size()) comments += (robust ? "[D]" : "D") + "(" + driftN + ")";
		else comments += robust ? "[O]" : "O";
		
		if(robust) {
			buffer = new WeightedPoint[(int) Math.ceil((double)size()/step)];
			for(int i=buffer.length; --i >= 0; ) buffer[i] = new WeightedPoint();
		}
		
		final int nt = size();
		for(int from=0; from < nt; from += driftN) {
			int to = Math.min(size(), from + driftN);
			parms.clear(channels, from, to);
			if(robust) medianLevel(channels, parms, from, to, step, buffer, aveOffset);
			else meanLevel(channels, parms, from, to, step, aveOffset);
			parms.apply(channels, from, to);
		}
		
		// Store the mean offset as a channel property...
		for(final Channel channel : channels) {
			final double G = isDetectorStage ? channel.getHardwareGain() : 1.0;
			channel.offset += G * aveOffset[channel.index].value();
		}
			
		if(driftN < size()) for(final Channel channel : channels) {
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
		for(final Signal signal : signals.values()) signal.removeDrifts();
		
		if(CRUSH.debug) checkForNaNs(channels, 0, size());
	}
	
	private synchronized void meanLevel(final ChannelGroup<?> group, final Dependents parms, final int fromt, int tot, final int step, final WeightedPoint[] aveOffset) {
		for(Channel channel : group) meanLevel(channel, parms, fromt, tot, step, aveOffset[channel.index]);
	}
	
	private synchronized void meanLevel(Channel channel, final Dependents parms, final int fromt, int tot, final int step, WeightedPoint aveOffset) {
		tot = Math.min(tot, size());
		
		double sum = 0.0, sumw = 0.0;
		
		// Calculate the weight sums for every pixel...
		for(int t=fromt; t<tot; t+=step) {
			final Frame exposure = get(t);
			if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {
				if(exposure.sampleFlag[channel.index] == 0) {
					sum += exposure.relativeWeight * exposure.data[channel.index];
					sumw += exposure.relativeWeight;
				}
			}
		}

		// Calculate the maximum-likelihood offsets and the channel dependence...
		if(sumw > 0.0) {
			sum /= sumw;
			parms.add(channel, 1.0);
			if(aveOffset != null) aveOffset.average(sum, sumw);
		}

		// Remove offsets from data and account frame dependence...
		for(int t=fromt; t<tot; t+=step) {
			final Frame exposure = get(t);
			if(exposure == null) continue;

			if(sumw > 0.0) { 
				exposure.data[channel.index] -= sum;
				parms.add(exposure, exposure.relativeWeight / sumw); 
			}
		}
	}
	
	private synchronized void medianLevel(final ChannelGroup<?> group, final Dependents parms, final int fromt, int tot, final int step, final WeightedPoint[] buffer, final WeightedPoint[] aveOffset) {
		tot = Math.min(tot, size());	
		for(final Channel channel : group) medianLevel(channel, parms, fromt, tot, step, buffer, aveOffset[channel.index]);
	}
	
	private synchronized void medianLevel(Channel channel, final Dependents parms, final int fromt, int tot, final int step, final WeightedPoint[] buffer, final WeightedPoint aveOffset) {
		tot = Math.min(tot, size());
	
		final WeightedPoint offset = new WeightedPoint();
					
		int n = 0;
		double sumw = 0.0;
		
		final int c = channel.index;
		
		for(int t=fromt; t<tot; t+=step) {
			final Frame exposure = get(t);
			if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] == 0) {
				final WeightedPoint point = buffer[n++];
				point.setValue(exposure.data[c]);
				point.setWeight(exposure.relativeWeight);
				sumw += point.weight();
			}
		}

		if(sumw > 0.0) {
			Statistics.smartMedian(buffer, 0, n, 0.25, offset);				
			if(aveOffset != null) aveOffset.average(offset);

			for(int t=fromt; t<tot; t+=step) {
				final Frame exposure = get(t);
				if(exposure == null) continue;
				exposure.data[c] -= offset.value();
				parms.add(exposure, exposure.relativeWeight / sumw);
			}
			parms.add(channel, 1.0);
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
			if(correlated.solveSignal) correlated.updateSignals(this, isRobust);
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
		for(final Channel channel : liveChannels) channel.weight = 0.0;	
		
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
		for(final Channel channel : liveChannels) channel.weight = 0.0;
		
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
 
		for(final Channel channel : liveChannels) if(channel.weight > 0.0) {
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
		comments += "tW";
		if(blockSize > 1) comments += "(" + blockSize + ")";
		getTimeWeights(blockSize, true); 
	}
	
	public void getTimeWeights(final int blockSize, boolean flag) {
		final ChannelGroup<?> detectorChannels = instrument.getDetectorChannels();
		
		final int nT = (int)Math.ceil((float)size() / blockSize);
		
		double sumfw = 0.0;
		int n = 0;
		
		for(int T=0,fromt=0; T<nT; T++) {
			int points = 0;
			double deps = 0.0;
			double sumChi2 = 0.0;
			
			final int tot = Math.min(fromt + blockSize, size());
			
			for(int t=tot; --t >= fromt; ) {
				final Frame exposure = get(t);
				if(exposure == null) continue;

				for(final Channel channel : detectorChannels) if(channel.isUnflagged()) if(exposure.sampleFlag[channel.index] == 0) {			
					final float value = exposure.data[channel.index];
					sumChi2 += (channel.weight * value * value);
					points++;
				}
				deps += exposure.dependents;
			}
			
			if(points > deps && sumChi2 > 0.0) {
				final double dof = 1.0 - deps / points;
				final float fw = (float) ((points-deps) / sumChi2);					
				
				for(int t=tot; --t >= fromt; ) {
					final Frame exposure = get(t);
					if(exposure == null) continue;
					exposure.unflag(Frame.FLAG_DOF);
					exposure.dof = dof;
					exposure.relativeWeight = fw;
					sumfw += fw;
					n++;
				}
			}
			else for(int t=tot; --t >= fromt; ) {
				final Frame exposure = get(t);
				if(exposure == null) continue;
				if(points > deps) exposure.relativeWeight = Float.NaN;
				else {
					exposure.relativeWeight = 0.0F;
					exposure.flag(Frame.FLAG_DOF);
				}
			}
			
			fromt = tot;
		}
		
		
		// Renormalize the time weights s.t. the pixel weights remain representative...
		final float inorm = n > 0 ? (float) (n / sumfw) : 1.0F; 
			
		Range wRange = new Range();
		
		if(hasOption("weighting.frames.noiserange")) wRange = option("weighting.frames.noiserange").getRange(true);
		else wRange.full();
		
		/*
		PrintStream out = null;
		try { out = new PrintStream(new FileOutputStream("frames.dat")); }
		catch(IOException e) { e.printStackTrace(); }
		*/
		 
		for(Frame exposure : this) if(exposure != null) {
			if(Float.isNaN(exposure.relativeWeight)) exposure.relativeWeight = 1.0F;
			else exposure.relativeWeight *= inorm;
			
			if(flag) {
				if(wRange.contains(exposure.relativeWeight)) exposure.unflag(Frame.FLAG_WEIGHT);
				else exposure.flag(Frame.FLAG_WEIGHT);
			}
			else exposure.unflag(Frame.FLAG_WEIGHT);
			
			/*
			out.println(exposure.index
					+ "\t" + exposure.isFlagged(Frame.FLAG_WEIGHT) 
					+ "\t" + exposure.relativeWeight 
					+ "\t" + exposure.dependents
					+ "\t" + exposure.dof
			); 
			*/
			
		}
		
		//out.close();
	}
	
	
	public void dejumpFrames() {
		final int resolution = hasOption("dejump.resolution") ? framesFor(option("dejump.resolution").getDouble() * Unit.sec) : 1;
		final double levelLength = hasOption("dejump.minlength") ? option("dejump.minlength").getDouble() * Unit.sec : 1.0 * Unit.sec;
		
		int minFrames = (int) Math.round(levelLength / instrument.samplingInterval);
		if(minFrames < 2) minFrames = Integer.MAX_VALUE;
		
		double level = hasOption("dejump.level") ? option("dejump.level").getDouble() : 2.0;
		boolean robust = false;
		
		if(hasOption("estimator")) if(option("estimator").equals("median")) robust=true;
		
		// Save the old time weights
		for(Frame exposure : this) if(exposure != null) exposure.tempC = exposure.relativeWeight;
		
		getTimeWeights(resolution, false);
			
		comments += robust ? "[J]" : "J";
	
		final Dependents parms = getDependents("jumps");
		
		WeightedPoint[] buffer = null;	// The timestream for robust estimates
			
		final WeightedPoint[] aveOffset = new WeightedPoint[instrument.size()];
		for(int i=aveOffset.length; --i >= 0; ) aveOffset[i] = new WeightedPoint();
		
		if(robust) {
			buffer = new WeightedPoint[size()];
			for(int i=buffer.length; --i >= 0; ) buffer[i] = new WeightedPoint();
		}
		
		// Convert level from rms to weight....
		level = 1.0 / (level * level);
		
		// Make sure that the level is significant at the 3-sigma level...
		level = Math.min(1.0 - 9.0 / instrument.mappingChannels, level);
		
		final int nt = size();
		
		int from = 0;
		int to = 0;
		int levelled = 0;
		int removed = 0;
		
		while((from = nextWeightTransit(to, level, -1)) < nt) {
			to = nextWeightTransit(from, level, 1);
			
			if(to - from > minFrames) {
				
				// Clear dependecies of any prior de-jumping. Will use new dependecies
				// on the currently obtained level for the interval.
				for(int t=from; t<to; t++) {
					final Frame exposure = get(t);
					if(exposure != null) parms.clear(exposure);
				}
				
				if(robust) medianLevel(instrument, parms, from, to, 1, buffer, aveOffset);		
				else meanLevel(instrument, parms, from, to, 1, aveOffset);
				
				// Level all correlated signals
				for(Signal signal : signals.values()) signal.level(from, to); 
					
				// Mark weights temporarily as NaN -- then these will be reset to 1.0 later...
				for(int t=from; t<to; t++) {
					Frame exposure = get(t);
					if(get(t) == null) continue;
					exposure.relativeWeight = Float.NaN;
				}
				
				parms.apply(instrument, from, to);
				
				levelled++;
				
			}
			else {
				for(int t=from; t<to; t++) if(get(t) != null) get(t).flag(Frame.FLAG_LEVEL);
				removed++;
			}
			
		}
		
		// Reinstate the old time weights
		for(Frame exposure : this) if(exposure != null) 
			exposure.relativeWeight = Float.isNaN(exposure.relativeWeight) ? 1.0F : exposure.tempC;
				
			
		comments += levelled + ":" + removed;
	}
	
	
	
	protected int nextWeightTransit(int fromt, double level, int direction) {
		int nt = size();
			
		for(int t=fromt; t<nt; t++) {	
			Frame exposure = get(t);
			if(exposure == null) continue;
			if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
			if(exposure.relativeWeight <= 0.0) continue;
		
			if(direction < 0) {
				if(exposure.relativeWeight < level) return t;
			}
			else {	
				if(exposure.relativeWeight > level) return t;
			}
		}
		return nt;
	}
	
	public void getTimeStream(final Channel channel, final double[] data) {
		final int c = channel.index;
		final int nt = size();
		for(int t=nt; --t >= 0; ) {
			final Frame exposure = get(t);
			if(exposure == null) data[t] = Double.NaN;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = Double.NaN;
			else if(exposure.sampleFlag[c] != 0) data[t] = Double.NaN;
			else data[t] = exposure.data[c];
		}
		// Pad if necessary...
		if(data.length > nt) Arrays.fill(data, nt, data.length, Double.NaN);
	}
	
	public void getTimeStream(final Channel channel, final float[] data) {
		final int c = channel.index;
		final int nt = size();
		for(int t=nt; --t >= 0; ) {
			final Frame exposure = get(t);
			if(exposure == null) data[t] = Float.NaN;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = Float.NaN;
			else if(exposure.sampleFlag[c] != 0) data[t] = Float.NaN;
			else data[t] = exposure.data[c];
		}
		// Pad if necessary...
		if(data.length > nt) Arrays.fill(data, nt, data.length, Float.NaN);
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
		for(int t=nt; --t >= 0; ) {
			final Frame exposure = get(t);
			if(exposure == null) data[t].noData();
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t].noData();
			else if(exposure.sampleFlag[c] != 0) data[t].noData();
			else {
				final WeightedPoint point = data[t];
				point.setValue(exposure.relativeWeight * exposure.data[c]);
				point.setWeight(exposure.relativeWeight);
			}
		}
		// Pad if necessary...
		if(data.length > nt) for(int t=nt; t<data.length; t++) data[t].noData();
	}

	public void getTimeStream(final Channel channel, final float[] data, final float[] weight) {
		final int c = channel.index;
		final int nt = size();
		for(int t=nt; --t >= 0; ) {
			final Frame exposure = get(t);
			if(exposure == null) data[t] = weight[t] = 0.0F;
			else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = weight[t] = 0.0F;
			else if(exposure.sampleFlag[c] != 0) data[t] = weight[t] = 0.0F;
			else {
				data[t] = exposure.relativeWeight * exposure.data[c];
				weight[t] = exposure.relativeWeight;
			}
		}
		// Pad if necessary...
		if(data.length > nt) for(int t=nt; t<data.length; t++) data[t] = weight[t] = 0.0F;
	}

	/*
	public double[] getPointSourceProfile(int nt) {
		double sigma = getPointCrossingTime() / Util.sigmasInFWHM / instrument.samplingInterval;
		double[] sourceProfile = new double[nt];
		int nF = nt >> 1;
		
		sourceProfile[0] = 1.0;
		for(int t=nF; --t > 0; ) {
			double dev = t / sigma;
			double a = Math.exp(-0.5*dev*dev);
			sourceProfile[t] = sourceProfile[sourceProfile.length-t] = a;
		}
		return sourceProfile;
	}
	*/
	
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
	
	
	public void despikeNeighbouring(final double significance) {
		comments += "dN";

		//final int delta = framesFor(filterTimeScale);
		final int delta = 1;
		final int excludeSamples = Frame.SAMPLE_SOURCE_BLANK | Frame.SAMPLE_SKIP;
		despikedNeighbours = true;
		
		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		
		for(final Channel channel : connectedChannels) channel.temp = (float) (significance * Math.sqrt(channel.variance));
		
		int nt = size();
		for(int t=delta; t < nt; t++) {
			final Frame exposure = get(t);
			final Frame prior = get(t-delta);
			
			if(exposure != null) if(prior != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(prior.isUnflagged(Frame.MODELING_FLAGS))  {
				final float chi = (float) (1.0 / Math.sqrt(exposure.relativeWeight) + 1.0 / Math.sqrt(prior.relativeWeight));
				
				for(final Channel channel : connectedChannels) if(((exposure.sampleFlag[channel.index] | prior.sampleFlag[channel.index]) & excludeSamples) == 0) {
					exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SPIKY_NEIGHBOUR;

					if(Math.abs(exposure.data[channel.index] - prior.data[channel.index]) > channel.temp * chi) {
						exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKY_NEIGHBOUR;
						prior.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKY_NEIGHBOUR;
					}
				}
			}	
		}
		
	}

	public void despikeAbsolute(final double significance) {
		comments += "dA";
		
		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		final int excludeSamples = Frame.SAMPLE_SOURCE_BLANK | Frame.SAMPLE_SKIP;
		
		for(final Channel channel : instrument) channel.temp = (float) (significance * Math.sqrt(channel.variance));

		for(final Frame exposure : this) if(exposure != null) {
			final float frameChi = 1.0F / (float)Math.sqrt(exposure.relativeWeight);
			for(final Channel channel : connectedChannels) if((exposure.sampleFlag[channel.index] & excludeSamples) == 0) {
				if(Math.abs(exposure.data[channel.index]) > channel.temp * frameChi) 
					exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;
				else 
					exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SPIKE;
			}
		}
	}
	

	public void despikeGradual(final double significance, final double depth) {
		comments += "dG";
		
		for(final Channel channel : instrument) channel.temp = (float) (significance * Math.sqrt(channel.variance));
		
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
					final double critical = Math.max(channel.gain * minSignal, channel.temp * frameChi);
					if(Math.abs(exposure.data[channel.index]) > critical) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;
					else exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SPIKE;
				}
			}
		}
		
	}
	
	public void despikeFeatures(double significance) {
		despikeFeatures(significance, framesFor(filterTimeScale));
	}
	
	public void despikeFeatures(final double significance, int maxBlockSize) {
		if(maxBlockSize > size()) maxBlockSize = size();
		comments += "dF";
		
		final ChannelGroup<?> liveChannels = instrument.getConnectedChannels();
		
		final int nt = size();
		final float[] data = new float[size()];
		final float[] weight = new float[size()];
		
		final DataPoint diff = new DataPoint();
		final DataPoint temp = new DataPoint();

		// Clear the spiky feature flag...
		for(final Frame exposure : this) if(exposure != null) for(final Channel channel : liveChannels) 
				exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SPIKY_FEATURE;		
		
		for(final Channel channel : instrument) {
			getTimeStream(channel, data, weight);
			
			// check and divide...
			int n = size();
			for(int blockSize = 1; blockSize <= maxBlockSize; blockSize *= 2) {
				for(int T=1; T < n; T++) if(T < n) {
					diff.setValue(data[T]);
					diff.setWeight(weight[T]);
					temp.setValue(data[T-1]);
					temp.setWeight(weight[T-1]);
					
					diff.subtract(temp);
					if(diff.significance() > significance) {
						for(int t=blockSize*(T-1), blockt=0; t<nt && blockt < 2*blockSize; t++, blockt++) {
							final Frame exposure = get(t);
							if(exposure != null) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKY_FEATURE;		
						}
					}
				}
				
				n /= 2;
				
				for(int to=0, from=0; to < n; to++, from += 2) {
					// to = average(from, from+1)
					data[to] = weight[from] * data[from] + weight[from+1] * data[from+1];
					weight[to] = weight[from] + weight[from+1];
					data[to] /= weight[to];
				}
			}
		}	

	}
	
	public void flagSpikyChannels(final int spikeTypes, final double flagFraction, final int minSpikes, final int channelFlag) {
		final int maxChannelSpikes = Math.max(minSpikes, (int)Math.round(flagFraction * size()));
		
		// Flag spiky channels even if spikes are in spiky frames
		//int frameFlags = LabocaFrame.MODELING_FLAGS & ~LabocaFrame.FLAG_SPIKY;
		
		// Only flag spiky channels if spikes are not in spiky frames
		final int frameFlags = Frame.MODELING_FLAGS;
	
		for(final Channel channel : instrument) channel.spikes = 0;
			
		for(final Frame exposure : this) if(exposure != null) if(exposure.isUnflagged(frameFlags)) 
			for(final Channel channel : instrument) if((exposure.sampleFlag[channel.index] & spikeTypes) != 0) channel.spikes++;
			
		for(final Channel channel : instrument) {
			if(channel.spikes > maxChannelSpikes) channel.flag(channelFlag);
			else channel.unflag(channelFlag);
		}
		
		instrument.census();
		comments += instrument.mappingChannels;
	}
			
	public void flagSpikyFrames(final int spikeTypes, final double minSpikes) {
		int spikyFrames = 0;
		
		// Flag spiky frames even if spikes are in spiky channels.
		//int channelFlags = ~(LabocaPixel.FLAG_SPIKY | LabocaPixel.FLAG_FEATURES);
		
		// Flag spiky frames only if spikes are not in spiky channels.
		final int channelFlags = ~0;
		
		for(final Frame exposure : this) if(exposure != null) {
			int frameSpikes = 0;

			for(final Channel channel : instrument) if(channel.isUnflagged(channelFlags))
				if((exposure.sampleFlag[channel.index] & spikeTypes) != 0) frameSpikes++;

			if(frameSpikes > minSpikes) {
				exposure.flag |= Frame.FLAG_SPIKY;
				spikyFrames++;
			}
			else exposure.flag &= ~Frame.FLAG_SPIKY;
		}
		
		//comments += "(" + Util.f1.format(100.0*spikyFrames/size()) + "%)";
		comments += "(" + spikyFrames + ")";
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
		
		for(final Channel channel : instrument) channel.temp = (float) channel.getHardwareGain();
		
		for(final Frame frame : this) if(frame != null) for(final Channel channel : instrument)
			frame.data[channel.index] /= channel.temp;
		
		isDetectorStage = true;		
	}
	
	public synchronized void readoutStage() { 
		if(!isDetectorStage) return;
		
		for(final Channel channel : instrument) channel.temp = (float) channel.getHardwareGain();
		
		for(final Frame frame : this) if(frame != null) for(final Channel channel : instrument)
			frame.data[channel.index] *= channel.temp;
		
		isDetectorStage = false;
	}
	
	public synchronized void clearData() {
		for(final Frame exposure : this) if(exposure != null) for(final Channel channel : instrument) exposure.data[channel.index] = 0.0F;
	}
	
	public synchronized void randomData() {
		final Random random = new Random();
		
		for(final Channel channel : instrument) channel.temp = (float)(Math.sqrt(1.0/channel.weight));
		
		for(Frame exposure : this) if(exposure != null) for(Channel channel : instrument)
			exposure.data[channel.index] = channel.temp * (float)random.nextGaussian();						
	
	}
	
	public void addCorrelated(final CorrelatedSignal signal) throws Exception {	
		final Mode mode = signal.getMode();
		final float[] gain = mode.getGains();
		final int nc = mode.size();
		
		for(final Frame exposure : this) {
			final float C = signal.valueAt(exposure);
			for(int k=nc; --k >= 0; ) exposure.data[mode.getChannel(k).index] += gain[k] * C;
		}
	}
	
	
	public Vector2D[] getPositions(int type) {
		final Vector2D[] position = new Vector2D[size()];
		final SphericalCoordinates coords = new SphericalCoordinates();

		for(int t=size(); --t >= 0; ) {
			final Frame exposure = get(t);
			if(exposure != null) {
				position[t] = new Vector2D();
				final Vector2D pos = position[t];
			
				// Telescope motion should be w/o chopper...
				// TELESCOPE motion with or w/o SCANNING and CHOPPER
				if((type & Motion.TELESCOPE) != 0) {
					coords.copy(exposure.getNativeCoords());
					// Subtract the chopper motion if it is not requested...
					if((type & Motion.CHOPPER) == 0) coords.subtractNativeOffset(exposure.chopperPosition);
					pos.setX(coords.getX());
					pos.setY(coords.getY());
					
					if((type & Motion.PROJECT_GLS) != 0) pos.scaleX(coords.cosLat());
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
		final double T = hasOption("positions.smooth") ? option("positions.smooth").getDouble() * Unit.s : instrument.samplingInterval;
		final int n = framesFor(T);

		final Vector2D[] pos = getPositions(type);
		if(n < 2) return pos;
		
		final Vector2D[] smooth = new Vector2D[size()];
		int valids = n;

		final Vector2D sum = new Vector2D();
		
		for(int t=n; --t >= 0; ) {
			if(pos[t] == null) valids--;
			else sum.add(pos[t]);
		}

		final int nm = n/2;
		final int np = n - nm;
		final int tot = size()-np-1;
		
		for(int t=nm; t<tot; t++) {
			if(pos[t + np] == null) valids--;
			else sum.add(pos[t + np]);
		
			if(valids > 0) {
				smooth[t] = (Vector2D) sum.clone();
				smooth[t].scale(1.0 / valids);
			}
			
			if(pos[t - nm] == null) valids++;
			else sum.subtract(pos[t - nm]);
		}
	
		return smooth;
	}
	
	
	public Signal getPositionSignal(Mode mode, final int type, final Motion direction) {
		final Vector2D[] pos = getSmoothPositions(type);
		final double[] data = new double[size()];	
		for(int t=data.length; --t >= 0; ) data[t] = pos[t] == null ? Float.NaN : direction.getValue(pos[t]);
		Signal signal = new Signal(mode, this, data, true);
		return signal;
	}
	
	public Vector2D[] getScanningVelocities() { 
		final Vector2D[] pos = getSmoothPositions(Motion.SCANNING | Motion.CHOPPER);
		final Vector2D[] v = new Vector2D[size()];

		final double i2dt = 0.5 / instrument.samplingInterval;
		
		for(int t=size()-1; --t > 0; ) {
			if(pos[t+1] == null || pos[t-1] == null) v[t] = null;
			else {
				v[t] = new Vector2D(
						pos[t+1].getX() - pos[t-1].getX(),
						pos[t+1].getY() - pos[t-1].getY()
				);
				v[t].scale(i2dt);
			}
		}

		return v;
	}
	
	public DataPoint getAverageScanningVelocity(double smoothT) {
		final Vector2D[] v = getScanningVelocities();		
		final float[] speed = new float[v.length];
		
		int n=0;
		for(int t=v.length; --t >= 0; ) if(v[t] != null) speed[n++] = (float) v[t].length();
		double avev = n > 0 ? Statistics.median(speed, 0, n) : Double.NaN;
		
		n=0;
		for(int t=v.length; --t >= 0; ) if(v[t] != null) {
			float dev = (float) (speed[n] - avev);
			speed[n++] = dev*dev;
		}
		double w = n > 0 ? 0.454937/Statistics.median(speed, 0, n) : 0.0;
		
		return new DataPoint(new WeightedPoint(avev, w));
	}
	
	public int velocityCut(final Range range) { 
		System.err.print("   Discarding unsuitable mapping speeds. ");
	
		final Vector2D[] v = getScanningVelocities();
		
		int flagged = 0;
		int cut = 0;

		for(int t=size(); --t >= 0; ) if(get(t) != null) {
			if(v[t] == null) {
				set(t, null);
				cut++;
			}
			else {	
				final double speed = v[t].length();
				if(speed < range.min()) {
					get(t).flag(Frame.SKIP_SOURCE);
					flagged++;
				}
				else if(speed > range.max()) {
					set(t, null);
					cut++;
				}
			}
		}
		
		System.err.print("[" + (int)Math.round(100.0 * flagged / size()) + "% flagged, ");
		System.err.println((int)Math.round(100.0 * cut / size()) + "% clipped]");

		return cut;
	}

	public int accelerationCut(final double maxA) {
		System.err.print("   Discarding excessive telescope accelerations. ");
	
		final Vector2D[] a = getAccelerations();

		int cut = 0;
		for(int t=size(); --t >= 0; ) if(get(t) != null) {
			final Vector2D value = a[t];
			
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
		final Vector2D[] pos = getSmoothPositions(Motion.TELESCOPE);
		final Vector2D[] a = new Vector2D[size()];
		
		final double idt = 1.0 / instrument.samplingInterval;
		
		for(int t=size()-1; --t > 0; ) {
			if(pos[t] == null || pos[t+1] == null || pos[t-1] == null) a[t] = null;
			else {
				a[t] = new Vector2D(
						Math.cos(pos[t].getY()) * Math.IEEEremainder(pos[t+1].getX() + pos[t-1].getX() - 2.0*pos[t].getX(), Constant.twoPI),
						pos[t+1].getY() + pos[t-1].getY() - 2.0*pos[t].getY()
				);
				a[t].scale(idt);
			}
		}
	
		return a;
	}
	
	
	public Signal getAccelerationSignal(Mode mode, final Motion direction) {
		final Vector2D[] a = getAccelerations();
		final double[] data = new double[size()];	
		for(int t=data.length; --t >= 0; ) data[t] = a[t] == null ? Float.NaN : direction.getValue(a[t]);
		return new Signal(mode, this, data, false);
	}
	
	// Redo with Filter...
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
		level(signal, channel, 0, size());
	}
	
	
	// TODO dependents accounting
	// Median removal...
	protected void level(float[] signal, final Channel channel, final int from, final int to) {
		final int c = channel.index;
		double sum = 0.0, sumw = 0.0;
		for(int i=to; --i >= from; ) {
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
	
			
	public void checkForNaNs(final Iterable<? extends Channel> channels, final int from, int to) {
		comments += "?";
		
		to = Math.min(to, size());
		
		for(int t=to; --t >= from; ) {
			final Frame exposure = get(t);
			
			if(exposure != null) for(final Channel channel : channels) {

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
	public synchronized void downsample(final int n) {
		System.err.print("   Downsampling by " + n);
		
		final int windowSize = (int)Math.round(1.82 * WindowFunction.getEquivalenWidth("Hann")*n);
		final int centerOffset = windowSize/2 + 1;
		final double[] w = WindowFunction.get("Hann", windowSize);
		final ArrayList<FrameType> buffer = new ArrayList<FrameType>((size()-windowSize)/n+1); 
		final double[] value = new double[instrument.size()];
		
		// Normalize window function to absolute intergral 1
		double norm = 0.0;
		for(int i=w.length; --i >= 0; ) norm += Math.abs(w[i]);
		for(int i=w.length; --i >= 0; ) w[i] /= norm;
		
		final int nt = size();
		
		for(int to=windowSize; to <= nt; to += n) {
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
		ensureCapacity(buffer.size());
		addAll(buffer);
		trimToSize();
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

	public synchronized void offset(final double value) {
		for(final Frame exposure : this) if(exposure != null) {
			for(final Channel channel : instrument) if(channel.flag == 0) exposure.data[channel.index] += value;			
		}
	}


	public void writeASCIITimeStream() throws IOException {
		String filename = CRUSH.workPath + File.separator + scan.getID() + "-" + getID() + ".tms";
		final PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(filename), 1000000));
		out.println("# " + Util.e3.format(1.0/instrument.samplingInterval));
		final int nc = instrument.size();
		
		for(final Frame exposure : this) {
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
		
		final double[][] covar = new double[instrument.size()][instrument.size()];
		final int[][] n = new int[instrument.size()][instrument.size()];
		
		for(final Frame exposure : this) if(exposure != null) if(exposure.isUnflagged(Frame.SOURCE_FLAGS)) {
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
	
		final double[][] groupedCovar = new double[n][n];
		for(int k=n; --k >= 0; ) Arrays.fill(groupedCovar[k], Double.NaN);
	
		
		int k1 = 0;
		for(final ChannelGroup<?> g1 : division) for(Channel ch1 : g1) {
			int k2 = 0;
			for(final ChannelGroup<?> g2 : division) for(Channel ch2 : g2) groupedCovar[k1][k2++] = covar[ch1.index][ch2.index];
			k1++;
		}
	
		return groupedCovar;
	}

	public double[][] getFullCovariance(double[][] covar) {
		
		final double[][] fullCovar = new double[instrument.storeChannels][instrument.storeChannels];
		
		for(int i=fullCovar.length; --i >= 0; ) Arrays.fill(fullCovar[i], Double.NaN);
		
		for(final Channel c1 : instrument) for(final Channel c2 : instrument)
			fullCovar[c1.storeIndex-1][c2.storeIndex-1] = covar[c1.index][c2.index];
			
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
		final double[] w = WindowFunction.get(windowName, windowSize);

		// System.err.println("  Calculating Power spectra.");
		final float[] data = new float[size()];
		final float[][] spectra = new float[instrument.size()][];
		final double df = 1.0 / (instrument.samplingInterval * windowSize);	
		final float Jy = gain * (float) instrument.janskyPerBeam();
		
		final int nt = size();

		for(final Channel channel : instrument) {
			for(int t=nt; --t >= 0; ) {
				final Frame exposure = get(t);
				if(exposure == null) data[t] = 0.0F;
				else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = 0.0F;
				else if(exposure.sampleFlag[channel.index] != 0) data[t] = 0.0F;
				else data[t] = exposure.data[channel.index];
			}
			final float[] spectrum = FFT.averagePower(data, w);
			final float[] channelSpectrum = new float[spectrum.length];
			
			for(int i=spectrum.length; --i>=0; ) channelSpectrum[i] = (float) Math.sqrt(spectrum[i] / df) / Jy;
			
			spectra[channel.index] = channelSpectrum;
		}
  
		return spectra;
	}
	
	public void writeSpectra(String windowName, int windowSize) throws IOException {
		String fileName = CRUSH.workPath + File.separator + scan.getID() + "-" + getID() + ".spec";

		final float[][] spectrum = getSpectra(windowName, windowSize);
		final double df = 1.0 / (instrument.samplingInterval * windowSize);
		
		final PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileName), 1000000));

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
		
		final double[][] covar = getCovariance(); 
		List<String> specs = hasOption("write.covar") ? option("write.covar").getList() : new ArrayList<String>();
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
		WhiteningFilter whitener = null;
		
		if(filter != null) if(filter.contains(WhiteningFilter.class)) 
			whitener = (WhiteningFilter) filter.get(WhiteningFilter.class);
		
		int[] dataIndex = new int[instrument.size()];
		float[] channelGain = new float[instrument.size()];
		float[] channelOffset = new float[instrument.size()];
		float[] channelWeight = new float[instrument.size()];
		int[] channelFlags = new int[instrument.size()];
		short[] channelSpikes = new short[instrument.size()];

		float[][] filterProfile = whitener == null ? null : new float[instrument.size()][];
		
		for(Channel channel : instrument) {
			dataIndex[channel.index] = channel.storeIndex;
			channelGain[channel.index] = (float) channel.gain;
			channelOffset[channel.index] = (float) channel.offset;
			channelWeight[channel.index] = (float) channel.weight;
			channelFlags[channel.index] = channel.flag;
			if(whitener != null) filterProfile[channel.index] = whitener.getValidProfile(channel);
			channelSpikes[channel.index] = (short) channel.spikes;
		}
		
		data.put("Channel_Index", dataIndex);
		data.put("Channel_Gain", channelGain);
		data.put("Channel_Offset", channelOffset);
		data.put("Channel_Weight", channelWeight);
		data.put("Channel_Flags", channelFlags);
		data.put("Channel_Spikes", channelSpikes);
		if(whitener != null) data.put("Whitening_Profile", filterProfile);
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
		for(int k=iters; --k >= 0; ) for(int t=frame.length; --t >= 0; ) {
			final Frame exposure = frame[t];
			if(exposure != null) for(int c=channel.length; --c >= 0; ) {
				final Channel pixel = channel[c];
				i += exposure.sampleFlag[pixel.index];
			}
		}
		a = System.currentTimeMillis() - a;
		m += i; 
		
		// Then the ArrayList
		i = 0;
		long b = System.currentTimeMillis();
		for(int k=iters; --k >= 0; )  for(Frame exposure : this) if(exposure != null) for(Channel pixel : instrument) 
			i += exposure.sampleFlag[pixel.index];
		b = System.currentTimeMillis() - b;
		m += i;
		
		// Then with two operations
		i = 0;
		int j=0;
		long c = System.currentTimeMillis();
		for(int k=iters; --k >= 0; ) for(Frame exposure : this) if(exposure != null) for(Channel pixel : instrument) {
			i += exposure.sampleFlag[pixel.index];
			j += i;
		}
		c = System.currentTimeMillis() - c;
		m += i;
		m += j;
		
		
		// Then the ArrayList
		i = 0;
		final ArrayList<Frame> frames = new ArrayList<Frame>();
		final ArrayList<Channel> channels = new ArrayList<Channel>();
		frames.addAll(this);
		channels.addAll(instrument);
		
		long d = System.currentTimeMillis();
		for(int k=iters; --k >= 0; )  for(Frame exposure : frames) if(exposure != null) for(Channel pixel : channels) 
			i += exposure.sampleFlag[pixel.index];
		d = System.currentTimeMillis() - d;
		m += i;
		
		// Then the ArrayList in inverted order
		i = 0;
		long e = System.currentTimeMillis();
		for(int k=iters; --k >= 0; ) for(Channel pixel : instrument) for(Frame exposure : this) if(exposure != null) 
			i += exposure.sampleFlag[pixel.index];
		e = System.currentTimeMillis() - e;
		m += i;
		
		
		// array (inverted order)...
		i = 0;
		long f = System.currentTimeMillis();
		for(int k=iters; --k >= 0; ) for(int p=channel.length; --p >= 0; ) {
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
			"# Instrument: " + instrument.getName() + "\n" +
			"# Scan: " + scan.getID() + "\n" +
			(scan.size() > 1 ? "# Integration: " + (integrationNo + 1) + "\n" : "") +
			"# Object: " + scan.getSourceName() + "\n" +
			"# Date: " + scan.timeStamp + " (MJD: " + scan.getMJD() + ")\n" +
			"# Project: " + scan.project + "\n" +
			"# Exposure: " + (getFrameCount(Frame.SOURCE_FLAGS) * instrument.integrationTime) + " s.\n" +
			"# Equatorial: " + scan.equatorial + "\n" +
			(scan instanceof GroundBased ? "# Horizontal: " + scan.horizontal + "\n" : "") +
			"# CRUSH version: " + CRUSH.getFullVersion();
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
			if(!decorrelate(modalityName, isRobust)) return false;
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
		else if(task.equals("filter")) {
			if(!filter.apply()) return false;
			if(indexOf("filter") > indexOf("weighting")) getWeights();
			updatePhases();
		}
		else if(task.equals("purify")) {
			if(this instanceof Purifiable) ((Purifiable) this).purify();
			comments += "P";
		}
		else if(task.equals("dejump")) {
			dejumpFrames();
		}
		else return false;
			
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
		else if(name.equals("scanspeed")) return Util.defaultFormat(aveScanSpeed.value() / (Unit.arcsec / Unit.s), f);
		else if(name.equals("rmsspeed")) return Util.defaultFormat(aveScanSpeed.rms() / (Unit.arcsec / Unit.s), f);
		else if(name.equals("hipass")) return Util.defaultFormat(filterTimeScale / Unit.s, f);
		else if(name.equals("chopfreq")) {
			if(chopper == null) return "---";
			else return  Util.defaultFormat(chopper.frequency / Unit.Hz, f);
		}
		else if(name.equals("chopthrow")) {
			if(chopper == null) return "---";
			else return  Util.defaultFormat(2.0 * chopper.amplitude / instrument.getSizeUnit(), f);
		}
		else if(name.equals("chopeff")) {
			if(chopper == null) return "---";
			else return  Util.defaultFormat(chopper.efficiency, f);
		}
		else return instrument.getFormattedEntry(name, formatSpec);
	}
	
	@Override
	public String toString() { return "Integration " + getFullID("|"); }
	
	
	// TODO use forks by channelgroup(?) and by block(?)
	// TODO implement forked processing where appropriate
	public class Fork extends Parallel {
	
		public abstract class Frames<ReturnType> extends Process<ReturnType> {
			@Override
			public void processIndex(int index, int threadCount) {
				for(int t=index; t<size(); t += threadCount) {
					if(isInterrupted()) return;
					process(get(t));
					Thread.yield();
				}
			}
			
			public abstract void process(FrameType frame);
		}
		
		public abstract class Channels<ReturnType> extends Process<ReturnType> {
			private ChannelGroup<?> channels;
	
			public Channels(ChannelGroup<?> channels) { this.channels = channels; }
				
			@Override
			public void processIndex(int index, int threadCount) {
				for(int c=index; c<channels.size(); c+=threadCount) {
					if(isInterrupted()) return;
					process(channels.get(c));
					Thread.yield();
				}
			}
			
			public abstract void process(Channel channel);
		}
	}
	
	public Fork parallel;
	
}
