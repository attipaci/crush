package crush.sourcemodel;

import java.util.Map;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.stream.IntStream;

import crush.Channel;
import crush.Instrument;
import crush.Integration;
import crush.PhaseModulated;
import crush.Scan;
import crush.ChannelGroup.Fork;
import crush.instrument.RCPFile.Entry;
import crush.Frame;
import crush.telescope.TelescopeFrame;
import crush.telescope.TelescopeScan;
import jnum.data.DataPoint;
import jnum.data.WeightedPoint;
import jnum.math.Range;

public class BlinkPhotometry extends Photometry {
    /**
     * 
     */
    private static final long serialVersionUID = 1853084529550142868L;

    public BlinkPhotometry(Instrument<?> instrument) {
        super(instrument);
    }
    
    @Override
    public void process(Scan<?> scan) {
        Instrument<?> instrument = getInstrument();
        
        Data data = new Data(instrument.size());
        
        for(Integration<?> integration : scan) extractData(integration, data);
        
        // TODO fork over frames...
        
    }
    
    protected void extractData(final Integration<?> integration, final Data data) {     
        // Proceed only if there are enough pixels to do the job...
        if(!checkPixelCount(integration)) return;       
        if(!(integration instanceof PhaseModulated)) 
            throw new IllegalArgumentException("Integration " + integration.getDisplayID() + " is not phase-modulated.");

        final Instrument<?> instrument = integration.getInstrument();
        final PixelData[] local = new PixelData[instrument.size()];
        final double[] sourceGains = instrument.getSourceGains(getPointSize(), false);
        
        integration.new Fork<Void>() {

            @Override
            protected void process(Frame frame) {
                if(frame.isFlagged(Frame.SOURCE_FLAGS)) return;
                double fG = frame.getSourceGain(Frame.TOTAL_POWER);
                
                instrument.stream().filter(Channel::isUnflagged).forEach(channel -> {
                    final int c = channel.getIndex();
                    final double wG = frame.relativeWeight * channel.weight * fG * sourceGains[c];
                    if(wG != 0.0) {
                        DataPoint p = frame.isSourceExposed() ? local[c].on : local[c].off;
                        p.add(wG * frame.data[channel.index]);
                        p.addWeight(wG * G);
                    }
                });
            }
            
        }.process();
        
        

        double T = 1.0;

        if(integration.getScan() instanceof TelescopeScan)
            T = 0.5 * (((TelescopeFrame) integration.getFirstFrame()).getTransmission() + ((TelescopeFrame) integration.getLastFrame()).getTransmission());

        final double[] sourceGain = instrument.getSourceGains(getPointSize(), false);
        final ChopperPhases phases = (ChopperPhases) modulated.getPhases();
        final double transmission = T;
        
        
        
        instrument.getObservingChannels().new Fork<Void>() {

            @Override
            protected void process(Channel channel) {   

                Datum datum = data.get(channel.getID());
                if(datum == null) {
                    datum = new Datum();
                    data.put(channel.getID(), datum);
                }

                DataPoint point = null;

                if((channel.sourcePhase & TelescopeFrame.CHOP_LEFT) != 0) point = datum.L;
                else if((channel.sourcePhase & TelescopeFrame.CHOP_RIGHT) != 0) point = datum.R;
                else return;

                WeightedPoint df = phases.getLROffset(channel);
                double chi2 = phases.getLRChi2(channel, df.value());
         
                if(hasOption("chirange")) {
                    Range r = option("chirange").getRange(true);
                    if(!r.contains(Math.sqrt(chi2))) {
                        integration.comments.append(" <<skip>>"); 
                        df.noData();
                    }
                }

                if(!Double.isNaN(chi2)) {
                    df.scaleWeight(Math.min(1.0, 1.0 / chi2));            
                    df.scale(1.0 / (transmission * integration.gain * sourceGain[channel.getIndex()]));
                    point.average(df);
                }

            }

        }.process();
    }

    

    @Override
    public void write() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean isValid() {
        // TODO Auto-generated method stub
        return false;
    }
    
    class PixelData {
        DataPoint on, off;
        
        PixelData() {
            on = new DataPoint();
            off = new DataPoint();
        }
        
        void zero() {
            on.zero();
            off.zero();
        }
        
        void averageWidth(PixelData p) {
            on.average(p.on);
            off.average(p.off);
        }
        
        void getValue(DataPoint dst) {
            dst.copy(on);
            dst.subtract(off);
        }
        
        DataPoint getValue() {
            DataPoint p = new DataPoint();
            getValue();
            return p;
        }
    }
    
    class Data extends HashMap<String, PixelData> { 
        /**
         * 
         */
        private static final long serialVersionUID = 2470668200959181868L;

        Data(int capacity) { super(capacity); }
        
        void combineWith(Map<? extends String, ? extends PixelData> map) {
            map.entrySet().parallelStream().forEach(e -> {
                PixelData p = get(e.getKey());
                if(p == null) put(e.getKey(), p);
                else p.averageWidth(e.getValue());
            });
        }
    }


}
