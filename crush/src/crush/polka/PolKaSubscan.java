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
import crush.polarization.*;
import crush.laboca.*;
import util.*;
import util.data.FFT;

import java.util.*;

public class PolKaSubscan extends LabocaSubscan implements Modulated, Biased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -901946410688579472L;

	double[] filterI;
	double wavePlateFrequency, jitter;
	
	
	public PolKaSubscan(APEXArrayScan<Laboca, LabocaSubscan> parent) {
		super(parent);
	}

	
	public int getPeriod(int mode) {
		return (int)Math.round(instrument.samplingInterval / (4.0*wavePlateFrequency));
	}
	
	@Override
	public void validate() {
		// TODO Apply the time-stamps to the frames
		timeStamp();
		
		super.validate();
		
		getWavePlateFrequency();
		
		prepareFilter();
		
	}
	
	public void getWavePlateFrequency() {
		PolKa polka = (PolKa) instrument;
		
		if(polka.frequencyChannel != null) {
			double sum = 0.0;
			int n=0;
			for(Frame frame : this) if(frame != null) {
				sum += ((PolKaFrame) frame).wavePlateFrequency;
				n++;
			}
			wavePlateFrequency = sum / n;
			sum = 0.0;
			for(Frame frame : this) if(frame != null) {
				double dev = ((PolKaFrame) frame).wavePlateFrequency - wavePlateFrequency;
				sum += dev*dev;				
			}
			//jitter = Math.sqrt(sum / (n-1)) / wavePlateFrequency;
			jitter = polka.jitter;
			
			System.err.println("   Waveplate frequency is " + Util.f3.format(wavePlateFrequency) + "Hz (" + Util.f1.format(100.0*jitter) + "% jitter)");
		}
		else {
			wavePlateFrequency = polka.wavePlateFrequency;
			jitter = polka.jitter;			
		}
	}

	
	public void prepareFilter() {
		PolKa polka = (PolKa) instrument;
		//int filterFrames = hasOption("drifts") ? getFilterFrames(option("drifts").getValue(), 10.0 * Unit.s) : size();
		int filterFrames = size();
		filterI = new double[FFT.getPaddedSize(filterFrames)];
		Arrays.fill(filterI, 1.0);
		double fundamental = polka.wavePlateFrequency / (filterI.length * instrument.samplingInterval);
		int harmonics = (int) (1.0 / (2.0 * instrument.samplingInterval * polka.wavePlateFrequency));
		int width = (int) Math.ceil(1.0 * jitter * filterI.length);
		
		System.err.println("   Preparing filter for spurious polarization signal (" + harmonics + " harmonics x " + width + " channels).");
		
		for(int i=1; i<harmonics; i++) {
			int f = (int)Math.round(i * fundamental);
			int fromf = Math.max(0, f - width/2);
			int tof = Math.min(filterI.length-1, fromf + width);
			
			for(int k=fromf; k<=tof; k++) filterI[k] = 0.0;
		}
		
	}
	
	public void timeStamp() {
		PolKaTimeStamps stamps = ((PolKa) scan.instrument).timeStamps;

		if(stamps == null) {
			System.err.println("   WARNING! Wave plate timings not defined. Phaseless polarization.");
			final double MJD0 = 54000.0;
			for(Frame frame : this) if(frame != null) ((PolKaFrame) frame).MJD0 = MJD0;
			return;
		}
		
		int index = stamps.higherIndex(get(0).MJD);
		double MJD0 = stamps.get(index);
		double expire = MJD0;
		for(int t=0; t<size(); t++) {
			PolKaFrame frame = (PolKaFrame) get(t);
			if(frame != null) while(frame.MJD > expire) {
				MJD0 = stamps.get(++index);
				expire = index < size() - 1 ? MJD0 : Double.POSITIVE_INFINITY; 
			}
			frame.MJD0 = MJD0;
		}
	}
	
	public void getWaveForm(int mode, int index, float[] waveform) {	
		
		if(mode == PolarizationMode.I) Arrays.fill(waveform, 1.0F);
		else if(mode == PolarizationMode.Q) {
			int nt = Math.min(waveform.length, size() - index);
			for(int blockt=0; blockt<nt; blockt++, index++) { 
				PolKaFrame exposure = (PolKaFrame) get(index);
				if(exposure != null) waveform[blockt] = exposure.Q;
				else waveform[blockt] = Float.NaN;
			}
		}
		else if(mode == PolarizationMode.U) {
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

		filter(instrument.getObservingChannels(), filterI);
	}


	@Override
	public LabocaFrame getFrameInstance() {
		return new PolKaFrame((PolKaScan) scan);
	}
	
	


}

