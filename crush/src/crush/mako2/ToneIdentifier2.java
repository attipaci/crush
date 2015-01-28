/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.mako2;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.StringTokenizer;

import crush.mako.AbstractMakoPixel;
import crush.mako.ResonanceList;
import kovacs.data.fitting.AmoebaMinimizer;
import kovacs.math.Range;
import kovacs.math.Vector2D;
import kovacs.util.Configurator;
import kovacs.util.Unit;
import kovacs.util.Util;



public class ToneIdentifier2 extends ArrayList<ResonanceID2> implements Cloneable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3011775640230135691L;
		
	public Range deltaRange = new Range(-1e-3, 1e-3);
	public int attempts = 100;
	public double rchi;
	public double maxDeviation = 3.0;
	public double power = 1.0;
	
	
	public ToneIdentifier2() {}
	
	public ToneIdentifier2(Configurator options) throws IOException {
		this(options.getValue());
		if(options.isConfigured("power")) power = options.get("power").getDouble();
		if(options.isConfigured("deltarange")) deltaRange = options.get("deltarange").getRange();
		if(options.isConfigured("max")) maxDeviation = options.get("max").getDouble();
		if(options.isConfigured("attempts")) attempts = options.get("attempts").getInt();
	}
	
	public ToneIdentifier2(String fileName) throws IOException {
		this();
		read(fileName);
	}
	
	public void discardAbove(double freq) {
		int n = size();
		for(int i=size(); --i >= 0; ) {
			ResonanceID2 id = get(i);
			if(id.freq >= freq) remove(i);
		}
		System.err.println(" Discarded " + (n-size()) + ", kept " + size() + " tones below " + (freq / Unit.MHz) + "MHz.");
	}
	 
	public void discardBelow(double freq) {
		int n = size();
		for(int i=size(); --i >= 0; ) {
			ResonanceID2 id = get(i);
			if(id.freq < freq) remove(i);
		}
		System.err.println(" Discarded " + (n-size()) + ", kept " + size() + " tones above " + (freq / Unit.MHz) + "MHz.");
	}
	 
	
	public void read(String fileSpec) throws IOException {
		System.err.println(" Loading resonance identifications from " + fileSpec);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(Util.getSystemPath(fileSpec))));
		String line = null;

		clear();
			
		int index = 1;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line, ", \t");
			ResonanceID2 id = new ResonanceID2(index++);
			
			id.freq = Double.parseDouble(tokens.nextToken());
			id.position = new Vector2D(Double.parseDouble(tokens.nextToken()), Double.parseDouble(tokens.nextToken()));
			id.position.scale(Unit.arcsec);
			//if(tokens.hasMoreTokens()) id.gain = Double.parseDouble(tokens.nextToken());
			
			add(id);
		}
		
		in.close();
		
		Collections.sort(this);
		
		System.err.println(" Got IDs for " + size() + " resonances.");
	}
	
	
	public double match(ResonanceList<Mako2Pixel> channels) {
		double delta = fit(channels);
		assign(channels, delta);
		return delta;
	}
	
	protected double fit(final ResonanceList<Mako2Pixel> channels) {
		channels.sort();
			
		final double maxSearchDev = maxDeviation * 0.5 * deltaRange.span();
		
		AmoebaMinimizer opt = new AmoebaMinimizer() {
			@Override
			public double evaluate(double[] tryparms) {
				double delta = tryparms[0];
				double chi2 = 0.0;
				int n = 0;
				
				for(ResonanceID2 id : ToneIdentifier2.this) {
					double fExp = id.freq * (1.0 + delta);
					double dev = (channels.getNearest(fExp).toneFrequency - fExp) / fExp;
					if(Math.abs(dev) < maxSearchDev) {
						chi2 += Math.pow(Math.abs(dev), power);
						n++;
					}
				}
				
				if(delta < deltaRange.min()) chi2 *= Math.exp(deltaRange.min() - delta);
				else if(delta > deltaRange.max()) chi2 *= Math.exp(delta - deltaRange.max());
				
				return chi2 / n;
			}	
		};
		
		opt.init(new double[] { 0.5 * (deltaRange.min() + deltaRange.max()) });
		opt.setStartSize(new double[] { 0.3 * deltaRange.span() });
		opt.precision = 1e-12;
		opt.verbose = false;
		opt.minimize(attempts);
		
		rchi = Math.pow(opt.getChi2(), 1.0 / power);
		
		double delta = opt.getFitParameters()[0];
		
		System.err.println("   Tone assignment rms = " + Util.s3.format(1e6 * rchi) + " ppm.");
		System.err.println("   --> df/f (id) = " + Util.s4.format(1e6 * delta) + " ppm.");
		
		return delta;
	}
	
	protected void assign(ResonanceList<Mako2Pixel> tones, double delta) {
		for(Mako2Pixel pixel : tones) {
			pixel.id = null;
			pixel.flag(Mako2Pixel.FLAG_NOTONEID);
		}
		int n = assign(tones, delta, 5);
		System.err.println("   Identified " + n + " resonances.");
		
		for(Mako2Pixel pixel : tones) if(pixel.id != null) pixel.unflag(Mako2Pixel.FLAG_NOTONEID);
		
	}
	
	private int assign(ResonanceList<Mako2Pixel> tones, double delta, int rounds) {
		if(rounds == 0) return 0;
		if(tones.isEmpty()) return 0; 
	
		int ids = 0;
		
		final double maxShift = rchi * maxDeviation;
		
		for(ResonanceID2 id : this) {
			double fExp = id.freq * (1.0 - delta);
			Mako2Pixel tone = tones.getNearest(fExp);
			double adf = Math.abs(tone.toneFrequency - fExp);
			
			// If the nearest tone is too far, then do not assign...
			if(adf > maxShift * fExp) continue;
			
			// If there is a better existing id, then leave it...
			if(tone.id == null) ids++;
			else if(Math.abs(tone.toneFrequency - tone.id.freq * (1.0 + delta)) < adf) continue;						
			
			tone.id = id;
			tone.position = id.position;
			tone.row = -1;
			tone.col = -1;
				
			tone.unflag(AbstractMakoPixel.FLAG_UNASSIGNED);
			
			if(!Double.isNaN(id.gain)) tone.gain = id.gain;
		}
		
		ResonanceList<Mako2Pixel> remaining = new ResonanceList<Mako2Pixel>(tones.size());
		ToneIdentifier2 extraIDs = (ToneIdentifier2) clone();
		
		for(int i=0; i<tones.size(); i++) {
			Mako2Pixel tone = tones.get(i);
			if(tone.id == null) remaining.add(tone);
			else extraIDs.remove(tone.id);
		}
		
		
		//System.err.println("     +" + ids + " resonances.");
		
		return ids + extraIDs.assign(remaining, delta, rounds-1);	
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

	public static double initDelta = 3.0 * Unit.kHz;
	
}