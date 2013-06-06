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
// Copyright (c) 2010 Attila Kovacs 

package crush.polka;

import crush.*;
import crush.apex.APEXArrayScan;
import crush.filters.Filter;
import crush.polarization.*;
import crush.laboca.*;

import java.text.NumberFormat;
import java.util.*;

import kovacs.util.*;
import kovacs.util.data.WeightedPoint;
import kovacs.util.text.TableFormatter;

public class PolKaSubscan extends LabocaSubscan implements Modulated, Purifiable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -901946410688579472L;
	double meanTimeStampDelay = Double.NaN;
	boolean hasTimeStamps = true;
	
	public PolKaSubscan(APEXArrayScan<Laboca, LabocaSubscan> parent) {
		super(parent);
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("wpdelay")) return Util.defaultFormat(meanTimeStampDelay / Unit.ms, f);
		else if(name.equals("wpok")) return Boolean.toString(hasTimeStamps);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
	@Override
	public Filter getFilter(String name) {
		name = name.toLowerCase();
		if(name.equals("hwp")) return new HWPFilter(this, filter.getData());
		else return super.getFilter(name);
	}

	
	public int getPeriod(int mode) {
		return (int)Math.round(instrument.samplingInterval / (4.0*getWaveplateFrequency()));
	}
	
	public double getWaveplateFrequency() {
		return ((PolKa) instrument).waveplateFrequency;
	}
	
	/*
	@Override
	public double getCrossingTime(double sourceSize) {		
		return 1.0 / (1.0/super.getCrossingTime() + 4.0 * getWaveplateFrequency());
	}
	*/
	
	@Override
	public void validate() {
		PolKa polka = (PolKa) instrument;
		
		if(!polka.hasAnalyzer) {
			super.validate();
			return;
		}
		
		reindex();
		
		for(Frame exposure : this) if(exposure != null) ((PolKaFrame) exposure).loadWaveplateData();
		
		// TODO could it be that MJD options not yet set here?
		
		if(!isWaveplateValid()) { 
			hasTimeStamps = false;
			System.err.println("     WARNING! Invalid waveplate data. Will attempt workaround...");
			calcMeanWavePlateFrequency();
			Channel channel = hasOption("waveplate.tpchannel") ?
					instrument.getFixedIndexLookup().get(option("waveplate.tpchannel").getInt()) :
					instrument.referencePixel;
			setTPPhases(channel);
		}
		else if(hasOption("waveplate.regulate")) regularAngles();
		else if(hasOption("waveplate.frequency")) {
			System.err.println("   Setting waveplate frequency " + Util.f3.format(getWaveplateFrequency()) + " Hz.");
			System.err.println("   WARNING! Phaseless polarization (i.e. uncalibrated angles).");
		}
		else {
			try { calcMeanWavePlateFrequency(); }
			catch(IllegalStateException e) { System.err.println("   WARNING! " + e.getMessage() + " Using defaults."); }
		}

		if(hasOption("waveplate.tpchar")) {
			trim();
			
			Channel channel = hasOption("waveplate.tpchannel") ?
					instrument.getFixedIndexLookup().get(option("waveplate.tpchannel").getInt()) :
					instrument.referencePixel;
					
			measureTPPhases(channel);
			setTPPhases(channel);
		}

		super.validate();		
		
		if(hasOption("purify")) removeTPModulation();
	}
	
	public void calcMeanWavePlateFrequency() throws IllegalStateException {
		PolKa polka = (PolKa) instrument;
		
		if(polka.frequencyChannel == null) 
			throw new IllegalStateException("WARNING! Frequency channel undefined.");

		double sum = 0.0;
		int n=0;
		for(Frame frame : this) if(frame != null) {
			sum += ((PolKaFrame) frame).waveplateFrequency;
			n++;
		}
		if(n < 1) throw new IllegalStateException("No valid frames with waveplate data");	
		if(sum == 0.0) throw new IllegalStateException("All zeros in waveplate frequency channel.");
		if(Double.isNaN(sum)) throw new IllegalStateException("No waveplate frequency data in channel.");

		polka.waveplateFrequency = sum / n;
		sum = 0.0;
		for(Frame frame : this) if(frame != null) {
			double dev = ((PolKaFrame) frame).waveplateFrequency - polka.waveplateFrequency;
			sum += dev*dev;				
		}
		double jitter = Math.sqrt(sum / (n-1)) / polka.waveplateFrequency;
		if(!hasOption("waveplate.jitter")) polka.jitter = jitter;
		
		System.err.println("   Measured waveplate frequency is " + Util.f3.format(polka.waveplateFrequency) + " Hz (" + Util.f1.format(100.0*jitter) + "% jitter).");
	}
	
	
	WeightedPoint[] tpWaveform, dw;
	double dAngle;
	
	public void removeTPModulation() {
		PolKa polka = (PolKa) instrument;
		
		// If the waveplate is not rotating, then there is nothing to do...
		if(!(polka.waveplateFrequency > 0.0)) return;
		
		if(tpWaveform == null) {
			double oversample = hasOption("waveplate.oversample") ? option("waveplate.oversample").getDouble() : 1.0;
			int n = (int)Math.round(oversample / (instrument.samplingInterval * polka.waveplateFrequency));
		
			tpWaveform = new WeightedPoint[n];
			dw = new WeightedPoint[n];
			for(int i=dw.length; --i >= 0; ) {
				tpWaveform[i] = new WeightedPoint();
				dw[i] = new WeightedPoint();
			}
				
			dAngle = Util.twoPi / n;
		}
		else for(int i=dw.length; --i >= 0; ) dw[i].noData();
		
		comments += "P(" + dw.length + ") ";
		
		ChannelGroup<?> channels = instrument.getObservingChannels();
		Dependents parms = getDependents("tpmod");
		parms.clear(channels, 0, size());

		
		for(Channel channel : channels) {
			for(int i=dw.length; --i >= 0; ) dw[i].noData();
			final int c = channel.index;
			
			for(LabocaFrame exposure : this) if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {
				if(exposure.sampleFlag[c] != 0) continue;
				
				final double normAngle = Math.IEEEremainder(((PolKaFrame) exposure).waveplateAngle, Util.twoPi) + Math.PI;
				final WeightedPoint point = dw[(int)Math.floor(normAngle / dAngle)];
				
				point.add(exposure.relativeWeight * exposure.data[c]);
				point.addWeight(exposure.relativeWeight);
			}
			
			for(int i=dw.length; --i >= 0; ) {
				final WeightedPoint point = dw[i];
				if(point.weight() > 0.0) {
					point.scaleValue(1.0 / point.weight());
					tpWaveform[i].add(point.value());
					tpWaveform[i].setWeight(point.weight());
					parms.add(channel, 1.0);
				}
			}
			
			for(LabocaFrame exposure : this) if(exposure != null) {
				final double normAngle = Math.IEEEremainder(((PolKaFrame) exposure).waveplateAngle, Util.twoPi) + Math.PI;
				final WeightedPoint point = dw[(int)Math.floor(normAngle / dAngle)];
				
				exposure.data[c] -= point.value();
				if(point.weight() > 0.0) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] != 0)
					parms.add(channel, exposure.relativeWeight / point.weight());
			}
		}
		
		
		parms.apply(channels, 0, size());
	}

	
	public void getWaveForm(int mode, int index, float[] waveform) {	
		
		if(mode == PolarModulation.N) Arrays.fill(waveform, 1.0F);
		else if(mode == PolarModulation.Q) {
			int nt = Math.min(waveform.length, size() - index);
			for(int blockt=0; blockt<nt; blockt++, index++) { 
				PolKaFrame exposure = (PolKaFrame) get(index);
				if(exposure != null) waveform[blockt] = exposure.Q;
				else waveform[blockt] = Float.NaN;
			}
		}
		else if(mode == PolarModulation.U) {
			int nt = Math.min(waveform.length, size() - index);
			for(int blockt=0; blockt<nt; blockt++, index++) {
				PolKaFrame exposure = (PolKaFrame) get(index);
				if(exposure != null) waveform[blockt] = exposure.U;
				else waveform[blockt] = Float.NaN;
			}
		}
		else throw new IllegalArgumentException("Mode " + mode + " is undefined for " + getClass().getSimpleName());
	}
	
	public void purify() {
		//if(!hasOption("filter.hwp")) 
			if(hasOption("purify")) removeTPModulation();
	}

	@Override
	public LabocaFrame getFrameInstance() {
		return new PolKaFrame((PolKaScan) scan);
	}
		
	@Override
	public Vector2D getTauCoefficients(String id) {
		if(id.equals(instrument.getName())) return getTauCoefficients("laboca");
		else return super.getTauCoefficients(id);
	}
		
	
	public void regularAngles() throws IllegalStateException {	
		PolKa polka = (PolKa) instrument;
		Channel offsetChannel = polka.offsetChannel;
		if(offsetChannel == null) return;
		int c = offsetChannel.index;
		
		System.err.print("   Fixing waveplate: ");
		
		ArrayList<Double> crossings = new ArrayList<Double>();
		double lastCrossing = Double.NaN;
		double tolerance = 10 * Unit.ms / Unit.day;
		
		for(int t=0; t<size(); t++) {
			Frame exposure = get(t);
			if(exposure == null) continue;
			double MJD = exposure.MJD - exposure.data[c] * Unit.ms / Unit.day;
			if(!(Math.abs(MJD - lastCrossing) < tolerance)) {
				lastCrossing = MJD;
				crossings.add(MJD);
			}	
		}
		Collections.sort(crossings);
		System.err.print(crossings.size() + " crossings, ");
		
		if(hasOption("waveplate.despike")) {
			double level = 6.0;
			try { level = option("waveplate.despike").getDouble(); }
			catch(NumberFormatException e) {}
			
			try { regress(crossings, level); }
			// If the errors are too large, try a second round
			catch(IllegalStateException e) {
				try { regress(crossings, level); }
				catch(IllegalStateException e2) {}
			}
		}
		
		Vector2D coeffs = regress(crossings, Double.NaN);
		
		setMinDelay(crossings, coeffs);
		
		double MJD0 = coeffs.getX();
		double dMJDdn = coeffs.getY();
		double freq = 1.0 / (dMJDdn * Unit.day);
		System.err.println("f = " + Util.f3.format(freq) + " Hz.");
		
		for(Frame exposure : this) if(exposure != null) {
			PolKaFrame frame = (PolKaFrame) exposure;
			frame.waveplateAngle = Util.twoPi * Math.IEEEremainder((frame.MJD - MJD0) / dMJDdn, 1.0);
			frame.waveplateFrequency = freq;
		}
		
		polka.waveplateFrequency = freq;
		
	}
	
	public Vector2D regress(ArrayList<Double> crossings, double despike) throws IllegalStateException {
			
		double sumx = 0.0, sumy = 0.0, sumxy = 0.0, sumxx = 0.0, sumyy = 0.0;
		int n = crossings.size();
		
		double MJD0 = crossings.get(0);
		
		for(int i=n; --i >= 0; ) {
			final double y = crossings.get(i) - MJD0;
			if(Double.isNaN(y)) continue;
			sumx += i;
			sumy += y;
			sumxy += i * y;
			sumxx += i * i;
			sumyy += y * y;
		}
		
		double dMJDdn = (n * sumxy - sumx * sumy) / (n * sumxx - sumx * sumx);
		MJD0 += (sumy - dMJDdn * sumx) / n;
		double sigma2 = (n * sumyy - sumy*sumy - dMJDdn*dMJDdn*(n*sumxx - sumx*sumx)) / (n * (n-2));
		double sigma = Math.sqrt(sigma2);
		
		//System.err.println("### sigma = " + Util.f3.format(sigma * Unit.day / Unit.ms) + " ms.");
		
		if(!Double.isNaN(despike) && despike > 0.0) {
			int removed = 0;
			for(int i=crossings.size(); --i >= 0; ) {
				double MJD = crossings.get(i);
				double dMJD = MJD - (MJD0 + dMJDdn * i);
				if(Double.isNaN(MJD)) continue;
				if(Math.abs(dMJD) > despike * sigma) {
					crossings.set(i, Double.NaN);
					removed++;
				}
			}
			n -= removed;
			System.err.print(removed + " bad, ");
		}
		
		// If the rms error in the timings is greater than 3.6 degrees
		// then consider it bad...
		if(Math.sqrt(sigma2 / (n-1)) > 0.01 * dMJDdn) throw new IllegalStateException("Bad waveplate timings!");
		
		return new Vector2D(MJD0, dMJDdn);
	}
	
	public void setMinDelay(ArrayList<Double> crossings, Vector2D coeffs) {
		double MJD0 = coeffs.getX();
		double dMJDdn = coeffs.getY();
		double mindMJD = 0.0;
		for(int i=crossings.size(); --i >= 0; ) {
			double dMJD = crossings.get(i) - (MJD0 + dMJDdn * i);
			if(dMJD < mindMJD) mindMJD = dMJD;
		}
		
		meanTimeStampDelay = -mindMJD * Unit.day;
		
		System.err.print("dt = " + Util.f1.format(meanTimeStampDelay / Unit.ms) + "ms, ");
		
		coeffs.addX(mindMJD);
	}
	
	// Check the waveplate for the bridge error during 2011 Dec 6-8, when 
	// angles were written as zero...
	public boolean isWaveplateValid() {
		for(Frame exposure : this) if(exposure != null) if(((PolKaFrame) exposure).waveplateAngle != 0.0) return true;
		return false;		
	}
	
	public void measureTPPhases(Channel channel) {
		PolKa polka = (PolKa) instrument;
		PolKaFrame firstFrame = (PolKaFrame) get(0);
		
		//if(firstFrame.waveplateAngle == 0.0) System.err.println("### WARNING zero waveplate angle.");
		
		double phase1 = getMeanTPPhase(channel, polka.waveplateFrequency) + firstFrame.waveplateAngle;
		double phase2 = getMeanTPPhase(channel, 2.0 * polka.waveplateFrequency) + 2.0 * firstFrame.waveplateAngle;
		double phase4 = getMeanTPPhase(channel, 4.0 * polka.waveplateFrequency) + 4.0 * firstFrame.waveplateAngle;
		
		System.err.println("   Measured TP Phases: "
				+ Util.f1.format(Math.IEEEremainder(phase1, Util.twoPi) / Unit.deg) + ", "
				+ Util.f1.format(Math.IEEEremainder(phase2, Util.twoPi) / Unit.deg) + ", "
 				+ Util.f1.format(Math.IEEEremainder(phase4, Util.twoPi) / Unit.deg) + " deg"
				);
	}
	
	public void setTPPhases(Channel channel) {
		System.err.println("   Reconstructing waveplate angles from TP modulation.");
		
		PolKa polka = (PolKa) instrument;
			
		int harmonic = hasOption("waveplate.tpharmonic") ? option("waveplate.tpharmonic").getInt() : 2;
		
		//if(firstFrame.waveplateAngle == 0.0) System.err.println("### WARNING zero waveplate angle.");
		String analyzer = "analyzer." + (polka.isVertical ? "v" : "h") + ".";
			
		double delta = option(analyzer + "phase").getDouble() * Unit.deg; 
		double phase = getMeanTPPhase(channel, harmonic * polka.waveplateFrequency);
		double alpha = (delta - phase) / harmonic;
		
		System.err.println("   ---> Using phase " + Util.f1.format(delta / Unit.deg) + " deg for pixel " + channel.getFixedIndex() + ".");
		
		if(hasOption("waveplate.tpchar")) {
			PolKaFrame firstFrame = (PolKaFrame) get(0);
			System.err.println("   Reconstruction error: " + 
					Util.f1.format(Math.IEEEremainder(alpha - firstFrame.waveplateAngle, Util.twoPi) / Unit.deg) +
					" deg.");
		}
		
		final double w = Util.twoPi * polka.waveplateFrequency * instrument.integrationTime;
		for(Frame exposure : this) if(exposure != null) {
			PolKaFrame frame = (PolKaFrame) exposure;
			frame.waveplateAngle = Math.IEEEremainder(alpha + w * frame.index, Util.twoPi);
		}
		
	}
	
	// Calculate the average TP phases at a given frequency...
	public double getMeanTPPhase(Channel channel, double freq) {		
		final int c = channel.index;
		final double w = Util.twoPi * freq * instrument.integrationTime;
		double sumc = 0.0, sums = 0.0;
		int n = 0;
		
		for(Frame exposure : this) if(exposure != null) {
			final double value = exposure.data[c];
			final double theta = w * exposure.index;
			sumc += Math.cos(theta) * value;
			sums += Math.sin(theta) * value;
			n++;
		}
		sumc *= 2.0/n;
		sums *= 2.0/n;
		
		System.err.println("   ---> TP: " + Util.e3.format(Math.hypot(sums, sumc))
				+ " @ " + Util.f3.format(freq) + "Hz");
		
		return Math.atan2(sums, sumc);
	}
	
}

