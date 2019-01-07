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

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Vector;

import crush.instrument.ChannelGroup;
import crush.instrument.FieldGainProvider;
import crush.instrument.GainProvider;
import crush.instrument.Response;
import jnum.ExtraMath;
import jnum.data.WeightedPoint;
import jnum.math.Range;


public class Mode implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -1953090499269762683L;

    public String name;
    private ChannelGroup<?> channels;
    public GainProvider gainProvider;

    Vector<CoupledMode> coupledModes;

    public boolean fixedGains = false;
    public boolean phaseGains = false;
    public double resolution;
    public Range gainRange = Range.getFullRange();
    public int gainFlag = 0;
    public int gainType = Instrument.GAINS_BIDIRECTIONAL;

    private static int counter = 0;
    
    private float[] gain;

    
    public Mode(String name) {
        this.name = name;
    }
    
    public Mode() {
        this("mode-" + (counter+1));
    }

    public Mode(ChannelGroup<?> group) {
        this(group.getName());
        setChannels(group);
    }

    public Mode(ChannelGroup<?> group, GainProvider gainProvider) { 
        this(group);
        setGainProvider(gainProvider);
    }

    public Mode(ChannelGroup<?> group, Field gainField) { 
        this(group, new FieldGainProvider(gainField));
    }


    public void setGainProvider(GainProvider source) {
        this.gainProvider = source;
    }	


    public String getName() { return name; }	

    public ChannelGroup<?> getChannels() { return channels; }

    public int getChannelCount() { return channels.size(); }

    public void setChannels(ChannelGroup<?> group) {
        channels = group;
        name = group.getName();
        if(coupledModes != null) for(CoupledMode mode : coupledModes) mode.setChannels(group);
    }

    public Channel getChannel(int k) { return channels.get(k); }

    public int size() { return channels.size(); }

    private void addCoupledMode(CoupledMode m) {
        if(coupledModes == null) coupledModes = new Vector<CoupledMode>();
        coupledModes.add(m);        
    }


    public final float[] getGains() throws Exception {
        return getGains(true);
    }

 
    private void applyProviderGains(boolean validate) throws Exception {
        if(validate) gainProvider.validate(this);
       
        for(int c=channels.size(); --c >= 0; ) {
            gain[c] = (float) gainProvider.getGain(channels.get(c));
            if(Float.isNaN(gain[c])) gain[c] = 0.0F;
        }
    }
    
    /**
     * Returns a temporarily valid gain array for immediate use. The same array is updated every time
     * getGains() is called, hence the stored values may change. 
     * 
     * @param validate
     * @return
     * @throws Exception
     */
    public float[] getGains(boolean validate) throws Exception {
        if(gain == null) {
            gain = new float[channels.size()];
            Arrays.fill(gain, 1.0F);
        }
        else if(gain.length != channels.size()) 
            throw new IllegalStateException("Gain array size differs from mode channels.");
        
        if(gainProvider != null) applyProviderGains(validate);
        return gain;
    }

    
    public final boolean setGains(float[] gain) throws Exception {
        return setGains(gain, true);
    }

    public int getSkipChannelFlags() { return 0; }

    // Return true if flagging...
    public boolean setGains(float[] gain, boolean flagNormalized) throws Exception {
        if(gainProvider == null) this.gain = gain;
        else for(int c=channels.size(); --c>=0; ) gainProvider.setGain(channels.get(c), gain[c]);
        return flagGains(flagNormalized);
    }

    public void uniformGains() throws Exception {
        float[] G = new float[channels.size()];
        Arrays.fill(G, 1.0F);
        setGains(G, false);
    }

    protected boolean flagGains(boolean normalize) throws Exception {
        if(gainFlag == 0) return false;

        final float[] gain = getGains();
        final float aveG = normalize ? (float) channels.getTypicalGainMagnitude(gain, ~gainFlag) : 1.0F;

        for(int k=channels.size(); --k >= 0; ) {
            final Channel channel = channels.get(k);

            float G = Float.NaN;
            if(gainType == Instrument.GAINS_SIGNED) G = gain[k] / aveG;
            else if(gainType == Instrument.GAINS_BIDIRECTIONAL) G = Math.abs(gain[k] / aveG);

            if(!gainRange.contains(G)) channel.flag(gainFlag);
            else channel.unflag(gainFlag);
        }
        return true;
    }	



    public WeightedPoint[] deriveGains(Integration<?> integration, boolean isRobust) throws Exception {
        if(fixedGains) throw new IllegalStateException("Cannot solve gains for fixed gain modes.");

        float[] G0 = getGains();
        
        WeightedPoint[] G = phaseGains && integration.isPhaseModulated()
                ? ((PhaseModulated) integration).getPhases().getGainIncrement(this) 
                : integration.getSignal(this).getGainIncrement(isRobust);
                
        for(int i=G0.length; --i >= 0; ) {
            if(G[i] != null) G[i].add(G0[i]);
            else G[i] = new WeightedPoint(G0[i], 0.0);
        }

        return G;		
    }

    protected void syncAllGains(Integration<?> integration, float[] sumwC2, boolean isTempReady) throws Exception {			
        integration.getSignal(this).syncGains(sumwC2, isTempReady);

        // Solve for the correlated phases also, if required
        if(integration.isPhaseModulated()) if(integration.hasOption("phases"))
            ((PhaseModulated) integration).getPhases().syncGains(this);

        // Sync the gains to all the dependent modes too... 
        if(coupledModes != null) for(CoupledMode mode : coupledModes) mode.resyncGains(integration);
    }

    public int getFrameResolution(Integration<?> integration) {
        return integration.power2FramesFor(resolution/Math.sqrt(2.0));
    }

    public int signalLength(Integration<?> integration) {
        return ExtraMath.roundupRatio(integration.size(), getFrameResolution(integration));
    }

    @Override
    public String toString() {
        String description = name + ":";
        for(Channel channel : channels) description += " " + channel.getID();
        return description;
    }


    public class CoupledMode extends CorrelatedMode {

        /**
         * 
         */
        private static final long serialVersionUID = -1524809691029533295L;

        public CoupledMode() {
            super(Mode.this.getChannels());
            Mode.this.addCoupledMode(this);
            name = getClass().getSimpleName().toLowerCase() + "-" + Mode.this.name;
            fixedGains = true;
        }

        public CoupledMode(float[] gains) throws Exception {
            this();
            super.setGains(gains);
        }

        public CoupledMode(GainProvider gains) { 
            this();
            setGainProvider(gains);
        }
        
        public CoupledMode(Field gainField) {
            this(new FieldGainProvider(gainField));
        }


        @Override
        public float[] getGains(boolean validate) throws Exception {
            final float[] parentgains = Mode.this.getGains(validate);
            final float[] gains = super.getGains(validate);
             
            for(int i=gains.length; --i>=0; ) gains[i] *= parentgains[i];
            
            return gains;
        }

        @Override
        public boolean setGains(float[] gain, boolean flagNormalized) throws IllegalAccessException {
            throw new UnsupportedOperationException("Cannot adjust gains for " + getClass().getSimpleName());
        }

        // Recursively resync all dependent modes...
        protected void resyncGains(Integration<?> integration) throws Exception {
            Signal signal = integration.getSignal(this);
            if(signal != null) signal.resyncGains();

            // Sync the gains to all the dependent modes too... 
            if(coupledModes != null) for(CoupledMode mode : coupledModes) mode.resyncGains(integration);
        }

    }
    
    public class NonLinearResponse extends Response {
        /**
         * 
         */
        private static final long serialVersionUID = -3060028666043495588L;
       
        public NonLinearResponse() {
            setChannels(Mode.this.getChannels());
            name = "nonlinear-" + Mode.this.name;
        }
  
        public NonLinearResponse(GainProvider gainSource) {
            this();
            setGainProvider(gainSource);
        }
        
        @Override
        public Signal getSignal(Integration<?> integration) {
            Signal signal = integration.getSignal(Mode.this);
            
            float[] C2 = new float[signal.value.length];
            Signal s2 = new Signal(this, integration, C2, false);
            
            updateSignal(integration);
            
            return s2;
        }
        
        public void updateSignal(Integration<?> integration) {
            Signal c = integration.getSignal(Mode.this);
            Signal c2 = integration.getSignal(NonLinearResponse.this);
            
            for(int i=c2.value.length; --i >= 0; ) {
                float C = c.value[i];
                if(c.drifts != null) C += c.drifts[i / c.driftN];
                c2.value[i] = C * C;
            }
            
            // Remove drifts from the squared signal... 
            if(c.drifts != null) c2.removeDrifts(c.driftN * c.resolution, false);  
        }
        
        @Override
        public WeightedPoint[] deriveGains(Integration<?> integration, boolean isRobust) throws Exception {
            // Undo the prior nonlinearity correction....
            setGains(new float[size()]);
            syncAllGains(integration, null, true);       
            
            // Calculated the new nonlinearity signal...
            updateSignal(integration);
            
            // Calculate new gains...
            return super.deriveGains(integration, isRobust);
        }
        
    }



    protected static int nextMode = 0;
    public final static int TOTAL_POWER = nextMode++;
    public final static int CHOPPED = nextMode++;

}
