package crush.sourcemodel;

import java.util.stream.IntStream;

import crush.Instrument;
import crush.Scan;
import jnum.data.DataPoint;

public class BlinkPhotometry extends Photometry {

   
    public BlinkPhotometry(Instrument<?> instrument) {
        super(instrument);
    }
    
    @Override
    public void process(Scan<?> scan) {
        Instrument<?> instrument = getInstrument();
        
        DataPoint[] on = instrument.getDataPoints();
        DataPoint[] off = instrument.getDataPoints();
        IntStream.range(0, instrument.size()).parallel().forEach(i -> { on[i].noData(); off[i].noData(); } );
        
        // TODO fork over frames...
        
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

}
