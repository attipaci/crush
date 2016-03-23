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

import java.io.Serializable;
import java.util.Arrays;

import crush.CorrelatedMode.CoupledMode;
import jnum.Parallel;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.data.WeightedPoint;

public class PhaseSignal implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8172618561500360854L;
	
	PhaseSet phases;
	CorrelatedMode mode;

	double[] value, weight;
	float[] syncGains;
	int generation = 0;
	
	public PhaseSignal(PhaseSet phases, CorrelatedMode mode) {
		this.phases = phases;
		this.mode = mode;
		
		value = new double[phases.size()];
		weight = new double[phases.size()];
		
		syncGains = new float[mode.size()];
	
		phases.signals.put(mode, this);
	}
	
	@Override
	public int hashCode() {
		int hash = super.hashCode() ^ mode.hashCode() ^ generation;
		//if(value != null) hash ^= HashCode.sampleFrom(value);
		//if(syncGains != null) hash ^= HashCode.sampleFrom(syncGains);
		return hash;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof PhaseSignal)) return false;
		if(!super.equals(o)) return false;
		
		PhaseSignal sig = (PhaseSignal) o;
		if(generation != sig.generation) return false;
		if(!Util.equals(mode, sig.mode)) return false;
		//if(!Arrays.equals(value, sig.value)) return false;
		//if(!Arrays.equals(syncGains, sig.syncGains)) return false;
		return true;
	}
	
	public double getValue(int i) { return value[i]; }
	
	public double getWeight(int i) { return weight[i]; }
	
	public void scale(double factor) {
		double f2 = factor * factor;
		for(int i=value.length; --i >= 0; ) {
			value[i] *= factor;
			weight[i] /= f2;
		}
		
		// Rescale the signals in the coupled modes also...
		for(CoupledMode coupled : mode.coupledModes)
			if(phases.signals.containsKey(coupled)) phases.signals.get(coupled).scale(factor);
		
		// Rescale the corresponding synching gains also to keep the product intact
		for(int k=syncGains.length; --k >= 0; ) syncGains[k] /= factor;	
	}
	
	
	protected void update(boolean isRobust) throws Exception {
		// Make syncGains carry the gain increment since last sync...
		syncGains();
		
		final float[] G = mode.getGains();
		final ChannelGroup<?> channels = mode.getChannels();
		
		final Integration<?,?> integration = phases.getIntegration();
		
		// Allow phases.estimator to override the default estimator request
		if(integration.hasOption("phases.estimator")) 
			isRobust = integration.option("phases.estimator").equals("median");
		
		final boolean useMedians = isRobust;
	
		final PhaseDependents phaseParms = phases.getPhaseDependents(mode.getName());
		phaseParms.clear(channels);
		
		new CRUSH.Fork<double[]>(phases.size(), integration.getThreadCount()) {
			private DataPoint[] temp = null;
			private double phaseChannelParms[];
			private WeightedPoint dC; 
			
			@Override
			protected void init() {
				super.init();
				
				dC = new WeightedPoint();
				
				// Use local channel dependency accounting for the mode...
				phaseChannelParms = integration.instrument.getDoubles();
				Arrays.fill(phaseChannelParms, 0, mode.size(), 0.0);
				
				if(useMedians) {
					temp = integration.instrument.getDataPoints();
					for(int i=G.length; --i >= 0; ) temp[i].noData();
				}
			}
			
			@Override
			protected void cleanup() {
				super.cleanup();
				if(temp != null) Instrument.recycle(temp);
			}
			
			@Override
            protected void postProcess() {
                super.postProcess();
                
                for(Parallel<double[]> task : getWorkers()) {
                    double[] localChannelParms = task.getLocalResult();
                    for(int k=mode.size(); --k >= 0; ) phaseParms.addAsync(mode.getChannels().get(k), localChannelParms[k]);
                    Instrument.recycle(localChannelParms);
                }
            }
			
			@Override
            public double[] getLocalResult() { return phaseChannelParms; }
            
			@Override
			protected void processIndex(int i) {
				final PhaseData phase = phases.get(i);
				
				if(useMedians) phase.getRobustCorrelated(mode, G, temp, dC, phaseChannelParms);
				else phase.getMLCorrelated(mode, G, dC, phaseChannelParms);
				
				if(dC.weight() <= 0.0) return;
				
				value[i] += dC.value();
				weight[i] = dC.weight();
					
				phaseParms.addAsync(phase, 1.0);
			}
			
		}.process();
	
		phaseParms.apply(channels);
		
		generation++;
		setSyncGains(G);
	}
	
	public WeightedPoint[] getGainIncrement() {	
		final ChannelGroup<?> channels = mode.getChannels();
		final WeightedPoint[] dG = new WeightedPoint[channels.size()];
		
		new CRUSH.Fork<Void>(channels.size(), phases.getIntegration().getThreadCount()) {
			@Override
			protected void processIndex(int k) { dG[k] = getGainIncrement(channels.get(k)); }
		}.process();
			
		return dG;
	}
	
	protected WeightedPoint getGainIncrement(final Channel channel) {
		double sum = 0.0, sumw = 0.0;

		for(int i=phases.size(); --i >= 0; ) {
			final PhaseData phase = phases.get(i);
			
			if(phase.channelFlag[channel.index] != 0) continue;
			
			final double C = value[i];
			final double wC = weight[i] * C;
			sum += (wC * phase.value[channel.index]);
			sumw += (wC * C);
		}
		return sumw > 0.0 ? new WeightedPoint(sum / sumw, sumw) : new WeightedPoint();
	}
	
	// TODO robust gains?...
	
	protected void setSyncGains(final float[] G) {
		System.arraycopy(G, 0, syncGains, 0, G.length);
	}
	
	protected void syncGains() throws Exception {
		final ChannelGroup<?> channels = mode.getChannels();
		final float[] G = mode.getGains();		
		final float[] dG = syncGains;
		
		boolean changed = false;
		for(int k=G.length; --k >= 0; ) {
			dG[k] = G[k] - dG[k];
			if(dG[k] != 0.0F) changed = true;
		}
		if(!changed) return;
			
		new CRUSH.Fork<Void>(phases.size(), phases.getIntegration().getThreadCount()) {
			@Override
			protected void processIndex(int i) {
				if(!(weight[i] > 0.0)) return;
				final PhaseData phase = phases.get(i);
				for(int k=G.length; --k >= 0; ) phase.value[channels.get(k).index] -= dG[k] * value[i];
			}
			
		}.process();
		
		setSyncGains(G);
	}
	

}
