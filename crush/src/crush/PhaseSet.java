/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush;

import java.io.*;
import java.util.*;

import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.data.Statistics;
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

    public PhaseSignal getSignal(Mode mode) {
        PhaseSignal signal = signals.get(mode);
        if(signal == null) if(mode instanceof CorrelatedMode) {
            signal = new PhaseSignal(this, (CorrelatedMode) mode);
            signals.put(mode, signal);
        }
        return signal;
    }
    
    // TODO clone hash tables?

    public Integration<?,?> getIntegration() { return integration; }

    public PhaseDependents getPhaseDependents(String name) {
        return phaseDeps.containsKey(name) ? phaseDeps.get(name) : new PhaseDependents(this, name);
    }

    public void update(ChannelGroup<?> channels) {
        integration.comments.append(":P");

        for(PhaseData phase : this) phase.update(channels, integrationDeps);	

        int N = size();

        if(integration.hasOption("stability")) {
            double T = (integration.instrument.integrationTime * integration.size()) / size();	
            N = (int) Math.ceil(integration.option("stability").getDouble() * Unit.s / T);
            if((N & 1) != 0) N++;
            if(N > size()) N = size();
        }

        if(N < size()) integration.comments.append("(" + N + ")");

        removeDrifts(channels, N);

        generation++;
    }


    public void validate() {
        for(int i=size(); --i >=0; ) if(!get(i).validate()) remove(i);
        for(int i=size(); --i >=0; ) get(i).index = i;
    }

    public WeightedPoint[] getGainIncrement(Mode mode) {
        return getSignal(mode).getGainIncrement();
    }

    protected void syncGains(final Mode mode) throws Exception {
        if(signals.containsKey(mode)) signals.get(mode).syncGains();
    }

    public void getWeights() {
        integration.comments.append(",P");

        for(Channel channel : integration.instrument) if(channel instanceof PhaseWeighting) 
            ((PhaseWeighting) channel).deriveRelativePhaseWeights(this); 
    }

  

    public void despike(double level) { 
        integration.comments.append(",P");

        Hashtable<Integer, Double> levels = new Hashtable<Integer, Double>();
        Hashtable<Integer, Double> noise = new Hashtable<Integer, Double>();

        int spikes = 0;

        for(Channel channel : integration.instrument) {
            levels.clear();
            noise.clear();

            for(PhaseData p : this) {
                if(!levels.containsKey(p.phase)) levels.put(p.phase, getMedianLevel(channel, p.phase));
                if(!noise.containsKey(p.phase)) noise.put(p.phase, getMedianNoise(channel, p.phase, levels.get(p.phase)));

                despike(channel, level, levels.get(p.phase), noise.get(p.phase));
            }
        }

        integration.comments.append("(" + spikes + ")");
    }

    private int despike(Channel channel, double significance, double offset, double noise) { 
        int spikes = 0;

        for(PhaseData p : this) {
            double dev = (p.value[channel.index] - offset) / noise;

            if(Math.abs(dev) > significance) {
                p.sampleFlag[channel.index] |= PhaseData.FLAG_SPIKE;
                spikes++;
            }
            else p.sampleFlag[channel.index] &= ~PhaseData.FLAG_SPIKE;
        }

        return spikes;
    }


    public double ZgetMeanLevel(Channel channel, int phaseValue) {
        double sum = 0.0, sumw = 0.0;
        for(PhaseData p : this) if(p.phase == phaseValue) if(p.isUnflagged(channel)) {
            sum += p.weight[channel.index] * p.value[channel.index];
            sumw += p.weight[channel.index];
        }
        return sum / sumw;
    }

    public double getMedianLevel(Channel channel, int phaseValue) {
        WeightedPoint[] points = new WeightedPoint[size()];

        int k = 0;
        for(PhaseData p : this) if(p.phase == phaseValue) if(p.isUnflagged(channel)) {
            points[k++] = new WeightedPoint(p.value[channel.index], p.weight[channel.index]);
        }
        return k > 0 ? Statistics.Inplace.smartMedian(points, 0, k, 0.25).value() : Double.NaN;
    }

    public double getRMSNoise(Channel channel, int phaseValue, double level) {
        double sum = 0.0;
        int n=0;
        for(PhaseData p : this) if(p.phase == phaseValue) if(p.isUnflagged(channel)) {
            double dev = p.value[channel.index] * Math.sqrt(p.weight[channel.index]);
            sum += dev * dev;
            n++;
        }
        return n > 1 ? sum / (n-1) : Double.NaN;
    }

    public double getMedianNoise(Channel channel, int phaseValue, double level) {
        double[] var = new double[size()];

        int k = 0;
        for(PhaseData p : this) if(p.phase == phaseValue) if(p.isUnflagged(channel)) {
            double dev = p.value[channel.index] * Math.sqrt(p.weight[channel.index]);
            var[k++] = dev * dev;
        }
        return k > 1 ? Statistics.Inplace.median(var, 0, k) / Statistics.medianNormalizedVariance : Double.NaN;
    }



    public void removeDrifts(ChannelGroup<?> channels, int nPhases) {
        //integration.comments += "|DP(" + nPhases + ")";
        final PhaseDependents parms = getPhaseDependents("drifts");

        parms.clear(channels);

        for(Channel channel : channels) removeDrifts(channel, nPhases, parms);

        parms.apply(channels);
    }


    private void removeDrifts(final Channel channel, final int blockSize, final PhaseDependents parms) {		

        int nParms = ExtraMath.roundupRatio(size(), blockSize);

        for(int N=0; N < nParms; N++) {
            final int from = N * blockSize;
            final int to = Math.min(size(), from + blockSize);
            double sum = 0.0, sumw = 0.0;

            for(int n=from; n<to; n++) {
                PhaseData phase = get(n);
                if(phase.isFlagged(channel)) continue;

                sum += phase.weight[channel.index] * phase.value[channel.index];
                sumw += phase.weight[channel.index];
            }

            if(sumw > 0.0) {
                double level = (float) (sum / sumw);
                for(int n=from; n<to; n++) {
                    PhaseData phase = get(n);
                    phase.value[channel.index] -= level;
                    if(phase.isUnflagged(channel)) parms.addAsync(phase, phase.weight[channel.index] / sumw);
                }
                parms.addAsync(channel, 1.0);
            }		
        }		
    }

    public void write() throws IOException {
        String filename = CRUSH.workPath + File.separator + integration.scan.getID() + "-" + integration.getFileID() + ".phases.tms";
        PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(filename)));
        write(out);
        out.close();
        CRUSH.notify(this, "Written phases to " + filename);
    }

    public void write(PrintStream out) {
        out.println(integration.getASCIIHeader());
        for(int i=0; i<size(); i++) out.println(get(i).toString(Util.e3));
    }

}
