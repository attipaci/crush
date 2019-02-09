package crush.telescope.apex;

import crush.Channel;
import crush.Pixel;
import jnum.math.Vector2D;

public class APEXPixel extends Pixel {

    /**
     * 
     */
    private static final long serialVersionUID = -7173546618620029274L;
    
    public Vector2D fitsPosition;
    public int type;
    public int beIndex = 0; // Starting from 0...
    
    protected APEXPixel(APEXInstrument<? extends Channel> instrument, int fixedIndex) {
        super(instrument, fixedIndex);
    }

    protected APEXPixel(APEXInstrument<? extends Channel> instrument, String id, int fixedIndex) {
        super(instrument, id, fixedIndex);
    }
    
    @Override
    public APEXPixel copy() {
        APEXPixel copy = (APEXPixel) super.copy();
        if(fitsPosition != null) copy.fitsPosition = fitsPosition.copy();
        return copy;
    }

}
