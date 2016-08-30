/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

import crush.filters.*;
import jnum.Configurator;
import jnum.Constant;
import jnum.ExtraMath;
import jnum.LockedException;
import jnum.Parallel;
import jnum.Unit;
import jnum.Util;
import jnum.data.*;
import jnum.fft.FloatFFT;
import jnum.io.fits.FitsToolkit;
import jnum.math.Complex;
import jnum.math.Range;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.reporting.BasicMessaging;
import jnum.text.TableFormatter;
import jnum.util.HashCode;
import nom.tam.fits.*;

/**
 * 
 * @author pumukli
 *
 * @param <InstrumentType>
 * @param <FrameType>
 * 
 */
public abstract class Integration<InstrumentType extends Instrument<?>, FrameType extends Frame> 
extends ArrayList<FrameType> 
implements Comparable<Integration<InstrumentType, FrameType>>, TableFormatter.Entries, BasicMessaging {
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
	private Hashtable<Mode, Signal> signals = new Hashtable<Mode, Signal>();	
	
	public boolean approximateSourceMap = false;
	public int sourceGeneration = 0;
	public double[] sourceSyncGain;
	
	public DataPoint aveScanSpeed;
	public MultiFilter filter;
	private FloatFFT sequentialFFT, parallelFFT;
	
	public double filterTimeScale = Double.POSITIVE_INFINITY;
	public double nefd = Double.NaN; // It is readily cast into the Jy sqrt(s) units!!!
	
	protected boolean isDetectorStage = false;
	protected boolean isValid = false;
	
	private int parallelism = 1;
	
	// The integration should carry a copy of the instrument s.t. the integration can freely modify it...
	// The constructor of Integration thus copies the Scan instrument for private use...
	@SuppressWarnings("unchecked")
	public Integration(Scan<InstrumentType, ?> parent) {
		scan = parent;
		instrument = (InstrumentType) scan.instrument.copy();
		instrument.setParent(this);
		setThreadCount(CRUSH.maxThreads);
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public Object clone() { 
		Integration<InstrumentType, FrameType> clone = (Integration<InstrumentType, FrameType>) super.clone();
		// TODO redo it safely, s.t. existing reduction steps copy over as well?
		clone.dependents = new Hashtable<String, Dependents>(); 
		clone.signals = new Hashtable<Mode, Signal>();
		clone.filter = null;
		if(this instanceof Chopping) ((Chopping) this).setChopper(null);
		
		return clone;
	}

	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof Integration)) return false;
		if(!super.equals(o)) return false;
		
		final Integration<?,?> other = (Integration<?,?>) o;
		if(other.integrationNo != integrationNo) return false;
		if(!other.scan.getID().equals(scan.getID())) return false;
		if(other.size() != size()) return false;
		return other.getDisplayID().equals(getDisplayID());
	}
	
	@Override
	public int hashCode() {
		return super.hashCode() ^ scan.getID().hashCode() ^ HashCode.from(integrationNo) ^ size() ^ getDisplayID().hashCode();
	}
	
	@Override
	public int compareTo(Integration<InstrumentType, FrameType> other) {
		if(integrationNo == other.integrationNo) return 0;
		else return integrationNo < other.integrationNo ? -1 : 1;
	}
	
	public void reindex() {
		for(int k=size(); --k >= 0; ) {
			final Frame exposure = get(k);
			if(exposure != null) exposure.index = k;
		}
	}
	
	public boolean hasOption(String key) {
		return instrument.hasOption(key);
	}
	
	public Configurator option(String key) {
		return instrument.option(key);
	}

	public void setThreadCount(int threads) { 
		parallelism = threads; 
		instrument.setThreadCount(threads);
	}
	
	public int getThreadCount() { return parallelism; }
	
	public void validate() {
		if(isValid) return;		
	
		scan.info("Processing integration " + getID() + ":");
		
		// Incorporate the relative instrument gain (under loading) in the scan gain...
		gain *= instrument.sourceGain;	
		
		if(hasOption("shift")) shiftData(option("shift").getDouble() * Unit.s);

		new CRUSH.Fork<Void>(size(), getThreadCount()) {
			@Override
			protected void processIndex(int index) {
				Frame frame = get(index);
				if(frame == null) return;
				
				if(!frame.validate()) set(index, null); 
				else frame.index = index;
			}
		}.process();
		
		if(hasOption("fillgaps")) if(hasGaps(1)) fillGaps();
	
		if(hasOption("notch")) notchFilter();
	
		if(hasOption("detect.chopped")) detectChopper();
		
		//if(hasOption("shift")) shiftData();
		if(hasOption("frames")) selectFrames();
		
		// Explicit downsampling should precede v-clipping
		if(hasOption("downsample")) if(!option("downsample").equals("auto")) downsample();
		
		if(!hasOption("lab")) {
		    if(hasOption("vclip")) velocityClip();
		    if(hasOption("aclip")) accelerationClip();

		    calcScanSpeedStats();
		}
		else if(hasOption("lab.scanspeed")) {
		    aveScanSpeed = new DataPoint(option("lab.scanspeed").getDouble() * Unit.arcsec / Unit.s, 0.0);
		}
		else {
		    aveScanSpeed = new DataPoint(10.0 * instrument.getResolution(), 0.0);
		}
		    
		if(hasOption("filter.kill")) {
			info("FFT Filtering specified sub-bands...");
			removeOffsets(false);
			KillFilter filter = new KillFilter(this);
			filter.updateConfig();
			filter.apply();
		}
		

		// Flag out-of-range data
		if(hasOption("range")) checkRange();
		// Continue only if enough valid channels remain...
		int minChannels = hasOption("mappingpixels") ? option("mappingpixels").getInt() : 2;
		if(instrument.mappingChannels < minChannels)
			throw new IllegalStateException("Too few valid channels (" + instrument.mappingChannels + ").");
		
		// Automatic downsampling after vclipping...
		if(hasOption("downsample")) if(option("downsample").equals("auto")) downsample();
		
		trim();
		
		// Continue only if integration is long enough to be processed...
		int minFrames = hasOption("subscan.minlength") ? (int) Math.floor(option("subscan.minlength").getDouble() / instrument.samplingInterval) : 2;
		int mappingFrames = getFrameCount(Frame.SOURCE_FLAGS);
		if(getFrameCount(Frame.SOURCE_FLAGS) < minFrames) 
			throw new IllegalStateException("Integration is too short (" + Util.f1.format(mappingFrames * instrument.samplingInterval / Unit.s) + " seconds).");
		
		// Filter motion only after downsampling...
		if(hasOption("filter.ordering")) setupFilters();
		
		detectorStage();
		
		// Remove the DC offsets, either if explicitly requested
		// or to allow bootstrapping pixel weights when pixeldata is not defined.
		// Must do this before direct tau estimates...
		if(hasOption("level") || !hasOption("pixeldata")) {
			boolean isRobust = false;
			if(hasOption("estimator")) if(option("estimator").equals("median")) isRobust=true;
			
			info("Removing DC offsets" + (isRobust ? " (robust)" : "") + ".");
			removeOffsets(isRobust);
		}
			
		if(hasOption("tau")) {
			try { setTau(); }
			catch(Exception e) { 
				warning("Problem setting tau: " + e.getMessage()); 
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}
	
		if(hasOption("scale")) {
			try { setScaling(); }
			catch(Exception e) {
				warning("Problem setting calibration scaling: " + e.getMessage()); 
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}
		if(hasOption("invert")) gain *= -1.0;
		
		if(!hasOption("noslim")) slim(getThreadCount());
		
		if(hasOption("jackknife")) if(Math.random() < 0.5) {
			notify("JACKKNIFE: This integration will produce an inverted source.");
			gain *= -1.0;
		}
		
		if(hasOption("jackknife.frames")) {
			notify("JACKKNIFE: Randomly inverted frames in source.");
			for(Frame exposure : this) exposure.jackknife();
		}
		
		if(!hasOption("pixeldata")) if(hasOption("weighting")) if(!hasOption("uniformweights")) {
			getDifferentialPixelWeights();
			instrument.census();
			info("Bootstrapping pixel weights (" + instrument.mappingChannels + " active channels).");
		}
		
		System.gc();
		
		isValid = true;
		
		if(hasOption("speedtest")) speedTest();
	}
	
	public void setIteration(int i, int rounds) {
		CRUSH.setIteration(instrument.getOptions(), i, rounds);
	}
 	
	public double getExposureTime() { return getFrameCount(Frame.SKIP_SOURCE_MODELING) * instrument.samplingInterval; }
	
	public double getDuration() { return size() * instrument.samplingInterval; }
	
	public void invert() {
		new Fork<Void>() {
			@Override
			protected void process(FrameType frame) { frame.invert(); }
		}.process();
	}
	
	public int getFrameCount(final int excludeFlags) {
		int n=0;
		for(Frame frame : this) if(frame != null) if(frame.isUnflagged(excludeFlags)) n++;
		return n;
	}
	
	
	public int getFrameCount(final int excludeFlags, final Channel channel, final int excludeSamples) {
		int n=0;
		for(Frame frame : this) if(frame != null) if(frame.isUnflagged(excludeFlags)) if((frame.sampleFlag[channel.index] & excludeSamples) == 0) n++;
		return n;
	}
	
	public void selectFrames() {
		Range range = option("frames").getRange(true);
		final int from = (int)range.min();
		final int to = Math.min(size(), (int)range.max());
		
		for(int t=size(); --t >= to; ) remove(t);
		
		if(from == 0) return;
		
		final ArrayList<FrameType> selected = new ArrayList<FrameType>(to - from);
		for(int t=from; t<to; t++) selected.add(get(t));
		
		clear();
		addAll(selected);
		
		reindex();
	}
	
	public void checkRange() {
		if(!hasOption("range")) return;
		final Range range = option("range").getRange();
		
		int[] n = getInts();
		
		for(Frame frame : this) if(frame != null) {
			for(final Channel channel : instrument) if(!range.contains(frame.data[channel.index])) {
				frame.sampleFlag[channel.index] |= Frame.SAMPLE_SKIP;
				n[channel.index]++;
			}
		}
		
		if(!hasOption("range.flagfraction")) {
			recycle(n);
			return;
		}
		
		int flagged = 0;
		final double f = 1.0 / getFrameCount(0);
		final double critical = option("range.flagfraction").getDouble();
		
		
		for(final Channel channel : instrument) {
			if(f * n[channel.index] > critical) {
				channel.flag(Channel.FLAG_DAC_RANGE | Channel.FLAG_DEAD);
				flagged++;
			}
		}

		recycle(n);
		
		
		info("Flagging out-of-range data." + flagged + " channel(s) discarded.");

		instrument.census();
	}
	
	public void downsample() {
		// Keep to the rule of thumb of at least 2.5 samples per beam
		if(option("downsample").equals("auto")) {
			// Choose downsampling to accomodate at least 90% of scanning speeds... 
			double maxv = aveScanSpeed.value() + 1.25 * aveScanSpeed.rms();
			// Choose downsampling to accomodate at ~98% of scanning speeds... 
			//double maxv = aveScanSpeed.value + 2.0 * aveScanSpeed.rms();
			if(maxv == 0.0) { 
				warning("No automatic downsampling for zero scan speed.");
				return; 
			}
			double maxInt = 0.4 * instrument.getPointSize() / maxv;
			
			int factor = (int)Math.floor(maxInt / instrument.samplingInterval);
			if(factor == Integer.MAX_VALUE) {
				warning("No automatic downsampling for negligible scan speed.");
				return;
			}
			
			if(hasOption("downsample.autofactor")) factor = (int)Math.floor(factor * option("downsample.autofactor").getDouble());
			
			
			if(factor > 1) downsample(factor);
			else return;
		}
		else {
			int factor = option("downsample").getInt();
			downsample(factor);
		}
		trim();		
	}
	
	public void setScaling() throws Exception {
		setScaling(option("scale").getDouble());
	}

	public void setScaling(double value) {
		gain /= value;
		info("Applying scaling factor " + Util.f3.format(value));		
	}

	// Try in this order:
	//   1. in-band value, e.g. "0.304"
	//   2. scaling relation, e.g. "225GHz", provided "tau.225GHz" is defined.
	//	 
	public void setTau() throws Exception {	
		String spec = option("tau").getValue();
		
		try { setTau(Double.parseDouble(spec)); }
		catch(Exception notanumber) {
			String id = spec.toLowerCase();
			if(hasOption("tau." + id)) setTau(id, option("tau." + id).getDouble());
			else throw new IllegalArgumentException("Supplied tau is neither a number nor a known subtype.");
		}
	}
	
	public void setTau(String id, double value) {
		Vector2D t = getTauCoefficients(id);
		Vector2D inband = getTauCoefficients(instrument.getName());
		try { setZenithTau(inband.x() / t.x() * (value - t.y()) + inband.y()); }
		catch(Exception e) { 
		    warning("Could not set zenith tau: " + e.getMessage()); 
		    if(CRUSH.debug) CRUSH.trace(e);
		}
	}
	
	public double getTau(String id, double value) {
		Vector2D t = getTauCoefficients(id);
		Vector2D inband = getTauCoefficients(instrument.getName());
		return t.x() / inband.x() * (value - inband.y()) + t.y();
	}
	
	public double getTau(String id) {
		return getTau(id, zenithTau);
	}
	
	public Vector2D getTauCoefficients(String id) {
		String key = "tau." + id.toLowerCase();
		
		if(!hasOption(key + ".a")) throw new IllegalStateException(key + " has no scaling relation.");
		
		Vector2D coeff = new Vector2D();
		coeff.setX(option(key + ".a").getDouble());
		if(hasOption(key + ".b")) coeff.setY(option(key + ".b").getDouble());
	
		return coeff;
	}
	
	public void setTau(final double value) throws Exception {	
		if(this instanceof GroundBased) {
			try { setZenithTau(value); }
			catch(NumberFormatException e) {}
		}
		else {
			final double transmission = Math.exp(-value);
			for(Frame frame : this) if(frame != null) frame.setTransmission(transmission);
		}
	}
	
	public void setZenithTau(final double value) {
		if(!(this instanceof GroundBased)) throw new UnsupportedOperationException("Only implementation of GroundBased can set a zenith tau.");
		info("Setting zenith tau to " + Util.f3.format(value));
		zenithTau = value;
		
		for(Frame frame : this) if(frame != null) ((HorizontalFrame) frame).setZenithTau(value);
	}

	public void calcScanSpeedStats() {
		aveScanSpeed = getMedianScanningVelocity(0.5 * Unit.s);
		info("Typical scanning speeds are " 
				+ Util.f1.format(aveScanSpeed.value()/(instrument.getSizeUnitValue()/Unit.s)) 
				+ " +- " + Util.f1.format(aveScanSpeed.rms()/(instrument.getSizeUnitValue()/Unit.s)) 
				+ " " + instrument.getSizeName() + "/s");
	}
	
	public void velocityClip() {
		Range vRange = null;
		Configurator option = option("vclip");
		
		if(option.equals("auto")) {	
			// Move at least 5 fwhms over the stability timescale
			// But less that 1/2.5 beams per sample to avoid smearing
			vRange = new Range(5.0 * instrument.getSourceSize() / instrument.getStability(), 0.4 * instrument.getPointSize() / instrument.samplingInterval);
		}
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
	

	public void pointingAt(final Vector2D offset) {
		for(Frame frame : this) if(frame != null) frame.pointingAt(offset);
	}
	
	
	public void setupFilters() {
		info("Configuring filters.");
		List<String> ordering = option("filter.ordering").getList();
		filter = new MultiFilter(this);
		for(final String name : ordering) {	
			Filter f = getFilter(name);
			if(f == null) warning("No filter for '" + name + "'.");
			else filter.addFilter(f);
		}	
	}
	
	public Filter getFilter(String name) {
		name = name.toLowerCase();
		if(name.equals("motion")) return new MotionFilter(this);
		else if(name.equals("kill")) return new KillFilter(this);
		else if(name.equals("whiten")) return new WhiteningFilter(this);
		else return null;
	}
	
	public FloatFFT getSequentialFFT() {
		if(sequentialFFT == null) {
			sequentialFFT = new FloatFFT();
			sequentialFFT.setSequential();
		}
		return sequentialFFT;
	}
	
	public FloatFFT getParallelFFT() {
		if(parallelFFT == null) {
			parallelFFT = new FloatFFT();
			if(CRUSH.executor instanceof ThreadPoolExecutor) parallelFFT.setPool((ThreadPoolExecutor) CRUSH.executor);
			else parallelFFT.setThreads(parallelism);
		}
			
		return parallelFFT;
	}
	
	public abstract FrameType getFrameInstance();
	
	public double getCrossingTime() {
		return getCrossingTime(scan.sourceModel == null ? instrument.getSourceSize() : scan.sourceModel.getSourceSize());
	}
	
	public double getCrossingTime(double sourceSize) {		
		if(this instanceof Chopping) {
			Chopper chopper = ((Chopping) this).getChopper();
			if(chopper != null) return Math.min(chopper.stareDuration(), sourceSize / aveScanSpeed.value());
		}
		return sourceSize / aveScanSpeed.value();		
	}


	public double getPointCrossingTime() {
		return getCrossingTime(scan.sourceModel == null ? instrument.getPointSize() : scan.sourceModel.getPointSize()); 
	}
			
	public double getMJD() {
		return 0.5 * (getFirstFrame().MJD + getLastFrame().MJD);	
	}

	// Always returns a value between 1 and driftN...
	public int framesFor(double time) {
		return Math.max(1, Math.min(size(), (int)Math.round(Math.min(time, filterTimeScale) / instrument.samplingInterval)));	
	}	
	
	public int power2FramesFor(double time) {
		return ExtraMath.pow2ceil(Math.max(1, Math.min(size(), (int)Math.round(Math.min(time, filterTimeScale) / instrument.samplingInterval / Math.sqrt(2.0)))));	
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
		
		return ExtraMath.pow2ceil(frames);
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


	public boolean hasGaps(final int tolerance) {
		String text = "Checking for gaps: ";
		
		final Frame first = getFirstFrame();
		
		
		for(int t=size(); --t > 0; ) {
			final Frame frame = get(t);
			
			if(frame == null) continue;
			
			double gap = (frame.MJD - first.MJD) * Unit.day - (frame.index - first.index) * instrument.samplingInterval;
			
			if((int) Math.round(gap / instrument.samplingInterval) > tolerance) { 
				info(text + "Gap(s) found! :-(  [e.g.: " + Util.f1.format(gap / Unit.ms) + " ms]");
				return true; 
			}
		}
		
		info(text + "No gaps. :-)");
		return false;
	}
	
	public void fillGaps() {
		final Frame first = getFirstFrame();
		
		final int nt = size();
		final int n = (int) Math.ceil((getLastFrame().MJD - first.MJD) / instrument.samplingInterval);
		int padded = 0;
		
		final ArrayList<FrameType> buffer = new ArrayList<FrameType>(n);
		
		
		for(int t=0; t < nt; t++) {
			final FrameType frame = get(t);
			
			if(frame == null) continue;
			
			double gap = (frame.MJD - first.MJD) * Unit.day - (buffer.size() - first.index) * instrument.samplingInterval;
			int frameGaps = (int) Math.round(gap / instrument.samplingInterval);
			
			if(frameGaps > 10) warning("Large gap of " + frameGaps + " frames at index " + t + ", MJD: " + frame.MJD);
			
			for(int i=frameGaps; --i >= 0; padded++) buffer.add(null);
		
			buffer.add(frame);	
		}	
		
		if(padded != 0) {
			clear();
			addAll(buffer);
			info("Padded with " + padded + " empty frames.");
		}
		
		reindex();
	}
	
	
	public void trimEnd() {
		// Remove null frames from the end;
		for(int t=size(); --t >= 0; ) {
			if(get(t) == null) remove(t);
			else return;
		}		
	}
	
	public void trim() {
		trimEnd();
		
		final int nt = size();
		int from = 0;
		for( ; from < nt; from++) if(get(from) != null) break;
		
		if(from == 0) return;
		
		final ArrayList<FrameType> timmed = new ArrayList<FrameType>(nt);
		for( ; from<nt; from++) timmed.add(get(from));
			
		clear();
		addAll(timmed);
			
		reindex();
		
		info("Trimmed to " + size() + " frames.");
	}

	
	
	public void slim(int threads) {
		if(instrument.slim(false)) {
			new Fork<Void>() {
				@Override
				protected void process(FrameType frame) { if(frame != null) frame.slimTo(instrument); }
			}.process();

			instrument.reindex();
		}
	}
	
	public void scale(final double factor) {
		if(factor == 1.0) return;
		for(Frame frame : this) if(frame != null) frame.scale(factor);
	}
		

	public double getPA() {
		HorizontalFrame first = (HorizontalFrame) getFirstFrame();
		HorizontalFrame last = (HorizontalFrame) getLastFrame();
		return 0.5 * (Math.atan2(first.sinPA, first.cosPA) + Math.atan2(last.sinPA, last.cosPA));
	}
	
	
	public void localLevel(final int from, final int to, final Dependents parms, final boolean robust) {
		// Clear dependencies of any prior local levelling. Will use new dependencies
		// on the currently obtained level for the interval.
		parms.clear(instrument, from, to);
		
		instrument.new Fork<float[]>() {
			private WeightedPoint increment;
			private DataPoint[] buffer;
			private float[] frameParms;
			
			@Override
			protected void init() {
				super.init();
				increment = new WeightedPoint();
				
				frameParms = getFloats();
				Arrays.fill(frameParms, 0, size(), 0.0F);
				
				if(robust) buffer = getDataPoints();
			}
			
			@Override
			protected void cleanup() { 
				super.cleanup();
				if(buffer != null) recycle(buffer); 
			}
			
			@Override
			public float[] getLocalResult() { return frameParms; }
			
			@Override
			public void postProcess() {
				super.postProcess();
				for(Parallel<float[]> task : getWorkers()) {
					float[] localFrameParms = task.getLocalResult();
					parms.addForFrames(localFrameParms);
					recycle(localFrameParms);
				}
			}
			
			@Override
			protected void process(Channel channel) {
				if(robust) getMedianLevel(channel, from, to, buffer, increment);
				else getMeanLevel(channel, from, to, increment);
				level(channel, from, to, frameParms, increment);
				parms.addAsync(channel, 1.0);
			}
		}.process();
		

		// Apply the local-level dependencies
		parms.apply(instrument, from, to);			
		
		
		// Remove the drifts from all signals also to match bandpass..
		final ArrayList<Signal> sigs = new ArrayList<Signal>(signals.values());
		new CRUSH.Fork<Void>(sigs.size(), getThreadCount()) {
			@Override
			protected void processIndex(int k) { sigs.get(k).level(from, to); }
		}.process();
	}
	
	
	
	public void removeOffsets(final boolean robust) {
		removeDrifts(size(), robust);
	}
	

	public void removeDrifts(final int targetFrameResolution, final boolean robust) {
		final int driftN = Math.min(size(), ExtraMath.pow2ceil(targetFrameResolution));
		filterTimeScale = Math.min(filterTimeScale, driftN * instrument.samplingInterval);
			
		final Dependents parms = getDependents("drifts");
		
		if(driftN < size()) comments += (robust ? "[D]" : "D") + "(" + driftN + ")";
		else comments += robust ? "[O]" : "O";
		
		// Remove the 1/f drifts from all channels
		removeChannelDrifts(instrument, parms, driftN, robust);	

		// Remove the drifts from all signals also to match bandpass..
		final ArrayList<Signal> sigs = new ArrayList<Signal>(signals.values());
		
		if(!sigs.isEmpty()) new CRUSH.Fork<Void>(sigs.size(), getThreadCount()) {
			@Override
			protected void processIndex(int k) { sigs.get(k).removeDrifts(driftN, true); }
		}.process();
	}

	public void removeChannelDrifts(final ChannelGroup<? extends Channel> channels, final int targetFrameResolution, final boolean robust) {
		final int driftN = Math.min(size(), ExtraMath.pow2ceil(targetFrameResolution));
		filterTimeScale = Math.min(filterTimeScale, driftN * instrument.samplingInterval);	
		removeChannelDrifts(channels, getDependents("drifts"), driftN, robust);	
	}
	
	
	public void removeChannelDrifts(final ChannelGroup<? extends Channel> channels, final Dependents parms, final int driftN, final boolean robust) {
		parms.clear(channels, 0, size());
		
		final DataPoint[] aveOffset = instrument.getDataPoints();
		for(int i=channels.size(); --i >= 0; ) {
		    aveOffset[i].noData();
		    instrument.get(i).inconsistencies = 0;
		}
		
		final int nt = size();
		
		new CRUSH.Fork<float[]>(channels.size(), getThreadCount()) {
			private WeightedPoint increment = new WeightedPoint();
			private float[] frameParms;
			private DataPoint[] buffer;
			
			@Override
			protected void init() {
				super.init();
				increment = new WeightedPoint();
				
				frameParms = getFloats();
				Arrays.fill(frameParms, 0, size(), 0.0F);
				
				if(robust) buffer = getDataPoints();
			}
			
			@Override
			protected void cleanup() {
				super.cleanup();
				
				parms.addForFrames(frameParms);
				recycle(frameParms);
				
				if(buffer != null) recycle(buffer);
			}
			
			@Override
			public float[] getLocalResult() { return frameParms; }
			
			@Override
			protected void processIndex(int k) {
				final Channel channel = channels.get(k);
				
				for(int from=0; from < nt; from += driftN) {
					final int to = Math.min(from + driftN, size());
						
					if(robust) getMedianLevel(channel, from, to, buffer, increment);
					else getMeanLevel(channel, from, to, increment);
				
					aveOffset[k].average(increment);
					
					if(!level(channel, from, to, frameParms, increment)) channel.inconsistencies++;
					
					if(increment.weight() > 0.0) parms.addAsync(channel, 1.0);
				}
			}
		}.process();
				
		final double crossingTime = getPointCrossingTime();	
		
		int inconsistentChannels = 0;
		int inconsistencies = 0;
		
		for(int k=channels.size(); --k >= 0; ) {
			final Channel channel = channels.get(k);
			final double G = isDetectorStage ? channel.getHardwareGain() : 1.0;
			channel.offset += G * aveOffset[k].value();
			
			if(channel.inconsistencies > 0) inconsistentChannels++;
			inconsistencies += channel.inconsistencies;
			
			if(driftN >= size()) break;
			
			if(!Double.isNaN(crossingTime) && !Double.isInfinite(crossingTime)) {
				// Undo prior drift corrections....
				if(!Double.isInfinite(channel.filterTimeScale)) {
					if(channel.filterTimeScale > 0.0) channel.sourceFiltering /= 1.0 - crossingTime / channel.filterTimeScale;
					else channel.sourceFiltering = 0.0;
				}
				// Apply the new drift correction
				channel.sourceFiltering *= 1.0 - crossingTime / channel.filterTimeScale;
			}
			else channel.sourceFiltering = 0.0;

			channel.filterTimeScale = Math.min(filterTimeScale, channel.filterTimeScale);
		}
		
		if(inconsistencies > 0) comments += "!" + inconsistentChannels + ":" + inconsistencies;
				
		Instrument.recycle(aveOffset);
	
		if(CRUSH.debug) checkForNaNs(channels, 0, size());	
	}
	

	private boolean level(final Channel channel, final int from, int to, final float[] frameParms, final WeightedPoint increment) {
		final float delta = (float) increment.value();			
		final float pNorm = (float) (channel.getFiltering(this) / increment.weight());
		
		// Remove offsets from data and account frame dependence...	
		while(--to >= from) {
			final Frame exposure = get(to);
			if(exposure == null) continue;
			
			exposure.data[channel.index] -= delta;
			
			if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[channel.index] == 0)
				frameParms[exposure.index] += exposure.relativeWeight * pNorm;
		}
		
		return checkConsistency(channel, from, to);
	}

	
	public boolean checkConsistency(final Channel channel, int from, int to) {
	    return true;
	}
	
	private void getMeanLevel(final Channel channel, final int from, int to, final WeightedPoint increment) {
		to = Math.min(to, size());
		
		increment.noData();
		
		// Calculate the weight sums for every pixel...
		while(--to >= from) {
			final Frame exposure = get(to);
			if(exposure == null) continue; 
		
			if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[channel.index] == 0) {
				increment.add(exposure.relativeWeight * exposure.data[channel.index]);
				increment.addWeight(exposure.relativeWeight);
			}
		}
		if(increment.weight() > 0.0) increment.setValue(increment.value() / increment.weight());
	}

	
	private void getMedianLevel(final Channel channel, final int from, int to, final WeightedPoint[] buffer, final WeightedPoint increment) {
		to = Math.min(to, size());
						
		int n = 0;
		double sumw = 0.0;
		
		final int c = channel.index;
		
		while(--to >= from) {
			final Frame exposure = get(to);
			if(exposure == null) continue; 
		
			if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] == 0) {
				final WeightedPoint point = buffer[n++];
				point.setValue(exposure.data[c]);
				point.setWeight(exposure.relativeWeight);
				sumw += exposure.relativeWeight;
			}
		}
		
		if(sumw > 0.0) Statistics.smartMedian(buffer, 0, n, 0.25, increment);	
	}
	

	public boolean decorrelate(final String modalityName, final boolean isRobust) {
		
		final Modality<?> modality = instrument.modalities.get(modalityName);
		if(modality == null) return false;
		
		modality.solveGains = hasOption("gains");
		modality.phaseGains = hasOption("phasegains");
		modality.setOptions(option("correlated." + modality.name));
		
		if(modality.trigger != null) if(!hasOption(modality.trigger)) return false;
		
		final String left = isRobust ? "[" : "";
		final String right = isRobust ? "]" : "";
		
		comments += left + modality.id + right;
		final int frameResolution = power2FramesFor(modality.resolution);
		if(frameResolution > 1) comments += "(" + frameResolution + ")";	
			
		if(modality instanceof CorrelatedModality) {
			CorrelatedModality correlated = (CorrelatedModality) modality;
			if(correlated.solveSignal) correlated.updateSignals(this, isRobust);
		}	
		
		// Continue to solve gains only if gains are derived per integration
		// If the gains span over scans, then return to allow them to be
		// derived globally...
		if(hasOption("correlated." + modality.name + ".span")) return false;
		
		boolean isGainRobust = false;		
		if(modality.solveGains) {
			Configurator gains = option("gains");
			
			try { gains.mapValueTo("estimator"); }
			catch(LockedException e) {} // TODO...
			
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
	
	
	public void getRMSPixelWeights() {
		comments += "W";
		
		final ChannelGroup<?> channels = instrument.getConnectedChannels();
		
		Fork<DataPoint[]> variances = new Fork<DataPoint[]>() {
			private DataPoint[] var;
			
			@Override
			protected void init() {
				super.init();
				
				var = instrument.getDataPoints();
				for(int i=instrument.size(); --i >= 0; ) var[i].noData();
			}
			
			@Override
			protected void process(FrameType exposure) {
				if(exposure.isFlagged(Frame.CHANNEL_WEIGHTING_FLAGS)) return;
				
				for(Channel channel : channels) if(exposure.sampleFlag[channel.index] == 0) {
					final float value = exposure.data[channel.index];
					final DataPoint point = var[channel.index];
					
					point.add(exposure.relativeWeight * value * value);
					point.addWeight(exposure.relativeWeight);
				}
			}
			
			@Override
			public DataPoint[] getLocalResult() { return var; }
			
			@Override
			public DataPoint[] getResult() {
				
				for(Parallel<DataPoint[]> task : getWorkers()) {
					final DataPoint[] localVar = task.getLocalResult();
					
					if(var == null) var = localVar;
					else {
						for(int i=instrument.size(); --i >= 0; ) {
							final DataPoint global = var[i];
							final DataPoint local = localVar[i];

							global.add(local.value());
							global.addWeight(local.weight());
						}
						Instrument.recycle(localVar);
					}
				}
				return var;
			}	
		};
		
		variances.process();
		setWeightsFromVarStats(channels, variances.getResult());
	}
	
	private void setWeightsFromVarStats(ChannelGroup<?> channels, final DataPoint[] var) {
	    if(var == null) return;
	    
		for(Channel channel : channels) {
			final DataPoint x = var[channel.index];
			if(x.weight() <= 0.0) return;
			channel.dof = Math.max(0.0, 1.0 - channel.dependents / x.weight());
			channel.variance = x.value() / x.weight();
			channel.weight = channel.variance > 0.0 ? channel.dof / channel.variance : 0.0;
		}
		Instrument.recycle(var);
	}
	
	public void getDifferentialPixelWeights() {
		final int delta = framesFor(10.0 * getPointCrossingTime());
		final ChannelGroup<?> channels = instrument.getConnectedChannels();
		
		comments += "w";
			
		CRUSH.Fork<DataPoint[]> variances = new CRUSH.Fork<DataPoint[]>(size() - delta, getThreadCount()) {
			private DataPoint[] var;
			
			@Override
			protected void init() {
				super.init();
				var = instrument.getDataPoints();
				for(int i=instrument.size(); --i >= 0; ) var[i].noData();
			}
			
			@Override
			protected void processIndex(int t) {
				final Frame exposure = get(t);
				
				if(exposure == null) return;
				if(exposure.isFlagged(Frame.CHANNEL_WEIGHTING_FLAGS)) return;
				
				final Frame prior = get(t+delta);
				if(prior == null) return;
				if(prior.isFlagged(Frame.CHANNEL_WEIGHTING_FLAGS)) return;
				
				for(Channel channel : channels) if((exposure.sampleFlag[channel.index] | prior.sampleFlag[channel.index]) == 0) {
					final float diff = exposure.data[channel.index] - prior.data[channel.index];
					final DataPoint point = var[channel.index];
					
					point.add(exposure.relativeWeight * diff * diff);
					point.addWeight(exposure.relativeWeight);
				}
			}
			
			@Override
			public DataPoint[] getLocalResult() { return var; }
			
			@Override
			public DataPoint[] getResult() {
				init();
				
				for(Parallel<DataPoint[]> task : getWorkers()) {
					final DataPoint[] localVar = task.getLocalResult();
					for(int i=instrument.size(); --i >= 0; ) {
						final DataPoint global = var[i];
						final DataPoint local = localVar[i];
						
						global.add(local.value());
						global.addWeight(local.weight());
					}
					Instrument.recycle(localVar);
				}
				return var;
			}	
		};
		
		variances.process();
		setWeightsFromVarStats(channels, variances.getResult());
		
	}

	public void getRobustPixelWeights() {
		comments += "[W]";
	
		instrument.getConnectedChannels().new Fork<Void>() {
			private float[] dev2;
			
			@Override
			protected void init() {
				super.init();
				dev2 = getFloats();
			}
			
			@Override
			protected void cleanup() {
				super.cleanup();
				recycle(dev2); 	
			}
			
			@Override
			protected void process(Channel channel) {
				int points = 0;
				
				for(final Frame exposure : Integration.this) if(exposure != null) if(exposure.isUnflagged(Frame.CHANNEL_WEIGHTING_FLAGS)) {
					if(exposure.sampleFlag[channel.index] == 0) {
						final float value = exposure.data[channel.index];
						dev2[points++] = exposure.relativeWeight * value * value;
					}		    
				}	
				
				if(points > 0) {
					channel.dof = Math.max(0.0, 1.0 - channel.dependents / points);
					channel.variance = points > 0 ? (Statistics.median(dev2, 0, points) / Statistics.medianNormalizedVariance) : 0.0;
					channel.weight = channel.variance > 0.0 ? channel.dof / channel.variance : 0.0;	
				}
			}
			
		}.process();
		
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
		if(hasOption("nefd.map")) nefd /= Math.sqrt(scan.weight);
		comments += "(" + Util.e2.format(nefd) + ")";	
	}

	public void getTimeWeights() {
		int n = hasOption("weighting.frames.resolution") ? filterFramesFor(option("weighting.frames.resolution").getValue(), 10.0 * Unit.s) : 1;
		getTimeWeights(ExtraMath.pow2ceil(n));
	}
	
	public void getTimeWeights(final int blockSize) { 
		comments += "tW";
		if(blockSize > 1) comments += "(" + blockSize + ")";
		getTimeWeights(blockSize, true); 
	}
	
	protected void getTimeWeight(final int from, final int to, final WeightedPoint stats) {
		int points = 0;
		double deps = 0.0;
		double sumChi2 = 0.0;
			
		for(int t=to; --t >= from; ) {
			final Frame exposure = get(t);
			if(exposure == null) continue;
			
			exposure.unflag(Frame.FLAG_WEIGHT);

			for(final Channel channel : instrument) if(channel.isUnflagged(Frame.TIME_WEIGHTING_FLAGS)) if(exposure.sampleFlag[channel.index] == 0) {			
				final float value = exposure.data[channel.index];
				sumChi2 += (channel.weight * value * value);
				points++;
			}
			deps += exposure.dependents;
		}		
		
		if(points - deps >= 1.0) {
			final float fw = sumChi2 > 0.0 ? (float) ((points-deps) / sumChi2) : 1.0F;	
			final double dof = 1.0 - deps / points;
			
			for(int t=to; --t >= from; ) {
				final Frame exposure = get(t);
				if(exposure == null) continue;
				
				exposure.unflag(Frame.FLAG_DOF);
				exposure.dof = dof;
				exposure.relativeWeight = fw;
				
				if(stats != null) {
					stats.addWeight(fw);
					stats.add(1.0);
				}
			}
		}
		else for(int t=to; --t >= from; ) {
			final Frame exposure = get(t);
			if(exposure == null) continue;
	
			exposure.flag(Frame.FLAG_DOF);
			exposure.dof = 0.0F;
			exposure.relativeWeight = Float.NaN; //	These will be set to 1.0 when renormalizing below...			
		}	
	}
	
	protected void getTimeWeights(final int blockSize, final boolean flag) {
		
		final BlockFork<WeightedPoint> weighting = new BlockFork<WeightedPoint>(blockSize) {
			private WeightedPoint stats;
			
			@Override
			protected void init() {
				super.init();
				stats = new WeightedPoint();
			}
			
			@Override
			protected void process(int from, int to) {
				getTimeWeight(from, to, stats);
			}
			
			@Override
			public WeightedPoint getLocalResult() { return stats; }
			
			@Override
			public WeightedPoint getResult() {
				WeightedPoint global = new WeightedPoint();
				for(Parallel<WeightedPoint> task : getWorkers()) {
					WeightedPoint local = task.getLocalResult();
					global.add(local.value());
					global.addWeight(local.weight());
				}
				return global;
			}
			
		};
		
		weighting.process();
		WeightedPoint stats = weighting.getResult();
		
		// Renormalize the time weights s.t. the pixel weights remain representative...
		final float inorm = stats.weight() > 0.0 ? (float) (stats.value() / stats.weight()) : 1.0F; 
			
		for(final Frame exposure : this) if(exposure != null) {
			if(Float.isNaN(exposure.relativeWeight)) exposure.relativeWeight = 0.0F;
			else exposure.relativeWeight *= inorm;
		}
		
		if(!flag) return;
		if(!hasOption("weighting.frames.noiserange")) return;
		
		final Range weightRange = option("weighting.frames.noiserange").getRange(true);

		for(final Frame exposure : this) if(exposure != null) if(!weightRange.contains(exposure.relativeWeight)) 
			exposure.flag(Frame.FLAG_WEIGHT);	
	}
	
	
	public void dejumpFrames() {
		final int resolution = ExtraMath.pow2round(hasOption("dejump.resolution") ? framesFor(option("dejump.resolution").getDouble() * Unit.sec) : 1);
		double level = hasOption("dejump.level") ? option("dejump.level").getDouble() : 2.0;
		
		// Convert level from rms to weight....
		level = 1.0 / (level * level);
		
		// Make sure that the level is significant at the 3-sigma level...
		// TODO this is assuming Gaussian distribution, whereas it's the distribution of weights that matters
		//      but it should be roughly correct, and if anything conservative...
		level = Math.min(1.0 - 9.0 / (resolution * instrument.mappingChannels), level);
		
		if(level <= 0.0) return;
		
		boolean robust = false;
		if(hasOption("estimator")) if(option("estimator").equals("median")) robust=true;
		comments += robust ? "[J]" : "J";
		
		final double minLevelTime = hasOption("dejump.minlength") ? option("dejump.minlength").getDouble() * Unit.sec : 5.0 * getPointCrossingTime();
		
		int minFrames = (int) Math.round(minLevelTime / instrument.samplingInterval);
		if(minFrames < 2) minFrames = Integer.MAX_VALUE;
		
		// Save the old time weights
		for(Frame exposure : this) if(exposure != null) exposure.tempC = exposure.relativeWeight;
			
		final Dependents parms = getDependents("jumps");		
		
		// Derive new time weights temporarily...
		getTimeWeights(resolution, false);
		
		int from = 0;
		int to = 0;
		int levelled = 0;
		int removed = 0;
		
		while((from = nextWeightTransit(to, level, -1)) > 0) {
			to = nextWeightTransit(from, level, 1);
			if(to < 0) to = size();
			
			if(to - from > minFrames) {
				// Remove the offsets, and update the dependencies...
				localLevel(from, to, parms, robust);
				
				// Set default frame weights (for now, might be overwritten if re-weighting below...)
				for(int t=to; --t >= from; ) {
					final Frame exposure = get(t);
					if(exposure != null) exposure.tempC = 1.0F;
				}
				
				levelled++;
			}
			else {
				for(int t=from; t<to; t++) if(get(t) != null) get(t).flag(Frame.FLAG_JUMP);
				removed++;
			}
		}
		
		// Recalculate the frame weights as necessary... (it's fast!)
		if(levelled > 0  || removed > 0) {
			if(hasOption("weighting.frames")) getTimeWeights();
		}
		// Otherwise, just reinstate the old weights...
		else for(final Frame exposure : this) if(exposure != null) exposure.relativeWeight = exposure.tempC;
		
		comments += levelled + ":" + removed;
	}
	
	
	
	protected int nextWeightTransit(int fromt, double level, int direction) {
		int nt = size();
			
		for(int t=fromt; t<nt; t++) {	
			final Frame exposure = get(t);
			if(exposure == null) continue;
			if(exposure.isFlagged(Frame.TIME_WEIGHTING_FLAGS)) continue;
			if(exposure.relativeWeight <= 0.0) continue;
		
			if(direction < 0) {
				if(exposure.relativeWeight < level) return t;
			}
			else {	
				if(exposure.relativeWeight > level) return t;
			}
		}
		return -1;
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

			
	public void despike(Configurator despike) {
		String method = despike.isConfigured("method") ? despike.get("method").getValue().toLowerCase() : "absolute";
		
		double level = 10.0;
		
		try { despike.mapValueTo("level"); }
		catch(LockedException e) {}	 // TODO...
		
		if(despike.isConfigured("level")) level = despike.get("level").getDouble();
		
		double flagFraction = despike.isConfigured("flagfraction") ? despike.get("flagfraction").getDouble() : 1.0;
		int flagCount = despike.isConfigured("flagcount") ? despike.get("flagcount").getInt() : Integer.MAX_VALUE;
		int frameSpikes = despike.isConfigured("framespikes") ? despike.get("framespikes").getInt() : instrument.size();
		
		if(method.equals("neighbours") || method.equals("neighbors")) {
		    int delta = isPhaseModulated() ? 1 : framesFor(0.2 * getPointCrossingTime());
		    despikeNeighbouring(level, delta);
		}
		else if(method.equals("absolute")) despikeAbsolute(level);
		else if(method.equals("gradual")) despikeGradual(level, 0.1);
		else if(method.equals("multires") || method.equals("features")) despikeMultires(level);
	
			
		// Flag spiky frames first assumes that spikes tend to be caused in many pixels at once
		// rather than some pixels being inherently spiky...
		// Only do these for regular spikes (not features)...
		if(!method.equalsIgnoreCase("features")) flagSpikyFrames(frameSpikes);
		
		if(method.equalsIgnoreCase("features")) {
			int featureWidth = framesFor(filterTimeScale) >>> 1;
			double featureFraction = 1.0 - Math.exp(-featureWidth*flagFraction);
			flagSpikyChannels(featureFraction, featureWidth*flagCount);
		}
		else flagSpikyChannels(flagFraction, flagCount);
		
		
		if(isPhaseModulated()) if(hasOption("phasedespike")) {
			PhaseSet phases = ((PhaseModulated) this).getPhases();
			if(phases != null) phases.despike(level);
		}
		
	}
	
	private void setTempDespikeLevels(ChannelGroup<?> channels, final double significance) {
		for(Channel channel : channels) channel.temp = (float) (significance * Math.sqrt(channel.variance));
	}
	
	public void despikeNeighbouring(final double significance, final int delta) {
		comments += "dN";
		
		if(size() < delta) return;
		
		final int excludeSamples = Frame.SAMPLE_SOURCE_BLANK | Frame.SAMPLE_SKIP;
		final int notSpike = ~Frame.SAMPLE_SPIKE;
			
		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		
		setTempDespikeLevels(connectedChannels, significance);
		
		final float[] frameLevel = getFloats();
		
		// Clear the spike flag for every sample...
		// and precalculate the thresholds...
		new Fork<Void>() {
			@Override
			protected void process(final FrameType exposure) {
				for(final Channel channel : connectedChannels) exposure.sampleFlag[channel.index] &= notSpike;
				if(exposure.index >= delta) {
					final Frame before = get(exposure.index - delta);
					frameLevel[exposure.index] = before == null ? Float.NaN : (float) Math.sqrt(1.0F / exposure.relativeWeight + 1.0F / before.relativeWeight);
				}
				else frameLevel[exposure.index] = Float.NaN;
			}			
		}.process();
		
		// perform the actual despiking...
		connectedChannels.new Fork<Void>() {
			@Override 
			protected void process(final Channel channel) {

				for(int t=size() - delta; --t >= 0; ) {
					final Frame exposure = get(t);
					if(exposure == null) continue;
					
					final Frame after = get(t + delta);
					if(after == null) continue;
					
					if((exposure.sampleFlag[channel.index] & excludeSamples) != 0) continue;
					if((after.sampleFlag[channel.index] & excludeSamples) != 0) continue;
					
					// Flag both points as spike, since it's not immediately clear which is the troubled one...
					if((after.sampleFlag[channel.index] & Frame.SAMPLE_SPIKE) == 0) {
						if(Math.abs(exposure.data[channel.index] - after.data[channel.index]) > channel.temp * frameLevel[after.index]) {
							exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;
							after.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;
						}
					}
					
					// Exonerate the prior spike if possible...
					else if(Math.abs(exposure.data[channel.index] - after.data[channel.index]) <= channel.temp * frameLevel[after.index])
						after.sampleFlag[channel.index] &= notSpike;
				}
			}
			
		}.process();
	
		recycle(frameLevel);

	}

	public void despikeAbsolute(final double significance) {
		comments += "dA";
		
		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		final int excludeSamples = Frame.SAMPLE_SOURCE_BLANK | Frame.SAMPLE_SKIP;
		final int notSpike = ~Frame.SAMPLE_SPIKE;
		
		setTempDespikeLevels(connectedChannels, significance);
		
		new Fork<Void>() {
			@Override
			protected void process(final FrameType exposure) {
				final float frameChi = 1.0F / (float)Math.sqrt(exposure.relativeWeight);
				for(final Channel channel : connectedChannels) {
					// Clear any prior spike flag...
					exposure.sampleFlag[channel.index] &= notSpike;
					// Check for spikes...
					if((exposure.sampleFlag[channel.index] & excludeSamples) == 0) 
						if(Math.abs(exposure.data[channel.index]) > channel.temp * frameChi) 
							exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;
				}
			}	
		}.process();
	}
	

	public void despikeGradual(final double significance, final double depth) {
		comments += "dG";

		final ChannelGroup<?> connectedChannels = instrument.getConnectedChannels();
		
		setTempDespikeLevels(connectedChannels, significance);
		
		final int excludeSamples = Frame.SAMPLE_SOURCE_BLANK | Frame.SAMPLE_SKIP;
		final int notSpike = ~Frame.SAMPLE_SPIKE;
		
		new Fork<Void>() {

			@Override
			protected void process(FrameType exposure) {
				if(exposure.isFlagged(Frame.MODELING_FLAGS)) return;
				
				double maxdev = 0.0;
				final float frameChi = 1.0F / (float)Math.sqrt(exposure.relativeWeight);
				
				// Clear prior spike flags...
				// Find the largest not yet flagged as spike deviation.
				for(final Channel channel : connectedChannels) {
					exposure.sampleFlag[channel.index] &= notSpike;
					if((exposure.sampleFlag[channel.index] & excludeSamples) == 0)
						maxdev = Math.max(maxdev, Math.abs(exposure.data[channel.index] / (float)channel.gain));
				}
					
				if(maxdev > 0.0) {
					double minSignal = depth * maxdev;
					for(final Channel channel : connectedChannels) if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SOURCE_BLANK) == 0) {
						final double critical = Math.max(channel.gain * minSignal, channel.temp * frameChi);
						if(Math.abs(exposure.data[channel.index]) > critical) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;
					}
				}
			}
			
		}.process();
		
	}
	

	public void despikeMultires(final double significance) {
		int maxBlockSize = framesFor(filterTimeScale) >>> 1;
		if(maxBlockSize < 1) maxBlockSize = 1;	
		if(maxBlockSize > size()) maxBlockSize = size();
		
		comments += "dM";
		
		final ChannelGroup<?> liveChannels = instrument.getConnectedChannels();
		final int nt = size();
		final int mbSize = maxBlockSize;
		final int notSpike = ~Frame.SAMPLE_SPIKE;
		
		// Clear the spike flags...
		new Fork<Void>() {
			@Override
			protected void process(FrameType exposure) {
				for(final Channel channel : liveChannels) exposure.sampleFlag[channel.index] &= notSpike;	
			}
		}.process();
		
		instrument.new Fork<Void>() {
			private float[] data, weight;
			private DataPoint diff, temp;
			
			@Override
			protected void init() {
				super.init();
				diff = new DataPoint();
				temp = new DataPoint();
				data = getFloats();
				weight = getFloats();
			}
			
			@Override
			protected void cleanup() {
				super.cleanup();
				recycle(data);
				recycle(weight);
			}
			
			@Override
			protected void process(final Channel channel) {
				getTimeStream(channel, data, weight);
				
				// check and divide...
				int n = size();
				for(int blockSize = 1; blockSize <= mbSize; blockSize <<= 1) {
					for(int T=1; T < n; T++) if(T < n) {
						diff.setValue(data[T]);
						diff.setWeight(weight[T]);
						temp.setValue(data[T-1]);
						temp.setWeight(weight[T-1]);
						
						diff.subtract(temp);
						if(diff.significance() > significance) {
							for(int t=blockSize*(T-1), blockt=0; t<nt && blockt < (blockSize<<1); t++, blockt++) {
								final Frame exposure = get(t);
								if(exposure != null) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SPIKE;		
							}
						}
					}
					
					n >>>= 1;
					
					for(int to=0, from=0; to < n; to++, from += 2) {
						// to = average(from, from+1)
						data[to] = weight[from] * data[from] + weight[from+1] * data[from+1];
						weight[to] = weight[from] + weight[from+1];
						data[to] /= weight[to];
					}
				}
			}
			
		}.process();
	}
	
	public void flagSpikyChannels(final double flagFraction, final int minSpikes) {
		final int maxChannelSpikes = Math.max(minSpikes, (int)Math.round(flagFraction * size()));
		
		// Flag spiky channels even if spikes are in spiky frames
		//int frameFlags = LabocaFrame.MODELING_FLAGS & ~LabocaFrame.FLAG_SPIKY;
		
		// Only flag spiky channels if spikes are not in spiky frames
		final int frameFlags = Frame.MODELING_FLAGS;
	
		for(Channel channel : instrument) channel.spikes = 0;
		
		Fork<int[]> spikeCount = new Fork<int[]>() {
			private int[] channelSpikes;
			
			@Override
			protected void init(){
				super.init();
				channelSpikes = instrument.getInts();
				Arrays.fill(channelSpikes, 0);
			}
			
			@Override
			protected void process(FrameType exposure) {
				if(exposure.isFlagged(frameFlags)) return;
				for(final Channel channel : instrument) if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SPIKE) != 0) channelSpikes[channel.index]++;
			}
			
			@Override
			public int[] getLocalResult() { return channelSpikes; }
			
			@Override
			public int[] getResult() {
				init();
				for(Parallel<int[]> task : getWorkers()) {
					int[] localSpikes = task.getLocalResult();
					for(int c=instrument.size(); --c >= 0; ) channelSpikes[c] += localSpikes[c];
					Instrument.recycle(localSpikes);
				}
				return channelSpikes;
			}
			
		};
		
		spikeCount.process();
			
		final int[] channelSpikes = spikeCount.getResult();
		
		for(Channel channel : instrument) {
			channel.spikes = channelSpikes[channel.index];
			if(channel.spikes > maxChannelSpikes) channel.flag(Channel.FLAG_SPIKY);
			else channel.unflag(Channel.FLAG_SPIKY);
		}
			
		Instrument.recycle(channelSpikes);
		
		instrument.census();
		comments += instrument.mappingChannels;
	}
			
	public void flagSpikyFrames(final double minSpikes) {
		
		// Flag spiky frames even if spikes are in spiky channels.
		//int channelFlags = ~(LabocaPixel.FLAG_SPIKY | LabocaPixel.FLAG_FEATURES);
		
		// Flag spiky frames only if spikes are not in spiky channels.
		final int channelFlags = ~0;
		
		Fork<Integer> flagger = new Fork<Integer>() {
			private int spikyFrames = 0;
			
			@Override
			protected void process(FrameType exposure) {
				int frameSpikes = 0;

				for(final Channel channel : instrument) if(channel.isUnflagged(channelFlags))
					if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SPIKE) != 0) frameSpikes++;

				if(frameSpikes > minSpikes) {
					exposure.flag |= Frame.FLAG_SPIKY;
					spikyFrames++;
				}
				else exposure.flag &= ~Frame.FLAG_SPIKY;
			}
			
			@Override
			public Integer getLocalResult() { return spikyFrames; }
			
			@Override
			public Integer getResult() {
				int globalSpikyFrames = 0;
				for(Parallel<Integer> task : getWorkers()) globalSpikyFrames += task.getLocalResult();
				return globalSpikyFrames;
			}
			
		};
		flagger.process();
		
		//comments += "(" + Util.f1.format(100.0*spikyFrames/size()) + "%)";
		comments += "(" + flagger.getResult() + ")";
	}
	
	public boolean isDetectorStage() {
		return isDetectorStage;
	}
		
	public void detectorStage() { 
		if(isDetectorStage) return;
		
		instrument.loadTempHardwareGains();
		
		new Fork<Void>() {
			@Override
			public void process(FrameType frame) {
				for(final Channel channel : instrument) frame.data[channel.index] /= channel.temp;
			}
		}.process();
					
		isDetectorStage = true;		
	}
	
	public void readoutStage() { 
		if(!isDetectorStage) return;
		
		instrument.loadTempHardwareGains();
		
		new Fork<Void>() {
			@Override
			public void process(FrameType frame) {
				for(final Channel channel : instrument) frame.data[channel.index] *= channel.temp;
			}
		}.process();
		
		isDetectorStage = false;
	}
	
	public void clearData() {
		new Fork<Void>() {
			@Override
			public void process(FrameType frame) { for(final Channel channel : instrument) frame.data[channel.index] = 0.0F; }
		}.process();
	}
	
	public void randomData() {
		final Random random = new Random();
		
		instrument.new Fork<Void>() {
			@Override
			public void process(Channel channel) { channel.temp = (float)(Math.sqrt(1.0/channel.weight)); }
		}.process();

		new Fork<Void>() {
			@Override
			public void process(FrameType frame) { 
				for(final Channel channel : instrument) frame.data[channel.index] = channel.temp * (float) random.nextGaussian(); 	
			}
		}.process();	
	}
	
	public void addCorrelated(final CorrelatedSignal signal) throws Exception {	
		final Mode mode = signal.getMode();
		final float[] gain = mode.getGains();
		final int nc = mode.size();
		
		new Fork<Void>() {
			@Override
			public void process(FrameType frame) { 
				final float C = signal.valueAt(frame);
				for(int k=nc; --k >= 0; ) frame.data[mode.getChannel(k).index] += gain[k] * C;
			}				
		}.process();
	}
	
	
	public Vector2D[] getPositions(final int type) {
		final Vector2D[] position = new Vector2D[size()];
		SphericalCoordinates coords = new SphericalCoordinates();
		
		for(Frame exposure : this) if(exposure != null) {
			final Vector2D pos = new Vector2D();
			position[exposure.index] = pos;

			// Telescope motion should be w/o chopper...
			// TELESCOPE motion with or w/o SCANNING and CHOPPER
			if((type & Motion.TELESCOPE) != 0) {
				coords.copy(exposure.getNativeCoords());
				// Subtract the chopper motion if it is not requested...
				if((type & Motion.CHOPPER) == 0) coords.subtractNativeOffset(exposure.chopperPosition);
				pos.copy(coords);

				if((type & Motion.PROJECT_GLS) != 0) pos.scaleX(coords.cosLat());
			}

			// Scanning includes the chopper motion
			// SCANNING with or without CHOPPER
			else if((type & Motion.SCANNING) != 0) {
				exposure.getNativeOffset(pos);
				// Subtract the chopper motion if it is not requested...
				if((type & Motion.CHOPPER) == 0) pos.subtract(exposure.chopperPosition);
			}	

			// CHOPPER only...
			else if(type == Motion.CHOPPER) pos.copy(exposure.chopperPosition);
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

		final int nm = n >>> 1;
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
	
	
	public Signal getPositionSignal(final Mode mode, final int type, final Motion direction) {
		final Vector2D[] pos = getSmoothPositions(type);
		final float[] data = new float[size()];	

		for(int t=size(); --t >= 0; ) 
			data[t] = (pos[t] == null) ? Float.NaN : (float) direction.getValue(pos[t]);
		
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
					pos[t+1].x() - pos[t-1].x(),
					pos[t+1].y() - pos[t-1].y()
				);
				v[t].scale(i2dt);
			}		
		}
	
		// Extrapolate first and last...
		if(size() > 1) v[0] = v[1];
		if(size() > 2) v[size()-1] = v[size()-2];
		
		return v;
	}
	
	public DataPoint getMedianScanningVelocity(double smoothT) {
		final Vector2D[] v = getScanningVelocities();		
		
		final float[] speed = getFloats();
		Arrays.fill(speed, 0, v.length, 0.0F);
		
		int n=0;
		for(int t=v.length; --t >= 0; ) if(v[t] != null) speed[n++] = (float) v[t].length();
		double avev = n > 0 ? Statistics.median(speed, 0, n) : Double.NaN;
		
		n=0;
		for(int t=v.length; --t >= 0; ) if(v[t] != null) {
			float dev = (float) (speed[n] - avev);
			speed[n++] = dev*dev;
		}
		double w = n > 0 ? Statistics.medianNormalizedVariance / Statistics.median(speed, 0, n) : 0.0;
		
		recycle(speed);
		
		return new DataPoint(new WeightedPoint(avev, w));
	}
	
	public int velocityCut(final Range range) { 
		
		final Vector2D[] v = getScanningVelocities();
		int flagged = 0, cut = 0;
		
		for(Frame frame : this) if(frame != null) {
			final Vector2D value = v[frame.index];

			if(value == null) {
				set(frame.index, null);
				cut++;
			}
			else {	
				final double speed = value.length();
				
				if(speed < range.min()) {
					frame.flag(Frame.SKIP_SOURCE_MODELING);
					flagged++;
				}			
				else if(speed > range.max()) {
					set(frame.index, null);
					cut++;
				}
				
			}
		}
		
		info("Discarding unsuitable mapping speeds. " +
		        "[" + (int)Math.round(100.0 * flagged / size()) + "% flagged, " +
		        (int) Math.round(100.0 * cut / size()) + "% clipped]");

		return cut;

	}

	
	public int accelerationCut(final double maxA) {
		
		final Vector2D[] a = getAccelerations();
		int cut = 0;
		
		for(Frame frame : this) if(frame != null) {
			final Vector2D value = a[frame.index];

			if(value == null) {
				set(frame.index, null);
				cut++;
			}
			else if(!(value.length() <= maxA)) {
				set(frame.index, null);
				cut++;
			}
		}

		info("Discarding excessive telescope accelerations. [" + (int)Math.round(100.0 * cut / size()) + "% clipped]");

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
						Math.cos(pos[t].y()) * Math.IEEEremainder(pos[t+1].x() + pos[t-1].x() - 2.0*pos[t].x(), Constant.twoPi),
						pos[t+1].y() + pos[t-1].y() - 2.0*pos[t].y()
						);
				a[t].scale(idt);
			}
		}
		
		// Extrapolate to ends...
		if(size() > 1) a[0] = a[1];
		if(size() > 2) a[size()-1] = a[size()-2];
		
		return a;
	}
	
	public Signal getAccelerationSignal(Mode mode, final Motion direction) {
		final Vector2D[] a = getAccelerations();
		final float[] data = new float[size()];	
		
		for(int t=size(); --t >= 0; ) 
			data[t] = a[t] == null ? Float.NaN : (float) direction.getValue(a[t]);
		
		return new Signal(mode, this, data, false);
	}
	

	
	// TODO parallelize...
	public void checkForNaNs(final Iterable<? extends Channel> channels, final int from, int to) {
		comments += "?";
		
		to = Math.min(to, size());
		
 		for(int t=to; --t >= from; ) {
			final Frame exposure = get(t);
			
			if(exposure != null) for(final Channel channel : channels) {

				if(Float.isNaN(exposure.data[channel.index])) {
					comments += "NaN: " + exposure.index + "," + channel.index;
					error(comments);
					System.exit(1);
				}

				if(Float.isInfinite(exposure.data[channel.index])) {
					comments += "Inf: " + exposure.index + "," + channel.index;
					error(comments);
					System.exit(1);
				}

				/*
				if(exposure.data[channel.index] > 10.0) {
					comments += "Big: " + exposure.index + "," + channel.index;
					error(comments);
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
	public void downsample(final int n) {
		if(n < 2) return;
		
		final int windowSize = (int)Math.round(1.82 * n * WindowFunction.getEquivalentWidth("Hann"));
		final int centerOffset = windowSize/2 + 1;
		final double[] w = WindowFunction.get("Hann", windowSize);
		
		final int N = ExtraMath.roundupRatio(size()-windowSize, n);
		
		if(N <= 0) {
			warning("Time stream too short to downsample by specified amount.");
			return;
		}
	
		info("Downsampling by " + n + " to " + N + " frames.");
		
		final Frame[] buffer = new Frame[N];
		
		// Normalize window function to absolute integral 1
		double norm = 0.0;
		for(int i=w.length; --i >= 0; ) norm += Math.abs(w[i]);
		for(int i=w.length; --i >= 0; ) w[i] /= norm;

		new CRUSH.Fork<Void>(N, getThreadCount()) {
			@Override
			protected void processIndex(int k) { buffer[k] = getDownsampled(k); }
			
			private final FrameType getDownsampled(int k) {
				final int to = windowSize + k*n;
				final FrameType central = get(to-centerOffset);

				if(central != null) {
					final FrameType downsampled = (FrameType) central.copy(false);

					for(int t=to-windowSize; t<to; t++) {
						final FrameType exposure = get(t);
						if(exposure == null) return null;
						downsampled.addDataFrom(exposure, w[to-t-1]);
					}
					return downsampled;
				}
				return null;
			}
		}.process();

		instrument.samplingInterval *= n;
		instrument.integrationTime *= n;
		
		clear();
		for(int t=0; t<N; t++) add((FrameType) buffer[t]);
		trimToSize();
		reindex();
		
		// TODO downsample dependents / signals too
		dependents.clear();
		signals.clear();
	}
	
	
	public void notchFilter() {
		if(!hasOption("notch.frequencies")) return;
		
		List<Double> frequencies = option("notch.frequencies").getDoubles();
		double width = hasOption("notch.width") ? option("notch.width").getDouble() : 0.1;
		
		if(hasOption("notch.harmonics")) {
			int harmonics = option("notch.harmonics").getInt();
			
			for(int i=frequencies.size(); --i >= 0; ) {
				double f0 = frequencies.get(i);
				for(int k=2; k<=harmonics; k++) frequencies.add(f0 * k);
			}
		}
		
		if(hasOption("notch.bands")) {
			List<String> ranges = option("notch.bands").getList();
			for(String rangeSpec : ranges) {
				Range range = Range.parse(rangeSpec, true);
				for(double f = range.min(); f<range.max(); f += width) frequencies.add(f);
			}
		}
		
		if(frequencies.isEmpty()) return;
		
		Collections.sort(frequencies);
		
		notchFilter(frequencies, width);
	}
	
	public void notchFilter(final List<Double> frequencies, final double width) {
		final int windowSize = ExtraMath.pow2ceil((int) Math.ceil(1.0 / (width * instrument.samplingInterval)));
		final double df = 1.0 / (windowSize * instrument.samplingInterval);
		final int nf = windowSize >>> 1;
		
		info("Notching " + frequencies.size() + " frequencies.");
		
		instrument.new Fork<Void>() {
			private FloatFFT fft;
			private float[] data;
			
			@Override
			protected void init() {
				fft = new FloatFFT();
				data = new float[windowSize];
			}

			@Override
			protected void process(Channel channel) {
				for(int from = 0; from < size(); from += windowSize) {
					process(channel, from, Math.min(from + windowSize, size()));
				}
			}
			
			private void process(Channel channel, int from, int to) {
				double sum = 0.0;
				int n = 0;
				
				for(int t=from; t<to; t++) {
					final Frame frame = get(t);
					if(frame == null) data[t - from] = 0.0F;
					else {
						data[t - from] = frame.data[channel.index];
						sum += frame.data[channel.index];
						n++;
					}
				}
				final float ave = n > 0 ? (float) (sum / n) : 0.0F;
				for(int t=from; t<to; t++) if(get(t) != null) data[t - from] -= ave;
				
				Arrays.fill(data, to - from, data.length, 0.0F);
				
				fft.real2Amplitude(data);
				
				for(double f : frequencies) {
					int bin = (int)Math.floor(f / df);
					filter(bin);
					filter(bin+1);
				}	
				
				fft.amplitude2Real(data);
				
				for(int t=from; t<to; t++) {
					final Frame frame = get(t);
					if(frame != null) frame.data[channel.index] = ave + data[t - from];
				}
				
			}
			
			private void filter(int bin) {
				if(bin > nf) return;
				if(bin == nf) data[1] = 0.0F;
				else {
					bin <<= 1;
					data[bin] = 0.0F;
					data[bin+1] = 0.0F;
				}
			}

		}.process();		
	}
	

	public void offset(final double value) {
		new Fork<Void>() {
			@Override
			protected void process(FrameType frame) {
				for(final Channel channel : instrument) if(channel.flag == 0) frame.data[channel.index] += value;
			}
		}.process();
	}

	public void writeASCIITimeStream() throws IOException {
		String filename = CRUSH.workPath + File.separator + scan.getID() + "-" + getFileID() + ".tms";
		final PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(filename), 1000000));
		out.println("# " + Util.e3.format(1.0/instrument.samplingInterval));
		final int nc = instrument.size();
		
		String flagValue = "---";
		
		for(final Frame exposure : this) {
			boolean isEmpty = true;
			
			//if(exposure != null) out.print(Util.f1.format(exposure.getNativeOffset().y() / Unit.arcsec) + "\t");
			//else out.print("0.0\t");
			
			if(exposure != null) if(exposure.isUnflagged(Frame.BAD_DATA)) {
				isEmpty = false;
				for(int c=0; c<nc; c++) 
					out.print((exposure.sampleFlag[c] & Frame.SAMPLE_SPIKE) != 0 ? flagValue + "\t\t" : Util.e5.format(exposure.data[c]) + "\t");
			}
			if(isEmpty) for(int c=0; c<nc; c++) out.print(flagValue + "\t\t");
		
			out.println();
				
		}
		out.flush();
		out.close();
		notify("Written ASCII time-streams to " + filename);
	}

	public double[][] getCovariance() {
		info("Calculating Covariance Matrix (this may take a while...)");
		
		final double[][] covar = new double[instrument.size()][instrument.size()];
		final int[][] n = new int[instrument.size()][instrument.size()];
		
		instrument.new Fork<Void>() {
			@Override
			protected void process(Channel channel) {
				if(channel.flag != 0) return; 
				
				final double[] rowC = covar[channel.index];
				final int[] rowN = n[channel.index];
				
				for(final Frame exposure : Integration.this) if(exposure != null) 
					if(exposure.isUnflagged(Frame.SOURCE_FLAGS)) if(exposure.sampleFlag[channel.index] == 0) 
						for(int c2=instrument.size(); --c2 > channel.index; ) if(instrument.get(c2).flag == 0) if(exposure.sampleFlag[c2] == 0) {
							rowC[c2] += exposure.relativeWeight * exposure.data[channel.index] * exposure.data[c2];
							rowN[c2]++;
						}
				
				for(int c2=instrument.size(); --c2 >= channel.index; ) {
					rowC[c2] *= Math.sqrt(instrument.get(channel.index).weight * instrument.get(c2).weight) / rowN[c2];
					covar[c2][channel.index] = rowC[c2];
				}
				
			}	
		}.process();
		
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

	public double[][] getFullCovariance(final double[][] covar) {	
		final double[][] fullCovar = new double[instrument.storeChannels][instrument.storeChannels];
		
		new CRUSH.Fork<Void>(instrument.storeChannels, getThreadCount()) {
            @Override
            protected void processIndex(int index) {
                Arrays.fill(fullCovar[index], Double.NaN);
            }
        }.process();
		
		instrument.new Fork<Void>() {
			@Override
			protected void process(Channel c1) {
				for(final Channel c2 : instrument)
					fullCovar[c1.getFixedIndex()][c2.getFixedIndex()] = covar[c1.index][c2.index];
			}
		}.process();
				
		return fullCovar;
	}

	protected void writeCovariance(String name, double[][] covar) throws IOException, FitsException {
	    
	    if(hasOption("write.covar.condensed")) covar = condenseCovariance(covar);
	        
		if(!name.endsWith(".fits")) name += "-" + scan.getID() + "-" + getFileID() + ".fits";
		
		if(covar == null) return;
		
		Fits fits = new Fits();
		BasicHDU<?> hdu = Fits.makeHDU(covar);
		fits.addHDU(hdu);
		
		FitsToolkit.write(fits, name);
		fits.close();
		
		notify("Written " + name);
	}

	protected double[][] condenseCovariance(double[][] covar) {
	    int n = covar.length;
	    ArrayList<Integer> lookup = new ArrayList<Integer>(n);

	    for(int i=0; i < n; i++) {
	        for(int j=0; j < i; j++) if(!Double.isNaN(covar[i][j])) if(covar[i][j] != 0) {
	            lookup.add(i);
	            break;
	        }
	    }
	    if(lookup.size() == n) return covar;

	    double[][] condensed = new double[lookup.size()][lookup.size()];
	    
	    for(int i=condensed.length; --i >= 0; ) {
	        int fromi = lookup.get(i);
	        for(int j=condensed.length; --j > i; ) condensed[i][j] = condensed[j][i] = covar[fromi][lookup.get(j)];
	    }
	    
	    return condensed;
	    
	}
	
	float[][] getSpectra() {
		return getSpectra("Hamming", 2*framesFor(filterTimeScale));
	}
	
	float[][] getSpectra(String windowName, int windowSize) {
		final double[] w = WindowFunction.get(windowName, windowSize);

		// info("Calculating Power spectra.");
		final float[][] spectra = new float[instrument.size()][];
		final double df = 1.0 / (instrument.samplingInterval * windowSize);	
		final float Jy = gain * (float) instrument.janskyPerBeam();
		
		final int nt = size();

		instrument.new Fork<Void>() {
			private float[] data;
			
			@Override
			protected void init() {
				super.init();
				data = getFloats();
			}
			
			@Override
			protected void cleanup() {
				super.cleanup();
				recycle(data);
			}

			@Override
			protected void process(Channel channel) {
				for(int t=nt; --t >= 0; ) {
					final Frame exposure = get(t);
					if(exposure == null) data[t] = 0.0F;
					else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = 0.0F;
					else if(exposure.sampleFlag[channel.index] != 0) data[t] = 0.0F;
					else data[t] = exposure.data[channel.index];
				}
				
				final double[] spectrum = getSequentialFFT().averagePower(data, w);
				final float[] channelSpectrum = new float[spectrum.length];
				for(int i=spectrum.length; --i>=0; ) channelSpectrum[i] = (float) Math.sqrt(spectrum[i] / df) / Jy;		
				spectra[channel.index] = channelSpectrum;	
			}
			
		
		}.process();
		
		return spectra;
	}
	
	public void writeSpectra(String windowName, int windowSize) throws IOException {
		String fileName = CRUSH.workPath + File.separator + getFileID() + ".spec";

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

		notify("Written Power spectra to " + fileName);

	}


	public void writeCovariances() {
	
		final double[][] covar = getCovariance(); 
		List<String> specs = hasOption("write.covar") ? option("write.covar").getList() : new ArrayList<String>();
		String prefix = CRUSH.workPath + File.separator + "covar";
		
		// If no argument is specified, write the full covariance in backend channel ordering
		if(specs.size() == 0) specs.add("full");

		for(String name : specs) {
			if(name.equalsIgnoreCase("full")){
				try { writeCovariance(prefix + "-" + getFileID() + ".fits", getFullCovariance(covar)); }
				catch(Exception e) { error(e); }	
			}
			else if(name.equalsIgnoreCase("reduced")){
				try { writeCovariance(prefix + "-" + getFileID() + ".reduced.fits", covar); }
				catch(Exception e) { error(e); }	
			}	
			else {
				ChannelDivision<?> division = instrument.divisions.get(name);
				if(division == null) warning("Cannot write covariance for " + name + ". Undefined grouping.");
				else {
					try { writeCovariance(prefix + "-" + getFileID() + "." + name + ".fits", getGroupCovariance(division, covar)); }
					catch(Exception e) { error(e); }	
				}
			}
		}
	}
	
	public void writeProducts() {
		

		if(hasOption("write.pattern")) {
			try { writeScanPattern(); }
			catch(Exception e) { error(e); }
		}
		
		if(hasOption("write.pixeldata")) {
			String fileName = CRUSH.workPath + File.separator + "pixel-" + getFileID() + ".dat";
			try { instrument.writeChannelData(fileName, getASCIIHeader()); }
			catch(Exception e) { error(e); }
		}

		if(hasOption("write.covar")) writeCovariances();
			
		if(hasOption("write.ascii")) {
			try { writeASCIITimeStream(); }
			catch(Exception e) { error(e); }
		}
		
		if(hasOption("write.phases")) if(isPhaseModulated()) {
			try { ((PhaseModulated) this).getPhases().write(); }
			catch(Exception e) { error(e); }
		}
		
		if(hasOption("write.signals")) for(Mode mode : signals.keySet()) {
			try { 
				PrintStream out = new PrintStream(new FileOutputStream(CRUSH.workPath + File.separator + mode.name + "-" + getFileID() + ".tms"));
				signals.get(mode).print(out);
				notify("Written " + mode.name + ".tms");
				out.close();
			}
			catch(IOException e) {}
		}
		
		if(hasOption("write.spectrum")) {
			Configurator spectrumOption = option("write.spectrum");
			String argument = spectrumOption.getValue();
			String windowName = argument.length() == 0 ? "Hamming" : argument;
			int windowSize = spectrumOption.isConfigured("size") ? spectrumOption.get("size").getInt() : 2*framesFor(filterTimeScale);
		
			try { writeSpectra(windowName, windowSize); }
			catch(Exception e) { error(e); }
		}
		
		
		if(hasOption("write.coupling")) writeCouplingGains(option("write.coupling").getList()); 
		
		if(hasOption("write.coupling.spec")) writeCouplingSpectrum(option("write.coupling.spec").getList()); 
		
	}
	
	public void writeScanPattern() throws IOException {
		String fileName = CRUSH.workPath + File.separator + "pattern-" + getFileID() + ".dat";
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		
		for(int i=0; i<size(); i++) {
			Frame exposure = get(i);
			if(exposure == null) out.println("---\t---");
			else if(exposure.isFlagged()) out.println("...\t...");
			else {
				Vector2D offset = exposure.getNativeOffset();
				out.println(Util.f1.format(offset.x() / Unit.arcsec) + "\t" + Util.f1.format(offset.y() / Unit.arcsec));
			}
		}
		out.close();
		
		notify("Written " + fileName);
		
	}
	
	public void getFitsData(LinkedHashMap<String, Object> data) {
		data.put("Obs", new int[] { integrationNo });
		data.put("Integration_Time", new double[] { size()*instrument.integrationTime });
		data.put("Frames", new int[] { size() });
		data.put("Relative_Gain", new double[] { gain });
		data.put("NEFD", new double[] { nefd } );
		data.put("Hipass_Timescale", new double[] { filterTimeScale / Unit.s } );
		data.put("Filter_Resolution", new double[] { 0.5/ExtraMath.pow2ceil(framesFor(filterTimeScale)) } );
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
			dataIndex[channel.index] = channel.getFixedIndex();
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
		int iters = 100;
		
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
		
		DecimalFormat df = Util.f2;
		
		CRUSH.result(this, "> " + Integer.toHexString(m) + "\n"
		        + "# array:     " + df.format((double) a/iters) + " ms\t(inverted: " + df.format((double) f/iters) + " ms)\n"
		        + "# ArrayList: " + df.format((double) b/iters) + " ms\t(inverted: " + df.format((double) e/iters) + " ms)\n"
		        + "# Vector:    " + df.format((double) d/iters) + " ms");
		
		System.exit(0);
	}
	
	public void detectChopper() {
		if(!(this instanceof Chopping)) return;
		
		Signal x = getPositionSignal(null, Motion.CHOPPER, Motion.X);
		Signal y = getPositionSignal(null, Motion.CHOPPER, Motion.Y);
		
		x.level(false);
		y.level(false);
		
		int xTransitions = 0, yTransitions = 0;
		int xFrom = -1, xTo = -1, yFrom = -1, yTo = -1;
		
		Frame first = getFirstFrame();
		boolean xPositive = x.valueAt(first) > 0.0;
		boolean yPositive = y.valueAt(first) > 0.0; 
		final int nt = size();
		
		double sumA = 0.0, sumw = 0.0;
		
		float[] distance = getFloats();
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
				double d = ExtraMath.hypot(dx, dy);
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
			
			Chopper chopper = new Chopper();
			chopper.amplitude = Statistics.median(distance, 0, n);
			
			if(chopper.amplitude < threshold) {
				chopper = null;
				info("Small chopper fluctuations (assuming chopper not used).");
				instrument.forget("chopped");
				return;
			}
			chopper.positions = 2;
			chopper.frequency = (transitions-1) / (2.0*dt);
			chopper.angle = sumA / sumw;
	
			int steady = 0;
			for(int k=0; k<n; k++) if(Math.abs(distance[k] - chopper.amplitude) < threshold) steady++;
			chopper.efficiency = (double)steady / n;
			
			((Chopping) this).setChopper(chopper);
			
			info("Chopper detected: " + chopper.toString());
			instrument.setOption("chopped");
		}
		else {
			info("Chopper not used.");
			instrument.forget("chopped");
		}	
		
		recycle(distance);
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
		return scan.getID() + separator + (integrationNo + 1);		
	}
	
	public String getDisplayID() {
		return getStandardID("|");
	}

	public String getFileID() {
        return getStandardID("-");
    }
	
	public String getStandardID(String separator) {
        return scan.size() > 1 | scan.isSplit ? getFullID(separator) : scan.getID();
    }
	
	public boolean perform(String task) {
		boolean isRobust = false;
		if(hasOption("estimator")) if(option("estimator").equals("median")) isRobust = true;
			
		if(task.equals("offsets")) {
			removeOffsets(isRobust);	    
		}
		else if(task.equals("drifts")) {
			if(isPhaseModulated()) return false;
			int driftN = filterFramesFor(option("drifts").getValue(), 10.0*Unit.sec);
			removeDrifts(driftN, isRobust);
		}
		else if(task.startsWith("correlated.")) {	
			String modalityName = task.substring(task.indexOf('.')+1);
			if(!decorrelate(modalityName, isRobust)) return false;
		}
		else if(task.equals("weighting")) {
			getWeights();
		}
		else if(task.equals("weighting.frames")) {
			getTimeWeights();
			updatePhases();
		}
		else if(task.equals("despike")) {
			despike(option(task));
			updatePhases();
		}
		else if(task.equals("filter")) {
			if(filter == null) return false;
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
	
		//Thread.yield();
		
		return true;
	}

	public boolean isPhaseModulated() {
		if(!(this instanceof PhaseModulated)) return false;
		PhaseSet phases = ((PhaseModulated) this).getPhases();
		if(phases == null) return false;
		if(phases.isEmpty()) return false;
		return true;
	}
	
	public void updatePhases() {
		if(isPhaseModulated()) ((PhaseModulated) this).getPhases().update(instrument);
	}
	
	public void getWeights() {
		String method = "rms";
		Configurator weighting = option("weighting");
		
		try { weighting.mapValueTo("method"); }
		catch(LockedException e) {}
		
		if(weighting.isConfigured("method")) method = weighting.get("method").getValue().toLowerCase();
		
		getWeights(method);
		
		if(isPhaseModulated()) if(hasOption("phaseweights")) {
			PhaseSet phases = ((PhaseModulated) this).getPhases();
			if(phases != null) phases.getWeights();
		}
	}
	
	public void getWeights(String method) {
		if(method.equals("robust")) getRobustPixelWeights();
		else if(method.equals("differential")) getDifferentialPixelWeights();
		else getRMSPixelWeights();	
		flagWeights();
	}

	public void addSignal(Signal signal) {
		signals.put(signal.getMode(), signal);
	}
	
	public Signal getSignal(Mode mode) {
		Signal signal = signals.get(mode);
		if(signal == null) if(mode instanceof Response) {
			signal = ((Response) mode).getSignal(this);	
			if(signal.isFloating) signal.level(false);
			signal.removeDrifts();
		}
		return signal;
	}
	
	void writeCouplingGains(List<String> signalNames) {
		for(String name : signalNames) {
			try { writeCouplingGains(name); }
			catch(Exception e) {
			    warning("Couplings for '" + name + "' not written: " + e.getMessage());
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}
	}
	
	void writeCouplingGains(String name) throws Exception { 
		Modality<?> modality = instrument.modalities.get(name);
		if(modality == null) return;
		
		modality.updateAllGains(this, false);
		
		double[] g = new double[instrument.size()];
		
		for(Mode mode : modality) getCouplingGains(getSignal(mode), g);
		
		String fileName = CRUSH.workPath + File.separator + getFileID() + "." + name + "-coupling.dat";
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		out.println(this.getASCIIHeader());
		out.println("#");
		out.println("# ch\tgain");
		
		for(int c=0; c<instrument.size(); c++) {
			Channel channel = instrument.get(c);
			if(g[c] != 0.0) out.println(channel.getID() + "\t" + Util.f3.format(g[c]));
		}
		
		notify("Written " + fileName);
		
		out.close();	
	}
	
	private void getCouplingGains(Signal signal, double[] g) throws Exception {	
		Mode mode = signal.getMode();
		
		float[] gains = mode.getGains();
		
		for(int k=0; k<mode.size(); k++) g[mode.getChannel(k).index] = gains[k];
	}
	
	
	void writeCouplingSpectrum(List<String> signalNames) {
		int windowSize = hasOption("write.coupling.spec.windowsize") ? option("write.couplig.spec.windowsize").getInt() : framesFor(filterTimeScale);
		
		for(String name : signalNames) {
			try { writeCouplingSpectrum(name, windowSize); }
			catch(Exception e) {
				warning("Coupling spectra for '" + name + "' not written: " + e.getMessage());
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}
	}	
	
	void writeCouplingSpectrum(String name, int windowSize) throws Exception {
		Modality<?> modality = instrument.modalities.get(name);
		if(modality == null) return;

		modality.updateAllGains(this, false);
		
		String fileName = CRUSH.workPath + File.separator + getFileID() + "." + name + "-coupling.spec";
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		
		out.println(this.getASCIIHeader());
		out.println();
		
		Complex[][] C = new Complex[instrument.size()][];
		
		Channel[] allChannels = new Channel[instrument.storeChannels];
		for(Channel channel : instrument) allChannels[channel.getFixedIndex()] = channel;
		
		for(Mode mode : modality) getCouplingSpectrum(getSignal(mode), windowSize, C);
			
		Complex z = new Complex();

		int nF = C[0].length;
		double df = 1.0 / (windowSize * instrument.samplingInterval);

		for(int c=0; c < instrument.storeChannels; c++) {
			Channel channel = allChannels[c];
			z.set(channel == null ? 0.0 : C[channel.index][0].x(), 0.0);
			out.print(Util.f5.format(0.0) + "\t" + Util.e3.format(z.length()) + "\t" + Util.f3.format(z.angle()));
		}
		out.println();

		// Write the bulk of the spectrum...
		for(int f=1; f<nF; f++) {
			out.print(Util.f5.format(f*df));
			for(int c=0; c < instrument.storeChannels; c++) {
				Channel channel = allChannels[c];
				if(channel == null) z.zero();
				else z.copy(C[channel.index][f]);	
				out.print("\t" + Util.e3.format(z.length()) + "\t" + Util.f3.format(z.angle()));		
			}
			out.println();
		}

		// Write the Nyquist frequency component;
		for(int c=0; c < instrument.storeChannels; c++) {
			Channel channel = allChannels[c];
			z.set(channel == null ? 0.0 : C[channel.index][0].y(), 0.0);
			out.print(Util.f5.format(nF * df) + "\t" + Util.e3.format(z.length()) + "\t" + Util.f3.format(z.angle()));
		}
		out.println();	
		
		notify("Written " + fileName);
		out.close();
		
		writeDelayedCoupling(name, C);
	}
	
	void getCouplingSpectrum(Signal signal, int windowSize, Complex[][] C) throws Exception {	
		Complex[][] spectrum = getCouplingSpectrum(signal, windowSize);
		
		Mode mode = signal.getMode();
		ChannelGroup<? extends Channel> channels = mode.getChannels();
		
		for(int k=mode.size(); --k >= 0; ) {
			Channel channel = channels.get(k);
			C[channel.index] = spectrum[k];
		}
	}
	
			
	void writeDelayedCoupling(String name, Complex[][] spectrum) throws IOException {
		int nF = spectrum[0].length;
		
		FauxComplexArray.Float C = new FauxComplexArray.Float(nF);
		FloatFFT fft = new FloatFFT();
		
		float[][] delay = new float[spectrum.length][nF << 1];
		
		Channel[] allChannels = new Channel[instrument.storeChannels];		
		for(Channel channel : instrument) allChannels[channel.getFixedIndex()] = channel;
		
		
		for(int c=spectrum.length; --c >= 0; ) {
			for(int f=nF; --f >= 0; ) C.set(f, spectrum[c][f]);
			fft.amplitude2Real(C.getData());
			System.arraycopy(C.getData(), 0, delay[c], 0, nF << 1);		
		}
	
		
		String fileName = CRUSH.workPath + File.separator + getFileID() + "." + name + "-coupling.delay";
		PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
		
		out.println(this.getASCIIHeader());
		out.println();
		
		int n = nF << 1;
				
		final int nc = instrument.getPixelCount();
		
		for(int t=0; t<n; t++) {
			out.print(Util.f5.format(t * instrument.samplingInterval));
			for(int c=0; c<nc; c++) {
				Channel channel = allChannels[c];
				if(channel == null) out.print("\t---   ");					
				else out.print("\t" + Util.e3.format(delay[channel.index][t]));
			}
			out.println();
		}
		
		out.close();
		
		notify("Written " + fileName);
	}
	
	
	Complex[][] getCouplingSpectrum(final Signal signal, int windowSize) throws Exception {
		final double[] w = WindowFunction.getHann(windowSize);
		
		final Mode mode = signal.getMode();
		final ChannelGroup<? extends Channel> channels = mode.getChannels();
		final float[] gain = mode.getGains();
		
		final Complex[][] C = new Complex[mode.size()][];
			
		new CRUSH.Fork<Void>(mode.size(), getThreadCount()) {

			@Override
			protected void processIndex(int k) {
				Channel channel = channels.get(k);
				C[k] = getCouplingSpectrum(signal, channel, gain[k], w);
				if(channel.isFlagged()) for(Complex z : C[k]) z.zero();
			}

		}.process();
		
		return C;
	}
	
	
	Complex[] getCouplingSpectrum(Signal signal, Channel channel, float gain, double[] w) {
		int windowSize = w.length;
		windowSize = ExtraMath.pow2ceil(windowSize);
		int step = windowSize >>> 1;
		int nt = size();
		int nF = windowSize >>> 1;
		
		Complex[] c = new Complex[nF];
		for(int i=nF; --i >= 0; ) c[i] = new Complex();
		
		FauxComplexArray.Float D = new FauxComplexArray.Float(nF);
		FauxComplexArray.Float S = new FauxComplexArray.Float(nF);

		float[] d = D.getData();
		float[] s = S.getData();
		
		Complex dComponent = new Complex();
		Complex sComponent = new Complex();
		
		FloatFFT fft = new FloatFFT();
		double norm = 0.0;
				
 		for(int from = 0; from < nt; from+=step) {
			int to = from + windowSize;
			if(to > nt) break;
			
			Arrays.fill(d, 0.0F);
			Arrays.fill(s, 0.0F);
			
			for(int k=windowSize; --k >= 0; ) {
				Frame exposure = get(from + k);
				if(exposure == null) continue;
				if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
				
				s[k] = (float) w[k] * signal.valueAt(exposure);
				d[k] = (float) (w[k] * exposure.data[channel.index] + gain * s[k]);
			}
			
			fft.real2Amplitude(d);
			fft.real2Amplitude(s);
			
			for(int f=nF; --f >= 0; ) {
				D.get(f, dComponent);
				S.get(f, sComponent);
				norm += sComponent.asquare();
				
				sComponent.conjugate();
				dComponent.multiplyBy(sComponent);
				c[f].add(dComponent);
			}	
		}

		if(norm > 0.0) norm  = 1.0 / norm; 
		
		for(int i=nF; --i >= 0; ) c[i].scale(norm);
		
		return c;
	}
	
	
	public void shiftData(double dt) {
		shiftData((int) Math.round(dt / instrument.samplingInterval));
	}
	
	public void shiftData(int nFrames) {
		if(nFrames == 0) return;
		
		info("Shifting data by " + nFrames + " frames.");
		
		if(nFrames > 0) {
			if(nFrames > size()) nFrames = size();
			for(int t=size(); --t >= nFrames; ) {
			    FrameType to = get(t);
			    FrameType from = get (t - nFrames);
			    if(to == null) continue;
			    if(from == null) continue;
			    to.cloneReadout(from);
			}
			for(int t=nFrames; --t >= 0; ) set(t, null);	
		}
		else {
			nFrames *= -1;
			for(int t=nFrames; t<size(); t++) {
			    FrameType from = get(t);
                FrameType to = get (t - nFrames);
                if(to == null) continue;
                if(from == null) continue;
			    to.cloneReadout(from);
			}
			for(int t=size()-nFrames; t<size(); t++) set(t, null);
		}
	}
	
	@Override
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
			Chopper chopper = this instanceof Chopping ? ((Chopping) this).getChopper() : null;
			if(chopper == null) return "---";
			else return  Util.defaultFormat(chopper.frequency / Unit.Hz, f);
		}
		else if(name.equals("chopthrow")) {
			Chopper chopper = this instanceof Chopping ? ((Chopping) this).getChopper() : null;
			if(chopper == null) return "---";
			else return  Util.defaultFormat(2.0 * chopper.amplitude / instrument.getSizeUnitValue(), f);
		}
		else if(name.equals("chopeff")) {
			Chopper chopper = this instanceof Chopping ? ((Chopping) this).getChopper() : null;
			if(chopper == null) return "---";
			else return  Util.defaultFormat(chopper.efficiency, f);
		}
		else return instrument.getFormattedEntry(name, formatSpec);
	}
	
	@Override
	public String toString() { return "Integration " + getFullID("|"); }
	
	

	
	public abstract class Fork<ReturnType> extends CRUSH.Fork<ReturnType> {
		public Fork() { super(size(), getThreadCount()); }
		
		@Override
		public final void processIndex(int index) { 
			FrameType exposure = get(index);
			if(exposure != null) process(exposure);
		}
		
		protected abstract void process(FrameType frame);
	}

	
	public abstract class BlockFork<ReturnType> extends CRUSH.Fork<ReturnType> {
		private int blocksize;

		public BlockFork(int blocksize) { 
			super(ExtraMath.roundupRatio(size(), Math.max(1, blocksize)), getThreadCount());
			this.blocksize = Math.max(1, blocksize); 
		}

		@Override
		protected final void processIndex(int index) {
			int from = blocksize * index;
			process(from, Math.min(from + blocksize, size()));
		}

		protected abstract void process(int from, int to);
		
		public final int getBlockSize() { return blocksize; }
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

		
	
	
	public int pow2Size() { return ExtraMath.pow2ceil(size()); }

	public int[] getInts() { return recycler.getIntArray(pow2Size()); }
	
	public float[] getFloats() { return recycler.getFloatArray(pow2Size()); }
	
	public double[] getDoubles() { return recycler.getDoubleArray(pow2Size()); }
	
	public DataPoint[] getDataPoints() { return recycler.getDataPointArray(pow2Size()); }
	
	
	
	public static void recycle(int[] array) { recycler.recycle(array); }
	
	public static void recycle(float[] array) { recycler.recycle(array); }
	
	public static void recycle(double[] array) { recycler.recycle(array); }
	
	public static void recycle(WeightedPoint[] array) { recycler.recycle(array); }
	
	public static void setRecyclerCapacity(int size) { recycler.setSize(size); }
	
	public static void clearRecycler() { recycler.clear(); }
	
	
	
	private static ArrayRecycler recycler = new ArrayRecycler();
	
	
}
