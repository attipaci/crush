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

package crush.motion;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import crush.CRUSH;
import crush.Channel;
import crush.Frame;
import crush.Integration;
import crush.PhaseData;
import crush.PhaseSet;
import crush.Pixel;
import crush.telescope.TelescopeFrame;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;
import jnum.fft.DoubleFFT;
import jnum.math.Vector2D;

public class ChopperPhases extends PhaseSet {
    /**
     * 
     */
    private static final long serialVersionUID = 6319712806053123689L;

    private Chopper chopper;
    
    
    public ChopperPhases(Integration<? extends TelescopeFrame> integration, Chopper chopper) {
        super(integration);
        this.chopper = chopper;
        this.chopper.phases = this;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Integration<? extends TelescopeFrame> getIntegration() { 
        return (Integration<? extends TelescopeFrame>) super.getIntegration();
    }
 
    
    @Override
    public double deriveRawRelativeWeightFor(Channel channel) {    
        double chi2 = getLRChi2(channel, getLROffset(channel).value());
        if(Double.isNaN(chi2)) return Double.NaN; 
        return 1.0 / chi2;
    }

        
    public WeightedPoint getChopSignal(final Channel channel, final int i) {
        final PhaseData A = get(i);
        final PhaseData B = get(i-1);
        
        // Check that it's a left/right chop pair...
        if(A.phase == TelescopeFrame.CHOP_LEFT && B.phase != TelescopeFrame.CHOP_RIGHT) return new WeightedPoint();
        if(B.phase == TelescopeFrame.CHOP_LEFT && A.phase != TelescopeFrame.CHOP_RIGHT) return new WeightedPoint();
        
        // Check that neither phase is flagged...
        if(A.isFlagged(channel) || B.isFlagged(channel)) return new WeightedPoint();
        
        final WeightedPoint signal = A.getValue(channel);      
        signal.subtract(B.getValue(channel));
        
        if((A.phase & TelescopeFrame.CHOP_LEFT) == 0) signal.scale(-1.0);
        return signal;
    }
    
    
    public WeightedPoint getBGCorrectedChopSignal(final Channel channel, final int i, final Collection<? extends Channel> bgChannels, final double[] G) {
        final WeightedPoint bg = new WeightedPoint();
        
        for(final Channel bgChannel : bgChannels) if(!bgChannel.isFlagged()) if(bgChannel != channel) if(bgChannel.sourcePhase == 0) {
            final WeightedPoint lr = getChopSignal(bgChannel, i);
            if(G[bgChannel.getIndex()] == 0.0) continue;
            lr.scale(1.0 / G[bgChannel.getIndex()]);
            bg.average(lr);
        }

        final WeightedPoint value = getChopSignal(channel, i);
        
        if(bg.weight() > 0.0) {
            bg.scale(G[channel.getIndex()]);  
            value.subtract(bg);
        }
        
        return value;
    }

    
    public void writeLROffset(final Channel channel, String fileName, final Collection<? extends Channel> bgChannels, final double[] sourceGain) throws IOException {  
        final Integration<?> subscan = getIntegration();
        
        if(!(subscan instanceof Chopping)) return;
        Chopping chopped = (Chopping) subscan;
        
        try(final PrintWriter out = new PrintWriter(new FileOutputStream(fileName))) {
            out.println("# CRUSH Photometry Nod-cycle Data");
            out.println("# =============================================================================");
            out.println(subscan.getASCIIHeader());
            out.println("# Chop Frequency: " + Util.f3.format(chopped.getChopper().frequency / Unit.Hz) + "Hz"); 
            out.println("# Pixel: " + channel.getID());
            out.println("# Source Phase: " + channel.sourcePhase);
            out.println();
            out.println("# chop#\tSignal\t\tCorrected");


            for(int i=1; i < size(); i++) out.println("  " + i
                    + "\t" + getChopSignal(channel, i).toString(Util.e5)
                    + "\t" + getBGCorrectedChopSignal(channel, i, bgChannels, sourceGain).toString(Util.e5));

            out.println();
            out.println();
            out.close();
        }
    }
    
    public void writeLRSpectrum(final Channel channel, String fileName) throws IOException {
        final Integration<?> subscan = getIntegration();
        
        if(!(subscan instanceof Chopping)) return;
        Chopping chopped = (Chopping) subscan;
        
        try(final PrintWriter out = new PrintWriter(new FileOutputStream(fileName))) {
            out.println("# CRUSH Photometry Nod-cycle Spectrum");
            out.println("# =============================================================================");
            out.println(subscan.getASCIIHeader());
            out.println("# Chop Frequency: " + Util.f3.format(chopped.getChopper().frequency / Unit.Hz) + "Hz"); 
            out.println("# Pixel: " + channel.getID());
            out.println("# Source Phase: " + channel.sourcePhase);
            out.println();
            out.println("# Freq(Hz)\tAmplitude\tPhase(deg)");

            final double[] data = new double[ExtraMath.pow2ceil(size())];
            final double dF = 0.5 * chopped.getChopper().frequency / data.length;
            final double mean = getLROffset(channel).value();

            for(int i=1; i < size(); i++) {
                final WeightedPoint point = getChopSignal(channel, i);
                point.subtract(mean);
                data[i] = DataPoint.significanceOf(point);
            }

            new DoubleFFT(CRUSH.executor).real2Amplitude(data);

            out.println(data[0]);
            for(int i=2; i < data.length; i+=2) {
                out.println("  " + (i*dF) + "\t" + Util.e5.format(ExtraMath.hypot(data[i], data[i+1])) + "\t" + Util.f5.format(Math.atan2(data[i+1], data[i])));
            }
            out.println(data[1]);

            out.println();
            out.println();
            out.close();
        }
    }
    
    public WeightedPoint getLROffset(final Channel channel) {
        final WeightedPoint lr = new WeightedPoint();
        for(int i=1; i < size(); i++) lr.average(getChopSignal(channel, i));
        return lr;
    }
    
    public WeightedPoint getMedianLROffset(final Channel channel) {
        WeightedPoint[] points = new WeightedPoint[size()];
        int k=0;

        for(int i=1; i < size(); i++) points[k++] = getChopSignal(channel, i);
        return Statistics.Destructive.smartMedian(points, 0, k, 0.25);
    }
    
    public WeightedPoint getBGCorrectedLROffset(final Channel channel, final Collection<? extends Channel> bgChannels, final double[] sourceGain) {
        final WeightedPoint lr = new DataPoint();
        for(int i=1; i < size(); i++) lr.average(getBGCorrectedChopSignal(channel, i, bgChannels, sourceGain));
        return lr;
    }

    public WeightedPoint getBGCorrectedMedianLROffset(final Channel channel, final Collection<? extends Channel> bgChannels, final double[] sourceGain) {
        WeightedPoint[] points = new WeightedPoint[size()];
        int k=0;

        for(int i=1; i < size(); i++) points[k++] = getBGCorrectedChopSignal(channel, i, bgChannels, sourceGain);
        return Statistics.Destructive.smartMedian(points, 0, k, 0.25);
    }
    
    
    public double getLRChi2(final Channel channel, final double mean) { 
        double chi2 = 0.0;
        int n = 0;
        
        for(int i=1; i < size(); i++) {
            final WeightedPoint LR = getChopSignal(channel, i);
            if(LR.weight() <= 0.0) continue;
            
            LR.subtract(mean);

            final double chi = DataPoint.significanceOf(LR);
            chi2 += chi * chi;
            n++;
        }
        
        channel.dof = (n + 1) - channelParms[channel.getIndex()];
        
        return channel.dof > 0.0 ? chi2 / channel.dof : Double.NaN;
    }

    public double getBGCorrectedLRChi2(final Channel channel, final Collection<? extends Channel> bgChannels, final double mean, final double[] sourceGain) {    
        double chi2 = 0.0;
        int n = 0;
        
        for(int i=1; i < size(); i++) {
            final WeightedPoint LR = getBGCorrectedChopSignal(channel, i, bgChannels, sourceGain);
            if(LR.weight() == 0.0) continue;
            
            LR.subtract(mean);

            final double chi = DataPoint.significanceOf(LR);
            chi2 += chi * chi;
            n++;
        }
        
        channel.dof = n - channelParms[channel.getIndex()];
        
        return channel.dof > 0.0 ? chi2 / channel.dof : Double.NaN;
    }
    
    
    
    public void mark(Vector2D left, Vector2D right, double tolerance) {
        // Flag pixels that chop on source
        // left and right are the pixel positions, where, if there's a pixel, it will have the source
        // in the left or right beams...
        //info("on phase is " + subscan[i][k].onPhase);
        
        StringBuffer buf = new StringBuffer();
        
        buf.append("Marking Chopper Phases... ");
    
        for(Pixel pixel : getInstrument().getPixels()) {
            Vector2D position = pixel.getPosition();
            
            if(position.distanceTo(left) < tolerance) for(Channel channel : pixel) {
                channel.sourcePhase |= TelescopeFrame.CHOP_LEFT;
                buf.append(" L" + channel.getID());
            }
            else if(position.distanceTo(right) < tolerance) for(Channel channel : pixel) {
                channel.sourcePhase |= TelescopeFrame.CHOP_RIGHT;
                buf.append(" R" + channel.getID()); 
            }
            else for(Channel channel : pixel) channel.sourcePhase &= ~TelescopeFrame.CHOP_FLAGS;
        }
        
        Integration<? extends TelescopeFrame> integration = getIntegration();
        
        integration.info(new String(buf));
        
        int usable = 0;
        
        // Flag frames according to chopper phase ---> left, right, transit.
        PhaseData current = new PhaseData(integration);

        int transitFlag = TelescopeFrame.CHOP_TRANSIT | Frame.SKIP_MODELING | Frame.SKIP_WEIGHTING | Frame.SKIP_SOURCE_MODELING;
        
        for(TelescopeFrame exposure : integration) if(exposure != null) {
            exposure.unflag(TelescopeFrame.CHOP_FLAGS);
                
            if(Math.abs(exposure.chopperPosition.x() + chopper.amplitude) < tolerance) {
                exposure.flag(TelescopeFrame.CHOP_LEFT);
                if(current.phase != TelescopeFrame.CHOP_LEFT) {
                    current = new PhaseData(integration);
                    current.phase = TelescopeFrame.CHOP_LEFT;
                    //if(current.phase == nodPhase) current.flag |= PhaseOffsets.SKIP_GAINS;
                    current.start = exposure;
                    current.end = exposure;
                    if(current.phase != 0) add(current);
                }
                else current.end = exposure;
                usable++;
            }
            else if(Math.abs(exposure.chopperPosition.x() - chopper.amplitude) < tolerance) {
                exposure.flag(TelescopeFrame.CHOP_RIGHT);
                if(current.phase != TelescopeFrame.CHOP_RIGHT) {
                    current = new PhaseData(integration);
                    current.phase = TelescopeFrame.CHOP_RIGHT;
                    //if(current.phase == nodPhase) current.flag |= PhaseOffsets.SKIP_GAINS;
                    current.start = exposure;
                    current.end = exposure;
                    if(current.phase != 0) add(current);
                }
                else current.end = exposure;
                usable++;
            }
            else exposure.flag(transitFlag);
        }
        
        chopper.efficiency = ((double) usable / size());
        
        CRUSH.values(this, "Chopper parameters: " + chopper);
        
        validate();
        
        // Discard transit frames altogether...
        for(int i=integration.size(); --i >=0; ) {
            final Frame exposure = integration.get(i);
            if(exposure != null) if(exposure.isFlagged(TelescopeFrame.CHOP_TRANSIT)) integration.set(i, null);
        }
        
        integration.removeOffsets(false);
        
        // Get the initial phase data...
        integration.updatePhases();      
    }
        
}
