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
	public Range alphaRange = new Range(0.0, 1.0);
	public int attempts = 3;

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
			id.delta = Double.parseDouble(tokens.nextToken());
			
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
					double f = id.freq + alpha * id.delta;
					int i = channels.getNearestIndex(f);
					double dev = (channels.get(i).toneFrequency - f) / f;
					chi2 += dev * dev;
				}
				
				if(alpha < alphaRange.min()) alpha *= Math.exp(alphaRange.min()-alpha);
				else if(alpha > alphaRange.max()) alpha *= Math.exp(alpha - alphaRange.max());
				
				return chi2;
				
			}	
		};
		
		opt.init(new double[] { 0.5 });
		opt.setStartSize(new double[] { 0.1 });
		opt.precision = 1e-10;
		opt.verbose = false;
		opt.minimize(attempts);
		
		double rchi = Math.sqrt(opt.getChi2() / size());
		if(CRUSH.verbose) System.err.println(" Pixels assignment rms = " + Util.e3.format(1e6 * rchi) + " ppm.");
		
		alpha = opt.getFitParameters()[0];
	}
	
	protected void assign(ToneList pixels) {
		for(ResonanceID id : PixelAssignment.this) {
			double f = id.freq + alpha * id.delta;
			int i = pixels.getNearestIndex(f);
			MakoPixel pixel = pixels.get(i);
			pixel.association = id;
			pixel.row = id.row;
			pixel.col = id.col;
			pixel.storeIndex = pixel.row * Mako.cols + pixel.col;
			pixel.unflag(MakoPixel.FLAG_UNASSIGNED);
		}	
	}
	
}
