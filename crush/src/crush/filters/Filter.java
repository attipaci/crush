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
package crush.filters;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.IntStream;

import crush.Channel;
import crush.ChannelGroup;
import crush.Dependents;
import crush.Frame;
import crush.Instrument;
import crush.Integration;
import jnum.Configurator;
import jnum.Constant;
import jnum.CopiableContent;
import jnum.ExtraMath;
import jnum.Util;
import jnum.data.Statistics;
import jnum.parallel.ParallelTask;


public abstract class Filter implements Serializable, Cloneable, CopiableContent<Filter> {
    /**
     * 
     */
    private static final long serialVersionUID = 9018904303446615792L;

    protected Integration<?> integration;
    protected Dependents parms;
    protected float[] frameParms;
    private ChannelGroup<?> channels;

    protected int nt, nf;
    protected double df, points;

    boolean dft = false;
    boolean isEnabled = false;

    boolean isPedantic = false;

    private float[] data;
    private float[] pointResponse;

    public Filter(Integration<?> integration) {
        setIntegration(integration);
    }

    public Filter(Integration<?> integration, float[] data) {
        this(integration);
        this.data = data;
    }

    @Override
    public Filter clone() {
        try {
            Filter clone = (Filter) super.clone();
            if(channels != null) clone.channels = (ChannelGroup<?>) channels.clone();
            clone.data = null;
            clone.frameParms = null;
            return clone;
        } catch(CloneNotSupportedException e) { return null; }
    }

    @Override
    public Filter copy() { return copy(true); }

    @Override
    public Filter copy(boolean withContents) {
        Filter copy = clone();
        if(data != null) {
            copy.data = new float[data.length];
            if(withContents) System.arraycopy(data, 0, copy.data, 0, data.length);
        }
        if(frameParms != null) {
            copy.frameParms = new float[frameParms.length];
            if(withContents) System.arraycopy(frameParms, 0, copy.frameParms, 0, frameParms.length);
        }
        if(parms != null) copy.parms = parms.copy();
        return copy;
    }

    public final Instrument<?> getInstrument() { return integration.getInstrument(); }

    protected void makeTempData() {
        if(data != null) discardTempData();
        data = integration.getFloats();
    }

    protected void discardTempData() {
        Integration.recycle(data);
        data = null;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public float[] getTempData() { return data; }

    public void setTempData(float[] data) { this.data = data; }

    public abstract String getID();

    public abstract String getConfigName();

    protected abstract double responseAt(int fch);

    protected double countParms() {
        return IntStream.range(getHipassIndex(), nf).parallel().mapToDouble(f -> rejectionAt(f)).sum();
    }
    
    protected final double getPointResponse(Channel channel) {
        return pointResponse[channel.getIndex()];
    }
    
    protected void setPointResponse(Channel channel, double value) {
        pointResponse[channel.getIndex()] = (float) value;
    }
    
    
    protected double getMeanPointResponse() {
        double sumwG2 = 0.0, sumwG = 0.0;

        for(Channel channel : getChannels()) if(channel.isUnflagged()) {
            final double G = getPointResponse(channel);
            sumwG2 += channel.weight * G * G;
            sumwG += channel.weight * G;
        }

        return sumwG2 / sumwG;
    }

    public double rejectionAt(int fch) {
        return 1.0 - responseAt(fch);
    }

    protected void setIntegration(Integration<?> integration) {
        this.integration = integration;
        
        pointResponse = new float[integration.getInstrument().size()];
        Arrays.fill(pointResponse, 1.0F);

        nt = ExtraMath.pow2ceil(integration.size());	
        nf = nt >>> 1;
        df = 1.0 / (getInstrument().samplingInterval * nt);

        if(getChannels() == null) setChannels(getInstrument());
    }

    public ChannelGroup<?> getChannels() {
        return channels;
    }

    public void setChannels(ChannelGroup<?> channels) {
        this.channels = channels;
    }

    public boolean hasOption(String key) { 
        return integration.hasOption(getConfigName() + "." + key);
    }

    public Configurator option(String key) {
        return integration.option(getConfigName() + "." + key);
    }

    public void updateConfig() {
        isEnabled = integration.hasOption(getConfigName());	
        isPedantic = integration.hasOption("filter.mrproper");
    }

    // Allows to adjust the FFT filter after the channel spectrum has been loaded
    protected void updateProfile(Channel channel) {}

    public final boolean apply() { return apply(true); }

    public final boolean apply(boolean report) {

        updateConfig();

        if(!isEnabled()) return false;

        integration.comments.append(getID());

        preFilter();

        getChannels().new Fork<float[]>() {
            private Filter worker;

            @Override
            protected void init() {
                super.init();
                worker = Filter.this.copy();
                worker.makeTempData();

                worker.frameParms = integration.getFloats();
                Arrays.fill(worker.frameParms, 0, integration.size(), 0.0F);
            }

            @Override
            protected void cleanup() {
                super.cleanup();
                worker.discardTempData();
            }

            @Override
            public float[] getLocalResult() { return worker.frameParms; }

            @Override
            protected void postProcess() {
                super.postProcess();

                for(ParallelTask<float[]> task : getWorkers()) {
                    float[] localFrameParms = task.getLocalResult();
                    parms.addForFrames(localFrameParms);
                    Integration.recycle(localFrameParms);
                }
            }

            @Override
            protected void process(Channel channel) {
                worker.applyTo(channel);
            }

        }.process();


        postFilter();

        if(report) report();

        return true;
    }

    private void applyTo(Channel channel) {
        boolean localTemp = (data == null);
        if(localTemp) makeTempData();

        loadTimeStream(channel);

        preFilter(channel);

        // Apply the filter, with the rejected signal written to the local data array
        if(dft) dftFilter(channel);
        else fftFilter(channel);

        if(isPedantic) levelDataForChannel(channel);

        postFilter(channel);

        remove(channel);

        if(localTemp) discardTempData();
    }

    protected void preFilter() {
        if(parms == null) parms = integration.getDependents(getConfigName());
        parms.clear(getChannels(), 0, integration.size());
    }

    protected void postFilter() {
        parms.apply(getChannels(), 0, integration.size());	
    }

    protected void preFilter(Channel channel) {}

    protected void postFilter(Channel channel) {
        // Remove the DC component...
        //levelDataFor(channel);
    }

    protected void remove(Channel channel) {
        // Subtract the rejected signal...
        final int c = channel.getIndex();
        integration.validParallelStream().forEach(f -> remove(data[f.index], f, c));
    }

    protected void remove(final float value, final Frame exposure, final int channel) {
        if(exposure == null) return;
        exposure.data[channel] -= value;
    }

    public void report() {
        integration.comments.append(
                getInstrument().mappingChannels > 0 ? 
                        "(" + Util.f2.format(getMeanPointResponse()) + ")" :
                            "(---)"
                );
    }

    // TODO smart timestream access...
    protected void loadTimeStream(Channel channel) {
        final int c = channel.getIndex();

        points = 0.0;

        double sum = 0.0;
        int n=0;

        // Load the channel data into the data array
        for(int t = integration.size(); --t >= 0; ) {
            final Frame exposure = integration.get(t);

            if(exposure == null) data[t] = Float.NaN;
            else if(exposure.isFlagged(Frame.MODELING_FLAGS)) data[t] = Float.NaN;
            else if(exposure.sampleFlag[c] != 0) data[t] = Float.NaN;
            else {
                sum += (data[t] = exposure.relativeWeight * exposure.data[c]);
                points += exposure.relativeWeight;
                n++;
            }
        }

        // Remove the DC offset...
        if(n > 0) {
            final float ave = (float) (sum / n);
            IntStream.range(0, integration.size()).parallel().forEach(t -> data[t] = Float.isNaN(data[t]) ? 0.0F : data[t] - ave);
        }
        else Arrays.fill(data, 0, integration.size(), 0.0F);
    }


    // Convert data into a rejected signal (unlevelled)
    protected void fftFilter(Channel channel) {
        // Pad with zeroes as necessary...
        Arrays.fill(data, integration.size(), data.length, 0.0F);

        integration.getFFT().real2Amplitude(data);

        updateProfile(channel);

        data[0] = 0.0F;
        data[1] *= rejectionAt(nf);	

        IntStream.range(1, data.length>>>1).parallel().forEach(f -> { double r = rejectionAt(f); f<<=1; data[f] *= r; data[f+1] *= r; } );

        integration.getFFT().amplitude2Real(data);
    }

    // Convert data into a rejected signal (unlevelled)
    protected void dftFilter(Channel channel) {
        // TODO make rejected a private field, initialize or throw away as needed (setDFT())
        float[] rejected = new float[integration.size()];

        IntStream.rangeClosed(0, nf).parallel().forEach(f -> { 
            double r = rejectionAt(f); 
            if(r > 0.0) dftFilter(channel, f, r, rejected); 
        }); 

        System.arraycopy(rejected, 0, data, 0, rejected.length);
    }


    protected int getHipassIndex() {
        double hipassf = 0.5 / integration.filterTimeScale;

        if(Double.isNaN(hipassf)) return 1;
        if(hipassf < 0.0) return 1;

        return (int) Math.ceil(hipassf / df);
    }


    protected void levelDataForChannel(Channel channel) {
        levelForChannel(channel, data);
    }

    protected void levelForChannel(Channel channel, float[] signal) {	
        final int c = channel.getIndex();

        final float ave = (float) integration.validParallelStream(Frame.MODELING_FLAGS).filter(f -> f.sampleFlag[c] != 0).mapToDouble(f -> signal[f.index]).average().orElse(0.0);

        if(ave != 0.0) IntStream.range(0,  integration.size()).parallel().forEach(t -> signal[t] -= ave);
        else Arrays.fill(signal, 0, integration.size(), 0.0F);
    }

    protected void levelData() { level(data); }

    protected void level(float[] signal) {
        final float level = Statistics.mean(signal);
        if(!Double.isNaN(level)) IntStream.range(0,  integration.size()).parallel().forEach(t -> signal[t] = Float.isNaN(signal[t]) ? 0.0F : signal[t] - level);
    }

    public void setDFT(boolean value) { dft = value; }

    public boolean isDFT() { return dft; }

    protected void dftFilter(Channel channel, int F, double rejection, float[] rejected) {		
        double sumc = 0.0, sums = 0.0;

        if(F == 0) F = data.length >> 1;

        final double theta = F * Constant.twoPi / data.length;
        final double s0 = Math.sin(theta);
        final double c0 = Math.cos(theta);

        double c = 1.0;
        double s = 0.0;

        // 27 real ops per frequency...
        for(int t = integration.size(); --t >= 0; ) {
            final double x = data[t];

            sumc += c * x;
            sums += s * x;

            final double temp = c;
            c = temp * c0 - s * s0;
            s = temp * s0 + s * c0;
        }

        final double norm = 2.0 / data.length * rejection;
        sumc *= norm;
        sums *= norm;

        c = 1.0;
        s = 0.0;

        // 25 real ops per frequency
        for(int t = integration.size(); --t >= 0; ) {
            rejected[t] += c * sumc + s * sums;

            final double temp = c;
            c = temp * c0 - s * s0;
            s = temp * s0 + s * c0;
        }	
    }

    // Get a fixed-length representation of the filter response.
    protected void getFilterResponse(float[] response) {
        final double n = (double) (nf+1) / response.length;

        for(int i=response.length; --i >= 0; ) {
            final int fromf = (int) Math.round(i * n); 
            final int tof = (int) Math.round((i+1) * n);

            if(tof == fromf) response[i] = (float) responseAt(fromf);
            else response[i] = (float) IntStream.range(fromf, tof).mapToDouble(f -> responseAt(f)).sum() / (tof - fromf);
        }

    }
    
    protected double getSourceProfile(int f, double crossingTime, double modulationFrequency) {
        // Assume Gaussian source profile under crossing time
        // sigmaT sigmaw = 1
        // T/2.35 * 2pi * sigmaf = 1
        // sigmaf = 2.35/2pi * 1/T;
        // df = 1 / (n dt)
        // sigmaF = sigmaf / df = 2.35/2Pi * n dt / T; 
        
        double f0 = modulationFrequency / df;
        final double sigma = Constant.sigmasInFWHM / (Constant.twoPi * crossingTime * df);
        final double a = -0.5 / (sigma * sigma);

        return 0.5 * (Math.exp(a*(f-f0)*(f-f0)) + Math.exp(a*(f+f0)*(f+f0)));
    }

    protected double calcPointResponse(Channel channel) {
        // Start from the 1/f filter cutoff
        final int minf = getHipassIndex();
        final double T = integration.getCrossingTime(channel.getResolution());
        final double f0 = integration.getModulationFrequency(Frame.TOTAL_POWER) / df;
        
        // Below the hipass time-scale, the filter has no effect, so count it as such...
        double sum = IntStream.range(0, minf).parallel().mapToDouble(f -> getSourceProfile(f, T, f0)).sum();
    
        double sourceNorm = sum + IntStream.rangeClosed(minf, nf).parallel().mapToDouble(f -> getSourceProfile(f, T, f0)).sum();
        
        // Calculate the true source filtering above the hipass timescale...
        sum += IntStream.rangeClosed(minf, nf).parallel().mapToDouble(f -> getSourceProfile(f, T, f0) * responseAt(f)).sum();

        return sum / sourceNorm;
    }

}
