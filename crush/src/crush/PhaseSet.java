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
package crush;

import java.io.*;
import java.util.*;

import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.data.WeightedPoint;


public class PhaseSet extends ArrayList<PhaseData> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3448515171055358173L;
	
	private Integration<?,?> integration;
	protected Hashtable<Mode, PhaseSignal> signals = new Hashtable<Mode, PhaseSignal>();
	protected Hashtable<String, PhaseDependents> phaseDeps = new Hashtable<String, PhaseDependents>();
	
	public double[] channelParms;
	
	Dependents integrationDeps;
	int generation = 0;	
	//public int driftParms = 0;
	
	public PhaseSet(Integration<?,?> integration) {
		this.integration = integration;	
		integrationDeps = new Dependents(integration, "phases");
		channelParms = new double[integration.instrument.size()];
	}
	
	public Integration<?,?> getIntegration() { return integration; }
	
	public PhaseDependents getPhaseDependents(String name) {
		return phaseDeps.containsKey(name) ? phaseDeps.get(name) : new PhaseDependents(this, name);
	}
	
	public void update(ChannelGroup<?> channels) {
		
		integration.comments += "|P";
		
		for(PhaseData offsets : this) offsets.update(channels, integrationDeps);	
		
		int N = size();
		if(integration.hasOption("stability")) {
			double T = (integration.instrument.integrationTime * integration.size()) / size();	
			N = (int) Math.ceil(integration.option("stability").getDouble() * Unit.s / T);
			if((N & 1) != 0) N++;
			if(N > size()) N = size();
		}
		
		/*
		if(N == size()) integration.comments += "PO";
		else integration.comments += " PD(" + N + ")";
		*/
		
		removeDrifts(channels, N);
		
		generation++;
	}
	
	public void validate() {
		for(int i=size(); --i >=0; ) if(!get(i).validate()) remove(i);
		for(int i=size(); --i >=0; ) get(i).index = i;
	}
		
	public WeightedPoint[] getGainIncrement(Mode mode) {
		return signals.get(mode).getGainIncrement();
	}
	
	protected void syncGains(final Mode mode) throws Exception {
		if(signals.containsKey(mode)) signals.get(mode).syncGains();
	}
	
	public void getWeights() {
		integration.comments += "|wP";
		
		for(Channel channel : integration.instrument)
			if(channel instanceof PhaseWeighting) ((PhaseWeighting) channel).deriveRelativePhaseWeights(this); 
	}
	
	public void despike(double level) {
		integration.comments += "|dP";
		
		int spikes = 0;
		
		for(Channel channel : integration.instrument)
			if(channel instanceof PhaseDespiking) spikes += ((PhaseDespiking) channel).despike(this, level); 
		
		integration.comments += "(" + spikes + ")";
	}
	
	public void removeDrifts(ChannelGroup<? extends Channel> channels, int nPhases) {
		//integration.comments += "|DP(" + nPhases + ")";
		for(Channel channel : channels) removeDrifts(channel, nPhases);		
	}
	
	private void removeDrifts(final Channel channel, final int nPhases) {		
		final PhaseDependents parms = getPhaseDependents("drifts");
		parms.clear(channel);
		
		int nParms = ExtraMath.roundupRatio(size(), nPhases);
		
		for(int N=0; N < nParms; N++) {
			double sum = 0.0, sumw = 0.0;
			final int from = N * nPhases;
			final int to = Math.min(size(), from+nPhases);
			
			for(int n=from; n<to; n++) {
				PhaseData offsets = get(n);
				sum += offsets.weight[channel.index] * offsets.value[channel.index];
				sumw += offsets.weight[channel.index];
			}
			
			if(sumw > 0.0) {
				double level = (float) (sum / sumw);
				for(int n=from; n<to; n++) {
					PhaseData offsets = get(n);
					offsets.value[channel.index] -= level;
					parms.addAsync(offsets, offsets.weight[channel.index] / sumw);
				}
			}	
			parms.addAsync(channel, 1.0);
		}		
		
		parms.apply(channel);
	}
	
	// TODO levelling on just the left frames...
	// TODO Use removeDrifts for dependence 
	public void level(final Channel channel) {
		//driftParms = Math.max(driftParms, 1);
		
		final int c = channel.index;
		double sum = 0.0, sumw = 0.0;
		for(PhaseData offsets : this) {			
			sum += offsets.weight[c] * offsets.value[c];
			sumw += offsets.weight[c];
		}
		if(sumw == 0.0) return;
		final double ave = sum / sumw;
		
		for(PhaseData offsets : this) offsets.value[c] -= ave;
	}
	
	public void write() throws IOException {
		String filename = CRUSH.workPath + File.separator + integration.scan.getID() + "-" + integration.getID() + ".phases.tms";
		PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(filename)));
		write(out);
		out.close();
		System.err.println("Written phases to " + filename);
	}
	
	public void write(PrintStream out) {
		out.println(integration.getASCIIHeader());
		for(int i=0; i<size(); i++) out.println(get(i).toString(Util.e3));
	}

}
