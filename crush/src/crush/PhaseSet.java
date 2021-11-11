/* *****************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
package crush;

import java.io.*;
import java.util.*;

import jnum.Util;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;


public abstract class PhaseSet extends ArrayList<PhaseData> {
    /**
     * 
     */
    private static final long serialVersionUID = 3448515171055358173L;

    private Integration<?> integration;
    protected Hashtable<Mode, PhaseSignal> signals = new Hashtable<>();
    protected Hashtable<String, PhaseDependents> phaseDeps = new Hashtable<>();

    public double[] channelParms;

    private double[] relativePhaseWeights;

    Dependents integrationDeps;
    int generation = 0;	
    //public int driftParms = 0;

    public PhaseSet(Integration<?> integration) {
        this.integration = integration;	
        integrationDeps = new Dependents(integration, "phases");
        channelParms = new double[getInstrument().size()];
        relativePhaseWeights = new double[getInstrument().size()];
        Arrays.fill(relativePhaseWeights, 1.0);
    }

    public PhaseSignal getSignal(Mode mode) {
        PhaseSignal signal = signals.get(mode);
        if(signal == null) if(mode instanceof CorrelatedMode) {
            signal = new PhaseSignal(this, (CorrelatedMode) mode);
            signals.put(mode, signal);
        }
        return signal;
    }
   

    public Integration<?> getIntegration() { return integration; }

    public Instrument<?> getInstrument() { return getIntegration().getInstrument(); }
    
    
    public final double getRelativeWeight(Channel channel) {
        return relativePhaseWeights[channel.index];    
    }
    
    private void setRelativePhaseWeight(Channel channel, double w) {
        relativePhaseWeights[channel.index] = w;    
    }    
    
    public void deriveRelativeChannelWeights() {
        getIntegration().comments.append(",P");
        for(Channel channel : getInstrument()) deriveRelativeWeightFor(channel); 
    }
    
    
    private void deriveRelativeWeightFor(Channel channel) {    
        channel.unflag(Channel.FLAG_PHASE_DOF);
         
        // Get the raw relative channel weights from the phase data.
        double wr = deriveRawRelativeWeightFor(channel);
        
        if(Double.isNaN(wr)) {
            channel.flag(Channel.FLAG_PHASE_DOF);
            return;
        }
        
        // Do not allow relative phaseWeights to become larger than 1.0
        if(wr > 1.0) wr = 1.0;        
        
        setRelativePhaseWeight(channel, wr);
    }
    
    
    public abstract double deriveRawRelativeWeightFor(Channel channel);

    
    
    public PhaseDependents getPhaseDependents(String name) {
        return phaseDeps.containsKey(name) ? phaseDeps.get(name) : new PhaseDependents(this, name);
    }

    public void update(ChannelGroup<?> channels) {
        integration.comments.append(":P");

        for(PhaseData phase : this)
            phase.update(channels, integrationDeps);	
        
        deriveRelativeChannelWeights();
        
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



    public void despike(double level) { 
        integration.comments.append(",P");
        
        HashMap<Integer, Double> levels = new HashMap<>();
        HashMap<Integer, Double> noise = new HashMap<>();

        int spikes = 0;

        for(Channel channel : integration.getInstrument()) {
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

        if(Double.isNaN(noise)) return 0;
        
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


    public double getMeanLevel(Channel channel, int phaseValue) {
        double sum = 0.0, sumw = 0.0;
        final double wr = getRelativeWeight(channel);
        
        for(PhaseData p : this) if(p.phase == phaseValue) if(p.isUnflagged(channel)) {
            final double w = wr * p.weight[channel.index];
            sum += w * p.value[channel.index];
            sumw += w;
        }
        return sum / sumw;
    }

    public double getMedianLevel(Channel channel, int phaseValue) {
        WeightedPoint[] points = new WeightedPoint[size()];

        int k = 0;
        for(PhaseData p : this) if(p.phase == phaseValue) if(p.isUnflagged(channel)) {
            points[k++] = new WeightedPoint(p.value[channel.index], p.weight[channel.index]);
        }
        return k > 0 ? Statistics.Destructive.smartMedian(points, 0, k, 0.25).value() : Double.NaN;
    }

    public double getRMSNoise(Channel channel, int phaseValue, double level) {
        double sum = 0.0;
        int n=0;
        
        final double wr = getRelativeWeight(channel);
        
        for(PhaseData p : this) if(p.phase == phaseValue) if(p.isUnflagged(channel)) {
            double dev = p.value[channel.index] * Math.sqrt(wr * p.weight[channel.index]);
            sum += dev * dev;
            n++;
        }
        return n > 1 ? sum / (n-1) : Double.NaN;
    }

    public double getMedianNoise(Channel channel, int phaseValue, double level) {
        double[] var = new double[size()];
        
        final double wr = getRelativeWeight(channel);

        int k = 0;
        for(PhaseData p : this) if(p.phase == phaseValue) if(p.isUnflagged(channel)) {
            double dev = p.value[channel.index] * Math.sqrt(wr * p.weight[channel.index]);
            var[k++] = dev * dev;
        }
        return k > 1 ? Statistics.Destructive.median(var, 0, k) / Statistics.medianNormalizedVariance : Double.NaN;
    }


    public void write(String path) throws IOException {
        String filename = path + File.separator + integration.scan.getID() + "-" + integration.getFileID() + ".phases.tms";
        
        try(PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(filename)))) {
            write(out);
            out.close();
        }
        CRUSH.notify(this, "Written phases to " + filename);
    }

    public void write(PrintStream out) {
        out.println(integration.getASCIIHeader());
        for(int i=0; i<size(); i++) out.println(get(i).toString(Util.e3));
    }

}
