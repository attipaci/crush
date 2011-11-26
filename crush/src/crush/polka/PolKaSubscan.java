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
// Copyright (c) 2010 Attila Kovacs 

package crush.polka;

import crush.*;
import crush.apex.APEXArrayScan;
import crush.filters.Filter;
import crush.polarization.*;
import crush.laboca.*;
import util.*;
import util.data.WeightedPoint;
import util.text.TableFormatter;

import java.text.NumberFormat;
import java.util.*;

public class PolKaSubscan extends LabocaSubscan implements Modulated, Biased, Purifiable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -901946410688579472L;

	public double[] phi;
	
	public PolKaSubscan(APEXArrayScan<Laboca, LabocaSubscan> parent) {
		super(parent);
	}
	
	@Override
	public Filter getFilter(String name) {
		name = name.toLowerCase();
		if(name.equals("hwp")) return new HWPFilter(this, filter.getData());
		else return super.getFilter(name);
	}

	public void regularAngles() {
		System.err.println("   WARNING! Phaseless polarization (i.e. uncalibrated angles).");
		
		double MJD0 = 54000.0;
		double f0 = getWavePlateFrequency();
		
		for(LabocaFrame frame : this) if(frame != null) {
			((PolKaFrame) frame).wavePlateAngle = 2.0 * Math.PI * (frame.MJD - MJD0) * Unit.day * f0;
		}
	}
		
	public int getPeriod(int mode) {
		return (int)Math.round(instrument.samplingInterval / (4.0*getWavePlateFrequency()));
	}
	
	public double getWavePlateFrequency() {
		return ((PolKa) instrument).wavePlateFrequency;
	}
	
	@Override
	public void validate() {	
		super.validate();
		
		// If the frequency is set manually, then calculate angles based on it...
		if(hasOption("waveplate.frequency")) regularAngles();
		else {
			try { calcMeanWavePlateFrequency(); }
			catch(IllegalStateException e) { System.err.println("   WARNING! " + e.getMessage() + " Using defaults."); }
		}

		System.err.println("   Using waveplate frequency " + Util.f3.format(getWavePlateFrequency()) + " Hz.");
		
		removeTPModulation();
		
	}
	
	public void calcMeanWavePlateFrequency() throws IllegalStateException {
		PolKa polka = (PolKa) instrument;
		
		if(polka.frequencyChannel == null) 
			throw new IllegalStateException("WARNING! Frequency channel undefined.");

		double sum = 0.0;
		int n=0;
		for(Frame frame : this) if(frame != null) {
			sum += ((PolKaFrame) frame).wavePlateFrequency;
			n++;
		}
		if(n < 1) throw new IllegalStateException("No valid frames with waveplate data");	
		if(sum == 0.0) throw new IllegalStateException("All zeros in waveplate frequency channel.");
		if(Double.isNaN(sum)) throw new IllegalStateException("No waveplate frequency data in channel.");

		polka.wavePlateFrequency = sum / n;
		sum = 0.0;
		for(Frame frame : this) if(frame != null) {
			double dev = ((PolKaFrame) frame).wavePlateFrequency - polka.wavePlateFrequency;
			sum += dev*dev;				
		}
		polka.jitter = Math.sqrt(sum / (n-1)) / polka.wavePlateFrequency;

		System.err.println("   Measured waveplate frequency is " + Util.f3.format(polka.wavePlateFrequency) + " Hz (" + Util.f1.format(100.0*polka.jitter) + "% jitter).");
	}
	
	public void removeTPModulation() {
		PolKa polka = (PolKa) instrument;
		
		double oversample = hasOption("waveplate.oversample") ? option("waveplate.oversample").getDouble() : 1.0;
		int n = (int)Math.round(oversample / (instrument.samplingInterval * polka.wavePlateFrequency));
		
		WeightedPoint[] waveform = new WeightedPoint[n];
		for(int i=waveform.length; --i >= 0; ) waveform[i] = new WeightedPoint();
		double dAngle = 2.0 * Math.PI / n;
		
		comments += "P(" + n + ") ";
		
		ChannelGroup<?> channels = instrument.getObservingChannels();
		Dependents parms = getDependents("tpmod");
		parms.clear(channels, 0, size());
		
		for(Channel channel : channels) {
			for(int i=waveform.length; --i >= 0; ) waveform[i].noData();
			final int c = channel.index;
			
			for(LabocaFrame exposure : this) if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {
				if(exposure.sampleFlag[c] != 0) continue;
				
				final double normAngle = Math.IEEEremainder(((PolKaFrame) exposure).wavePlateAngle, 2.0 * Math.PI) + Math.PI;
				final WeightedPoint point = waveform[(int)Math.floor(normAngle / dAngle)];
				
				point.value += exposure.relativeWeight * exposure.data[c];
				point.weight += exposure.relativeWeight;
			}
			
			for(int i=waveform.length; --i >= 0; ) {
				final WeightedPoint point = waveform[i];
				if(point.weight > 0.0) {
					point.value /= point.weight;				
					parms.add(channel, 1.0);
				}
			}
			
			for(LabocaFrame exposure : this) if(exposure != null) {
				final double normAngle = Math.IEEEremainder(((PolKaFrame) exposure).wavePlateAngle, 2.0 * Math.PI) + Math.PI;
				final WeightedPoint point = waveform[(int)Math.floor(normAngle / dAngle)];
				
				exposure.data[c] -= point.value;
				if(point.weight > 0.0) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] != 0)
					parms.add(channel, exposure.relativeWeight / point.weight);
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


	public void removeBias(double[] dG) {	
		comments += "b";
		
		final PolKa polka = (PolKa) instrument;
		for(LabocaFrame exposure : this) if(exposure != null) {

			final PolKaFrame frame = (PolKaFrame) exposure;
			for(Channel channel : instrument)
				frame.data[channel.index] -= dG[channel.index] * (frame.Qh * polka.Q0 + frame.Uh * polka.U0);
		}
		
	}
	
	public void purify() {
		removeTPModulation();
	}

	@Override
	public LabocaFrame getFrameInstance() {
		return new PolKaFrame((PolKaScan) scan);
	}
		
	@Override
	public Vector2D getTauCoefficients(String id) {
		if(id.equals(instrument.name)) return getTauCoefficients("laboca");
		else return super.getTauCoefficients(id);
	}
		
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
		PolKa polka = (PolKa) instrument;
		
		if(name.equals("wpfreq")) return Util.defaultFormat(polka.wavePlateFrequency / Unit.Hz, f);
		else if(name.equals("wpjitter")) return Util.defaultFormat(polka.jitter, f);
		else return super.getFormattedEntry(name, formatSpec);
	}


}

