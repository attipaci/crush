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


package crush.mako;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;

import java.util.StringTokenizer;

import util.Configurator;
import util.Range;
import util.Unit;
import util.Util;
import util.data.AmoebaMinimizer;
import util.data.Statistics;


public class ToneIdentifier extends ArrayList<ResonanceID> implements Cloneable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3011775640230135691L;
	
	public double Thot = 273.16 * Unit.K;	// Temperature at which 'hot' ids are derived...
	public Range TRange = new Range(-1000.0, 1000.0 * Unit.K);
	public int attempts = 100;
	public double rchi;
	public double maxDeviation = 3.0;
	public double power = 1.0;
	
	public ToneIdentifier() {}
	
	public ToneIdentifier(Configurator options) throws IOException {
		this(options.getValue());
		if(options.isConfigured("uniform")) uniformize();
		if(options.isConfigured("power")) power = options.get("power").getDouble();
		if(options.isConfigured("trange")) {
			TRange = options.get("trange").getRange();
			TRange.scale(Unit.K);
		}
		if(options.isConfigured("max")) maxDeviation = options.get("max").getDouble();
	}
	
	public ToneIdentifier(String fileName) throws IOException {
		this();
		read(fileName);
	}
	
	public void read(String fileSpec) throws IOException {
		System.err.println(" Loading resonance identifications from " + fileSpec);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(Util.getSystemPath(fileSpec))));
		String line = null;

		clear();
		
		// Assuming 12 C for the hot load...
		// and 195 K for the cold
		double dT = Thot - 75.0 * Unit.K;
		
		int index = 1;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line, ", \t");
			ResonanceID id = new ResonanceID(index++);
			
			double fcold = Double.parseDouble(tokens.nextToken());
			id.freq = Double.parseDouble(tokens.nextToken());
			id.delta = (id.freq - fcold) / dT; 
			id.T0 = Thot;
			
			add(id);
		}
		
		in.close();
		
		Collections.sort(this);
		
		System.err.println(" Got IDs for " + size() + " resonances.");
	}
	
	public void uniformize() {
		double[] deltas = new double[size()];
		for(int i=size(); --i >= 0; ) {
			ResonanceID id = get(i);
			deltas[i] = id.delta / id.freq;
		}
		
		double ave = Statistics.median(deltas);
		System.err.println(" Median hot/cold response is " + Util.s3.format(1e6 * ave) + " ppm / K");
		
		for(int i=size(); --i >= 0; ) {
			ResonanceID id = get(i);
			id.delta = ave * id.freq;
		}
	}
	
	public double match(ResonanceList channels, double guessT) {
		double T = fit(channels, guessT);
		assign(channels, T);
		return T;
	}
	
	protected double fit(final ResonanceList channels, double guessT) {
		channels.sort();
			
		AmoebaMinimizer opt = new AmoebaMinimizer() {

			@Override
			public double evaluate(double[] tryparms) {
				double T = tryparms[0];
				double chi2 = 0.0;
				
				for(ResonanceID id : ToneIdentifier.this) {
					double fExp = id.freq + (T - Thot) * id.delta;
					double dev = (channels.getNearest(fExp).toneFrequency - fExp) / fExp;
					chi2 += Math.pow(Math.abs(dev), power);
				}
				
				if(T < TRange.min()) chi2 *= Math.exp(TRange.min() - T);
				else if(T > TRange.max()) chi2 *= Math.exp(T - TRange.max());
				
				return chi2;
				
			}	
		};
		
		opt.init(new double[] { guessT });
		opt.setStartSize(new double[] { 0.2 * TRange.span() });
		opt.precision = 1e-10;
		opt.verbose = false;
		opt.minimize(attempts);
		
		//rchi = Math.sqrt(opt.getChi2() / size());
		rchi = Math.pow(opt.getChi2() / size(), 1.0 / power);
		//alpha = opt.getFitParameters()[0];
		
		double T = opt.getFitParameters()[0];
		
		System.err.println("   Tone assignment rms = " + Util.s3.format(1e6 * rchi) + " ppm.");
		System.err.println("   --> T(id) = " + Util.s4.format(T / Unit.K) + " K.");
		
		return T;
	}
	
	protected void assign(ResonanceList tones, double T) {
		for(MakoPixel pixel : tones) {
			pixel.id = null;
			pixel.flag(MakoPixel.FLAG_NOTONEID);
		}
		int n = assign(tones, T, 5);
		System.err.println("   Identified " + n + " resonances.");
		
		for(MakoPixel pixel : tones) if(pixel.id != null) pixel.unflag(MakoPixel.FLAG_NOTONEID);
	}
	
	private int assign(ResonanceList tones, double T, int rounds) {
		if(rounds == 0) return 0;
		if(tones.isEmpty()) return 0; 
	
		int ids = 0;
		
		final double maxShift = rchi * maxDeviation;
		
		for(ResonanceID id : this) {
			double fExp = id.expectedFreqFor(T);
			MakoPixel tone = tones.getNearest(fExp);
			double adf = Math.abs(tone.toneFrequency - fExp);
			
			// If the nearest tone is too far, then do not assign...
			if(adf > maxShift * fExp) continue;
			
			// If there is a better existing id, then leave it...
			if(tone.id == null) ids++;
			else if(Math.abs(tone.toneFrequency - tone.id.expectedFreqFor(T)) < adf) continue;						
			
			tone.id = id;
		}
		
		ResonanceList remaining = new ResonanceList(tones.size());
		ToneIdentifier extraIDs = (ToneIdentifier) clone();
		
		for(int i=0; i<tones.size(); i++) {
			MakoPixel tone = tones.get(i);
			if(tone.id == null) remaining.add(tone);
			else extraIDs.remove(tone.id);
		}
		
		
		//System.err.println("     +" + ids + " resonances.");
		
		return ids + extraIDs.assign(remaining, T, rounds-1);
		
		
	}
	public int indexBefore(double f) throws ArrayIndexOutOfBoundsException {
		int i = 0;
		int step = size() >> 1;

		
		if(get(0).freq > f) 
			throw new ArrayIndexOutOfBoundsException("Specified point precedes lookup range.");
		
		if(get(size() - 1).freq < f) 
			throw new ArrayIndexOutOfBoundsException("Specified point is beyond lookup range.");
		
		
		while(step > 0) {
			if(get(i + step).freq < f) i += step;
			step >>= 1;
		}
			
		return i;
	}
	
	
	public int getNearestIndex(double f) {	
		try {
			int lower = indexBefore(f);		
			int upper = lower+1;
		
			if(lower < 0) return upper;
			
			if(f - get(lower).freq < get(upper).freq - f) return lower;
			return upper;
		}
		catch(ArrayIndexOutOfBoundsException e) {
			if(f < get(0).freq) return 0;
			return size() - 1;
		}
	}
	
}
