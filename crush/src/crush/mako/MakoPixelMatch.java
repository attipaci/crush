/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import crush.CRUSH;
import crush.resonators.*;
import jnum.Configurator;
import jnum.Unit;
import jnum.Util;
import jnum.data.Statistics;
import jnum.math.Range;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;



public class MakoPixelMatch extends ToneIdentifier<MakoFrequencyID> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3011775640230135691L;
	
	public double Thot = 285.0 * Unit.K;	// Temperature at which 'hot' ids are derived...
	public double Tcold = 112.2 * Unit.K;	// Effective cold-load temperature of hot/cold measurements.
	
	public MakoPixelMatch() {}
	
	public MakoPixelMatch(Configurator options) throws IOException {
		super(options);
		if(options.isConfigured("uniform")) uniformize();
		if(options.isConfigured("trange")) {
			deltaRange = options.get("trange").getRange();
			deltaRange.scale(Unit.K);
		}
		read(options.getValue());
	}
	
	public MakoPixelMatch(String fileName) throws IOException {
		this();
		read(fileName);
	}
		
	
	@Override
	public Range getDefaultShiftRange() { return new Range(0.0, 350.0 * Unit.K); }
	

	public void read(String fileSpec) throws IOException {
		CRUSH.info(this, "Loading resonance identifications from " + fileSpec);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(Util.getSystemPath(fileSpec))));
		String line = null;

		clear();
		
		// Assuming 12 C for the hot load...
		// and 195 K for the cold
		double dT = Thot - Tcold;
		
		int index = 1;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line, ", \t");
			MakoFrequencyID id = new MakoFrequencyID(index++);
			
			double fcold = Double.parseDouble(tokens.nextToken());
			id.freq = Double.parseDouble(tokens.nextToken());
			id.delta = (id.freq - fcold) / dT; 
			id.T0 = Thot;
			
			add(id);
		}
		
		in.close();
		
		//Collections.sort(this);
		
		CRUSH.info(this, "Got IDs for " + size() + " resonances.");
	}
	
	public void uniformize() {
		double[] deltas = new double[size()];
		for(int i=size(); --i >= 0; ) {
			MakoFrequencyID id = get(i);
			deltas[i] = id.delta / id.freq;
		}
		
		double ave = Statistics.median(deltas);
		CRUSH.values(this, "Median hot/cold response is " + Util.s3.format(1e6 * ave) + " ppm / K");
		
		for(int i=size(); --i >= 0; ) {
			MakoFrequencyID id = get(i);
			id.delta = ave * id.freq;
		}
	}
	

	public double getExpectedFrequency(MakoFrequencyID id, double T) {
		return id.freq + (T - Thot) * id.delta;
	}
	
	@Override
	protected double fit(final ResonatorList<?> resonators, double guessT) {
		double T = super.fit(resonators, guessT);
		CRUSH.values(this, "--> T(id) = " + Util.s4.format(T / Unit.K) + " K.");
		return T;
	}
	

}
