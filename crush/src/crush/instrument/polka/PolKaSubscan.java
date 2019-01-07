/*******************************************************************************
 * Copyright (c) 2017 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.polka;

import crush.*;
import crush.filters.Filter;
import crush.instrument.laboca.*;
import crush.polarization.HWPFilter;
import crush.polarization.PolarModulation;
import crush.telescope.apex.APEXScan;
import jnum.Constant;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;
import jnum.math.Vector2D;
import jnum.parallel.ParallelTask;

import java.util.*;

public class PolKaSubscan extends LabocaSubscan implements Periodic, Purifiable {
    /**
     * 
     */
    private static final long serialVersionUID = -901946410688579472L;
    double meanTimeStampDelay = Double.NaN;
    boolean hasTimeStamps = true;


    WeightedPoint[] tpWaveform, dw;
    double dAngle;

    
    public PolKaSubscan(APEXScan<LabocaSubscan> parent) {
        super(parent);
    }
    
    @Override
    public PolKaScan getScan() { return (PolKaScan) super.getScan(); }
    
    @Override
    public PolKa getInstrument() { return (PolKa) super.getInstrument(); }

    @Override
    public double getModulationFrequency(int signalMode) {
        switch(signalMode) {
        case PolarModulation.Q : 
        case PolarModulation.U : return 4.0 * getWaveplateFrequency();
        default: return super.getModulationFrequency(signalMode);
        }
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("wpdelay")) return meanTimeStampDelay / Unit.ms;
        if(name.equals("wpok")) return hasTimeStamps;
        return super.getTableEntry(name);
    }

    @Override
    public Filter getFilter(String name) {
        name = name.toLowerCase();
        if(name.equals("hwp")) return new HWPFilter(this, filter.getTempData());
        return super.getFilter(name);
    }


    @Override
    public int getPeriod(int mode) {
        return (int)Math.round(getInstrument().samplingInterval / (4.0*getWaveplateFrequency()));
    }

    public double getWaveplateFrequency() {
        return getInstrument().waveplateFrequency;
    }

    /*
	@Override
	public double getCrossingTime(double sourceSize) {		
		return 1.0 / (1.0/super.getCrossingTime() + 4.0 * getWaveplateFrequency());
	}
     */

    @Override
    public void validate() {
        PolKa polka = getInstrument();

        if(!polka.hasAnalyzer()) {
            super.validate();
            return;
        }

        reindex();

        for(Frame exposure : this) if(exposure != null) ((PolKaFrame) exposure).loadWaveplateData();

        // TODO could it be that MJD options not yet set here?

        if(!isWaveplateValid() || hasOption("waveplate.tp")) { 
            hasTimeStamps = false;
            warning("Invalid waveplate data. Will attempt workaround...");
            //!calcMeanWavePlateFrequency();
            LabocaPixel channel = hasOption("waveplate.tpchannel") ?
                    new ChannelLookup<LabocaPixel>(polka).get(option("waveplate.tpchannel").getValue()) : polka.referencePixel;
                    setTPPhases(channel);
        }
        else if(hasOption("waveplate.fix")) fixAngles();
        else if(hasOption("waveplate.frequency")) {
            info("Setting waveplate frequency " + Util.f3.format(getWaveplateFrequency()) + " Hz.");
            warning("Phaseless polarization (i.e. uncalibrated angles).");
        }
      
                

        if(hasOption("waveplate.tpchar")) {
            trim();

            LabocaPixel channel = hasOption("waveplate.tpchannel") ? new ChannelLookup<LabocaPixel>(polka).get(option("waveplate.tpchannel").getValue())
                : polka.referencePixel;

            measureTPPhases(channel);
            setTPPhases(channel);
        }

        super.validate();	

        
        //if(hasOption("analyzer.detect")) detectAnalyzer();    
        //else 
        
        if(hasOption("purify")) removeTPModulation();
    }

    
    
    /*
    // TODO this needs to be verified and improved as necessary...
    public void detectAnalyzer() {      
        if(tpWaveform == null) removeTPModulation();      
        
        Range r = new Range();
        for(WeightedPoint p : tpWaveform) if(p.weight() > 0.0) r.include(p.value());
        
        final double threshold = r.min() + 0.25 * r.span();
        boolean onPeak = false;
        int peaks = 0;
        
        for(WeightedPoint p : tpWaveform) if(p.weight() > 0.0) {
            if(onPeak) {
                if(p.value() < threshold) onPeak = false;
            }
            else {
                if(p.value() > threshold) {
                    onPeak = true;
                    peaks++;
                }
            }
        }
            
        // Helmut's observation is that the H analyzer is double-peaked, whereas the V analyzer is single peaked...
        PolKa polka = (PolKa) instrument;
        if(peaks == 1) polka.analyzerPosition = PolKa.ANALYZER_V;
        else if(peaks == 2) polka.analyzerPosition = PolKa.ANALYZER_H;
        else {
            warning("Cound not detect analyzer. Total power modulation has " + peaks + " peaks.");
            polka.analyzerPosition = PolKa.ANALYZER_UNKNOWN;
            return;
        }
        
        info("Detected analyzer position is " + PolKa.analyzerIDs[polka.analyzerPosition]);
        
    }
    */


    public void removeTPModulation() {
        PolKa polka = getInstrument();

        // If the waveplate is not rotating, then there is nothing to do...
        if(!(polka.waveplateFrequency > 0.0)) return;

        if(tpWaveform == null) {
            double oversample = hasOption("waveplate.oversample") ? option("waveplate.oversample").getDouble() : 1.0;
            int n = (int)Math.round(oversample / (polka.samplingInterval * polka.waveplateFrequency));

            tpWaveform = WeightedPoint.createArray(n);
            dw = WeightedPoint.createArray(n);
            dAngle = Constant.twoPi / n;
        }
        else for(int i=dw.length; --i >= 0; ) dw[i].noData();

        comments.append("P(" + dw.length + ") ");

        final ChannelGroup<?> channels = polka.getObservingChannels();
        final Dependents parms = getDependents("tpmod");

        parms.clear(channels, 0, size());

        channels.new Fork<float[]>() {
            private float[] frameParms;

            @Override
            protected void init() {
                super.init();
                frameParms = getFloats();
                Arrays.fill(frameParms,  0, size(), 0.0F);
            }

            @Override
            public float[] getLocalResult() { return frameParms; }

            @Override
            protected void postProcess() {
                super.postProcess();

                for(ParallelTask<float[]> task : getWorkers()) {
                    float[] localFrameParms = task.getLocalResult();
                    parms.addForFrames(localFrameParms);
                    recycle(localFrameParms);
                }
            }

            @Override
            protected void process(Channel channel) {
                for(int i=dw.length; --i >= 0; ) dw[i].noData();
                final int c = channel.index;

                for(LabocaFrame exposure : PolKaSubscan.this) if(exposure != null) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) {
                    if(exposure.sampleFlag[c] != 0) continue;

                    final double normAngle = Math.IEEEremainder(((PolKaFrame) exposure).waveplateAngle, Constant.twoPi) + Math.PI;
                    final WeightedPoint point = dw[(int)Math.floor(normAngle / dAngle)];

                    point.add(exposure.relativeWeight * exposure.data[c]);
                    point.addWeight(exposure.relativeWeight);
                }

                for(int i=dw.length; --i >= 0; ) {
                    final WeightedPoint point = dw[i];
                    if(point.weight() > 0.0) {
                        point.scaleValue(1.0 / point.weight());
                        tpWaveform[i].add(point.value());
                        tpWaveform[i].setWeight(point.weight());
                        parms.addAsync(channel, 1.0);
                    }
                }

                for(LabocaFrame exposure : PolKaSubscan.this) if(exposure != null) {
                    final double normAngle = Math.IEEEremainder(((PolKaFrame) exposure).waveplateAngle, Constant.twoPi) + Math.PI;
                    final WeightedPoint point = dw[(int)Math.floor(normAngle / dAngle)];

                    exposure.data[c] -= point.value();
                    if(point.weight() > 0.0) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] != 0)
                        frameParms[exposure.index] += exposure.relativeWeight / point.weight();
                }
            }

        }.process();

        parms.apply(channels, 0, size());
    }


    @Override
    public void purify() {
        if(hasOption("purify")) removeTPModulation();
    }

    @Override
    public LabocaFrame getFrameInstance() {
        return new PolKaFrame(getScan());
    }

    @Override
    public Vector2D getTauCoefficients(String id) {
        if(id.equals(getInstrument().getName())) return getTauCoefficients("laboca");
        return super.getTauCoefficients(id);
    }

    public ArrayList<Double> getMJDCrossings() {
        PolKa polka = getInstrument();
        Channel offsetChannel = polka.offsetChannel;
        if(offsetChannel == null) return null;
        int c = offsetChannel.index;

        ArrayList<Double> crossings = new ArrayList<Double>();
        double lastCrossing = Double.NaN;
        double tolerance = 100 * Unit.ms / Unit.day;

        // dt = frametime - hwptime
        // --> hwptime = frametime - dt;
        for(int t=0; t<size(); t++) {
            Frame exposure = get(t);
            if(exposure == null) continue;
            double MJD = exposure.MJD - exposure.data[c] * Unit.ms / Unit.day;
            if(!(Math.abs(MJD - lastCrossing) <= tolerance)) {
                lastCrossing = MJD;
                crossings.add(MJD);
            }   
        }
     
        return crossings;
    }

   
   
    private void fixAngles() throws IllegalStateException {	
        PolKa polka = getInstrument();

        StringBuffer buf = new StringBuffer();
        buf.append("Fixing waveplate: ");

        ArrayList<Double> crossings = getMJDCrossings();
        buf.append(crossings.size() + " crossings, ");
        
        double mjdPeriod = getMedianMJDPeriod(crossings);
        
        polka.waveplateFrequency = 1.0 / (mjdPeriod * Unit.day);
        
        // Detect and apply standard rotation values when possible
        if(Math.abs(polka.waveplateFrequency - 1.56/Unit.s) < 0.05/Unit.s) polka.waveplateFrequency = 1.56 / Unit.s; 
        else if(Math.abs(polka.waveplateFrequency - 1.00/Unit.s) < 0.05/Unit.s) polka.waveplateFrequency = 1.00 / Unit.s; 
        else polka.waveplateFrequency = 0.01 * Math.round(100.0 * polka.waveplateFrequency);
    
        buf.append("f = " + Util.f3.format(polka.waveplateFrequency) + " Hz,");
        
        mjdPeriod = 1.0 / polka.waveplateFrequency / Unit.day;      
         
        double delay = selectDelay(crossings, mjdPeriod, 0.5);  
        buf.append("delay = " + Util.f1.format(delay / Unit.ms) + "ms.");

        double MJD0 = crossings.get(0) + delay / Unit.day;
      
        info(new String(buf));

        for(Frame exposure : this) if(exposure != null) {
            PolKaFrame frame = (PolKaFrame) exposure;
            frame.waveplateAngle = Constant.twoPi * Math.IEEEremainder((frame.MJD - MJD0) / mjdPeriod, 1.0);
            frame.waveplateFrequency = polka.waveplateFrequency;
        }

    } 
    
    public double getMedianMJDPeriod(ArrayList<Double> crossings) {
        double dMJD = Double.NaN;  
        for(int i=0; i<3; i++) dMJD = getMedianMJDPeriod(crossings, dMJD); 
        return dMJD;
    }
    
    private double getMedianMJDPeriod(ArrayList<Double> crossings, double dMJD) {   
        final double[] sorter = new double[crossings.size()];
        int k=0;
        
        for(int i=crossings.size(); --i > 0; ) {
            double delta = crossings.get(i) - crossings.get(i-1);
            if(Double.isNaN(delta)) continue;
            if(!Double.isNaN(dMJD)) if(Math.abs(delta - dMJD) > 0.5 * dMJD) continue;
            sorter[k++] = delta;
        }
        
        return Statistics.Inplace.median(sorter, 0, k);
    }

   

    public double selectDelay(ArrayList<Double> crossings, double mjdPeriod, double selectionPoint) {
        final double[] delays = new double[crossings.size()];
        final double MJD0 = crossings.get(0);
        
        int k=0;

        for(int i=crossings.size(); --i >= 0; ) {
            if(Double.isNaN(crossings.get(i))) continue;      
            delays[k++] = Math.IEEEremainder(crossings.get(i) - MJD0, mjdPeriod);
        }

        double medianDelay = Statistics.Inplace.select(delays, selectionPoint, 0, k);

        return medianDelay * Unit.day;
    }



    // Check the waveplate for the bridge error during 2011 Dec 6-8, when 
    // angles were written as zero...
    public boolean isWaveplateValid() {
        for(Frame exposure : this) if(exposure != null) if(((PolKaFrame) exposure).waveplateAngle != 0.0) return true;
        return false;		
    }

    public void measureTPPhases(Channel channel) {
        PolKa polka = getInstrument();
        PolKaFrame firstFrame = (PolKaFrame) get(0);

        //if(firstFrame.waveplateAngle == 0.0) warning("Zero waveplate angle.");

        double phase1 = getMeanTPPhase(channel, polka.waveplateFrequency) + firstFrame.waveplateAngle;
        double phase2 = getMeanTPPhase(channel, 2.0 * polka.waveplateFrequency) + 2.0 * firstFrame.waveplateAngle;
        double phase4 = getMeanTPPhase(channel, 4.0 * polka.waveplateFrequency) + 4.0 * firstFrame.waveplateAngle;

        info("Measured TP Phases: "
                + Util.f1.format(Math.IEEEremainder(phase1, Constant.twoPi) / Unit.deg) + ", "
                + Util.f1.format(Math.IEEEremainder(phase2, Constant.twoPi) / Unit.deg) + ", "
                + Util.f1.format(Math.IEEEremainder(phase4, Constant.twoPi) / Unit.deg) + " deg"
                );
    }

    public void setTPPhases(Channel channel) {
        info("Reconstructing waveplate angles from TP modulation.");

        PolKa polka = getInstrument();

        int harmonic = hasOption("waveplate.tpharmonic") ? option("waveplate.tpharmonic").getInt() : 2;

        //if(firstFrame.waveplateAngle == 0.0) warning("Zero waveplate angle.");
        String analyzer = "analyzer." + PolKa.analyzerIDs[polka.analyzerPosition].toLowerCase() + ".";

        double delta = option(analyzer + "phase").getDouble() * Unit.deg; 
        double phase = getMeanTPPhase(channel, harmonic * polka.waveplateFrequency);
        double alpha = (delta - phase) / harmonic;

        info("---> Using phase " + Util.f1.format(delta / Unit.deg) + " deg for pixel " + channel.getID() + ".");

        if(hasOption("waveplate.tpchar")) {
            PolKaFrame firstFrame = (PolKaFrame) get(0);
            warning("Reconstruction error: " + 
                    Util.f1.format(Math.IEEEremainder(alpha - firstFrame.waveplateAngle, Constant.twoPi) / Unit.deg) +
                    " deg.");
        }

        final double w = Constant.twoPi * polka.waveplateFrequency * polka.integrationTime;
        for(Frame exposure : this) if(exposure != null) {
            PolKaFrame frame = (PolKaFrame) exposure;
            frame.waveplateAngle = Math.IEEEremainder(alpha + w * frame.index, Constant.twoPi);
        }

    }

    // Calculate the average TP phases at a given frequency...
    private double getMeanTPPhase(Channel channel, double freq) {		
        final int c = channel.index;
        final double w = Constant.twoPi * freq * getInstrument().integrationTime;
        double sumc = 0.0, sums = 0.0;
        int n = 0;

        for(Frame exposure : this) if(exposure != null) {
            final double value = exposure.data[c];
            final double theta = w * exposure.index;
            sumc += Math.cos(theta) * value;
            sums += Math.sin(theta) * value;
            n++;
        }
        sumc *= 2.0/n;
        sums *= 2.0/n;

        info("---> TP: " + Util.e3.format(ExtraMath.hypot(sums, sumc))
        + " @ " + Util.f3.format(freq) + "Hz");

        return Math.atan2(sums, sumc);
    }

}

