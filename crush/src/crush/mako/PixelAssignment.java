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
import java.util.StringTokenizer;

import crush.CRUSH;


import util.Range;
import util.Util;
import util.data.AmoebaMinimizer;


public class PixelAssignment extends ArrayList<ResonanceID> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3011775640230135691L;
	
	public double alpha = 0.0;
	public Range alphaRange = new Range(-30.0, 0.1);
	public int attempts = 100;
	public double rchi;
	public double maxDeviation = 3.0;
	
	public PixelAssignment() {}
	
	public PixelAssignment(String fileName) throws IOException {
		this();
		read(fileName);
	}
	
	public void read(String fileSpec) throws IOException {
		if(CRUSH.verbose) System.err.println(" Assigning pixels from " + fileSpec);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(Util.getSystemPath(fileSpec))));
		String line = null;

		clear();
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line, ", \t");
			ResonanceID id = new ResonanceID();
			
			id.freq = Double.parseDouble(tokens.nextToken());
			id.row = Integer.parseInt(tokens.nextToken()) - 1;
			id.col = Integer.parseInt(tokens.nextToken()) - 1;
			//id.delta = Double.parseDouble(tokens.nextToken());
			id.delta = 1e-4 * id.freq; // Assuming 10 linewidths for Q~10^5...
			
			add(id);
		}
		
		in.close();
		
		if(CRUSH.verbose) System.err.println(" Loaded pixel assignments for " + size() + " resonances.");
	}
	
	public void match(ToneList channels) {
		fit(channels);
		assign(channels);
	}
	
	protected void fit(final ToneList channels) {
		AmoebaMinimizer opt = new AmoebaMinimizer() {

			@Override
			public double evaluate(double[] tryparms) {
				alpha = tryparms[0];
				double chi2 = 0.0;
				
				for(ResonanceID id : PixelAssignment.this) {
					double fExp = id.freq + alpha * id.delta;
					int i = channels.getNearestIndex(fExp);
					double dev = (channels.get(i).toneFrequency - fExp) / fExp;
					chi2 += Math.abs(dev);
				}
				
				if(alpha < alphaRange.min()) chi2 *= Math.exp(alphaRange.min() - alpha);
				else if(alpha > alphaRange.max()) chi2 *= Math.exp(alpha - alphaRange.max());
				
				return chi2;
				
			}	
		};
		
		opt.init(new double[] { -3.0 });
		opt.setStartSize(new double[] { 2.0 });
		opt.precision = 1e-10;
		opt.verbose = false;
		opt.minimize(attempts);
		
		//rchi = Math.sqrt(opt.getChi2() / size());
		rchi = opt.getChi2() / size();
		//alpha = opt.getFitParameters()[0];
		
		System.err.println(" Tone-pixel assignment rms = " + Util.s3.format(1e6 * rchi) + " ppm.");
		System.err.println(" --> alpha = " + Util.s4.format(alpha));
		
		
	}
	
	protected void assign(ToneList tones) {
		int assigned = 0;
		
		for(ResonanceID id : this) {
			double fExp = id.freq + alpha * id.delta;
			MakoPixel pixel = tones.get(tones.getNearestIndex(fExp));
			
			if(Math.abs(pixel.toneFrequency - fExp) > rchi * maxDeviation * fExp) continue;
			
			pixel.association = id;
			pixel.row = id.row;
			pixel.col = id.col;
			pixel.storeIndex = pixel.row * Mako.cols + pixel.col;
			pixel.unflag(MakoPixel.FLAG_UNASSIGNED);
			assigned++;
		}	
		
		System.err.println(" Assigned " + assigned + " of " + size() + " resonances.");
	}
	
}
